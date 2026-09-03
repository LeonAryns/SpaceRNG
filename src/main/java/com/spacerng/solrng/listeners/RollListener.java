package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RollListener implements Listener {

    private final SolRNGPlugin plugin;
    private final NamespacedKey rarityKey;
    private final NamespacedKey rollNameKey;
    private final Map<UUID, BukkitTask> rollingTasks = new HashMap<>();
    // ticks remaining in the current roll, kept up to date so the scoreboard
    // can show a live countdown without duplicating the timing logic.
    private final Map<UUID, Long> remainingTicks = new HashMap<>();
    private final Random random = new Random();

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

    public boolean isRolling(UUID uuid) {
        return rollingTasks.containsKey(uuid);
    }

    /**
     * Seconds left in a player's in-progress roll, or 0 if they aren't
     * currently rolling. Used by the scoreboard's live status line.
     */
    public int getRemainingSeconds(UUID uuid) {
        Long ticks = remainingTicks.get(uuid);
        if (ticks == null || ticks <= 0) return 0;
        return (int) Math.ceil(ticks / 20.0);
    }

    /**
     * Cancels a player's in-progress roll task without granting anything —
     * used when they log out mid-roll so the task doesn't keep running
     * against an offline player.
     */
    public void cancelRoll(UUID uuid) {
        BukkitTask task = rollingTasks.remove(uuid);
        remainingTicks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
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
                plugin.getConfig().getString("roll-item.name", "&d&lRoll"));
        if (meta == null || meta.getDisplayName() == null || !meta.getDisplayName().equals(expectedName)) {
            return; // not our special item, just a normal nether star etc.
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (isRolling(player.getUniqueId())) {
            return; // already mid-roll, ignore extra clicks
        }

        startRoll(player);
    }

    /**
     * Kicks off the roll animation: no cooldown, but the result isn't
     * decided/granted until the timer finishes. A player's roll-speed
     * multiplier (from the Rolling Speed skill tree branch, or future
     * armor upgrades) shortens the wait.
     */
    private void startRoll(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        double baseSeconds = plugin.getConfig().getDouble("roll-item.roll-duration-seconds", 5.0);
        double multiplier = Math.max(0.1, data.getRollSpeedMultiplier());
        long totalTicks = Math.max(1L, Math.round((baseSeconds / multiplier) * 20.0));

        long[] elapsed = {0L};
        int[] lastStep = {-1};
        remainingTicks.put(player.getUniqueId(), totalTicks);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += 2L;

            if (elapsed[0] >= totalTicks) {
                taskHolder[0].cancel();
                rollingTasks.remove(player.getUniqueId());
                remainingTicks.remove(player.getUniqueId());
                finishRoll(player, data);
                return;
            }

            remainingTicks.put(player.getUniqueId(), totalTicks - elapsed[0]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.6f + (float) elapsed[0] / totalTicks);

            // Case-opening-style teaser: every 5% of the roll, flash a
            // random candidate item + its odds in the center of the screen.
            int step = (int) (elapsed[0] * 20 / totalTicks);
            if (step != lastStep[0]) {
                lastStep[0] = step;
                sendRollPreview(player, data);
            }
        }, 0L, 2L);

        rollingTasks.put(player.getUniqueId(), taskHolder[0]);
    }

    /**
     * Flashes a candidate item drawn from the same luck-weighted odds as
     * the real roll — pulling a uniform-random item here made the teaser
     * flash absurd combinations (a 1-in-10M item right before landing on
     * something 1-in-17), which didn't feel believable.
     */
    private void sendRollPreview(Player player, PlayerData data) {
        if (plugin.getRarityManager().getItems().isEmpty()) return;
        RollableItem preview = plugin.getRarityManager().roll(plugin.getPrestigeManager().effectiveLuck(data));

        Component name = LegacyComponentSerializer.legacySection()
                .deserialize(RollFormat.naturalColor(preview.getMaterial()) + "" + ChatColor.BOLD + preview.getDisplayName());
        Component odds = LegacyComponentSerializer.legacySection()
                .deserialize(ChatColor.GRAY + "· " + RollFormat.chance(preview.getOdds()) + " ·");

        Title title = Title.title(name, odds, Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ZERO));
        player.showTitle(title);
    }

    private void finishRoll(Player player, PlayerData data) {
        clearActionBar(player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

        RollableItem result = plugin.getRarityManager().roll(plugin.getPrestigeManager().effectiveLuck(data));
        grantRoll(player, data, result, false);

        // Bonus Roll skill tree branch: a chance to immediately chain into
        // another free roll, no click required.
        if (data.getBonusRollChance() > 0.0 && random.nextDouble() < data.getBonusRollChance()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Bonus Roll! " + ChatColor.RESET
                    + ChatColor.GRAY + "Rolling again...");
            startRoll(player);
        }
    }

    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    private void clearActionBar(Player player) {
        sendActionBar(player, "");
    }

    /**
     * Gives the player their rolled item (or converts it straight to points
     * if they've toggled auto-convert for that rarity), then broadcasts it
     * if it meets the configured rarity threshold. Either way, a preview
     * item is built so chat messages can show a hoverable tooltip of it.
     */
    public void grantRoll(Player player, PlayerData data, RollableItem result, boolean silent) {
        data.addRoll();
        Rarity rarity = result.getRarity();
        ItemStack previewItem = buildTaggedItem(result);

        if (data.isAutoConverting(rarity)) {
            long points = plugin.getConfig().getLong("conversion.points-per-rarity." + rarity.name(), 1L);
            data.addPoints(points);
            data.addConverted(rarity, 1L);
            if (!silent) {
                sendHoverable(player, previewItem, RollFormat.personalRollLine(plugin, result)
                        + ChatColor.YELLOW + " → +" + points + " Credits (auto-converted)");
            }
        } else {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(previewItem.clone());
            if (!overflow.isEmpty()) {
                overflow.values().forEach(leftover ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                player.sendMessage(ChatColor.RED + "Your inventory is full — the item dropped at your feet!");
            }
            if (!silent) {
                sendHoverable(player, previewItem, RollFormat.personalRollLine(plugin, result));
            }
        }

        if (!silent) {
            sendActionBar(player, RollFormat.naturalColor(result.getMaterial()) + "" + ChatColor.BOLD + result.getDisplayName()
                    + ChatColor.GRAY + "  " + RollFormat.compactOdds(result.getOdds()));
        }

        maybeRegisterDiscovery(player, data, result, silent);
        maybeBroadcast(player, result, previewItem);
    }

    /**
     * Sends a chat line where hovering over it shows the real item tooltip
     * (name, lore — Rarity/Chance) via Minecraft's built-in hover-item
     * component. No resource pack needed; this is the same mechanism as
     * shift-clicking an item into chat.
     */
    private void sendHoverable(Player player, ItemStack item, String legacyText) {
        Component message = LegacyComponentSerializer.legacySection().deserialize(legacyText)
                .hoverEvent(item.asHoverEvent());
        player.sendMessage(message);
    }

    /**
     * The first time a player rolls a given item, it's added to their
     * /index and grants a small permanent luck bonus — collecting every
     * item is itself a form of progression.
     */
    private void maybeRegisterDiscovery(Player player, PlayerData data, RollableItem result, boolean silent) {
        if (data.hasDiscovered(result.getDisplayName())) return;

        data.markDiscovered(result.getDisplayName());
        double bonus = plugin.getConfig().getDouble("index.luck-per-item", 0.01);
        data.addBonusLuck(bonus);

        if (!silent) {
            String color = plugin.getRarityManager().colorFor(result.getRarity());
            String notice = ChatColor.GREEN + "" + ChatColor.BOLD + "NEW! " + ChatColor.RESET
                    + color + result.getDisplayName() + ChatColor.GRAY + " added to your index";
            player.sendMessage(notice);
            sendActionBar(player, notice);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        }
    }

    private ItemStack buildTaggedItem(RollableItem result) {
        ItemStack item = new ItemStack(result.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(RollFormat.displayName(plugin, result));
        meta.setLore(RollFormat.lore(plugin, result));
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, result.getRarity().name());
        meta.getPersistentDataContainer().set(rollNameKey, PersistentDataType.STRING, result.getDisplayName());
        item.setItemMeta(meta);
        return item;
    }

    private void maybeBroadcast(Player player, RollableItem result, ItemStack previewItem) {
        String minRarityName = plugin.getConfig().getString("broadcast.min-rarity-to-broadcast", "EPIC");
        Rarity minRarity;
        try {
            minRarity = Rarity.valueOf(minRarityName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            minRarity = Rarity.EPIC;
        }

        if (result.getRarity().ordinal() < minRarity.ordinal()) return;

        Component banner = LegacyComponentSerializer.legacySection()
                .deserialize(RollFormat.broadcastBanner(plugin, player.getName(), result))
                .hoverEvent(previewItem.asHoverEvent());
        Bukkit.broadcast(banner);
    }
}
