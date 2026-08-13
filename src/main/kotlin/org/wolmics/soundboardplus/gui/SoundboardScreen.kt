package org.wolmics.soundboardplus.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.gui.overlay.CategoryConfigOverlay
import org.wolmics.soundboardplus.gui.overlay.OverlayContext
import org.wolmics.soundboardplus.gui.overlay.YtDlpOverlay
import org.wolmics.soundboardplus.util.SoundboardSession
import org.lwjgl.glfw.GLFW
import org.wolmics.soundboardplus.config.SoundData
import org.wolmics.soundboardplus.gui.components.CategoryTabWidget
import org.wolmics.soundboardplus.gui.components.ProgressBar
import org.wolmics.soundboardplus.gui.components.ResultListWidget
import org.wolmics.soundboardplus.gui.components.VolumeSlider
import org.wolmics.soundboardplus.gui.overlay.DeleteCategoryOverlay
import java.awt.Color
import java.util.Collections.emptyList

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
    private lateinit var categoryTabWidget: CategoryTabWidget

    private lateinit var detailLocalSlider: VolumeSlider
    private lateinit var detailPlayerSlider: VolumeSlider
    private lateinit var pauseButton: ButtonWidget
    private lateinit var detailBindBtn: ButtonWidget
    private lateinit var detailLabel: TextWidget
    private lateinit var progressBar: ProgressBar

    private lateinit var soundDeleteButton: ButtonWidget
    private lateinit var soundLoopButton: ButtonWidget
    private lateinit var stopAllButton: ButtonWidget

    private val overlayCtx get() = OverlayContext(textRenderer, width, height)
    private lateinit var categoryOverlay: CategoryConfigOverlay
    private lateinit var downloadOverlay: YtDlpOverlay
    private lateinit var deleteConfirmOverlay: DeleteCategoryOverlay

    private var selectedSound: SoundData? = null
    private var selectedCategory: String? = null
    private var isBinding = false
    private var results: List<SoundData> = emptyList()
    private var categoryList: List<String> = emptyList()

    // Sound Sorting stuff
    enum class SortMethod(val label: String) {
        ALPHABETICAL("A–Z ▾"),
        RECENTLY_ADDED("Recent ▾"),
        MOST_PLAYED("Played ▾");

        fun next(): SortMethod = entries[(ordinal + 1) % entries.size]
    }
    private var sortingMethod: SortMethod = SoundboardSession.getSortingMethod() ?: SortMethod.ALPHABETICAL

    // Sound that the user pressed down on — not yet confirmed as a drag
    private var dragCandidate: SoundData? = null
    private var dragStartX = 0
    private var dragStartY = 0
    private var draggingSound: SoundData? = null
    // Current cursor position while dragging
    private var dragX = 0
    private var dragY = 0

    private val dragThreshold = 6

    override fun init() {
        val padding = 10

        val titleWidget = TextWidget(Text.literal("Soundboard+").formatted(Formatting.BOLD), textRenderer)
        Text.literal("_")

        titleWidget.setPosition(width / 2 - titleWidget.width / 2, 5)
        addDrawableChild(titleWidget)

        val btnAreaWidth = 5 + 65 + 5 + 75 + 11
        val searchWidth = width - padding - btnAreaWidth
        queryField = TextFieldWidget(textRenderer, padding, 20, searchWidth, 20, Text.literal("Search"))
        queryField.setChangedListener { scanSounds() }
        addDrawableChild(queryField)

        setupTopButtons()
        setupDetailButtons(padding)

        categoryOverlay = CategoryConfigOverlay(
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

        deleteConfirmOverlay = DeleteCategoryOverlay(
            overlayCtx,
            title = Text.literal("Delete this category?").formatted(Formatting.BOLD),
            onConfirm = { categoryName ->
                SoundboardConfig.deleteCategory(categoryName)
                scanSounds()
            }
        )

        val listTop = headerHeight + 2
        val listBottom = height - bottomPaneHeight
        resultsList = ResultListWidget(
            mc, width - 2 * padding, listBottom - listTop, listTop, 22,
            textRenderer = textRenderer,
            getSelectedSound = { selectedSound },
            getDraggingSound = { draggingSound },
            onSelectSound = { selectSound(it) },
            onEntryMouseDown = { sound, x, y -> onEntryMouseDown(sound, x, y) },
            onSoundListChanged = { scanSounds() },
        )
        resultsList.x = padding
        addDrawableChild(resultsList)

        categoryTabWidget = CategoryTabWidget(
            textRenderer = textRenderer,
            barY = tabBarY,
            barHeight = tabBarH,
            getCategories = { categoryList },
            getSelectedCategory = { selectedCategory },
            getDraggingSound = { draggingSound },
            getDragPosition = { dragX to dragY },
        )

        if (SoundboardConfig.data.saveLastCategory) {
            selectedCategory = SoundboardSession.getSelectedCategory()
            categoryTabWidget.currentPage = SoundboardSession.getSelectedCategoryPage() ?: 0
        } else {
            selectedCategory = null
            categoryTabWidget.currentPage = 0
        }

        scanSounds()
        setInitialFocus(queryField)
    }

    private fun setupTopButtons() {
        val rightEdge = width - 10

        addDrawableChild(
                ButtonWidget.builder(Text.literal(sortingMethod.label)) {
                sortingMethod = sortingMethod.next()
                it.message = Text.literal(sortingMethod.label)
                it.setTooltip(buildSortTooltip())
                it.isFocused = false
                scanSounds()
            }
                .size(50, 20)
                .position(rightEdge - 75 - 18 - 2 - 50 - 2, 20)
                .tooltip(buildSortTooltip())
                .build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Download")) {
                downloadOverlay.setCategory(selectedCategory)
                downloadOverlay.show()
            }
                .size(75, 20)
                .position(rightEdge - 75 - 18 - 2, 20)
                .tooltip(Tooltip.of(Text.literal("Download Sounds")))
                .build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("☰")) { mc.setScreen(SoundboardConfigScreen(this)) }
                .size(20, 20)
                .position(rightEdge - 18, 20)
                .tooltip(Tooltip.of(Text.literal("Edit Config")))
                .build()
        )
    }

    private fun buildSortTooltip(): Tooltip {
        fun line(key: String, label: String, method: SortMethod): Text {
            val active = sortingMethod == method
            val color = if (active) Formatting.GREEN else Formatting.GRAY
            val prefix = if (active) "▸ " else "   "
            return Text.literal("$prefix$key  $label").formatted(color)
        }

        val text = Text.literal("")
            .append(line("1", "Alphabetically", SortMethod.ALPHABETICAL))
            .append(Text.literal("\n"))
            .append(line("2", "Recently Added", SortMethod.RECENTLY_ADDED))
            .append(Text.literal("\n"))
            .append(line("3", "Most Played", SortMethod.MOST_PLAYED))

        return Tooltip.of(text)
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
            if (selectedSound != null) {
                SoundboardAudioSystem.stop(selectedSound!!)
                SoundboardConfig.deleteSound(selectedSound!!.id)
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
            if (selectedSound != null) {
                isBinding = true
                it.message = Text.literal("> Press Key <").formatted(Formatting.YELLOW)
            }
        }.size(80, 20).position(width - 110, detailsY).build()
        detailBindBtn.active = false
        addDrawableChild(detailBindBtn)

        progressBar = ProgressBar(
            x = padding,
            y = detailsY + 25,
            width = width - 2 * padding,
            height = 14,
            getSelectedSound = { selectedSound },
            SoundboardConfig.data.showProgressBar
        )
        addDrawableChild(progressBar)
    }

    // ── Data ────────────────────────────────────────────────────────────────────

    fun scanSounds() {
        SoundboardConfig.refresh()

        categoryList = SoundboardConfig.data.categories

        if (selectedCategory != null && selectedCategory !in SoundboardConfig.data.categories) {
            selectedCategory = null
        }

        val query = queryField.text.trim().lowercase()
        results = buildSoundList(query)
        resultsList.setResults(results)

        if (selectedSound != null && results.none { it == selectedSound }) {
            selectSound(null)
        }
    }

    private fun buildSoundList(query: String): List<SoundData> {
        val base = SoundboardConfig.data.sounds
            .filter { selectedCategory == null || it.category == selectedCategory }
            .filter { it.name.lowercase().contains(query) }

        val comparator = when (sortingMethod) {
            SortMethod.ALPHABETICAL   -> compareBy<SoundData> { it.name.lowercase() }
            SortMethod.MOST_PLAYED    -> compareByDescending<SoundData> { it.playCount }
            SortMethod.RECENTLY_ADDED -> compareByDescending<SoundData> { it.id }
        }

        // Favorites always at the top regardless of active sort
        return base.sortedWith(compareByDescending<SoundData> { it.favorite }.then(comparator))
    }

    fun selectSound(sound: SoundData?) {
        selectedSound = sound
        isBinding = false

        if (sound == null) {
            soundDeleteButton.active = false
            soundLoopButton.active = false

            detailBindBtn.active = false
            detailBindBtn.message = Text.literal("Keybind: -")
        } else {
            detailBindBtn.active = true
            soundDeleteButton.active = true

            resultsList.scrollToSound(sound)

            updateSoundLoopButtonText()
            updateBindButtonText(sound.keybind)
        }
    }

    private fun toggleSoundLoop() {
        if (selectedSound == null) return
        val enabled: Boolean = SoundboardAudioSystem.getSoundRepeat(selectedSound!!.id) ?: return

        SoundboardAudioSystem.setSoundRepeat(selectedSound!!.id, !enabled)
    }

    private fun updateSoundLoopButtonText() {
        if (selectedSound == null) return
        val enabled: Boolean? = SoundboardAudioSystem.getSoundRepeat(selectedSound!!.id)

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
            GLFW.glfwGetKeyName(keyCode, 0) ?: "Unknown"
        else
            "-"
        detailBindBtn.message = Text.literal("Keybind: $keyName")
    }

    private fun updateDetailLabel() {
        val fileName = selectedSound
            ?.takeIf { SoundboardAudioSystem.getProgress(it.id) != null }
            ?.name
            ?: SoundboardAudioSystem.getSinglePlayingId()?.let { SoundboardConfig[it] }?.name

        detailLabel.message = if (fileName == null) {
            Text.literal("- No sound Playing -")
        } else {
            Text.literal(fileName).formatted(Formatting.YELLOW)
        }
        detailLabel.x = width / 2 - textRenderer.getWidth(detailLabel.message) / 2
    }

    override fun charTyped(input: CharInput): Boolean {
        if (categoryOverlay.charTyped(input)) return true
        if (downloadOverlay.charTyped(input)) return true
        if (deleteConfirmOverlay.charTyped(input)) return true
        return super.charTyped(input)
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val overlayOpen = categoryOverlay.isVisible || downloadOverlay.isVisible || deleteConfirmOverlay.isVisible
        val mx = if (overlayOpen) -1 else mouseX
        val my = if (overlayOpen) -1 else mouseY

        pauseButton.message = if (SoundboardAudioSystem.playbackActive()) Text.literal("⏸") else Text.literal("⏵")
        stopAllButton.active = !SoundboardAudioSystem.activeSoundsEmpty()
        updateDetailLabel()

        super.render(context, mx, my, delta)
        categoryTabWidget.render(context, mx, my, 10, width - 10)
        progressBar.tick()

        context.fill(10, headerHeight, width - 10, headerHeight + 1, Color.GRAY.rgb)
        context.fill(10, height - bottomPaneHeight, width - 10, height - bottomPaneHeight + 1, Color.GRAY.rgb)

        categoryOverlay.render(context, mouseX, mouseY, delta)
        downloadOverlay.render(context, mouseX, mouseY, delta)
        deleteConfirmOverlay.render(context, mouseX, mouseY, delta)

        // Drag ghost
        if (draggingSound != null) {
            renderDragGhost(context, dragX, dragY)
        }
    }

    //Renders a small floating label at the cursor showing the dragged filename.
    private fun renderDragGhost(context: DrawContext, x: Int, y: Int) {
        val sound = draggingSound ?: return
        var label = sound.name + ".mp3"
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
        context.fill(gx,         gy,         gx + w,     gy + 1,     0xFF55E879.toInt()) // top
        context.fill(gx,         gy + h - 1, gx + w,     gy + h,     0xFF55E879.toInt()) // bottom
        context.fill(gx,         gy + 1,     gx + 1,     gy + h - 1, 0xFF55E879.toInt()) // left
        context.fill(gx + w - 1, gy + 1,     gx + w,     gy + h - 1, 0xFF55E879.toInt()) // right

        context.drawText(textRenderer, label, gx + padH, gy + padV, 0xFFFFFFFF.toInt(), false)

        // Arrow hint toward the tab bar if the cursor is below it
        if (dragY > tabBarY + tabBarH + 40) {
            val hint = "↑ drop on a category"
            val hw = textRenderer.getWidth(hint)
            context.drawText(textRenderer, hint, gx + w / 2 - hw / 2, gy - textRenderer.fontHeight - 2, 0xAAFFFFFF.toInt(), false)
        }
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (categoryOverlay.mouseClicked(click, doubled)) return true
        if (downloadOverlay.mouseClicked(click, doubled)) return true
        if (deleteConfirmOverlay.mouseClicked(click, doubled)) return true

        // Cancel any pending drag on right-click
        if (click.button() == 1) {
            cancelDrag()
        }

        when (val tabAction = categoryTabWidget.mouseClicked(click.x.toInt(), click.y.toInt())) {
            CategoryTabWidget.Action.AddCategory -> {
                categoryOverlay.show()
                return true
            }
            is CategoryTabWidget.Action.SelectCategory -> {
                setFocused(false) // Remove focus from queryField
                if (selectedCategory != tabAction.category) {
                    selectedCategory = tabAction.category
                    scanSounds()
                }
                return true
            }
            CategoryTabWidget.Action.Handled -> return true
            null -> {}
        }

        return super.mouseClicked(click, doubled)
    }

    fun onEntryMouseDown(sound: SoundData, mouseX: Int, mouseY: Int) {
        dragCandidate = sound
        dragStartX = mouseX
        dragStartY = mouseY
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        // Activate drag once the cursor has moved past the threshold

        if (dragCandidate != null && draggingSound == null) {
            val dx = click.x - dragStartX
            val dy = click.y - dragStartY
            if (dx * dx + dy * dy >= dragThreshold * dragThreshold) {
                draggingSound = dragCandidate
            }
        }

        if (draggingSound != null) {
            dragX = click.x.toInt()
            dragY = click.y.toInt()
            return true
        }

        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        val droppedSound = draggingSound

        if (droppedSound != null) {
            // Find the tab the cursor is over (ignore scroll arrows / the "+" adder)
            val targetTab = categoryTabWidget.categoryTabAt(click.x.toInt(), click.y.toInt())

            if (targetTab != null) {
                SoundboardConfig.changeSoundCategory(droppedSound, targetTab.category)
                scanSounds()
            }

            cancelDrag()
            return true
        }

        cancelDrag()
        return super.mouseReleased(click)
    }

    private fun cancelDrag() {
        draggingSound = null
        dragCandidate = null
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (categoryOverlay.keyPressed(input)) return true
        if (downloadOverlay.keyPressed(input)) return true
        if (deleteConfirmOverlay.keyPressed(input)) return true

        // ESC cancels an active drag instead of closing the screen
        if (input.key == GLFW.GLFW_KEY_ESCAPE && draggingSound != null) {
            cancelDrag()
            return true
        }

        if (input.key == GLFW.GLFW_KEY_F5) {
            scanSounds()
            return true
        }

        if (input.key == GLFW.GLFW_KEY_BACKSPACE && selectedSound == null && selectedCategory != null && !queryField.isFocused) {
            deleteConfirmOverlay.setCategory(selectedCategory!!)
            deleteConfirmOverlay.show()
            return true
        }

        if (input.key == GLFW.GLFW_KEY_SPACE && selectedSound != null && !queryField.isFocused) {
            SoundboardAudioSystem.cyclePlaySound(selectedSound!!)
            return true
        }

        // Select Sound with arrow keys
        if (input.key == GLFW.GLFW_KEY_DOWN || input.key == GLFW.GLFW_KEY_UP) {
            val direction = if (input.key == GLFW.GLFW_KEY_DOWN) 1 else -1
            val currentIndex = selectedSound?.let { results.indexOf(it) } ?: -direction
            val nextIndex = (currentIndex + direction).coerceIn(0, results.size - 1)
            selectSound(results[nextIndex])
            return true
        }

        // Hotkey Binding
        if (isBinding && selectedSound != null) {
            selectedSound!!.keybind = if (input.isEscape) -1 else input.key
            SoundboardConfig.save()
            updateBindButtonText(selectedSound!!.keybind)
            isBinding = false
            return true
        }
        if (input.isEscape) { close(); return true }

        // If an entry is currently being renamed, it owns the keystroke.
        val renamingEntry = resultsList.renamingEntry()
        if (renamingEntry != null) {
            return renamingEntry.keyPressed(input)
        }

        // Otherwise the search field should have focus and take the key.
        if (!queryField.isFocused) {
            setFocused(queryField)
        }
        return queryField.keyPressed(input)
    }

    override fun close() {
        if (SoundboardConfig.data.saveLastCategory) {
            SoundboardSession.setSelectedCategory(selectedCategory)
            SoundboardSession.setSelectedCategoryPage(categoryTabWidget.currentPage)
        }
        SoundboardSession.setSortingMethod(sortingMethod)
        SoundboardConfig.save()
        mc.setScreen(parent)
    }
}