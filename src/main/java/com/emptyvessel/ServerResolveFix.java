package com.emptyvessel;

import net.neoforged.fml.common.Mod;

/**
 * Functional code lives in {@link com.emptyvessel.mixin.ServerNameResolverMixin}; this class
 * only exists because the loader needs an entry point. The dist attribute is 1.20.5+ only, so the
 * mod loads on both sides and does nothing on a dedicated server.
 */
@Mod(ServerResolveFix.MODID)
public final class ServerResolveFix {
    public static final String MODID = "serverresolvefix";

    public ServerResolveFix() {
    }
}


