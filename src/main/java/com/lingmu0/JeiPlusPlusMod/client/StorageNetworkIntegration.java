package com.lingmu0.JeiPlusPlusMod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
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
    private static final String RS1_MENU = "com.refinedmods.refinedstorage.container.GridContainerMenu";
    private static final String RS2_MENU = "com.refinedmods.refinedstorage.common.grid.AbstractCraftingGridContainerMenu";
    private static final String BD_MENU = "com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu";

    private StorageNetworkIntegration() {
    }

    static List<StoredStack> storedStacks() {
        Object menu = Ae2StorageIntegration.activeMenu();
        if (menu == null) {
            return List.of();
        }
        List<StoredStack> ae2 = new ArrayList<>();
        for (Ae2StorageIntegration.StoredStack stored : Ae2StorageIntegration.storedStacks()) {
            ae2.add(new StoredStack(stored.stack(), stored.amount()));
        }
        if (!ae2.isEmpty() || Ae2StorageIntegration.isCraftingMenu(menu)) {
            return ae2;
        }
        List<StoredStack> refined = refinedStorageStacks(menu);
        if (!refined.isEmpty() || isRefinedStorageMenu(menu)) {
            return refined;
        }
        List<StoredStack> beyond = beyondStacks(menu);
        return beyond;
    }

    /** Moves network-backed entries ahead of ordinary entries where the mod exposes a mutable view. */
    static void prioritizeVisibleEntries(Set<String> keys) {
        Ae2StorageIntegration.prioritizeVisibleEntries(keys);
        Object menu = Ae2StorageIntegration.activeMenu();
        if (menu == null || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            Object repository = invokeNoArg(menu, "getRepository");
            if (repository != null) {
                Object view = invokeNoArg(repository, "getViewList");
                if (view instanceof List<?> list) {
                    @SuppressWarnings("unchecked")
                    List<Object> mutable = (List<Object>) list;
                    mutable.sort((left, right) -> Boolean.compare(
                        isHighlightedResource(right, repository, keys),
                        isHighlightedResource(left, repository, keys)
                    ));
                }
            }
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
        return -1;
    }

    /** Result slots of RS/BD extend vanilla ResultSlot, so generic clicks remain preferred. */
    static Boolean takeCraftingResult(Object menu, int resultSlot, boolean send) {
        return Ae2StorageIntegration.takeCraftingResult(menu, resultSlot, send);
    }

    /** Only AE2 currently exposes a safe client-side cursor-to-network hook. */
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

    static boolean isCraftingMenu(Object menu) {
        return Ae2StorageIntegration.isCraftingMenu(menu)
            || isRs1Menu(menu)
            || isRs2Menu(menu)
            || isBeyondMenu(menu);
    }

    private static List<StoredStack> refinedStorageStacks(Object menu) {
        try {
            if (isRs1Menu(menu)) {
                Object grid = invokeNoArg(menu, "getGrid");
                Object cache = invokeNoArg(grid, "getStorageCache");
                Object list = invokeNoArg(cache, "getList");
                Object entries = invokeNoArg(list, "getStacks");
                if (entries instanceof Iterable<?> iterable) {
                    List<StoredStack> result = new ArrayList<>();
                    for (Object entry : iterable) {
                        ItemStack stack = stackOf(invokeNoArg(entry, "getStack"));
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
            ItemStack[][] recipe = new ItemStack[9][];
            for (int i = 0; i < 9; i++) {
                ItemStack stack = templates.get(i);
                recipe[i] = stack == null || stack.isEmpty()
                    ? new ItemStack[0]
                    : new ItemStack[] {stack.copy()};
            }
            Object message = constructor(messageType, ItemStack[][].class).newInstance((Object) recipe);
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
        private final long amount;

        private StoredStack(ItemStack stack, long amount) {
            this.stack = stack.copy();
            this.stack.setCount(1);
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
