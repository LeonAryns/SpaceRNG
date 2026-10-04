package com.spacerng.solrng.realm;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Secret Realm (V229): a place that opens on its own at random times,
 * for a few minutes, and only for players at Prestige 25 or higher.
 *
 * While it is open, /realm takes an eligible player there and every roll
 * they make inside it has a small chance to turn up a secret: a drop that
 * exists nowhere else and goes into the Secret Index rather than the
 * inventory. Each secret found is a little permanent Luck, read by
 * StatSources like every other source, so nothing is stored but the set
 * of names.
 *
 * Where it is comes from /rngadmin realm here and lives in realm.yml, next
 * to when it opens next, so a restart does not reset the clock. The world
 * is kept as a name and looked up when needed, because a Multiverse world
 * loads after the plugin does.
 *
 * Everybody sent there is sent back to where they came from when it
 * closes, when they quit, and when the plugin is reloaded.
 */
public class RealmManager implements Listener {

    private final SolRNGPlugin plugin;
    private final File file;

    // Config
    private boolean enabled;
    private int minPrestige;
    private long openMillis;
    private long minGapMillis;
    private long maxGapMillis;
    private int findOneIn;
    private double luckPerSecret;
    private double radius;
    private final Map<String, Secret> secrets = new LinkedHashMap<>();

    // Where it is. The world stays a name until it is needed.
    private String worldName;
    private double x, y, z;
    private float yaw, pitch;

    // When
    private long nextOpenAt;
    private long openUntil;
    private boolean open;

    private final Map<UUID, Location> returns = new HashMap<>();
    private KeyedBossBar bar;
    private BukkitTask task;

    /**
     * V291: a secret has its own odds, 1 in oneIn per roll inside, which
     * Luck does not touch, and the Luck multiplier it gives when picked in
     * /secretindex. That pick replaced the equipped drop's Tag Luck.
     */
    public record Secret(String id, String display, List<String> colors, Material icon,
                         double weight, String hint, long oneIn, double multiplier) {
        public String[] stops() {
            return colors.toArray(new String[0]);
        }
    }

    /** One-in and multiplier per secret, for a live config without them (V291). */
    private static final Map<String, double[]> DEFAULTS = Map.of(
            "quiet_comet", new double[]{1_000, 1.5},
            "veiled_nebula", new double[]{2_500, 2.0},
            "hollow_moon", new double[]{5_000, 2.5},
            "starless_key", new double[]{10_000, 3.0},
            "eclipse_shard", new double[]{25_000, 4.0},
            "lost_constellation", new double[]{50_000, 5.0},
            "whisper_of_the_void", new double[]{100_000, 7.0},
            "origin_spark", new double[]{250_000, 10.0});

    public RealmManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "realm.yml");
    }

    public void load() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("secret-realm.enabled", true);
        minPrestige = Math.max(0, config.getInt("secret-realm.min-prestige", 0));
        openMillis = Math.max(30L, config.getLong("secret-realm.open-seconds", 900L)) * 1000L;
        // Never less than two hours apart (V291, Leon's call).
        minGapMillis = Math.max(120L, config.getLong("secret-realm.min-gap-minutes", 120L)) * 60_000L;
        maxGapMillis = Math.max(minGapMillis, config.getLong("secret-realm.max-gap-minutes", 240L) * 60_000L);
        findOneIn = Math.max(1, config.getInt("secret-realm.find-one-in", 40));
        luckPerSecret = Math.max(0.0, config.getDouble("secret-realm.luck-per-secret", 0.02));
        radius = Math.max(4.0, config.getDouble("secret-realm.radius", 64.0));

        secrets.clear();
        ConfigurationSection section = config.getConfigurationSection("secret-realm.secrets");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) continue;
                Material icon = Material.matchMaterial(s.getString("icon", "AMETHYST_SHARD"));
                List<String> colors = s.getStringList("colors");
                double[] fallback = DEFAULTS.getOrDefault(id, new double[]{10_000, 2.0});
                secrets.put(id, new Secret(id, s.getString("display", id),
                        colors.isEmpty() ? List.of("#B388FF", "#40C4FF") : colors,
                        icon == null ? Material.AMETHYST_SHARD : icon,
                        Math.max(0.0, s.getDouble("weight", 1.0)), s.getString("hint", ""),
                        Math.max(1L, s.getLong("one-in", (long) fallback[0])),
                        Math.max(1.0, s.getDouble("multiplier", fallback[1]))));
            }
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        worldName = yml.getString("spot.world", null);
        x = yml.getDouble("spot.x");
        y = yml.getDouble("spot.y");
        z = yml.getDouble("spot.z");
        yaw = (float) yml.getDouble("spot.yaw");
        pitch = (float) yml.getDouble("spot.pitch");
        nextOpenAt = yml.getLong("next-open-at", 0L);
        // V329: a radius marked out in game beats the config default, so
        // the area Leon walked is kept across a reload.
        if (yml.contains("radius")) radius = Math.max(4.0, Math.min(512.0, yml.getDouble("radius")));
        if (nextOpenAt <= 0L) scheduleNext();
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        if (worldName != null) {
            yml.set("spot.world", worldName);
            yml.set("spot.x", x);
            yml.set("spot.y", y);
            yml.set("spot.z", z);
            yml.set("spot.yaw", (double) yaw);
            yml.set("spot.pitch", (double) pitch);
        }
        yml.set("next-open-at", nextOpenAt);
        yml.set("radius", radius);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save realm.yml (" + ex.getMessage() + ").");
        }
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    /** On the way down: everybody back where they came from, nothing announced. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sendEverybodyBack();
        removeBar();
        // A reload mid opening moves the clock on, so the new instance does
        // not open it again the moment it starts.
        if (open) scheduleNext();
        open = false;
    }

    // ---------------------------------------------------------------
    // The clock
    // ---------------------------------------------------------------

    private void tick() {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        if (!open && now >= nextOpenAt && spot() != null) {
            open();
        } else if (open && now >= openUntil) {
            close();
        } else if (open) {
            updateBar(now);
        }
    }

    private void scheduleNext() {
        long gap = minGapMillis + (long) (ThreadLocalRandom.current().nextDouble() * (maxGapMillis - minGapMillis));
        nextOpenAt = System.currentTimeMillis() + gap;
        save();
    }

    public void open() {
        if (spot() == null) return;
        open = true;
        openUntil = System.currentTimeMillis() + openMillis;
        long minutes = Math.max(1L, openMillis / 60_000L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            boolean allowed = data.getPrestige() >= minPrestige;
            player.sendMessage("");
            player.sendMessage(Lore.gradient("THE SECRET REALM HAS OPENED", true, "#B388FF", "#40C4FF"));
            if (allowed) {
                player.sendMessage(ChatColor.GRAY + "For " + minutes + " minutes. Type "
                        + ChatColor.LIGHT_PURPLE + "/realm" + ChatColor.GRAY + " to go through.");
                player.sendTitle(Lore.gradient("Secret Realm", true, "#B388FF", "#40C4FF"),
                        ChatColor.GRAY + "/realm for " + minutes + " minutes", 10, 60, 20);
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.4f);
            } else {
                player.sendMessage(ChatColor.GRAY + "Only players at Prestige " + minPrestige
                        + " and up can enter.");
            }
            player.sendMessage("");
        }
        updateBar(System.currentTimeMillis());
    }

    public void close() {
        if (!open) return;
        open = false;
        for (UUID uuid : new ArrayList<>(returns.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "The Secret Realm closes behind you.");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
            }
        }
        sendEverybodyBack();
        removeBar();
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "The Secret Realm has closed.");
        scheduleNext();
    }

    private void updateBar(long now) {
        long left = Math.max(0L, openUntil - now);
        if (bar == null) {
            bar = Bukkit.createBossBar(SolRNGPlugin.key("secret_realm"), "", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        }
        bar.setTitle(ChatColor.LIGHT_PURPLE + "Secret Realm " + ChatColor.GRAY + "open for "
                + ChatColor.WHITE + (left / 60_000L) + "m " + ((left / 1000L) % 60L) + "s"
                + ChatColor.DARK_GRAY + "  /realm");
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) left / openMillis)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean allowed = plugin.getPlayerDataManager().get(player.getUniqueId()).getPrestige() >= minPrestige;
            if (allowed && !bar.getPlayers().contains(player)) bar.addPlayer(player);
            else if (!allowed && bar.getPlayers().contains(player)) bar.removePlayer(player);
        }
    }

    private void removeBar() {
        if (bar != null) {
            bar.removeAll();
            Bukkit.removeBossBar(bar.getKey());
            bar = null;
        }
    }

    // ---------------------------------------------------------------
    // Going in and out
    // ---------------------------------------------------------------

    /** /realm: in, when it is open and you are allowed. */
    public void enter(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "The Secret Realm is switched off.");
            return;
        }
        if (data.getPrestige() < minPrestige) {
            player.sendMessage(ChatColor.RED + "The Secret Realm opens to you at Prestige " + minPrestige + ".");
            return;
        }
        if (!open) {
            player.sendMessage(ChatColor.GRAY + "The Secret Realm is closed. It opens at random times, "
                    + "and everybody who can enter is told when.");
            return;
        }
        if (returns.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "You are already inside. " + ChatColor.LIGHT_PURPLE
                    + "/realm leave" + ChatColor.GRAY + " takes you back.");
            return;
        }
        Location spot = spot();
        if (spot == null) {
            player.sendMessage(ChatColor.RED + "The Secret Realm has no place yet.");
            return;
        }
        returns.put(player.getUniqueId(), player.getLocation());
        player.teleport(spot);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "You are in the Secret Realm. " + ChatColor.GRAY
                + "Roll here for a chance at a secret. " + ChatColor.LIGHT_PURPLE + "/secretindex"
                + ChatColor.GRAY + " shows what you have found.");
        player.playSound(spot, Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        wearSecret(player, data);
    }

    /** /realm leave, closing, quitting: back where they came from. */
    public void leave(Player player) {
        Location back = returns.remove(player.getUniqueId());
        if (back == null) {
            player.sendMessage(ChatColor.GRAY + "You are not in the Secret Realm.");
            return;
        }
        player.teleport(back);
        wearNormal(player);
    }

    /**
     * What a player wears inside (V329): their secret over their head and
     * the Luck it is paying, with no aura and no drop tag.
     *
     * The realm is the one place where the thing everybody is chasing is
     * the same thing, so it is the one place where the tag says what you
     * have found in HERE rather than what you rolled outside.
     */
    public void wearSecret(Player player, PlayerData data) {
        plugin.getAuraManager().hide(player.getUniqueId());
        Secret worn = wornSecret(data);
        if (worn == null) {
            // V337, Leon's call: nothing over somebody who has not found a
            // secret yet. "No secret yet" was a label where the point of
            // the realm is that there is nothing to show off.
            plugin.getTagManager().hideHologram(player);
            return;
        }
        plugin.getTagManager().showRealmTag(player, Lore.gradient(worn.display(), true, worn.stops()),
                ChatColor.GRAY + "Secret Luck " + ChatColor.WHITE + trim(multiplierFor(data)) + "x");
    }

    /** Their own tag and aura back, on the way out. */
    public void wearNormal(Player player) {
        plugin.getTagManager().refreshEquippedTag(player,
                plugin.getPlayerDataManager().get(player.getUniqueId()));
    }

    /** Everybody inside, redrawn: a new secret changes what they wear. */
    public void refreshWorn() {
        for (UUID id : new ArrayList<>(returns.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) wearSecret(player, plugin.getPlayerDataManager().get(id));
        }
    }

    private void sendEverybodyBack() {
        for (Map.Entry<UUID, Location> entry : new ArrayList<>(returns.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            player.teleport(entry.getValue());
            returns.remove(entry.getKey());
            // After the teleport AND after the map entry is gone, or
            // inside() still answers true and the aura stays hidden.
            wearNormal(player);
        }
        returns.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Location back = returns.remove(event.getPlayer().getUniqueId());
        if (back != null) event.getPlayer().teleport(back);
    }

    /** Inside means sent there by /realm, still in its world, and near the spot. */
    public boolean inside(Player player) {
        if (!open || !returns.containsKey(player.getUniqueId())) return false;
        Location spot = spot();
        if (spot == null || !player.getWorld().equals(spot.getWorld())) return false;
        return player.getLocation().distanceSquared(spot) <= radius * radius;
    }

    // ---------------------------------------------------------------
    // Secrets
    // ---------------------------------------------------------------

    /**
     * Called for every finished roll. Each secret is its own 1 in N, rarest
     * first, and the first that hits is found (V291). Luck plays no part;
     * the Secret Seeker prestige upgrade multiplies every chance.
     */
    public void onRoll(Player player, PlayerData data) {
        if (secrets.isEmpty() || !inside(player)) return;
        double boost = plugin.getPrestigeManager().upgradeMultiplier(data,
                com.spacerng.solrng.player.PrestigeUpgrade.Effect.SECRET_CHANCE);
        Secret found = null;
        List<Secret> rarestFirst = new ArrayList<>(secrets.values());
        rarestFirst.sort((a, b) -> Long.compare(b.oneIn(), a.oneIn()));
        for (Secret secret : rarestFirst) {
            if (ThreadLocalRandom.current().nextDouble() < boost / secret.oneIn()) {
                found = secret;
                break;
            }
        }
        if (found == null) return;
        String name = Lore.gradient(found.display(), true, found.stops());
        if (data.getSecretsFound().add(found.id())) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(Lore.gradient("SECRET", true, "#B388FF", "#40C4FF") + ChatColor.GRAY + " "
                        + player.getName() + " found " + ChatColor.RESET + name
                        + ChatColor.GRAY + " in the Secret Realm.");
            }
            player.sendTitle(name, ChatColor.GRAY + "a new secret, " + ChatColor.GREEN
                    + (secretLuck() ? trim(found.multiplier()) + "x Luck" + ChatColor.GRAY + " in /secretindex"
                            : "+" + trim(luckPerSecret * 100.0) + "% Luck"), 5, 50, 15);
            // The first one found is picked straight away.
            if (data.getSelectedSecret() == null) data.setSelectedSecret(found.id());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
            // V329: the grey reveal, and the tag over their head catches
            // up with the secret they are now carrying.
            SecretFx.reveal(plugin, player);
            wearSecret(player, data);
        } else {
            player.sendMessage(ChatColor.DARK_GRAY + "You found " + ChatColor.RESET + name
                    + ChatColor.DARK_GRAY + " again. It is already in your Secret Index.");
        }
    }

    private Secret pick() {
        double total = 0.0;
        for (Secret secret : secrets.values()) total += secret.weight();
        if (total <= 0.0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        Secret last = null;
        for (Secret secret : secrets.values()) {
            last = secret;
            roll -= secret.weight();
            if (roll <= 0.0) return secret;
        }
        return last;
    }

    /** The chance that one find is this secret, for the index. */
    public double shareOf(Secret secret) {
        double total = 0.0;
        for (Secret each : secrets.values()) total += each.weight();
        return total <= 0.0 ? 0.0 : secret.weight() / total;
    }

    /**
     * Secrets ARE the index Luck, always (V337).
     *
     * V292 put this behind secret-realm.secret-luck so the equipped drop
     * could keep paying Tag Luck until Leon was ready. He is: "i want the
     * multi to be your best from secret index not normal index". One
     * truth is better than a switch nobody will ever turn back, so the
     * method stays for the labels that read it and always answers true.
     */
    public boolean secretLuck() {
        return true;
    }

    /**
     * Dead since V337: the flat pile paid only while the multiplier system
     * was off, and it never is. Kept because secret-realm.luck-per-secret
     * is still in config and somebody reading it should find the one place
     * that used to spend it.
     */
    public double luckFor(PlayerData data) {
        if (secretLuck()) return 0.0;
        int count = 0;
        for (String id : data.getSecretsFound()) if (secrets.containsKey(id)) count++;
        return count * luckPerSecret;
    }

    /**
     * The Luck multiplier of the BEST secret found, 1 with none (V337).
     *
     * It used to be the one picked in /secretindex, so a player who found
     * something rare and never opened the menu was paid nothing for it.
     * Leon's call: it applies on its own. Picking one only decides which
     * secret you wear over your head inside the realm.
     */
    public double multiplierFor(PlayerData data) {
        Secret best = bestSecret(data);
        return best == null ? 1.0 : best.multiplier();
    }

    /** The found secret paying the most Luck, or null with none found. */
    public Secret bestSecret(PlayerData data) {
        Secret best = null;
        for (String id : data.getSecretsFound()) {
            Secret secret = secrets.get(id);
            if (secret == null) continue;
            if (best == null || secret.multiplier() > best.multiplier()) best = secret;
        }
        return best;
    }

    /**
     * The secret worn over the head in the realm: the one picked, or the
     * best found while nothing is picked, or null with none found.
     */
    public Secret wornSecret(PlayerData data) {
        String id = data.getSelectedSecret();
        if (id != null && data.getSecretsFound().contains(id)) {
            Secret picked = secrets.get(id);
            if (picked != null) return picked;
        }
        return bestSecret(data);
    }

    /** The chance per roll inside of this secret for this player, with Secret Seeker. */
    public double chanceFor(PlayerData data, Secret secret) {
        double boost = plugin.getPrestigeManager().upgradeMultiplier(data,
                com.spacerng.solrng.player.PrestigeUpgrade.Effect.SECRET_CHANCE);
        return Math.min(1.0, boost / secret.oneIn());
    }

    // ---------------------------------------------------------------
    // Admin and reading
    // ---------------------------------------------------------------

    /** How far the realm reaches from its spot, in blocks. */
    public double radius() {
        return radius;
    }

    /**
     * Sets how big the realm is and saves it (V329). It lives in
     * realm.yml next to the spot rather than only in config, so Leon can
     * mark out the area in game and a reload keeps it.
     */
    public void setRadius(double blocks) {
        radius = Math.max(4.0, Math.min(512.0, blocks));
        save();
    }

    public void setSpot(Location location) {
        worldName = location.getWorld().getName();
        x = location.getX();
        y = location.getY();
        z = location.getZ();
        yaw = location.getYaw();
        pitch = location.getPitch();
        save();
    }

    public Location spot() {
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z, yaw, pitch);
    }

    public String spotText() {
        return worldName == null ? "not set" : worldName + " " + (int) x + " " + (int) y + " " + (int) z
                + (Bukkit.getWorld(worldName) == null ? " (world not loaded)" : "");
    }

    public boolean isOpen() { return open; }
    public boolean isEnabled() { return enabled; }

    /** /rngadmin realm on|off (V293): off closes it now and stops the clock. */
    public void setEnabled(boolean on) {
        plugin.getConfig().set("secret-realm.enabled", on);
        plugin.saveConfig();
        if (!on) close();
        if (on && !enabled) scheduleNext();
        enabled = on;
    }
    public int minPrestige() { return minPrestige; }
    public long nextOpenAt() { return nextOpenAt; }
    public long openUntil() { return openUntil; }
    public int findOneIn() { return findOneIn; }
    public double luckPerSecret() { return luckPerSecret; }
    public Map<String, Secret> secrets() { return secrets; }

    private static String trim(double value) {
        String text = String.format("%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
