package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.wolmics.soundboardplus.SimpleSoundboardClient
import org.wolmics.soundboardplus.YtDlpManager
import org.wolmics.soundboardplus.util.ToastManager
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CompletableFuture

class YtDlpOverlay(ctx: OverlayContext, title: Component) : ScreenOverlay(ctx, overlayWidth = 200, overlayHeight = 90) {
    private val titleLabel = StringWidget(title, font)
    private var category: String? = null

    private var urlField = EditBox(
        font,
        x + 15,
        y + 26,
        overlayWidth - 30,  // 170px, padded 15px on each side
        16,
        Component.literal("YouTube / media URL")
    )

    private var downloadButton = Button.builder(Component.literal("Download")) {
        val url = urlField.value.trim()
        if (url.isBlank()) {
            ToastManager.createToast(Component.literal("Please enter a URL!"), 1500L)
            return@builder
        }

        startDownload(url)
    }.size(120, 20).pos(x + overlayWidth / 2 - 60, y + 55).build()

    private var openFolderButton = Button.builder(Component.literal("\uD83D\uDCC2")) {
        Util.getPlatform().openFile(SimpleSoundboardClient.soundDir)
    }.size(20, 20).pos(screenWidth / 2 + 70, y + 55).build()

    init {
        urlField.setMaxLength(256)
        urlField.setSuggestion("Paste YouTube / media URL")
    }

    private fun startDownload(url: String) {

        CompletableFuture.runAsync {YtDlpManager().downloadUrlIntoSoundDir(url, category)}
        hide()
    }

    fun setCategory(category: String?) {
        this.category = category
    }

    override fun onShow() {
        urlField.value = ""
        urlField.isFocused = true
    }

    override fun onHide() {
        urlField.isFocused = false
    }

    override fun onMouseClicked(click: MouseButtonEvent, double: Boolean) {
        downloadButton.mouseClicked(click, double)
        urlField.mouseClicked(click, double)
        openFolderButton.mouseClicked(click, double)
    }

    override fun onKeyPressed(input: KeyEvent): Boolean {
        if (input.key == GLFW.GLFW_KEY_ENTER) {
            hide()
            return true
        }
        return urlField.keyPressed(input)
    }

    override fun onCharTyped(input: CharacterEvent): Boolean {
        return urlField.charTyped(input)
    }

    override fun renderContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Title
        titleLabel.setPosition(screenWidth / 2 - 50, y + 8)
        titleLabel.extractRenderState(graphics, mouseX, mouseY, delta)
        // URL input
        if (urlField.value.isEmpty()) {
            urlField.setSuggestion("Paste YouTube / media URL")
        } else {
            urlField.setSuggestion("")
        }

        urlField.extractRenderState(graphics, mouseX, mouseY, delta)
        downloadButton.extractRenderState(graphics, mouseX, mouseY, delta)
        openFolderButton.extractRenderState(graphics, mouseX, mouseY, delta)
    }
}