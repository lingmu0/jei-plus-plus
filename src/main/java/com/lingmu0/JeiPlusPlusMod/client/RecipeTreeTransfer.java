package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Exact-count JEI transfer plus a bottom-up queue for instant crafting stations. */
public final class RecipeTreeTransfer {
    private static final int AE2_WAIT_FRAMES = 600;
    private static List<PendingCraft> pendingCrafts = List.of();
    private static int pendingCraftIndex;
    private static int pendingMenuId = -1;
    private static int pendingResultSlot = -1;
    private static int pendingChunk;
    private static boolean pendingAe2Carry;
    private static boolean pendingAe2NetworkDeposit;
    private static boolean pendingAe2NetworkRequest;
    private static int pendingAe2NetworkCount;
    private static int pendingAe2PlacementSlot = -1;
    private static boolean pendingAe2PlacementInFlight;
    private static int pendingAe2PlacementCount;
    private static long pendingAe2PlacementInventoryBefore;
    private static String pendingAe2OutputKey = "";
    private static long pendingAe2InventoryBefore;
    private static boolean pendingVanillaPickup;
    private static int pendingPickupAttempts;
    private static int waitFrames;
    private static int emptyFrames;

    private RecipeTreeTransfer() {
    }

    public static void cancel() {
        clearPending();
    }

    public static boolean canTransfer(RecipeTreeData.CraftStep step, boolean recursive) {
        RecipeTreeData.CraftStep first = firstStep(step, recursive);
        if (first == null) {
            return false;
        }
        int chunk = transferChunk(first);
        return chunk > 0 && runTransfer(first, chunk, false);
    }

    public static boolean transfer(RecipeTreeData.CraftStep step, boolean recursive) {
        clearPending();
        if (!recursive) {
            if (!exposeParentContainer()) {
                return false;
            }
            int chunk = transferChunk(step);
            return chunk > 0 && runTransfer(step, chunk, true);
        }

        RecipeTreeData.Tree tree = RecipeTreeSession.craftingTree();
        if (tree == null) {
            return false;
        }
        List<RecipeTreeData.CraftStep> plan = tree.recursiveCraftingSteps(step);
        if (plan.isEmpty()) {
            return false;
        }
        pendingCrafts = plan.stream()
            .filter(candidate -> candidate.batches() > 0)
            .map(candidate -> new PendingCraft(candidate, candidate.batches()))
            .toList();
        if (!exposeParentContainer()) {
            clearPending();
            return false;
        }
        return startNextCraft();
    }

    /** Called from container rendering so each transfer/craft packet can settle before the next step. */
    public static void tick(Screen currentScreen) {
        if (pendingCrafts.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(currentScreen instanceof AbstractContainerScreen<?> screen)
            || minecraft.player == null
            || minecraft.gameMode == null
            || screen.getMenu().containerId != pendingMenuId) {
            clearPending();
            return;
        }
        if (waitFrames-- > 0) {
            return;
        }

        if (pendingAe2Carry) {
            tickAe2Carry(screen, minecraft);
            return;
        }

        if (pendingVanillaPickup) {
            tickVanillaPickup(screen, minecraft);
            return;
        }

        if (pendingResultSlot < 0) {
            startNextCraft();
            return;
        }
        if (pendingResultSlot >= screen.getMenu().slots.size()) {
            clearPending();
            return;
        }

        Slot result = screen.getMenu().getSlot(pendingResultSlot);
        if (!result.hasItem()) {
            if (++emptyFrames > AE2_WAIT_FRAMES) {
                clearPending();
            }
            return;
        }

        PendingCraft craft = pendingCrafts.get(pendingCraftIndex);
        Boolean gridReady = networkGridReady(screen.getMenu(), craft.step);
        if (Boolean.FALSE.equals(gridReady)) {
            if (++emptyFrames > AE2_WAIT_FRAMES) {
                clearPending();
            }
            return;
        }

        emptyFrames = 0;
        ItemStack outputBefore = result.getItem().copy();
        Boolean ae2Pickup = StorageNetworkIntegration.takeCraftingResult(
            screen.getMenu(),
            pendingResultSlot,
            true
        );
        if (Boolean.TRUE.equals(ae2Pickup)) {
            // CRAFT_ITEM places one result on the menu cursor. Wait for the
            // server sync, then put that exact one-result stack into the
            // player's inventory before advancing the recursive queue.
            pendingAe2Carry = true;
            pendingAe2PlacementSlot = -1;
            pendingAe2OutputKey = RecipeTreeData.ingredientKey(outputBefore);
            pendingAe2InventoryBefore = playerInventoryAmount(
                screen.getMenu(),
                minecraft.player,
                pendingAe2OutputKey
            );
            pendingResultSlot = -1;
            emptyFrames = 0;
            waitFrames = 2;
            return;
        }
        minecraft.gameMode.handleInventoryMouseClick(
            pendingMenuId,
            pendingResultSlot,
            0,
            ClickType.QUICK_MOVE,
            minecraft.player
        );
        pendingVanillaPickup = true;
        pendingPickupAttempts = 1;
        waitFrames = 1;
    }

    private static void tickVanillaPickup(AbstractContainerScreen<?> screen, Minecraft minecraft) {
        if (pendingResultSlot < 0 || pendingResultSlot >= screen.getMenu().slots.size()) {
            clearPending();
            return;
        }
        Slot result = screen.getMenu().getSlot(pendingResultSlot);
        if (!result.hasItem()) {
            completePendingCraft();
            return;
        }
        if (++pendingPickupAttempts > 8) {
            // Do not advance the dependency queue while a full inventory (or
            // a third-party result slot) still owns the product.
            clearPending();
            return;
        }
        minecraft.gameMode.handleInventoryMouseClick(
            pendingMenuId,
            pendingResultSlot,
            0,
            ClickType.QUICK_MOVE,
            minecraft.player
        );
        waitFrames = 1;
    }

    private static void tickAe2Carry(AbstractContainerScreen<?> screen, Minecraft minecraft) {
        AbstractContainerMenu menu = screen.getMenu();
        if (pendingAe2NetworkDeposit) {
            tickAe2NetworkDeposit(menu);
            return;
        }

        ItemStack carried = menu.getCarried();
        if (pendingAe2PlacementInFlight) {
            if (carried.isEmpty()) {
                if (!pendingAe2OutputKey.isEmpty()
                    && playerInventoryAmount(menu, minecraft.player, pendingAe2OutputKey)
                        > pendingAe2PlacementInventoryBefore) {
                    completePendingCraft();
                    return;
                }
                if (++emptyFrames > AE2_WAIT_FRAMES) {
                    clearPending();
                } else {
                    waitFrames = 1;
                }
                return;
            }

            long inventoryAmount = pendingAe2OutputKey.isEmpty()
                ? 0
                : playerInventoryAmount(menu, minecraft.player, pendingAe2OutputKey);
            if (carried.getCount() < pendingAe2PlacementCount
                || inventoryAmount > pendingAe2PlacementInventoryBefore) {
                // The server acknowledged the click. If there is a remainder,
                // re-scan once the acknowledgement is visible; do not resend
                // the same click while the first packet is still in flight.
                pendingAe2PlacementInFlight = false;
                pendingAe2PlacementSlot = -1;
                pendingAe2PlacementCount = 0;
                emptyFrames = 0;
                waitFrames = 1;
                return;
            }
            if (++emptyFrames > AE2_WAIT_FRAMES) {
                pendingAe2PlacementInFlight = false;
                if (startAe2NetworkDeposit(menu)) {
                    return;
                }
                clearPending();
            } else {
                waitFrames = 1;
            }
            return;
        }

        if (carried.isEmpty()) {
            // AE2 normally synchronizes CRAFT_ITEM through the menu carried
            // stack. Some AE2/Forge combinations instead insert the result
            // directly into the player inventory. Treat that path as a
            // completed operation too, otherwise the recursive queue waits
            // for the timeout after the first craft.
            if (!pendingAe2OutputKey.isEmpty()
                && playerInventoryAmount(menu, minecraft.player, pendingAe2OutputKey)
                    > pendingAe2InventoryBefore) {
                completePendingCraft();
                return;
            }
            if (++emptyFrames > AE2_WAIT_FRAMES) {
                clearPending();
            }
            return;
        }

        if (pendingAe2PlacementSlot < 0) {
            int target = findInventoryDestination(menu, minecraft.player, carried);
            if (target < 0) {
                if (startAe2NetworkDeposit(menu)) {
                    return;
                }
                clearPending();
                return;
            }
            pendingAe2PlacementSlot = target;
            pendingAe2PlacementInFlight = true;
            pendingAe2PlacementCount = carried.getCount();
            pendingAe2PlacementInventoryBefore = pendingAe2OutputKey.isEmpty()
                ? 0
                : playerInventoryAmount(menu, minecraft.player, pendingAe2OutputKey);
            minecraft.gameMode.handleInventoryMouseClick(
                pendingMenuId,
                target,
                0,
                ClickType.PICKUP,
                minecraft.player
            );
            waitFrames = 1;
            return;
        }
    }

    private static void tickAe2NetworkDeposit(AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            completePendingCraft();
            return;
        }
        if (pendingAe2NetworkRequest) {
            if (carried.getCount() < pendingAe2NetworkCount) {
                // A partial network insertion was acknowledged. Send the
                // next request only after the client sees that change.
                pendingAe2NetworkRequest = false;
                pendingAe2NetworkCount = carried.getCount();
                emptyFrames = 0;
                waitFrames = 1;
                return;
            }
            if (++emptyFrames > AE2_WAIT_FRAMES) {
                clearPending();
            } else {
                waitFrames = 1;
            }
            return;
        }
        if (!startAe2NetworkDeposit(menu)) {
            clearPending();
        }
    }

    /** Requests AE2 to insert the complete cursor stack into its network. */
    private static boolean startAe2NetworkDeposit(AbstractContainerMenu menu) {
        Boolean deposited = StorageNetworkIntegration.putCarriedItemIntoNetwork(menu, false);
        if (!Boolean.TRUE.equals(deposited)) {
            return false;
        }
        pendingAe2NetworkDeposit = true;
        pendingAe2NetworkRequest = true;
        pendingAe2NetworkCount = Math.max(1, menu.getCarried().getCount());
        emptyFrames = 0;
        waitFrames = 1;
        return true;
    }

    private static void completePendingCraft() {
        if (pendingCraftIndex >= pendingCrafts.size()) {
            clearPending();
            return;
        }
        PendingCraft craft = pendingCrafts.get(pendingCraftIndex);
        craft.remainingBatches = Math.max(0, craft.remainingBatches - pendingChunk);
        if (craft.remainingBatches <= 0) {
            pendingCraftIndex++;
        }
        pendingResultSlot = -1;
        pendingAe2Carry = false;
        pendingAe2NetworkDeposit = false;
        pendingAe2NetworkRequest = false;
        pendingAe2NetworkCount = 0;
        pendingAe2PlacementSlot = -1;
        pendingAe2PlacementInFlight = false;
        pendingAe2PlacementCount = 0;
        pendingAe2PlacementInventoryBefore = 0;
        pendingAe2OutputKey = "";
        pendingAe2InventoryBefore = 0;
        pendingVanillaPickup = false;
        pendingPickupAttempts = 0;
        pendingChunk = 0;
        emptyFrames = 0;
        waitFrames = 2;
    }

    private static int findInventoryDestination(
        AbstractContainerMenu menu,
        Player player,
        ItemStack stack
    ) {
        if (player == null || stack.isEmpty()) {
            return -1;
        }
        int empty = -1;
        int partial = -1;
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory() || !slot.mayPlace(stack)) {
                continue;
            }
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty()
                && RecipeTreeData.ingredientKey(existing).equals(RecipeTreeData.ingredientKey(stack))
                && existing.getCount() < Math.min(slot.getMaxStackSize(), stack.getMaxStackSize())) {
                int capacity = Math.min(slot.getMaxStackSize(), stack.getMaxStackSize()) - existing.getCount();
                if (capacity >= stack.getCount()) {
                    return slot.index;
                }
                if (partial < 0) {
                    partial = slot.index;
                }
            }
            if (empty < 0 && existing.isEmpty()) {
                empty = slot.index;
            }
        }
        return empty >= 0 ? empty : partial;
    }

    private static long playerInventoryAmount(
        AbstractContainerMenu menu,
        Player player,
        String key
    ) {
        if (menu == null || player == null || key == null || key.isEmpty()) {
            return 0;
        }
        long amount = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && !slot.getItem().isEmpty()
                && key.equals(RecipeTreeData.ingredientKey(slot.getItem()))) {
                amount += slot.getItem().getCount();
            }
        }
        return amount;
    }

    private static RecipeTreeData.CraftStep firstStep(RecipeTreeData.CraftStep step, boolean recursive) {
        if (step == null) {
            return null;
        }
        if (!recursive) {
            return step;
        }
        RecipeTreeData.Tree tree = RecipeTreeSession.craftingTree();
        if (tree == null) {
            return null;
        }
        return tree.recursiveCraftingSteps(step).stream().findFirst().orElse(null);
    }

    private static boolean startNextCraft() {
        if (pendingCraftIndex >= pendingCrafts.size()) {
            clearPending();
            RecipeTreeFavorites.refreshNow();
            return true;
        }

        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null) {
            clearPending();
            return false;
        }
        PendingCraft craft = pendingCrafts.get(pendingCraftIndex);
        AbstractContainerMenu menu = screen.getMenu();
        int resultSlot = instantResultSlot(menu);
        // AE2's recipe-transfer packet fills one crafting operation at a time.
        // Vanilla result slots also consume only one operation per output
        // click. Transfer one batch at a time so the result is always taken
        // before the next recursive dependency is started; otherwise a
        // scaled JEI transfer can leave a second result sitting in the slot.
        int chunk = resultSlot >= 0
            ? 1
            : transferChunk(craft.step, craft.remainingBatches);
        if (chunk <= 0 || !runTransfer(craft.step, chunk, true)) {
            clearPending();
            return false;
        }

        if (resultSlot < 0 || resultSlot >= menu.slots.size()) {
            // Timed machines can receive their exact materials, but their output
            // cannot be safely taken or used by a later recursive step.
            clearPending();
            return true;
        }
        pendingMenuId = menu.containerId;
        pendingResultSlot = resultSlot;
        pendingChunk = chunk;
        pendingAe2Carry = false;
        pendingAe2NetworkDeposit = false;
        pendingAe2NetworkRequest = false;
        pendingAe2NetworkCount = 0;
        pendingAe2PlacementSlot = -1;
        pendingAe2PlacementInFlight = false;
        pendingAe2PlacementCount = 0;
        pendingAe2PlacementInventoryBefore = 0;
        waitFrames = 3;
        emptyFrames = 0;
        return true;
    }

    private static int transferChunk(RecipeTreeData.CraftStep step) {
        return transferChunk(step, step == null ? 0 : step.batches());
    }

    private static int transferChunk(RecipeTreeData.CraftStep step, long requested) {
        if (step == null || requested <= 0) {
            return 0;
        }
        IRecipeLayoutDrawable<?> layout = RecipeTreeData.createLayout(step.recipe()).orElse(null);
        if (layout == null) {
            return 0;
        }
        int capacity = maxBatchesPerTransfer(layout.getRecipeSlotsView(), step.selectedInputs());
        return (int) Math.max(1, Math.min(Math.min(Integer.MAX_VALUE, requested), capacity));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean runTransfer(RecipeTreeData.CraftStep step, int batches, boolean doTransfer) {
        if (step == null || batches <= 0) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return false;
        }
        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null) {
            return false;
        }
        IRecipeLayoutDrawable<?> layout = RecipeTreeData.createLayout(step.recipe()).orElse(null);
        if (layout == null) {
            return false;
        }

        AbstractContainerMenu menu = screen.getMenu();
        // Prefer JEI's normal container-transfer handler.  It is the safest
        // path for ordinary menus and for network mods that already expose a
        // JEI handler.  The reflective network packet is only the fallback
        // for a fake-slot terminal where no generic handler can move items.
        boolean ae2Menu = Ae2StorageIntegration.isCraftingMenu(menu);
        if (!ae2Menu) {
            Boolean generic = tryJeiTransfer(menu, layout, step.selectedInputs(), batches, player, doTransfer);
            if (Boolean.TRUE.equals(generic)) {
                return true;
            }
        }

        Boolean networkTransfer = tryNetworkCraftingTransfer(menu, layout, step.selectedInputs(), doTransfer);
        if (networkTransfer != null) {
            return networkTransfer;
        }
        return ae2Menu
            ? tryJeiTransfer(menu, layout, step.selectedInputs(), batches, player, doTransfer)
            : false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Boolean tryJeiTransfer(
        AbstractContainerMenu menu,
        IRecipeLayoutDrawable<?> layout,
        List<String> selectedInputs,
        int batches,
        Player player,
        boolean doTransfer
    ) {
        IRecipeTransferManager manager = Internal.getJeiRuntime().getRecipeTransferManager();
        Optional<IRecipeTransferHandler<AbstractContainerMenu, Object>> handler = (Optional) manager
            .getRecipeTransferHandler(menu, (IRecipeCategory) layout.getRecipeCategory());
        if (handler.isEmpty()) {
            return false;
        }

        IRecipeSlotsView slots = adjustInputs(layout.getRecipeSlotsView(), selectedInputs, batches);
        try {
            IRecipeTransferError error = handler.get().transferRecipe(
                menu,
                layout.getRecipe(),
                slots,
                player,
                false,
                doTransfer
            );
            return error == null || error.getType().allowsTransfer;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * During a tree view, JEI's container screen is the parent rather than the
     * current screen. Simulations can use it directly; real transfers first
     * return to it so menu clicks and result extraction target the live menu.
     */
    private static AbstractContainerScreen<?> containerScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AbstractContainerScreen<?> screen) {
            return screen;
        }
        if (minecraft.screen instanceof RecipeTreeScreen treeScreen
            && treeScreen.parentScreen() instanceof AbstractContainerScreen<?> screen) {
            return screen;
        }
        return null;
    }

    private static boolean exposeParentContainer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RecipeTreeScreen treeScreen) {
            if (!(treeScreen.parentScreen() instanceof AbstractContainerScreen<?>)) {
                return false;
            }
            treeScreen.onClose();
        }
        return containerScreen() != null;
    }

    private static IRecipeSlotsView adjustInputs(
        IRecipeSlotsView original,
        List<String> selectedInputs,
        int batches
    ) {
        List<IRecipeSlotView> adjusted = new ArrayList<>();
        int inputIndex = 0;
        for (IRecipeSlotView slot : original.getSlotViews()) {
            if (slot.getRole() == RecipeIngredientRole.INPUT) {
                String selected = preferredInputKey(
                    slot,
                    inputIndex < selectedInputs.size() ? selectedInputs.get(inputIndex) : ""
                );
                adjusted.add(new AdjustedSlot(slot, selected, batches));
                inputIndex++;
            } else {
                adjusted.add(slot);
            }
        }
        List<IRecipeSlotView> immutable = List.copyOf(adjusted);
        return () -> immutable;
    }

    private static int maxBatchesPerTransfer(IRecipeSlotsView slots, List<String> selectedInputs) {
        int result = Integer.MAX_VALUE;
        int inputIndex = 0;
        boolean foundItemInput = false;
        for (IRecipeSlotView slot : slots.getSlotViews()) {
            if (slot.getRole() != RecipeIngredientRole.INPUT) {
                continue;
            }
            String selected = preferredInputKey(
                slot,
                inputIndex < selectedInputs.size() ? selectedInputs.get(inputIndex) : ""
            );
            inputIndex++;
            int slotCapacity = slot.getItemStacks()
                .filter(stack -> selected.isEmpty() || RecipeTreeData.ingredientKey(stack).equals(selected))
                .mapToInt(stack -> Math.max(1, stack.getMaxStackSize() / Math.max(1, stack.getCount())))
                .max()
                .orElse(Integer.MAX_VALUE);
            if (slotCapacity != Integer.MAX_VALUE) {
                foundItemInput = true;
                result = Math.min(result, slotCapacity);
            }
        }
        return foundItemInput ? Math.max(1, result) : 1;
    }

    /**
     * JEI transfer handlers normally use the displayed ingredient of a slot.
     * For an unfixed tag/directory input that used to be the first entry,
     * even when another candidate was the one actually present in the
     * inventory or can be reached by recursively crafting its inputs. Resolve
     * a concrete candidate at transfer time instead.
     */
    private static String preferredInputKey(IRecipeSlotView slot, String selectedKey) {
        if (!selectedKey.isEmpty()) {
            return selectedKey;
        }
        // A regular JEI transfer must remain cheap. Recursive dependency
        // search is only enabled while a recipe tree (and its defaults) is
        // active; otherwise use the direct inventory match or JEI's normal
        // first candidate fallback.
        return RecipeTreeData.findCandidateWithSupply(slot.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .toList(),
            RecipeTreeSession.tree() != null)
            .map(RecipeTreeData::ingredientKey)
            .orElse("");
    }

    private static int instantResultSlot(AbstractContainerMenu menu) {
        int networkResult = StorageNetworkIntegration.craftingResultSlot(menu);
        if (networkResult >= 0) {
            return networkResult;
        }
        if (menu instanceof CraftingMenu || menu instanceof InventoryMenu) {
            return 0;
        }
        if (menu instanceof StonecutterMenu) {
            return 1;
        }
        if (menu instanceof SmithingMenu || menu instanceof LoomMenu) {
            return 3;
        }
        if (menu instanceof CartographyTableMenu || menu instanceof GrindstoneMenu) {
            return 2;
        }
        return -1;
    }

    /**
     * AE2's crafting terminal has a server-authoritative packet that fills its
     * 3x3 matrix directly from ME storage. Use it for recipe-tree transfers so
     * network items behave like player-inventory inputs without adding a JEI++
     * server packet or submitting an AE autocrafting job.
     */
    private static Boolean tryNetworkCraftingTransfer(
        AbstractContainerMenu menu,
        IRecipeLayoutDrawable<?> layout,
        List<String> selectedInputs,
        boolean doTransfer
    ) {
        if (!(layout.getRecipe() instanceof CraftingRecipe)) {
            return null;
        }
        List<ItemStack> templates = ae2Templates(layout, selectedInputs);
        if (templates == null) {
            return null;
        }
        return StorageNetworkIntegration.tryFillCraftingGrid(menu, templates, doTransfer);
    }

    private static Boolean networkGridReady(
        AbstractContainerMenu menu,
        RecipeTreeData.CraftStep step
    ) {
        if (step == null || !(step.recipe().recipe() instanceof CraftingRecipe)
            || !StorageNetworkIntegration.isCraftingMenu(menu)) {
            return null;
        }
        IRecipeLayoutDrawable<?> layout = RecipeTreeData.createLayout(step.recipe()).orElse(null);
        if (layout == null) {
            return false;
        }
        List<ItemStack> templates = ae2Templates(layout, step.selectedInputs());
        return templates == null ? false : StorageNetworkIntegration.craftingGridMatches(menu, templates);
    }

    private static List<ItemStack> ae2Templates(
        IRecipeLayoutDrawable<?> layout,
        List<String> selectedInputs
    ) {
        List<IRecipeSlotView> inputs = layout.getRecipeSlotsView().getSlotViews().stream()
            .filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT)
            .toList();
        if (inputs.size() != 9) {
            return null;
        }

        List<ItemStack> templates = new ArrayList<>(9);
        for (int index = 0; index < inputs.size(); index++) {
            IRecipeSlotView input = inputs.get(index);
            String selected = preferredInputKey(
                input,
                index < selectedInputs.size() ? selectedInputs.get(index) : ""
            );
            ItemStack template = input.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .filter(stack -> selected.isEmpty() || RecipeTreeData.ingredientKey(stack).equals(selected))
                .findFirst()
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
            if (!template.isEmpty()) {
                template.setCount(1);
            }
            templates.add(template);
        }
        return templates;
    }

    private static void clearPending() {
        pendingCrafts = List.of();
        pendingCraftIndex = 0;
        pendingMenuId = -1;
        pendingResultSlot = -1;
        pendingChunk = 0;
        pendingAe2Carry = false;
        pendingAe2NetworkDeposit = false;
        pendingAe2NetworkRequest = false;
        pendingAe2NetworkCount = 0;
        pendingAe2PlacementSlot = -1;
        pendingAe2PlacementInFlight = false;
        pendingAe2PlacementCount = 0;
        pendingAe2PlacementInventoryBefore = 0;
        pendingAe2OutputKey = "";
        pendingAe2InventoryBefore = 0;
        pendingVanillaPickup = false;
        pendingPickupAttempts = 0;
        waitFrames = 0;
        emptyFrames = 0;
    }

    private static final class PendingCraft {
        private final RecipeTreeData.CraftStep step;
        private long remainingBatches;

        private PendingCraft(RecipeTreeData.CraftStep step, long remainingBatches) {
            this.step = step;
            this.remainingBatches = remainingBatches;
        }
    }

    private static final class AdjustedSlot implements IRecipeSlotView {
        private final IRecipeSlotView delegate;
        private final List<ITypedIngredient<?>> ingredients;

        private AdjustedSlot(IRecipeSlotView delegate, String selectedKey, int batches) {
            this.delegate = delegate;
            IJeiRuntime runtime = DirectoryRecipePlugin.getJeiRuntime();
            List<ITypedIngredient<?>> adjusted = new ArrayList<>();
            if (runtime != null) {
                for (ITypedIngredient<?> ingredient : delegate.getAllIngredientsList()) {
                    if (ingredient == null) {
                        continue;
                    }
                    Optional<ItemStack> itemStack = ingredient.getItemStack();
                    if (itemStack.isEmpty()) {
                        if (selectedKey.isEmpty()) {
                            adjusted.add(ingredient);
                        }
                        continue;
                    }
                    ItemStack stack = itemStack.get();
                    if (!selectedKey.isEmpty() && !RecipeTreeData.ingredientKey(stack).equals(selectedKey)) {
                        continue;
                    }
                    ItemStack scaled = stack.copy();
                    long count = (long) Math.max(1, stack.getCount()) * batches;
                    if (count > stack.getMaxStackSize()) {
                        continue;
                    }
                    scaled.setCount((int) count);
                    runtime.getIngredientManager()
                        .createTypedIngredient(VanillaTypes.ITEM_STACK, scaled, false)
                        .ifPresent(adjusted::add);
                }
            }
            this.ingredients = adjusted.isEmpty()
                ? delegate.getAllIngredients().toList()
                : List.copyOf(adjusted);
        }

        @Override
        public Stream<ITypedIngredient<?>> getAllIngredients() {
            return ingredients.stream();
        }

        @Override
        public List<ITypedIngredient<?>> getAllIngredientsList() {
            return ingredients;
        }

        @Override
        public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
            return ingredients.stream().findFirst();
        }

        @Override
        public RecipeIngredientRole getRole() {
            return delegate.getRole();
        }

        @Override
        public void drawHighlight(GuiGraphics guiGraphics, int color) {
            delegate.drawHighlight(guiGraphics, color);
        }

        @Override
        public Optional<String> getSlotName() {
            return delegate.getSlotName();
        }
    }
}
