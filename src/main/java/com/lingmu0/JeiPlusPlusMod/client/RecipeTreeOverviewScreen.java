package com.lingmu0.JeiPlusPlusMod.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeInputViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeNodeViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeRecipeViewModel;
import com.lingmu0.JeiPlusPlusMod.client.recipe_tree.RecipeTreeRootContext;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The graph view from AE2 Utility, kept independent from AE2's pattern and
 * network classes. The same column layout, framed cards, pan and zoom model
 * is used for the JEI-only recipe tree.
 */
public final class RecipeTreeOverviewScreen extends Screen {
    private static final int CARD_WIDTH = 142;
    private static final int CARD_HEIGHT = 42;
    private static final int LEVEL_GAP = 54;
    private static final int LEAF_GAP = 18;
    private static final int NODE_GAP = 12;
    private static final int SLOT_SIZE = 18;

    private final RecipeTreeRootContext context;
    private final Screen returnScreen;
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private GraphNode root;
    private Button backButton;
    private double panX;
    private double panY;
    private double zoom = 1.0D;
    private double lastMouseX;
    private double lastMouseY;
    private boolean dragging;
    private boolean initializedPan;

    public RecipeTreeOverviewScreen(RecipeTreeRootContext context, Screen returnScreen) {
        super(Component.translatable("jei_plus_plus.recipe_tree.overview"));
        this.context = context;
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();
        backButton = Button.builder(Component.translatable("jei_plus_plus.recipe_tree.back"), btn -> onClose())
                .bounds(width - 72, 10, 64, 20).build();
        addRenderableWidget(backButton);
        rebuildLayout();
    }

    private void rebuildLayout() {
        nodes.clear();
        edges.clear();
        Map<RecipeTreeNodeViewModel, GraphNode> seen = new IdentityHashMap<>();
        root = buildGraph(context.root(), seen);
        if (root != null && !initializedPan) {
            panX = width * 0.5D - root.x - CARD_WIDTH * 0.5D;
            panY = 58D - root.y;
            initializedPan = true;
        }
    }

    private GraphNode buildGraph(RecipeTreeNodeViewModel view, Map<RecipeTreeNodeViewModel, GraphNode> seen) {
        GraphNode existing = seen.get(view);
        if (existing != null) {
            return existing;
        }
        GraphNode node = new GraphNode(view.recipe(), null, false);
        seen.put(view, node);
        nodes.add(node);
        for (RecipeTreeInputViewModel input : view.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child != null && !view.containsRecipe(child.recipe())) {
                GraphNode childNode = buildGraph(child, seen);
                node.children.add(childNode);
                edges.add(new GraphEdge(node, childNode));
            } else {
                GraphNode leaf = new GraphNode(null, input, true);
                nodes.add(leaf);
                node.children.add(leaf);
                edges.add(new GraphEdge(node, leaf));
            }
        }
        return node;
    }

    private int subtreeWidth(GraphNode node) {
        if (node.children.isEmpty()) {
            return CARD_WIDTH;
        }
        int total = 0;
        for (GraphNode child : node.children) {
            if (total > 0) {
                total += LEAF_GAP;
            }
            total += subtreeWidth(child);
        }
        return Math.max(CARD_WIDTH, total);
    }

    private void layout(GraphNode node, int depth, int left) {
        int width = subtreeWidth(node);
        int childWidth = 0;
        for (GraphNode child : node.children) {
            if (childWidth > 0) {
                childWidth += LEAF_GAP;
            }
            childWidth += subtreeWidth(child);
        }
        node.x = left + Math.max(0, (width - CARD_WIDTH) / 2);
        node.y = 58 + depth * LEVEL_GAP;
        int childLeft = left + Math.max(0, (width - childWidth) / 2);
        for (GraphNode child : node.children) {
            layout(child, depth + 1, childLeft);
            childLeft += subtreeWidth(child) + LEAF_GAP;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // The graph supplies its own tiled-looking backdrop in render().
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF11161A);
        graphics.fill(0, 0, width, 48, 0xFF283039);
        if (root != null) {
            layout(root, 0, 0);
            graphics.pose().pushPose();
            graphics.pose().translate(panX, panY, 0.0F);
            graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
            renderEdges(graphics);
            renderNodes(graphics, mouseX, mouseY);
            graphics.pose().popPose();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, 10, 12, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("jei_plus_plus.recipe_tree.overview_hint"), 10, 28,
                0xFFD7E2EB, false);
    }

    private void renderEdges(GuiGraphics graphics) {
        for (GraphEdge edge : edges) {
            int startX = edge.from.x + CARD_WIDTH / 2;
            int startY = edge.from.y + CARD_HEIGHT;
            int endX = edge.to.x + CARD_WIDTH / 2;
            int endY = edge.to.y;
            int middleY = startY + Math.max(4, (endY - startY) / 2);
            fillLine(graphics, startX, startY, startX, middleY, 0xFF6F7E8A);
            fillLine(graphics, startX, middleY, endX, middleY, 0xFF6F7E8A);
            fillLine(graphics, endX, middleY, endX, endY, 0xFF6F7E8A);
        }
    }

    private void renderNodes(GuiGraphics graphics, int mouseX, int mouseY) {
        for (GraphNode node : nodes) {
            int color = node.leaf ? 0xFF35444C : 0xFF2D536D;
            graphics.fill(node.x, node.y, node.x + CARD_WIDTH, node.y + CARD_HEIGHT, 0xFF111820);
            graphics.fill(node.x + 1, node.y + 1, node.x + CARD_WIDTH - 1, node.y + CARD_HEIGHT - 1, color);
            graphics.fill(node.x + 3, node.y + 3, node.x + CARD_WIDTH - 3, node.y + CARD_HEIGHT - 3, 0xFFECF2F5);

            ITypedIngredient<?> ingredient = node.leaf ? node.input.displayIngredient() : node.recipe.primaryOutputIngredient();
            renderIngredient(graphics, ingredient, node.x + 6, node.y + 12);
            Component label = node.leaf ? Component.literal(node.input.displayName()) : node.recipe.title();
            String text = font.substrByWidth(label, CARD_WIDTH - 34).getString();
            graphics.drawString(font, Component.literal(text), node.x + 28, node.y + 7,
                    node.leaf ? 0xFFEBF2F5 : 0xFF18242C, false);
            String amount = node.leaf ? node.input.amountText() : node.recipe.primaryOutputCount() > 1
                    ? "x" + node.recipe.primaryOutputCount() : "";
            if (!amount.isBlank()) {
                graphics.drawString(font, Component.literal(amount), node.x + 28, node.y + 22, 0xFF53636E, false);
            }
        }
    }

    private void renderIngredient(GuiGraphics graphics, @Nullable ITypedIngredient<?> ingredient, int x, int y) {
        if (ingredient == null) {
            return;
        }
        ItemStack stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).map(ItemStack::copy).orElse(ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
            return;
        }
        IIngredientManager manager = getIngredientManager();
        if (manager == null) {
            return;
        }
        renderTypedIngredient(graphics, manager, ingredient, x, y);
    }

    private static <T> void renderTypedIngredient(GuiGraphics graphics, IIngredientManager manager,
            ITypedIngredient<?> raw, int x, int y) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> ingredient = (ITypedIngredient<T>) raw;
        IIngredientRenderer<T> renderer = manager.getIngredientRenderer(ingredient.getType());
        renderer.render(graphics, ingredient.getIngredient(), x, y);
    }

    private static @Nullable IIngredientManager getIngredientManager() {
        var runtime = DirectoryRecipePlugin.getJeiRuntime();
        return runtime == null ? null : runtime.getIngredientManager();
    }

    private static void fillLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            graphics.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
        } else if (y1 == y2) {
            graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (hasControlDown()) {
            zoom = Math.max(0.5D, Math.min(1.8D, zoom + scrollDelta * 0.08D));
        } else {
            panY += scrollDelta * 18.0D;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            dragging = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            panX += mouseX - lastMouseX;
            panY += mouseY - lastMouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(returnScreen);
    }

    private static final class GraphNode {
        private final @Nullable RecipeTreeRecipeViewModel recipe;
        private final @Nullable RecipeTreeInputViewModel input;
        private final boolean leaf;
        private final List<GraphNode> children = new ArrayList<>();
        private int x;
        private int y;

        private GraphNode(@Nullable RecipeTreeRecipeViewModel recipe, @Nullable RecipeTreeInputViewModel input,
                boolean leaf) {
            this.recipe = recipe;
            this.input = input;
            this.leaf = leaf;
        }
    }

    private record GraphEdge(GraphNode from, GraphNode to) {
    }
}
