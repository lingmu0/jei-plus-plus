package com.lingmu0.JeiPlusPlusMod.mixin;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Selects JEI mixin signatures that match the installed JEI build. */
public final class JeiPlusPlusMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String JEI_MOD_ID = "jei";
    private static final String SERVICE_MIXIN = ".IngredientLookupStateServiceMixin";
    private static final String LEGACY_MIXIN = ".IngredientLookupStateLegacyMixin";
    private static final String SERVICE_SIGNATURE_VERSION = "19.54.0.429";

    // JEI 19.64 replaced user-input handlers with input targets: mouseScrolled
    // moved into input controllers and BookmarkInputHandler became
    // BookmarkCommands.  Both shapes are shipped in the same jar and selected
    // here, so every supported JEI build only ever loads what it can apply.
    private static final String LEGACY_RECIPES_GUI_MIXIN = ".RecipesGuiMixin";
    private static final String MODERN_RECIPES_GUI_MIXIN = ".RecipesGuiScrollMixin";
    private static final String LEGACY_TABS_MIXIN = ".RecipeGuiTabsMixin";
    private static final String LEGACY_BOOKMARK_INPUT_MIXIN = ".BookmarkInputHandlerMixin";
    private static final String MODERN_BOOKMARK_COMMAND_MIXIN = ".BookmarkCommandsMixin";
    private static final String MODERN_RECIPES_GUI_ACCESSOR = ".RecipesGuiScrollAccessor";
    private static final String MODERN_TABS_ACCESSOR = ".RecipeGuiTabsAccessor";
    private static final String INPUT_TARGET_TYPE = "mezz.jei.common.input.IInputTarget";
    private static final String INPUT_TARGET_API_VERSION = "19.64.0";

    private LookupSignature lookupSignature;
    private Boolean inputTargetApi;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(SERVICE_MIXIN)) {
            return lookupSignature() == LookupSignature.SERVICE;
        }
        if (mixinClassName.endsWith(LEGACY_MIXIN)) {
            return lookupSignature() == LookupSignature.LEGACY;
        }
        if (mixinClassName.endsWith(MODERN_RECIPES_GUI_MIXIN)
            || mixinClassName.endsWith(MODERN_BOOKMARK_COMMAND_MIXIN)
            || mixinClassName.endsWith(MODERN_RECIPES_GUI_ACCESSOR)
            || mixinClassName.endsWith(MODERN_TABS_ACCESSOR)) {
            return inputTargetApi();
        }
        if (mixinClassName.endsWith(LEGACY_RECIPES_GUI_MIXIN)
            || mixinClassName.endsWith(LEGACY_TABS_MIXIN)
            || mixinClassName.endsWith(LEGACY_BOOKMARK_INPUT_MIXIN)) {
            return !inputTargetApi();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
        String targetClassName,
        ClassNode targetClass,
        String mixinClassName,
        IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
        String targetClassName,
        ClassNode targetClass,
        String mixinClassName,
        IMixinInfo mixinInfo
    ) {
    }

    private LookupSignature lookupSignature() {
        if (lookupSignature == null) {
            lookupSignature = detectLookupSignature();
        }
        return lookupSignature;
    }

    /** True when the installed JEI exposes the input-target API. */
    private boolean inputTargetApi() {
        if (inputTargetApi == null) {
            inputTargetApi = detectInputTargetApi();
        }
        return inputTargetApi;
    }

    private static LookupSignature detectLookupSignature() {
        String jeiVersion = findJeiVersion();
        if (jeiVersion != null) {
            return isAtLeast(jeiVersion, SERVICE_SIGNATURE_VERSION)
                ? LookupSignature.SERVICE
                : LookupSignature.LEGACY;
        }

        // Keep the feature safe if NeoForge's loading list is not ready yet.
        if (classExists("mezz.jei.common.transfer.RecipeTransferService")) {
            return LookupSignature.SERVICE;
        }
        if (classExists("mezz.jei.api.recipe.transfer.IRecipeTransferManager")) {
            return LookupSignature.LEGACY;
        }
        return LookupSignature.UNKNOWN;
    }

    private static boolean detectInputTargetApi() {
        String jeiVersion = findJeiVersion();
        if (jeiVersion != null) {
            return isAtLeast(jeiVersion, INPUT_TARGET_API_VERSION);
        }

        // Keep the feature safe if NeoForge's loading list is not ready yet.
        return classExists(INPUT_TARGET_TYPE);
    }

    private static String findJeiVersion() {
        try {
            ModFileInfo jei = FMLLoader.getLoadingModList().getModFileById(JEI_MOD_ID);
            return jei == null ? null : jei.versionString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean classExists(String className) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class.forName(className, false, classLoader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isAtLeast(String actual, String minimum) {
        String[] actualParts = actual.split("\\.");
        String[] minimumParts = minimum.split("\\.");
        int length = Math.max(actualParts.length, minimumParts.length);
        for (int i = 0; i < length; i++) {
            int actualPart = i < actualParts.length ? parseVersionPart(actualParts[i]) : 0;
            int minimumPart = i < minimumParts.length ? parseVersionPart(minimumParts[i]) : 0;
            if (actualPart != minimumPart) {
                return actualPart > minimumPart;
            }
        }
        return true;
    }

    private static int parseVersionPart(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(part.substring(0, end));
    }

    private enum LookupSignature {
        LEGACY,
        SERVICE,
        UNKNOWN
    }
}
