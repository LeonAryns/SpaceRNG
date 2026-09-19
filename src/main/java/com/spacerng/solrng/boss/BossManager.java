package com.spacerng.solrng.boss;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.crate.Crate;
import com.spacerng.solrng.consumable.Consumable;
import com.spacerng.solrng.gui.Lore;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bosses: a server event that every player fights on their own.
 *
 * One boss appears for everybody at once, but each player gets their own
 * copy of it with its own health and its own clock: beat 8000 crops
 * worth of it inside ten minutes and it is yours. That is deliberate.
 * Shared health means the top farmer takes the boss down before anybody
 * else has found their hoe, and everyone else is watching a bar move.
 * Per player, the event asks the same thing of everybody and nobody can
 * spoil it for the rest.
 *
 * Health is counted in crops, so a harvest is one point whatever the hoe
 * pays for it, and a landed roll is worth its rarity from config. Nobody
 * swings at anything: there is no combat code, no pathfinding, and no mob
 * that can be led away or griefed.
 *
 * Whoever beats theirs in time pulls from the boss's reward table, which
 * is written in the same vocabulary as a crate and paid out by the same
 * code. Everyone gets the same table and the same chances, so the reward
 * is luck rather than a race.
 *
 * The process that spawns one every couple of hours is started with a
 * command and remembered in boss.yml, so it survives a jar update.
 */
public class BossManager {

    private static final double VIEW = 64.0;

    /** One player's own copy of the boss that is up. */
    private static final class Fight {
        final long max;
        long health;
        boolean beaten;

        Fight(long max) {
            this.max = max;
            this.health = max;
        }
    }

    private final SolRNGPlugin plugin;
    private final Map<String, BossType> types = new LinkedHashMap<>();
    private final Map<Rarity, Long> rollDamage = new EnumMap<>(Rarity.class);
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Fight> fights = new HashMap<>();
    private final File file;
    private final NamespacedKey tagKey = SolRNGPlugin.key("boss");

    private boolean enabled = true;
    private int everyMinutes = 120;
    private int minPlayers = 1;
    private long damagePerCrop = 1L;
    private boolean announceKills = true;

    // The chance a boss turns up on its own off the back of one crop or
    // one roll, and how long the server is left alone afterwards. Both
    // default to zero so a server that never configures them keeps the
    // timed spawn it already had.
    private double harvestSpawnChance = 0.0;
    private double rollSpawnChance = 0.0;
    private int naturalCooldownMinutes = 45;
    private long naturalReadyAt = 0L;

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

    // The recurring process. Both of these live in boss.yml so a jar
    // update, which is a restart, picks the schedule back up instead of
    // quietly stopping it.
    private boolean running;
    private long nextSpawn;

    private BossType active;
    private long eventEndsAt;
    private int beaten;
    private int joined;
    private Location at;
    private ItemDisplay body;
    private TextDisplay panel;
    private long frame;
    private BukkitTask task;
    private BukkitTask timer;

    public BossManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "boss.yml");
        loadState();
    }

    // ---------------------------------------------------------------
    // Config
    // ---------------------------------------------------------------

    public void load(FileConfiguration config) {
        types.clear();
        rollDamage.clear();
        enabled = config.getBoolean("boss.enabled", true);
        everyMinutes = Math.max(1, config.getInt("boss.every-minutes", 120));
        minPlayers = Math.max(1, config.getInt("boss.min-players", 1));
        damagePerCrop = Math.max(0L, config.getLong("boss.damage-per-crop", 1L));
        announceKills = config.getBoolean("boss.announce-kills", true);
        // One in N, not a decimal: a chance small enough for a per crop
        // roll only survives a config round trip as an integer. See the
        // comment on boss.natural in config.yml.
        harvestSpawnChance = oneIn(config.getLong("boss.natural.harvest-one-in", 0L));
        rollSpawnChance = oneIn(config.getLong("boss.natural.roll-one-in", 0L));
        naturalCooldownMinutes = Math.max(0, config.getInt("boss.natural.cooldown-minutes", 45));

        for (Rarity rarity : Rarity.values()) {
            rollDamage.put(rarity, Math.max(0L, config.getLong("boss.roll-damage." + rarity.name(), 0L)));
        }

        ConfigurationSection section = config.getConfigurationSection("boss.types");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(id);
                if (t == null) continue;
                String key = id.toLowerCase(Locale.ROOT);
                List<String> colors = t.getStringList("colors");
                if (colors.isEmpty()) colors = List.of("#FFD54F");
                Material icon = Material.matchMaterial(t.getString("icon", "NETHER_STAR"));
                if (icon == null) icon = Material.NETHER_STAR;

                types.put(key, new BossType(key, t.getString("display", id), colors, icon,
                        Math.max(1L, t.getLong("health", 8000L)),
                        Math.max(0.0, t.getDouble("weight", 1.0)),
                        Math.max(1, t.getInt("duration-minutes", 10)),
                        t.getString("box", "boss").toLowerCase(Locale.ROOT),
                        Math.max(1, t.getInt("boxes", 1))));
            }
        }
        plugin.getLogger().info("Loaded " + types.size() + " boss types.");
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    /** Sweeps anything a reload left standing, then watches the clock. */
    public void start() {
        sweep();
        if (timer != null) timer.cancel();
        timer = plugin.getServer().getScheduler().runTaskTimer(plugin, this::considerSpawn, 100L, 100L);
    }

    public void stop() {
        if (timer != null) timer.cancel();
        timer = null;
        if (task != null) task.cancel();
        task = null;
        active = null;
        fights.clear();
        clear();
        for (UUID uuid : List.copyOf(bars.keySet())) hideBar(uuid);
    }

    /** Starts the recurring process and remembers that it is on. */
    public boolean startProcess() {
        if (running) return false;
        running = true;
        nextSpawn = System.currentTimeMillis() + everyMinutes * 60_000L;
        saveState();
        return true;
    }

    public boolean stopProcess() {
        if (!running) return false;
        running = false;
        nextSpawn = 0L;
        saveState();
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    private void considerSpawn() {
        if (!enabled || !running || active != null) return;
        if (nextSpawn <= 0L) {
            nextSpawn = System.currentTimeMillis() + everyMinutes * 60_000L;
            saveState();
            return;
        }
        if (System.currentTimeMillis() < nextSpawn) return;
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            // Nobody to fight it. Look again in a minute rather than burn
            // the event on an empty server.
            nextSpawn = System.currentTimeMillis() + 60_000L;
            saveState();
            return;
        }
        BossType type = pick();
        if (type == null) {
            nextSpawn = System.currentTimeMillis() + everyMinutes * 60_000L;
            saveState();
            return;
        }
        spawn(type);
    }

    /** A weighted random type, for the timer and for a bare boss start. */
    public BossType randomType() {
        return pick();
    }

    /** Turns "one in N" into a chance. N of zero means never. */
    private static double oneIn(long n) {
        return n <= 0L ? 0.0 : 1.0 / n;
    }

    /** What made a boss consider turning up on its own. */
    public enum Trigger {
        HARVEST, ROLL
    }

    /**
     * Rolls for a boss off the back of one crop or one roll.
     *
     * Rare on purpose, and behind a cooldown on top of the chance. A crop
     * is something a good farmer takes thousands of, so without the
     * cooldown even a tiny per-crop chance means a boss every few minutes
     * for whoever happens to be farming, and the event stops being an
     * event. The cooldown also covers the case where both chances fire
     * within the same minute.
     */
    public boolean maybeSpawnNaturally(Trigger trigger) {
        if (!enabled || active != null || trigger == null) return false;
        double chance = trigger == Trigger.HARVEST ? harvestSpawnChance : rollSpawnChance;
        if (chance <= 0.0) return false;
        long now = System.currentTimeMillis();
        if (now < naturalReadyAt) return false;
        if (Bukkit.getOnlinePlayers().size() < minPlayers) return false;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return false;

        BossType type = pick();
        if (type == null || !spawn(type)) return false;
        naturalReadyAt = now + naturalCooldownMinutes * 60_000L;
        return true;
    }

    /**
     * The Global Boss Respawn: a bought item forcing a boss for everyone,
     * right now.
     *
     * It ignores the natural cooldown, because somebody paid for it, but
     * it cannot stack a second boss on a live one. The buyer's name goes
     * in the broadcast, which is most of what makes the item worth
     * buying twice.
     */
    public boolean summon(String byName) {
        if (!enabled || active != null) return false;
        BossType type = pick();
        if (type == null || !spawn(type)) return false;
        naturalReadyAt = System.currentTimeMillis() + naturalCooldownMinutes * 60_000L;
        if (byName != null && !byName.isBlank()) {
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(
                    ChatColor.GRAY + "Summoned by " + ChatColor.WHITE + byName + ChatColor.GRAY + "."));
        }
        return true;
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
    // The event
    // ---------------------------------------------------------------

    public boolean isActive() {
        return active != null;
    }

    public Map<String, BossType> getTypes() {
        return types;
    }

    public long minutesToNext() {
        if (!running || nextSpawn <= 0L) return -1L;
        return Math.max(0L, (nextSpawn - System.currentTimeMillis() + 59_999L) / 60_000L);
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
        eventEndsAt = System.currentTimeMillis() + type.durationMinutes() * 60_000L;
        fights.clear();
        beaten = 0;
        joined = 0;
        frame = 0L;

        for (Player online : Bukkit.getOnlinePlayers()) enrol(online);

        draw(type, spot);
        announceArrival(type);

        // The next one is scheduled the moment this one starts, so a long
        // fight never eats into the gap and a restart mid-fight still
        // knows when the following boss is due.
        if (running) {
            nextSpawn = System.currentTimeMillis() + everyMinutes * 60_000L;
            saveState();
        }

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        return true;
    }

    /** Gives one player their own copy, with whatever time is left. */
    private void enrol(Player player) {
        if (active == null || fights.containsKey(player.getUniqueId())) return;
        fights.put(player.getUniqueId(), new Fight(active.health()));
        joined++;
    }

    /** A player joining mid-event still gets a boss and the time left. */
    public void onJoin(Player player) {
        if (active == null) return;
        enrol(player);
        showBar(player);
    }

    private void tick() {
        if (active == null) return;
        try {
            frame += 2L;
            if (System.currentTimeMillis() >= eventEndsAt) {
                finish();
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
            finish();
        }
    }

    /** Crops harvested hit the player's own copy. */
    public void onHarvest(Player player, long crops) {
        if (active == null || crops <= 0L || damagePerCrop <= 0L) return;
        hit(player, crops * damagePerCrop);
    }

    /** A landed roll hits for its rarity. */
    public void onRoll(Player player, Rarity rarity) {
        if (active == null || rarity == null) return;
        hit(player, rollDamage.getOrDefault(rarity, 0L));
    }

    private void hit(Player player, long amount) {
        if (amount <= 0L) return;
        Fight fight = fights.get(player.getUniqueId());
        if (fight == null || fight.beaten) return;

        fight.health = Math.max(0L, fight.health - amount);
        if (fight.health > 0L) return;

        fight.beaten = true;
        beaten++;
        reward(player);
        showBar(player);
        refreshPanel();
    }

    private void reward(Player player) {
        BossType type = active;
        if (type == null) return;

        player.sendMessage("");
        player.sendMessage("  " + Lore.gradient(type.display().toUpperCase(Locale.ROOT), true, type.stops())
                + ChatColor.RESET + " " + ChatColor.GRAY + ChatColor.BOLD + "BEATEN");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1.4f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);

        // The loot is a box rather than the loot itself: what is inside is
        // tuned in one place for every boss, and opening it is the same
        // show as every other crate.
        Crate box = plugin.getCrateManager().get(type.boxCrate());
        Consumable key = box == null ? null : plugin.getConsumableManager().get(box.keyId());
        if (key == null) {
            plugin.getLogger().warning("Boss '" + type.id() + "' pays the box '" + type.boxCrate()
                    + "', which has no crate or no key.");
            player.sendMessage(ChatColor.DARK_GRAY + "  This boss pays nothing yet.");
            player.sendMessage("");
            return;
        }
        plugin.getConsumableManager().give(player, key, type.boxes());
        player.sendMessage("  " + ChatColor.GRAY + "You won " + ChatColor.WHITE + type.boxes() + "x "
                + ChatColor.RESET + plugin.getConsumableManager().styledName(key));
        player.sendMessage(ChatColor.DARK_GRAY + "  Right click it to open it.");
        player.sendMessage("");

        if (announceKills) {
            String line = "  " + Lore.gradient(active.display(), true, active.stops())
                    + ChatColor.RESET + ChatColor.GRAY + " was beaten by "
                    + ChatColor.WHITE + player.getName() + ChatColor.GRAY + ".";
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) online.sendMessage(line);
            }
        }
    }

    private void finish() {
        BossType type = active;
        if (type == null) return;
        active = null;
        if (task != null) task.cancel();
        task = null;

        String name = Lore.gradient(type.display(), true, type.stops());
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("  " + name + ChatColor.RESET + ChatColor.GRAY + " is gone. "
                + ChatColor.WHITE + beaten + ChatColor.GRAY + " of " + ChatColor.WHITE + joined
                + ChatColor.GRAY + (joined == 1 ? " fighter beat it." : " fighters beat it."));
        long minutes = minutesToNext();
        if (minutes >= 0) {
            Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  The next one is due in " + minutes + " minutes.");
        }
        Bukkit.broadcastMessage("");
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 0.7f, 0.8f);
        }

        clear();
        fights.clear();
        for (UUID uuid : List.copyOf(bars.keySet())) hideBar(uuid);
    }

    /** Ends the event early, for an admin. */
    public boolean cancel() {
        if (active == null) return false;
        finish();
        return true;
    }

    // ---------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------

    private void announceArrival(BossType type) {
        String name = Lore.gradient(type.display().toUpperCase(Locale.ROOT), true, type.stops());
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage("        " + Lore.SPARK + " " + name + ChatColor.RESET
                + " " + ChatColor.GRAY + ChatColor.BOLD + "HAS APPEARED " + ChatColor.RESET + Lore.SPARK);
        Bukkit.broadcastMessage(ChatColor.GRAY + "  Everyone fights their own. "
                + ChatColor.WHITE + String.format("%,d", type.health()) + ChatColor.GRAY + " crops in "
                + ChatColor.WHITE + type.durationMinutes() + ChatColor.GRAY + " minutes.");
        Bukkit.broadcastMessage(ChatColor.GRAY + "  Every crop you harvest and every roll you land hurts yours.");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "  Beat it in time and the loot is yours. "
                + ChatColor.YELLOW + "/boss");
        Bukkit.broadcastMessage("");

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.6f);
            showBar(online);
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

        Fight fight = fights.get(player.getUniqueId());
        lines.add("");
        lines.add("  " + Lore.gradient(active.display().toUpperCase(Locale.ROOT), true, active.stops()));
        if (fight == null) {
            lines.add(ChatColor.GRAY + "  You are not in this one. The next boss is yours.");
            lines.add("");
            return lines;
        }

        double fraction = Math.max(0.0, (double) fight.health / fight.max);
        lines.add("  " + Lore.bar(active.accent(), fraction) + ChatColor.GRAY + "  "
                + String.format("%,d", fight.health) + ChatColor.DARK_GRAY + " / "
                + ChatColor.GRAY + String.format("%,d", fight.max));
        lines.add(fight.beaten
                ? ChatColor.GREEN + "  You beat it. Your loot is paid."
                : ChatColor.GRAY + "  " + timeLeft() + " left"
                        + ChatColor.DARK_GRAY + "  " + Lore.BULLET + "  "
                        + ChatColor.GRAY + String.format("%,d", fight.health) + " crops to go");
        lines.add(ChatColor.WHITE + "  Beaten by " + beaten + "/" + joined + " players");
        lines.add("");
        return lines;
    }

    private String timeLeft() {
        long seconds = Math.max(0L, (eventEndsAt - System.currentTimeMillis()) / 1000L);
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
        Fight fight = fights.get(player.getUniqueId());
        if (fight == null) return;
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar(barKey(uuid), "", BarColor.RED, BarStyle.SEGMENTED_20));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        refreshBar(fight, bar);
    }

    public void hideBar(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) bar.removeAll();
        Bukkit.removeBossBar(barKey(uuid));
    }

    private void refreshBars() {
        for (Player online : Bukkit.getOnlinePlayers()) showBar(online);
    }

    private void refreshBar(Fight fight, BossBar bar) {
        double fraction = Math.max(0.0, Math.min(1.0, (double) fight.health / fight.max));
        bar.setProgress(fight.beaten ? 1.0 : fraction);
        bar.setColor(fight.beaten ? BarColor.GREEN
                : fraction > 0.5 ? BarColor.RED : fraction > 0.25 ? BarColor.YELLOW : BarColor.WHITE);
        String name = Lore.gradient(active.display().toUpperCase(Locale.ROOT), true, active.stops());
        bar.setTitle(fight.beaten
                ? name + ChatColor.RESET + ChatColor.DARK_GRAY + "  |  "
                        + ChatColor.GREEN + "beaten" + ChatColor.DARK_GRAY + "  |  "
                        + ChatColor.GRAY + beaten + " of " + joined + " done"
                : name + ChatColor.RESET + ChatColor.DARK_GRAY + "  |  "
                        + ChatColor.WHITE + String.format("%,d", fight.health)
                        + ChatColor.GRAY + " crops left"
                        + ChatColor.DARK_GRAY + "  |  " + ChatColor.GRAY + timeLeft());
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

    /**
     * The panel carries the event, not one player's health: every fighter
     * has different numbers and one display cannot say two things.
     */
    private void refreshPanel() {
        if (panel == null || !panel.isValid() || active == null) return;
        String text = Lore.gradient(active.display().toUpperCase(Locale.ROOT), true, active.stops())
                + "\n" + ChatColor.WHITE + "Harvest " + String.format("%,d", active.health()) + " crops to beat it"
                + "\n" + ChatColor.WHITE + "Ends in " + timeLeft()
                + "\n" + ChatColor.WHITE + "Beaten by " + beaten + "/" + joined + " players";
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
    // The spot and the schedule, both in boss.yml
    // ---------------------------------------------------------------

    public void setSpot(Location location) {
        spotWorld = location.getWorld().getName();
        spotX = location.getX();
        spotY = location.getY();
        spotZ = location.getZ();
        spotYaw = location.getYaw();
        saveState();
    }

    public boolean hasSpot() {
        return spotWorld != null;
    }

    private void loadState() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        spotWorld = yml.getString("world");
        spotX = yml.getDouble("x");
        spotY = yml.getDouble("y");
        spotZ = yml.getDouble("z");
        spotYaw = (float) yml.getDouble("yaw");
        running = yml.getBoolean("running", false);
        nextSpawn = yml.getLong("next", 0L);
    }

    private void saveState() {
        YamlConfiguration yml = new YamlConfiguration();
        if (spotWorld != null) {
            yml.set("world", spotWorld);
            yml.set("x", spotX);
            yml.set("y", spotY);
            yml.set("z", spotZ);
            yml.set("yaw", (double) spotYaw);
        }
        yml.set("running", running);
        yml.set("next", nextSpawn);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save boss.yml: " + ex.getMessage());
        }
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
