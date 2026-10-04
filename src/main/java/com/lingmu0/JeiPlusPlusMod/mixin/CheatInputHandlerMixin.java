package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.client.IngredientListFeatures;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.input.handlers.SameElementInputHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Give Alt+click on a JEI++ group priority over JEI's cheat-item click. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.input.handlers.CheatInputHandler", remap = false)
public abstract class CheatInputHandlerMixin {
    @Shadow @Final private CombinedRecipeFocusSource focusSource;
    @Shadow @Final private IClientToggleState toggleState;

    @Inject(method = "handleUserInput", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void jeiPlusPlus$expandGroupInCheatMode(
        Screen screen,
        UserInput input,
        IInternalKeyMappings keyMappings,
        CallbackInfoReturnable<Optional<IUserInputHandler>> cir
    ) {
        if (!toggleState.isCheatItemsEnabled()
            || !(screen instanceof AbstractContainerScreen)
            || !IngredientListFeatures.isAltLeftClick(input)) {
            return;
        }

        Optional<IClickableIngredientInternal<?>> hovered = focusSource.getIngredientUnderMouse(input, keyMappings)
            .findFirst();
        if (hovered.isEmpty()) {
            return;
        }
        IClickableIngredientInternal<?> clicked = hovered.get();
        if (IngredientListFeatures.isGroupElement(clicked.getElement())
            && clicked.getElement().handleClick(input, keyMappings)) {
            cir.setReturnValue(Optional.of(new SameElementInputHandler(
                (IUserInputHandler) (Object) this,
                clicked::isMouseOver
            )));
        }
    }
}
