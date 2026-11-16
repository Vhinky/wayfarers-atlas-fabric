package com.wayfarersatlas;

import com.mojang.serialization.Codec;
import com.wayfarersatlas.platform.ModConfigSpec;
import com.wayfarersatlas.platform.Platform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Supplier;

/** Common part of the mod: the Atlas, its upgrades and their crafting parts (runs on client and server). */
public class WayfarersAtlas implements ModInitializer {
    public static final String MODID = "wayfarers_atlas";

    /** A registered thing, read with get() (like the holders on NeoForge). */
    public static final class Reg<T> implements Supplier<T> {
        private final T value;

        Reg(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static <T> Reg<T> reg(Registry<? super T> registry, String path, T value) {
        Registry.register(registry, id(path), value);
        return new Reg<>(value);
    }

    // --- data stored on the Atlas: installed upgrades as bit flags (see Upgrades) ---
    public static final Reg<DataComponentType<Integer>> UPGRADES = reg(BuiltInRegistries.DATA_COMPONENT_TYPE, "upgrades",
            DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** Which map lives in this Atlas. Given by the server; a new Atlas starts with an empty map. */
    public static final Reg<DataComponentType<java.util.UUID>> MAP_ID = reg(BuiltInRegistries.DATA_COMPONENT_TYPE, "map_id",
            DataComponentType.<java.util.UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

    // --- data kept on the player: the Atlas slot (stays with you when you die) ---
    public static final AttachmentType<ItemStack> ATLAS_SLOT = AttachmentRegistry.<ItemStack>builder()
            .persistent(ItemStack.OPTIONAL_CODEC)
            .copyOnDeath()
            .initializer(() -> ItemStack.EMPTY)
            .buildAndRegister(id("atlas_slot"));
    /** The player got the starter Atlas (server rule "starterAtlas"); kept through death. */
    public static final AttachmentType<Boolean> GOT_STARTER = AttachmentRegistry.<Boolean>builder()
            .persistent(Codec.BOOL)
            .copyOnDeath()
            .buildAndRegister(id("starter_atlas"));

    // --- blocks ---
    /** Two blocks wide table: share explored chunks (and markers) between Atlases. */
    public static final Reg<MapTableBlock> MAP_TABLE = reg(BuiltInRegistries.BLOCK, "map_table", new MapTableBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)
                    .ignitedByLava().noOcclusion().pushReaction(PushReaction.BLOCK)));

    // --- items ---
    public static final Reg<Item> ATLAS = reg(BuiltInRegistries.ITEM, "atlas", new AtlasItem(new Item.Properties().stacksTo(1)));
    /** Crafting part: a lens of amethyst in glass. */
    public static final Reg<Item> TRACKER_LENS = reg(BuiltInRegistries.ITEM, "tracker_lens",
            new TooltipItem(new Item.Properties(), "item.wayfarers_atlas.tracker_lens.tooltip"));
    /** Upgrade: shows creatures around you on the maps. */
    public static final Reg<Item> RADAR_UPGRADE = reg(BuiltInRegistries.ITEM, "radar_upgrade",
            new UpgradeItem(new Item.Properties().stacksTo(16), Upgrades.RADAR));
    /** Upgrade: marks where you died on the maps. */
    public static final Reg<Item> DEATH_PAGE = reg(BuiltInRegistries.ITEM, "death_page",
            new UpgradeItem(new Item.Properties().stacksTo(16), Upgrades.DEATH));
    /** Upgrade: shows items lying on the ground on the maps. */
    public static final Reg<Item> ITEM_PAGE = reg(BuiltInRegistries.ITEM, "item_page",
            new UpgradeItem(new Item.Properties().stacksTo(16), Upgrades.ITEMS));
    public static final Reg<BlockItem> MAP_TABLE_ITEM = reg(BuiltInRegistries.ITEM, "map_table",
            new TooltipBlockItem(MAP_TABLE.get(), new Item.Properties(), "block.wayfarers_atlas.map_table.tooltip"));

    // --- own tab in the creative inventory ---
    public static final Reg<CreativeModeTab> TAB = reg(BuiltInRegistries.CREATIVE_MODE_TAB, "main", FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.wayfarers_atlas"))
            .icon(() -> new ItemStack(ATLAS.get()))
            .displayItems((params, out) -> {
                out.accept(ATLAS.get());
                out.accept(RADAR_UPGRADE.get());
                out.accept(DEATH_PAGE.get());
                out.accept(ITEM_PAGE.get());
                out.accept(TRACKER_LENS.get());
                out.accept(MAP_TABLE_ITEM.get());
            })
            .build());

    private static boolean compatReady = false;

    @Override
    public void onInitialize() {
        ModConfigSpec.load(ServerConfig.SPEC, "wayfarers_atlas-server.toml");
        Net.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Platform.setServer(server);
            if (!compatReady) { // Waystones and FTB Teams are ready by now (whatever order the mods started in)
                compatReady = true;
                WaystonesServer.init();
                TeamsCompat.init();
            }
            ServerEvents.onStarting(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerEvents::onStopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Platform.setServer(null));
        ServerTickEvents.END_SERVER_TICK.register(ServerEvents::onTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerEvents.onLogin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerEvents.onLogout(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ServerEvents.onRespawn(newPlayer));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, env) -> AtlasCommands.register(dispatcher, context));
    }
}
