package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting

class DeleteCategoryOverlay(
    ctx: OverlayContext,
    private val title: Text,
    private val onConfirm: (String) -> Unit,
    onDismiss: (() -> Unit)? = null
) : ScreenOverlay(ctx, overlayWidth = 170, overlayHeight = 92) {

    private var category = ""

    private val titleWidget = TextWidget(title, textRenderer)
    private val categoryName = TextWidget(Text.literal(category), textRenderer)
    private val warningWidget = TextWidget(
        Text.literal("This cannot be undone").formatted(Formatting.GRAY, Formatting.ITALIC),
        textRenderer
    )

    private val cancelButton = ButtonWidget.builder(Text.literal("Cancel")) {
        onDismiss?.invoke()
        hide()
    }
        .position(buttonRowStartX(), y + overlayHeight - 26)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .build()

    private val deleteButton = ButtonWidget.builder(
        Text.literal("Delete").withColor(0xFF5555)
    ) {
        onConfirm(category)
        hide()
    }
        .position(buttonRowStartX() + BUTTON_WIDTH + BUTTON_GAP, y + overlayHeight - 26)
        .size(BUTTON_WIDTH, BUTTON_HEIGHT)
        .tooltip(
            Tooltip.of(
                Text.literal("This will delete all sounds in the category!")
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
            Text.literal(category).formatted(Formatting.ITALIC, Formatting.YELLOW)
        )
        categoryName.setPosition(getMid(categoryName.message), y + 32)
    }

    override fun renderContent(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        titleWidget.render(context, mouseX, mouseY, delta)
        categoryName.render(context, mouseX, mouseY, delta)
        warningWidget.render(context, mouseX, mouseY, delta)

        cancelButton.render(context, mouseX, mouseY, delta)
        deleteButton.render(context, mouseX, mouseY, delta)
    }

    override fun onMouseClicked(click: Click, double: Boolean) {
        cancelButton.mouseClicked(click, double)
        deleteButton.mouseClicked(click, double)
    }

    companion object {
        private const val BUTTON_WIDTH = 64
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_GAP = 8
    }
}