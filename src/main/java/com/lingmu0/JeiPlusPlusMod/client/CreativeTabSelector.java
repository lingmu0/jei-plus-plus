package com.lingmu0.JeiPlusPlusMod.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.input.UserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Modal category picker opened by right-clicking the creative-tab strip. */
public final class CreativeTabSelector {
    private static final int COLUMNS = 4;
    private static final int MAX_ROWS = 6;
    private static final int ROW_HEIGHT = 20;
    private static final int TARGET_WIDTH = 314;
    private static final int HEADER_HEIGHT = 25;
    private static final int FOOTER_HEIGHT = 27;

    private CreativeTabSelector() {
    }

    public static void draw(
        IngredientListFeatureSource source,
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY
    ) {
        Layout layout = getLayout();
        List<CreativeModeTab> tabs = source.jeiPlusPlus$getCreativeTabs();
        int total = tabs.size() + 1;
        int pageCount = getPageCount(total, layout.pageSize());
        int page = clampPage(source.jeiPlusPlus$getCreativeTabSelectorPage(), pageCount);
        if (page != source.jeiPlusPlus$getCreativeTabSelectorPage()) {
            source.jeiPlusPlus$setCreativeTabSelectorPage(page);
        }

        var pose = graphics.pose();
        pose.pushMatrix();
        // Draw the modal above the underlying item sprites, hover outlines, and highlights.
        graphics.nextStratum();
        graphics.fill( 0, 0, layout.screenWidth(), layout.screenHeight(), 0x90000000);
        graphics.fill( layout.x() - 1, layout.y() - 1,
            layout.x() + layout.width() + 1, layout.y() + layout.height() + 1, 0xFFE0E0E0);
        graphics.fill( layout.x(), layout.y(),
            layout.x() + layout.width(), layout.y() + layout.height(), 0xF0101010);
        graphics.centeredText(
            Minecraft.getInstance().font,
            Component.translatable("jei_plus_plus.creative_tab.selector_title"),
            layout.x() + layout.width() / 2,
            layout.y() + 8,
            0xFFFFFFFF
        );

        int closeX = layout.x() + layout.width() - 16;
        graphics.centeredText(
            Minecraft.getInstance().font,
            "x",
            closeX + 5,
            layout.y() + 7,
            contains(mouseX, mouseY, closeX, layout.y() + 4, 12, 12) ? 0xFFFF7777 : 0xFFD0D0D0
        );

        int start = page * layout.pageSize();
        int end = Math.min(total, start + layout.pageSize());
        for (int index = start; index < end; index++) {
            int local = index - start;
            int column = local % COLUMNS;
            int row = local / COLUMNS;
            int cellX = layout.x() + 6 + column * layout.cellWidth();
            int cellY = layout.y() + HEADER_HEIGHT + row * ROW_HEIGHT;
            int cellWidth = layout.cellWidth() - 2;
            boolean hovered = contains(mouseX, mouseY, cellX, cellY, cellWidth, 18);
            boolean selected = index == source.jeiPlusPlus$getSelectedCreativeTab();
            boolean pinned = index > 0 && CreativeTabOrder.isPinned(tabs.get(index - 1));
            int border = selected ? 0xFF55DDAA : pinned ? 0xFFFFCC55 : hovered ? 0xFF8099FF : 0xFF707070;
            graphics.fill( cellX, cellY, cellX + cellWidth, cellY + 18, border);
            graphics.fill( cellX + 1, cellY + 1,
                cellX + cellWidth - 1, cellY + 17, selected ? 0xFF17382D : 0xFF202020);

            ItemStack icon = getIcon(tabs, index);
            if (!icon.isEmpty()) {
                graphics.item(icon, cellX + 2, cellY + 1);
            }
            String title = getTitle(tabs, index).getString();
            String clippedTitle = clip(title, Math.max(1, cellWidth - 27));
            graphics.text(
                Minecraft.getInstance().font,
                clippedTitle,
                cellX + 21,
                cellY + 5,
                0xFFFFFFFF,
                false
            );
            if (pinned) {
                drawPin(graphics, cellX + cellWidth - 9, cellY + 4);
            }
        }

        int footerY = layout.y() + layout.height() - FOOTER_HEIGHT + 6;
        drawPageButton(graphics, layout.x() + 7, footerY, "<", pageCount > 1,
            contains(mouseX, mouseY, layout.x() + 7, footerY, 16, 14));
        drawPageButton(graphics, layout.x() + layout.width() - 23, footerY, ">", pageCount > 1,
            contains(mouseX, mouseY, layout.x() + layout.width() - 23, footerY, 16, 14));
        graphics.centeredText(
            Minecraft.getInstance().font,
            (page + 1) + "/" + pageCount,
            layout.x() + layout.width() / 2,
            footerY + 3,
            0xFFD0D0D0
        );
        pose.popMatrix();
    }

    public static void drawTooltip(
        IngredientListFeatureSource source,
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY
    ) {
        Layout layout = getLayout();
        int index = getEntryAt(source, layout, mouseX, mouseY);
        if (index >= 0) {
            var pose = graphics.pose();
            pose.pushMatrix();
            // Keep the tooltip above the selector and its item-render layer.
            graphics.nextStratum();
            graphics.setTooltipForNextFrame(
                Minecraft.getInstance().font,
                getTitle(source.jeiPlusPlus$getCreativeTabs(), index),
                mouseX,
                mouseY
            );
            pose.popMatrix();
        }
    }

    /** Returns true whenever an open selector consumes this input. */
    public static boolean handleClick(
        IngredientListFeatureSource source,
        ImmutableRect2i tabBarArea,
        UserInput input
    ) {
        if (input.getKey().getType() == InputConstants.Type.KEYSYM) {
            if (input.getKey().getValue() == InputConstants.KEY_ESCAPE && !input.isSimulate()) {
                source.jeiPlusPlus$setCreativeTabSelectorOpen(false);
            }
            return true;
        }
        if (input.getKey().getType() != InputConstants.Type.MOUSE) {
            return true;
        }

        Layout layout = getLayout();
        double mouseX = input.getMouseX();
        double mouseY = input.getMouseY();
        int button = input.getKey().getValue();
        if (button == 0 && isCloseButton(layout, mouseX, mouseY)) {
            if (!input.isSimulate()) {
                source.jeiPlusPlus$setCreativeTabSelectorOpen(false);
            }
            return true;
        }

        if (button == 0 && isPageButton(layout, mouseX, mouseY, true)) {
            if (!input.isSimulate()) {
                setPage(source, layout, source.jeiPlusPlus$getCreativeTabSelectorPage() - 1);
            }
            return true;
        }
        if (button == 0 && isPageButton(layout, mouseX, mouseY, false)) {
            if (!input.isSimulate()) {
                setPage(source, layout, source.jeiPlusPlus$getCreativeTabSelectorPage() + 1);
            }
            return true;
        }

        int index = getEntryAt(source, layout, mouseX, mouseY);
        if (index >= 0) {
            if (!input.isSimulate()) {
                if (button == 0) {
                    int capacity = CreativeTabBar.getCapacity(tabBarArea);
                    int pageCount = getPageCount(source.jeiPlusPlus$getCreativeTabs().size() + 1, capacity);
                    source.jeiPlusPlus$setCreativeTabPage(clampPage(index / capacity, pageCount));
                    source.jeiPlusPlus$selectCreativeTab(index);
                    source.jeiPlusPlus$setCreativeTabSelectorOpen(false);
                } else if (button == 1 && index > 0) {
                    togglePinned(source, index - 1);
                }
            }
            return true;
        }

        if (!contains(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
            if (!input.isSimulate()) {
                source.jeiPlusPlus$setCreativeTabSelectorOpen(false);
            }
        }
        return true;
    }

    /** Modal scrolling advances its own pages and blocks the JEI grid below. */
    public static boolean handleScroll(
        IngredientListFeatureSource source,
        double mouseX,
        double mouseY,
        double scrollDeltaY
    ) {
        if (!source.jeiPlusPlus$isCreativeTabSelectorOpen()) {
            return false;
        }
        Layout layout = getLayout();
        if (scrollDeltaY != 0 && contains(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
            int direction = scrollDeltaY < 0 ? 1 : -1;
            setPage(source, layout, source.jeiPlusPlus$getCreativeTabSelectorPage() + direction);
        }
        return true;
    }

    private static void togglePinned(IngredientListFeatureSource source, int tabIndex) {
        List<CreativeModeTab> oldOrder = source.jeiPlusPlus$getCreativeTabs();
        if (tabIndex < 0 || tabIndex >= oldOrder.size()) {
            return;
        }
        int selected = source.jeiPlusPlus$getSelectedCreativeTab();
        String selectedId = selected > 0 && selected <= oldOrder.size()
            ? CreativeTabOrder.getId(oldOrder.get(selected - 1))
            : null;

        CreativeTabOrder.togglePinned(oldOrder.get(tabIndex));
        List<CreativeModeTab> newOrder = source.jeiPlusPlus$getCreativeTabs();
        if (selectedId != null) {
            int newSelected = 0;
            for (int i = 0; i < newOrder.size(); i++) {
                if (selectedId.equals(CreativeTabOrder.getId(newOrder.get(i)))) {
                    newSelected = i + 1;
                    break;
                }
            }
            source.jeiPlusPlus$selectCreativeTab(newSelected);
        }
        source.jeiPlusPlus$setCreativeTabSelectorPage(0);
    }

    private static void setPage(IngredientListFeatureSource source, Layout layout, int page) {
        int pageCount = getPageCount(
            source.jeiPlusPlus$getCreativeTabs().size() + 1,
            layout.pageSize()
        );
        source.jeiPlusPlus$setCreativeTabSelectorPage(Math.floorMod(page, pageCount));
    }

    private static int getEntryAt(
        IngredientListFeatureSource source,
        Layout layout,
        double mouseX,
        double mouseY
    ) {
        int localX = (int) mouseX - layout.x() - 6;
        int localY = (int) mouseY - layout.y() - HEADER_HEIGHT;
        if (localX < 0 || localY < 0 || localY >= layout.rows() * ROW_HEIGHT) {
            return -1;
        }
        int column = localX / layout.cellWidth();
        int row = localY / ROW_HEIGHT;
        if (column < 0 || column >= COLUMNS
            || localX % layout.cellWidth() >= layout.cellWidth() - 2
            || localY % ROW_HEIGHT >= 18) {
            return -1;
        }
        int index = source.jeiPlusPlus$getCreativeTabSelectorPage() * layout.pageSize()
            + row * COLUMNS + column;
        return index < source.jeiPlusPlus$getCreativeTabs().size() + 1 ? index : -1;
    }

    private static boolean isCloseButton(Layout layout, double x, double y) {
        return contains(x, y, layout.x() + layout.width() - 16, layout.y() + 4, 12, 12);
    }

    private static boolean isPageButton(Layout layout, double x, double y, boolean left) {
        int buttonX = left ? layout.x() + 7 : layout.x() + layout.width() - 23;
        int buttonY = layout.y() + layout.height() - FOOTER_HEIGHT + 6;
        return contains(x, y, buttonX, buttonY, 16, 14);
    }

    private static Layout getLayout() {
        var window = Minecraft.getInstance().getWindow();
        int screenWidth = window.getGuiScaledWidth();
        int screenHeight = window.getGuiScaledHeight();
        int width = Math.min(TARGET_WIDTH, Math.max(1, screenWidth - 8));
        int rows = Math.max(1, Math.min(MAX_ROWS, Math.max(1, (screenHeight - 48) / ROW_HEIGHT)));
        int height = HEADER_HEIGHT + rows * ROW_HEIGHT + FOOTER_HEIGHT + 8;
        return new Layout(
            screenWidth,
            screenHeight,
            (screenWidth - width) / 2,
            (screenHeight - height) / 2,
            width,
            height,
            (width - 12) / COLUMNS,
            rows
        );
    }

    private static int getPageCount(int total, int pageSize) {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    private static int clampPage(int page, int pageCount) {
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    private static ItemStack getIcon(List<CreativeModeTab> tabs, int index) {
        return index == 0 ? Items.CRAFTING_TABLE.getDefaultInstance() : tabs.get(index - 1).getIconItem();
    }

    private static Component getTitle(List<CreativeModeTab> tabs, int index) {
        return index == 0
            ? Component.translatable("jei_plus_plus.creative_tab.all")
            : tabs.get(index - 1).getDisplayName();
    }

    private static String clip(String text, int maxWidth) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return end == 0 ? "" : text.substring(0, end) + suffix;
    }

    private static void drawPin(GuiGraphicsExtractor graphics, int x, int y) {
        int color = 0xFFFFCC55;
        graphics.fill( x + 2, y, x + 6, y + 3, color);
        graphics.fill( x + 3, y + 3, x + 5, y + 6, color);
        graphics.fill( x, y + 6, x + 8, y + 7, color);
    }

    private static void drawPageButton(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        String label,
        boolean enabled,
        boolean hovered
    ) {
        int border = enabled && hovered ? 0xFF8099FF : 0xFF707070;
        graphics.fill( x, y, x + 16, y + 14, border);
        graphics.fill( x + 1, y + 1, x + 15, y + 13, 0xFF202020);
        graphics.centeredText(
            Minecraft.getInstance().font,
            label,
            x + 8,
            y + 3,
            enabled ? 0xFFFFFFFF : 0xFF666666
        );
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Layout(
        int screenWidth,
        int screenHeight,
        int x,
        int y,
        int width,
        int height,
        int cellWidth,
        int rows
    ) {
        int pageSize() {
            return COLUMNS * rows;
        }
    }
}
