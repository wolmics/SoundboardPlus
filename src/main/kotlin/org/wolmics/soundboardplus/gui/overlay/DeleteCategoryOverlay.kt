package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class DeleteCategoryOverlay(
    ctx: OverlayContext,
    private val title: Component,
    private val onConfirm: (String) -> Unit,
    onDismiss: (() -> Unit)? = null
) : ScreenOverlay(ctx, overlayWidth = 170, overlayHeight = 92) {

    private var category = ""

    private val titleWidget = StringWidget(title, font)
    private val categoryName = StringWidget(Component.literal(category), font)
    private val warningWidget = StringWidget(
        Component.literal("This cannot be undone").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
        font
    )

    private val cancelButton = Button.builder(Component.literal("Cancel")) {
        onDismiss?.invoke()
        hide()
    }
        .pos(buttonRowStartX(), y + overlayHeight - 26)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .build()

    private val deleteButton = Button.builder(
        Component.literal("Delete").withColor(0xFF5555)
    ) {
        onConfirm(category)
        hide()
    }
        .pos(buttonRowStartX() + BUTTON_WIDTH + BUTTON_GAP, y + overlayHeight - 26)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .tooltip(
            Tooltip.create(
                Component.literal("This will delete all sounds in the category!")
                    .withColor(0xFF5555)
            )
        )
        .build()

    init {
        titleWidget.setPosition(getMid(titleWidget.message), y + 12)
        categoryName.setPosition(getMid(categoryName.message), y + 32)
        warningWidget.setPosition(getMid(warningWidget.message), y + 52)

        cancelButton.isFocused = true
    }

    private fun buttonRowStartX(): Int =
        screenWidth / 2 - (BUTTON_WIDTH * 2 + BUTTON_GAP) / 2

    fun setCategory(category: String) {
        this.category = category
        categoryName.setMessage(
            Component.literal(category).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW)
        )
        categoryName.setPosition(getMid(categoryName.message), y + 32)
    }

    override fun renderContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        titleWidget.extractRenderState(graphics, mouseX, mouseY, delta)
        categoryName.extractRenderState(graphics, mouseX, mouseY, delta)
        warningWidget.extractRenderState(graphics, mouseX, mouseY, delta)

        cancelButton.extractRenderState(graphics, mouseX, mouseY, delta)
        deleteButton.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun onMouseClicked(click: MouseButtonEvent, double: Boolean) {
        cancelButton.mouseClicked(click, double)
        deleteButton.mouseClicked(click, double)
    }

    companion object {
        private const val BUTTON_WIDTH = 64
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_GAP = 8
    }
}