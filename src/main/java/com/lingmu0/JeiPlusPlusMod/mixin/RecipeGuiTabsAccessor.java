package com.lingmu0.JeiPlusPlusMod.mixin;

import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.recipes.RecipeGuiTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RecipeGuiTabs.class, remap = false)
public interface RecipeGuiTabsAccessor {
    @Accessor("area")
    ImmutableRect2i jeiPlusPlus$getArea();

    @Accessor("pageNavigation")
    PageNavigation jeiPlusPlus$getPageNavigation();
}
