package org.wolmics.soundboardplus.gui.components

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.wolmics.soundboardplus.config.SoundData

class CategoryTabWidget(
    private val font: Font,
    private val barY: Int,
    private val barHeight: Int,
    private val getCategories: () -> List<String>,
    private val getSelectedCategory: () -> String?,
    private val getDraggingSound: () -> SoundData?,
    private val getDragPosition: () -> Pair<Int, Int>,
) {

    sealed interface Action {
        data class SelectCategory(val category: String?) : Action
        object AddCategory : Action
        // A click the tab bar fully handled itself (e.g. a page-arrow click) - callers
        // still need to know the click was consumed even though there's nothing left to do.
        object Handled : Action
    }

    // Which page of tabs is currently shown, when tabs don't fit on one row
    var currentPage: Int = 0

    private data class TabRect(val x: Int, val y: Int, val w: Int, val h: Int, val category: String?, val name: String)
    private val tabRects = mutableListOf<TabRect>()

    private var lastPageSwitchTime: Long = 0L
    private val pageSwitchCooldown = 500L
    private val pageArrowWidth = 14

    fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, areaLeft: Int, areaRight: Int) {
        tabRects.clear()

        data class TabDef(val label: String, val category: String?, val name: String)
        val defs = buildList {
            add(TabDef("All", null, "category"))
            for (catName in getCategories()) {
                add(TabDef(catName.replaceFirstChar { it.uppercase() }, catName, "category"))
            }
            add(TabDef("＋", null, "categoryAdder"))
        }

        val selectedCategory = getSelectedCategory()

        // Measure how much space every tab needs laid out back-to-back
        val tabWidths = defs.map { font.width(it.label) + 12 }
        val totalContentWidth = tabWidths.sum() + (defs.size - 1) * 4

        val needsPaging = totalContentWidth > (areaRight - areaLeft)

        if (!needsPaging) {
            // Everything fits on one row -> no arrows, no page label, page stays at 0
            currentPage = 0
            var curX = areaLeft
            for ((index, def) in defs.withIndex()) {
                val active = def.name != "categoryAdder" && selectedCategory == def.category
                curX = drawTab(graphics, def.label, curX, tabWidths[index], def.category, active, mouseX, mouseY, name = def.name)
            }
        } else {
            // Reserve room on the right for both nav arrows and a "page X/Y" label
            val labelReserve = font.width("00/00") + 10
            val contentLeft = areaLeft + pageArrowWidth + 2
            val contentRight = areaRight - pageArrowWidth - 2 - labelReserve
            val availableWidth = contentRight - contentLeft

            // Group tabs into pages
            val pages = mutableListOf<List<Int>>()
            var pageIndices = mutableListOf<Int>()
            var curWidth = 0
            for (i in defs.indices) {
                val w = tabWidths[i]
                when {
                    pageIndices.isEmpty() -> {
                        pageIndices.add(i)
                        curWidth = w
                    }
                    curWidth + 4 + w <= availableWidth -> {
                        pageIndices.add(i)
                        curWidth += 4 + w
                    }
                    else -> {
                        pages.add(pageIndices)
                        pageIndices = mutableListOf(i)
                        curWidth = w
                    }
                }
            }
            if (pageIndices.isNotEmpty()) pages.add(pageIndices)

            currentPage = currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

            var curX = contentLeft
            for (i in pages.getOrElse(currentPage) { emptyList() }) {
                val def = defs[i]
                val active = def.name != "categoryAdder" && selectedCategory == def.category
                curX = drawTab(graphics, def.label, curX, tabWidths[i], def.category, active, mouseX, mouseY, name = def.name)
            }

            drawPageArrow(graphics, areaLeft, "◀", currentPage > 0, mouseX, mouseY, "prevPage")
            drawPageArrow(graphics, areaRight - pageArrowWidth, "▶", currentPage < pages.size - 1, mouseX, mouseY, "nextPage")

            // "page X/Y" indicator
            val pageLabel = "${currentPage + 1}/${pages.size}"
            val labelX = contentRight + 4
            val labelW = (areaRight - pageArrowWidth - 2) - labelX
            graphics.text(
                font, pageLabel,
                labelX + (labelW - font.width(pageLabel)) / 2,
                barY + (barHeight - font.lineHeight) / 2,
                0xFFAAAAAA.toInt(), false
            )
        }

        // Page switching while a sound is being dragged over an arrow
        if (getDraggingSound() != null) {
            val (dragX, dragY) = getDragPosition()
            val hoveredArrow = tabRects.firstOrNull { tab ->
                (tab.name == "prevPage" || tab.name == "nextPage") &&
                        dragX in tab.x until tab.x + tab.w &&
                        dragY in tab.y until tab.y + tab.h
            }

            if (hoveredArrow != null && System.currentTimeMillis() - lastPageSwitchTime > pageSwitchCooldown) {
                if (hoveredArrow.name == "prevPage") {
                    currentPage = (currentPage - 1).coerceAtLeast(0)
                } else if (hoveredArrow.name == "nextPage") {
                    currentPage += 1
                }
                lastPageSwitchTime = System.currentTimeMillis()
            }
        }
    }

    fun mouseClicked(x: Int, y: Int): Action? {
        for (tab in tabRects) {
            if (x >= tab.x && x < tab.x + tab.w && y >= tab.y && y < tab.y + tab.h) {
                return when (tab.name) {
                    "categoryAdder" -> Action.AddCategory
                    "prevPage" -> { currentPage = (currentPage - 1).coerceAtLeast(0); Action.Handled }
                    "nextPage" -> { currentPage += 1; Action.Handled }
                    else -> Action.SelectCategory(tab.category)
                }
            }
        }
        return null
    }

    fun categoryTabAt(x: Int, y: Int): CategoryHit? {
        val tab = tabRects.firstOrNull { it.name == "category" && x in it.x until it.x + it.w && y in it.y until it.y + it.h }
        return tab?.let { CategoryHit(it.category) }
    }

    data class CategoryHit(val category: String?)

    private fun isHovered(x: Int, w: Int, mouseX: Int, mouseY: Int): Boolean =
        mouseX in x..(x + w) && mouseY in barY..(barY + barHeight)

    private fun drawBorderedBox(graphics: GuiGraphicsExtractor, x: Int, w: Int, bg: Int, border: Int) {
        graphics.fill(x, barY, x + w, barY + barHeight, bg)
        graphics.fill(x, barY, x + w, barY + 1, border)                     // top
        graphics.fill(x, barY + barHeight - 1, x + w, barY + barHeight, border) // bottom
        graphics.fill(x, barY, x + 1, barY + barHeight, border)               // left
        graphics.fill(x + w - 1, barY, x + w, barY + barHeight, border)       // right
    }

    private fun drawPageArrow(
        graphics: GuiGraphicsExtractor,
        x: Int,
        label: String,
        enabled: Boolean,
        mouseX: Int,
        mouseY: Int,
        tabName: String
    ) {
        val w = pageArrowWidth
        val hovered = enabled && isHovered(x, w, mouseX, mouseY)

        val bg = when {
            !enabled -> 0xFF202020.toInt()
            hovered  -> 0xFF404040.toInt()
            else     -> 0xFF282828.toInt()
        }
        val border = if (enabled) 0xFF555555.toInt() else 0xFF333333.toInt()
        val textCol = if (enabled) 0xFFAAAAAA.toInt() else 0xFF555555.toInt()

        drawBorderedBox(graphics, x, w, bg, border)
        graphics.text(
            font, label,
            x + (w - font.width(label)) / 2,
            barY + (barHeight - font.lineHeight) / 2,
            textCol, false
        )

        // Only register the arrow as clickable while it's actually usable
        if (enabled) {
            tabRects.add(TabRect(x, barY, w, barHeight, category = null, name = tabName))
        }
    }

    private fun drawTab(
        graphics: GuiGraphicsExtractor,
        label: String,
        x: Int,
        w: Int,
        category: String?,
        active: Boolean,
        mouseX: Int,
        mouseY: Int,
        name: String = "category"
    ): Int {
        val hovered = isHovered(x, w, mouseX, mouseY)

        // During a drag, highlight the tab the ghost is hovering over
        val draggingSound = getDraggingSound()
        val isDragTarget = if (draggingSound != null && name != "categoryAdder") {
            val (dragX, dragY) = getDragPosition()
            dragX in x until x + w && dragY in barY until barY + barHeight
        } else false

        val bg = when {
            isDragTarget -> 0xFF245C3A.toInt()   // green tint — valid drop zone
            active       -> 0xFF606060.toInt()
            hovered      -> 0xFF404040.toInt()
            else         -> 0xFF282828.toInt()
        }
        val border = when {
            isDragTarget -> 0xFF63E69A.toInt()   // bright green border
            active       -> 0xFFAAAAAA.toInt()
            else         -> 0xFF555555.toInt()
        }
        val textCol = when {
            isDragTarget -> 0xFFE8FFF0.toInt()
            active       -> 0xFFFFFFFF.toInt()
            else         -> 0xFFAAAAAA.toInt()
        }

        drawBorderedBox(graphics, x, w, bg, border)
        // w is always font.width(label) + 12, i.e. 6px padding on each side,
        // so the centered text offset is always exactly x + 6 — no need to re-measure the label.
        graphics.text(
            font, label,
            x + 6,
            barY + (barHeight - font.lineHeight) / 2,
            textCol, false
        )

        tabRects.add(TabRect(x, barY, w, barHeight, category, name = name))
        return x + w + 4
    }
}