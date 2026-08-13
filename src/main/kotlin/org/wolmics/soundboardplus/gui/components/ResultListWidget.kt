package org.wolmics.soundboardplus.gui.components

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.config.SoundData
import org.wolmics.soundboardplus.util.ToastManager
import java.awt.Color

@Suppress("EXPOSED_SUPER_CLASS")
class ResultListWidget(
    client: MinecraftClient,
    width: Int,
    height: Int,
    y: Int,
    itemHeight: Int,
    private val textRenderer: TextRenderer,
    private val getSelectedSound: () -> SoundData?,
    private val getDraggingSound: () -> SoundData?,
    private val onSelectSound: (SoundData) -> Unit,
    private val onEntryMouseDown: (SoundData, Int, Int) -> Unit,
    private val onSoundListChanged: () -> Unit,
) : ElementListWidget<ResultListWidget.Entry>(client, width, height, y, itemHeight) {

    fun setResults(sounds: List<SoundData>) {
        clearEntries()
        sounds.forEach { addEntry(Entry(it)) }

        setScrollY(scrollY) // Checks if the scroll is valid
    }

    fun renamingEntry(): Entry? = children().firstOrNull { it.needsKeyInput() }

    fun scrollToSound(sound: SoundData) {
        val entry = children().find { it.id == sound.id}!!
        super.scrollTo(entry)
    }

    override fun getRowWidth() = width - 30

    inner class Entry(private val sound: SoundData) : ElementListWidget.Entry<Entry>() {
        val id = sound.id

        private val favBtn: ButtonWidget
        private val playBtn: ButtonWidget

        private var renaming: Boolean = false
        private var renamedName: String = sound.name
        private val elements = mutableListOf<Element>()
        private val selectables = mutableListOf<Selectable>()

        init {
            favBtn = ButtonWidget.builder(
                Text.literal(if (sound.favorite) "★" else "☆")
                    .formatted(if (sound.favorite) Formatting.GOLD else Formatting.GRAY)
            ) {
                sound.favorite = !sound.favorite
                SoundboardConfig.save()
                onSoundListChanged()
            }.size(20, 20).build()

            playBtn = ButtonWidget.builder(Text.literal("Play")) {
                SoundboardAudioSystem.cyclePlaySound(sound)
                onSelectSound(sound)
            }.size(40, 20).build()

            elements += favBtn; elements += playBtn
            selectables += favBtn; selectables += playBtn
        }

        override fun render(context: DrawContext, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) {
            val isPlaying = SoundboardAudioSystem.isPlaying(sound)

            // Play Button
            playBtn.message = if (SoundboardAudioSystem.playbackPaused && isPlaying)
                Text.literal("Paused").formatted(Formatting.YELLOW)
            else if (isPlaying) Text.literal("Stop").formatted(Formatting.RED)
            else Text.literal("Play")

            // Dragging alpha layer
            val draggingSound = getDraggingSound()
            val alpha = if (draggingSound != null && sound != draggingSound) 0x55 else 0xFF

            // Selected File
            if (sound == getSelectedSound())
                context.fill(x, y + 1, x + width, y + height - 1, 0x33FFFFFF)

            // Song name
            val textY = y + (height - textRenderer.fontHeight) / 2 + 1
            var name = sound.name
            if (textRenderer.getWidth(name) > width - 70)
                name = textRenderer.trimToWidth(name, width - 75) + "..."

            val textColor = (alpha shl 24) or (0x00FFFFFF and Color.WHITE.rgb)

            if (!isFocused && renaming) renaming = false

            // Render normal Category Name
            if (!renaming) {
                context.drawText(textRenderer, name, x + 25, textY, textColor, true)
            } else {
                val text: Text = Text.literal(renamedName + "_").formatted(Formatting.ITALIC)
                context.drawText(textRenderer, text, x + 25, textY, textColor, true)
            }

            // Buttons
            favBtn.x = x;                 favBtn.y = y + (height - 20) / 2
            playBtn.x = x + width - 40;   playBtn.y = y + (height - 20) / 2

            favBtn.render(context, mouseX, mouseY, a)
            playBtn.render(context, mouseX, mouseY, a)
        }

        fun needsKeyInput(): Boolean {
            return renaming
        }

        override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
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

        override fun charTyped(event: CharInput): Boolean {
            if (renaming) renamedName += Char(event.codepoint())
            return super.charTyped(event)
        }

        override fun keyPressed(event: KeyInput): Boolean {
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

                    ToastManager.createToast(Text.literal("Successfully renamed File!").formatted(Formatting.GREEN), 2000)
                } else {
                    ToastManager.createToast(Text.literal("Illegal Characters").formatted(Formatting.RED), 2000)
                }
            }

            if (renaming && key == GLFW.GLFW_KEY_BACKSPACE) renamedName = renamedName.dropLast(1)

            return super.keyPressed(event)
        }

        override fun children() = elements
        override fun selectableChildren() = selectables
    }
}