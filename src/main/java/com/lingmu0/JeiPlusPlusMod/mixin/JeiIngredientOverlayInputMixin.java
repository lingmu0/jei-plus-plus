package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Register the reserved category row and give the modal picker first refusal on input. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class JeiIngredientOverlayInputMixin implements JeiExtraInputArea {
    @Override
    public boolean jeiPlusPlus$containsExtraInput(double x, double y) {
        return Boolean.TRUE.equals(JeiReflectionCompat.tryInvoke(this, "isListDisplayed"))
            && CreativeTabGridCompat.containsExtraInput(JeiReflectionCompat.readField(this, "contents"), x, y);
    }

    @Override
    public boolean jeiPlusPlus$isModalInput() {
        return CreativeTabGridCompat.ownsSelector(JeiReflectionCompat.readField(this, "contents"));
    }

    @Inject(method = "createInputArea(Lmezz/jei/common/input/IInternalKeyMappings;)Lmezz/jei/gui/input/InputArea;",
        at = @At("RETURN"), cancellable = true, remap = false)
    private void jeiPlusPlus$priorityInput(CallbackInfoReturnable<Object> cir) {
        Object contents = JeiReflectionCompat.readField(this, "contents");
        cir.setReturnValue(JeiInputAreaCompat.wrapControls(cir.getReturnValue(),
            controls -> CreativeTabGridCompat.wrapInput(contents, controls)));
    }
}
