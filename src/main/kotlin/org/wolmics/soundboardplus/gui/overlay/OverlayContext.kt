package org.wolmics.soundboardplus.gui.overlay

import net.minecraft.client.font.TextRenderer

data class OverlayContext(
    val textRenderer: TextRenderer,
    val screenWidth: Int,
    val screenHeight: Int
)