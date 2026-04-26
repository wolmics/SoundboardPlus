package org.wolmics.soundboardplus.mixin

import net.minecraft.client.gui.components.toasts.TutorialToast
import net.minecraft.util.FormattedCharSequence
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(TutorialToast::class)
interface TutorialToastAccessor {
    @get:Accessor("lines")
    val lines: MutableList<FormattedCharSequence?>?
}