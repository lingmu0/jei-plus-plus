package com.lingmu0.JeiPlusPlusMod;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client settings for JEI++ behaviour. */
public final class JeiPlusPlusConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PREFER_RECIPE_BOOKMARK_ON_OUTPUT =
        BUILDER
            .comment("Prefer a recipe bookmark when bookmarking a recipe output. When disabled, bookmark the output ingredient.")
            .define("preferRecipeBookmarkOnOutput", true);

    public static final ModConfigSpec.BooleanValue HIDE_RECIPE_BOOKMARK_BUTTON =
        BUILDER
            .comment("Hide JEI's native add-to-bookmark button in recipe layouts.")
            .define("hideRecipeBookmarkButton", true);

    public static final ModConfigSpec.BooleanValue CREATIVE_TAB_BAR_ENABLED =
        BUILDER
            .comment("Show a creative-mode item tab bar above JEI's ingredient list.")
            .define("creativeTabBarEnabled", true);

    public static final ModConfigSpec.BooleanValue STACK_GROUPING_ENABLED =
        BUILDER
            .comment("Collapse related item variants in JEI's ingredient list. Click a group to expand it.")
            .define("stackGroupingEnabled", true);

    public static final ModConfigSpec.BooleanValue RECIPE_TREE_ENABLED =
        BUILDER
            .comment("Add recipe-tree and crafting-assistant buttons to JEI recipe layouts.")
            .define("recipeTreeEnabled", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_RECIPE_TYPES =
        BUILDER
            .comment("Recipe type ids excluded from the recipe tree, for example [\"minecraft:blasting\"].")
            .defineList("disabledRecipeTypes", List.of(), value -> value instanceof String);

    public static final ModConfigSpec.BooleanValue AUTOMATIC_CRAFTING_ENABLED =
        BUILDER
            .comment("Allow Ctrl-left-click in the recipe-tree bookmarks to automatically craft missing intermediate steps.")
            .define("automaticCraftingEnabled", true);

    public static final ModConfigSpec.BooleanValue NBT_GROUPING_ENABLED =
        BUILDER
            .comment("Collapse different NBT/component variants of the same item, such as potions and enchanted books.")
            .define("nbtGroupingEnabled", true);

    public static final ModConfigSpec.BooleanValue TAG_GROUPING_ENABLED =
        BUILDER
            .comment("Enable JSON-defined item tag groups in config/jei_plus_plus/stack_groups.")
            .define("tagGroupingEnabled", true);

    public static final ModConfigSpec.BooleanValue JSON_GROUPING_ENABLED =
        BUILDER
            .comment("Enable custom JSON stack groups in config/jei_plus_plus/stack_groups.")
            .define("jsonGroupingEnabled", true);

    public static final ModConfigSpec.BooleanValue MIX_NAMESPACE_GROUPS =
        BUILDER
            .comment("Mix matching default groups from different mod namespaces. JSON groups are always explicit.")
            .define("mixNamespaceGroups", true);

    public static final Map<String, ModConfigSpec.BooleanValue> DEFAULT_GROUPS;

    static {
        BUILDER.comment("Each built-in suffix group can be disabled independently.").push("defaultGroups");
        Map<String, ModConfigSpec.BooleanValue> values = new LinkedHashMap<>();
        for (StackGroupCatalog.DefaultGroup group : StackGroupCatalog.DEFAULT_GROUPS) {
            values.put(group.id(), BUILDER.define(group.id(), true));
        }
        BUILDER.pop();
        DEFAULT_GROUPS = Map.copyOf(values);
    }

    public static boolean isDefaultGroupEnabled(String id) {
        ModConfigSpec.BooleanValue value = DEFAULT_GROUPS.get(id);
        return value == null || value.get();
    }

    public static boolean isRecipeTypeDisabled(ResourceLocation recipeType) {
        if (recipeType == null) {
            return false;
        }
        String id = recipeType.toString();
        for (String configured : DISABLED_RECIPE_TYPES.get()) {
            if (configured != null && configured.trim().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Avoid transforming JEI's ingredient list twice when a dedicated JEI
     * grouping addon owns that feature. The user setting remains untouched,
     * so grouping automatically returns when the other addon is removed.
     */
    public static boolean isStackGroupingEnabled() {
        return STACK_GROUPING_ENABLED.get() && !ExternalGroupingCompat.isGroupingModLoaded();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JeiPlusPlusConfig() {
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
