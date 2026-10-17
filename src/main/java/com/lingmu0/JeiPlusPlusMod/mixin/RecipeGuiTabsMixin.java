package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeGuiTabs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Optional;

/** Adds category navigation to the row of recipe-category icons. */
@Mixin(value = RecipeGuiTabs.class, remap = false)
public abstract class RecipeGuiTabsMixin {
    @Shadow @Final private IRecipeGuiLogic recipeGuiLogic;
    @Shadow private ImmutableRect2i area;
    @Shadow @Final private PageNavigation pageNavigation;
    @Shadow public abstract boolean nextPage();
    @Shadow public abstract boolean previousPage();

    @Inject(method = "createInputHandler", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$wrapTabInput(CallbackInfoReturnable<Object> cir) {
        Object delegate = cir.getReturnValue();
        Object[] combined = new Object[1];
        Object pageScrollInput = JeiReflectionCompat.customInputHandler(
            (proxy, method, args) -> jeiPlusPlus$handlePageScrollInput(combined, proxy, method, args)
        );
        combined[0] = JeiReflectionCompat.combineInputHandlers(
            "JEI++ recipe tab input handler",
            pageScrollInput,
            delegate
        );
        cir.setReturnValue(combined[0]);
    }

    @Unique
    private Object jeiPlusPlus$handlePageScrollInput(
        Object[] combined,
        Object proxy,
        Method method,
        Object[] args
    ) {
        switch (method.getName()) {
            case "equals" -> {
                return proxy == args[0];
            }
            case "hashCode" -> {
                return System.identityHashCode(proxy);
            }
            case "toString" -> {
                return "JEI++ recipe tab input handler";
            }
            case "unfocus" -> {
                return null;
            }
        }
        if (!"handleMouseScrolled".equals(method.getName())) {
            return Optional.empty();
        }

        double mouseX = ((Number) args[0]).doubleValue();
        double mouseY = ((Number) args[1]).doubleValue();
        double scrollDeltaY = ((Number) args[3]).doubleValue();
        if (scrollDeltaY == 0) {
            return Optional.empty();
        }
        if (isPageNavigationBand(mouseX, mouseY)) {
            if (scrollDeltaY < 0) {
                nextPage();
            } else {
                previousPage();
            }
            return Optional.of(combined[0]);
        }
        if (area.contains(mouseX, mouseY)) {
            if (scrollDeltaY < 0) {
                recipeGuiLogic.nextRecipeCategory();
            } else {
                recipeGuiLogic.previousRecipeCategory();
            }
            return Optional.of(combined[0]);
        }
        return Optional.empty();
    }

    /**
     * JEI's top page-number strip belongs to PageNavigation, not to the
     * RecipesGui page buttons.  Use its actual button bounds and include the
     * unbuttoned number area between them.
     */
    private boolean isPageNavigationBand(double mouseX, double mouseY) {
        ImmutableRect2i back = pageNavigation.getBackButtonArea();
        ImmutableRect2i next = pageNavigation.getNextButtonArea();
        if (back.isEmpty() || next.isEmpty()) {
            return false;
        }
        int left = Math.min(back.getX(), next.getX()) - 2;
        int right = Math.max(back.getX() + back.getWidth(), next.getX() + next.getWidth()) + 2;
        int top = Math.min(back.getY(), next.getY()) - 2;
        int bottom = Math.max(back.getY() + back.getHeight(), next.getY() + next.getHeight()) + 2;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
