package com.wayfarersatlas.platform;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Sending the mod's messages (the same calls as on NeoForge). */
public final class PacketDistributor {
    /** To one player (only if their game has the mod). */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }

    /** To the server (client side only). */
    public static void sendToServer(CustomPacketPayload payload) {
        ClientSend.send(payload);
    }

    private PacketDistributor() {}
}
