package org.wolmics.soundboardplus.gui

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.text.Text
import org.wolmics.soundboardplus.SoundboardAudioSystem
import java.io.File

class ProgressBar(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val getSelectedFile: () -> File?,
    private val isEnabled: Boolean = true,
) : SliderWidget(x, y, width, height, Text.literal("--:-- / --:--"), 0.0) {

    companion object {
         // Samples per second used by Simple Voice Chat's audio pipeline.
        const val SAMPLE_RATE = 48_000
    }

    private var userDragging = false

    init {
        if (!isEnabled) {
            this.visible = false
        }
    }

    // ── Target resolution ────────────────────────────────────────────────────
    private fun resolveTarget(): File? {
        // Priority 1: selected file, but only if it is actually playing
        val selected = getSelectedFile()
        if (selected != null && SoundboardAudioSystem.getProgress(selected.name) != null)
            return selected

        // Priority 2: the one and only playing sound
        val singleName = SoundboardAudioSystem.getSinglePlayingName() ?: return null
        return File(singleName)   // only .name is used; no I/O performed
    }

    // ── SliderWidget contract ────────────────────────────────────────────────
    override fun applyValue() {
        val name = resolveTarget()?.name ?: return
        SoundboardAudioSystem.seekTo(name, value)
    }

    override fun updateMessage() {
        val name = resolveTarget()?.name
        val total = if (name != null) SoundboardAudioSystem.getTotalSamples(name) ?: 0 else 0

        message = if (total == 0) {
            Text.literal("--:-- / --:--")
        } else {
            val elapsed = (value * total).toInt()
            Text.literal(
                "${formatTime(elapsed / SAMPLE_RATE)}  /  ${formatTime(total / SAMPLE_RATE)}"
            )
        }
    }

    // ── Per-frame sync ───────────────────────────────────────────────────────
    fun tick() {
        if (!isEnabled) return

        val name    = resolveTarget()?.name
        val progress = if (name != null) SoundboardAudioSystem.getProgress(name) else null

        // Grey out the widget when nothing is playing / tracked
        active = (progress != null)

        if (!userDragging) {
            value = progress ?: 0.0
            updateMessage()
        }
    }

    // ── Drag-state tracking ──────────────────────────────────────────────────
    override fun onClick(click: Click, double: Boolean) {
        userDragging = true
        super.onClick(click, double)
    }

    override fun onRelease(click: Click) {
        userDragging = false
        super.onRelease(click)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun formatTime(totalSeconds: Int): String =
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}