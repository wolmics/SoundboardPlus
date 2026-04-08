package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import org.lwjgl.glfw.GLFW

abstract class ScreenOverlay(
    protected val ctx: OverlayContext,
    protected open val overlayWidth: Int = 200,
    protected open val overlayHeight: Int = 100,
    private val dismissOnOutsideClick: Boolean = true,
    private val onDismiss: (() -> Unit)? = null
) {
    var isVisible = false
        private set

    protected val textRenderer get() = ctx.textRenderer
    protected val screenWidth get() = ctx.screenWidth
    protected val screenHeight get() = ctx.screenHeight

    protected val x get() = screenWidth / 2 - overlayWidth / 2
    protected val y get() = screenHeight / 2 - overlayHeight / 2

    fun init() {}

    fun show() { isVisible = true; onShow() }
    fun hide() { isVisible = false; onDismiss?.invoke(); onHide() }

    protected open fun onShow() {}
    protected open fun onHide() {}

    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (!isVisible) return
        renderDimOverlay(context)
        renderBackground(context)
        renderContent(context, mouseX, mouseY, delta)
    }

    fun mouseClicked(click: Click, double: Boolean): Boolean {
        if (!isVisible) return false
        val inside = click.x >= x && click.x <= x + overlayWidth &&
                click.y >= y && click.y <= y + overlayHeight
        return if (inside) {
            onMouseClicked(click, double)
            true
        } else {
            if (dismissOnOutsideClick) hide()
            true
        }
    }

    fun keyPressed(input: KeyInput): Boolean {
        if (!isVisible) return false
        if (input.keycode == GLFW.GLFW_KEY_ESCAPE) { hide(); return true }
        return onKeyPressed(input)
    }

    fun charTyped(input: CharInput): Boolean {
        if (!isVisible) return false
        return onCharTyped(input)
    }

    protected open fun onMouseClicked(click: Click, double: Boolean) {}
    protected open fun onKeyPressed(input: KeyInput): Boolean = true
    protected abstract fun renderContent(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float)
    protected open fun onCharTyped(input: CharInput): Boolean = false

    private fun renderDimOverlay(context: DrawContext) {
        context.fill(0, 0, screenWidth, screenHeight, 0x88000000.toInt())
    }

    private fun renderBackground(context: DrawContext) {
        val x2 = x + overlayWidth
        val y2 = y + overlayHeight

        // Outer black outline (matches the dark edge in your screenshot)
        context.fill(x - 2, y - 2, x2 + 2, y2 + 2, 0xFF000000.toInt())

        // Slightly lighter dark-gray inner border (the subtle 1px inset edge)
        context.fill(x - 1, y - 1, x2 + 1, y2 + 1, 0xFF222222.toInt())

        // Main gray background — same tone as vanilla Minecraft screens
        context.fill(x, y, x2, y2, 0xFF3F3F3F.toInt())

        // Subtle bevel: lighter on top/left, darker on bottom/right
        // This gives the slight 3D panel depth visible in the screenshot
        context.fill(x,      y,      x2,     y + 1,  0xFF5A5A5A.toInt())  // top highlight
        context.fill(x,      y,      x + 1,  y2,     0xFF5A5A5A.toInt())  // left highlight
        context.fill(x, y2 - 1, x2, y2, 0xFF262626.toInt())  // bottom shadow
        context.fill(x2 - 1, y, x2, y2, 0xFF262626.toInt())  // right shadow
    }
}