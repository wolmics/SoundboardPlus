package org.wolmics.soundboardplus

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.option.KeyBinding
import net.minecraft.util.Identifier
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.gui.SoundboardScreen
import org.lwjgl.glfw.GLFW
import org.wolmics.soundboardplus.util.KeyEventBus
import org.wolmics.soundboardplus.util.ToastRenderer
import java.io.File

class SimpleSoundboardClient : ClientModInitializer {

    companion object {

        const val MOD_ID = "soundboardplus"

        val KEY_CATEGORY: KeyBinding.Category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))

        lateinit var OPEN_GUI_KEY: KeyBinding
        lateinit var PAUSE_PLAYBACK_KEY: KeyBinding

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

        SoundboardConfig.load()

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(MOD_ID, "toast_hud")
        ) { context, _ -> ToastRenderer.render(context) }

        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenEvents.afterRender(screen).register { _, context, mouseX, mouseY, _ ->
                ToastRenderer.render(context)
            }

            ScreenMouseEvents.beforeMouseClick(screen).register { _, mouseButtonEvent ->
                ToastRenderer.mouseClicked(mouseButtonEvent.x(), mouseButtonEvent.y())
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register

            if (OPEN_GUI_KEY.wasPressed()) {
                client.setScreen(SoundboardScreen())
            }

            if (PAUSE_PLAYBACK_KEY.wasPressed()) {
                SoundboardAudioSystem.playbackPaused = !SoundboardAudioSystem.playbackPaused
            }

            KeyEventBus.handleSoundKeybinds(client)
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            SoundboardConfig.save()
        }
    }
}