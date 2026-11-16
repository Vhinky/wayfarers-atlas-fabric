package com.wayfarersatlas.mixin;

import com.wayfarersatlas.WorldMarkers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Markers in the world are drawn first, under the hotbar and everything else on the screen. */
@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void wayfarersAtlas$worldMarkers(GuiGraphics g, DeltaTracker dt, CallbackInfo ci) {
        if (!Minecraft.getInstance().options.hideGui) WorldMarkers.render(g, dt);
    }
}
