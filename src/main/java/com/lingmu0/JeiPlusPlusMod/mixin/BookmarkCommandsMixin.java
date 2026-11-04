package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI 19.64 replaced {@code BookmarkInputHandler} with {@code BookmarkCommands}
 * and now decides by itself whether an output slot becomes a recipe bookmark or
 * an ingredient bookmark.  Answer that decision with the JEI++ preference so the
 * output-slot behavior matches the older input handler:
 * recipe bookmarks only while JEI's BOOKMARKED sorter is enabled.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.input.handlers.BookmarkCommands", remap = false)
public abstract class BookmarkCommandsMixin {
    @Inject(
        method = "shouldBookmarkRecipe(Lmezz/jei/api/recipe/RecipeIngredientRole;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void jeiPlusPlus$preferRecipeBookmark(
        RecipeIngredientRole role,
        boolean jeiPrefersRecipe,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (role == RecipeIngredientRole.OUTPUT) {
            cir.setReturnValue(jeiPlusPlus$shouldUseRecipeBookmark());
        }
    }

    @Unique
    private static boolean jeiPlusPlus$shouldUseRecipeBookmark() {
        return JeiPlusPlusConfig.PREFER_RECIPE_BOOKMARK_ON_OUTPUT.get()
            && JeiReflectionCompat.isBookmarkedRecipeSortingEnabled();
    }
}
