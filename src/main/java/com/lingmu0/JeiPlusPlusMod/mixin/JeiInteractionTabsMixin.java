package com.lingmu0.JeiPlusPlusMod.mixin;
import com.lingmu0.JeiPlusPlusMod.client.JeiReflectionCompat;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native interaction-based JEI category-row scrolling. */
@Pseudo
@Mixin(targets="mezz.jei.gui.recipes.RecipeGuiTabs", remap=false)
public abstract class JeiInteractionTabsMixin {
    @Shadow @Final private IRecipeGuiLogic recipeGuiLogic;
    @Shadow private ImmutableRect2i area;
    @Shadow public abstract boolean nextPage();
    @Shadow public abstract boolean previousPage();

    @Inject(method="scroll", at=@At("HEAD"), cancellable=true, remap=false)
    private void jeiPlusPlus$scroll(double x, double y, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (dy == 0) return;
        Object pages = JeiReflectionCompat.readField(this, "pageNavigation");
        Object previous = JeiReflectionCompat.tryInvoke(pages, "getBackButtonArea");
        Object next = JeiReflectionCompat.tryInvoke(pages, "getNextButtonArea");
        if (previous instanceof ImmutableRect2i left && next instanceof ImmutableRect2i right
            && x >= left.getX()-2 && x <= right.getX()+right.getWidth()+2
            && y >= left.getY()-2 && y <= left.getY()+left.getHeight()+2) {
            if (dy < 0) nextPage(); else previousPage();
            cir.setReturnValue(true);
        } else if (area.contains(x,y)) {
            if (dy < 0) recipeGuiLogic.nextRecipeCategory(); else recipeGuiLogic.previousRecipeCategory();
            cir.setReturnValue(true);
        }
    }
}
