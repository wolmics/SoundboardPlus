package org.wolmics.soundboardplus

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.gui.SoundboardScreen
import org.lwjgl.glfw.GLFW
import java.io.File

class SimpleSoundboardClient : ClientModInitializer {

    companion object {

        const val MOD_ID = "simplesoundboard"

        val KEY_CATEGORY: KeyBinding.Category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))

        lateinit var OPEN_GUI_KEY: KeyBinding
        lateinit var PAUSE_PLAYBACK_KEY: KeyBinding

        private val pressedKeys = mutableSetOf<Int>()

        val soundDir = File(FabricLoader.getInstance().gameDir.toFile(), "soundboard")
        val modDir = File(FabricLoader.getInstance().gameDir.toFile(), "soundboard")
        val modDependencyDir = File(FabricLoader.getInstance().gameDir.toFile(), "soundboard-dependencies")
    }

    override fun onInitializeClient() {
        OPEN_GUI_KEY = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.$MOD_ID.open",
                GLFW.GLFW_KEY_J,
                KEY_CATEGORY
            )
        )

        PAUSE_PLAYBACK_KEY = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.$MOD_ID.pause",
                GLFW.GLFW_KEY_H,
                KEY_CATEGORY
            )
        )

        if (!soundDir.exists())
            soundDir.mkdirs()

        if (!modDir.exists())
            modDir.mkdirs()

        if (!modDependencyDir.exists())
            modDependencyDir.mkdirs()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register

            if (OPEN_GUI_KEY.wasPressed()) {
                client.setScreen(SoundboardScreen())
            }

            if (PAUSE_PLAYBACK_KEY.wasPressed()) {
                SoundboardAudioSystem.playbackPaused = !SoundboardAudioSystem.playbackPaused
            }

            if (client.currentScreen == null) {
                handleSoundKeybinds(client)
            }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SoundboardConfig.save()
        }
    }

    private fun handleSoundKeybinds(client: MinecraftClient) {
        for ((categoryName, category) in SoundboardConfig.data.categories) {
            for ((filename, soundData) in category.sounds) {
                val keyCode = soundData.keybind
                if (keyCode <= 0 || keyCode == GLFW.GLFW_KEY_ESCAPE) continue

                val isPressed = InputUtil.isKeyPressed(client.window, keyCode)
                val wasPressed = pressedKeys.contains(keyCode)

                if (isPressed && !wasPressed) {
                    pressedKeys.add(keyCode)

                    // Reconstruct the correct file path:
                    // "default" category → root of soundDir
                    // named categories → subdirectory matching the category name
                    val file = if (categoryName == "default") {
                        File(soundDir, filename)
                    } else {
                        File(soundDir, "$categoryName/$filename")
                    }

                    if (file.exists()) {
                        if (SoundboardAudioSystem.isPlaying(filename)) {
                            SoundboardAudioSystem.stop(filename)
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