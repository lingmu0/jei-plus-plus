package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeTreeInputHandler;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the standalone Alt+left-click tree gesture to every JEI recipe layout. */
@Mixin(value = RecipeLayoutWithButtons.class, remap = false)
public abstract class RecipeLayoutWithButtonsMixin {
    @Shadow @Final private IRecipeLayoutDrawable<?> recipeLayout;

    @Inject(method = "createUserInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$wrapTreeInput(CallbackInfoReturnable<IUserInputHandler> cir) {
        cir.setReturnValue(RecipeTreeInputHandler.wrap(cir.getReturnValue(), recipeLayout));
    }
}
