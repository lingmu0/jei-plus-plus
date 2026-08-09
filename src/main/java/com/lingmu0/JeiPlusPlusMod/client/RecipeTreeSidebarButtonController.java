package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

/** Controller used by JEI's own IconButton so this entry matches bookmarks/history exactly. */
public final class RecipeTreeSidebarButtonController implements IIconButtonController {
    private static final IDrawable BLANK = new IDrawable() {
        @Override public int getWidth() { return 18; }
        @Override public int getHeight() { return 18; }
        @Override public void draw(GuiGraphics graphics, int xOffset, int yOffset) { }
    };

    @Override
    public void initState(IButtonState state) {
        state.setIcon(BLANK);
        updateState(state);
    }

    @Override
    public void updateState(IButtonState state) {
        state.setActive(JeiPlusPlusConfig.RECIPE_TREE_ENABLED.get());
        state.setForcePressed(RecipeTreeSession.tree() != null);
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (!JeiPlusPlusConfig.RECIPE_TREE_ENABLED.get()) {
            return false;
        }
        if (!input.isSimulate()) {
            if (Minecraft.getInstance().screen instanceof RecipeTreeScreen treeScreen) {
                treeScreen.onClose();
            } else {
                RecipeTreeScreen.openCurrent(Minecraft.getInstance().screen);
            }
        }
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("jei_plus_plus.recipe_tree.global_button"));
        if (RecipeTreeSession.tree() == null) {
            tooltip.add(Component.translatable("jei_plus_plus.recipe_tree.empty_hint"));
        } else {
            tooltip.add(Component.translatable("jei_plus_plus.recipe_tree.global_button.clear"));
        }
    }

    @Override
    public void drawExtras(GuiGraphics graphics, Rect2i area, int mouseX, int mouseY, float partialTicks) {
        int x = area.getX() + Math.max(0, (area.getWidth() - 18) / 2);
        int y = area.getY() + Math.max(0, (area.getHeight() - 18) / 2);
        RecipeTreeIcons.drawGlobalTree(graphics, x, y);
    }
}
