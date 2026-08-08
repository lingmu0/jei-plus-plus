package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.gui.input.IUserInputHandler;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/** Compact creative-tab strip rendered over the top of JEI's ingredient list. */
public final class CreativeTabBar {
    private static final int HEIGHT = 18;
    private static final int RESERVED_HEIGHT = HEIGHT + 1;
    private static final int TAB_WIDTH = 18;
    private static final int MAX_VISIBLE_TABS = 12;

    private CreativeTabBar() {
    }

    /** Space reserved above the ingredient grid for the tab strip. */
    public static int getReservedHeight() {
        return RESERVED_HEIGHT;
    }

    public static ImmutableRect2i getArea(ImmutableRect2i backgroundArea, ImmutableRect2i navigationArea) {
        if (backgroundArea.isEmpty()) {
            return ImmutableRect2i.EMPTY;
        }
        int x = backgroundArea.getX();
        int width = backgroundArea.getWidth();
        int y = navigationArea.isEmpty()
            ? Math.max(2, backgroundArea.getY() - RESERVED_HEIGHT)
            : Math.max(2, navigationArea.getY() - HEIGHT - 1);
        return new ImmutableRect2i(x, y, width, HEIGHT);
    }

    public static void draw(
        IngredientListFeatureSource source,
        GuiGraphics guiGraphics,
        ImmutableRect2i area,
        int mouseX,
        int mouseY
    ) {
        if (!isEnabled(source) || area.isEmpty()) {
            return;
        }
        List<CreativeModeTab> tabs = source.jeiPlusPlus$getCreativeTabs();
        int selected = source.jeiPlusPlus$getSelectedCreativeTab();
        int total = tabs.size() + 1;
        int capacity = getCapacity(area);
        int start = getStart(selected, total, capacity);

        guiGraphics.fill(
            RenderType.guiOverlay(),
            area.getX(),
            area.getY(),
            area.getX() + area.getWidth(),
            area.getY() + area.getHeight(),
            0xD0202020
        );

        for (int slot = 0; slot < capacity && start + slot < total; slot++) {
            int tabIndex = start + slot;
            int x = area.getX() + slot * TAB_WIDTH;
            if (tabIndex == selected) {
                guiGraphics.fill(
                    RenderType.guiOverlay(),
                    x,
                    area.getY(),
                    x + TAB_WIDTH,
                    area.getY() + HEIGHT,
                    0xD0FFFFFF
                );
            }
            ItemStack icon = getIcon(tabs, tabIndex);
            if (!icon.isEmpty()) {
                guiGraphics.renderItem(icon, x + 1, area.getY() + 1);
            }
        }

        // A compact page hint is useful when a modpack registers more tabs
        // than fit in one row.  Scrolling over the strip cycles through them.
        if (total > capacity) {
            String hint = (selected + 1) + "/" + total;
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                hint,
                area.getX() + area.getWidth() - Minecraft.getInstance().font.width(hint) - 2,
                area.getY() + 5,
                0xFFFFFFFF,
                true
            );
        }
    }

    public static void drawTooltip(
        IngredientListFeatureSource source,
        GuiGraphics guiGraphics,
        ImmutableRect2i area,
        int mouseX,
        int mouseY
    ) {
        int tabIndex = getTabAt(source, area, mouseX, mouseY);
        if (tabIndex < 0) {
            return;
        }
        Component title = getTitle(source.jeiPlusPlus$getCreativeTabs(), tabIndex);
        guiGraphics.renderTooltip(Minecraft.getInstance().font, title, mouseX, mouseY);
    }

    public static boolean handleClick(
        IngredientListFeatureSource source,
        ImmutableRect2i area,
        UserInput input
    ) {
        if (!isEnabled(source) || input.getKey().getType() != com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
            || input.getKey().getValue() != 0) {
            return false;
        }
        int tabIndex = getTabAt(source, area, input.getMouseX(), input.getMouseY());
        if (tabIndex < 0) {
            return false;
        }
        if (!input.isSimulate()) {
            source.jeiPlusPlus$selectCreativeTab(tabIndex);
        }
        return true;
    }

    public static Optional<IUserInputHandler> handleScroll(
        IngredientListFeatureSource source,
        ImmutableRect2i area,
        double mouseX,
        double mouseY,
        double scrollDelta,
        IUserInputHandler self
    ) {
        if (!isEnabled(source) || scrollDelta == 0 || !area.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        int total = source.jeiPlusPlus$getCreativeTabs().size() + 1;
        int selected = source.jeiPlusPlus$getSelectedCreativeTab();
        int next = scrollDelta < 0 ? selected + 1 : selected - 1;
        if (next < 0) {
            next = total - 1;
        } else if (next >= total) {
            next = 0;
        }
        source.jeiPlusPlus$selectCreativeTab(next);
        return Optional.of(self);
    }

    private static boolean isEnabled(IngredientListFeatureSource source) {
        return source != null && !source.jeiPlusPlus$getCreativeTabs().isEmpty();
    }

    private static int getCapacity(ImmutableRect2i area) {
        return Math.max(1, Math.min(MAX_VISIBLE_TABS, area.getWidth() / TAB_WIDTH));
    }

    private static int getStart(int selected, int total, int capacity) {
        if (total <= capacity) {
            return 0;
        }
        int centered = selected - capacity / 2;
        return Math.max(0, Math.min(centered, total - capacity));
    }

    private static int getTabAt(IngredientListFeatureSource source, ImmutableRect2i area, double mouseX, double mouseY) {
        if (!isEnabled(source) || !area.contains(mouseX, mouseY)) {
            return -1;
        }
        int slot = (int) ((mouseX - area.getX()) / TAB_WIDTH);
        int capacity = getCapacity(area);
        if (slot < 0 || slot >= capacity) {
            return -1;
        }
        int total = source.jeiPlusPlus$getCreativeTabs().size() + 1;
        int index = getStart(source.jeiPlusPlus$getSelectedCreativeTab(), total, capacity) + slot;
        return index < total ? index : -1;
    }

    private static ItemStack getIcon(List<CreativeModeTab> tabs, int index) {
        if (index == 0) {
            return Items.CRAFTING_TABLE.getDefaultInstance();
        }
        if (index - 1 < tabs.size()) {
            return tabs.get(index - 1).getIconItem();
        }
        return ItemStack.EMPTY;
    }

    private static Component getTitle(List<CreativeModeTab> tabs, int index) {
        if (index == 0) {
            return Component.translatable("jei_plus_plus.creative_tab.all");
        }
        if (index - 1 < tabs.size()) {
            return tabs.get(index - 1).getDisplayName();
        }
        return Component.empty();
    }
}
