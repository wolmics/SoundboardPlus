package org.wolmics.soundboardplus.util

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.wolmics.soundboardplus.SimpleSoundboardClient.Companion.soundDir
import org.wolmics.soundboardplus.SoundboardAudioSystem
import org.wolmics.soundboardplus.config.SoundboardConfig
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

object KeyEventBus {
    private val pressedKeys = mutableSetOf<Int>()

    fun handleSoundKeybinds(client: Minecraft) {
        if (client.screen != null) return

        val snapshot = pressedKeys.toHashSet()

        for ((categoryName, category) in SoundboardConfig.data.categories) {
            for ((filename, soundData) in category.sounds) {
                val keyCode = soundData.keybind
                if (keyCode <= 0 || keyCode == GLFW.GLFW_KEY_ESCAPE) continue

                val isPressed = InputConstants.isKeyDown(client.window, keyCode)
                val wasPressed = snapshot.contains(keyCode)

                if (isPressed && !wasPressed) {
                    pressedKeys.add(keyCode)

                    val file = if (categoryName == "default") {
                        File(soundDir, filename)
                    } else {
                        File(soundDir, "$categoryName/$filename")
                    }

                    val soundKey = if (categoryName == "default") filename
                    else "$categoryName/$filename"

                    if (file.exists()) {
                        if (SoundboardAudioSystem.isPlaying(soundKey)) {
                            SoundboardAudioSystem.stop(soundKey)
                        } else {
                            SoundboardAudioSystem.playFile(file)
                        }
                    }

                } else if (!isPressed && wasPressed) {
                    pressedKeys.remove(keyCode)
                }
            }
        }
    }
}