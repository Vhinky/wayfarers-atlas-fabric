package com.wayfarersatlas.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/** What the mod needs from the loader (Fabric). */
public final class Platform {
    private static MinecraftServer server;

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /** The running server (also the one inside singleplayer), or null. */
    public static MinecraftServer server() {
        return server;
    }

    public static void setServer(MinecraftServer s) {
        server = s;
    }

    private Platform() {}
}
