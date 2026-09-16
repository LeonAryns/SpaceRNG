package com.spacerng.solrng.boss;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.consumable.Consumable;
import com.spacerng.solrng.crate.Crate;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bosses: a server event rather than a mob.
 *
 * Nobody swings at it. A boss stands over the spot an admin picked and
 * loses health to the two things everybody is already doing: every crop
 * harvested takes off the Coins it paid, and every roll takes off its
 * rarity's damage. A farmer and a roller both take part without learning
 * anything new, and there is no combat code, no pathfinding and no mob
 * that can be griefed or led away.
 *
 * Damage is tracked per player for the whole fight, and when the boss
 * falls the rewards follow it: everyone past a minimum share gets the
 * base reward, the top three get their own instead. A boss that runs out
 * of time simply leaves and pays nobody.
 *
 * The body is two display entities, an item and a text panel, both
 * non-persistent so neither can ever be written into a chunk. The bar is
 * one keyed boss bar per player, because the title carries that player's
 * own damage.
 */
public class BossManager {

    private static final double VIEW = 64.0;

    private final SolRNGPlugin plugin;
    private final Map<String, BossType> types = new LinkedHashMap<>();
    private final Map<Rarity, Long> rollDamage = new EnumMap<>(Rarity.class);
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final File file;
    private final NamespacedKey tagKey = SolRNGPlugin.key("boss");

    private boolean enabled = true;
    private int everyMinutes = 180;
    private int minPlayers = 2;
    private double harvestPerCoin = 1.0;
    private double minSharePercent = 1.0;

    // The spot is kept as a world NAME plus coordinates, never as a
    // resolved Location. A Multiverse world loads after this plugin
    // enables, so anything resolved at load time is null for the wrong
    // reason. Looking the world up when a boss actually starts costs
    // nothing and always works.
    private String spotWorld;
    private double spotX;
    private double spotY;
    private double spotZ;
    private float spotYaw;

    private BossType active;
    private long maxHealth;
    private long health;
    private long endsAt;
    private Location at;
    private ItemDisplay body;
    private TextDisplay panel;
    private final Map<UUID, Long> damage = new HashMap<>();
    private int lastMilestone = 100;
    private long frame;
    private BukkitTask task;
    private BukkitTask timer;
    private long nextSpawn;

    public BossManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss.yml");
        loadSpot();
    }

    // ---------------------------------------------------------------
    // Config
    // ---------------------------------------------------------------

    public void load(FileConfiguration config) {
        types.clear();
        rollDamage.clear();
        enabled = config.getBoolean("boss.enabled", true);
        everyMinutes = Math.max(0, config.getInt("boss.every-minutes", 180));
        minPlayers = Math.max(1, config.getInt("boss.min-players", 2));
        harvestPerCoin = Math.max(0.0, config.getDouble("boss.harvest-damage-per-coin", 1.0));
        minSharePercent = Math.max(0.0, config.getDouble("boss.min-share-percent", 1.0));

        for (Rarity rarity : Rarity.values()) {
            rollDamage.put(rarity, Math.max(0L, config.getLong("boss.roll-damage." + rarity.name(), 0L)));
        }

        ConfigurationSection section = config.getConfigurationSection("boss.types");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(id);
                if (t == null) continue;
                List<String> colors = t.getStringList("colors");
                if (colors.isEmpty()) colors = List.of("#FFD54F");
                Material icon = Material.matchMaterial(t.getString("icon", "NETHER_STAR"));
                if (icon == null) icon = Material.NETHER_STAR;
                List<BossReward> top = new ArrayList<>();
                for (Map<?, ?> raw : t.getMapList("rewards.top")) {
                    top.add(reward(raw));
                }
                types.put(id.toLowerCase(Locale.ROOT), new BossType(
                        id.toLowerCase(Locale.ROOT),
                        t.getString("display", id),
                        colors, icon,
                        Math.max(1L, t.getLong("health", 500_000L)),
                        Math.max(0.0, t.getDouble("weight", 1.0)),
                        Math.max(1, t.getInt("duration-minutes", 15)),
                        new BossReward(
                                Math.max(0L, t.getLong("rewards.credits", 0L)),
                                Math.max(0L, t.getLong("rewards.perk-tickets", 0L)),
                                Math.max(0L, t.getLong("rewards.coins", 0L)),
                                t.getString("rewards.crate", "").toLowerCase(Locale.ROOT),
                                Math.max(0, t.getInt("rewards.keys", 0))),
                        top));
            }
        }
        plugin.getLogger().info("Loaded " + types.size() + " boss types.");
    }

    /** One entry of the top-three list, which arrives as a raw map. */
    private BossReward reward(Map<?, ?> raw) {
        return new BossReward(
                number(raw.get("credits")),
                number(raw.get("perk-tickets")),
                number(raw.get("coins")),
                raw.get("crate") == null ? "" : String.valueOf(raw.get("crate")).toLowerCase(Locale.ROOT),
                (int) number(raw.get("keys")));
    }

    private long number(Object value) {
        return value instanceof Number n ? Math.max(0L, n.longValue()) : 0L;
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    /** Sweeps anything a reload left standing, then starts the timer. */
    public void start() {
        sweep();
        if (timer != null) timer.cancel();
        scheduleNext();
        timer = plugin.getServer().getScheduler().runTaskTimer(plugin, this::considerSpawn, 200L, 200L);
    }

    public void stop() {
        if (timer != null) timer.cancel();
        timer = null;
        if (task != null) task.cancel();
        task = null;
        active = null;
        clear();
        for (UUID uuid : List.copyOf(bars.keySet())) hideBar(uuid);
    }

    private void scheduleNext() {
        nextSpawn = everyMinutes <= 0 ? 0L : System.currentTimeMillis() + everyMinutes * 60_000L;
    }

    private void considerSpawn() {
        if (!enabled || active != null || everyMinutes <= 0) return;
        if (nextSpawn <= 0L || System.currentTimeMillis() < nextSpawn) return;
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            // Not enough people to make a dent in it. Wait rather than
            // burn the event on an empty server, and look again shortly.
            nextSpawn = System.currentTimeMillis() + 60_000L;
            return;
        }
        BossType type = pick();
        if (type == null) {
            scheduleNext();
            return;
        }
        spawn(type);
    }

    private BossType pick() {
        double total = 0.0;
        for (BossType type : types.values()) total += type.weight();
        if (total <= 0.0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double seen = 0.0;
        for (BossType type : types.values()) {
            seen += type.weight();
            if (roll < seen) return type;
        }
        return null;
    }

    // ---------------------------------------------------------------
    // The fight
    // ---------------------------------------------------------------

    public boolean isActive() {
        return active != null;
    }

    public Map<String, BossType> getTypes() {
        return types;
    }

    public long minutesToNext() {
        if (nextSpawn <= 0L) return -1L;
        return Math.max(0L, (nextSpawn - System.currentTimeMillis()) / 60_000L);
    }

    /** Starts a boss now. False when there is nowhere to put it. */
    public boolean spawn(BossType type) {
        if (active != null) return false;
        Location spot = resolveSpot();
        if (spot == null) {
            plugin.getLogger().warning("A boss wanted to spawn but no spot is set. Run /rngadmin boss here.");
            return false;
        }

        active = type;
        at = spot;
        maxHealth = type.health();
        health = maxHealth;
        endsAt = System.currentTimeMillis() + type.durationMinutes() * 60_000L;
        damage.clear();
        lastMilestone = 100;
        frame = 0L;

        draw(type, spot);
        announceArrival(type);

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        return true;
    }

    private void tick() {
        if (active == null) return;
        try {
            frame += 2L;
            if (health <= 0L) {
                finish(true);
                return;
            }
            if (System.currentTimeMillis() >= endsAt) {
                finish(false);
                return;
            }
            spin();
            if (frame % 8 == 0) pulse();
            if (frame % 20 == 0) {
                refreshBars();
                refreshPanel();
            }
        } catch (Exception ex) {
            // One bad frame must not throw ten times a second forever.
            plugin.getLogger().warning("Boss frame failed, ending the event: " + ex.getMessage());
            finish(false);
        }
    }

    /** A harvested crop hits for the Coins it paid. */
    public void onHarvest(Player player, long coins) {
        if (active == null || coins <= 0L || harvestPerCoin <= 0.0) return;
        hit(player, Math.round(coins * harvestPerCoin));
    }

    /** A landed roll hits for its rarity. */
    public void onRoll(Player player, Rarity rarity) {
        if (active == null || rarity == null) return;
        hit(player, rollDamage.getOrDefault(rarity, 0L));
    }

    private void hit(Player player, long amount) {
        if (amount <= 0L || health <= 0L) return;
        long dealt = Math.min(amount, health);
        health -= dealt;
        damage.merge(player.getUniqueId(), dealt, Long::sum);

        int percent = (int) Math.floor(health * 100.0 / maxHealth);
        for (int mark : new int[]{75, 50, 25}) {
            if (lastMilestone > mark && percent <= mark) {
                lastMilestone = mark;
                announceMilestone(mark);
                break;
            }
        }
        if (health <= 0L) finish(true);
    }

    private void finish(boolean killed) {
        BossType type = active;
        if (type == null) return;
        active = null;
        if (task != null) task.cancel();
        task = null;

        if (killed) {
            payOut(type);
        } else {
            String name = Lore.gradient(type.display(), true, type.stops());
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage("  " + name + ChatColor.RESET + ChatColor.GRAY + " left with "
                    + ChatColor.WHITE + Lore.shorten(health) + ChatColor.GRAY + " health still standing.");
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  Nobody is paid for a boss that walks away.");
            Bukkit.broadcastMessage("");
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 0.7f, 0.8f);
            }
        }

        clear();
        for (UUID uuid : List.copyOf(bars.keySet())) hideBar(uuid);
        damage.clear();
        scheduleNext();
    }

    /** Ends the event with no reward, for an admin. */
    public boolean cancel() {
        if (active == null) return false;
        finish(false);
        return true;
    }

    // ---------------------------------------------------------------
    // Rewards
    // ---------------------------------------------------------------

    private void payOut(BossType type) {
        List<Map.Entry<UUID, Long>> ranked = new ArrayList<>(damage.entrySet());
        ranked.sort(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed());

        long floor = Math.round(maxHealth * (minSharePercent / 100.0));
        String name = Lore.gradient(type.display().toUpperCase(Locale.ROOT), true, type.stops());

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("        " + Lore.SPARK + " " + name + ChatColor.RESET
                + " " + ChatColor.GRAY + ChatColor.BOLD + "HAS FALLEN " + ChatColor.RESET + Lore.SPARK);
        Bukkit.broadcastMessage("");

        int paid = 0;
        for (int i = 0; i < ranked.size(); i++) {
            Map.Entry<UUID, Long> entry = ranked.get(i);
            long dealt = entry.getValue();
            if (dealt < floor) continue;
            BossReward reward = i < type.top().size() ? type.top().get(i) : type.base();
            if (reward.isEmpty()) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            // Only players still online are paid. A key is a real item and
            // an item needs somewhere to land, so paying somebody who left
            // would need a queue this event does not have.
            if (player == null) continue;
            give(player, reward);
            paid++;

            if (i < 3) {
                double share = dealt * 100.0 / maxHealth;
                Bukkit.broadcastMessage("  " + place(i) + " " + ChatColor.WHITE + player.getName()
                        + ChatColor.DARK_GRAY + "  " + Lore.BULLET + "  "
                        + ChatColor.GRAY + Lore.shorten(dealt) + " damage"
                        + ChatColor.DARK_GRAY + " (" + String.format("%.1f", share) + "%)");
            }
        }

        if (paid > 3) {
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  and " + (paid - 3) + " more fighters were paid.");
        } else if (paid == 0) {
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  Nobody did enough damage to be paid.");
        }
        Bukkit.broadcastMessage("");

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.6f, 1.2f);
            online.playSound(online.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
        }
        if (at != null) {
            Location burst = at.clone().add(0, 2.2, 0);
            for (Player viewer : viewers()) {
                viewer.spawnParticle(Particle.EXPLOSION_EMITTER, burst, 2, 0.6, 0.4, 0.6, 0.0);
                viewer.spawnParticle(Particle.FIREWORK, burst, 80, 0.8, 0.8, 0.8, 0.35);
            }
        }
    }

    private String place(int index) {
        return switch (index) {
            case 0 -> ChatColor.GOLD + "" + ChatColor.BOLD + "1.";
            case 1 -> ChatColor.WHITE + "" + ChatColor.BOLD + "2.";
            case 2 -> ChatColor.GOLD + "3.";
            default -> ChatColor.DARK_GRAY + "" + (index + 1) + ".";
        };
    }

    private void give(Player player, BossReward reward) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        List<String> got = new ArrayList<>();

        if (reward.credits() > 0) {
            data.addPoints(reward.credits());
            got.add(ChatColor.LIGHT_PURPLE + String.format("%,d", reward.credits()) + " Credits");
        }
        if (reward.perkTickets() > 0) {
            data.setPerkTickets(data.getPerkTickets() + reward.perkTickets());
            got.add(ChatColor.AQUA + String.format("%,d", reward.perkTickets()) + " Perk Tickets");
        }
        if (reward.coins() > 0) {
            data.addTokens(reward.coins());
            got.add(ChatColor.GOLD + Lore.shorten(reward.coins()) + " Coins");
        }
        if (reward.keys() > 0 && !reward.crate().isBlank()) {
            Crate crate = plugin.getCrateManager().get(reward.crate());
            Consumable key = crate == null ? null : plugin.getConsumableManager().get(crate.keyId());
            if (key != null) {
                plugin.getConsumableManager().give(player, key, reward.keys());
                got.add(ChatColor.YELLOW + String.valueOf(reward.keys()) + "x "
                        + plugin.getCrateManager().keyName(crate));
            }
        }

        plugin.getScoreboardManager().update(player);
        if (got.isEmpty()) return;
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Boss reward  " + ChatColor.RESET
                + ChatColor.GRAY + String.join(ChatColor.DARK_GRAY + ", " + ChatColor.GRAY, got));
        player.sendMessage("");
    }

    // ---------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------

    private void announceArrival(BossType type) {
        String name = Lore.gradient(type.display().toUpperCase(Locale.ROOT), true, type.stops());
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("        " + Lore.SPARK + " " + name + ChatColor.RESET
                + " " + ChatColor.GRAY + ChatColor.BOLD + "HAS APPEARED " + ChatColor.RESET + Lore.SPARK);
        Bukkit.broadcastMessage(ChatColor.GRAY + "  " + Lore.shorten(maxHealth) + " health"
                + ChatColor.DARK_GRAY + "  " + Lore.BULLET + "  "
                + ChatColor.GRAY + type.durationMinutes() + " minutes"
                + ChatColor.DARK_GRAY + "  " + Lore.BULLET + "  "
                + ChatColor.GRAY + "at spawn");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  Every crop you harvest and every roll you land hurts it.");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  The rewards follow your damage. "
                + ChatColor.YELLOW + "/boss");
        Bukkit.broadcastMessage("");

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.6f);
            showBar(online);
        }
    }

    private void announceMilestone(int percent) {
        if (active == null) return;
        String name = Lore.gradient(active.display(), true, active.stops());
        String line = "  " + name + ChatColor.RESET + ChatColor.GRAY + " is down to "
                + ChatColor.WHITE + percent + "%" + ChatColor.GRAY + " health.";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(line);
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL,
                    0.5f, percent <= 25 ? 1.2f : 0.8f);
        }
    }

    /** The /boss readout. */
    public List<String> status(Player player) {
        List<String> lines = new ArrayList<>();
        if (active == null) {
            long minutes = minutesToNext();
            lines.add("");
            lines.add(ChatColor.GRAY + "  No boss is up right now.");
            lines.add(minutes < 0
                    ? ChatColor.DARK_GRAY + "  The timer is off, an admin starts them by hand."
                    : ChatColor.DARK_GRAY + "  The next one is due in " + ChatColor.WHITE + minutes
                            + ChatColor.DARK_GRAY + " minutes.");
            lines.add("");
            return lines;
        }

        double fraction = Math.max(0.0, (double) health / maxHealth);
        long mine = damage.getOrDefault(player.getUniqueId(), 0L);
        lines.add("");
        lines.add("  " + Lore.gradient(active.display().toUpperCase(Locale.ROOT), true, active.stops()));
        lines.add("  " + Lore.bar(active.accent(), fraction) + ChatColor.GRAY + "  "
                + Lore.shorten(health) + ChatColor.DARK_GRAY + " / " + ChatColor.GRAY + Lore.shorten(maxHealth));
        lines.add(ChatColor.GRAY + "  " + timeLeft() + " left"
                + ChatColor.DARK_GRAY + "  " + Lore.BULLET + "  "
                + ChatColor.GRAY + damage.size() + " fighting");
        lines.add("");
        lines.add(ChatColor.WHITE + "  Your damage  " + ChatColor.GRAY + Lore.shorten(mine)
                + ChatColor.DARK_GRAY + " (" + String.format("%.1f", mine * 100.0 / maxHealth) + "%)");

        List<Map.Entry<UUID, Long>> ranked = new ArrayList<>(damage.entrySet());
        ranked.sort(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed());
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            Map.Entry<UUID, Long> entry = ranked.get(i);
            String who = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            lines.add("  " + place(i) + " " + ChatColor.GRAY + (who == null ? "?" : who)
                    + ChatColor.DARK_GRAY + "  " + Lore.shorten(entry.getValue()));
        }
        lines.add("");
        return lines;
    }

    private String timeLeft() {
        long seconds = Math.max(0L, (endsAt - System.currentTimeMillis()) / 1000L);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    // ---------------------------------------------------------------
    // The bar
    // ---------------------------------------------------------------

    /**
     * The bar's key.
     *
     * Keyed rather than anonymous for the same reason as the Luck bar: a
     * bar the server never recorded survives a crash on everybody's screen
     * and the next instance draws a second one beside it.
     */
    private static NamespacedKey barKey(UUID uuid) {
        return SolRNGPlugin.key("bar_boss_" + uuid);
    }

    public void showBar(Player player) {
        if (active == null) return;
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar(barKey(uuid), "", BarColor.RED, BarStyle.SEGMENTED_20));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        refreshBar(player, bar);
    }

    public void hideBar(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) bar.removeAll();
        Bukkit.removeBossBar(barKey(uuid));
    }

    private void refreshBars() {
        for (Player online : Bukkit.getOnlinePlayers()) showBar(online);
    }

    private void refreshBar(Player player, BossBar bar) {
        double fraction = Math.max(0.0, Math.min(1.0, (double) health / maxHealth));
        long mine = damage.getOrDefault(player.getUniqueId(), 0L);
        bar.setProgress(fraction);
        bar.setColor(fraction > 0.5 ? BarColor.RED : fraction > 0.25 ? BarColor.YELLOW : BarColor.WHITE);
        bar.setTitle(Lore.gradient(active.display().toUpperCase(Locale.ROOT), true, active.stops())
                + ChatColor.RESET + ChatColor.DARK_GRAY + "  |  "
                + ChatColor.WHITE + Lore.shorten(health) + ChatColor.GRAY + " / " + Lore.shorten(maxHealth)
                + ChatColor.DARK_GRAY + "  |  "
                + ChatColor.GRAY + "you " + ChatColor.WHITE + Lore.shorten(mine)
                + ChatColor.DARK_GRAY + "  |  "
                + ChatColor.GRAY + timeLeft());
        bar.setVisible(true);
    }

    // ---------------------------------------------------------------
    // The body
    // ---------------------------------------------------------------

    private void draw(BossType type, Location spot) {
        Location centre = spot.clone().add(0, 2.2, 0);
        // A display copies the rotation of wherever it spawns, so both
        // pieces are pinned flat and turned by their own transformation.
        centre.setYaw(0f);
        centre.setPitch(0f);

        ItemStack icon = new ItemStack(type.icon());
        body = centre.getWorld().spawn(centre, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(icon);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(2.6f, 2.6f, 2.6f), new Quaternionf()));
            d.setViewRange((float) (VIEW / 64.0));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowRadius(1.2f);
            d.setShadowStrength(0.7f);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, type.id());
        });

        Location above = centre.clone().add(0, 2.4, 0);
        panel = above.getWorld().spawn(above, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setSeeThrough(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange((float) (VIEW / 64.0));
            d.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(1.4f, 1.4f, 1.4f), new Quaternionf()));
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, type.id());
        });
        refreshPanel();
    }

    private void refreshPanel() {
        if (panel == null || !panel.isValid() || active == null) return;
        double fraction = Math.max(0.0, (double) health / maxHealth);
        String text = Lore.gradient(active.display().toUpperCase(Locale.ROOT), true, active.stops())
                + "\n" + Lore.bar(active.accent(), fraction)
                + "\n" + ChatColor.WHITE + Lore.shorten(health) + ChatColor.GRAY + " / " + Lore.shorten(maxHealth)
                + "\n" + ChatColor.DARK_GRAY + damage.size() + " fighting  " + Lore.BULLET + "  "
                + timeLeft() + " left";
        panel.text(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    /** A slow turn, so the body reads as alive without the entity moving. */
    private void spin() {
        if (body == null || !body.isValid()) return;
        float angle = (float) (frame * Math.PI / 80.0);
        float bob = (float) (Math.sin(frame / 24.0) * 0.14);
        body.setTransformation(new Transformation(new Vector3f(0f, bob, 0f),
                new Quaternionf().rotateY(angle),
                new Vector3f(2.6f, 2.6f, 2.6f), new Quaternionf()));
    }

    /** A ring running outward, thinning as it widens. */
    private void pulse() {
        if (at == null || active == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(colourOf(active.accent()), 2.0f);
        double phase = (frame % 80) / 80.0;
        double radius = 1.0 + phase * 4.0;
        int points = 10 + (int) (phase * 8);
        Location centre = at.clone().add(0, 0.4, 0);
        for (Player viewer : viewers()) {
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2 / points) * i + frame * 0.05;
                viewer.spawnParticle(Particle.DUST,
                        centre.clone().add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius),
                        1, 0.0, 0.0, 0.0, 0.0, dust);
            }
            if (frame % 24 == 0) {
                viewer.spawnParticle(Particle.SOUL_FIRE_FLAME, centre.clone().add(0, 1.6, 0),
                        0, 0.0, 1.0, 0.0, 0.12);
            }
        }
    }

    private List<Player> viewers() {
        List<Player> out = new ArrayList<>();
        if (at == null || at.getWorld() == null) return out;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getWorld().equals(at.getWorld())) continue;
            if (online.getLocation().distanceSquared(at) > VIEW * VIEW) continue;
            out.add(online);
        }
        return out;
    }

    private static Color colourOf(String hex) {
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Color.fromRGB(Integer.parseInt(clean.substring(0, 2), 16),
                    Integer.parseInt(clean.substring(2, 4), 16),
                    Integer.parseInt(clean.substring(4, 6), 16));
        } catch (RuntimeException ex) {
            return Color.fromRGB(255, 213, 79);
        }
    }

    private void clear() {
        if (body != null && body.isValid()) body.remove();
        if (panel != null && panel.isValid()) panel.remove();
        body = null;
        panel = null;
        at = null;
    }

    /**
     * Removes anything this plugin left behind. The pieces are not
     * persistent, so only a reload inside a running server can strand
     * one, but one left standing forever is the first thing people see.
     */
    private void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(ItemDisplay.class, TextDisplay.class)) {
                if (entity.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // The spot
    // ---------------------------------------------------------------

    public void setSpot(Location location) {
        spotWorld = location.getWorld().getName();
        spotX = location.getX();
        spotY = location.getY();
        spotZ = location.getZ();
        spotYaw = location.getYaw();
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("world", spotWorld);
        yml.set("x", spotX);
        yml.set("y", spotY);
        yml.set("z", spotZ);
        yml.set("yaw", (double) spotYaw);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save the boss spot: " + ex.getMessage());
        }
    }

    public boolean hasSpot() {
        return spotWorld != null;
    }

    private void loadSpot() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        spotWorld = yml.getString("world");
        spotX = yml.getDouble("x");
        spotY = yml.getDouble("y");
        spotZ = yml.getDouble("z");
        spotYaw = (float) yml.getDouble("yaw");
    }

    /** The spot as a Location, or the server spawn when none is set. */
    private Location resolveSpot() {
        if (spotWorld != null) {
            World world = Bukkit.getWorld(spotWorld);
            if (world != null) return new Location(world, spotX, spotY, spotZ, spotYaw, 0f);
            plugin.getLogger().warning("Boss world '" + spotWorld + "' is not loaded yet.");
        }
        return plugin.getSpawnManager().hasSpawn() ? plugin.getSpawnManager().getSpawn() : null;
    }
}
