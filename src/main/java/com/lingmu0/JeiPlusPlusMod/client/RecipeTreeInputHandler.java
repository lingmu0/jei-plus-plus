package com.lingmu0.JeiPlusPlusMod.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/** Wraps JEI's input handler without replacing any of its normal click behavior. */
public final class RecipeTreeInputHandler implements IUserInputHandler {
    private final IUserInputHandler delegate;
    private final IRecipeLayoutDrawable<?> layout;
    private boolean pendingAltClick;

    private RecipeTreeInputHandler(IUserInputHandler delegate, IRecipeLayoutDrawable<?> layout) {
        this.delegate = delegate;
        this.layout = layout;
    }

    public static IUserInputHandler wrap(IUserInputHandler delegate, IRecipeLayoutDrawable<?> layout) {
        return new RecipeTreeInputHandler(delegate, layout);
    }

    @Override
    public Optional<IUserInputHandler> handleUserInput(Screen screen, UserInput input,
            IInternalKeyMappings keyBindings) {
        boolean leftClick = input.getKey().getType() == InputConstants.Type.MOUSE
                && input.getKey().getValue() == 0;
        boolean hovered = layout.isMouseOver(input.getMouseX(), input.getMouseY());
        if (leftClick && hovered && (Screen.hasAltDown() || pendingAltClick)) {
            if (input.isSimulate()) {
                pendingAltClick = true;
                return Optional.of(this);
            }
            if (pendingAltClick) {
                pendingAltClick = false;
                RecipeTreeOpenHelper.openFromLayout(layout, screen);
                return Optional.of(this);
            }
        }
        if (!input.isSimulate()) {
            pendingAltClick = false;
        }
        return delegate.handleUserInput(screen, input, keyBindings)
                .map(ignored -> (IUserInputHandler) this);
    }

    @Override
    public Optional<IUserInputHandler> handleMouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return delegate.handleMouseScrolled(mouseX, mouseY, scrollDelta)
                .map(ignored -> (IUserInputHandler) this);
    }

    @Override
    public void unfocus() {
        pendingAltClick = false;
        delegate.unfocus();
    }
}
