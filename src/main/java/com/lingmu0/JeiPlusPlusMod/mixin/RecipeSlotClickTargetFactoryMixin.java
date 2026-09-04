package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.DirectoryIngredientElement;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.input.ClickableIngredientInternal;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IMouseOverable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * JEI 19.51+ path for JEI++'s multi-ingredient recipe-slot directory action.
 *
 * JEI moved click-target creation from RecipeGuiLayouts#getClickedIngredient
 * into the package-private RecipeSlotClickTargetFactory. Using a pseudo string
 * target keeps the class harmless on older JEI versions where the factory does
 * not exist, while RecipeGuiLayoutsMixin continues to service those versions.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeSlotClickTargetFactory", remap = false)
public abstract class RecipeSlotClickTargetFactoryMixin {
    @Inject(
        method = "create(Lmezz/jei/api/gui/IRecipeLayoutDrawable;DD)Ljava/util/Optional;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void jeiPlusPlus$directoryClick(
        IRecipeLayoutDrawable<?> recipeLayout,
        double mouseX,
        double mouseY,
        CallbackInfoReturnable<Optional<IClickableIngredientInternal<?>>> cir
    ) {
        Optional<RecipeSlotUnderMouse> slotUnderMouse = recipeLayout.getSlotUnderMouse(mouseX, mouseY);
        if (slotUnderMouse.isEmpty()) {
            return;
        }

        RecipeSlotUnderMouse hovered = slotUnderMouse.get();
        List<ITypedIngredient<?>> ingredients = hovered.slot().getAllIngredients().toList();
        if (ingredients.size() <= 1) {
            return;
        }

        hovered.slot().getDisplayedIngredient().ifPresent(displayed -> {
            IMouseOverable mouseOverable = (x, y) -> recipeLayout.getSlotUnderMouse(x, y)
                .map(RecipeSlotUnderMouse::slot)
                .filter(slot -> slot == hovered.slot())
                .isPresent();

            cir.setReturnValue(Optional.of(new ClickableIngredientInternal<>(
                new DirectoryIngredientElement(displayed, ingredients),
                mouseOverable,
                false,
                true
            )));
        });
    }
}
