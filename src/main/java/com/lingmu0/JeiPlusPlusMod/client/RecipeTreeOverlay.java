package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import com.lingmu0.JeiPlusPlusMod.mixin.RecipeGuiLayoutsAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.common.Internal;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Draws and routes tree/default actions for JEI 15.x recipe layouts. */
public final class RecipeTreeOverlay {
    private static final int BUTTON_SIZE = 13;
    private static final int BUTTON_GAP = 2;

    private RecipeTreeOverlay() {
    }

    public static void draw(RecipeGuiLayouts layouts, GuiGraphics graphics, int mouseX, int mouseY) {
        if (!JeiPlusPlusConfig.RECIPE_TREE_ENABLED.get()) {
            return;
        }
        for (RecipeLayoutWithButtons<?> wrapper : wrappers(layouts)) {
            IRecipeLayoutDrawable<?> layout = wrapper.recipeLayout();
            RecipeTreeData.RecipeSnapshot snapshot = RecipeTreeData.snapshot(layout).orElse(null);
            if (snapshot == null) {
                continue;
            }

            Rect2i treeArea = buttonArea(wrapper, 0);
            Rect2i defaultArea = buttonArea(wrapper, 1);
            boolean treeHovered = contains(treeArea, mouseX, mouseY);
            boolean defaultHovered = contains(defaultArea, mouseX, mouseY);
            drawButton(graphics, treeArea, treeHovered, RecipeTreeSession.isCurrentResolution(layout));
            RecipeTreeIcons.drawTree(graphics, treeArea.getX() + 2, treeArea.getY() + 2);

            RecipeTreeDefaults.Status status = RecipeTreeDefaults.getStatus(snapshot);
            drawButton(graphics, defaultArea, defaultHovered, status == RecipeTreeDefaults.Status.FULL);
            RecipeTreeIcons.drawDefault(graphics, defaultArea.getX() + 2, defaultArea.getY() + 2);

            if (treeHovered) {
                String key = RecipeTreeSession.isCurrentResolution(layout)
                    ? "jei_plus_plus.recipe_tree.clear_resolution_button"
                    : (RecipeTreeSession.canResolve(layout)
                        ? "jei_plus_plus.recipe_tree.resolve_button"
                        : "jei_plus_plus.recipe_tree.button");
                graphics.renderTooltip(Minecraft.getInstance().font, Component.translatable(key), mouseX, mouseY);
            } else if (defaultHovered) {
                String key = switch (status) {
                    case FULL -> "jei_plus_plus.recipe_tree.default.unset";
                    case PARTIAL -> "jei_plus_plus.recipe_tree.default.partial";
                    case EMPTY -> "jei_plus_plus.recipe_tree.default.set";
                };
                graphics.renderTooltip(Minecraft.getInstance().font, Component.translatable(key), mouseX, mouseY);
            }
        }
    }

    public static boolean click(RecipeGuiLayouts layouts, double mouseX, double mouseY, int button) {
        if (!JeiPlusPlusConfig.RECIPE_TREE_ENABLED.get() || button != 0) {
            return false;
        }
        for (RecipeLayoutWithButtons<?> wrapper : wrappers(layouts)) {
            IRecipeLayoutDrawable<?> layout = wrapper.recipeLayout();
            RecipeTreeData.RecipeSnapshot snapshot = RecipeTreeData.snapshot(layout).orElse(null);
            if (snapshot == null) {
                continue;
            }
            if (contains(buttonArea(wrapper, 0), mouseX, mouseY)) {
                RecipeTreeScreen.openFromRecipeButton(layout);
                return true;
            }
            if (contains(buttonArea(wrapper, 1), mouseX, mouseY)) {
                RecipeTreeDefaults.toggle(snapshot);
                return true;
            }
        }
        return false;
    }

    /**
     * Reserve only the columns that the JEI-style side-button layout actually
     * needs.  Buttons are assigned the same vertical-first indices as JEI
     * 19.x: transfer, bookmark, then the extra controls.  This keeps the
     * recipe centered when the controls fit above the transfer button and
     * grows the layout only when a new column is required.
     */
    public static int extraWidth(RecipeGuiLayouts layouts, int baseWidth) {
        if (!JeiPlusPlusConfig.RECIPE_TREE_ENABLED.get()) {
            return 0;
        }

        int requiredWidth = baseWidth;
        for (RecipeLayoutWithButtons<?> wrapper : wrappers(layouts)) {
            if (RecipeTreeData.snapshot(wrapper.recipeLayout()).isEmpty()) {
                continue;
            }
            Rect2i rect = wrapper.recipeLayout().getRect();
            Rect2i bordered = wrapper.recipeLayout().getRectWithBorder();
            Rect2i last = buttonArea(wrapper, 1);
            int leftBorder = rect.getX() - bordered.getX();
            int right = last.getX() + last.getWidth() - rect.getX();
            requiredWidth = Math.max(requiredWidth, leftBorder + right);
        }
        return Math.max(0, requiredWidth - baseWidth);
    }

    private static List<RecipeLayoutWithButtons<?>> wrappers(RecipeGuiLayouts layouts) {
        return ((RecipeGuiLayoutsAccessor) layouts).jeiPlusPlus$getRecipeLayoutsWithButtons();
    }

    private static Rect2i buttonArea(RecipeLayoutWithButtons<?> wrapper, int index) {
        IRecipeLayoutDrawable<?> layout = wrapper.recipeLayout();
        Rect2i rect = layout.getRect();
        Rect2i buttonArea = layout.getRecipeTransferButtonArea();
        int buttonIndex = index;
        if (wrapper.transferButton().isVisible()) {
            buttonIndex++;
        }
        if (wrapper.bookmarkButton().isVisible()) {
            buttonIndex++;
        }
        if (buttonArea.getWidth() <= 0 || buttonArea.getHeight() <= 0) {
            int maxRows = Math.max(1, (layout.getRectWithBorder().getHeight() + BUTTON_GAP)
                / (BUTTON_SIZE + BUTTON_GAP));
            int xIndex = buttonIndex / maxRows;
            int yIndex = buttonIndex % maxRows;
            return new Rect2i(
                rect.getX() + rect.getWidth() + BUTTON_GAP + xIndex * (BUTTON_SIZE + BUTTON_GAP),
                rect.getY() + rect.getHeight() - BUTTON_SIZE - yIndex * (BUTTON_SIZE + BUTTON_GAP),
                BUTTON_SIZE,
                BUTTON_SIZE
            );
        }

        int maxRows = Math.max(1, (layout.getRectWithBorder().getHeight() + BUTTON_GAP)
            / (buttonArea.getHeight() + BUTTON_GAP));
        int xIndex = buttonIndex / maxRows;
        int yIndex = buttonIndex % maxRows;
        buttonArea.setX(buttonArea.getX() + xIndex * (buttonArea.getWidth() + BUTTON_GAP));
        buttonArea.setY(buttonArea.getY() - yIndex * (buttonArea.getHeight() + BUTTON_GAP));
        return new Rect2i(
            rect.getX() + buttonArea.getX(),
            rect.getY() + buttonArea.getY(),
            buttonArea.getWidth(),
            buttonArea.getHeight()
        );
    }

    private static void drawButton(GuiGraphics graphics, Rect2i area, boolean hovered, boolean pressed) {
        // Use JEI's own nine-slice button texture so the extra controls have
        // the same border, hover, disabled, and pressed appearance as the
        // native bookmark/transfer buttons.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Internal.getTextures().getButtonForState(pressed, true, hovered)
            .draw(graphics, area.getX(), area.getY(), area.getWidth(), area.getHeight());
    }

    private static boolean contains(Rect2i area, double mouseX, double mouseY) {
        return mouseX >= area.getX() && mouseX < area.getX() + area.getWidth()
            && mouseY >= area.getY() && mouseY < area.getY() + area.getHeight();
    }
}
