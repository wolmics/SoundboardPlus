package org.wolmics.soundboardplus.util

import net.minecraft.client.MinecraftClient
import net.minecraft.client.toast.SystemToast
import net.minecraft.client.toast.TutorialToast
import net.minecraft.text.Text
import org.wolmics.soundboardplus.mixin.TutorialToastAccessor
import java.util.Timer
import java.util.TimerTask

object ToastManager {
    private val client: MinecraftClient = MinecraftClient.getInstance()

    fun createToast(text: Text, ms: Long) {
        val toast = SystemToast.create(client, SystemToast.Type.FILE_DROP_FAILURE, Text.literal("Better Soundboard"), text)
        client.toastManager.add(toast)
        hideSystemToastIn(toast, ms)
    }

    fun createProgressToast(text: Text): TutorialToast {
        val toast = TutorialToast(
            client.textRenderer,
            TutorialToast.Type.SOCIAL_INTERACTIONS,
            Text.literal("Better Soundboard"),
            Text.literal(""),
            true
        )
        client.toastManager.add(toast)

        // Update text immediately to test if the accessor works
        val textList = (toast as TutorialToastAccessor).getText()
        textList.clear()
        textList.add(text.asOrderedText())

        toast.setProgress(0.0f)
        return toast
    }

    fun changeToastText(toast: TutorialToast, text: Text) {
        val textList = (toast as TutorialToastAccessor).getText()
        textList.clear()
        textList.add(text.asOrderedText())
    }

    fun changeToastProgress(toast: TutorialToast, progress: Float) {
        toast.setProgress(progress)
    }

    fun hideSystemToastIn(toast: SystemToast, ms: Long = 1000L) {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                MinecraftClient.getInstance().execute {
                    toast.hide() // works for ANY Toast implementation
                }
            }
        }, ms)
    }

    fun hideTutorialToastIn(toast: TutorialToast, ms: Long = 1000L) {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                MinecraftClient.getInstance().execute {
                    toast.hide() // works for ANY Toast implementation
                }
            }
        }, ms)
    }
}