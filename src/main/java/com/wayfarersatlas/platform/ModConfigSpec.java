package com.wayfarersatlas.platform;

import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A small settings file in the style of NeoForge's ModConfigSpec (same calls, so the settings classes are the same
 * on both loaders): sections, comments, true/false, numbers in a range, choices and lists of words, kept in a TOML
 * file in the config folder. The settings screen (ConfigScreen) is built from it.
 */
public final class ModConfigSpec {
    /** A section of the file (its title in the settings screen comes from the translation). */
    public record Section(String name, String translation, List<ConfigValue<?>> values) {}

    final List<Section> sections = new ArrayList<>();
    private Path file;

    public List<Section> sections() {
        return sections;
    }

    /** Reads the file (config/&lt;name&gt;) and writes it back with every setting in it. */
    public static void load(ModConfigSpec spec, String name) {
        spec.file = Platform.configDir().resolve(name);
        Map<String, String> raw = new LinkedHashMap<>();
        if (Files.exists(spec.file)) {
            try {
                String section = "";
                for (String line : Files.readAllLines(spec.file, StandardCharsets.UTF_8)) {
                    String l = line.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    if (l.startsWith("[") && l.endsWith("]") && !l.contains("=")) {
                        section = l.substring(1, l.length() - 1).trim();
                        continue;
                    }
                    int eq = l.indexOf('=');
                    if (eq < 0) continue;
                    raw.put((section.isEmpty() ? "" : section + ".") + l.substring(0, eq).trim(), l.substring(eq + 1).trim());
                }
            } catch (IOException e) {
                LogUtils.getLogger().warn("Wayfarer's Atlas: could not read {}", spec.file, e);
            }
        }
        for (Section s : spec.sections) {
            for (ConfigValue<?> v : s.values) {
                String text = raw.get(v.path);
                if (text != null) v.read(text);
            }
        }
        spec.save();
    }

    public void save() {
        if (file == null) return;
        StringBuilder sb = new StringBuilder();
        for (Section s : sections) {
            if (!s.name.isEmpty()) sb.append('[').append(s.name).append("]\n");
            for (ConfigValue<?> v : s.values) {
                if (v.comment != null) for (String c : v.comment.split("\n")) sb.append("\t# ").append(c).append('\n');
                if (v instanceof IntValue iv) sb.append("\t# Range: ").append(iv.min).append(" ~ ").append(iv.max).append('\n');
                if (v instanceof EnumValue<?> ev) {
                    sb.append("\t# Allowed values:");
                    for (Object o : ev.type.getEnumConstants()) sb.append(' ').append(((Enum<?>) o).name());
                    sb.append('\n');
                }
                sb.append('\t').append(v.name).append(" = ").append(v.write()).append('\n');
            }
            sb.append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LogUtils.getLogger().warn("Wayfarer's Atlas: could not save {}", file, e);
        }
    }

    // ------------------------------------------------------------------ values

    public abstract static class ConfigValue<T> {
        final ModConfigSpec spec;
        final String path, name;
        public final String comment, translation;
        final T def;
        T value;

        ConfigValue(ModConfigSpec spec, String path, String name, String comment, String translation, T def) {
            this.spec = spec;
            this.path = path;
            this.name = name;
            this.comment = comment;
            this.translation = translation;
            this.def = def;
            this.value = def;
        }

        public T get() {
            return value;
        }

        public T getDefault() {
            return def;
        }

        public void set(T v) {
            value = v;
        }

        public void save() {
            spec.save();
        }

        public String name() {
            return name;
        }

        abstract void read(String text);

        abstract String write();
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(ModConfigSpec s, String p, String n, String c, String t, boolean d) {
            super(s, p, n, c, t, d);
        }

        @Override
        void read(String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) value = Boolean.parseBoolean(text);
        }

        @Override
        String write() {
            return String.valueOf(value);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        public final int min, max;

        IntValue(ModConfigSpec s, String p, String n, String c, String t, int d, int min, int max) {
            super(s, p, n, c, t, d);
            this.min = min;
            this.max = max;
        }

        @Override
        public void set(Integer v) {
            value = Math.max(min, Math.min(max, v));
        }

        @Override
        void read(String text) {
            try {
                set(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        @Override
        String write() {
            return String.valueOf(value);
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        public final Class<E> type;

        EnumValue(ModConfigSpec s, String p, String n, String c, String t, E d) {
            super(s, p, n, c, t, d);
            this.type = d.getDeclaringClass();
        }

        @Override
        void read(String text) {
            String v = unquote(text);
            for (E e : type.getEnumConstants()) if (e.name().equalsIgnoreCase(v)) value = e;
        }

        @Override
        String write() {
            return "\"" + value.name() + "\"";
        }
    }

    public static final class ListValue extends ConfigValue<List<? extends String>> {
        ListValue(ModConfigSpec s, String p, String n, String c, String t, List<? extends String> d) {
            super(s, p, n, c, t, d);
        }

        @Override
        void read(String text) {
            String t = text.trim();
            if (!t.startsWith("[") || !t.endsWith("]")) return;
            List<String> out = new ArrayList<>();
            for (String part : t.substring(1, t.length() - 1).split(",")) {
                String v = unquote(part.trim());
                if (!v.isEmpty()) out.add(v);
            }
            value = out;
        }

        @Override
        String write() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < value.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append('"').append(value.get(i).replace("\"", "")).append('"');
            }
            return sb.append(']').toString();
        }
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        return t;
    }

    // ------------------------------------------------------------------ building

    public static final class Builder {
        private final ModConfigSpec spec = new ModConfigSpec();
        private final List<String> path = new ArrayList<>();
        private String comment, translation;
        private Section current;

        public Builder() {
            current = new Section("", null, new ArrayList<>());
            spec.sections.add(current);
        }

        public Builder comment(String c) {
            comment = c;
            return this;
        }

        public Builder translation(String t) {
            translation = t;
            return this;
        }

        public Builder push(String name) {
            path.add(name);
            current = new Section(String.join(".", path), translation, new ArrayList<>());
            spec.sections.add(current);
            comment = null;
            translation = null;
            return this;
        }

        public Builder pop() {
            if (!path.isEmpty()) path.remove(path.size() - 1);
            current = new Section(String.join(".", path), null, new ArrayList<>());
            spec.sections.add(current);
            return this;
        }

        private String full(String name) {
            return path.isEmpty() ? name : String.join(".", path) + "." + name;
        }

        private <V extends ConfigValue<?>> V add(V v) {
            current.values().add(v);
            comment = null;
            translation = null;
            return v;
        }

        public BooleanValue define(String name, boolean def) {
            return add(new BooleanValue(spec, full(name), name, comment, translation, def));
        }

        public IntValue defineInRange(String name, int def, int min, int max) {
            return add(new IntValue(spec, full(name), name, comment, translation, def, min, max));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(String name, E def) {
            return add(new EnumValue<>(spec, full(name), name, comment, translation, def));
        }

        public ConfigValue<List<? extends String>> defineListAllowEmpty(String name, List<? extends String> def,
                                                                         Supplier<?> newElement, Predicate<Object> check) {
            return add(new ListValue(spec, full(name), name, comment, translation, def));
        }

        public ModConfigSpec build() {
            spec.sections.removeIf(s -> s.values().isEmpty());
            return spec;
        }
    }
}
