package org.wolmics.soundboardplus.gui.components

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSelectionList
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.config.SoundData
import org.wolmics.soundboardplus.util.ToastManager
import java.awt.Color

@Suppress("EXPOSED_SUPER_CLASS")
class ResultListWidget(
    client: Minecraft,
    width: Int,
    height: Int,
    y: Int,
    itemHeight: Int,
    private val font: Font,
    private val getSelectedSound: () -> SoundData?,
    private val getDraggingSound: () -> SoundData?,
    private val onSelectSound: (SoundData) -> Unit,
    private val onEntryMouseDown: (SoundData, Int, Int) -> Unit,
    private val onSoundListChanged: () -> Unit,
) : AbstractSelectionList<ResultListWidget.Entry>(client, width, height, y, itemHeight) {

    fun setResults(sounds: List<SoundData>) {
        clearEntries()
        sounds.forEach { addEntry(Entry(it)) }

        setScrollAmount(scrollAmount()) // Checks if the scroll is valid
    }

    fun renamingEntry(): Entry? = children().firstOrNull { it.needsKeyInput() }

    fun scrollToSound(sound: SoundData) {
        val entry = children().find { it.id == sound.id}!!
        super.scrollToEntry(entry)
    }

    override fun getRowWidth() = width - 30

    override fun updateWidgetNarration(output: NarrationElementOutput) {}

    override fun extractSelection(graphics: GuiGraphicsExtractor, entry: Entry, outlineColor: Int) {
        // Prevents the default-rendered selection outlines
    }

    inner class Entry(private val sound: SoundData) : AbstractSelectionList.Entry<Entry>() {
        val id = sound.id

        private val favBtn: Button
        private val playBtn: Button

        private var renaming: Boolean = false
        private var renamedName: String = sound.name

        init {
            favBtn = Button.builder(
                Component.literal(if (sound.favorite) "★" else "☆")
                    .withStyle(if (sound.favorite) ChatFormatting.GOLD else ChatFormatting.GRAY)
            ) {
                sound.favorite = !sound.favorite
                SoundboardConfig.save()
                onSoundListChanged()
            }.size(20, 20).build()

            playBtn = Button.builder(Component.literal("Play")) {
                SoundboardAudioSystem.cyclePlaySound(sound)
                onSelectSound(sound)
            }.size(40, 20).build()
        }

        override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) {
            val isPlaying = SoundboardAudioSystem.isPlaying(sound)

            // Play Button
            playBtn.message = if (SoundboardAudioSystem.playbackPaused && isPlaying)
                Component.literal("Paused").withStyle(ChatFormatting.YELLOW)
            else if (isPlaying) Component.literal("Stop").withStyle(ChatFormatting.RED)
            else Component.literal("Play")

            // Dragging alpha layer
            val draggingSound = getDraggingSound()
            val alpha = if (draggingSound != null && sound != draggingSound) 0x55 else 0xFF

            // Selected File
            if (sound == getSelectedSound())
                graphics.fill(x, y + 1, x + width, y + height - 1, 0x33FFFFFF)

            // Song name
            val textY = y + (height - font.lineHeight) / 2 + 1
            var name = sound.name
            if (font.width(name) > width - 70)
                name = font.plainSubstrByWidth(name, width - 75) + "..."

            val textColor = (alpha shl 24) or (0x00FFFFFF and Color.WHITE.rgb)

            if (!isFocused && renaming) renaming = false

            // Render normal Category Name
            if (!renaming) {
                graphics.text(font, name, x + 25, textY, textColor, true)
            } else {
                val text: Component = Component.literal(renamedName + "_").withStyle(ChatFormatting.ITALIC)
                graphics.text(font, text, x + 25, textY, textColor, true)
            }

            // Buttons
            favBtn.x = x;                 favBtn.y = y + (height - 20) / 2
            playBtn.x = x + width - 40;   playBtn.y = y + (height - 20) / 2

            favBtn.extractRenderState(graphics, mouseX, mouseY, a)
            playBtn.extractRenderState(graphics, mouseX, mouseY, a)
        }

        fun needsKeyInput(): Boolean {
            return renaming
        }

        override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
            if (favBtn.mouseClicked(click, doubled)) return true
            if (playBtn.mouseClicked(click, doubled)) return true

            // 1 == right-click
            renaming = click.button() == 1

            onEntryMouseDown(sound, click.x.toInt(), click.y.toInt())

            if (doubled && !renaming) {
                SoundboardAudioSystem.cyclePlaySound(sound)
            } else onSelectSound(sound)
            return true
        }

        override fun charTyped(event: CharacterEvent): Boolean {
            if (renaming) renamedName += Char(event.codepoint())
            return super.charTyped(event)
        }

        override fun keyPressed(event: KeyEvent): Boolean {
            val key = event.key()
            println(key)

            if (renaming && key == GLFW.GLFW_KEY_ENTER) {
                if (sound.name == renamedName) return super.keyPressed(event)

                SoundboardAudioSystem.stop(sound)
                val success = SoundboardConfig.renameSound(sound.id, renamedName)

                if (success) {
                    onSoundListChanged()

                    renaming = false
                    setFocused(false)

                    ToastManager.createToast(Component.literal("Successfully renamed File!").withStyle(ChatFormatting.GREEN), 2000)
                } else {
                    ToastManager.createToast(Component.literal("Illegal Characters").withStyle(ChatFormatting.RED), 2000)
                }
            }

            if (renaming && key == GLFW.GLFW_KEY_BACKSPACE) renamedName = renamedName.dropLast(1)

            return super.keyPressed(event)
        }
    }
}