package com.wayfarersatlas;

import com.wayfarersatlas.platform.ModConfigSpec;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionResult;

/** Client-only part: keys, minimap layer, map screens. */
public class WayfarersAtlasClient implements ClientModInitializer {
    private static final MinimapLayer MINIMAP = new MinimapLayer();

    @Override
    public void onInitializeClient() {
        ModConfigSpec.load(AtlasConfig.SPEC, "wayfarers_atlas-client.toml");
        Net.registerClient(new Net.Client() {
            @Override
            public <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void on(
                    net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> type, java.util.function.BiConsumer<T, Object> h) {
                ClientPlayNetworking.registerGlobalReceiver(type, (p, ctx) -> h.accept(p, ctx));
            }
        });
        Keys.register();
        // the minimap, the "lead me there" arrow and the region title over the game (markers in the world are drawn
        // under the rest of the screen, see GuiMixin)
        HudRenderCallback.EVENT.register((g, dt) -> {
            if (Minecraft.getInstance().options.hideGui) return;
            MINIMAP.render(g, dt);
            Nav.render(g, dt);
            RegionTitle.render(g, dt);
        });
        WorldRenderEvents.END.register(WorldMarkers::onStage);
        // resource packs can add marker icons and recolour the frames, the parchment and the map background
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(WayfarersAtlas.MODID, "icons_and_style");
            }

            @Override
            public void onResourceManagerReload(ResourceManager rm) {
                Icons.reload(rm);
                Style.reload(rm);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> WaystonesClient.init()); // after every mod has started
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide) WaystonesClient.onClick(player, level, hand, hit);
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> ClientEvents.onTick());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> ClientEvents.onLogin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> ClientEvents.onLogout());
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || !TeleportHelper.isHiddenFeedback(message));
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : ClientEvents.shareLine(message));
        // a marker code in a player's chat message: the line with the "add marker" button instead
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
            net.minecraft.network.chat.Component line = ClientEvents.shareLine(message);
            if (line == message) return true;
            Minecraft.getInstance().gui.getChat().addMessage(line);
            return false;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> ClientEvents.onClientCommands(dispatcher));
        // the Atlas slot next to the inventory
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof InventoryScreen) AtlasSlotUi.attach(screen);
        });
    }
}
