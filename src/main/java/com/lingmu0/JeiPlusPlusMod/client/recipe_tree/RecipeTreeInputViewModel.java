package com.lingmu0.JeiPlusPlusMod.client.recipe_tree;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** One ingredient row in the recipe tree, including JEI alternatives. */
public final class RecipeTreeInputViewModel {
    private final RecipeTreeRequestedIngredient requestedIngredient;
    private final List<DisplayOption> displayOptions;
    private final int amount;
    private final String amountText;
    private RecipeTreeNodeViewModel child;
    private int selectedAlternativeIndex;

    public RecipeTreeInputViewModel(RecipeTreeRequestedIngredient requestedIngredient,
            List<DisplayOption> displayOptions, int amount, String amountText) {
        this.requestedIngredient = requestedIngredient == null ? null : requestedIngredient.copy();
        this.displayOptions = List.copyOf(new ArrayList<>(displayOptions));
        this.amount = Math.max(1, amount);
        this.amountText = amountText == null ? "" : amountText;
    }

    public ItemStack displayStack() {
        DisplayOption option = displayOption();
        if (option != null && !option.itemStack().isEmpty()) {
            return option.itemStack().copyWithCount(Math.max(1, amount));
        }
        return ItemStack.EMPTY;
    }

    public ITypedIngredient<?> displayIngredient() {
        DisplayOption option = displayOption();
        return option == null ? null : option.typedIngredient();
    }

    public String displayName() {
        DisplayOption option = displayOption();
        return option == null ? "" : option.label();
    }

    public int amount() {
        return amount;
    }

    public String amountText() {
        return amountText;
    }

    public List<DisplayOption> displayOptions() {
        return displayOptions;
    }

    public RecipeTreeNodeViewModel child() {
        return child;
    }

    public void setChild(RecipeTreeNodeViewModel child) {
        this.child = child;
    }

    public boolean hasAlternativeChoices() {
        return displayOptions.size() > 1;
    }

    public void cycleAlternative() {
        if (displayOptions.size() > 1) {
            selectedAlternativeIndex = (selectedAlternativeIndex + 1) % displayOptions.size();
        }
    }

    public RecipeTreeInputViewModel(List<DisplayOption> displayOptions, int amount, String amountText) {
        this(null, displayOptions, amount, amountText);
    }

    public RecipeTreeRequestedIngredient requestedIngredient() {
        return requestedIngredient == null ? null : requestedIngredient.copy();
    }

    /** Selects the same alternative from the AE2-Utility tree interaction. */
    public void selectAlternative(int index) {
        if (!displayOptions.isEmpty()) {
            selectedAlternativeIndex = Math.max(0, Math.min(displayOptions.size() - 1, index));
        }
    }

    public int selectedAlternativeIndex() {
        return selectedAlternativeIndex;
    }

    private DisplayOption displayOption() {
        if (displayOptions.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(selectedAlternativeIndex, displayOptions.size() - 1));
        return displayOptions.get(index);
    }

    public record DisplayOption(ITypedIngredient<?> typedIngredient, String label, ItemStack itemStack) {
        public DisplayOption {
            label = label == null ? "" : label;
            itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
        }
    }
}
