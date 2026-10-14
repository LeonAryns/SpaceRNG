package com.spacerng.solrng.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom skulls from a texture Value, the long base64 code under "For
 * Developers" on minecraft-heads.com (V384 pets, V385 crates).
 */
public final class Heads {

    private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();

    private Heads() {
    }

    /** A player head wearing this texture, or null for a blank value. */
    public static ItemStack textured(String value) {
        if (value == null || value.isBlank()) return null;
        return CACHE.computeIfAbsent(value.trim(), Heads::build).clone();
    }

    private static ItemStack build(String value) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            // The same texture always gets the same id, so the client
            // caches one skin per head however many things share it.
            UUID uuid = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfile(uuid, null);
            profile.setProperty(new ProfileProperty("textures", value));
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
        }
        return skull;
    }
}
