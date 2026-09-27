package org.wolmics.soundboardplus.util

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundboardConfig

object KeyEventBus {
    private val pressedKeys = mutableSetOf<Int>()

    fun handleSoundKeybinds(client: Minecraft) {
        if (client.gui.screen() != null) return
        val snapshot = pressedKeys.toHashSet()

        for (sound in SoundboardConfig.data.sounds) {
            val keyCode = sound.keybind
            if (keyCode <= 0 || keyCode == InputConstants.KEY_RETURN) continue

            val isPressed = InputConstants.isKeyDown(keyCode)
            val wasPressed = snapshot.contains(keyCode)

            if (isPressed && !wasPressed) {
                pressedKeys.add(keyCode)

                if (SoundboardAudioSystem.isPlaying(sound.id)) {
                    SoundboardAudioSystem.stop(sound)
                } else {
                    SoundboardAudioSystem.playFile(sound)
                }


            } else if (!isPressed && wasPressed) {
                pressedKeys.remove(keyCode)
            }
        }
    }
}