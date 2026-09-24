package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The question mark held in front of a roller while the odds climb.
 *
 * A big drop used to show its real item at 78% of the roll, because the
 * reel lands there and holds. That is fine for a two second Common and
 * wrong for a twenty second Divine: the thing the whole build-up is about
 * was already on the screen while the comet was still in the sky, so the
 * impact revealed nothing. This is what hangs there instead until the
 * counter reaches the real number.
 *
 * <h2>How the texture gets on it, and why it changed in V197</h2>
 *
 * V196 set the head through Paper's own profile with the raw base64
 * property on it and no name, and in game it showed nothing at all.
 * This goes through the route Bukkit actually specifies instead:
 * {@code createPlayerProfile}, {@link PlayerTextures#setSkin(URL)}, and
 * crucially {@link PlayerProfile#setTextures} afterwards, because
 * {@code getTextures()} is allowed to hand back a copy and an
 * implementation that does leaves a skin set on nothing. The profile also
 * carries a name, since a nameless one is the other thing that gets
 * dropped on the way to the client.
 *
 * Config still takes the plain base64 "value" every head site hands out,
 * because that is what a person can actually paste. It is decoded here
 * and the URL inside it is what the API is given. A bare URL works too.
 */
public final class MysteryHead {

    /**
     * The black question mark Leon picked. Swap it in config rather than
     * here: any "value" field from a head site works as is, and so does
     * the plain texture URL.
     */
    private static final String DEFAULT_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0"
                    + "L3RleHR1cmUvNDZiYTYzMzQ0ZjQ5ZGQxYzRmNTQ4OGU5MjZiZjNkOWUyYjI5OTE2YTZjNTBk"
                    + "NjEwYmI0MGE1MjczZGM4YzgyIn19fQ==";

    /** The only host a skin may come from, which Bukkit itself enforces. */
    private static final String HOST = "https://textures.minecraft.net/texture/";

    private static final Pattern URL_IN_JSON = Pattern.compile("texture/([0-9a-fA-F]+)");

    private static String builtFrom;
    private static ItemStack built;

    private MysteryHead() {
    }

    /**
     * The head, built once and rebuilt only when the texture in config
     * changes. A copy comes back every time, because the caller hands it
     * to a display that would otherwise share one stack with every roll on
     * the server.
     */
    public static ItemStack item(SolRNGPlugin plugin) {
        String texture = plugin.getConfig().getString("roll-item.comet.mystery-head", DEFAULT_TEXTURE);
        if (texture == null || texture.isBlank()) texture = DEFAULT_TEXTURE;
        if (built != null && texture.equals(builtFrom)) return built.clone();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        URL skin = skinUrl(texture);
        if (skin != null && head.getItemMeta() instanceof SkullMeta meta) {
            // A fixed UUID per texture, so every client resolves one
            // profile once instead of a new one on every roll.
            PlayerProfile profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), "Mystery");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(skin);
            // Not optional. getTextures may return a copy, and without
            // this the skin is set on something nobody ever reads.
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            // Never seen in a tooltip, since the showcase is a display
            // entity, but an item with no name at all shows its material.
            meta.setDisplayName(ChatColor.DARK_GRAY + "???");
            head.setItemMeta(meta);
        } else {
            plugin.getLogger().warning("The mystery head texture in roll-item.comet.mystery-head "
                    + "is not a texture value or a textures.minecraft.net URL. Using a plain head.");
        }
        builtFrom = texture;
        built = head;
        return head.clone();
    }

    /**
     * The skin URL out of whatever config holds: a base64 value, a full
     * URL, or the bare hash. Everything ends up as the https form of the
     * one host Bukkit accepts, because a value copied off a head site
     * usually carries the old http one and the two are the same texture.
     */
    static URL skinUrl(String raw) {
        String text = raw.trim();
        if (!text.startsWith("http") && !text.matches("[0-9a-fA-F]{32,}")) {
            try {
                text = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        Matcher found = URL_IN_JSON.matcher(text);
        String hash = found.find() ? found.group(1) : (text.matches("[0-9a-fA-F]{32,}") ? text : null);
        if (hash == null) return null;
        try {
            return URI.create(HOST + hash).toURL();
        } catch (Exception ex) {
            return null;
        }
    }
}
