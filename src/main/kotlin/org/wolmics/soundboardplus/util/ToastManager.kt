package org.wolmics.soundboardplus.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.toasts.TutorialToast
import net.minecraft.network.chat.Component
import org.wolmics.soundboardplus.mixin.TutorialToastAccessor
import java.util.Timer
import java.util.TimerTask

object ToastManager {
    private val client: Minecraft = Minecraft.getInstance()

    fun createToast(text: Component, ms: Long) {
        val toast = SystemToast(SystemToast.SystemToastId.FILE_DROP_FAILURE, Component.literal("Better Soundboard"), text)
        client.gui.toastManager().addToast(toast)
        hideSystemToastIn(toast, ms)
    }

    fun createProgressToast(text: Component): TutorialToast {
        val toast = TutorialToast(
            client.font,
            TutorialToast.Icons.SOCIAL_INTERACTIONS,
            Component.literal("Better Soundboard"),
            Component.literal(""),
            true
        )
        client.gui.toastManager().addToast(toast)

        changeToastText(toast, text)
        toast.updateProgress(0.0f)
        return toast
    }

    fun changeToastText(toast: TutorialToast, text: Component) {
        val textList = (toast as TutorialToastAccessor).lines ?: return
        textList.clear()
        textList.addAll(client.font.split(Component.literal("Soundboard+").withColor(-11534256), 126))
        textList.addAll(client.font.split(text, 126))
    }

    fun hideSystemToastIn(toast: SystemToast, ms: Long = 1000L) {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                Minecraft.getInstance().execute {
                    toast.forceHide()
                }
            }
        }, ms)
    }

    fun hideTutorialToastIn(toast: TutorialToast, ms: Long = 1000L) {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                Minecraft.getInstance().execute {
                    toast.hide()
                }
            }
        }, ms)
    }
}