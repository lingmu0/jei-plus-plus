package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Holds the current recipe tree and an optional node awaiting a recipe choice. */
public final class RecipeTreeSession {
    private static RecipeTreeData.Tree tree;
    /** The tree that owns the persistent crafting/bookmark workflow. */
    private static RecipeTreeData.Tree craftingTree;
    /** True while {@link #tree} is a temporary view opened over craftingTree. */
    private static boolean transientView;
    private static String pendingIngredient;
    private static String pendingRecipeKey;
    private static Screen resolutionParent;

    private RecipeTreeSession() {
    }

    public static RecipeTreeData.Tree tree() {
        return tree;
    }

    /** Returns the tree whose missing materials and synthetic bookmarks are active. */
    public static RecipeTreeData.Tree craftingTree() {
        if (craftingTree != null) {
            return craftingTree;
        }
        return tree != null && tree.craftingMode() ? tree : null;
    }

    public static boolean setGoal(IRecipeLayoutDrawable<?> layout) {
        RecipeTreeData.Tree replacement = RecipeTreeData.build(layout).orElse(null);
        if (replacement == null) {
            // Keep an active crafting tree intact when a button press cannot
            // produce a valid new goal.
            return false;
        }
        // Opening another recipe tree while a crafting tree is active creates
        // a temporary view. The active tree, its progress, and any pending
        // transfer remain intact until the temporary view is closed. Turning
        // the new view into crafting mode is the explicit replacement action.
        if (!transientView && tree != null && tree.craftingMode()) {
            craftingTree = tree;
        }
        if (craftingTree == null) {
            RecipeTreeTransfer.cancel();
        }
        tree = replacement;
        transientView = craftingTree != null;
        pendingIngredient = null;
        pendingRecipeKey = null;
        resolutionParent = null;
        RecipeTreeFavorites.refreshNow();
        return tree != null;
    }

    public static void clear() {
        RecipeTreeTransfer.cancel();
        tree = null;
        craftingTree = null;
        transientView = false;
        pendingIngredient = null;
        pendingRecipeKey = null;
        resolutionParent = null;
        RecipeTreeFavorites.refreshNow();
    }

    public static void beginResolution(RecipeTreeData.Node node, Screen parent) {
        pendingIngredient = node == null ? null : node.ingredientKey();
        pendingRecipeKey = node == null || node.recipe() == null ? null : node.recipe().ref().key();
        resolutionParent = pendingIngredient == null ? null : parent;
    }

    public static void cancelResolution() {
        pendingIngredient = null;
        pendingRecipeKey = null;
        resolutionParent = null;
    }

    public static boolean isResolving() {
        return pendingIngredient != null && tree != null;
    }

    public static boolean canResolve(IRecipeLayoutDrawable<?> layout) {
        if (!isResolving()) {
            return false;
        }
        return RecipeTreeData.snapshot(layout)
            .map(snapshot -> snapshot.produces(pendingIngredient))
            .orElse(false);
    }

    public static boolean isCurrentResolution(IRecipeLayoutDrawable<?> layout) {
        if (!canResolve(layout) || pendingRecipeKey == null) {
            return false;
        }
        return RecipeTreeData.snapshot(layout)
            .map(snapshot -> snapshot.ref().key().equals(pendingRecipeKey))
            .orElse(false);
    }

    public static boolean resolve(IRecipeLayoutDrawable<?> layout) {
        if (!canResolve(layout)) {
            return false;
        }
        if (isCurrentResolution(layout)) {
            tree.clearResolution(pendingIngredient);
            pendingIngredient = null;
            pendingRecipeKey = null;
            RecipeTreeFavorites.refreshNow();
            return true;
        }
        RecipeTreeData.RecipeSnapshot snapshot = RecipeTreeData.snapshot(layout).orElseThrow();
        tree.resolve(pendingIngredient, snapshot.ref());
        pendingIngredient = null;
        pendingRecipeKey = null;
        RecipeTreeFavorites.refreshNow();
        return true;
    }

    public static boolean selectInput(RecipeTreeData.Node node, ItemStack stack) {
        if (tree == null || node == null || stack == null || stack.isEmpty()) {
            return false;
        }
        String selectedKey = RecipeTreeData.ingredientKey(stack);
        boolean valid = node.alternatives().stream()
            .anyMatch(alternative -> RecipeTreeData.ingredientKey(alternative).equals(selectedKey));
        if (!valid) {
            return false;
        }
        tree.selectInput(node, stack);
        RecipeTreeFavorites.refreshNow();
        return true;
    }

    public static Screen takeResolutionParent() {
        Screen parent = resolutionParent;
        resolutionParent = null;
        return parent;
    }

    public static void clearResolution(RecipeTreeData.Node node) {
        if (tree != null && node != null) {
            tree.clearResolution(node.ingredientKey());
            RecipeTreeFavorites.refreshNow();
        }
    }

    public static void clearInputSelection(RecipeTreeData.Node node) {
        if (tree != null && node != null) {
            tree.clearInputSelection(node);
            RecipeTreeFavorites.refreshNow();
        }
    }

    /** Toggle crafting mode and make an explicitly chosen view the active tree. */
    public static void setCraftingMode(boolean enabled) {
        if (tree == null) {
            return;
        }
        if (enabled) {
            if (transientView || (craftingTree != null && craftingTree != tree)) {
                RecipeTreeTransfer.cancel();
            }
            craftingTree = tree;
            transientView = false;
            tree.setCraftingMode(true);
        } else {
            tree.setCraftingMode(false);
            if (craftingTree == tree) {
                craftingTree = null;
            }
            transientView = false;
        }
        RecipeTreeFavorites.refreshNow();
    }

    /** Restore the saved crafting tree when a temporary recipe view closes. */
    public static void restoreCraftingTree() {
        if (transientView && craftingTree != null) {
            tree = craftingTree;
            craftingTree = null;
        }
        transientView = false;
        RecipeTreeFavorites.refreshNow();
    }

    public static boolean autoResolve(RecipeTreeData.Node node) {
        if (tree == null || node == null) {
            return false;
        }
        List<RecipeTreeData.RecipeSnapshot> candidates = RecipeTreeData.candidateSnapshots(node.stack()).stream()
            .filter(candidate -> !RecipeTreeDefaults.isExcludedFromAutomaticSelection(
                candidate.ref().registryId()
            ))
            .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        // This is an explicit user action (shift-click), so resolve the first
        // non-excluded candidate without consulting inventory availability.
        tree.resolve(node.ingredientKey(), candidates.getFirst().ref());
        RecipeTreeFavorites.refreshNow();
        return true;
    }

    public static void refreshDefaults() {
        if (tree != null) {
            tree.rebuild();
        }
        if (craftingTree != null && craftingTree != tree) {
            craftingTree.rebuild();
        }
        if (tree != null || craftingTree != null) {
            RecipeTreeFavorites.refreshNow();
        }
    }

}
