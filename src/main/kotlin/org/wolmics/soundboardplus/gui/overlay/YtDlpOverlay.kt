package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.wolmics.soundboardplus.SimpleSoundboardClient
import org.wolmics.soundboardplus.YtDlpManager
import org.wolmics.soundboardplus.util.ToastManager
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CompletableFuture

class YtDlpOverlay(ctx: OverlayContext, title: Text) : ScreenOverlay(ctx, overlayWidth = 200, overlayHeight = 90) {
    private val titleLabel = TextWidget(title, textRenderer)
    private var category: String? = null

    private var urlField = TextFieldWidget(
        textRenderer,
        x + 15,
        y + 26,
        overlayWidth - 30,  // 170px, padded 15px on each side
        16,
        Text.literal("YouTube / media URL")
    )

    private var downloadButton = ButtonWidget.builder(Text.literal("Download")) {
        val url = urlField.text.trim()
        if (url.isBlank()) {
            ToastManager.createToast(Text.literal("Please enter a URL!"), 1500L)
            return@builder
        }

        startDownload(url)
    }.size(120, 20).position(x + overlayWidth / 2 - 60, y + 55).build()

    private var openFolderButton = ButtonWidget.builder(Text.literal("\uD83D\uDCC2")) {
        Util.getOperatingSystem().open(SimpleSoundboardClient.soundDir)
    }.size(20, 20).position(screenWidth / 2 + 70, y + 55).build()

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
        urlField.text = ""
        urlField.isFocused = true
    }

    override fun onHide() {
        urlField.isFocused = false
    }

    override fun onMouseClicked(click: Click, double: Boolean) {
        downloadButton.mouseClicked(click, double)
        urlField.mouseClicked(click, double)
        openFolderButton.mouseClicked(click, double)
    }

    override fun onKeyPressed(input: KeyInput): Boolean {
        if (input.key == GLFW.GLFW_KEY_ENTER) {
            hide()
            return true
        }
        return urlField.keyPressed(input)
    }

    override fun onCharTyped(input: CharInput): Boolean {
        return urlField.charTyped(input)
    }

    override fun renderContent(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Title
        titleLabel.setPosition(screenWidth / 2 - 50, y + 8)
        titleLabel.render(context, mouseX, mouseY, delta)
        // URL input
        if (urlField.text == "") {
            urlField.setSuggestion("Paste YouTube / media URL")
        } else {
            urlField.setSuggestion("")
        }

        urlField.render(context, mouseX, mouseY, delta)
        downloadButton.render(context, mouseX, mouseY, delta)
        openFolderButton.render(context, mouseX, mouseY, delta)
    }
}