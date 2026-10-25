package com.lingmu0.JeiPlusPlusMod.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.gui.input.IClickableIngredientInternal;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** Reflection bridges for JEI internals whose packages or method names changed. */
public final class JeiReflectionCompat {
    private static final String[] USER_INPUT_HANDLER_TYPES = {
        "mezz.jei.common.input.IInputTarget",
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
        "mezz.jei.common.input.handlers.InputGroup",
        "mezz.jei.common.input.handlers.CombinedInputHandler",
        "mezz.jei.gui.input.handlers.CombinedInputHandler"
    };
    private static final String[] PROXY_INPUT_HANDLER_TYPES = {
        "mezz.jei.gui.input.handlers.ProxyInputHandler",
        "mezz.jei.common.input.handlers.ProxyInputHandler"
    };

    private JeiReflectionCompat() {
    }

    public static Object readField(Object target, String name) {
        if (target == null) return null;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.trySetAccessible(); return field.get(target); }
            catch (NoSuchFieldException ignored) { }
            catch (ReflectiveOperationException error) { return null; }
        }
        return null;
    }

    public static Object tryInvoke(Object target, String name) {
        try { return invokeNoArgs(target, name); }
        catch (ReflectiveOperationException | RuntimeException error) { return null; }
    }

    public static boolean hasMethod(Object target, String name) {
        for (Method method : target.getClass().getMethods()) if (method.getName().equals(name)) return true;
        return false;
    }

    public static Object clientConfigs() {
        Class<?> internal = loadType("mezz.jei.common.Internal");
        return invokeStaticNoArgs(internal, "getClientConfigs", "getJeiClientConfigs");
    }

    public static Object customInputHandler(InvocationHandler handler) {
        Class<?> type = loadType(USER_INPUT_HANDLER_TYPES);
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (!type.getName().endsWith("IInputTarget")) return handler.invoke(proxy, method, args);
            Method dispatch = switch (method.getName()) {
                case "beginInput" -> LegacyDispatch.class.getMethod("handleUserInput", Object.class, Object.class, Object.class);
                case "scroll" -> LegacyDispatch.class.getMethod("handleMouseScrolled", double.class, double.class, double.class, double.class);
                case "resetInput" -> LegacyDispatch.class.getMethod("unfocus");
                default -> method;
            };
            Object result = handler.invoke(proxy, dispatch, args);
            if (method.getName().equals("scroll")) return result instanceof Optional<?> optional && optional.isPresent();
            if (method.getName().equals("beginInput") && result instanceof Optional<?> optional && optional.isPresent()) {
                Object accepted = optional.get();
                Class<?> interaction = loadType("mezz.jei.common.input.interaction.IInputInteraction");
                if (interaction.isInstance(accepted)) return result;
                Object[] press = args.clone();
                return Optional.of(inputAction(release -> {
                    Object[] completed = press.clone();
                    completed[1] = release;
                    try { handler.invoke(proxy, dispatch, completed); }
                    catch (RuntimeException | Error error) { throw error; }
                    catch (Throwable error) { throw new IllegalStateException(error); }
                }));
            }
            return result;
        });
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
        return customInputHandler((proxy, method, args) -> invokeInputHandler(handlerSupplier.get(), method, args));
    }

    /** Method tokens for dispatching the legacy and interaction-based APIs without linking either. */
    public interface LegacyDispatch {
        Object handleUserInput(Object screen, Object input, Object keys);
        Object handleMouseScrolled(double x, double y, double scrollX, double scrollY);
        void unfocus();
    }

    public static Object inputAction(java.util.function.Consumer<Object> action) {
        try {
            return loadType("mezz.jei.common.input.interaction.InputAction")
                .getConstructor(java.util.function.Consumer.class).newInstance(action);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    public static Optional<Object> prepareAction(Object input, java.util.function.Predicate<Object> click) {
        if (!click.test(input)) return Optional.empty();
        return Optional.of(inputAction(release -> click.test(release)));
    }

    public static Object inputArgument(Object[] args) { return args[args.length - 2]; }

    public static Object invokeInputHandler(Object target, Method method, Object[] args) throws Throwable {
        if (target == null) {
            throw new IllegalStateException("JEI returned a null input handler");
        }
        try {
            if (method.getDeclaringClass() == LegacyDispatch.class) {
                String name = switch (method.getName()) {
                    case "handleUserInput" -> "beginInput";
                    case "handleMouseScrolled" -> "scroll";
                    case "unfocus" -> "resetInput";
                    default -> method.getName();
                };
                for (Method actual : loadType("mezz.jei.common.input.IInputTarget").getMethods()) {
                    if (actual.getName().equals(name)) {
                        Object value = actual.invoke(target, args);
                        return name.equals("scroll") ? (Boolean.TRUE.equals(value) ? Optional.of(target) : Optional.empty()) : value;
                    }
                }
            }
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
        final Method method;
        try {
            // JEI often passes a generated lambda here. Resolve the method on
            // the public interface so invocation does not depend on the
            // generated implementation class being accessible.
            method = loadType(MOUSE_OVER_TYPES).getMethod(
                "isMouseOver",
                double.class,
                double.class
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to query JEI mouse-over target", exception);
        }

        return (mouseX, mouseY) -> {
            try {
                return (Boolean) method.invoke(mouseOverable, mouseX, mouseY);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Unable to query JEI mouse-over target", cause);
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
