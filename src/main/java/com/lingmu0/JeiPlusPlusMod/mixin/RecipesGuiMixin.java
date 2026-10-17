package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.RecipeCategoryInputTarget;
import mezz.jei.common.input.handlers.InputGroup;
import mezz.jei.gui.input.InputArea;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes both JEI navigation rows and the page-number band wheel-aware. */
@Mixin(value = RecipesGui.class, remap = false)
public abstract class RecipesGuiMixin {
    @Shadow @Final private IRecipeGuiLogic logic;
    @Shadow @Final private IconButton nextRecipeCategory;
    @Shadow @Final private IconButton previousRecipeCategory;

    @Inject(method = "createInputArea", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$scrollNavigation(CallbackInfoReturnable<InputArea> cir) {
        InputArea area = cir.getReturnValue();
        cir.setReturnValue(new InputArea(area.name(), area.area(), area.blocksUnhandledMouseInput(),
            new InputGroup("JeiPlusPlusRecipeNavigation",
                new RecipeCategoryInputTarget(logic, previousRecipeCategory, nextRecipeCategory), area.controls()),
            area.commands(), area.dragSources(), area.apiDragHandler()));
    }
}
