package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

class CategoryConfigOverlay(ctx: OverlayContext, private val title: Text, private val onConfirm: (String) -> Unit, onDismiss: (() -> Unit)? = null): ScreenOverlay(ctx, overlayWidth = 160, overlayHeight = 80, onDismiss = onDismiss) {
    private val label = TextWidget(Text.literal("Category Creater"), textRenderer)

    private val confirmButton = ButtonWidget.builder(Text.literal("Add Category")) {
        onConfirm(categoryNameField.text)
        hide()
    }.size(100, 20).position(0, 0).build()

    private var categoryNameField: TextFieldWidget = TextFieldWidget(textRenderer, (screenWidth / 2 - overlayWidth / 2) + 10, y + 25, overlayWidth - 20, 20, Text.literal("Enter the category name"))

    init {
        categoryNameField.setMaxLength(16)
        categoryNameField.setPlaceholder(Text.literal("Enter the category name"))
    }

    override fun onShow() {
        categoryNameField.text = ""
        categoryNameField.setFocused(true)
        super.onShow()
    }

    override fun onHide() {
        categoryNameField.setFocused(false)
        super.onHide()
    }


    override fun renderContent(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.drawCenteredTextWithShadow(textRenderer, title, screenWidth / 2, y + 14, 0xFFFFFF)

        confirmButton.setPosition(screenWidth / 2 - 50, y + overlayHeight - 28)
        label.setPosition(screenWidth / 2 - 50, y + 8)

        if (categoryNameField.text == "") {
            categoryNameField.setSuggestion("Enter the category name")
        } else {
            categoryNameField.setSuggestion("")
        }

        label.render(context, mouseX, mouseY, delta)
        categoryNameField.render(context, mouseX, mouseY, delta)
        confirmButton.render(context, mouseX, mouseY, delta)
    }

    override fun onMouseClicked(click: Click, double: Boolean) {
        confirmButton.mouseClicked(click, double)
        categoryNameField.mouseClicked(click, double)
    }

    override fun onKeyPressed(input: KeyInput): Boolean {
        if (input.keycode == GLFW.GLFW_KEY_ENTER) {
            onConfirm(categoryNameField.text)
            hide()
            return true
        }
        return categoryNameField.keyPressed(input)
    }

    override fun onCharTyped(input: CharInput): Boolean {
        return categoryNameField.charTyped(input)
    }
}