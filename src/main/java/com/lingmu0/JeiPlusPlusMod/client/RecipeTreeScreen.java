package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeExpansionResolver;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeInputViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeNodeViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeRecipeViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeRootContext;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, dependency-free recipe tree viewer.  Inputs can be expanded to any
 * JEI recipe producing that ingredient; recipes which occur in the parent
 * chain are rejected so cyclic recipe sets cannot lock up the screen.
 */
public final class RecipeTreeScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 360;
    private static final int HEADER_HEIGHT = 34;
    private static final int NODE_HEIGHT = 34;
    private static final int INPUT_HEIGHT = 24;
    private static final int INDENT = 18;
    private static final int ROW_LEFT = 12;
    private static final int FOOTER_HEIGHT = 28;
    private static final int SELECTION_WIDTH = 248;
    private static final int SELECTION_CARD_HEIGHT = 34;

    private final RecipeTreeRootContext context;
    private final List<Row> rows = new ArrayList<>();
    private List<RecipeTreeRecipeViewModel> candidates = List.of();
    private RecipeTreeNodeViewModel pendingParent;
    private RecipeTreeInputViewModel pendingInput;
    private Button backButton;
    private int leftPos;
    private int topPos;
    private int treeScroll;
    private int selectionScroll;
    private int contentHeight;

    public RecipeTreeScreen(RecipeTreeRootContext context) {
        super(Component.translatable("jei_plus_plus.recipe_tree.title"));
        this.context = context;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = Math.max(8, (width - PANEL_WIDTH) / 2);
        topPos = Math.max(8, (height - PANEL_HEIGHT) / 2);
        backButton = Button.builder(Component.translatable("jei_plus_plus.recipe_tree.back"), button -> onClose())
                .bounds(leftPos + PANEL_WIDTH - 72, topPos + PANEL_HEIGHT - 23, 64, 18).build();
        addRenderableWidget(backButton);
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        contentHeight = collectRows(context.root(), 0, 0);
        treeScroll = clamp(treeScroll, 0, Math.max(0, contentHeight - viewHeight()));
    }

    private int collectRows(RecipeTreeNodeViewModel node, int depth, int y) {
        if (depth > 64) {
            return y;
        }
        rows.add(Row.node(node, depth, y));
        y += NODE_HEIGHT;
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            rows.add(Row.input(node, input, depth + 1, y));
            y += INPUT_HEIGHT;
            if (input.child() != null) {
                y = collectRows(input.child(), depth + 2, y);
            }
        }
        return y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        rebuildRows();
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, 0xE8101018);
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + HEADER_HEIGHT, 0xFF25253A);
        graphics.drawString(font, Component.translatable("jei_plus_plus.recipe_tree.root"), leftPos + 12,
                topPos + 11, 0xFFFFFFFF, false);

        int viewTop = topPos + HEADER_HEIGHT + 4;
        int viewBottom = topPos + PANEL_HEIGHT - FOOTER_HEIGHT;
        for (Row row : rows) {
            int rowTop = viewTop + row.relativeY - treeScroll;
            if (rowTop + row.height < viewTop || rowTop > viewBottom) {
                continue;
            }
            if (row.node != null) {
                drawNode(graphics, row.node, row.depth, rowTop);
            } else {
                drawInput(graphics, row.input, row.parent, row.depth, rowTop, mouseX, mouseY);
            }
        }
        if (contentHeight > viewHeight()) {
            drawScrollBar(graphics, viewTop, viewBottom, treeScroll, contentHeight - viewHeight());
        }
        if (!candidates.isEmpty() || pendingInput != null) {
            drawSelectionPanel(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawNode(GuiGraphics graphics, RecipeTreeNodeViewModel node, int depth, int rowTop) {
        int x = leftPos + ROW_LEFT + depth * INDENT;
        graphics.fill(x - 4, rowTop, leftPos + PANEL_WIDTH - 12, rowTop + NODE_HEIGHT - 2, 0x80303046);
        drawIngredient(graphics, node.recipe().primaryOutputIngredient(), node.recipe().primaryOutput(), x, rowTop + 7);
        graphics.drawString(font, node.recipe().title(), x + 24, rowTop + 5, 0xFFFFFFFF, false);
        if (!node.recipe().subtitle().getString().isBlank()) {
            graphics.drawString(font, node.recipe().subtitle(), x + 24, rowTop + 18, 0xFFAAAAAA, false);
        }
    }

    private void drawInput(GuiGraphics graphics, RecipeTreeInputViewModel input, RecipeTreeNodeViewModel parent,
            int depth, int rowTop, int mouseX, int mouseY) {
        int x = leftPos + ROW_LEFT + depth * INDENT;
        int buttonX = leftPos + PANEL_WIDTH - 90;
        boolean hovered = mouseX >= leftPos && mouseX <= leftPos + PANEL_WIDTH
                && mouseY >= rowTop && mouseY < rowTop + INPUT_HEIGHT;
        if (hovered) {
            graphics.fill(leftPos + 6, rowTop, leftPos + PANEL_WIDTH - 6, rowTop + INPUT_HEIGHT - 2, 0x503F3F62);
        }
        drawIngredient(graphics, input.displayIngredient(), input.displayStack(), x, rowTop + 3);
        String amount = input.amountText().isBlank() ? "x" + input.amount() : input.amountText();
        graphics.drawString(font, amount + " " + input.displayName(), x + 24, rowTop + 7, 0xFFE0E0E0, false);
        graphics.drawString(font, input.child() == null ? "+" : "-", buttonX, rowTop + 7, 0xFFFFFFFF, false);
        if (input.hasAlternativeChoices()) {
            graphics.drawString(font, "…", buttonX + 22, rowTop + 7, 0xFFE0C070, false);
        }
    }

    private void drawSelectionPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = Math.min(width - SELECTION_WIDTH - 8, leftPos + PANEL_WIDTH + 8);
        int y = topPos + 8;
        int bottom = topPos + PANEL_HEIGHT - 8;
        graphics.fill(x, y, x + SELECTION_WIDTH, bottom, 0xF0181824);
        graphics.drawString(font, Component.translatable("jei_plus_plus.recipe_tree.select"), x + 8, y + 8,
                0xFFFFFFFF, false);
        int rowY = y + 26 - selectionScroll;
        for (int i = 0; i < candidates.size(); i++) {
            if (rowY + SELECTION_CARD_HEIGHT >= y + 24 && rowY <= bottom - 6) {
                boolean hovered = mouseX >= x + 4 && mouseX <= x + SELECTION_WIDTH - 4
                        && mouseY >= rowY && mouseY < rowY + SELECTION_CARD_HEIGHT;
                graphics.fill(x + 4, rowY, x + SELECTION_WIDTH - 4, rowY + SELECTION_CARD_HEIGHT - 2,
                        hovered ? 0xFF4E4E70 : 0xFF303044);
                RecipeTreeRecipeViewModel candidate = candidates.get(i);
                drawIngredient(graphics, candidate.primaryOutputIngredient(), candidate.primaryOutput(), x + 10,
                        rowY + 7);
                graphics.drawString(font, candidate.title(), x + 34, rowY + 6, 0xFFFFFFFF, false);
                graphics.drawString(font, candidate.subtitle(), x + 34, rowY + 19, 0xFFAAAAAA, false);
            }
            rowY += SELECTION_CARD_HEIGHT;
        }
        if (candidates.isEmpty()) {
            graphics.drawString(font, Component.translatable("jei_plus_plus.recipe_tree.no_recipes"), x + 8, y + 34,
                    0xFFAAAAAA, false);
        }
    }

    private void drawIngredient(GuiGraphics graphics, ITypedIngredient<?> typed, ItemStack fallback, int x, int y) {
        if (typed != null) {
            typed.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(stack -> {
                ItemStack copy = stack.copy();
                if (!fallback.isEmpty()) {
                    copy.setCount(Math.max(1, fallback.getCount()));
                }
                graphics.renderItem(copy, x, y);
                graphics.renderItemDecorations(font, copy, x, y);
            });
            if (typed.getIngredient(VanillaTypes.ITEM_STACK).isEmpty()) {
                renderTyped(graphics, typed, x, y);
            }
        } else if (!fallback.isEmpty()) {
            graphics.renderItem(fallback, x, y);
            graphics.renderItemDecorations(font, fallback, x, y);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void renderTyped(GuiGraphics graphics, ITypedIngredient<?> raw, int x, int y) {
        IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
        if (runtime == null) {
            return;
        }
        IIngredientManager manager = runtime.getIngredientManager();
        IIngredientRenderer renderer = manager.getIngredientRenderer(raw.getType());
        renderer.render(graphics, raw.getIngredient(), x, y);
    }

    private void drawScrollBar(GuiGraphics graphics, int top, int bottom, int scroll, int maxScroll) {
        int barHeight = Math.max(10, (bottom - top) * (bottom - top) / Math.max(1, contentHeight));
        int barTop = top + (bottom - top - barHeight) * scroll / Math.max(1, maxScroll);
        graphics.fill(leftPos + PANEL_WIDTH - 5, barTop, leftPos + PANEL_WIDTH - 2, barTop + barHeight, 0xFF777799);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (pendingInput != null) {
            int x = Math.min(width - SELECTION_WIDTH - 8, leftPos + PANEL_WIDTH + 8);
            int y = topPos + 34 - selectionScroll;
            for (int i = 0; i < candidates.size(); i++) {
                if (mouseX >= x + 4 && mouseX <= x + SELECTION_WIDTH - 4 && mouseY >= y
                        && mouseY < y + SELECTION_CARD_HEIGHT) {
                    RecipeTreeRecipeViewModel candidate = candidates.get(i);
                    pendingInput.setChild(new RecipeTreeNodeViewModel(candidate, pendingParent));
                    closeSelection();
                    return true;
                }
                y += SELECTION_CARD_HEIGHT;
            }
            return true;
        }
        int viewTop = topPos + HEADER_HEIGHT + 4;
        for (Row row : rows) {
            int rowTop = viewTop + row.relativeY - treeScroll;
            if (row.input == null || mouseY < rowTop || mouseY >= rowTop + INPUT_HEIGHT
                    || mouseX < leftPos || mouseX > leftPos + PANEL_WIDTH) {
                continue;
            }
            int buttonX = leftPos + PANEL_WIDTH - 90;
            if (mouseX >= buttonX + 18 && row.input.hasAlternativeChoices()) {
                row.input.cycleAlternative();
                row.input.setChild(null);
                rebuildRows();
            } else if (mouseX >= buttonX || row.input.child() == null) {
                openSelection(row.parent, row.input);
            } else {
                row.input.setChild(null);
                rebuildRows();
            }
            return true;
        }
        return false;
    }

    private void openSelection(RecipeTreeNodeViewModel parent, RecipeTreeInputViewModel input) {
        pendingParent = parent;
        pendingInput = input;
        candidates = RecipeTreeExpansionResolver.resolveCandidates(input).stream()
                .filter(candidate -> !parent.containsRecipe(candidate)).limit(128).toList();
        selectionScroll = 0;
    }

    private void closeSelection() {
        pendingParent = null;
        pendingInput = null;
        candidates = List.of();
        selectionScroll = 0;
        rebuildRows();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        if (pendingInput != null) {
            selectionScroll = clamp(selectionScroll - (int) Math.signum(scrollY) * SELECTION_CARD_HEIGHT,
                    0, Math.max(0, candidates.size() * SELECTION_CARD_HEIGHT - (PANEL_HEIGHT - 40)));
            return true;
        }
        treeScroll = clamp(treeScroll - (int) Math.signum(scrollY) * INPUT_HEIGHT, 0,
                Math.max(0, contentHeight - viewHeight()));
        return true;
    }

    private int viewHeight() {
        return PANEL_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT - 4;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(context.returnScreen());
    }

    private static final class Row {
        private final RecipeTreeNodeViewModel node;
        private final RecipeTreeNodeViewModel parent;
        private final RecipeTreeInputViewModel input;
        private final int depth;
        private final int relativeY;
        private final int height;

        private Row(RecipeTreeNodeViewModel node, RecipeTreeNodeViewModel parent, RecipeTreeInputViewModel input,
                int depth, int relativeY, int height) {
            this.node = node;
            this.parent = parent;
            this.input = input;
            this.depth = depth;
            this.relativeY = relativeY;
            this.height = height;
        }

        private static Row node(RecipeTreeNodeViewModel node, int depth, int y) {
            return new Row(node, null, null, depth, y, NODE_HEIGHT);
        }

        private static Row input(RecipeTreeNodeViewModel parent, RecipeTreeInputViewModel input, int depth, int y) {
            return new Row(null, parent, input, depth, y, INPUT_HEIGHT);
        }
    }
}
