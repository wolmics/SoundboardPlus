package org.wolmics.soundboardplus.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ElementListWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Formatting
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
) : Screen(Text.literal("Soundboard")) {

    private val mc = MinecraftClient.getInstance()

    private val bottomPaneHeight = if (SoundboardConfig.data.showProgressBar) 70 else 50
    private val headerHeight = 70
    private val tabBarY = 46
    private val tabBarH = 18

    private lateinit var queryField: TextFieldWidget
    private lateinit var resultsList: ResultListWidget

    private lateinit var detailLocalSlider: VolumeSlider
    private lateinit var detailPlayerSlider: VolumeSlider
    private lateinit var pauseButton: ButtonWidget
    private lateinit var detailBindBtn: ButtonWidget
    private lateinit var detailLabel: TextWidget
    private lateinit var progressBar: ProgressBar

    private lateinit var soundDeleteButton: ButtonWidget
    private lateinit var stopAllButton: ButtonWidget
    private lateinit var soundLoopButton: ButtonWidget

    private val overlayCtx get() = OverlayContext(textRenderer, width, height)
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

        val titleWidget = TextWidget(Text.literal("Soundboard+").formatted(Formatting.BOLD), textRenderer)
        titleWidget.setPosition(width / 2 - titleWidget.width / 2, 5)
        addDrawableChild(titleWidget)

        val btnAreaWidth = 5 + 65 + 5 + 75 + 11
        val searchWidth = width - padding - btnAreaWidth
        queryField = TextFieldWidget(textRenderer, padding, 20, searchWidth, 20, Text.literal("Search"))
        queryField.setChangedListener { scanSounds() }
        addDrawableChild(queryField)

        setupTopButtons()
        setupDetailButtons(padding)

        deleteConfirm = CategoryConfigOverlay(
            overlayCtx,
            title = Text.literal("Delete this sound?"),
            onConfirm = { categoryName ->
                SoundboardConfig.createNewCategory(categoryName)
                scanSounds()
            }
        )

        downloadOverlay = YtDlpOverlay(
            overlayCtx, title = Text.literal("Download Sounds"),
        )

        val listTop = headerHeight + 2
        val listBottom = height - bottomPaneHeight
        resultsList = ResultListWidget(mc, width - 2 * padding, listBottom - listTop, listTop, 22)
        resultsList.setX(padding)
        addDrawableChild(resultsList)

        if (SoundboardSession.getSelectedCategory() != null) {
            selectedCategory = SoundboardSession.getSelectedCategory()
        }

        scanSounds()
        setInitialFocus(queryField)
    }

    private fun setupTopButtons() {
        val rightEdge = width - 10
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Download")) {
                downloadOverlay.setCategory(selectedCategory)
                downloadOverlay.show() 
            }.size(75, 20).position(rightEdge - 75, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Config")) { mc.setScreen(SoundboardConfigScreen(this)) }
                .size(65, 20).position(rightEdge - 75 - 5 - 65, 20).build()
        )
    }

    private fun setupDetailButtons(padding: Int) {
        val detailsY = height - bottomPaneHeight + 20

        detailLabel = TextWidget(Text.literal("- No sound Playing -"), textRenderer)
        detailLabel.setPosition(width / 2 - detailLabel.width / 2, detailsY - 15)
        addDrawableChild(detailLabel)

        pauseButton = ButtonWidget.builder(Text.literal("⏵")) {
            SoundboardAudioSystem.playbackPaused = !SoundboardAudioSystem.playbackPaused
        }.size(80, 20).position(width / 2 - 40, detailsY).build()
        addDrawableChild(pauseButton)

        detailLocalSlider = VolumeSlider(
            width / 2 - 160, detailsY, 100, 20,
            Text.literal("Local"), SoundboardAudioSystem.localVolume
        ) { updateSelectedVolume(local = it) }
        addDrawableChild(detailLocalSlider)

        detailPlayerSlider = VolumeSlider(
            width / 2 + 60, detailsY, 100, 20,
            Text.literal("Player"), SoundboardAudioSystem.playerVolume
        ) { updateSelectedVolume(player = it) }
        addDrawableChild(detailPlayerSlider)

        val soundSettingsStart = 10
        soundDeleteButton = ButtonWidget.builder(Text.literal("\uD83D\uDDD1").formatted(Formatting.RED)) {
            if (selectedFile != null) {
                SoundboardConfig.deleteSound(selectedFile!!)
                SoundboardAudioSystem.stop(selectedFile!!.name)
                scanSounds()
            }
        }.size(20, 20).position(soundSettingsStart, detailsY).build()
        soundDeleteButton.active = false
        addDrawableChild(soundDeleteButton)

        stopAllButton = ButtonWidget.builder(Text.literal("■").formatted(Formatting.RED)) {
            SoundboardAudioSystem.stopAll()
            updateSoundLoopButtonText()
        }.size(20, 20).position(soundSettingsStart + 25, detailsY).build()
        stopAllButton.active = false
        addDrawableChild(stopAllButton)

        soundLoopButton = ButtonWidget.builder(Text.literal("\uD83D\uDD03").formatted(Formatting.DARK_GRAY)) {
            toggleSoundLoop()
            updateSoundLoopButtonText()
        }.size(20, 20).position(soundSettingsStart + 50, detailsY).build()
        soundLoopButton.active = false
        addDrawableChild(soundLoopButton)

        detailBindBtn = ButtonWidget.builder(Text.literal("Keybind: None")) {
            if (selectedFile != null) {
                isBinding = true
                it.message = Text.literal("> Press Key <").formatted(Formatting.YELLOW)
            }
        }.size(80, 20).position(width - 110, detailsY).build()
        detailBindBtn.active = false
        addDrawableChild(detailBindBtn)

        progressBar = ProgressBar(
            x      = padding,
            y      = detailsY + 25,
            width  = width - 2 * padding,
            height = 14,
            getSelectedFile = { selectedFile },
            SoundboardConfig.data.showProgressBar
        )
        addDrawableChild(progressBar)
    }

    // ── Data ────────────────────────────────────────────────────────────────────

    private fun scanSounds() {
        SoundboardConfig.refresh()

        categoryList = SoundboardConfig.data.categories.keys
            .sortedWith(compareBy { if (it == "default") "" else it.lowercase() })

        if (selectedCategory != null && selectedCategory !in SoundboardConfig.data.categories) {
            selectedCategory = null
        }

        val query = queryField.text.trim().lowercase()
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
            detailBindBtn.message = Text.literal("Keybind: -")
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

        soundLoopButton.message = Text.literal("\uD83D\uDD03")
            .formatted(if (enabled == true) Formatting.GREEN else Formatting.GRAY)
        soundLoopButton.active = enabled != null
    }

    private fun updateSelectedVolume(local: Float? = null, player: Float? = null) {
        local?.let { SoundboardAudioSystem.localVolume = it; SoundboardConfig.data.localVolume = it }
        player?.let { SoundboardAudioSystem.playerVolume = it; SoundboardConfig.data.playerVolume = it }
        SoundboardConfig.save()
    }

    private fun updateBindButtonText(keyCode: Int) {
        val keyName = if (keyCode > 0)
            InputUtil.fromKeyCode(KeyInput(keyCode, 0, 0)).localizedText
        else
            Text.literal("None")
        detailBindBtn.message = Text.literal("Keybind: ").append(keyName)
    }

    private fun updateDetailLabel() {
        val fileName = selectedFile
            ?.takeIf { SoundboardAudioSystem.getProgress(it.name) != null }
            ?.name
            ?: SoundboardAudioSystem.getSinglePlayingName()

        detailLabel.message = if (fileName == null) {
            Text.literal("- No sound Playing -")
        } else {
            Text.literal(fileName).formatted(Formatting.YELLOW)
        }
        detailLabel.x = width / 2 - textRenderer.getWidth(detailLabel.message) / 2
    }

    override fun charTyped(input: CharInput): Boolean {
        if (deleteConfirm.charTyped(input)) return true
        if (downloadOverlay.charTyped(input)) return true
        return super.charTyped(input)
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val overlayOpen = deleteConfirm.isVisible || downloadOverlay.isVisible
        val mx = if (overlayOpen) -1 else mouseX
        val my = if (overlayOpen) -1 else mouseY

        pauseButton.message = if (SoundboardAudioSystem.playbackActive()) Text.of("⏸") else Text.of("⏵")
        stopAllButton.active = SoundboardAudioSystem.playbackActive()
        updateDetailLabel()

        super.render(context, mx, my, delta)
        renderCategoryTabs(context, mx, my)
        progressBar.tick()

        context.fill(10, headerHeight, width - 10, headerHeight + 1, Color.GRAY.rgb)
        context.fill(10, height - bottomPaneHeight, width - 10, height - bottomPaneHeight + 1, Color.GRAY.rgb)

        deleteConfirm.render(context, mouseX, mouseY, delta)
        downloadOverlay.render(context, mouseX, mouseY, delta)

        // ── Drag ghost ─────────────────────────────────────────────────────────
        if (draggingFile != null) {
            renderDragGhost(context, dragX, dragY)
        }
    }

    //Renders a small floating label at the cursor showing the dragged filename.
    private fun renderDragGhost(context: DrawContext, x: Int, y: Int) {
        val file = draggingFile ?: return
        var label = file.nameWithoutExtension
        val maxLabelWidth = 140
        if (textRenderer.getWidth(label) > maxLabelWidth)
            label = textRenderer.trimToWidth(label, maxLabelWidth - 10) + "…"

        val padH = 4
        val padV = 3
        val w = textRenderer.getWidth(label) + padH * 2
        val h = textRenderer.fontHeight + padV * 2

        // Offset so the ghost sits just above-right of the cursor tip
        val gx = x + 10
        val gy = y - h - 2

        // Semi-transparent dark background
        context.fill(gx, gy, gx + w, gy + h, 0xCC1a1a1a.toInt())
        // Bright border to make it pop
        context.fill(gx,         gy,         gx + w,     gy + 1,     0xFFFFD700.toInt()) // top
        context.fill(gx,         gy + h - 1, gx + w,     gy + h,     0xFFFFD700.toInt()) // bottom
        context.fill(gx,         gy,         gx + 1,     gy + h,     0xFFFFD700.toInt()) // left
        context.fill(gx + w - 1, gy,         gx + w,     gy + h,     0xFFFFD700.toInt()) // right

        context.drawText(textRenderer, label, gx + padH, gy + padV, 0xFFFFFFFF.toInt(), false)

        // Arrow hint toward the tab bar if the cursor is below it
        if (dragY > tabBarY + tabBarH + 20) {
            val hint = "↑ drop on a tab"
            val hw = textRenderer.getWidth(hint)
            context.drawText(textRenderer, hint, gx + w / 2 - hw / 2, gy - textRenderer.fontHeight - 2, 0xAAFFFFFF.toInt(), false)
        }
    }

    private fun renderCategoryTabs(context: DrawContext, mouseX: Int, mouseY: Int) {
        tabRects.clear()

        // Update which tab is hovered while dragging
        if (draggingFile != null) {
            dragHoveredTab = tabRects.firstOrNull { tab ->
                dragX in tab.x until tab.x + tab.w && dragY in tab.y until tab.y + tab.h
            }
        }

        var curX = 10
        curX = drawTab(context, "All", curX, null, selectedCategory == null, mouseX, mouseY)
        for (catName in categoryList) {
            val label = catName.replaceFirstChar { it.uppercase() }
            curX = drawTab(context, label, curX, catName, selectedCategory == catName, mouseX, mouseY)
        }
        drawTab(context, "＋", curX, null, false, mouseX, mouseY, name = "categoryAdder")

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
        context: DrawContext,
        label: String,
        x: Int,
        category: String?,
        active: Boolean,
        mouseX: Int,
        mouseY: Int,
        name: String = "category"
    ): Int {
        val w = textRenderer.getWidth(label) + 12
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

        context.fill(x, tabBarY, x + w, tabBarY + tabBarH, bg)
        context.fill(x, tabBarY,           x + w, tabBarY + 1,                border)
        context.fill(x, tabBarY + tabBarH - 1, x + w, tabBarY + tabBarH,      border)
        context.fill(x, tabBarY,           x + 1, tabBarY + tabBarH,          border)
        context.fill(x + w - 1, tabBarY,   x + w, tabBarY + tabBarH,          border)

        context.drawText(
            textRenderer, label,
            x + (w - textRenderer.getWidth(label)) / 2,
            tabBarY + (tabBarH - textRenderer.fontHeight) / 2,
            textCol, false
        )

        tabRects.add(TabRect(x, tabBarY, w, tabBarH, category, name = name))
        return x + w + 4
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
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

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
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

    override fun mouseReleased(click: Click): Boolean {
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

    override fun keyPressed(input: KeyInput): Boolean {
        if (deleteConfirm.keyPressed(input)) return true
        if (downloadOverlay.keyPressed(input)) return true

        // ESC cancels an active drag instead of closing the screen
        if (input.key == GLFW.GLFW_KEY_ESCAPE && draggingFile != null) {
            cancelDrag()
            return true
        }

        if (isBinding && selectedFile != null) {
            val data = SoundboardConfig[selectedFile!!.name]
            data.keybind = if (input.key == GLFW.GLFW_KEY_ESCAPE) -1 else input.key()
            SoundboardConfig.save()
            updateBindButtonText(data.keybind)
            isBinding = false
            return true
        }
        if (input.key == GLFW.GLFW_KEY_ESCAPE) { close(); return true }
        return super.keyPressed(input)
    }

    override fun close() {
        SoundboardConfig.save()
        SoundboardSession.setSelectedCategory(selectedCategory)
        mc.setScreen(parent)
    }

    // ── List widget ─────────────────────────────────────────────────────────────

    private inner class ResultListWidget(
        client: MinecraftClient, width: Int, height: Int, y: Int, itemHeight: Int
    ) : ElementListWidget<ResultListWidget.Entry>(client, width, height, y, itemHeight) {

        fun setResults(files: List<File>) {
            clearEntries()
            files.forEach { addEntry(Entry(it)) }
            scrollY = 0.0
        }

        override fun getRowWidth() = width - 20

        inner class Entry(private val file: File) : ElementListWidget.Entry<Entry>() {

            private val favBtn: ButtonWidget
            private val playBtn: ButtonWidget
            private val elements = mutableListOf<Element>()
            private val selectables = mutableListOf<Selectable>()

            init {
                val data = SoundboardConfig[file.name]

                favBtn = ButtonWidget.builder(
                    Text.literal(if (data.favorite) "★" else "☆")
                        .formatted(if (data.favorite) Formatting.GOLD else Formatting.GRAY)
                ) {
                    data.favorite = !data.favorite
                    SoundboardConfig.save()
                    scanSounds()
                }.size(20, 20).build()

                playBtn = ButtonWidget.builder(Text.literal("Play")) {
                    if (SoundboardAudioSystem.isPlaying(file.name)) SoundboardAudioSystem.stop(file.name)
                    else SoundboardAudioSystem.playFile(file)
                    selectSound(file)
                }.size(40, 20).build()

                elements += favBtn; elements += playBtn
                selectables += favBtn; selectables += playBtn
            }

            override fun render(context: DrawContext, mouseX: Int, mouseY: Int, hovered: Boolean, deltaTicks: Float) {
                val isPlaying = SoundboardAudioSystem.isPlaying(file.name)

                playBtn.message = if (SoundboardAudioSystem.playbackPaused && isPlaying)
                    Text.literal("Paused").formatted(Formatting.YELLOW)
                else if (isPlaying) Text.literal("Stop").formatted(Formatting.RED)
                else Text.literal("Play")

                // Dim entries while a different file is being dragged
                val beingDragged = draggingFile?.name == file.name
                val alpha = if (draggingFile != null && !beingDragged) 0x55 else 0xFF

                if (selectedFile?.name == file.name)
                    context.fill(x, y + 1, x + width, y + height - 1, 0x33FFFFFF)

                // Highlight the row being dragged
                if (beingDragged)
                    context.fill(x, y + 1, x + width, y + height - 1, 0x44FFD700)

                val textY = y + (height - textRenderer.fontHeight) / 2 + 1
                var name = file.nameWithoutExtension
                if (textRenderer.getWidth(name) > width - 70)
                    name = textRenderer.trimToWidth(name, width - 75) + "..."

                val textColor = (alpha shl 24) or (0x00FFFFFF and Color.WHITE.rgb)
                context.drawText(textRenderer, name, x + 25, textY, textColor, true)

                favBtn.x = x;                 favBtn.y = y + (height - 20) / 2
                playBtn.x = x + width - 40;   playBtn.y = y + (height - 20) / 2

                favBtn.render(context, mouseX, mouseY, deltaTicks)
                playBtn.render(context, mouseX, mouseY, deltaTicks)
            }

            override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
                if (favBtn.mouseClicked(click, doubled)) return true
                if (playBtn.mouseClicked(click, doubled)) return true

                // Register as a drag candidate (drag activates in mouseDragged)
                onEntryMouseDown(file, click.x.toInt(), click.y.toInt())

                if (doubled) {
                    if (SoundboardAudioSystem.isPlaying(file.name)) SoundboardAudioSystem.stop(file.name)
                    else SoundboardAudioSystem.playFile(file)
                } else selectSound(file)
                return true
            }

            override fun children() = elements
            override fun selectableChildren() = selectables
        }
    }
}