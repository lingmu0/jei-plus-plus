package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.library.transfer.BasicRecipeTransferHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes JEI 15.x's slot mapping so exact-count transfers can be emulated. */
@Mixin(value = BasicRecipeTransferHandler.class, remap = false)
public interface BasicRecipeTransferHandlerAccessor {
    @Accessor("transferInfo")
    IRecipeTransferInfo<?, ?> jeiPlusPlus$getTransferInfo();
}
