package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persists and applies the creative-tab order changed in the tab selector. */
public final class CreativeTabOrder {
    private CreativeTabOrder() {
    }

    public static List<CreativeModeTab> order(List<CreativeModeTab> tabs) {
        Map<String, CreativeModeTab> remaining = new LinkedHashMap<>();
        for (CreativeModeTab tab : tabs) {
            String id = getId(tab);
            if (id != null) {
                remaining.putIfAbsent(id, tab);
            }
        }

        List<CreativeModeTab> result = new ArrayList<>(tabs.size());
        for (String id : JeiPlusPlusConfig.PINNED_CREATIVE_TABS.get()) {
            CreativeModeTab tab = remaining.remove(id);
            if (tab != null) {
                result.add(tab);
            }
        }
        result.addAll(remaining.values());
        return List.copyOf(result);
    }

    public static String getId(CreativeModeTab tab) {
        Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id == null ? null : id.toString();
    }

    public static boolean isPinned(CreativeModeTab tab) {
        String id = getId(tab);
        return id != null && JeiPlusPlusConfig.PINNED_CREATIVE_TABS.get().contains(id);
    }

    /** Newly pinned tabs are inserted ahead of all existing pins. */
    public static void togglePinned(CreativeModeTab tab) {
        String id = getId(tab);
        if (id == null) {
            return;
        }

        List<String> pinned = new ArrayList<>();
        for (String pinnedId : JeiPlusPlusConfig.PINNED_CREATIVE_TABS.get()) {
            if (!pinnedId.equals(id) && !pinned.contains(pinnedId)) {
                pinned.add(pinnedId);
            }
        }
        boolean wasPinned = JeiPlusPlusConfig.PINNED_CREATIVE_TABS.get().contains(id);
        if (!wasPinned) {
            pinned.add(0, id);
        }

        JeiPlusPlusConfig.PINNED_CREATIVE_TABS.set(List.copyOf(pinned));
        JeiPlusPlusConfig.PINNED_CREATIVE_TABS.save();
    }
}
