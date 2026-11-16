package com.wayfarersatlas.mixin;

import com.wayfarersatlas.ServerEvents;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The world is saved (autosave, /save-all, quitting): the Atlas maps are saved with it. */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "saveEverything", at = @At("HEAD"))
    private void wayfarersAtlas$save(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        ServerEvents.onSave();
    }
}
