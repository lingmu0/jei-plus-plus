package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.world.item.CreativeModeTab;

import java.util.List;

/**
 * State exposed by the JEI ingredient filter mixin to the small client-side
 * creative-tab bar and to grouped ingredient elements.
 */
public interface IngredientListFeatureSource {
    List<CreativeModeTab> jeiPlusPlus$getCreativeTabs();

    int jeiPlusPlus$getSelectedCreativeTab();

    void jeiPlusPlus$selectCreativeTab(int index);

    void jeiPlusPlus$toggleGroup(String groupKey);

    List<IElement<?>> jeiPlusPlus$transformElements(List<IElement<?>> elements);
}
