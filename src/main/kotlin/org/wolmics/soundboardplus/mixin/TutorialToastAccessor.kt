package org.wolmics.soundboardplus.mixin

import net.minecraft.client.toast.TutorialToast
import net.minecraft.text.OrderedText
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(TutorialToast::class)
interface TutorialToastAccessor {
    @Accessor("text")
    fun getText(): MutableList<OrderedText>
}