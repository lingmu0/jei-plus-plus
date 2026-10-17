package com.lingmu0.JeiPlusPlusMod.client;

import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.InputAction;

import java.util.Optional;
import java.util.function.Predicate;

/** Adapts JEI's simulate/execute callbacks to its press/release interactions. */
public final class JeiInputActions {
    private JeiInputActions() {}

    public static Optional<InputAction> prepare(UserInput input, Predicate<UserInput> handler) {
        if (!handler.test(input)) return Optional.empty();
        // Keyboard handlers already ran; mouse presses only tested availability.
        return Optional.of(input.isKeyboardInput()
            ? InputAction.run(() -> {})
            : new InputAction(release -> handler.test(release)));
    }
}
