package com.lingmu0.JeiPlusPlusMod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public static final ForgeConfigSpec.BooleanValue NBT_GROUPING_ENABLED =
        BUILDER
            .comment("Collapse different NBT/component variants of the same item, such as potions and enchanted books.")
            .define("nbtGroupingEnabled", true);

    public static final ForgeConfigSpec.BooleanValue TAG_GROUPING_ENABLED =
        BUILDER
            .comment("Enable JSON-defined item tag groups in config/jei_plus_plus/stack_groups.")
            .define("tagGroupingEnabled", true);

    public static final ForgeConfigSpec.BooleanValue JSON_GROUPING_ENABLED =
        BUILDER
            .comment("Enable custom JSON stack groups in config/jei_plus_plus/stack_groups.")
            .define("jsonGroupingEnabled", true);

    public static final ForgeConfigSpec.BooleanValue MIX_NAMESPACE_GROUPS =
        BUILDER
            .comment("Mix matching default groups from different mod namespaces. JSON groups are always explicit.")
            .define("mixNamespaceGroups", true);

    public static final Map<String, ForgeConfigSpec.BooleanValue> DEFAULT_GROUPS;

    static {
        BUILDER.comment("Each built-in suffix group can be disabled independently.").push("defaultGroups");
        Map<String, ForgeConfigSpec.BooleanValue> values = new LinkedHashMap<>();
        for (StackGroupCatalog.DefaultGroup group : StackGroupCatalog.DEFAULT_GROUPS) {
            values.put(group.id(), BUILDER.define(group.id(), true));
        }
        BUILDER.pop();
        DEFAULT_GROUPS = Map.copyOf(values);
    }

    public static boolean isDefaultGroupEnabled(String id) {
        ForgeConfigSpec.BooleanValue value = DEFAULT_GROUPS.get(id);
        return value == null || value.get();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private JeiPlusPlusConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
