package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.roll.RollAura;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.inventory.EquipmentSlot;
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
    // Guards against a single physical right-click firing PlayerInteractEvent
    // twice — Bukkit/Paper fires a second RIGHT_CLICK_AIR event right after
    // RIGHT_CLICK_BLOCK for the same hand when the clicked block doesn't
    // consume the interaction (most blocks). EquipmentSlot filtering alone
    // doesn't catch this since both events are for the main hand.
    private final Map<UUID, Long> lastInteractMillis = new HashMap<>();
    // The Epic+ build-up currently running for a player, so it can be
    // revealed when the roll lands and torn down if the roll is abandoned.
    private final Map<UUID, RollAura> activeAuras = new HashMap<>();
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
        lastInteractMillis.remove(uuid);
        RollAura aura = activeAuras.remove(uuid);
        if (aura != null) {
            aura.cancel();
        }
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!rightClick && !leftClick) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // ignore the duplicate off-hand firing

        // Identified by its PersistentData tag, not its name — every
        // Starforge tier is a different display name but the same item.
        if (!plugin.getStarforgeManager().isStarforge(event.getItem())) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        // Same physical click can still fire twice for the main hand alone
        // (RIGHT_CLICK_BLOCK immediately followed by RIGHT_CLICK_AIR) — if
        // we just handled a click from this player within the last tick,
        // this is that duplicate, not a real second click.
        long now = System.currentTimeMillis();
        Long last = lastInteractMillis.get(player.getUniqueId());
        if (last != null && now - last < 100L) return;
        lastInteractMillis.put(player.getUniqueId(), now);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (leftClick) {
            toggleAutoRoll(player, data);
            return;
        }

        if (isRolling(player.getUniqueId())) {
            return; // already mid-roll, ignore extra clicks
        }

        startRoll(player);
    }

    /**
     * The Starforge can't be dropped — it's the one item a player can't
     * afford to lose by fumbling the drop key.
     */
    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (!plugin.getStarforgeManager().isStarforge(event.getItemDrop().getItemStack())) return;

        event.setCancelled(true);
        sendActionBar(event.getPlayer(),
                ChatColor.RED + "Your Starforge can't be dropped.");
    }

    /**
     * Left-clicking the Starforge flips Auto Roll, but only once the Auto
     * Roll skill is unlocked — otherwise it just points them at the tree.
     */
    private void toggleAutoRoll(Player player, PlayerData data) {
        if (!data.hasUnlocked("auto_roll_root")) {
            sendActionBar(player, ChatColor.RED + "Unlock \"Auto Roll\" in /skilltree first!");
            return;
        }

        boolean enabled = !data.isAutoRollEnabled();
        data.setAutoRollEnabled(enabled);
        sendActionBar(player, enabled
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "Auto Roll ON"
                : ChatColor.RED + "" + ChatColor.BOLD + "Auto Roll OFF");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, enabled ? 1.5f : 0.8f);
    }

    /**
     * Kicks off the roll animation: no cooldown, but the result isn't
     * decided/granted until the timer finishes. A player's roll-speed
     * multiplier (from the Rolling Speed skill tree branch, or future
     * armor upgrades) shortens the wait.
     */
    public void startRoll(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // The result is decided up front rather than when the timer ends,
        // so the animation can land on it: the teaser flashes candidates,
        // then the final frames ARE the drop you're about to be handed.
        // Rolling at the end instead meant the reel visibly stopped on one
        // item and gave you a different one.
        RollableItem result = plugin.getRarityManager().roll(plugin.getPrestigeManager().effectiveLuck(data));

        // An Epic+ roll is stretched to at least the length of its own
        // build-up, so the effect always gets to play out in full — a
        // 10-second Mythical reveal on a 2-second roll would just be a
        // flash. It also means a longer-than-usual roll is itself the
        // first hint that something good is coming.
        // Assigned once: the timer lambda below captures it, so it has to
        // stay effectively final.
        long baseTicks = effectiveRollTicks(data);
        final long totalTicks = RollAura.isBigDrop(result.getRarity())
                ? Math.max(baseTicks, RollAura.durationTicks(result.getRarity()))
                : baseTicks;

        RollAura aura = RollAura.start(plugin, player, result.getRarity());
        if (aura != null) {
            activeAuras.put(player.getUniqueId(), aura);
        }

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
                finishRoll(player, data, result);
                return;
            }

            remainingTicks.put(player.getUniqueId(), totalTicks - elapsed[0]);
            // The per-tick click is a constant clatter that buries the
            // aura's score, so a big roll goes quiet and lets the build-up
            // carry the audio instead.
            if (data.isRollSoundEnabled() && aura == null) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }

            // Case-opening-style teaser: every 5% of the roll, flash a
            // candidate item + its odds in the center of the screen.
            if (data.isRollAnimationEnabled()) {
                int step = (int) (elapsed[0] * 20 / totalTicks);
                if (step != lastStep[0]) {
                    lastStep[0] = step;
                    // The last frame already shows the real result, so the
                    // reel visibly slows onto it instead of cutting to it.
                    showRollTitle(player, step >= 19 ? result : randomPreview(data));
                }
            }
        }, 0L, 2L);

        rollingTasks.put(player.getUniqueId(), taskHolder[0]);
    }

    /**
     * How long one roll takes for this player right now, in ticks —
     * base duration scaled by their current Speed (skill tree + worn
     * armor). Auto Roll fires on this same cadence, so upgrading Speed
     * speeds up manual and automatic rolls identically.
     */
    public long effectiveRollTicks(PlayerData data) {
        double baseSeconds = plugin.getConfig().getDouble("roll-item.roll-duration-seconds", 5.0);
        double multiplier = data.getEffectiveRollSpeedMultiplier();
        return Math.max(1L, Math.round((baseSeconds / multiplier) * 20.0));
    }

    /**
     * Flashes a candidate item drawn from the same luck-weighted odds as
     * the real roll — pulling a uniform-random item here made the teaser
     * flash absurd combinations (a 1-in-10M item right before landing on
     * something 1-in-17), which didn't feel believable.
     */
    private RollableItem randomPreview(PlayerData data) {
        if (plugin.getRarityManager().getItems().isEmpty()) return null;
        return plugin.getRarityManager().roll(plugin.getPrestigeManager().effectiveLuck(data));
    }

    /** Flashes one item + its odds in the center of the screen. */
    private void showRollTitle(Player player, RollableItem item) {
        if (item == null) return;

        Component name = LegacyComponentSerializer.legacySection()
                .deserialize(RollFormat.displayName(plugin, item));
        Component odds = LegacyComponentSerializer.legacySection()
                .deserialize(ChatColor.GRAY + "· " + RollFormat.chance(item.getOdds()) + " ·");

        Title title = Title.title(name, odds, Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ZERO));
        player.showTitle(title);
    }

    private void finishRoll(Player player, PlayerData data, RollableItem result) {
        clearActionBar(player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

        RollAura aura = activeAuras.remove(player.getUniqueId());
        if (aura != null) {
            aura.reveal();
        }

        // Hold the landed item on screen so the reel ends on exactly what
        // the player is handed.
        if (data.isRollAnimationEnabled()) {
            showRollTitle(player, result);
        }
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

        double moneyEarned = depositRollMoney(player, result);

        if (data.isAutoConverting(rarity)) {
            // Auto-convert banks the drop instead of handing over the item —
            // the same stored drops /convert produces, spendable in /armor
            // and /starforge.
            data.addBankedDrops(rarity, 1L);
            data.addConverted(rarity, 1L);
            if (!silent) {
                sendHoverable(player, previewItem, RollFormat.personalRollLine(plugin, result)
                        + ChatColor.YELLOW + " → stored (auto-converted)");
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
            String moneyText = moneyEarned > 0
                    ? ChatColor.GREEN + "  +$" + String.format("%.0f", moneyEarned)
                    : "";
            sendActionBar(player, RollFormat.displayName(plugin, result)
                    + ChatColor.GRAY + "  " + RollFormat.compactOdds(result.getOdds()) + moneyText);
        }

        maybeRegisterDiscovery(player, data, result, silent);
        maybeBroadcast(player, result, previewItem);
    }

    /**
     * Every roll also pays real Money (Vault) on top of the item itself —
     * money = odds x configured multiplier, so rarer items pay out more.
     * Returns 0 if Vault/an economy plugin isn't installed.
     */
    private double depositRollMoney(Player player, RollableItem result) {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) return 0.0;

        double multiplier = plugin.getConfig().getDouble("economy.money-per-odds-multiplier", 10.0);
        double money = result.getOdds() * multiplier;
        registration.getProvider().depositPlayer(player, money);
        return money;
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

        if (!silent) {
            String notice = ChatColor.GREEN + "" + ChatColor.BOLD + "NEW! " + ChatColor.RESET
                    + RollFormat.displayName(plugin, result) + ChatColor.GRAY + " added to your index "
                    + ChatColor.DARK_AQUA + "(" + String.format("%.2f", result.getLuckMultiplier()) + "x Luck)";
            player.sendMessage(notice);
            sendActionBar(player, notice);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        }
    }

    /** The physical, PDC-tagged drop item. Public so /rngadmin can hand them out. */
    public ItemStack buildTaggedItem(RollableItem result) {
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
