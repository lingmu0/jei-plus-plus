package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.gui.GuiGraphics;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Shared reflective bridge for JEI's pre- and post-19.42 ingredient grids. */
public final class CreativeTabGridCompat {
    private static volatile boolean selectorOpen;

    private static final ClassValue<GridAccess> ACCESS = new ClassValue<>() {
        @Override
        protected GridAccess computeValue(Class<?> type) {
            try {
                Field source = type.getDeclaredField("ingredientSource");
                source.setAccessible(true);
                return new GridAccess(
                    source,
                    type.getMethod("getBackgroundArea"),
                    type.getMethod("getBackButtonArea")
                );
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported JEI ingredient-grid internals: " + type.getName(), exception);
            }
        }
    };

    private CreativeTabGridCompat() {
    }

    public static ImmutableRect2i reserveRow(Object owner, ImmutableRect2i availableArea) {
        if (getFeatureSource(owner) == null || !JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()) {
            return availableArea;
        }
        return availableArea.cropTop(CreativeTabBar.getReservedHeight());
    }

    public static void draw(Object owner, GuiGraphics graphics, int mouseX, int mouseY) {
        IngredientListFeatureSource source = getFeatureSource(owner);
        if (source != null && JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()) {
            CreativeTabBar.draw(source, graphics, getArea(owner), mouseX, mouseY);
        }
    }

    public static void drawTooltip(Object owner, GuiGraphics graphics, int mouseX, int mouseY) {
        IngredientListFeatureSource source = getFeatureSource(owner);
        if (source != null && JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()) {
            CreativeTabBar.drawTooltip(source, graphics, getArea(owner), mouseX, mouseY);
        }
    }

    public static Object wrapInput(Object owner, Object delegate) {
        return JeiReflectionCompat.customInputHandler(
            (proxy, method, args) -> handleTabInput(owner, delegate, proxy, method, args)
        );
    }

    public static boolean isSelectorOpen(Object owner) {
        if (selectorOpen) {
            return true;
        }
        IngredientListFeatureSource source = getFeatureSource(owner);
        return source != null && source.jeiPlusPlus$isCreativeTabSelectorOpen();
    }

    public static void setSelectorOpen(boolean open) {
        selectorOpen = open;
    }

    public static boolean isAnySelectorOpen() {
        return selectorOpen;
    }

    private static IngredientListFeatureSource getFeatureSource(Object owner) {
        try {
            Object source = ACCESS.get(owner.getClass()).ingredientSource().get(owner);
            return source instanceof IngredientListFeatureSource features ? features : null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to access JEI ingredient source", exception);
        }
    }

    private static ImmutableRect2i getArea(Object owner) {
        GridAccess access = ACCESS.get(owner.getClass());
        try {
            ImmutableRect2i background = (ImmutableRect2i) access.getBackgroundArea().invoke(owner);
            ImmutableRect2i backButton = (ImmutableRect2i) access.getBackButtonArea().invoke(owner);
            return CreativeTabBar.getArea(background, backButton);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read JEI ingredient-grid bounds", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("JEI ingredient-grid bounds failed", exception.getCause());
        }
    }

    private record GridAccess(Field ingredientSource, Method getBackgroundArea, Method getBackButtonArea) {
    }

    private static Object handleTabInput(
        Object owner,
        Object delegate,
        Object proxy,
        Method method,
        Object[] args
    ) throws Throwable {
        switch (method.getName()) {
            case "equals" -> {
                return proxy == args[0];
            }
            case "hashCode" -> {
                return System.identityHashCode(proxy);
            }
            case "toString" -> {
                return "JEI++ creative tab input handler";
            }
            case "unfocus" -> {
                return JeiReflectionCompat.invokeInputHandler(delegate, method, args);
            }
            case "handleUserInput" -> {
                IngredientListFeatureSource source = getFeatureSource(owner);
                return source != null && CreativeTabBar.handleClick(source, getArea(owner), args[1])
                    ? java.util.Optional.of(proxy)
                    : JeiReflectionCompat.invokeInputHandler(delegate, method, args);
            }
            case "handleMouseScrolled" -> {
                IngredientListFeatureSource source = getFeatureSource(owner);
                if (source != null) {
                    java.util.Optional<Object> result = CreativeTabBar.handleScroll(
                        source,
                        getArea(owner),
                        ((Number) args[0]).doubleValue(),
                        ((Number) args[1]).doubleValue(),
                        ((Number) args[3]).doubleValue(),
                        proxy
                    );
                    if (result.isPresent()) {
                        return result;
                    }
                }
                return JeiReflectionCompat.invokeInputHandler(delegate, method, args);
            }
            default -> {
                return JeiReflectionCompat.invokeInputHandler(delegate, method, args);
            }
        }
    }
}
