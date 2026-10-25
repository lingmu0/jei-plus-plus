package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import com.lingmu0.JeiPlusPlusMod.client.CreativeTabGridCompat;
import com.lingmu0.JeiPlusPlusMod.client.DirectoryIngredientElement;
import com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/** Adds a directory action to cycling/multi-ingredient recipe slots. */
@Mixin(value = RecipeGuiLayouts.class, remap = false)
public abstract class RecipeGuiLayoutsMixin {
    @Inject(method = "drawTooltips", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void jeiPlusPlus$hideRecipeTooltipsWhileSelectorOpen(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
    ) {
        if (CreativeTabGridCompat.isAnySelectorOpen()) {
            ci.cancel();
        }
    }

    /** JEI 15.20 and older kept the click target helper in this class. */
    @Inject(
        method = "getClickedIngredient",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void jeiPlusPlus$directoryClick(
        RecipeSlotUnderMouse slotUnderMouse,
        CallbackInfoReturnable<Optional<IClickableIngredientInternal<?>>> cir
    ) {
        if (!JeiPlusPlusConfig.RECIPE_INGREDIENT_DIRECTORY_ENABLED.get()) {
            return;
        }
        List<ITypedIngredient<?>> ingredients = slotUnderMouse.slot().getAllIngredients().toList();
        if (ingredients.size() <= 1) {
            return;
        }

        slotUnderMouse.slot().getDisplayedIngredient().ifPresent(displayed -> {
            cir.setReturnValue(Optional.of(JeiReflectionCompat.clickableIngredient(
                new DirectoryIngredientElement(displayed, ingredients),
                slotUnderMouse::isMouseOver,
                false,
                true
            )));
        });
    }
}
