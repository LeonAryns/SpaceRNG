package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RollListener implements Listener {

    private final SolRNGPlugin plugin;
    private final NamespacedKey rarityKey;
    private final NamespacedKey rollNameKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RollListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.rarityKey = new NamespacedKey(plugin, "solrng_rarity");
        this.rollNameKey = new NamespacedKey(plugin, "solrng_roll_name");
    }

    public NamespacedKey getRarityKey() {
        return rarityKey;
    }

    public NamespacedKey getRollNameKey() {
        return rollNameKey;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = event.getItem();
        if (hand == null) return;

        String configuredMaterial = plugin.getConfig().getString("roll-item.material", "NETHER_STAR");
        Material rollMaterial = Material.matchMaterial(configuredMaterial);
        if (rollMaterial == null || hand.getType() != rollMaterial) return;

        ItemMeta meta = hand.getItemMeta();
        String expectedName = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("roll-item.name", "&d&lRNG Core"));
        if (meta == null || meta.getDisplayName() == null || !meta.getDisplayName().equals(expectedName)) {
            return; // not our special item, just a normal nether star etc.
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (isOnCooldown(player)) return;

        int cooldownSeconds = plugin.getConfig().getInt("roll-item.cooldown-seconds", 5);
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownSeconds * 1000L));

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        RollableItem result = plugin.getRarityManager().roll(data.getBonusLuck());
        grantRoll(player, data, result, false);
    }

    private boolean isOnCooldown(Player player) {
        Long expiry = cooldowns.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(player.getUniqueId());
            return false;
        }
        long remaining = (expiry - System.currentTimeMillis()) / 1000L + 1;
        player.sendMessage(ChatColor.GRAY + "Roll on cooldown: " + remaining + "s");
        return true;
    }

    /**
     * Gives the player their rolled item (or converts it straight to points
     * if they've toggled auto-convert for that rarity), then broadcasts it
     * if it meets the configured rarity threshold.
     */
    public void grantRoll(Player player, PlayerData data, RollableItem result, boolean silent) {
        Rarity rarity = result.getRarity();
        String color = plugin.getRarityManager().colorFor(rarity);

        if (data.isAutoConverting(rarity)) {
            long points = plugin.getConfig().getLong("conversion.points-per-rarity." + rarity.name(), 1L);
            data.addPoints(points);
            if (!silent) {
                player.sendMessage(color + "Rolled " + result.getDisplayName() + ChatColor.GRAY
                        + " (1 in " + result.getOdds() + ") " + ChatColor.YELLOW + "→ +" + points + " points (auto-converted)");
            }
        } else {
            ItemStack item = buildTaggedItem(result);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(leftover ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                player.sendMessage(ChatColor.RED + "Your inventory is full — the item dropped at your feet!");
            }
            if (!silent) {
                player.sendMessage(color + "Rolled " + result.getDisplayName() + ChatColor.GRAY
                        + " (1 in " + result.getOdds() + ")");
            }
        }

        maybeBroadcast(player, result);
    }

    private ItemStack buildTaggedItem(RollableItem result) {
        ItemStack item = new ItemStack(result.getMaterial());
        ItemMeta meta = item.getItemMeta();
        String color = plugin.getRarityManager().colorFor(result.getRarity());
        meta.setDisplayName(color + result.getDisplayName());
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, result.getRarity().name());
        meta.getPersistentDataContainer().set(rollNameKey, PersistentDataType.STRING, result.getDisplayName());
        item.setItemMeta(meta);
        return item;
    }

    private void maybeBroadcast(Player player, RollableItem result) {
        String minRarityName = plugin.getConfig().getString("broadcast.min-rarity-to-broadcast", "EPIC");
        Rarity minRarity;
        try {
            minRarity = Rarity.valueOf(minRarityName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            minRarity = Rarity.EPIC;
        }

        if (result.getRarity().ordinal() < minRarity.ordinal()) return;

        String color = plugin.getRarityManager().colorFor(result.getRarity());
        plugin.getServer().broadcastMessage(color + "" + ChatColor.BOLD + "✦ "
                + player.getName() + " rolled " + result.getDisplayName()
                + " (1 in " + result.getOdds() + ")! ✦");
    }
}
