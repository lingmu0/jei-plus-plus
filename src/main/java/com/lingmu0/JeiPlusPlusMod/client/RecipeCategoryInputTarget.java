package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/** Scrolls categories across the recipe-title navigation band. */
public record RecipeCategoryInputTarget(IRecipeGuiLogic logic, IconButton left, IconButton right) implements IInputTarget {
    @Override
    public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
        return Optional.empty();
    }

    @Override
    public boolean scroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || mouseX < left.getX() || mouseX > right.getX() + right.getWidth()
            || mouseY < left.getY() - 2 || mouseY > left.getY() + left.getHeight() + 2) return false;
        if (scrollY < 0) logic.nextRecipeCategory(); else logic.previousRecipeCategory();
        return true;
    }
}
