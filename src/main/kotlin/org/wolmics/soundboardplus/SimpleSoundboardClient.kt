package org.wolmics.soundboardplus

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.gui.SoundboardScreen
import org.lwjgl.glfw.GLFW
import org.wolmics.soundboardplus.util.KeyEventBus
import java.io.File

class SimpleSoundboardClient : ClientModInitializer {

    companion object {

        const val MOD_ID = "soundboardplus"

        val KEY_CATEGORY: KeyMapping.Category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
        )

        lateinit var OPEN_GUI_KEY: KeyMapping
        lateinit var PAUSE_PLAYBACK_KEY: KeyMapping

        val soundDir = File(FabricLoader.getInstance().gameDir.toFile(), "soundboard")
        val modDir = File(FabricLoader.getInstance().gameDir.toFile(), "soundboard")
        val modDependencyDir = File(FabricLoader.getInstance().gameDir.toFile(), "soundboard-dependencies")
    }

    override fun onInitializeClient() {
        OPEN_GUI_KEY = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.$MOD_ID.open",
                GLFW.GLFW_KEY_J,
                KEY_CATEGORY
            )
        )
        PAUSE_PLAYBACK_KEY = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
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

        ClientTickEvents.END_CLIENT_TICK.register { client: Minecraft ->
            if (client.player == null) return@register

            if (OPEN_GUI_KEY.consumeClick()) {
                client.setScreenAndShow(SoundboardScreen())
            }

            if (PAUSE_PLAYBACK_KEY.consumeClick()) {
                SoundboardAudioSystem.playbackPaused = !SoundboardAudioSystem.playbackPaused
            }

            KeyEventBus.handleSoundKeybinds(client)
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SoundboardConfig.save()
        }
    }
}