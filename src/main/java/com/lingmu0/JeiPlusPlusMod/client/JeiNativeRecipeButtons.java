package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.helpers.IGuiHelper;

/** Loaded only when the installed JEI supports interaction-based recipe buttons. */
final class JeiNativeRecipeButtons {
    private JeiNativeRecipeButtons() {}

    static void register(IAdvancedRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeButtonFactory(new RecipeTreeButtonFactory(helper));
        registration.addRecipeButtonFactory(new RecipeDefaultButtonFactory(helper));
    }
}
