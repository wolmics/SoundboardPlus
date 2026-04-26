package org.wolmics.soundboardplus.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.AbstractSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.narration.NarrationElementOutput
import org.wolmics.soundboardplus.SimpleSoundboardClient
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.gui.overlay.CategoryConfigOverlay
import org.wolmics.soundboardplus.gui.overlay.OverlayContext
import org.wolmics.soundboardplus.gui.overlay.YtDlpOverlay
import org.wolmics.soundboardplus.util.SoundboardSession
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.io.File

class SoundboardScreen(
    private val parent: Screen? = null
) : Screen(Component.literal("Soundboard")) {

    private val mc = Minecraft.getInstance()

    private val bottomPaneHeight = if (SoundboardConfig.data.showProgressBar) 70 else 50
    private val headerHeight = 70
    private val tabBarY = 46
    private val tabBarH = 18

    private lateinit var queryField: EditBox
    private lateinit var resultsList: ResultListWidget

    private lateinit var detailLocalSlider: VolumeSlider
    private lateinit var detailPlayerSlider: VolumeSlider
    private lateinit var pauseButton: Button
    private lateinit var detailBindBtn: Button
    private lateinit var detailLabel: StringWidget
    private lateinit var progressBar: ProgressBar

    private lateinit var soundDeleteButton: Button
    private lateinit var soundLoopButton: Button

    private val overlayCtx get() = OverlayContext(font, width, height)
    private lateinit var deleteConfirm: CategoryConfigOverlay
    private lateinit var downloadOverlay: YtDlpOverlay

    private var selectedFile: File? = null
    private var selectedCategory: String? = null
    private var isBinding = false
    private var results: List<File> = emptyList()
    private var categoryList: List<String> = emptyList()

    // File that the user pressed down on — not yet confirmed as a drag
    private var dragCandidate: File? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var draggingFile: File? = null
    // Current cursor position while dragging
    private var dragX = 0
    private var dragY = 0
    private var dragHoveredTab: TabRect? = null

    private val DRAG_THRESHOLD = 6

    private data class TabRect(val x: Int, val y: Int, val w: Int, val h: Int, val category: String?, val name: String = "category")
    private val tabRects = mutableListOf<TabRect>()

    override fun init() {
        val padding = 10

        val titleWidget = StringWidget(Component.literal("Soundboard+").withStyle(ChatFormatting.BOLD), font)
        Component.literal("_")

        titleWidget.setPosition(width / 2 - titleWidget.width / 2, 5)
        addRenderableWidget(titleWidget)

        val btnAreaWidth = 5 + 65 + 5 + 75 + 11
        val searchWidth = width - padding - btnAreaWidth
        queryField = EditBox(font, padding, 20, searchWidth, 20, Component.literal("Search"))
        queryField.setResponder { scanSounds() }
        addRenderableWidget(queryField)

        setupTopButtons()
        setupDetailButtons(padding)

        deleteConfirm = CategoryConfigOverlay(
            overlayCtx,
            title = Component.literal("Delete this sound?"),
            onConfirm = { categoryName ->
                SoundboardConfig.createNewCategory(categoryName)
                scanSounds()
            }
        )

        downloadOverlay = YtDlpOverlay(
            overlayCtx, title = Component.literal("Download Sounds"),
        )

        val listTop = headerHeight + 2
        val listBottom = height - bottomPaneHeight
        resultsList = ResultListWidget(mc, width - 2 * padding, listBottom - listTop, listTop, 22)
        resultsList.setX(padding)
        addRenderableWidget(resultsList)

        if (SoundboardSession.getSelectedCategory() != null) {
            selectedCategory = SoundboardSession.getSelectedCategory()
        }

        scanSounds()
        setInitialFocus(queryField)
    }

    private fun setupTopButtons() {
        val rightEdge = width - 10
        addRenderableWidget(
            Button.builder(Component.literal("Download")) {
                downloadOverlay.setCategory(selectedCategory)
                downloadOverlay.show() 
            }.size(75, 20).pos(rightEdge - 75, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Config")) { mc.setScreen(SoundboardConfigScreen(this)) }
                .size(65, 20).pos(rightEdge - 75 - 5 - 65, 20).build()
        )
    }

    private fun setupDetailButtons(padding: Int) {
        val detailsY = height - bottomPaneHeight + 20

        detailLabel = StringWidget(Component.literal("- No sound Playing -"), font)
        detailLabel.setPosition(width / 2 - detailLabel.width / 2, detailsY - 15)
        addRenderableWidget(detailLabel)

        pauseButton = Button.builder(Component.literal("⏵")) {
            SoundboardAudioSystem.playbackPaused = !SoundboardAudioSystem.playbackPaused
        }.size(80, 20).pos(width / 2 - 40, detailsY).build()
        addRenderableWidget(pauseButton)

        detailLocalSlider = VolumeSlider(
            width / 2 - 160, detailsY, 100, 20,
            Component.literal("Local"), SoundboardAudioSystem.localVolume
        ) { updateSelectedVolume(local = it) }
        addRenderableWidget(detailLocalSlider)

        detailPlayerSlider = VolumeSlider(
            width / 2 + 60, detailsY, 100, 20,
            Component.literal("Player"), SoundboardAudioSystem.playerVolume
        ) { updateSelectedVolume(player = it) }
        addRenderableWidget(detailPlayerSlider)

        val soundSettingsStart = 10
        soundDeleteButton = Button.builder(Component.literal("\uD83D\uDDD1").withStyle(ChatFormatting.RED)) {
            if (selectedFile != null) {
                SoundboardConfig.deleteSound(selectedFile!!)
                SoundboardAudioSystem.stop(selectedFile!!.name)
                scanSounds()
            }
        }.size(20, 20).pos(soundSettingsStart, detailsY).build()
        soundDeleteButton.active = false
        addRenderableWidget(soundDeleteButton)

        soundLoopButton = Button.builder(Component.literal("\uD83D\uDD03").withStyle(ChatFormatting.DARK_GRAY)) {
            toggleSoundLoop()
            updateSoundLoopButtonText()
        }.size(20, 20).pos(soundSettingsStart + 25, detailsY).build()
        soundLoopButton.active = false
        addRenderableWidget(soundLoopButton)

        detailBindBtn = Button.builder(Component.literal("Keybind: None")) {
            if (selectedFile != null) {
                isBinding = true
                it.message = Component.literal("> Press Key <").withStyle(ChatFormatting.YELLOW)
            }
        }.size(80, 20).pos(width - 110, detailsY).build()
        detailBindBtn.active = false
        addRenderableWidget(detailBindBtn)

        progressBar = ProgressBar(
            x      = padding,
            y      = detailsY + 25,
            width  = width - 2 * padding,
            height = 14,
            getSelectedFile = { selectedFile },
            SoundboardConfig.data.showProgressBar
        )
        addRenderableWidget(progressBar)
    }

    // ── Data ────────────────────────────────────────────────────────────────────

    private fun scanSounds() {
        SoundboardConfig.refresh()

        categoryList = SoundboardConfig.data.categories.keys
            .sortedWith(compareBy { if (it == "default") "" else it.lowercase() })

        if (selectedCategory != null && selectedCategory !in SoundboardConfig.data.categories) {
            selectedCategory = null
        }

        val query = queryField.value.trim().lowercase()
        results = buildFileList(query)
        resultsList.setResults(results)

        if (selectedFile != null && results.none { it.name == selectedFile!!.name }) {
            selectSound(null)
        }
    }

    private fun buildFileList(query: String): List<File> {
        val soundDir = SimpleSoundboardClient.soundDir
        val categoriesToShow = if (selectedCategory == null)
            SoundboardConfig.data.categories.keys.toList()
        else
            listOf(selectedCategory!!)

        return categoriesToShow.flatMap { catName ->
            val dir = if (catName == "default") soundDir else File(soundDir, catName)
            val catData = SoundboardConfig.data.categories[catName] ?: return@flatMap emptyList()
            catData.sounds.keys
                .filter { it.lowercase().contains(query) }
                .mapNotNull { filename -> File(dir, filename).takeIf { it.exists() } }
        }.sortedWith(
            compareByDescending<File> { SoundboardConfig[it.name].favorite }.thenBy { it.name }
        )
    }

    private fun moveToCategory(file: File, targetCategory: String) {
        val soundDir = SimpleSoundboardClient.soundDir

        // Find which category currently owns this file
        val srcCategory = SoundboardConfig.data.categories.entries
            .firstOrNull { (_, cat) -> cat.sounds.containsKey(file.name) }
            ?.key ?: return

        if (srcCategory == targetCategory) return

        // Destination directory
        val destDir = if (targetCategory == "default") soundDir else File(soundDir, targetCategory)
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, file.name)
        if (!file.renameTo(destFile)) return   // filesystem move failed — bail out

        // Update config: copy sound entry, remove from source, add to dest
        val srcCat  = SoundboardConfig.data.categories[srcCategory]  ?: return
        val destCat = SoundboardConfig.data.categories[targetCategory] ?: return

        val soundEntry = srcCat.sounds[file.name] ?: return
        srcCat.sounds.remove(file.name)
        destCat.sounds[file.name] = soundEntry

        SoundboardConfig.save()

        // If the moved file was selected, keep it selected (now at new path)
        if (selectedFile?.name == file.name) {
            selectedFile = destFile
        }

        scanSounds()
    }

    fun selectSound(file: File?) {
        selectedFile = file
        isBinding = false

        if (file == null) {
            soundDeleteButton.active = false
            soundLoopButton.active = false

            detailBindBtn.active = false
            detailBindBtn.message = Component.literal("Keybind: -")
        } else {
            val data = SoundboardConfig[file.name]
            detailBindBtn.active = true
            soundDeleteButton.active = true


            updateSoundLoopButtonText()
            updateBindButtonText(data.keybind)
        }
    }

    private fun toggleSoundLoop() {
        if (selectedFile == null || selectedFile!!.name == null) return
        val enabled: Boolean = SoundboardAudioSystem.getSoundRepeat(selectedFile!!.name) ?: return

        SoundboardAudioSystem.setSoundRepeat(selectedFile!!.name, !enabled)
    }

    private fun updateSoundLoopButtonText() {
        if (selectedFile == null || selectedFile!!.name == null) return
        val enabled: Boolean? = SoundboardAudioSystem.getSoundRepeat(selectedFile!!.name)

        soundLoopButton.message = Component.literal("\uD83D\uDD03")
            .withStyle(if (enabled == true) ChatFormatting.GREEN else ChatFormatting.GRAY)
        soundLoopButton.active = enabled != null
    }

    private fun updateSelectedVolume(local: Float? = null, player: Float? = null) {
        local?.let { SoundboardAudioSystem.localVolume = it; SoundboardConfig.data.localVolume = it }
        player?.let { SoundboardAudioSystem.playerVolume = it; SoundboardConfig.data.playerVolume = it }
        SoundboardConfig.save()
    }

    private fun updateBindButtonText(keyCode: Int) {
        val keyName = if (keyCode > 0)
            GLFW.glfwGetKeyName(keyCode, 0)
        else
            "None"
        detailBindBtn.message = Component.literal("Keybind: $keyName")
    }

    private fun updateDetailLabel() {
        val fileName = selectedFile
            ?.takeIf { SoundboardAudioSystem.getProgress(it.name) != null }
            ?.name
            ?: SoundboardAudioSystem.getSinglePlayingName()

        detailLabel.message = if (fileName == null) {
            Component.literal("- No sound Playing -")
        } else {
            Component.literal(fileName).withStyle(ChatFormatting.YELLOW)
        }
        detailLabel.x = width / 2 - font.width(detailLabel.message) / 2
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (deleteConfirm.charTyped(input)) return true
        if (downloadOverlay.charTyped(input)) return true
        return super.charTyped(input)
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val overlayOpen = deleteConfirm.isVisible || downloadOverlay.isVisible
        val mx = if (overlayOpen) -1 else mouseX
        val my = if (overlayOpen) -1 else mouseY

        pauseButton.message = if (SoundboardAudioSystem.playbackActive()) Component.literal("⏸") else Component.literal("⏵")
        updateDetailLabel()

        super.extractRenderState(graphics, mx, my, delta)
        renderCategoryTabs(graphics, mx, my)
        progressBar.tick()

        graphics.fill(10, headerHeight, width - 10, headerHeight + 1, Color.GRAY.rgb)
        graphics.fill(10, height - bottomPaneHeight, width - 10, height - bottomPaneHeight + 1, Color.GRAY.rgb)

        deleteConfirm.render(graphics, mouseX, mouseY, delta)
        downloadOverlay.render(graphics, mouseX, mouseY, delta)

        // Drag ghost
        if (draggingFile != null) {
            renderDragGhost(graphics, dragX, dragY)
        }
    }

    //Renders a small floating label at the cursor showing the dragged filename.
    private fun renderDragGhost(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        val file = draggingFile ?: return
        var label = file.nameWithoutExtension
        val maxLabelWidth = 140
        if (font.width(label) > maxLabelWidth)
            label = font.plainSubstrByWidth(label, maxLabelWidth - 10) + "…"

        val padH = 4
        val padV = 3
        val w = font.width(label) + padH * 2
        val h = font.lineHeight + padV * 2

        // Offset so the ghost sits just above-right of the cursor tip
        val gx = x + 10
        val gy = y - h - 2

        // Semi-transparent dark background
        graphics.fill(gx, gy, gx + w, gy + h, 0xCC1a1a1a.toInt())
        // Bright border to make it pop
        graphics.fill(gx,         gy,         gx + w,     gy + 1,     0xFFFFD700.toInt()) // top
        graphics.fill(gx,         gy + h - 1, gx + w,     gy + h,     0xFFFFD700.toInt()) // bottom
        graphics.fill(gx,         gy,         gx + 1,     gy + h,     0xFFFFD700.toInt()) // left
        graphics.fill(gx + w - 1, gy,         gx + w,     gy + h,     0xFFFFD700.toInt()) // right

        graphics.text(font, label, gx + padH, gy + padV, 0xFFFFFFFF.toInt(), false)

        // Arrow hint toward the tab bar if the cursor is below it
        if (dragY > tabBarY + tabBarH + 20) {
            val hint = "↑ drop on a tab"
            val hw = font.width(hint)
            graphics.text(font, hint, gx + w / 2 - hw / 2, gy - font.lineHeight - 2, 0xAAFFFFFF.toInt(), false)
        }
    }

    private fun renderCategoryTabs(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        tabRects.clear()

        // Update which tab is hovered while dragging
        if (draggingFile != null) {
            dragHoveredTab = tabRects.firstOrNull { tab ->
                dragX in tab.x until tab.x + tab.w && dragY in tab.y until tab.y + tab.h
            }
        }

        var curX = 10
        curX = drawTab(graphics, "All", curX, null, selectedCategory == null, mouseX, mouseY)
        for (catName in categoryList) {
            val label = catName.replaceFirstChar { it.uppercase() }
            curX = drawTab(graphics, label, curX, catName, selectedCategory == catName, mouseX, mouseY)
        }
        drawTab(graphics, "＋", curX, null, false, mouseX, mouseY, name = "categoryAdder")

        // Second pass: update dragHoveredTab now that tabRects is populated this frame
        if (draggingFile != null) {
            dragHoveredTab = tabRects.firstOrNull { tab ->
                tab.name != "categoryAdder" &&
                        dragX in tab.x until tab.x + tab.w &&
                        dragY in tab.y until tab.y + tab.h
            }
        }
    }

    private fun drawTab(
        graphics: GuiGraphicsExtractor,
        label: String,
        x: Int,
        category: String?,
        active: Boolean,
        mouseX: Int,
        mouseY: Int,
        name: String = "category"
    ): Int {
        val w = font.width(label) + 12
        val hovered = mouseX in x..(x + w) && mouseY in tabBarY..(tabBarY + tabBarH)

        // During a drag, highlight the tab the ghost is hovering over
        val isDragTarget = draggingFile != null &&
                name != "categoryAdder" &&
                dragX in x until x + w &&
                dragY in tabBarY until tabBarY + tabBarH

        val bg = when {
            isDragTarget -> 0xFF2a5a2a.toInt()   // green tint — valid drop zone
            active       -> 0xFF606060.toInt()
            hovered      -> 0xFF404040.toInt()
            else         -> 0xFF282828.toInt()
        }
        val border = when {
            isDragTarget -> 0xFF55FF55.toInt()   // bright green border
            active       -> 0xFFAAAAAA.toInt()
            else         -> 0xFF555555.toInt()
        }
        val textCol = when {
            isDragTarget -> 0xFF88FF88.toInt()
            active       -> 0xFFFFFFFF.toInt()
            else         -> 0xFFAAAAAA.toInt()
        }

        graphics.fill(x, tabBarY, x + w, tabBarY + tabBarH, bg)
        graphics.fill(x, tabBarY,           x + w, tabBarY + 1,                border)
        graphics.fill(x, tabBarY + tabBarH - 1, x + w, tabBarY + tabBarH,      border)
        graphics.fill(x, tabBarY,           x + 1, tabBarY + tabBarH,          border)
        graphics.fill(x + w - 1, tabBarY,   x + w, tabBarY + tabBarH,          border)

        graphics.text(
            font, label,
            x + (w - font.width(label)) / 2,
            tabBarY + (tabBarH - font.lineHeight) / 2,
            textCol, false
        )

        tabRects.add(TabRect(x, tabBarY, w, tabBarH, category, name = name))
        return x + w + 4
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (deleteConfirm.mouseClicked(click, doubled)) return true
        if (downloadOverlay.mouseClicked(click, doubled)) return true

        // Cancel any pending drag on right-click
        if (click.button() == 1) {
            cancelDrag()
        }

        for (tab in tabRects) {
            if (click.x >= tab.x && click.x < tab.x + tab.w &&
                click.y >= tab.y && click.y < tab.y + tab.h
            ) {
                if (tab.name == "categoryAdder") {
                    deleteConfirm.show()
                    return true
                }
                if (selectedCategory != tab.category) {
                    selectedCategory = tab.category
                    scanSounds()
                }
                return true
            }
        }

        return super.mouseClicked(click, doubled)
    }

    fun onEntryMouseDown(file: File, mouseX: Int, mouseY: Int) {
        dragCandidate = file
        dragStartX = mouseX
        dragStartY = mouseY
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetY: Double, offsetX: Double): Boolean {
        // Activate drag once the cursor has moved past the threshold
        if (dragCandidate != null && draggingFile == null) {
            val dx = click.x - dragStartX
            val dy = click.y - dragStartY
            if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                draggingFile = dragCandidate
            }
        }

        if (draggingFile != null) {
            dragX = click.x.toInt()
            dragY = click.y.toInt()
            return true
        }

        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        val droppedFile = draggingFile

        if (droppedFile != null) {
            // Find the tab the cursor is over
            val targetTab = tabRects.firstOrNull { tab ->
                tab.name != "categoryAdder" &&
                        click.x.toInt() in tab.x until tab.x + tab.w &&
                        click.y.toInt() in tab.y until tab.y + tab.h
            }

            if (targetTab != null) {
                // "All" tab (category == null) means default
                val dest = targetTab.category ?: "default"
                moveToCategory(droppedFile, dest)
            }

            cancelDrag()
            return true
        }

        cancelDrag()
        return super.mouseReleased(click)
    }

    private fun cancelDrag() {
        draggingFile = null
        dragCandidate = null
        dragHoveredTab = null
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (deleteConfirm.keyPressed(input)) return true
        if (downloadOverlay.keyPressed(input)) return true

        // ESC cancels an active drag instead of closing the screen
        if (input.key == GLFW.GLFW_KEY_ESCAPE && draggingFile != null) {
            cancelDrag()
            return true
        }

        if (isBinding && selectedFile != null) {
            val data = SoundboardConfig[selectedFile!!.name]
            data.keybind = if (input.key == GLFW.GLFW_KEY_ESCAPE) -1 else input.key
            SoundboardConfig.save()
            updateBindButtonText(data.keybind)
            isBinding = false
            return true
        }
        if (input.key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun onClose() {
        SoundboardConfig.save()
        SoundboardSession.setSelectedCategory(selectedCategory)
        mc.setScreen(parent)
    }

    // ── List widget ─────────────────────────────────────────────────────────────

    private inner class ResultListWidget(
        client: Minecraft, width: Int, height: Int, y: Int, itemHeight: Int
    ) : AbstractSelectionList<ResultListWidget.Entry>(client, width, height, y, itemHeight) {

        fun setResults(files: List<File>) {
            clearEntries()
            files.forEach { addEntry(Entry(it)) }
            setScrollAmount(0.0)
        }

        override fun getRowWidth() = width - 20

        override fun updateWidgetNarration(output: NarrationElementOutput) {}

        override fun extractSelection(graphics: GuiGraphicsExtractor, entry: Entry, outlineColor: Int) {
            // Prevents the default-rendered selection outlines
        }

        inner class Entry(private val file: File) : AbstractSelectionList.Entry<Entry>() {

            private val favBtn: Button
            private val playBtn: Button

            init {
                val data = SoundboardConfig[file.name]

                favBtn = Button.builder(
                    Component.literal(if (data.favorite) "★" else "☆")
                        .withStyle(if (data.favorite) ChatFormatting.GOLD else ChatFormatting.GRAY)
                ) {
                    data.favorite = !data.favorite
                    SoundboardConfig.save()
                    scanSounds()
                }.size(20, 20).build()

                playBtn = Button.builder(Component.literal("Play")) {
                    if (SoundboardAudioSystem.isPlaying(file.name)) SoundboardAudioSystem.stop(file.name)
                    else SoundboardAudioSystem.playFile(file)
                    selectSound(file)
                }.size(40, 20).build()
            }

            override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, a: Float) {
                val isPlaying = SoundboardAudioSystem.isPlaying(file.name)

                // Play Button
                playBtn.message = if (SoundboardAudioSystem.playbackPaused && isPlaying)
                    Component.literal("Paused").withStyle(ChatFormatting.YELLOW)
                else if (isPlaying) Component.literal("Stop").withStyle(ChatFormatting.RED)
                else Component.literal("Play")

                // Dragging
                val beingDragged = draggingFile?.name == file.name
                val alpha = if (draggingFile != null && !beingDragged) 0x55 else 0xFF

                if (beingDragged)
                    graphics.fill(x, y + 1, x + width, y + height - 1, 0x44FFD700)

                // Selected File
                if (selectedFile?.name == file.name)
                    graphics.fill(x, y + 1, x + width, y + height - 1, 0x33FFFFFF)

                // Song name
                val textY = y + (height - font.lineHeight) / 2 + 1
                var name = file.nameWithoutExtension
                if (font.width(name) > width - 70)
                    name = font.plainSubstrByWidth(name, width - 75) + "..."

                val textColor = (alpha shl 24) or (0x00FFFFFF and Color.WHITE.rgb)
                graphics.text(font, name, x + 25, textY, textColor, true)

                // Buttons
                favBtn.x = x;                 favBtn.y = y + (height - 20) / 2
                playBtn.x = x + width - 40;   playBtn.y = y + (height - 20) / 2

                favBtn.extractRenderState(graphics, mouseX, mouseY, a)
                playBtn.extractRenderState(graphics, mouseX, mouseY, a)
            }

            override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
                if (favBtn.mouseClicked(click, doubled)) return true
                if (playBtn.mouseClicked(click, doubled)) return true

                onEntryMouseDown(file, click.x.toInt(), click.y.toInt())

                if (doubled) {
                    if (SoundboardAudioSystem.isPlaying(file.name)) SoundboardAudioSystem.stop(file.name)
                    else SoundboardAudioSystem.playFile(file)
                } else selectSound(file)
                return true
            }
        }
    }
}