package com.lingmu0.JeiPlusPlusMod;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for JEI++ behaviour. */
public final class JeiPlusPlusConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PREFER_BOOKMARKED_RECIPE_ON_INGREDIENT_BOOKMARK =
        BUILDER
            .comment("When JEI prioritizes bookmarked recipes, bookmark the recipe under the cursor instead of the ingredient.")
            .define("preferBookmarkedRecipeOnIngredientBookmark", true);

    public static final ModConfigSpec.BooleanValue CREATIVE_TAB_BAR_ENABLED =
        BUILDER
            .comment("Show a creative-mode item tab bar above JEI's ingredient list.")
            .define("creativeTabBarEnabled", true);

    public static final ModConfigSpec.BooleanValue STACK_GROUPING_ENABLED =
        BUILDER
            .comment("Collapse related item variants in JEI's ingredient list. Click a group to expand it.")
            .define("stackGroupingEnabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JeiPlusPlusConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
