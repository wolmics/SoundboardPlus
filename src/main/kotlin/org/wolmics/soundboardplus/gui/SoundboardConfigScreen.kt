package org.wolmics.soundboardplus.gui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CyclingButtonWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.wolmics.soundboardplus.config.SoundboardConfig
import java.net.URI

class SoundboardConfigScreen(private val parent: Screen?) : Screen(Text.literal("Soundboard Configuration")) {
    private val originalAuthorText: String = "Originally created by 0x1bd"
    private val forkAuthorText: String = "Modified and maintained by Wolmics"

    private val originalAuthorGithubUrl: String = "https://github.com/0x1bd/SimpleSoundboard"
    private val forkAuthorGithubUrl: String = "https://github.com/wolmics/SoundboardPlus"

    private var redoRender: Boolean = false

    override fun init() {
        renderButtons()
        renderAuthor()

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Debug")) { MinecraftClient.getInstance().setScreen(DebugScreen(this)) }
                .position(width - 90, 10)
                .size(80, 20)
                .build()
        )
    }

    // Init Widgets
    private fun renderButtons() {
        var y = 50

        addDrawableChild(ButtonWidget.builder(Text.literal("Back")) { close() }
            .position(width / 2 - 100, height - 30)
            .size(200, 20)
            .build()
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playWhileMuted)
                .build(width / 2 - 100, y, 200, 20, Text.literal("Play While Muted")) { _, value ->
                    SoundboardConfig.data.playWhileMuted = value
                    SoundboardConfig.save()
                }
        )
        y += 25

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playOnlyOne)
               .build(width / 2 - 100, y, 200, 20, Text.literal("No overlapping sounds")) { _, value ->
                    SoundboardConfig.data.playOnlyOne = value
                    SoundboardConfig.save()
                }
        )
        y += 25

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.showProgressBar)
                .build(width / 2 - 100, y, 200, 20, Text.literal("Show Progress Bar")) { _, value ->
                    SoundboardConfig.data.showProgressBar = value
                    SoundboardConfig.save()

                    redoRender = true
                }
        )
        y += 25

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.saveLastCategory)
                .build(width / 2 - 100, y, 200, 20, Text.literal("Save Last Category & Page")) { _, value ->
                    SoundboardConfig.data.saveLastCategory = value
                    SoundboardConfig.save()
                }
        )
    }

    private fun renderAuthor() {
        val originalAuthor = TextWidget(Text.literal(originalAuthorText), textRenderer)
        originalAuthor.setPosition(10, height - 35)
        addDrawableChild(originalAuthor)

        val forkAuthor = TextWidget(Text.literal(forkAuthorText), textRenderer)
        forkAuthor.setPosition(10, height - 20)
        addDrawableChild(forkAuthor)
    }

    override fun mouseClicked(click: Click, double: Boolean): Boolean {
        if (click.button() == 0) { // left click only
            val origWidth = textRenderer.getWidth(originalAuthorText)
            if (click.x in 10.0..(10.0 + origWidth) && click.y in (height - 35.0)..(height - 25.0)) {
                Util.getOperatingSystem().open(URI(originalAuthorGithubUrl))
            }

            val forkWidth = textRenderer.getWidth(forkAuthorText)
            if (click.x in 10.0..(10.0 + forkWidth) && click.y in (height - 20.0)..(height - 10.0)) {
                Util.getOperatingSystem().open(URI(forkAuthorGithubUrl))
            }
        }
        return super.mouseClicked(click, double)
    }

    override fun close() {
        if (redoRender) { // Some Settings might change the look of SoundboardScreen -> redo Render
            client.setScreen(SoundboardScreen(null))
        } else {
            client.setScreen(parent)
        }
    }
}