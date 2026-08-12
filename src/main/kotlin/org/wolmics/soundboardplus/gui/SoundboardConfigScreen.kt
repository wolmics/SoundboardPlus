package org.wolmics.soundboardplus.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.wolmics.soundboardplus.config.SoundboardConfig
import java.net.URI

class SoundboardConfigScreen(private val parent: Screen?) : Screen(Component.literal("Soundboard Configuration")) {
    private val originalAuthorText: String = "Originally created by 0x1bd"
    private val forkAuthorText: String = "Modified and maintained by Wolmics"

    private val originalAuthorGithubUrl: String = "https://github.com/0x1bd/SimpleSoundboard"
    private val forkAuthorGithubUrl: String = "https://github.com/wolmics/SoundboardPlus"

    private var redoRender: Boolean = false

    override fun init() {
        renderButtons()
        renderAuthor()

        addRenderableWidget(
            Button.builder(Component.literal("Debug")) { Minecraft.getInstance().setScreen(DebugScreen(this)) }
                .pos(width - 90, 10)
                .size(80, 20)
                .build()
        )
    }

    // Init Widgets
    private fun renderButtons() {
        var y = 50

        addRenderableWidget(Button.builder(Component.literal("Back")) { onClose() }
            .pos(width / 2 - 100, height - 30)
            .size(200, 20)
            .build()
        )

        addRenderableWidget(
            CycleButton.onOffBuilder(SoundboardConfig.data.playWhileMuted)
                .create(width / 2 - 100, y, 200, 20, Component.literal("Play While Muted")) { _, value ->
                    SoundboardConfig.data.playWhileMuted = value
                    SoundboardConfig.save()
                }
        )
        y += 25

        addRenderableWidget(
            CycleButton.onOffBuilder(SoundboardConfig.data.playOnlyOne)
               .create(width / 2 - 100, y, 200, 20, Component.literal("No overlapping sounds")) { _, value ->
                    SoundboardConfig.data.playOnlyOne = value
                    SoundboardConfig.save()
                }
        )
        y += 25

        addRenderableWidget(
            CycleButton.onOffBuilder(SoundboardConfig.data.showProgressBar)
                .create(width / 2 - 100, y, 200, 20, Component.literal("Show Progress Bar")) { _, value ->
                    SoundboardConfig.data.showProgressBar = value
                    SoundboardConfig.save()

                    redoRender = true
                }
        )
        y += 25

        addRenderableWidget(
            CycleButton.onOffBuilder(SoundboardConfig.data.saveLastCategory)
                .create(width / 2 - 100, y, 200, 20, Component.literal("Save Last Category & Page")) { _, value ->
                    SoundboardConfig.data.saveLastCategory = value
                    SoundboardConfig.save()
                }
        )
    }

    private fun renderAuthor() {
        val originalAuthor = StringWidget(Component.literal(originalAuthorText), font)
        originalAuthor.setPosition(10, height - 35)
        addRenderableWidget(originalAuthor)

        val forkAuthor = StringWidget(Component.literal(forkAuthorText), font)
        forkAuthor.setPosition(10, height - 20)
        addRenderableWidget(forkAuthor)
    }

    override fun mouseClicked(click: MouseButtonEvent, double: Boolean): Boolean {
        if (click.button() == 0) { // left click only
            val origWidth = font.width(originalAuthorText)
            if (click.x in 10.0..(10.0 + origWidth) && click.y in (height - 35.0)..(height - 25.0)) {
                Util.getPlatform().openUri(URI(originalAuthorGithubUrl))
            }

            val forkWidth = font.width(forkAuthorText)
            if (click.x in 10.0..(10.0 + forkWidth) && click.y in (height - 20.0)..(height - 10.0)) {
                Util.getPlatform().openUri(URI(forkAuthorGithubUrl))
            }
        }
        return super.mouseClicked(click, double)
    }

    override fun onClose() {
        if (redoRender) { // Some Settings might change the look of SoundboardScreen -> redo Render
            minecraft.setScreen(SoundboardScreen(null))
        } else {
            minecraft.setScreen(parent)
        }
    }
}