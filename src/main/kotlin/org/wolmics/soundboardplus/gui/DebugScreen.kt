package org.wolmics.soundboardplus.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import org.wolmics.soundboardplus.YtDlpManager
import java.util.concurrent.CompletableFuture
import org.lwjgl.util.tinyfd.TinyFileDialogs
import org.wolmics.soundboardplus.AudioDecoder
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.util.ToastRenderer

class DebugScreen(private val parent: Screen?) : Screen(Text.literal("Debug Screen")) {

    private val panelWidth = 130
    private val panelGap = 15
    private val buttonWidth = 110
    private val buttonHeight = 20
    private val buttonGap = 4
    private val headerHeight = 40   // title + subtitle + divider
    private val dangerGap = 6       // extra breathing room before a destructive action

    private val colorBackground = 0x80404040.toInt()
    private val colorOutline = 0xFFAAAAAA.toInt()
    private val colorDivider = 0xFFAAAAAA.toInt()

    private val ytDlpManager = YtDlpManager()

    private var currentAudioDecoder: AudioDecoder = SoundboardAudioSystem.getAudioDecoder()

    private data class Panel(
        val x: Int,
        val y: Int,
        val height: Int,
        val dangerDividerY: Int?
    )

    private data class ButtonSpec(
        val label: String,
        val danger: Boolean = false,
        val tooltip: Tooltip? = null,
        val action: (ButtonWidget) -> Unit
    )

    private val panels = mutableListOf<Panel>()

    override fun init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Back")) { close() }
            .position(width / 2 - 100, height - 30)
            .size(200, 20)
            .build()
        )

        panels.clear()
        val totalWidth = 3 * panelWidth + 2 * panelGap
        val startX = width / 2 - totalWidth / 2
        val columnX = IntArray(3) { startX + it * (panelWidth + panelGap) }
        val panelY = 50

        buildPanel(
            title = "yt-dlp",
            subtitle = "Video downloader",
            x = columnX[0], y = panelY,
            buttons = listOf(
                ButtonSpec("Update") { onYtDlpUpdate() },
                ButtonSpec("Update to Nightly") { onYtDlpUpdateNightly() },
                ButtonSpec("Open logs") { onYtDlpOpenLogs() },
                ButtonSpec("Set custom path") { onYtDlpSetCustomPath() },
                ButtonSpec("Uninstall", danger = true) { onYtDlpUninstall() }
            )
        )

        buildPanel(
            title = "ffmpeg",
            subtitle = "Audio converter",
            x = columnX[1], y = panelY,
            buttons = listOf(
                ButtonSpec("Update") { onFfmpegUpdate() },
                ButtonSpec("Set custom path") { onFfmpegSetCustomPath() },
                ButtonSpec("Uninstall", danger = true) { onFfmpegUninstall() }
            )
        )

        buildPanel(
            title = "Audio Decoder",
            subtitle = "Decoding backend",
            x = columnX[2], y = panelY,
            buttons = listOf(
                ButtonSpec(
                    label = currentAudioDecoder.displayName,
                    tooltip = decoderTooltip
                ) { btn -> cycleAudioDecoder(btn) }
            )
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        for (panel in panels) drawPanelBackground(context, panel)
        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawPanelBackground(context: DrawContext, panel: Panel) {
        context.fill(panel.x, panel.y, panel.x + panelWidth, panel.y + panel.height, colorBackground)
        context.drawStrokedRectangle(panel.x, panel.y, panelWidth, panel.height, colorOutline)

        // Divider under the header
        context.fill(
            panel.x + 4, panel.y + headerHeight - 4,
            panel.x + panelWidth - 4, panel.y + headerHeight - 3,
            colorDivider
        )

        // Divider that separates the destructive action from the rest
        panel.dangerDividerY?.let { y ->
            context.fill(panel.x + 4, y, panel.x + panelWidth - 4, y + 1, colorDivider)
        }
    }

    private fun buildPanel(title: String, subtitle: String, x: Int, y: Int, buttons: List<ButtonSpec>) {
        val titleWidget = TextWidget(Text.literal(title).formatted(Formatting.BOLD), textRenderer)
        titleWidget.setPosition(x + panelWidth / 2 - titleWidget.width / 2, y + 6)
        addDrawableChild(titleWidget)

        val subtitleWidget = TextWidget(
            Text.literal(subtitle).formatted(Formatting.GRAY, Formatting.ITALIC),
            textRenderer
        )
        subtitleWidget.setPosition(x + panelWidth / 2 - subtitleWidget.width / 2, y + 18)
        addDrawableChild(subtitleWidget)

        var dangerDividerY: Int? = null
        var buttonY = y + headerHeight
        var lastButton: ButtonWidget? = null

        for (spec in buttons) {
            if (spec.danger && dangerDividerY == null) {
                buttonY += dangerGap
                dangerDividerY = buttonY - dangerGap / 2 - 1
            }

            val label = if (spec.danger) {
                Text.literal(spec.label).formatted(Formatting.RED)
            } else {
                Text.literal(spec.label)
            }

            val button = ButtonWidget.builder(label) { btn -> spec.action(btn) }
                .position(x + (panelWidth - buttonWidth) / 2, buttonY)
                .size(buttonWidth, buttonHeight)
                .tooltip(spec.tooltip)
                .build()

            addDrawableChild(button)
            lastButton = button

            buttonY += buttonHeight + buttonGap
        }

        val panelHeight = (lastButton?.let { it.y + it.height } ?: (y + headerHeight)) - y + 5
        panels += Panel(x, y, panelHeight, dangerDividerY)
    }

    override fun close() {
        MinecraftClient.getInstance().setScreen(parent)
    }

    // --- ffmpeg actions ---
    private fun onFfmpegUpdate() {
        ToastRenderer.showText("Updating FFmpeg...", 3000)
        CompletableFuture.runAsync {
            val success = ytDlpManager.updateFfmpeg()
            if (success) {
                ToastRenderer.showText("FFmpeg updated successfully!", 3000)
            } else {
                ToastRenderer.showText("Failed to update FFmpeg...", 3000)
            }
        }
    }

    private fun onFfmpegUninstall() {
        ToastRenderer.showText("Uninstalling FFmpeg...", 3000)
        CompletableFuture.runAsync {
            val success = ytDlpManager.uninstallFfmpeg()
            if (success) {
                ToastRenderer.showText("FFmpeg uninstalled successfully!", 3000)
            } else {
                ToastRenderer.showText("Failed to uninstall FFmpeg...", 3000)
            }
        }
    }

    private fun onFfmpegSetCustomPath() {
        browseAndSetPath("Select ffmpeg executable") { path ->
            val valid = ytDlpManager.setFfmpegPath(path)
            val message = if (valid) {
                Text.literal("FFmpeg path set successfully!").formatted(Formatting.GREEN)
            } else {
                Text.literal("Invalid FFmpeg path.").formatted(Formatting.RED)
            }

            ToastRenderer.showText(message, 3000)
            return@browseAndSetPath true
        }
    }

    // --- yt-dlp actions ---
    private fun onYtDlpUpdate() {
        ToastRenderer.showText("Updating yt-dlp...", 3000)
        CompletableFuture.runAsync {
            val success = ytDlpManager.updateYtDlp()
            if (success) {
                ToastRenderer.showText("yt-dlp updated successfully!", 3000)
            } else {
                ToastRenderer.showText("Failed to update yt-dlp...", 3000)
            }
        }
    }

    private fun onYtDlpUpdateNightly() {
        ToastRenderer.showText("Updating yt-dlp to nightly...", 3000)
        CompletableFuture.runAsync {
            val success = ytDlpManager.updateYtDlpNightly()
            if (success) {
                ToastRenderer.showText("yt-dlp nightly updated successfully!", 3000)
            } else {
                ToastRenderer.showText("Failed to update yt-dlp nightly...", 3000)
            }
        }
    }

    private fun onYtDlpUninstall() {
        ToastRenderer.showText("Uninstalling yt-dlp...", 3000)
        CompletableFuture.runAsync {
            val success = ytDlpManager.uninstallYtDlp()
            if (success) {
                ToastRenderer.showText("yt-dlp uninstalled successfully!", 3000)
            } else {
                ToastRenderer.showText("Failed to uninstall yt-dlp...", 3000)
            }
        }
    }

    private fun onYtDlpOpenLogs() {
        Util.getOperatingSystem().open(ytDlpManager.logFile)
        ToastRenderer.showText("Opened yt-dlp log file.", 3000)
    }

    private fun onYtDlpSetCustomPath() {
        browseAndSetPath("Select yt-dlp executable") { path ->
            val valid = ytDlpManager.setYtDlpPath(path)
            val message = if (valid) {
                Text.literal("yt-dlp path set successfully!").formatted(Formatting.GREEN)
            } else {
                Text.literal("Invalid yt-dlp path").formatted(Formatting.RED)
            }
            ToastRenderer.showText(message, 3000)
            return@browseAndSetPath true
        }
    }

    private fun browseAndSetPath(dialogTitle: String, setPath: (String) -> Boolean) {
        CompletableFuture.supplyAsync {
            // tinyfd starts the file selecting process
            TinyFileDialogs.tinyfd_openFileDialog(dialogTitle, null, null, null, false)
        }.thenAccept { selected ->
            if (selected == null) return@thenAccept // user cancelled the dialog

            val valid = setPath(selected)
            val message = if (valid) {
                Text.literal("Path set successfully!").formatted(Formatting.GREEN)
            } else {
                Text.literal("That path is invalid.").formatted(Formatting.RED)
            }
            ToastRenderer.showText(message, 3000)
        }
    }
    // --- Audio Decoder actions ---
    private val decoderTooltip: Tooltip = Tooltip.of(
        Text.literal("Automatic")
            .formatted(Formatting.GREEN)
            .append(Text.literal(": Automatically chooses between Simple Voice Chat Decoder and JLayer.")
                .formatted(Formatting.GRAY))
            .append(Text.literal("\n\n"))
            .append(Text.literal("Simple Voice Chat Decoder")
                .formatted(Formatting.GREEN))
            .append(Text.literal(": Decodes the whole file at once. Can lead to longer loading times on large files and higher RAM usage.")
                .formatted(Formatting.GRAY))
            .append(Text.literal("\n\n"))
            .append(Text.literal("JLayer")
                .formatted(Formatting.GREEN))
            .append(Text.literal(": Streams the audio from file. Best for large files or if you have issues with Simple Voice Chat Decoder.")
                .formatted(Formatting.GRAY))
    )

    private fun cycleAudioDecoder(button: ButtonWidget) {
        val values = AudioDecoder.entries.toTypedArray()
        currentAudioDecoder = values[(currentAudioDecoder.ordinal + 1) % values.size]
        button.message = Text.literal(currentAudioDecoder.displayName)

        SoundboardAudioSystem.setAudioDecoder(currentAudioDecoder)
        SoundboardConfig.data.selectedAudioDecoder = currentAudioDecoder
    }
}