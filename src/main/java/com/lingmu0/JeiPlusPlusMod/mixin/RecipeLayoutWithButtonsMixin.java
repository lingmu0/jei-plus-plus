package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Hides JEI's native recipe-bookmark button in favour of the tree actions. */
@Mixin(value = RecipeLayoutWithButtons.class, remap = false)
public abstract class RecipeLayoutWithButtonsMixin {
    @Shadow @Final private List<IconButton> buttons;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void jeiPlusPlus$removeNativeRecipeBookmarkButton(CallbackInfo ci) {
        if (!JeiPlusPlusConfig.HIDE_RECIPE_BOOKMARK_BUTTON.get()) {
            return;
        }
        // JEI creates buttons in the fixed order: transfer, recipe bookmark,
        // then registered extra buttons. Removing index 1 keeps the extra
        // buttons contiguous and makes JEI recalculate their side positions.
        if (buttons.size() > 1) {
            buttons.remove(1);
        }
    }
}
