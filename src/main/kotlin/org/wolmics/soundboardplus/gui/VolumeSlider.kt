package org.wolmics.soundboardplus.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.roundToInt

class VolumeSlider(
    x: Int, y: Int, width: Int, height: Int,
    private val prefix: Component,
    initialValue: Float,
    private val onChange: (Float) -> Unit
) : AbstractSliderButton(x, y, width, height, Component.empty(), initialValue.toDouble()) {

    init {
        updateMessage()
    }

    fun setValue(newValue: Float) {
        this.value = newValue.toDouble()
        updateMessage()
        applyValue()
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta)

        if (this.active) return

        val font = Minecraft.getInstance().font

        val drawText = Component.literal("").append(prefix).append(": None")

        val textWidth = font.width(drawText)
        val textX = this.x + (this.width - textWidth) / 2

        val textY = this.y + (this.height - font.lineHeight) / 2 + 1

        graphics.text(font, drawText, textX, textY, Color(160, 160, 160).rgb, false)
    }

    override fun updateMessage() {
        val percent = (value * 100).roundToInt()
        message = Component.literal("").append(prefix).append(": ${percent}%")
    }

    override fun applyValue() {
        onChange(value.toFloat())
    }
}