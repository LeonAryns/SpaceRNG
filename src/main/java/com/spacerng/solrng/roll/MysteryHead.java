package com.spacerng.solrng.roll;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The question mark held in front of a roller while a comet falls.
 *
 * A big drop used to show its real item at 78% of the roll, because the
 * reel lands there and holds. That is fine for a two second Common and
 * wrong for a fifteen second Divine: the thing the whole build-up is
 * about was already on the screen while the comet was still in the sky,
 * so the impact revealed nothing. This is what hangs there instead until
 * the odds counter reaches the real number.
 *
 * It is a player head wearing a texture rather than a barrier block or a
 * name tag, because a head is a real three dimensional model that turns
 * well in the showcase and reads as an object rather than as a missing
 * one.
 *
 * The texture is a base64 property in config, which is the value every
 * head site hands out and the one thing about a custom head that never
 * needs parsing. Nothing here talks to Mojang: the value already carries
 * the skin URL, the client fetches it, and the profile only needs a UUID
 * to be a valid carrier for it. That UUID is derived from the texture
 * itself, so the same head is always the same profile and a client caches
 * it once.
 */
public final class MysteryHead {

    /**
     * The black question mark Leon picked. Swap it in config rather than
     * here: any "value" field from a head site works as is.
     */
    private static final String DEFAULT_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0"
                    + "L3RleHR1cmUvNDZiYTYzMzQ0ZjQ5ZGQxYzRmNTQ4OGU5MjZiZjNkOWUyYjI5OTE2YTZjNTBk"
                    + "NjEwYmI0MGE1MjczZGM4YzgyIn19fQ==";

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
        if (head.getItemMeta() instanceof SkullMeta meta) {
            // A fixed UUID per texture. A random one per roll would make
            // every client treat every head as a new profile to resolve.
            PlayerProfile profile = Bukkit.createProfile(
                    UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
            // Never seen in a tooltip, since the showcase is a display
            // entity, but an item with no name at all shows its material.
            meta.setDisplayName(ChatColor.DARK_GRAY + "???");
            head.setItemMeta(meta);
        }
        builtFrom = texture;
        built = head;
        return head.clone();
    }
}
