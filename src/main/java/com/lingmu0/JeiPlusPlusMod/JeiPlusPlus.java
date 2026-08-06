package com.lingmu0.JeiPlusPlusMod;

import net.neoforged.fml.common.Mod;

/** Common entry point; JEI integration is optional and client-only. */
@Mod(JeiPlusPlus.MODID)
public final class JeiPlusPlus {
    public static final String MODID = "jei_plus_plus";

    public JeiPlusPlus() {
        // JEI discovers the @JeiPlugin class when the optional client dependency is present.
    }
}
