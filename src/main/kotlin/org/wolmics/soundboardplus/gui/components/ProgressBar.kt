package org.wolmics.soundboardplus.gui.components

import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundData
import org.wolmics.soundboardplus.config.SoundboardConfig

class ProgressBar(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val getSelectedSound: () -> SoundData?,
    private val isEnabled: Boolean = true,
) : AbstractSliderButton(x, y, width, height, Component.literal("--:-- / --:--"), 0.0) {

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
    private fun resolveTarget(): SoundData? {
        // Priority 1: selected file, but only if it is actually playing
        val selected = getSelectedSound()
        if (selected != null && SoundboardAudioSystem.getProgress(selected.id) != null)
            return selected

        // Priority 2: the one and only playing sound
        val singleId = SoundboardAudioSystem.getSinglePlayingId() ?: return null
        return SoundboardConfig[singleId]
    }

    // ── SliderWidget contract ────────────────────────────────────────────────
    override fun applyValue() {
        val id = resolveTarget()?.id ?: return
        SoundboardAudioSystem.seekTo(id, value)
    }

    override fun updateMessage() {
        val id = resolveTarget()?.id
        val total = if (id != null) SoundboardAudioSystem.getTotalSamples(id) ?: 0 else 0

        message = if (total == 0) {
            Component.literal("--:-- / --:--")
        } else {
            val elapsed = (value * total).toInt()
            Component.literal(
                "${formatTime(elapsed / SAMPLE_RATE)}  /  ${formatTime(total / SAMPLE_RATE)}"
            )
        }
    }

    // ── Per-frame sync ───────────────────────────────────────────────────────
    fun tick() {
        if (!isEnabled) return

        val id = resolveTarget()?.id
        val progress = if (id != null) SoundboardAudioSystem.getProgress(id) else null

        // Grey out the widget when nothing is playing / tracked
        active = (progress != null)

        if (!userDragging) {
            value = progress ?: 0.0
            updateMessage()
        }
    }

    // ── Drag-state tracking ──────────────────────────────────────────────────
    override fun onClick(click: MouseButtonEvent, double: Boolean) {
        userDragging = true
        super.onClick(click, double)
    }

    override fun onRelease(click: MouseButtonEvent) {
        userDragging = false
        super.onRelease(click)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun formatTime(totalSeconds: Int): String =
        "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}