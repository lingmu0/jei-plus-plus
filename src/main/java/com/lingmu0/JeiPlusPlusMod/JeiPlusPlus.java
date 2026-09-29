package com.lingmu0.JeiPlusPlusMod;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModLoadingContext;

/** Common entry point; the AE2 pattern channel is optional for client-only use. */
@Mod(JeiPlusPlus.MODID)
public final class JeiPlusPlus {
    public static final String MODID = "jei_plus_plus";

    public JeiPlusPlus() {
        JeiPlusPlusConfig.register(ModLoadingContext.get().getActiveContainer());
        ModLoadingContext.get().getActiveContainer().getEventBus().addListener(Ae2PatternNetwork::register);
    }
}
