package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IRecipesGui;
import mezz.jei.gui.overlay.elements.RecipeBookmarkElement;
import mezz.jei.gui.util.FocusUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A recipe bookmark is still the preferred result, but the lookup now keeps
 * all matching recipes/usages available. JEI's BOOKMARKED sorter stage places
 * the bookmarked recipe first when that JEI option is enabled.
 */
@Mixin(value = RecipeBookmarkElement.class, remap = false)
public abstract class RecipeBookmarkElementMixin {
    @Inject(method = "show", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeiPlusPlus$showAllRecipes(
        IRecipesGui recipesGui,
        FocusUtil focusUtil,
        List<RecipeIngredientRole> roles,
        CallbackInfo ci
    ) {
        RecipeBookmarkElement<?, ?> element = (RecipeBookmarkElement<?, ?>) (Object) this;
        ITypedIngredient<?> ingredient = element.getTypedIngredient();
        List<RecipeIngredientRole> lookupRoles = roles.isEmpty()
            ? List.of(RecipeIngredientRole.OUTPUT)
            : roles;
        List<IFocus<?>> focuses = focusUtil.createFocuses(ingredient, lookupRoles);
        recipesGui.show(focuses);
        ci.cancel();
    }
}
