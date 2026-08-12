package org.wolmics.soundboardplus.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.util.Util
import org.wolmics.soundboardplus.YtDlpManager
import org.wolmics.soundboardplus.util.ToastManager
import java.util.concurrent.CompletableFuture
import org.lwjgl.util.tinyfd.TinyFileDialogs

class DebugScreen(private val parent: Screen?) : Screen(Component.literal("Debug Screen")) {

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

    private data class Panel(
        val x: Int,
        val y: Int,
        val height: Int,
        val dangerDividerY: Int?
    )

    private data class ButtonSpec(
        val label: String,
        val danger: Boolean = false,
        val action: (Button) -> Unit
    )

    private val panels = mutableListOf<Panel>()

    override fun init() {
        addRenderableWidget(Button.builder(Component.literal("Back")) { onClose() }
            .pos(width / 2 - 100, height - 30)
            .size(200, 20)
            .build()
        )

        panels.clear()

        val ffmpegButtons = listOf(
            ButtonSpec("Update") { onFfmpegUpdate() },
            ButtonSpec("Set custom path") { onFfmpegSetCustomPath() },
            ButtonSpec("Uninstall", danger = true) { onFfmpegUninstall() }
        )
        buildPanel(
            title = "ffmpeg",
            subtitle = "Audio converter",
            x = width / 2 - panelWidth / 2 + panelWidth / 2 + panelGap, y = 50,
            buttons = ffmpegButtons
        )

        val ytDlpButtons = listOf(
            ButtonSpec("Update") { onYtDlpUpdate() },
            ButtonSpec("Update to Nightly") { onYtDlpUpdateNightly() },
            ButtonSpec("Open logs") { onYtDlpOpenLogs() },
            ButtonSpec("Set custom path") { onYtDlpSetCustomPath() },
            ButtonSpec("Uninstall", danger = true) { onYtDlpUninstall() }
        )
        buildPanel(
            title = "yt-dlp",
            subtitle = "Video downloader",
            x = width / 2 - panelWidth / 2 - panelWidth / 2 - panelGap, y = 50,
            buttons = ytDlpButtons
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        for (panel in panels) drawPanelBackground(graphics, panel)
        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    private fun drawPanelBackground(graphics: GuiGraphicsExtractor, panel: Panel) {
        graphics.fill(panel.x, panel.y, panel.x + panelWidth, panel.y + panel.height, colorBackground)
        graphics.outline(panel.x, panel.y, panelWidth, panel.height, colorOutline)

        // Divider under the header
        graphics.fill(
            panel.x + 4, panel.y + headerHeight - 4,
            panel.x + panelWidth - 4, panel.y + headerHeight - 3,
            colorDivider
        )

        // Divider that separates the destructive action from the rest
        panel.dangerDividerY?.let { y ->
            graphics.fill(panel.x + 4, y, panel.x + panelWidth - 4, y + 1, colorDivider)
        }
    }

    private fun buildPanel(title: String, subtitle: String, x: Int, y: Int, buttons: List<ButtonSpec>) {
        val titleWidget = StringWidget(Component.literal(title).withStyle(ChatFormatting.BOLD), font)
        titleWidget.setPosition(x + panelWidth / 2 - titleWidget.width / 2, y + 6)
        addRenderableWidget(titleWidget)

        val subtitleWidget = StringWidget(
            Component.literal(subtitle).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
            font
        )
        subtitleWidget.setPosition(x + panelWidth / 2 - subtitleWidget.width / 2, y + 18)
        addRenderableWidget(subtitleWidget)

        var dangerDividerY: Int? = null
        var buttonY = y + headerHeight
        var lastButton: Button? = null

        for (spec in buttons) {
            if (spec.danger && dangerDividerY == null) {
                buttonY += dangerGap
                dangerDividerY = buttonY - dangerGap / 2 - 1
            }

            val label = if (spec.danger) {
                Component.literal(spec.label).withStyle(ChatFormatting.RED)
            } else {
                Component.literal(spec.label)
            }

            val button = Button.builder(label) { btn -> spec.action(btn) }
                .pos(x + (panelWidth - buttonWidth) / 2, buttonY)
                .size(buttonWidth, buttonHeight)
                .build()
            addRenderableWidget(button)
            lastButton = button

            buttonY += buttonHeight + buttonGap
        }

        val panelHeight = (lastButton?.let { it.y + it.height } ?: (y + headerHeight)) - y + 5
        panels += Panel(x, y, panelHeight, dangerDividerY)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    // --- ffmpeg actions ---
    private fun onFfmpegUpdate() {
        minecraft.execute {
            ToastManager.createToast(Component.literal("Updating FFmpeg..."), 3000)
            CompletableFuture.runAsync {
                val success = ytDlpManager.updateFfmpeg()
                minecraft.execute {
                    if (success) {
                        ToastManager.createToast(Component.literal("FFmpeg updated successfully"), 3000)
                    } else {
                        ToastManager.createToast(Component.literal("Failed to update FFmpeg"), 3000)
                    }
                }
            }
        }
    }

    private fun onFfmpegUninstall() {
        minecraft.execute {
            ToastManager.createToast(Component.literal("Uninstalling FFmpeg..."), 3000)
            CompletableFuture.runAsync {
                val success = ytDlpManager.uninstallFfmpeg()
                minecraft.execute {
                    if (success) {
                        ToastManager.createToast(Component.literal("FFmpeg uninstalled successfully"), 3000)
                    } else {
                        ToastManager.createToast(Component.literal("Failed to uninstall FFmpeg"), 3000)
                    }
                }
            }
        }
    }

    private fun onFfmpegSetCustomPath() {
        browseAndSetPath("Select ffmpeg executable") { path ->
            val valid = ytDlpManager.setFfmpegPath(path)
            val message = if (valid) {
                Component.literal("FFmpeg path set successfully!").withColor(TextColor.GREEN)
            } else {
                Component.literal("Invalid FFmpeg path").withColor(TextColor.RED)
            }
            ToastManager.createToast(message, 3000)
            return@browseAndSetPath true
        }
    }

    // --- yt-dlp actions ---
    private fun onYtDlpUpdate() {
        minecraft.execute {
            ToastManager.createToast(Component.literal("Updating yt-dlp..."), 3000)
            CompletableFuture.runAsync {
                val success = ytDlpManager.updateYtDlp()
                minecraft.execute {
                    if (success) {
                        ToastManager.createToast(Component.literal("yt-dlp updated successfully"), 3000)
                    } else {
                        ToastManager.createToast(Component.literal("Failed to update yt-dlp"), 3000)
                    }
                }
            }
        }
    }

    private fun onYtDlpUpdateNightly() {
        minecraft.execute {
            ToastManager.createToast(Component.literal("Updating yt-dlp to nightly..."), 3000)
            CompletableFuture.runAsync {
                val success = ytDlpManager.updateYtDlpNightly()
                minecraft.execute {
                    if (success) {
                        ToastManager.createToast(Component.literal("yt-dlp nightly updated successfully"), 3000)
                    } else {
                        ToastManager.createToast(Component.literal("Failed to update yt-dlp nightly"), 3000)
                    }
                }
            }
        }
    }

    private fun onYtDlpUninstall() {
        minecraft.execute {
            ToastManager.createToast(Component.literal("Uninstalling yt-dlp..."), 3000)
            CompletableFuture.runAsync {
                val success = ytDlpManager.uninstallYtDlp()
                minecraft.execute {
                    if (success) {
                        ToastManager.createToast(Component.literal("yt-dlp uninstalled successfully"), 3000)
                    } else {
                        ToastManager.createToast(Component.literal("Failed to uninstall yt-dlp"), 3000)
                    }
                }
            }
        }
    }

    private fun onYtDlpOpenLogs() {
        minecraft.execute {
            Util.getPlatform().openFile(ytDlpManager.logFile)
            ToastManager.createToast(Component.literal("Opened yt-dlp log file"), 3000)
        }
    }

    private fun onYtDlpSetCustomPath() {
        browseAndSetPath("Select yt-dlp executable") { path ->
            val valid = ytDlpManager.setYtDlpPath(path)
            val message = if (valid) {
                Component.literal("yt-dlp path set successfully!").withColor(TextColor.GREEN)
            } else {
                Component.literal("Invalid yt-dlp path").withColor(TextColor.RED)
            }
            ToastManager.createToast(message, 3000)
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
            minecraft.execute {
                val message = if (valid) {
                    Component.literal("Path set successfully!").withColor(TextColor.GREEN)
                } else {
                    Component.literal("That path is invalid.").withColor(TextColor.RED)
                }
                ToastManager.createToast(message, 3000)
            }
        }
    }
}