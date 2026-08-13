package org.wolmics.soundboardplus.util

import net.minecraft.client.MinecraftClient
import net.minecraft.client.toast.SystemToast
import net.minecraft.client.toast.Toast
import net.minecraft.client.toast.TutorialToast
import net.minecraft.text.Text
import org.wolmics.soundboardplus.mixin.TutorialToastAccessor

object ToastManager {
    private val client: MinecraftClient = MinecraftClient.getInstance()
    private val pendingHideToasts = mutableMapOf<Toast, Long>()

    fun tick() {
        if (pendingHideToasts.isEmpty()) return
        val now = System.currentTimeMillis()
        val iterator = pendingHideToasts.iterator()
        while (iterator.hasNext()) {
            val (toast, hideTime) = iterator.next()
            if (now >= hideTime) {
                if (toast is SystemToast) toast.hide()
                else if (toast is TutorialToast) toast.hide()
                iterator.remove()
            }
        }
    }

    fun createToast(text: Text, ms: Long) {
        val toast = SystemToast.create(client, SystemToast.Type.FILE_DROP_FAILURE, Text.literal("Soundboard+"), text)
        client.toastManager.add(toast)
        hideToast(toast, ms)
    }

    fun createProgressToast(text: Text): TutorialToast {
        val toast = TutorialToast(
            client.textRenderer,
            TutorialToast.Type.SOCIAL_INTERACTIONS,
            Text.literal("Soundboard+"),
            Text.literal(""),
            true
        )
        client.toastManager.add(toast)

        changeToastText(toast, text)
        toast.setProgress(0.0f)
        return toast
    }

    fun changeToastText(toast: TutorialToast, text: Text) {
        val textList = (toast as TutorialToastAccessor).getText()
        textList.clear()
        textList.add(text.asOrderedText())
    }

    fun updateTextAsync(toast: Toast?, text: Text) {
        val tutorialToast = toast as? TutorialToast ?: return
        MinecraftClient.getInstance().execute { changeToastText(tutorialToast, text) }
    }

    fun hideToast(toast: Toast?, ms: Long) {
        if (toast == null) return
        pendingHideToasts[toast] = System.currentTimeMillis() + ms
    }
}