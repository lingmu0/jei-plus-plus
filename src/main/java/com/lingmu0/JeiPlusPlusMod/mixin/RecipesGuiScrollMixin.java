package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeGuiTabs;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * JEI 19.64 removed {@code RecipesGui.mouseScrolled} and moved wheel handling
 * into input targets. The category scroll handler is the first controller of
 * the RecipesGui input group, so intercepting it keeps both navigation rows
 * wheel-aware exactly like {@link RecipesGuiMixin} and
 * {@link RecipeGuiTabsMixin} do for older builds.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui$RecipeCategoryScrollInputHandler", remap = false)
public abstract class RecipesGuiScrollMixin {
    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void jeiPlusPlus$scrollNavigation(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (scrollY == 0) {
            return;
        }
        RecipesGui recipesGui = jeiPlusPlus$recipesGui();
        if (recipesGui == null) {
            return;
        }
        RecipesGuiScrollAccessor accessor = (RecipesGuiScrollAccessor) (Object) recipesGui;
        IRecipeGuiLogic logic = accessor.jeiPlusPlus$scrollLogic();
        if (logic == null) {
            return;
        }

        IconButton previousCategory = accessor.jeiPlusPlus$scrollPreviousCategory();
        IconButton nextCategory = accessor.jeiPlusPlus$scrollNextCategory();
        if (jeiPlusPlus$insideButtonBand(mouseX, mouseY, previousCategory, nextCategory)) {
            jeiPlusPlus$cycleCategory(logic, scrollY);
            cir.setReturnValue(true);
            return;
        }

        RecipeGuiTabs tabs = accessor.jeiPlusPlus$scrollTabs();
        if (tabs == null) {
            return;
        }
        RecipeGuiTabsAccessor tabsAccessor = (RecipeGuiTabsAccessor) (Object) tabs;
        PageNavigation pageNavigation = tabsAccessor.jeiPlusPlus$getPageNavigation();
        if (pageNavigation != null && jeiPlusPlus$insideAreaBand(
            mouseX,
            mouseY,
            pageNavigation.getBackButtonArea(),
            pageNavigation.getNextButtonArea()
        )) {
            if (scrollY < 0) {
                tabs.nextPage();
            } else {
                tabs.previousPage();
            }
            cir.setReturnValue(true);
            return;
        }
        ImmutableRect2i tabArea = tabsAccessor.jeiPlusPlus$getArea();
        if (tabArea != null && tabArea.contains(mouseX, mouseY)) {
            jeiPlusPlus$cycleCategory(logic, scrollY);
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static void jeiPlusPlus$cycleCategory(IRecipeGuiLogic logic, double scrollY) {
        if (scrollY < 0) {
            logic.nextRecipeCategory();
        } else {
            logic.previousRecipeCategory();
        }
    }

    @Unique
    private RecipesGui jeiPlusPlus$recipesGui() {
        for (Class<?> type = getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!RecipesGui.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(this);
                    return value instanceof RecipesGui recipesGui ? recipesGui : null;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @Unique
    private static boolean jeiPlusPlus$insideButtonBand(double mouseX, double mouseY, IconButton left, IconButton right) {
        if (left == null || right == null) {
            return false;
        }
        int leftEdge = left.getX();
        int rightEdge = right.getX() + right.getWidth();
        int top = left.getY() - 2;
        int bottom = left.getY() + left.getHeight() + 2;
        return mouseX >= leftEdge && mouseX <= rightEdge && mouseY >= top && mouseY <= bottom;
    }

    /**
     * The page-number strip belongs to PageNavigation, not to the RecipesGui
     * page buttons.  Use its actual button bounds and include the unbuttoned
     * number area between them.
     */
    @Unique
    private static boolean jeiPlusPlus$insideAreaBand(
        double mouseX,
        double mouseY,
        ImmutableRect2i back,
        ImmutableRect2i next
    ) {
        if (back == null || next == null || back.isEmpty() || next.isEmpty()) {
            return false;
        }
        int left = Math.min(back.getX(), next.getX()) - 2;
        int right = Math.max(back.getX() + back.getWidth(), next.getX() + next.getWidth()) + 2;
        int top = Math.min(back.getY(), next.getY()) - 2;
        int bottom = Math.max(back.getY() + back.getHeight(), next.getY() + next.getHeight()) + 2;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }
}
