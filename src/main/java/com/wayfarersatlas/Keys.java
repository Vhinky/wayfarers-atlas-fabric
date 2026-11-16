package com.wayfarersatlas;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    private static final String CAT = "key.categories.wayfarers_atlas";

    public static final KeyMapping OPEN_MAP = new KeyMapping("key.wayfarers_atlas.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CAT);
    public static final KeyMapping ADD_MARKER = new KeyMapping("key.wayfarers_atlas.marker",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CAT);
    public static final KeyMapping ZOOM = new KeyMapping("key.wayfarers_atlas.zoom",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, CAT);

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_MAP);
        KeyBindingHelper.registerKeyBinding(ADD_MARKER);
        KeyBindingHelper.registerKeyBinding(ZOOM);
    }

    /** True while the key is physically held, even inside a screen. Used so a held key cannot re-trigger or type. */
    public static boolean physicallyDown(KeyMapping km) {
        InputConstants.Key k = KeyBindingHelper.getBoundKeyOf(km);
        if (k.getType() != InputConstants.Type.KEYSYM) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), k.getValue());
    }

    private Keys() {}
}
