package com.wayfarersatlas.platform;

import com.wayfarersatlas.AtlasConfig;
import com.wayfarersatlas.ParchButton;
import com.wayfarersatlas.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings of the mod (the gear on the map, or Mod Menu): every setting of AtlasConfig with its section titles,
 * a switch, a slider or a choice next to it, its explanation when you point at it. Changes apply and are saved at once.
 */
public class ConfigScreen extends Screen {
    private static final int ROW = 22;

    private final Screen parent;
    private final List<Object> rows = new ArrayList<>(); // String (section title) or a setting
    private int px, py, pw, ph, listY, listH;
    private double scroll = 0;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("wayfarers_atlas.configuration.title"));
        this.parent = parent;
        for (ModConfigSpec.Section s : AtlasConfig.SPEC.sections()) {
            if (s.translation() != null) rows.add(I18n.get(s.translation()));
            rows.addAll(s.values());
        }
    }

    @Override
    protected void init() {
        pw = Math.min(380, width - 20);
        ph = height - 20;
        px = (width - pw) / 2;
        py = 10;
        listY = py + 28;
        listH = ph - 28 - 32;
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int cw = 110, cx = px + pw - 14 - cw;
        for (int i = 0; i < rows.size(); i++) {
            int y = listY + i * ROW - (int) scroll;
            if (y < listY || y + ROW > listY + listH) continue; // only the rows that are seen
            Object o = rows.get(i);
            if (o instanceof ModConfigSpec.BooleanValue b) {
                addRenderableWidget(new ParchButton(cx, y + 1, cw, 18, onOff(b.get()), ParchButton.Kind.TEXT, b.get(), btn -> {
                    b.set(!b.get());
                    b.save();
                    rebuildWidgets();
                }));
            } else if (o instanceof ModConfigSpec.EnumValue<?> e) {
                addRenderableWidget(new ParchButton(cx, y + 1, cw, 18, Component.literal(e.get().name()), ParchButton.Kind.TEXT, false, btn -> {
                    cycle(e);
                    rebuildWidgets();
                }));
            } else if (o instanceof ModConfigSpec.IntValue iv) {
                addRenderableWidget(new Slider(cx, y + 1, cw, 18, iv));
            }
        }
        addRenderableWidget(new ParchButton(px + pw - 14 - 90, py + ph - 26, 90, 20, CommonComponents.GUI_DONE,
                ParchButton.Kind.TEXT, false, b -> onClose()));
    }

    private static Component onOff(boolean on) {
        return on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    private static <E extends Enum<E>> void cycle(ModConfigSpec.EnumValue<E> e) {
        E[] all = e.type.getEnumConstants();
        e.set(all[(e.get().ordinal() + 1) % all.length]);
        e.save();
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW - listH);
    }

    /** A number in its range. */
    private static final class Slider extends AbstractSliderButton {
        private final ModConfigSpec.IntValue v;

        Slider(int x, int y, int w, int h, ModConfigSpec.IntValue v) {
            super(x, y, w, h, Component.literal(String.valueOf(v.get())), (v.get() - v.min) / (double) Math.max(1, v.max - v.min));
            this.v = v;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.valueOf(v.get())));
        }

        @Override
        protected void applyValue() {
            v.set((int) Math.round(v.min + value * (v.max - v.min)));
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            v.save();
        }
    }

    private boolean bgDone = false; // the background is drawn once per frame, before the panel

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (bgDone) return;
        if (parent != null && minecraft.level != null) parent.render(g, -1000, -1000, pt);
        else super.renderBackground(g, mx, my, pt);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        bgDone = false;
        renderBackground(g, mx, my, pt);
        bgDone = true;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 700);
        g.fill(0, 0, width, height, 0x66000000);
        Ui.panel(g, px, py, pw, ph);
        Ui.fitCentered(g, font, title.getString(), px + pw / 2f, py + 11, pw - 20, Ui.INK, false);
        String tip = null;
        g.enableScissor(px + 6, listY, px + pw - 6, listY + listH);
        for (int i = 0; i < rows.size(); i++) {
            int y = listY + i * ROW - (int) scroll;
            if (y + ROW < listY || y > listY + listH) continue;
            Object o = rows.get(i);
            if (o instanceof String title) { // a section
                g.drawString(font, title, px + 14, y + 8, 0xFF6B4A26, false);
                int lx = px + 14 + font.width(title) + 6;
                g.fill(lx, y + 12, px + pw - 14, y + 13, 0x553A2410);
                continue;
            }
            ModConfigSpec.ConfigValue<?> v = (ModConfigSpec.ConfigValue<?>) o;
            String key = v.translation;
            String label = key != null && I18n.exists(key) ? I18n.get(key) : v.name();
            int room = pw - 28 - 110 - 8;
            if (font.width(label) > room) label = font.plainSubstrByWidth(label, room - font.width("…")) + "…";
            g.drawString(font, label, px + 18, y + 7, Ui.INK, false);
            if (my >= y && my < y + ROW && mx >= px + 14 && mx < px + pw - 14 - 110 && my >= listY && my < listY + listH) {
                String tk = key == null ? null : key + ".tooltip";
                tip = tk != null && I18n.exists(tk) ? I18n.get(tk) : v.comment;
            }
        }
        g.disableScissor();
        super.render(g, mx, my, pt); // the switches and sliders (the background is not drawn again)
        if (tip != null) g.renderTooltip(font, font.split(Component.literal(tip), 220), mx, my);
        g.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double before = scroll;
        scroll = Mth.clamp(scroll - sy * ROW * 2, 0, maxScroll());
        if (scroll != before) rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return parent == null || minecraft.level == null;
    }
}
