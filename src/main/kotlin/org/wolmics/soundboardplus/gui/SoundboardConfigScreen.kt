package org.wolmics.soundboardplus.gui

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
    private val forkAuthorText: String = "Modified, and maintained by Wolmics"

    override fun init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Back")) { close() }
            .position(width / 2 - 100, height - 30)
            .size(200, 20)
            .build()
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playLocally)
                .build(width / 2 - 100, 50, 200, 20, Text.literal("Play Locally")) { _, value ->
                    SoundboardConfig.data.playLocally = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playWhileMuted)
                .build(width / 2 - 100, 75, 200, 20, Text.literal("Play While Muted")) { _, value ->
                    SoundboardConfig.data.playWhileMuted = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.playOnlyOne)
                .build(width / 2 - 100, 100, 200, 20, Text.literal("Play only one at a time")) { _, value ->
                    SoundboardConfig.data.playOnlyOne = value
                    SoundboardConfig.save()
                }
        )

        addDrawableChild(
            CyclingButtonWidget.onOffBuilder(SoundboardConfig.data.showProgressBar)
                .build(width / 2 - 100, 125, 200, 20, Text.literal("Show Progress Bar")) { _, value ->
                    SoundboardConfig.data.showProgressBar = value
                    SoundboardConfig.save()
                }
        )

        val originalAuthor = TextWidget(Text.literal(originalAuthorText), textRenderer)
        originalAuthor.setPosition(10, height - 35)
        addDrawableChild(originalAuthor)

        val forkAuthor = TextWidget(Text.literal(forkAuthorText), textRenderer)
        forkAuthor.setPosition(10, height - 20)
        addDrawableChild(forkAuthor)
    }

    override fun mouseClicked(click: Click?, double: Boolean): Boolean {
        if (click?.button() == 0) { // left click only
            val origWidth = textRenderer.getWidth(originalAuthorText)
            if (click.x in 10.0..(10.0 + origWidth) && click.y in (height - 35.0)..(height - 25.0)) {
                Util.getOperatingSystem().open(URI("https://github.com/0x1bd/SimpleSoundboard"))
            }

            val forkWidth = textRenderer.getWidth(forkAuthorText)
            if (click.x in 10.0..(10.0 + forkWidth) && click.y in (height - 20.0)..(height - 10.0)) {
                Util.getOperatingSystem().open(URI("https://github.com/wolmics"))
            }
        }
        return super.mouseClicked(click, double)
    }

    override fun close() {
        client?.setScreen(SoundboardScreen())
    }
}
