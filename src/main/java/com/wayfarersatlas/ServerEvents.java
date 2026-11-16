package com.wayfarersatlas;

import net.minecraft.server.MinecraftServer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Server side: keeps the Atlas maps, gives new Atlases their map id, sends maps to players. */
public final class ServerEvents {
    private static int tick = 0;

    public static void onStarting(MinecraftServer server) {
        MapStore.start(server);
        WaystonesServer.start(server);
        WorldMarks.start(server);
        TeamStore.start(server);
    }

    public static void onStopping(MinecraftServer server) {
        MapStore.stop();
        WaystonesServer.stop();
        WorldMarks.stop();
        TeamStore.stop();
        Reveal.clear();
    }

    public static void onTick(MinecraftServer server) {
        tick++;
        Net.pump(server);
        Reveal.tick(server);
        TeamStore.tick(server);
        AtlasSlot.tick(server); // the slot shown by each player's game is what the server keeps
        if (tick % 20 == 10) WorldMarks.tick(server); // secret markers and regions that someone found
        if (tick % 20 == 0) {
            // every Atlas gets its own map id the first time someone carries it
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                for (ItemStack s : AtlasCheck.all(p)) {
                    if (s.get(WayfarersAtlas.MAP_ID.get()) == null) {
                        s.set(WayfarersAtlas.MAP_ID.get(), UUID.randomUUID());
                        if (s == AtlasSlot.get(p)) AtlasSlot.sync(p); // the slot is not a normal inventory slot
                    }
                }
            }
        }
    }

    /** The world is saved (autosave, /save-all, quitting): save the maps with it (see MinecraftServerMixin). */
    public static void onSave() {
        MapStore.saveAll(true);
    }

    public static void onLogin(ServerPlayer sp) {
        Net.sendRules(sp);
        AtlasSlot.sync(sp);
        WorldMarks.send(sp);
        starterAtlas(sp);
    }

    /** Server rule "starterAtlas": an Atlas for everyone who joins this world for the first time. */
    private static void starterAtlas(ServerPlayer sp) {
        if (!ServerConfig.STARTER_ATLAS.get()) return;
        // kept on the player (survives death)
        var kept = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget) sp;
        if (Boolean.TRUE.equals(kept.getAttached(WayfarersAtlas.GOT_STARTER))) return;
        kept.setAttached(WayfarersAtlas.GOT_STARTER, true);
        if (AtlasCheck.has(sp)) return; // already carries one
        ItemStack atlas = new ItemStack(WayfarersAtlas.ATLAS.get());
        if (!sp.getInventory().add(atlas)) sp.drop(atlas, false);
    }

    /** After death the Atlas slot was copied to the new player: show it again. */
    public static void onRespawn(ServerPlayer sp) {
        AtlasSlot.syncAfterRespawn(sp);
    }

    public static void onLogout(ServerPlayer sp) {
        Net.forget(sp.getUUID());
        AtlasSlot.forget(sp.getUUID());
        TeamStore.logout(sp);
    }

    private ServerEvents() {}
}
