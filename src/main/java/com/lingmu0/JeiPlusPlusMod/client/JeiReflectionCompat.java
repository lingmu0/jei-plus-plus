package com.lingmu0.JeiPlusPlusMod.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.gui.input.IClickableIngredientInternal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** Reflection bridges for JEI internals whose packages or method names changed. */
public final class JeiReflectionCompat {
    private static final String[] USER_INPUT_HANDLER_TYPES = {
        "mezz.jei.common.input.IUserInputHandler",
        "mezz.jei.gui.input.IUserInputHandler"
    };
    private static final String[] MOUSE_OVER_TYPES = {
        "mezz.jei.common.input.IMouseOverable",
        "mezz.jei.gui.input.IMouseOverable"
    };
    private static final String[] SAME_ELEMENT_HANDLER_TYPES = {
        "mezz.jei.common.input.handlers.SameElementInputHandler",
        "mezz.jei.gui.input.handlers.SameElementInputHandler"
    };
    private static final String[] COMBINED_INPUT_HANDLER_TYPES = {
        "mezz.jei.common.input.handlers.CombinedInputHandler",
        "mezz.jei.gui.input.handlers.CombinedInputHandler"
    };
    private static final String[] PROXY_INPUT_HANDLER_TYPES = {
        "mezz.jei.gui.input.handlers.ProxyInputHandler",
        "mezz.jei.common.input.handlers.ProxyInputHandler"
    };

    private JeiReflectionCompat() {
    }

    public static Object clientConfigs() {
        Class<?> internal = loadType("mezz.jei.common.Internal");
        return invokeStaticNoArgs(internal, "getClientConfigs", "getJeiClientConfigs");
    }

    public static Object customInputHandler(InvocationHandler handler) {
        Class<?> type = loadType(USER_INPUT_HANDLER_TYPES);
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    public static Object combineInputHandlers(String label, Object... handlers) {
        Class<?> type = loadType(COMBINED_INPUT_HANDLER_TYPES);
        try {
            Constructor<?> constructor = type.getConstructor(String.class, List.class);
            return constructor.newInstance(label, List.of(handlers));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create JEI combined input handler", exception);
        }
    }

    public static Object proxyInputHandler(Supplier<Object> handlerSupplier) {
        Class<?> type = loadType(PROXY_INPUT_HANDLER_TYPES);
        try {
            Constructor<?> constructor = type.getConstructor(Supplier.class);
            return constructor.newInstance(handlerSupplier);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create JEI proxy input handler", exception);
        }
    }

    public static Object invokeInputHandler(Object target, Method method, Object[] args) throws Throwable {
        if (target == null) {
            throw new IllegalStateException("JEI returned a null input handler");
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    public static Object sameElementInputHandler(Object delegate, BiPredicate<Double, Double> isMouseOver) {
        Object mouseOver = mouseOverable(isMouseOver);

        Class<?> handlerType = loadType(SAME_ELEMENT_HANDLER_TYPES);
        for (Constructor<?> constructor : handlerType.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 2 && parameters[0].isInstance(delegate) && parameters[1].isInstance(mouseOver)) {
                try {
                    return constructor.newInstance(delegate, mouseOver);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to create JEI same-element input handler", exception);
                }
            }
        }
        throw new IllegalStateException("Unsupported JEI same-element input handler: " + handlerType.getName());
    }

    public static IClickableIngredientInternal<?> clickableIngredient(
        Object element,
        BiPredicate<Double, Double> isMouseOver,
        boolean canClickToFocus,
        boolean allowCheat
    ) {
        Object mouseOverable = mouseOverable(isMouseOver);
        Class<?> type = loadType("mezz.jei.gui.input.ClickableIngredientInternal");
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 4
                && parameters[0].isInstance(element)
                && parameters[1].isInstance(mouseOverable)
                && parameters[2] == boolean.class
                && parameters[3] == boolean.class) {
                try {
                    return (IClickableIngredientInternal<?>) constructor.newInstance(
                        element,
                        mouseOverable,
                        canClickToFocus,
                        allowCheat
                    );
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to create JEI clickable ingredient", exception);
                }
            }
        }
        throw new IllegalStateException("Unsupported JEI clickable ingredient constructor: " + type.getName());
    }

    public static BiPredicate<Double, Double> mouseOverPredicate(Object mouseOverable) {
        return (mouseX, mouseY) -> {
            try {
                Method method = mouseOverable.getClass().getMethod("isMouseOver", double.class, double.class);
                return (Boolean) method.invoke(mouseOverable, mouseX, mouseY);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to query JEI mouse-over target", exception);
            }
        };
    }

    private static Object mouseOverable(BiPredicate<Double, Double> isMouseOver) {
        Class<?> mouseOverType = loadType(MOUSE_OVER_TYPES);
        return Proxy.newProxyInstance(
            mouseOverType.getClassLoader(),
            new Class<?>[]{mouseOverType},
            (proxy, method, args) -> switch (method.getName()) {
                case "isMouseOver" -> isMouseOver.test(
                    ((Number) args[0]).doubleValue(),
                    ((Number) args[1]).doubleValue()
                );
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "JEI++ mouse-over predicate";
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    public static InputConstants.Key inputKey(Object input) {
        return (InputConstants.Key) invokeNoArgsUnchecked(input, "getKey");
    }

    public static double inputMouseX(Object input) {
        return ((Number) invokeNoArgsUnchecked(input, "getMouseX")).doubleValue();
    }

    public static double inputMouseY(Object input) {
        return ((Number) invokeNoArgsUnchecked(input, "getMouseY")).doubleValue();
    }

    public static boolean isInputSimulated(Object input) {
        return (Boolean) invokeNoArgsUnchecked(input, "isSimulate");
    }

    public static Object invokeNoArgsUnchecked(Object target, String name) {
        try {
            return invokeNoArgs(target, name);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read JEI input " + name, exception);
        }
    }

    public static Object invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invokeStaticNoArgs(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                return method.invoke(null);
            } catch (NoSuchMethodException ignored) {
                // Try the getter used by the other supported JEI line.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to read JEI client configs", exception);
            }
        }
        throw new IllegalStateException("No supported client config getter on " + type.getName());
    }

    private static Class<?> loadType(String... names) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        for (String name : names) {
            try {
                return Class.forName(name, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
                try {
                    return Class.forName(name, false, JeiReflectionCompat.class.getClassLoader());
                } catch (ClassNotFoundException ignoredAgain) {
                    // Continue with the alternate package name.
                }
            }
        }
        throw new IllegalStateException("None of the supported JEI classes are available: " + String.join(", ", names));
    }
}
