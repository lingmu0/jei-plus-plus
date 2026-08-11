package com.lingmu0.JeiPlusPlusMod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional integrations for network-backed crafting terminals.
 *
 * <p>Normal crafting slots and result slots are deliberately left to JEI's
 * normal container-click transfer path.  RS and Beyond Dimensions use fake
 * storage slots, however, so a vanilla click cannot extract an item from the
 * network.  For those menus this class calls the mod's own client API/packet
 * only for the recipe-transfer operation.  Every reference is reflective so
 * JEI++ remains a client-only, optional integration.</p>
 */
final class StorageNetworkIntegration {
    private static final long CACHE_FALLBACK_NANOS = 50_000_000L;
    private static final String RS1_MENU = "com.refinedmods.refinedstorage.container.GridContainerMenu";
    private static final String RS2_MENU = "com.refinedmods.refinedstorage.common.grid.AbstractCraftingGridContainerMenu";
    private static final String RS1_SCREEN = "com.refinedmods.refinedstorage.screen.grid.GridScreen";
    private static final String BD_MENU = "com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu";
    private static final String IT_MENU =
        "org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorageBase";
    private static final String IT_SCREEN =
        "org.cyclops.integratedterminals.client.gui.container.ContainerScreenTerminalStorage";

    private static volatile Object cachedMenu;
    private static volatile long cachedGameTime = Long.MIN_VALUE;
    private static volatile long cachedAtNanos = Long.MIN_VALUE;
    private static volatile long cachedSnapshotRevision;
    private static volatile List<StoredStack> cachedStacks = List.of();

    private StorageNetworkIntegration() {
    }

    static List<StoredStack> storedStacks() {
        Object menu = Ae2StorageIntegration.activeMenu();
        long now = System.nanoTime();
        if (menu == null) {
            if (cachedMenu != null || !cachedStacks.isEmpty()) {
                cachedSnapshotRevision++;
            }
            cachedMenu = null;
            cachedGameTime = Long.MIN_VALUE;
            cachedAtNanos = now;
            cachedStacks = List.of();
            return List.of();
        }
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
        long cacheAge = now - cachedAtNanos;
        if (menu == cachedMenu
            && gameTime == cachedGameTime
            && cacheAge >= 0L
            && cacheAge < CACHE_FALLBACK_NANOS) {
            return cachedStacks;
        }

        List<StoredStack> ae2 = new ArrayList<>();
        for (Ae2StorageIntegration.StoredStack stored : Ae2StorageIntegration.storedStacks()) {
            ae2.add(new StoredStack(stored.stack(), stored.amount()));
        }
        List<StoredStack> result;
        if (!ae2.isEmpty() || Ae2StorageIntegration.isCraftingMenu(menu)) {
            result = ae2;
        } else {
            List<StoredStack> refined = refinedStorageStacks(menu);
            if (!refined.isEmpty() || isRefinedStorageMenu(menu)) {
                result = refined;
            } else {
                List<StoredStack> beyond = beyondStacks(menu);
                result = !beyond.isEmpty() || isBeyondMenu(menu)
                    ? beyond
                    : integratedTerminalStacks(menu);
            }
        }
        boolean snapshotChanged = menu != cachedMenu || !sameSnapshot(cachedStacks, result);
        cachedMenu = menu;
        cachedGameTime = gameTime;
        cachedAtNanos = now;
        cachedStacks = List.copyOf(result);
        if (snapshotChanged) {
            cachedSnapshotRevision++;
        }
        return cachedStacks;
    }

    /**
     * Version of the combined client-side storage snapshot. It advances when
     * the cache is queried again, including the real-time fallback used while
     * an AE screen is waiting for its initial repository sync packet.
     */
    static long snapshotRevision() {
        storedStacks();
        return cachedSnapshotRevision;
    }

    private static boolean sameSnapshot(List<StoredStack> previous, List<StoredStack> next) {
        if (previous.size() != next.size()) {
            return false;
        }
        for (int index = 0; index < previous.size(); index++) {
            StoredStack left = previous.get(index);
            StoredStack right = next.get(index);
            if (left.amount != right.amount || !left.key.equals(right.key)) {
                return false;
            }
        }
        return true;
    }

    /** Moves network-backed entries ahead of ordinary entries where the mod exposes a mutable view. */
    static void prioritizeVisibleEntries(Set<String> keys) {
        Ae2StorageIntegration.prioritizeVisibleEntries(keys);
        Object menu = Ae2StorageIntegration.activeMenu();
        if (menu == null || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            prioritizeRs2(menu, keys);
            prioritizeRs1(keys);
            prioritizeBeyond(menu, keys);
            prioritizeIntegrated(menu, keys);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Optional internal APIs are allowed to change without affecting JEI++.
        }
    }

    /**
     * Uses a network mod's own recipe-transfer packet when its slots are fake.
     * A null result means the menu is not one of the supported network menus,
     * allowing the caller to fall back to JEI's generic transfer handler.
     */
    static Boolean tryFillCraftingGrid(
        AbstractContainerMenu menu,
        List<ItemStack> templates,
        boolean send
    ) {
        Boolean ae2 = Ae2StorageIntegration.tryFillCraftingGrid(menu, templates, send);
        if (ae2 != null) {
            return ae2;
        }
        if (isRs1Menu(menu)) {
            return sendRs1Recipe(menu, templates, send);
        }
        if (isRs2Menu(menu)) {
            return sendRs2Recipe(menu, templates, send);
        }
        if (isBeyondMenu(menu)) {
            return sendBeyondRecipe(menu, templates, send);
        }
        return null;
    }

    static Boolean craftingGridMatches(Object menu, List<ItemStack> templates) {
        Boolean ae2 = Ae2StorageIntegration.craftingGridMatches(menu, templates);
        if (ae2 != null) {
            return ae2;
        }
        try {
            List<ItemStack> actual = new ArrayList<>(9);
            if (isRs1Menu(menu)) {
                Object grid = invokeNoArg(menu, "getGrid");
                Object matrix = invokeNoArg(grid, "getCraftingMatrix");
                readContainer(matrix, actual, 9);
            } else if (isRs2Menu(menu)) {
                Object slots = invokeNoArg(menu, "getCraftingMatrixSlots");
                if (!(slots instanceof List<?> list) || list.size() < 9) {
                    return false;
                }
                for (int i = 0; i < 9; i++) {
                    Object slot = list.get(i);
                    actual.add(stackOf(invokeNoArg(slot, "getItem")));
                }
            } else if (isBeyondMenu(menu)) {
                int start = intValue(readField(menu, "craftSlotStartIndex"));
                int end = intValue(readField(menu, "craftSlotEndIndex"));
                if (start < 0 || end - start < 9) {
                    return false;
                }
                for (int i = 0; i < 9; i++) {
                    actual.add(stackOf(invokeNoArg(invoke(menu, "getSlot", int.class, start + i), "getItem")));
                }
            } else if (isIntegratedTerminalMenu(menu)) {
                Object commonTab = integratedSelectedCommonTab(menu);
                Object matrix = invokeNoArg(commonTab, "getInventoryCrafting");
                readContainer(matrix, actual, 9);
            } else {
                return null;
            }
            if (actual.size() < templates.size()) {
                return false;
            }
            for (int i = 0; i < templates.size(); i++) {
                ItemStack expected = templates.get(i);
                ItemStack present = actual.get(i);
                if (expected == null || expected.isEmpty()) {
                    if (!present.isEmpty()) {
                        return false;
                    }
                } else if (present.isEmpty()
                    || !RecipeTreeData.ingredientKey(expected).equals(RecipeTreeData.ingredientKey(present))) {
                    return false;
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static int craftingResultSlot(AbstractContainerMenu menu) {
        int ae2 = Ae2StorageIntegration.craftingResultSlot(menu);
        if (ae2 >= 0 || menu == null) {
            return ae2;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            String name = menu.getSlot(i).getClass().getName();
            if (name.endsWith("ResultCraftingGridSlot")
                || name.endsWith("CraftingGridResultSlot")
                || name.endsWith("AutoRefillResultSlot")) {
                return i;
            }
        }
        if (isIntegratedTerminalMenu(menu)) {
            try {
                Object commonTab = integratedSelectedCommonTab(menu);
                Object result = invokeNoArg(commonTab, "getSlotCrafting");
                for (int i = 0; i < menu.slots.size(); i++) {
                    if (menu.getSlot(i) == result) {
                        return i;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // Optional Integrated Terminals internals.
            }
        }
        return -1;
    }

    /**
     * Network menus must take one result with a normal pickup. RS and Beyond
     * Dimensions override or special-case quick-move, so using QUICK_MOVE can
     * leave the result in place forever and stall the recursive queue.
     */
    static Boolean takeCraftingResult(Object menu, int resultSlot, boolean send) {
        Boolean ae2 = Ae2StorageIntegration.takeCraftingResult(menu, resultSlot, send);
        if (ae2 != null) {
            return ae2;
        }
        if (!isRefinedStorageMenu(menu) && !isBeyondMenu(menu) && !isIntegratedTerminalMenu(menu)) {
            return null;
        }
        if (!send) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(menu instanceof AbstractContainerMenu container)
            || minecraft.player == null
            || minecraft.gameMode == null
            || resultSlot < 0
            || resultSlot >= container.slots.size()) {
            return false;
        }
        minecraft.gameMode.handleInventoryMouseClick(
            container.containerId,
            resultSlot,
            0,
            ClickType.PICKUP,
            minecraft.player
        );
        return true;
    }

    /** Uses each network terminal's own cursor-insertion hook when available. */
    static Boolean putCarriedItemIntoNetwork(Object menu, boolean single) {
        Boolean ae2 = Ae2StorageIntegration.putCarriedItemIntoNetwork(menu, single);
        if (ae2 != null) {
            return ae2;
        }
        try {
            if (isRs1Menu(menu)) {
                Class<?> messageType = Class.forName(
                    "com.refinedmods.refinedstorage.network.grid.GridItemInsertHeldMessage"
                );
                Object message = constructor(messageType, boolean.class).newInstance(single);
                Class<?> rsType = Class.forName("com.refinedmods.refinedstorage.RS");
                Object handler = readStaticField(rsType, "NETWORK_HANDLER");
                Method sender = findCompatibleMethod(handler == null ? null : handler.getClass(), "sendToServer", messageType);
                if (handler != null && sender != null) {
                    sender.invoke(handler, message);
                    return true;
                }
                return false;
            }
            if (isRs2Menu(menu)) {
                Class<?> modeType = Class.forName(
                    "com.refinedmods.refinedstorage.api.network.node.grid.GridInsertMode"
                );
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object mode = Enum.valueOf(
                    (Class<? extends Enum>) modeType.asSubclass(Enum.class),
                    single ? "SINGLE_RESOURCE" : "ENTIRE_RESOURCE"
                );
                Class<?> packets = Class.forName(
                    "com.refinedmods.refinedstorage.common.support.packet.c2s.C2SPackets"
                );
                Method sender = findCompatibleMethod(packets, "sendGridInsert", modeType, boolean.class);
                if (sender != null) {
                    sender.invoke(null, mode, true);
                    return true;
                }
                return false;
            }
            if (isBeyondMenu(menu)) {
                return sendBeyondCursorInsert(menu);
            }
            if (isIntegratedTerminalMenu(menu)) {
                return sendIntegratedCursorInsert(menu);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Optional network insertion APIs.
        }
        return null;
    }

    private static Boolean sendBeyondCursorInsert(Object menu) {
        try {
            Slot target = null;
            for (Slot slot : menu instanceof AbstractContainerMenu container ? container.slots : List.<Slot>of()) {
                String name = slot.getClass().getName();
                if (name.contains("beyonddimensions") && name.contains("StackTypedSlot")) {
                    target = slot;
                    break;
                }
            }
            if (target == null) {
                return false;
            }
            Object clickItem = invokeNoArg(target, "getVanillaActualStack");
            Class<?> packetType = Class.forName(
                "com.wintercogs.beyonddimensions.network.packet.c2s.CallSeverClickPacket"
            );
            Constructor<?> packetConstructor = constructor(
                packetType,
                int.class,
                clickItem == null ? Object.class : clickItem.getClass(),
                int.class,
                boolean.class
            );
            if (packetConstructor == null) {
                return false;
            }
            int index = intValue(readField(target, "index"));
            if (index < 0) {
                index = intValue(invokeNoArg(target, "getContainerSlot"));
            }
            Object packet = packetConstructor.newInstance(index, clickItem, 0, false);
            for (String distributorName : List.of(
                "net.neoforged.neoforge.network.PacketDistributor",
                "net.minecraftforge.network.PacketDistributor"
            )) {
                try {
                    Class<?> distributor = Class.forName(distributorName);
                    Method sender = findCompatibleMethod(distributor, "sendToServer", packetType);
                    if (sender != null) {
                        sender.invoke(null, packet);
                        return true;
                    }
                } catch (ClassNotFoundException ignored) {
                    // Try the other loader's packet distributor.
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Optional Beyond Dimensions network click packet.
        }
        return false;
    }

    /** Lets Integrated Terminals build and send its own PLAYER_PLACE_STORAGE packet. */
    private static Boolean sendIntegratedCursorInsert(Object menu) {
        try {
            Object tab = integratedItemClientTab(menu);
            if (tab == null || !(menu instanceof AbstractContainerMenu container)) {
                return false;
            }
            int channel = intValue(invokeNoArg(menu, "getSelectedChannel"));
            Method click = findCompatibleMethod(
                tab.getClass(),
                "handleClick",
                AbstractContainerMenu.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class,
                int.class,
                boolean.class
            );
            if (click == null) {
                return false;
            }
            Object handled = click.invoke(tab, container, channel, 0, 0, false, true, -1, false);
            return Boolean.TRUE.equals(handled);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static boolean isCraftingMenu(Object menu) {
        return Ae2StorageIntegration.isCraftingMenu(menu)
            || isRs1Menu(menu)
            || isRs2Menu(menu)
            || isBeyondMenu(menu)
            || isIntegratedTerminalMenu(menu);
    }

    private static List<StoredStack> refinedStorageStacks(Object menu) {
        try {
            if (isRs1Menu(menu)) {
                // The client grid view is the authoritative RS 1.x snapshot:
                // its IGridStack quantity is what the terminal actually shows.
                // Storage-cache entries vary between RS releases and may only
                // carry a normalized display stack, which made bookmark counts
                // appear as 1 even while highlighting and sorting worked.
                List<StoredStack> viewStacks = refinedStorageViewStacks();
                if (viewStacks != null) {
                    return viewStacks;
                }
                Object grid = invokeNoArg(menu, "getGrid");
                Object cache = invokeNoArg(grid, "getStorageCache");
                Object list = invokeNoArg(cache, "getList");
                Object entries = invokeNoArg(list, "getStacks");
                if (entries instanceof Iterable<?> iterable) {
                    List<StoredStack> result = new ArrayList<>();
                    for (Object entry : iterable) {
                        ItemStack stack = stackOf(entry);
                        if (stack.isEmpty()) {
                            stack = stackOf(invokeNoArg(entry, "getStack"));
                        }
                        if (!stack.isEmpty() && stack.getCount() > 0) {
                            result.add(new StoredStack(stack, stack.getCount()));
                        }
                    }
                    return result;
                }
            }
            if (isRs2Menu(menu)) {
                Object repository = invokeNoArg(menu, "getRepository");
                Object entries = invokeNoArg(repository, "getViewList");
                if (entries instanceof Iterable<?> iterable) {
                    List<StoredStack> result = new ArrayList<>();
                    for (Object entry : iterable) {
                        String name = entry == null ? "" : entry.getClass().getName();
                        if (!name.endsWith("ItemGridResource")) {
                            continue;
                        }
                        ItemStack stack = stackOf(invokeNoArg(entry, "getItemStack"));
                        long amount = numberValue(invoke(entry, "getAmount", repository.getClass(), repository));
                        if (amount <= 0) {
                            amount = stack.getCount();
                        }
                        if (!stack.isEmpty() && amount > 0) {
                            stack.setCount(1);
                            result.add(new StoredStack(stack, amount));
                        }
                    }
                    return result;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Optional RS APIs.
        }
        return List.of();
    }

    /** Returns null when no compatible RS screen is available, and an empty list for an empty grid. */
    private static List<StoredStack> refinedStorageViewStacks() {
        AbstractContainerScreen<?> screen = activeContainerScreen();
        if (screen == null || !classOrSuper(screen.getClass(), RS1_SCREEN)) {
            return null;
        }
        try {
            Object view = invokeNoArg(screen, "getView");
            Object entries = invokeNoArg(view, "getAllStacks");
            if (!(entries instanceof Iterable<?> iterable)) {
                return null;
            }
            List<StoredStack> result = new ArrayList<>();
            for (Object entry : iterable) {
                ItemStack stack = rs1EntryStack(entry);
                long amount = numberValue(invokeNoArg(entry, "getQuantity"));
                if (!stack.isEmpty() && amount > 0) {
                    result.add(new StoredStack(stack, amount));
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static List<StoredStack> integratedTerminalStacks(Object menu) {
        if (!isIntegratedTerminalMenu(menu)) {
            return List.of();
        }
        try {
            Object tab = integratedItemClientTab(menu);
            if (tab == null) {
                return List.of();
            }
            int channel = intValue(invokeNoArg(menu, "getSelectedChannel"));
            Object entries = invoke(tab, "createUnfilteredIngredientsView", int.class, channel);
            if (!(entries instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<StoredStack> result = new ArrayList<>();
            for (Object entry : iterable) {
                // Crafting-option rows are recipes, not items physically stored
                // in the Integrated Dynamics network.
                if (invokeNoArg(entry, "getCraftingOption") != null) {
                    continue;
                }
                ItemStack stack = stackOf(invokeNoArg(entry, "getInstance"));
                if (!stack.isEmpty() && stack.getCount() > 0) {
                    result.add(new StoredStack(stack, stack.getCount()));
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    private static List<StoredStack> beyondStacks(Object menu) {
        if (!isBeyondMenu(menu)) {
            return List.of();
        }
        try {
            Object storage = readField(menu, "storage");
            Object entries = invokeNoArg(storage, "getStorage");
            if (!(entries instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<StoredStack> result = new ArrayList<>();
            for (Object entry : iterable) {
                long amount = numberValue(invokeNoArg(entry, "amount"));
                Object key = invokeNoArg(entry, "key");
                ItemStack stack = stackOf(invokeNoArg(key, "getReadOnlyStack"));
                if (stack.isEmpty()) {
                    stack = stackOf(invokeNoArg(key, "toStack"));
                }
                if (!stack.isEmpty() && amount > 0) {
                    stack.setCount(1);
                    result.add(new StoredStack(stack, amount));
                }
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    private static Boolean sendRs1Recipe(Object menu, List<ItemStack> templates, boolean send) {
        if (templates.size() != 9) {
            return false;
        }
        if (!send) {
            return true;
        }
        try {
            Class<?> messageType = Class.forName(
                "com.refinedmods.refinedstorage.network.grid.GridTransferMessage"
            );
            List<List<ItemStack>> inputs = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                ItemStack stack = templates.get(i);
                inputs.add(stack == null || stack.isEmpty() ? List.of() : List.of(stack.copy()));
            }
            Object message;
            Constructor<?> listConstructor = findConstructor(messageType, List.class);
            if (listConstructor != null) {
                message = listConstructor.newInstance(inputs);
            } else {
                ItemStack[][] recipe = new ItemStack[9][];
                for (int i = 0; i < inputs.size(); i++) {
                    recipe[i] = inputs.get(i).toArray(ItemStack[]::new);
                }
                Constructor<?> arrayConstructor = findConstructor(messageType, ItemStack[][].class);
                if (arrayConstructor == null) {
                    return false;
                }
                message = arrayConstructor.newInstance((Object) recipe);
            }
            Class<?> rsType = Class.forName("com.refinedmods.refinedstorage.RS");
            Object handler = readStaticField(rsType, "NETWORK_HANDLER");
            Method sender = findCompatibleMethod(handler.getClass(), "sendToServer", messageType);
            if (sender == null) {
                return false;
            }
            sender.invoke(handler, message);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Boolean sendRs2Recipe(Object menu, List<ItemStack> templates, boolean send) {
        if (templates.size() != 9) {
            return false;
        }
        if (!send) {
            return true;
        }
        try {
            Class<?> itemResourceType = Class.forName(
                "com.refinedmods.refinedstorage.common.support.resource.ItemResource"
            );
            Method ofStack = findCompatibleMethod(itemResourceType, "ofItemStack", ItemStack.class);
            if (ofStack == null) {
                return false;
            }
            List<List<Object>> recipe = new ArrayList<>(9);
            for (ItemStack template : templates) {
                if (template == null || template.isEmpty()) {
                    recipe.add(List.of());
                } else {
                    recipe.add(List.of(ofStack.invoke(null, template.copy())));
                }
            }
            Class<?> packets = Class.forName(
                "com.refinedmods.refinedstorage.common.support.packet.c2s.C2SPackets"
            );
            Method sender = findCompatibleMethod(packets, "sendCraftingGridRecipeTransfer", List.class);
            if (sender == null) {
                return false;
            }
            sender.invoke(null, recipe);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Boolean sendBeyondRecipe(Object menu, List<ItemStack> templates, boolean send) {
        if (templates.size() != 9) {
            return false;
        }
        if (!send) {
            return true;
        }
        try {
            Class<?> keyType = Class.forName(
                "com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey"
            );
            Field emptyField = findField(keyType, "EMPTY");
            Object empty = emptyField == null ? null : emptyField.get(null);
            Constructor<?> keyConstructor = constructor(keyType, ItemStack.class);
            List<Object> keys = new ArrayList<>(9);
            List<Long> amounts = new ArrayList<>(9);
            for (ItemStack template : templates) {
                if (template == null || template.isEmpty()) {
                    keys.add(empty);
                    amounts.add(0L);
                } else {
                    keys.add(keyConstructor.newInstance(template.copy()));
                    amounts.add(1L);
                }
            }
            Class<?> packetType = Class.forName(
                "com.wintercogs.beyonddimensions.network.packet.c2s.RecipeFillC2SPacket"
            );
            Object packet = constructor(packetType, List.class, List.class).newInstance(keys, amounts);
            for (String distributorName : List.of(
                "net.neoforged.neoforge.network.PacketDistributor",
                "net.minecraftforge.network.PacketDistributor"
            )) {
                try {
                    Class<?> distributor = Class.forName(distributorName);
                    Method sendToServer = findCompatibleMethod(distributor, "sendToServer", packetType);
                    if (sendToServer != null) {
                        sendToServer.invoke(null, packet);
                        return true;
                    }
                } catch (ClassNotFoundException ignored) {
                    // Try the other loader's packet distributor.
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Optional Beyond Dimensions API.
        }
        return false;
    }

    private static boolean isRefinedStorageMenu(Object menu) {
        return isRs1Menu(menu) || isRs2Menu(menu);
    }

    private static boolean isRs1Menu(Object menu) {
        return menu != null && classOrSuper(menu.getClass(), RS1_MENU);
    }

    private static boolean isRs2Menu(Object menu) {
        return menu != null && classOrSuper(menu.getClass(), RS2_MENU);
    }

    private static boolean isBeyondMenu(Object menu) {
        return menu != null && classOrSuper(menu.getClass(), BD_MENU);
    }

    private static boolean isIntegratedTerminalMenu(Object menu) {
        return menu != null && classOrSuper(menu.getClass(), IT_MENU);
    }

    private static void prioritizeRs2(Object menu, Set<String> keys) throws ReflectiveOperationException {
        Object repository = invokeNoArg(menu, "getRepository");
        if (repository == null) {
            return;
        }
        Object view = invokeNoArg(repository, "getViewList");
        if (view instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) list;
            mutable.sort(Comparator.comparing(
                entry -> !isHighlightedResource(entry, repository, keys)
            ));
        }
    }

    private static void prioritizeRs1(Set<String> keys) throws ReflectiveOperationException {
        Object screen = Minecraft.getInstance().screen;
        if (screen == null || !classOrSuper(screen.getClass(), RS1_SCREEN)) {
            return;
        }
        Object view = invokeNoArg(screen, "getView");
        Object entries = invokeNoArg(view, "getStacks");
        if (entries instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) list;
            mutable.sort(Comparator.comparing(entry -> !isHighlightedGridStack(entry, keys)));
        }
    }

    private static void prioritizeBeyond(Object menu, Set<String> keys) throws ReflectiveOperationException {
        if (!isBeyondMenu(menu)) {
            return;
        }
        Object clientStorage = readField(menu, "clientNetStorage");
        if (clientStorage == null) {
            return;
        }
        Class<?> settings = Class.forName("com.wintercogs.beyonddimensions.config.CommonConfigRuntime");
        Object primary = readStaticField(settings, "uiSortButton");
        Object secondary = readStaticField(settings, "uiSecondSortButton");
        Object reverseState = readStaticField(settings, "uiReverseButton");
        boolean reverse = reverseState != null && "ENABLED".equals(reverseState.toString());
        Method builder = findCompatibleMethod(
            clientStorage.getClass(),
            "buildSortedIndex",
            primary == null ? null : primary.getClass(),
            secondary == null ? null : secondary.getClass(),
            boolean.class
        );
        if (builder == null) {
            return;
        }
        Object built = builder.invoke(clientStorage, primary, secondary, reverse);
        if (!(built instanceof List<?> raw)) {
            return;
        }
        List<Integer> indexes = new ArrayList<>();
        for (Object value : raw) {
            if (value instanceof Number number) {
                indexes.add(number.intValue());
            }
        }
        indexes.sort(Comparator.comparing(index -> !isHighlightedBeyondIndex(clientStorage, index, keys)));
        int first = Math.max(0, intValue(readField(menu, "lineData")) * 9);
        int visible = Math.max(0, intValue(invokeNoArg(menu, "getLines")) * 9);
        ArrayList<Integer> page = new ArrayList<>(visible);
        for (int i = 0; i < visible; i++) {
            int index = first + i;
            page.add(index < indexes.size() ? indexes.get(index) : -1);
        }
        Method loader = findCompatibleMethod(menu.getClass(), "loadIndexList", ArrayList.class);
        if (loader != null) {
            loader.invoke(menu, page);
        }
    }

    private static void prioritizeIntegrated(Object menu, Set<String> keys) throws ReflectiveOperationException {
        if (!isIntegratedTerminalMenu(menu)) {
            return;
        }
        Object tab = integratedItemClientTab(menu);
        if (tab == null) {
            return;
        }
        int channel = intValue(invokeNoArg(menu, "getSelectedChannel"));
        int count = Math.max(1, intValue(invoke(tab, "getSlotCount", int.class, channel)));
        invokeThreeInts(tab, "getSlots", channel, 0, count);
        Object views = readField(tab, "filteredIngredientsViews");
        Object entries = invoke(views, "get", int.class, channel);
        if (entries instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) list;
            mutable.sort(Comparator.comparing(entry -> !isHighlightedIntegratedEntry(entry, keys)));
        }
    }

    static void renderVirtualStorageHighlights(
        GuiGraphics graphics,
        AbstractContainerScreen<?> screen
    ) {
        if (graphics == null || screen == null || !RecipeTreeFavorites.isActive()) {
            return;
        }
        try {
            if (classOrSuper(screen.getClass(), RS1_SCREEN)) {
                renderRs1Highlights(graphics, screen);
            } else if (classOrSuper(screen.getClass(), IT_SCREEN)) {
                renderIntegratedHighlights(graphics, screen);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Rendering integrations must never make an optional terminal fatal.
        }
    }

    private static void renderRs1Highlights(GuiGraphics graphics, AbstractContainerScreen<?> screen)
        throws ReflectiveOperationException {
        Object view = invokeNoArg(screen, "getView");
        Object entries = invokeNoArg(view, "getStacks");
        if (!(entries instanceof List<?> list)) {
            return;
        }
        int rowOffset = Math.max(0, intValue(invokeNoArg(screen, "getCurrentOffset")));
        int rows = Math.max(0, intValue(invokeNoArg(screen, "getVisibleRows")));
        int start = rowOffset * 9;
        for (int local = 0; local < rows * 9 && start + local < list.size(); local++) {
            ItemStack stack = rs1EntryStack(list.get(start + local));
            drawHighlight(
                graphics,
                screen.getGuiLeft() + 8 + local % 9 * 18,
                screen.getGuiTop() + 19 + local / 9 * 18,
                stack
            );
        }
    }

    private static void renderIntegratedHighlights(GuiGraphics graphics, AbstractContainerScreen<?> screen)
        throws ReflectiveOperationException {
        Object menu = screen.getMenu();
        Object tab = integratedItemClientTab(menu);
        if (tab == null) {
            return;
        }
        int channel = intValue(invokeNoArg(menu, "getSelectedChannel"));
        int firstRow = Math.max(0, intValue(invokeNoArg(screen, "getSelectedFirstRow")));
        int rowLength = Math.max(1, intValue(invokeNoArg(screen, "getSlotRowLength")));
        int visibleRows = Math.max(0, intValue(invokeNoArg(screen, "getSlotVisibleRows")));
        int start = firstRow * rowLength;
        Object visible = invokeThreeInts(tab, "getSlots", channel, start, visibleRows * rowLength);
        if (!(visible instanceof List<?> list)) {
            return;
        }
        for (int local = 0; local < list.size(); local++) {
            ItemStack stack = stackOf(invokeNoArg(list.get(local), "getInstance"));
            Object value = invoke(screen, "getStorageSlotRect", int.class, start + local);
            // Rect2i belongs to Minecraft, so reflective Mojmap method names
            // are not stable in a production Forge runtime. Cast it and let
            // the loader remap the direct calls instead.
            if (value instanceof Rect2i rect) {
                // Integrated Terminals exposes the 16x16 item-content rect,
                // while our border starts one pixel outside that content.
                // Its tooltip is also rendered below the generic z=300
                // virtual-slot overlay, so keep this integration below the
                // tooltip while remaining above the item texture.
                drawHighlight(graphics, rect.getX() - 1, rect.getY() - 1, stack, 200);
            }
        }
    }

    private static AbstractContainerScreen<?> activeContainerScreen() {
        Object current = Minecraft.getInstance().screen;
        for (int depth = 0; depth < 4 && current instanceof RecipeTreeScreen tree; depth++) {
            current = tree.parentScreen();
        }
        return current instanceof AbstractContainerScreen<?> screen ? screen : null;
    }

    private static void drawHighlight(GuiGraphics graphics, int x, int y, ItemStack stack) {
        drawHighlight(graphics, x, y, stack, 300);
    }

    private static void drawHighlight(GuiGraphics graphics, int x, int y, ItemStack stack, int z) {
        boolean intermediate = RecipeTreeFavorites.isIntermediate(stack);
        boolean required = RecipeTreeFavorites.isRequired(stack);
        if (!intermediate && !required) {
            return;
        }
        int fill = intermediate ? 0x44FF2222 : 0x3300BBFF;
        int border = intermediate ? 0xDDFF5555 : 0xCC55DDFF;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, z);
        graphics.fill(x, y, x + 16, y + 16, fill);
        graphics.fill(x, y, x + 16, y + 1, border);
        graphics.fill(x, y + 15, x + 16, y + 16, border);
        graphics.fill(x, y, x + 1, y + 16, border);
        graphics.fill(x + 15, y, x + 16, y + 16, border);
        graphics.pose().popPose();
    }

    private static boolean isHighlightedGridStack(Object entry, Set<String> keys) {
        try {
            ItemStack stack = rs1EntryStack(entry);
            return !stack.isEmpty() && keys.contains(RecipeTreeData.ingredientKey(stack));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** RS 1.x has used both raw ItemStacks and wrapper entries across releases. */
    private static ItemStack rs1EntryStack(Object entry) throws ReflectiveOperationException {
        ItemStack stack = stackOf(entry);
        if (stack.isEmpty()) {
            stack = stackOf(invokeNoArg(entry, "getIngredient"));
        }
        if (stack.isEmpty()) {
            stack = stackOf(invokeNoArg(entry, "getStack"));
        }
        return stack;
    }

    private static boolean isHighlightedBeyondIndex(Object storage, int index, Set<String> keys) {
        try {
            Object entry = invoke(storage, "getStackBySlot", int.class, index);
            ItemStack stack = stackOf(invokeNoArg(entry, "toStack"));
            return !stack.isEmpty() && keys.contains(RecipeTreeData.ingredientKey(stack));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isHighlightedIntegratedEntry(Object entry, Set<String> keys) {
        try {
            ItemStack stack = stackOf(invokeNoArg(entry, "getInstance"));
            return !stack.isEmpty() && keys.contains(RecipeTreeData.ingredientKey(stack));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Object integratedSelectedCommonTab(Object menu) throws ReflectiveOperationException {
        Object selected = invokeNoArg(menu, "getSelectedTab");
        return selected instanceof String id
            ? invoke(menu, "getTabCommon", String.class, id)
            : null;
    }

    private static Object integratedItemClientTab(Object menu) throws ReflectiveOperationException {
        if (!isIntegratedTerminalMenu(menu)) {
            return null;
        }
        Object selected = invokeNoArg(menu, "getSelectedTab");
        if (selected instanceof String id) {
            Object tab = invoke(menu, "getTabClient", String.class, id);
            if (isIntegratedItemTab(tab)) {
                return tab;
            }
        }
        Object tabs = invokeNoArg(menu, "getTabsClient");
        if (tabs instanceof Map<?, ?> map) {
            for (Object tab : map.values()) {
                if (isIntegratedItemTab(tab)) {
                    return tab;
                }
            }
        }
        return null;
    }

    private static boolean isIntegratedItemTab(Object tab) {
        if (tab == null) {
            return false;
        }
        String name = tab.getClass().getName();
        return name.contains("TerminalStorageTabIngredientComponentItemStack");
    }

    private static boolean classOrSuper(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (name.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHighlightedResource(Object resource, Object repository, Set<String> keys) {
        try {
            ItemStack stack = stackOf(invokeNoArg(resource, "getItemStack"));
            return !stack.isEmpty() && keys.contains(RecipeTreeData.ingredientKey(stack));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static void readContainer(Object container, List<ItemStack> target, int size) throws ReflectiveOperationException {
        int actualSize = intValue(invokeNoArg(container, "getContainerSize"));
        if (actualSize <= 0) {
            actualSize = intValue(invokeNoArg(container, "size"));
        }
        for (int i = 0; i < Math.min(size, actualSize); i++) {
            target.add(stackOf(invoke(container, "getItem", int.class, i)));
        }
    }

    private static ItemStack stackOf(Object value) {
        return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : -1;
    }

    private static long numberValue(Object value) {
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        Field field = findField(target == null ? null : target.getClass(), name);
        return field == null ? null : field.get(target);
    }

    private static Object readStaticField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = findField(type, name);
        return field == null ? null : field.get(null);
    }

    private static Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = findNoArgMethod(target.getClass(), name);
        return method == null ? null : method.invoke(target);
    }

    private static Object invoke(Object target, String name, Class<?> parameterType, Object argument)
        throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = findCompatibleMethod(target.getClass(), name, parameterType);
        return method == null ? null : method.invoke(target, argument);
    }

    private static Constructor<?> constructor(Class<?> type, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        try {
            constructor.trySetAccessible();
        } catch (RuntimeException ignored) {
            // Let newInstance report a real access problem.
        }
        return constructor;
    }

    private static Constructor<?> findConstructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            return constructor(type, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object invokeThreeInts(Object target, String name, int first, int second, int third)
        throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = findCompatibleMethod(target.getClass(), name, int.class, int.class, int.class);
        return method == null ? null : method.invoke(target, first, second, third);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                try {
                    field.trySetAccessible();
                } catch (RuntimeException ignored) {
                    // Best effort for optional internals.
                }
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue through the hierarchy.
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        return findCompatibleMethod(type, name);
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?>... arguments) {
        if (type == null) {
            return null;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!method.getName().equals(name) || parameters.length != arguments.length) {
                    continue;
                }
                boolean compatible = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (arguments[i] != null && !parameters[i].isAssignableFrom(arguments[i])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    try {
                        method.trySetAccessible();
                    } catch (RuntimeException ignored) {
                        // Best effort.
                    }
                    return method;
                }
            }
        }
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!method.getName().equals(name) || parameters.length != arguments.length) {
                continue;
            }
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                if (arguments[i] != null && !parameters[i].isAssignableFrom(arguments[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                return method;
            }
        }
        return null;
    }

    static final class StoredStack {
        private final ItemStack stack;
        private final String key;
        private final long amount;

        private StoredStack(ItemStack stack, long amount) {
            this.stack = stack.copy();
            this.stack.setCount(1);
            this.key = RecipeTreeData.ingredientKey(this.stack);
            this.amount = amount;
        }

        ItemStack stack() {
            return stack;
        }

        long amount() {
            return amount;
        }
    }
}
