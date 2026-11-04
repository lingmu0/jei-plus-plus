package com.lingmu0.JeiPlusPlusMod.client;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Bridges JEI 19.64+'s input-target API.
 *
 * <p>JEI 19.64 replaced {@code IUserInputHandler} with {@code IInputTarget}:
 * {@code beginInput} now returns an {@code InputAction} that JEI runs when the
 * mouse button is released instead of the handler which consumed the click,
 * and {@code scroll} returns a plain boolean.  Everything here is reflective so
 * the same jar keeps working on the older user-input API.</p>
 */
public final class ModernInputTargetCompat {
    private static final String INPUT_TARGET_TYPE = "mezz.jei.common.input.IInputTarget";
    private static final String INPUT_GROUP_TYPE = "mezz.jei.common.input.handlers.InputGroup";
    private static final String INPUT_ACTION_TYPE = "mezz.jei.common.input.interaction.InputAction";

    /** Intercepts a press. Returning {@code true} consumes the click. */
    public interface ClickHandler {
        boolean onClick(Object screen, Object userInput) throws Throwable;
    }

    /** Intercepts a wheel turn. Returning {@code null} forwards the scroll. */
    public interface ScrollHandler {
        Boolean onScroll(double mouseX, double mouseY, double scrollX, double scrollY) throws Throwable;
    }

    private static volatile Boolean available;
    private static volatile Class<?> inputTargetType;
    private static volatile Method inputActionRun;

    private ModernInputTargetCompat() {
    }

    /** True when the installed JEI uses the newer input-target API. */
    public static boolean isAvailable() {
        Boolean known = available;
        if (known == null) {
            known = inputTargetType() != null;
            available = known;
        }
        return known;
    }

    /**
     * Wraps an existing input target, letting the callbacks consume input
     * before it would reach the delegate.
     */
    public static Object wrap(Object delegate, ClickHandler click, ScrollHandler scroll) {
        return create(() -> delegate, click, scroll);
    }

    /**
     * Creates an input target which re-resolves its delegate for every event,
     * so callers can switch handlers as the screen or config changes.
     */
    public static Object defer(Supplier<Object> delegate, ClickHandler click, ScrollHandler scroll) {
        return create(delegate, click, scroll);
    }

    /** Combines input targets the same way JEI's own InputGroup does. */
    public static Object group(String label, Object... targets) {
        Class<?> groupType = loadType(INPUT_GROUP_TYPE);
        if (groupType == null) {
            throw new IllegalStateException("Unsupported JEI input group: " + INPUT_GROUP_TYPE);
        }
        for (Constructor<?> constructor : groupType.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 2 || parameters[0] != String.class || !parameters[1].isArray()) {
                continue;
            }
            Class<?> componentType = parameters[1].getComponentType();
            if (!jeiPlusPlus$accepts(componentType, targets)) {
                // `InputGroup` is overloaded for InputGroup.Child[] as well.
                continue;
            }
            Object array = Array.newInstance(componentType, targets.length);
            for (int i = 0; i < targets.length; i++) {
                Array.set(array, i, targets[i]);
            }
            try {
                return constructor.newInstance(label, array);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unable to create JEI input group", exception);
            }
        }
        throw new IllegalStateException("Unsupported JEI input group constructor: " + groupType.getName());
    }

    private static boolean jeiPlusPlus$accepts(Class<?> componentType, Object[] targets) {
        for (Object target : targets) {
            if (target != null && !componentType.isInstance(target)) {
                return false;
            }
        }
        return true;
    }

    private static Object create(Supplier<Object> delegate, ClickHandler click, ScrollHandler scroll) {
        Class<?> type = inputTargetType();
        if (type == null) {
            throw new IllegalStateException("Unsupported JEI input target: " + INPUT_TARGET_TYPE);
        }
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "JEI++ input target";
                case "beginInput":
                    if (click != null && click.onClick(args[0], args[1])) {
                        return Optional.of(inputAction());
                    }
                    return forwardOptional(delegate.get(), method, args);
                case "scroll":
                    if (scroll != null) {
                        Boolean consumed = scroll.onScroll(
                            ((Number) args[0]).doubleValue(),
                            ((Number) args[1]).doubleValue(),
                            ((Number) args[2]).doubleValue(),
                            ((Number) args[3]).doubleValue()
                        );
                        if (consumed != null) {
                            return consumed;
                        }
                    }
                    return forwardScroll(delegate.get(), method, args);
                case "resetInput":
                    forward(delegate.get(), method, args);
                    return null;
                default:
                    return forward(delegate.get(), method, args);
            }
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object forwardOptional(Object target, Method method, Object[] args) throws Throwable {
        if (target == null) {
            return Optional.empty();
        }
        return forward(target, method, args);
    }

    private static Boolean forwardScroll(Object target, Method method, Object[] args) throws Throwable {
        if (target == null) {
            return Boolean.FALSE;
        }
        Object result = forward(target, method, args);
        return result instanceof Boolean value ? value : Boolean.FALSE;
    }

    private static Object forward(Object target, Method method, Object... args) throws Throwable {
        if (target == null) {
            Class<?> returnType = method.getReturnType();
            return returnType == boolean.class ? Boolean.FALSE : null;
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Object inputAction() {
        Method run = inputActionRun;
        if (run == null) {
            Class<?> actionType = loadType(INPUT_ACTION_TYPE);
            if (actionType == null) {
                throw new IllegalStateException("Unsupported JEI input action: " + INPUT_ACTION_TYPE);
            }
            try {
                run = actionType.getMethod("run", Runnable.class);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Unsupported JEI input action: " + actionType.getName(), exception);
            }
            inputActionRun = run;
        }
        try {
            return run.invoke(null, (Runnable) () -> {
            });
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create JEI input action", exception);
        }
    }

    private static Class<?> inputTargetType() {
        Class<?> type = inputTargetType;
        if (type == null) {
            type = loadType(INPUT_TARGET_TYPE);
            inputTargetType = type;
        }
        return type;
    }

    private static Class<?> loadType(String name) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        try {
            return Class.forName(name, false, contextLoader);
        } catch (ClassNotFoundException ignored) {
            try {
                return Class.forName(name, false, ModernInputTargetCompat.class.getClassLoader());
            } catch (ClassNotFoundException ignoredAgain) {
                return null;
            }
        }
    }
}
