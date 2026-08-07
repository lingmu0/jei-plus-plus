package com.lingmu0.JeiPlusPlusMod;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModLoadingContext;

/** Common entry point; JEI integration is optional and client-only. */
@Mod(JeiPlusPlus.MODID)
public final class JeiPlusPlus {
    public static final String MODID = "jei_plus_plus";

    public JeiPlusPlus() {
        JeiPlusPlusConfig.register(ModLoadingContext.get().getActiveContainer());
    }
}
