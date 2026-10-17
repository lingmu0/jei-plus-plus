package com.lingmu0.JeiPlusPlusMod.client;

import com.lingmu0.JeiPlusPlusMod.JeiPlusPlusConfig;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.IInputInteraction;
import mezz.jei.common.input.interaction.InputAction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

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

    public static void draw(Object owner, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        IngredientListFeatureSource source = getFeatureSource(owner);
        if (source != null && JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()) {
            CreativeTabBar.draw(source, graphics, getArea(owner), mouseX, mouseY);
        }
    }

    public static void drawTooltip(Object owner, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        IngredientListFeatureSource source = getFeatureSource(owner);
        if (source != null && JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()) {
            CreativeTabBar.drawTooltip(source, graphics, getArea(owner), mouseX, mouseY);
        }
    }

    public static IInputTarget wrapInput(Object owner, IInputTarget delegate) {
        return new CreativeTabInputHandler(owner, delegate);
    }

    public static boolean isMouseOverTabs(Object owner, double mouseX, double mouseY) {
        return getFeatureSource(owner) != null && JeiPlusPlusConfig.CREATIVE_TAB_BAR_ENABLED.get()
            && (isSelectorOpen(owner) || getArea(owner).contains(mouseX, mouseY));
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

    private static final class CreativeTabInputHandler implements IInputTarget {
        private final Object owner;
        private final IInputTarget delegate;

        private CreativeTabInputHandler(Object owner, IInputTarget delegate) {
            this.owner = owner;
            this.delegate = delegate;
        }

        @Override
        public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keyBindings) {
            IngredientListFeatureSource source = getFeatureSource(owner);
            Optional<InputAction> action = JeiInputActions.prepare(input,
                event -> CreativeTabBar.handleClick(source, getArea(owner), event));
            return action.isPresent() ? Optional.of(action.get()) : delegate.beginInput(screen, input, keyBindings);
        }

        @Override
        public void resetInput() {
            delegate.resetInput();
        }

        @Override
        public boolean scroll(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
            IngredientListFeatureSource source = getFeatureSource(owner);
            return (source != null && CreativeTabBar.handleScroll(source, getArea(owner), mouseX, mouseY, scrollDeltaY))
                || delegate.scroll(mouseX, mouseY, scrollDeltaX, scrollDeltaY);
        }
    }
}
