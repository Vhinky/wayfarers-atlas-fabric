package com.wayfarersatlas.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-only half of PacketDistributor (kept apart so a server never loads client classes). */
final class ClientSend {
    static void send(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
    }

    private ClientSend() {}
}
