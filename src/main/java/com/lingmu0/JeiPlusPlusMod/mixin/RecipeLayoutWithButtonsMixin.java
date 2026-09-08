package com.lingmu0.JeiPlusPlusMod.mixin;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/** Hides JEI's native recipe-bookmark button in favour of the tree actions. */
@Mixin(value = RecipeLayoutWithButtons.class, remap = false)
public abstract class RecipeLayoutWithButtonsMixin {
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void jeiPlusPlus$removeNativeRecipeBookmarkButton(CallbackInfo ci) {
        if (!JeiPlusPlusConfig.HIDE_RECIPE_BOOKMARK_BUTTON.get()) {
            return;
        }

        // JEI 19.x keeps all side buttons in one list. JEI 15.56+ instead
        // stores the recipe bookmark button separately and uses its visible
        // flag when calculating the side-button layout. Resolve both shapes
        // reflectively so a 1.20.1 instance can run the same jar logic as
        // 1.21.1 without a version-specific field shadow failure.
        Object buttons = jeiPlusPlus$getField(this, "buttons");
        if (buttons instanceof List<?> list) {
            // JEI creates buttons in the fixed order: transfer, recipe
            // bookmark, then registered extra buttons.
            if (list.size() > 1) {
                try {
                    list.remove(1);
                } catch (UnsupportedOperationException ignored) {
                    // A future JEI may expose an immutable copy here. The
                    // compatibility fix must never make JEI fail to start.
                }
            }
            return;
        }

        Object bookmarkButton = jeiPlusPlus$getField(this, "bookmarkButton");
        Object button = jeiPlusPlus$getField(bookmarkButton, "button");
        if (button != null) {
            jeiPlusPlus$setBooleanField(button, false, "visible", "f_93624_");
            jeiPlusPlus$setBooleanField(button, false, "active", "f_93623_");
        }
    }

    @Unique
    private static Object jeiPlusPlus$getField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Field field = jeiPlusPlus$findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static boolean jeiPlusPlus$setBooleanField(Object target, boolean value, String... names) {
        if (target == null) {
            return false;
        }
        for (String name : names) {
            Field field = jeiPlusPlus$findField(target.getClass(), name);
            if (field == null || field.getType() != boolean.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.setBoolean(target, value);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next mapped/obfuscated field name.
            }
        }
        return false;
    }

    @Unique
    private static Field jeiPlusPlus$findField(Class<?> type, String name) {
        while (type != null) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }
}
