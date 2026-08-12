package org.wolmics.soundboardplus.util

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.TutorialToast
import net.minecraft.network.chat.Component
import org.wolmics.soundboardplus.mixin.TutorialToastAccessor

object ToastManager {
    private val client: Minecraft = Minecraft.getInstance()
    private val pendingHideToasts = mutableMapOf<Toast, Long>()

    fun tick() {
        if (pendingHideToasts.isEmpty()) return
        val now = System.currentTimeMillis()
        val iterator = pendingHideToasts.iterator()
        while (iterator.hasNext()) {
            val (toast, hideTime) = iterator.next()
            if (now >= hideTime) {
                if (toast is SystemToast) toast.forceHide()
                else if (toast is TutorialToast) toast.hide()
                iterator.remove()
            }
        }
    }

    fun createToast(text: Component, ms: Long) {
        val toast = SystemToast(SystemToast.SystemToastId.FILE_DROP_FAILURE, Component.literal("Soundboard+"), text)
        client.gui.toastManager().addToast(toast)
        hideToast(toast, ms)
    }

    fun createProgressToast(text: Component): TutorialToast {
        val toast = TutorialToast(
            client.font,
            TutorialToast.Icons.SOCIAL_INTERACTIONS,
            Component.literal("Soundboard+"),
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

    fun updateTextAsync(toast: Toast?, text: Component) {
        val tutorialToast = toast as? TutorialToast ?: return
        Minecraft.getInstance().execute { changeToastText(tutorialToast, text) }
    }

    fun hideToast(toast: Toast?, ms: Long) {
        if (toast == null) return
        pendingHideToasts[toast] = System.currentTimeMillis() + ms
    }
}