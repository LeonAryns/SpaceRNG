package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Game textures drawn inside text (V163), no resource pack needed.
 *
 * Since Minecraft 1.21.9 a text component can show any sprite from the
 * client's own atlases: an emerald, a gold nugget, the Luck effect's
 * clover. The client already has every one of them, so nothing is sent
 * but the name. A client older than 1.21.9 simply shows nothing there.
 *
 * Legacy strings mark an icon as {@code ICON + "money" + ICON}; render()
 * turns such a string into a component with the sprite in place. Which
 * sprite each name uses is config (scoreboard.icons), written as
 * "atlas|sprite", so a wrong guess is fixed without a new jar.
 */
public final class Icons {

    public static final String ICON = "";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private Icons() {
    }

    /** The marker for one icon, to drop into a legacy string. */
    public static String of(String name) {
        return ICON + name + ICON;
    }

    /** A legacy string with icon markers, as a component with the sprites drawn. */
    public static Component render(SolRNGPlugin plugin, String legacy) {
        if (legacy.indexOf(ICON) < 0) return LEGACY.deserialize(legacy);
        TextComponent.Builder out = Component.text();
        String[] parts = legacy.split(ICON, -1);
        // Even pieces are text, odd pieces are icon names.
        String carried = "";
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 0) {
                if (!parts[i].isEmpty()) out.append(LEGACY.deserialize(carried + parts[i]));
            } else {
                Component icon = sprite(plugin, parts[i]);
                if (icon != null) out.append(icon);
                carried = lastColour(parts[i - 1]);
            }
        }
        return out.build();
    }

    /** One named icon, or null when it is switched off or unknown. */
    public static Component sprite(SolRNGPlugin plugin, String name) {
        if (!plugin.getConfig().getBoolean("scoreboard.icons-enabled", true)) return null;
        String spec = plugin.getConfig().getString("scoreboard.icons." + name, "");
        if (spec == null || spec.isBlank()) return null;
        try {
            String[] split = spec.split("\\|", 2);
            ObjectContents contents = split.length == 2
                    ? ObjectContents.sprite(Key.key(split[0].trim()), Key.key(split[1].trim()))
                    : ObjectContents.sprite(Key.key(split[0].trim()));
            return Component.object(contents);
        } catch (Exception ex) {
            return null;
        }
    }

    /** The colour codes a following piece of text should carry on with. */
    private static String lastColour(String legacy) {
        int at = legacy.lastIndexOf('§');
        if (at < 0 || at + 1 >= legacy.length()) return "";
        // A hex colour is 14 characters (section x plus six pairs); take it whole.
        int hex = legacy.lastIndexOf("§x");
        if (hex >= 0 && hex + 14 <= legacy.length() && hex + 12 >= at) return legacy.substring(hex, hex + 14);
        return legacy.substring(at, at + 2);
    }
}
