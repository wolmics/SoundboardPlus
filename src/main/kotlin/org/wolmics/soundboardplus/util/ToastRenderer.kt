package org.wolmics.soundboardplus.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.abs


object ToastRenderer {

    private val TITLE: Component = Component.literal("Soundboard+")
    private val CLOSE_TEXT: Component = Component.literal("x")

    // Style
    private const val COLOR_BACKGROUND = 0xFF20281F.toInt() // dark, barely-green panel
    private const val COLOR_BORDER = 0xFF3E8F63.toInt()     // saturated green outline
    private const val COLOR_ACCENT = 0xFF63E69A.toInt()     // title text
    private const val COLOR_PROGRESS_TRACK = 0xFF404040.toInt()
    private const val COLOR_PROGRESS_FILL = 0xFF63E69A.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFAAAAAA.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFECECEC.toInt()

    private const val BACKGROUND_ALPHA = 0.80f
    private const val HOVER_LIGHTEN = 0.18f

    // Layout
    private const val WIDTH = 160
    private const val HEIGHT = 38
    private const val MARGIN = 6
    private const val GAP = 4
    private const val PADDING = 5
    private const val PROGRESS_BAR_HEIGHT = 4
    private const val MAX_MESSAGE_LINES = 2

    private const val SLIDE_EASE = 0.25f // per-frame easing, normalized to ~60fps

    private val lock = Any()
    private val toasts = mutableListOf<Entry>()
    private var lastFrameTimeMs = System.currentTimeMillis()

    // Toast types

    sealed class Toast {
        abstract val message: Component
    }

    class TextToast(
        override val message: Component,
        val durationMs: Long = 3000L
    ) : Toast()

    class ProgressToast(
        initialMessage: Component,
        initialProgress: Float = 0f
    ) : Toast() {
        @Volatile
        override var message: Component = initialMessage

        @Volatile
        var progress: Float = initialProgress.coerceIn(0f, 1f)
            set(value) {
                field = value.coerceIn(0f, 1f)
            }

        @Volatile
        var completed: Boolean = false
            private set

        @Volatile
        internal var completeAtMs: Long = Long.MAX_VALUE

        // Changes the toasts text
        fun updateMessage(text: Component) {
            message = text
        }

        fun updateMessage(text: String) {
            message = Component.literal(text)
        }

        // Changes the progress (0f-1f)
        fun updateProgress(value: Float) {
            progress = value
        }

        // Marks a toast as finished
        fun complete() {
            completed = true
        }

        // Marks toast as finished with delay
        fun completeAfter(delayMs: Long) {
            completeAtMs = System.currentTimeMillis() + delayMs
        }
    }

    private class Entry(val toast: Toast) {
        var animatedX: Float? = null // null = not yet placed, starts off-screen
        var animatedY: Float? = null
        var removing = false
        var elapsedMs = 0L
        var closeBounds: IntArray? = null // last-rendered close "x" hitbox
    }

    // Public API

    fun showText(message: Component, durationMs: Long = 3000L): TextToast =
        add(TextToast(message, durationMs))

    fun showText(message: String, durationMs: Long = 3000L): TextToast =
        showText(Component.literal(message), durationMs)

    fun showProgress(message: Component, initialProgress: Float = 0f): ProgressToast =
        add(ProgressToast(message, initialProgress))

    fun showProgress(message: String, initialProgress: Float = 0f): ProgressToast =
        showProgress(Component.literal(message), initialProgress)

    private fun <T : Toast> add(toast: T): T {
        synchronized(lock) { toasts.add(0, Entry(toast)) }
        return toast
    }

    // Begins to do the exit animation
    fun dismiss(toast: Toast) {
        synchronized(lock) { toasts.firstOrNull { it.toast === toast }?.removing = true }
    }

    // Clears all toasts
    fun clearAll() {
        synchronized(lock) { toasts.clear() }
    }

    fun mouseClicked(mouseX: Double, mouseY: Double): Boolean {
        synchronized(lock) {
            for (entry in toasts) {
                val b = entry.closeBounds ?: continue
                if (mouseX >= b[0] && mouseX <= b[2] && mouseY >= b[1] && mouseY <= b[3]) {
                    entry.removing = true
                    return true
                }
            }
        }
        return false
    }

    // Rendering

    fun extract(
        graphics: GuiGraphicsExtractor,
        mouseX: Double = -1.0,
        mouseY: Double = -1.0
    ) {
        val now = System.currentTimeMillis()
        var deltaMs = now - lastFrameTimeMs
        lastFrameTimeMs = now
        if (deltaMs !in 0..200) deltaMs = 16 // clamp lag spikes / clock jumps

        val screenWidth = graphics.guiWidth()
        updateAndPrune(deltaMs, screenWidth)

        val snapshot = synchronized(lock) { toasts.toList() }
        for (entry in snapshot) {
            renderToast(graphics, entry, mouseX, mouseY)
        }
    }

    // Internals

    private fun updateAndPrune(deltaMs: Long, screenWidth: Int) {
        synchronized(lock) {
            val ease = (SLIDE_EASE * (deltaMs / 16f)).coerceIn(0f, 1f)
            val restX = (screenWidth - WIDTH - MARGIN).toFloat()
            val offScreenX = screenWidth.toFloat() + 4f

            val it = toasts.iterator()
            var index = 0
            while (it.hasNext()) {
                val entry = it.next()

                when (val toast = entry.toast) {
                    is TextToast -> {
                        entry.elapsedMs += deltaMs
                        if (entry.elapsedMs >= toast.durationMs) entry.removing = true
                    }
                    is ProgressToast ->
                        if (toast.completed || System.currentTimeMillis() >= toast.completeAtMs) entry.removing = true
                }

                val targetX = if (entry.removing) offScreenX else restX
                val targetY = (MARGIN + index * (HEIGHT + GAP)).toFloat()

                // Defaulting to offScreenX/targetY when unset makes a new toast
                // slide in horizontally while appearing directly in its stack slot.
                entry.animatedX = lerp(entry.animatedX ?: offScreenX, targetX, ease)
                entry.animatedY = lerp(entry.animatedY ?: targetY, targetY, ease)

                if (entry.removing && abs(entry.animatedX!! - offScreenX) < 1f) {
                    it.remove()
                } else {
                    index++
                }
            }
        }
    }

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

    private fun wrapText(font: Font, text: String, maxWidth: Int, maxLines: Int = MAX_MESSAGE_LINES): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var i = 0

        while (i < words.size && lines.size < maxLines) {
            val word = words[i]
            val candidate = if (current.isEmpty()) word else "$current $word"
            when {
                font.width(candidate) <= maxWidth -> {
                    current = StringBuilder(candidate)
                    i++
                }
                current.isEmpty() -> {
                    // Single word wider than the line; keep it as-is rather than looping forever.
                    current = StringBuilder(word)
                    i++
                }
                else -> {
                    lines.add(current.toString())
                    current = StringBuilder()
                }
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())

        if (i < words.size) {
            var last = lines.last()
            while (last.isNotEmpty() && font.width("$last…") > maxWidth) {
                last = last.dropLast(1).trimEnd()
            }
            lines[lines.lastIndex] = "$last…"
        }

        return lines
    }

    private fun renderToast(graphics: GuiGraphicsExtractor, entry: Entry, mouseX: Double, mouseY: Double) {
        val x = entry.animatedX?.toInt() ?: return
        val y = entry.animatedY?.toInt() ?: return
        val font = Minecraft.getInstance().font

        val hovered = mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + HEIGHT
        val bgColor = if (hovered) lighten(COLOR_BACKGROUND) else COLOR_BACKGROUND
        val borderColor = if (hovered) lighten(COLOR_BORDER) else COLOR_BORDER

        // NOTE: outline takes (x, y, width, height, color) like vanilla
        // GuiGraphics.renderOutline — NOT corner coordinates like fill does.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, withAlpha(bgColor, BACKGROUND_ALPHA))
        graphics.outline(x, y, WIDTH, HEIGHT, borderColor)

        graphics.text(font, TITLE, x + PADDING, y + PADDING, COLOR_ACCENT, false)

        val closeWidth = font.width(CLOSE_TEXT)
        val closeX = x + WIDTH - PADDING - closeWidth
        val closeY = y + PADDING
        graphics.text(font, CLOSE_TEXT, closeX, closeY, COLOR_TEXT_SECONDARY, false)
        entry.closeBounds = intArrayOf(closeX - 3, closeY - 2, closeX + closeWidth + 3, closeY + font.lineHeight + 2)

        val contentTop = y + PADDING + font.lineHeight + 3
        val contentBottom = y + HEIGHT - PADDING
        val maxTextWidth = WIDTH - PADDING * 2

        when (val toast = entry.toast) {
            is TextToast -> {
                val lines = wrapText(font, toast.message.string, maxTextWidth)
                val totalHeight = lines.size * font.lineHeight
                var messageY = contentTop + ((contentBottom - contentTop - totalHeight) / 2).coerceAtLeast(0)
                for (line in lines) {
                    graphics.text(font, Component.literal(line), x + PADDING, messageY, COLOR_TEXT_PRIMARY, false)
                    messageY += font.lineHeight
                }
            }

            is ProgressToast -> {
                val lines = wrapText(font, toast.message.string, maxTextWidth)
                var messageY = contentTop
                for (line in lines) {
                    graphics.text(font, Component.literal(line), x + PADDING, messageY, COLOR_TEXT_PRIMARY, false)
                    messageY += font.lineHeight
                }

                val barX = x + PADDING
                val barY = contentBottom - PROGRESS_BAR_HEIGHT
                val barWidth = WIDTH - PADDING * 2
                graphics.fill(barX, barY, barX + barWidth, barY + PROGRESS_BAR_HEIGHT, COLOR_PROGRESS_TRACK)

                val filled = (barWidth * toast.progress).toInt()
                if (filled > 0) {
                    graphics.fill(barX, barY, barX + filled, barY + PROGRESS_BAR_HEIGHT, COLOR_PROGRESS_FILL)
                }
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (((color ushr 24) and 0xFF) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    private fun lighten(color: Int, amount: Float = HOVER_LIGHTEN): Int {
        fun channel(shift: Int): Int {
            val c = (color ushr shift) and 0xFF
            return (c + (255 - c) * amount).toInt().coerceIn(0, 255)
        }
        return (color and 0xFF000000.toInt()) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}