package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeBookmarkNavigationContext;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.IngredientLookupState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/** Starts a recipe-bookmark lookup on the bookmark's category. */
@Mixin(value = IngredientLookupState.class, remap = false)
public abstract class IngredientLookupStateMixin {
    @Inject(
            method = "create(Lmezz/jei/api/recipe/IRecipeManager;Lmezz/jei/api/recipe/IFocusGroup;Ljava/util/List;Lmezz/jei/common/transfer/RecipeTransferService;)Lmezz/jei/gui/recipes/lookups/ILookupState;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void jeiPlusPlus$bookmarkCategoryFirstModern(
            IRecipeManager recipeManager,
            IFocusGroup focusGroup,
            List<IRecipeCategory<?>> recipeCategories,
            RecipeTransferService recipeTransferService,
            CallbackInfoReturnable<ILookupState> cir
    ) {
        jeiPlusPlus$moveToBookmarkCategory(cir);
    }

    @Inject(
            method = "create(Lmezz/jei/api/recipe/IRecipeManager;Lmezz/jei/api/recipe/IFocusGroup;Ljava/util/List;Lmezz/jei/api/recipe/transfer/IRecipeTransferManager;)Lmezz/jei/gui/recipes/lookups/ILookupState;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void jeiPlusPlus$bookmarkCategoryFirstLegacy(
            IRecipeManager recipeManager,
            IFocusGroup focusGroup,
            List<IRecipeCategory<?>> recipeCategories,
            IRecipeTransferManager recipeTransferManager,
            CallbackInfoReturnable<ILookupState> cir
    ) {
        jeiPlusPlus$moveToBookmarkCategory(cir);
    }

    private static void jeiPlusPlus$moveToBookmarkCategory (
            CallbackInfoReturnable <ILookupState> cir
    ) {
        Optional <IRecipeCategory<?>> category =
                RecipeBookmarkNavigationContext.category();
        if (category.isPresent()) {
            cir.getReturnValue().moveToRecipeCategory(category.get());
        }
    }
}
