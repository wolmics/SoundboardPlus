package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class CategoryConfigOverlay(ctx: OverlayContext, private val title: Component, private val onConfirm: (String) -> Unit, onDismiss: (() -> Unit)? = null): ScreenOverlay(ctx, overlayWidth = 160, overlayHeight = 80, onDismiss = onDismiss) {
    private val label = StringWidget(Component.literal("Create Category"), font)

    private val confirmButton = Button.builder(Component.literal("Add")) {
        onConfirm(categoryNameField.value)
        hide()
    }.size(100, 20).pos(0, 0).build()

    private var categoryNameField: EditBox = EditBox(font, (screenWidth - overlayWidth) / 2 + 10, y + 25, overlayWidth - 20, 20, Component.literal("Enter the category name"))

    init {
        categoryNameField.setMaxLength(16)
        categoryNameField.setSuggestion("Enter the category name")
    }

    override fun onShow() {
        categoryNameField.value = ""
        categoryNameField.isFocused = true
        super.onShow()
    }

    override fun onHide() {
        categoryNameField.isFocused = false
        super.onHide()
    }


    override fun renderContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.centeredText(font, title, screenWidth / 2, y + 14, 0xFFFFFF)

        confirmButton.setPosition(screenWidth / 2 - 50, y + overlayHeight - 28)
        label.setPosition((screenWidth - font.width(label.message)) / 2, y + 8)

        if (categoryNameField.value.isEmpty()) {
            categoryNameField.setSuggestion("Enter the category name")
        } else {
            categoryNameField.setSuggestion("")
        }

        label.extractRenderState(graphics, mouseX, mouseY, delta)
        categoryNameField.extractRenderState(graphics, mouseX, mouseY, delta)
        confirmButton.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun onMouseClicked(click: MouseButtonEvent, double: Boolean) {
        confirmButton.mouseClicked(click, double)
        categoryNameField.mouseClicked(click, double)
    }

    override fun onKeyPressed(input: KeyEvent): Boolean {
        if (input.key == GLFW.GLFW_KEY_ENTER) {
            onConfirm(categoryNameField.value)
            hide()
            return true
        }
        return categoryNameField.keyPressed(input)
    }

    override fun onCharTyped(input: CharacterEvent): Boolean {
        return categoryNameField.charTyped(input)
    }
}