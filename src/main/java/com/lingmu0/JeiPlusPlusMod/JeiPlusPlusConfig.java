package com.lingmu0.JeiPlusPlusMod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Client settings for JEI++ behaviour. */
public final class JeiPlusPlusConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue PREFER_BOOKMARKED_RECIPE_ON_INGREDIENT_BOOKMARK =
        BUILDER
            .comment("When JEI prioritizes bookmarked recipes, bookmark the recipe under the cursor instead of the ingredient.")
            .define("preferBookmarkedRecipeOnIngredientBookmark", true);

    public static final ForgeConfigSpec.BooleanValue CREATIVE_TAB_BAR_ENABLED =
        BUILDER
            .comment("Show a creative-mode item tab bar above JEI's ingredient list.")
            .define("creativeTabBarEnabled", true);

    public static final ForgeConfigSpec.BooleanValue STACK_GROUPING_ENABLED =
        BUILDER
            .comment("Collapse related item variants in JEI's ingredient list. Click a group to expand it.")
            .define("stackGroupingEnabled", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private JeiPlusPlusConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
