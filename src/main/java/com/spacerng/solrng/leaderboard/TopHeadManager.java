package com.spacerng.solrng.leaderboard;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.RollFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Floating, spinning player heads for the top of a leaderboard.
 *
 * Each spot is a board and a rank pinned to a location. The plugin draws a
 * player head there as an ItemDisplay with the name and score floating
 * under it, and swaps the face whenever somebody new takes the rank.
 * FancyHolograms can show a skull, but it cannot change whose skull it is
 * when the board moves, which is the whole point of a podium.
 *
 * The entities are never saved to the world (setPersistent false). They
 * vanish with their chunk and are drawn again when it loads, so a crash or
 * a removed plugin can never leave orphaned heads behind. Anything tagged
 * from an earlier run is swept on start anyway.
 *
 * The spin is cheap on purpose: one transformation update every ten ticks
 * with client-side interpolation doing the smoothing, rather than a
 * teleport every tick.
 */
public class TopHeadManager {

    public record Spot(String board, int rank, Location at) {
        public String id() {
            return board + ":" + rank;
        }
    }

    private static final int FRAME_TICKS = 10;

    private final SolRNGPlugin plugin;
    private final File file;
    private final NamespacedKey tagKey;
    private final Map<String, Spot> spots = new LinkedHashMap<>();
    private final List<String> unresolved = new ArrayList<>();
    private final Map<String, ItemDisplay> heads = new HashMap<>();
    private final Map<String, TextDisplay> labels = new HashMap<>();
    private final Map<String, UUID> showing = new HashMap<>();
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> resolving = ConcurrentHashMap.newKeySet();

    private int refreshTicks = 600;
    private double spinDegreesPerSecond = 90.0;
    private double bobHeight = 0.12;
    private float scale = 1.5f;
    private double labelDrop = 1.0;
    private boolean showLabel = true;
    private boolean sparkle = true;
    private float viewRange = 1.0f;

    private BukkitTask task;
    private long ticks;
    private long nextRefresh;
    private boolean warned;

    public TopHeadManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "topheads.yml");
        this.tagKey = SolRNGPlugin.key("tophead");
        loadSpots();
    }

    public void load(FileConfiguration config) {
        refreshTicks = Math.max(20, config.getInt("top-heads.refresh-seconds", 30) * 20);
        // Past about 300 degrees a frame, interpolation takes the short way
        // round and the head visibly spins backwards.
        spinDegreesPerSecond = Math.max(0.0, Math.min(300.0,
                config.getDouble("top-heads.spin-degrees-per-second", 90.0)));
        bobHeight = config.getDouble("top-heads.bob-height", 0.12);
        scale = (float) config.getDouble("top-heads.scale", 1.5);
        labelDrop = config.getDouble("top-heads.label-drop", 1.0);
        showLabel = config.getBoolean("top-heads.show-label", true);
        sparkle = config.getBoolean("top-heads.sparkle-first", true);
        viewRange = (float) config.getDouble("top-heads.view-range", 1.0);

        // New settings only reach entities spawned after them, so take the
        // current ones down and let the next frame draw them fresh.
        despawnAll();
        nextRefresh = 0;
    }

    // ---------------------------------------------------------- lifecycle

    public void start() {
        if (task != null) task.cancel();
        sweep();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::frame, 40L, FRAME_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        despawnAll();
    }

    /** Removes anything tagged as a top head, from this run or an earlier one. */
    private void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(ItemDisplay.class, TextDisplay.class)) {
                if (entity.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING)) entity.remove();
            }
        }
    }

    // --------------------------------------------------------------- spots

    public static boolean isBoard(String board) {
        return LeaderboardManager.BOARDS.contains(board.toLowerCase(Locale.ROOT));
    }

    public void set(String board, int rank, Location at) {
        Spot spot = new Spot(board.toLowerCase(Locale.ROOT), rank,
                new Location(at.getWorld(), at.getX(), at.getY(), at.getZ()));
        despawn(spot.id());
        spots.put(spot.id(), spot);
        saveSpots();
        nextRefresh = 0;
    }

    /**
     * A whole podium in one go, laid out from where the admin is standing
     * and looking. #1 floats at eye height; #2 and #3 sit to either side
     * and a little lower, arranged so that people standing where the admin
     * is looking see #2 on their left and #3 on their right.
     */
    public void podium(String board, Location eye) {
        double yaw = Math.toRadians(eye.getYaw());
        // A player facing yaw 0 looks south, and their right hand points
        // west: (-cos, 0, -sin).
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        set(board, 1, eye.clone());
        set(board, 2, eye.clone().add(rightX * 1.8, -0.4, rightZ * 1.8));
        set(board, 3, eye.clone().add(-rightX * 1.8, -0.7, -rightZ * 1.8));
    }

    public boolean remove(String board, int rank) {
        String id = board.toLowerCase(Locale.ROOT) + ":" + rank;
        if (spots.remove(id) == null) return false;
        despawn(id);
        saveSpots();
        return true;
    }

    public int removeNear(Location at, double radius) {
        List<String> doomed = new ArrayList<>();
        for (Spot spot : spots.values()) {
            if (spot.at().getWorld() != null && spot.at().getWorld().equals(at.getWorld())
                    && spot.at().distanceSquared(at) <= radius * radius) {
                doomed.add(spot.id());
            }
        }
        for (String id : doomed) {
            spots.remove(id);
            despawn(id);
        }
        if (!doomed.isEmpty()) saveSpots();
        return doomed.size();
    }

    public Collection<Spot> list() {
        return spots.values();
    }

    private void loadSpots() {
        spots.clear();
        unresolved.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String line : yml.getStringList("spots")) {
            String[] parts = line.split(";");
            if (parts.length != 6) continue;
            World world = Bukkit.getWorld(parts[2]);
            if (world == null) {
                unresolved.add(line);
                continue;
            }
            try {
                Spot spot = new Spot(parts[0], Integer.parseInt(parts[1]), new Location(world,
                        Double.parseDouble(parts[3]), Double.parseDouble(parts[4]), Double.parseDouble(parts[5])));
                spots.put(spot.id(), spot);
            } catch (NumberFormatException ignored) {
                // A hand-edited line that doesn't parse is dropped, not fatal.
            }
        }
    }

    private void saveSpots() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> lines = new ArrayList<>(unresolved);
        for (Spot spot : spots.values()) {
            Location at = spot.at();
            lines.add(spot.board() + ";" + spot.rank() + ";" + at.getWorld().getName() + ";"
                    + String.format(Locale.ROOT, "%.3f;%.3f;%.3f", at.getX(), at.getY(), at.getZ()));
        }
        yml.set("spots", lines);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save topheads.yml: " + ex.getMessage());
        }
    }

    // --------------------------------------------------------------- frame

    private void frame() {
        try {
            ticks += FRAME_TICKS;
            boolean refresh = ticks >= nextRefresh;
            if (refresh) nextRefresh = ticks + refreshTicks;

            float angle = (float) Math.toRadians((ticks / 20.0 * spinDegreesPerSecond) % 360.0);
            // One gentle bob every four seconds.
            float bob = (float) (Math.sin(ticks / 20.0 * Math.PI / 2.0) * bobHeight);

            for (Spot spot : spots.values()) {
                Location at = spot.at();
                World world = at.getWorld();
                if (world == null || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;

                boolean fresh = false;
                ItemDisplay head = heads.get(spot.id());
                if (head == null || !head.isValid()) {
                    head = spawnHead(spot);
                    fresh = true;
                }
                if (showLabel) {
                    TextDisplay label = labels.get(spot.id());
                    if (label == null || !label.isValid()) {
                        spawnLabel(spot);
                        fresh = true;
                    }
                }

                animate(head, angle, bob);
                if (refresh || fresh) update(spot);
                if (sparkle && spot.rank() == 1) sparkle(at, angle);
            }
        } catch (Exception ex) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Top heads frame failed: " + ex.getMessage());
            }
        }
    }

    private void animate(ItemDisplay head, float angle, float bob) {
        head.setInterpolationDelay(0);
        head.setInterpolationDuration(FRAME_TICKS);
        head.setTransformation(new Transformation(
                new Vector3f(0f, bob, 0f),
                new Quaternionf().rotationY(angle),
                new Vector3f(scale, scale, scale),
                new Quaternionf()));
    }

    private ItemDisplay spawnHead(Spot spot) {
        clear(spot, ItemDisplay.class);
        ItemDisplay head = spot.at().getWorld().spawn(spot.at(), ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(viewRange);
            display.setItemStack(new ItemStack(Material.PLAYER_HEAD));
        });
        heads.put(spot.id(), head);
        showing.remove(spot.id());
        return head;
    }

    private void spawnLabel(Spot spot) {
        clear(spot, TextDisplay.class);
        Location at = spot.at().clone().subtract(0, labelDrop * scale / 1.5, 0);
        TextDisplay label = spot.at().getWorld().spawn(at, TextDisplay.class, text -> {
            text.setPersistent(false);
            text.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
            text.setBillboard(Display.Billboard.CENTER);
            text.setAlignment(TextDisplay.TextAlignment.CENTER);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setShadowed(true);
            text.setBrightness(new Display.Brightness(15, 15));
            text.setViewRange(viewRange);
            text.text(Component.text("#" + spot.rank()));
        });
        labels.put(spot.id(), label);
    }

    /** Removes strays of one kind for a spot, before drawing a fresh one. */
    private void clear(Spot spot, Class<? extends Entity> type) {
        for (Entity entity : spot.at().getWorld().getNearbyEntities(spot.at(), 2, 3, 2)) {
            if (!type.isInstance(entity)) continue;
            String tag = entity.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
            if (spot.id().equals(tag)) entity.remove();
        }
    }

    private void despawn(String id) {
        ItemDisplay head = heads.remove(id);
        if (head != null && head.isValid()) head.remove();
        TextDisplay label = labels.remove(id);
        if (label != null && label.isValid()) label.remove();
        showing.remove(id);
    }

    private void despawnAll() {
        for (String id : new ArrayList<>(heads.keySet())) despawn(id);
        for (String id : new ArrayList<>(labels.keySet())) despawn(id);
    }

    // ---------------------------------------------------------------- data

    private void update(Spot spot) {
        List<LeaderboardManager.Entry> rows = plugin.getLeaderboardManager().top(spot.board(), spot.rank());
        LeaderboardManager.Entry entry = rows.size() >= spot.rank() ? rows.get(spot.rank() - 1) : null;
        UUID owner = entry == null ? null : entry.uuid();

        ItemDisplay head = heads.get(spot.id());
        if (head != null && head.isValid()
                && (!showing.containsKey(spot.id()) || !Objects.equals(owner, showing.get(spot.id())))) {
            head.setItemStack(headFor(entry));
            showing.put(spot.id(), owner);
        }
        TextDisplay label = labels.get(spot.id());
        if (label != null && label.isValid()) label.text(labelFor(spot, entry));
    }

    private ItemStack headFor(LeaderboardManager.Entry entry) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (entry == null) return item;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = profiles.get(entry.uuid());
        if (profile == null) {
            Player online = Bukkit.getPlayer(entry.uuid());
            if (online != null && online.getPlayerProfile().hasTextures()) {
                profile = online.getPlayerProfile();
                profiles.put(entry.uuid(), profile);
            }
        }
        if (profile != null) {
            meta.setPlayerProfile(profile);
        } else {
            // A bare profile draws a default face until the real one is
            // fetched, which happens off the main thread.
            meta.setPlayerProfile(Bukkit.createProfile(entry.uuid(), entry.name()));
            resolve(entry.uuid(), entry.name());
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Fetches a skin once, asynchronously, then repaints every spot showing that player. */
    private void resolve(UUID uuid, String name) {
        if (!resolving.add(uuid)) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile profile = Bukkit.createProfile(uuid, name);
            boolean complete;
            try {
                complete = profile.complete(true) && profile.hasTextures();
            } catch (Exception ex) {
                complete = false;
            }
            if (complete) profiles.put(uuid, profile);
            if (!plugin.isEnabled()) return;
            final boolean found = complete;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Cleared either way: a failed lookup is retried on the next
                // refresh rather than never.
                resolving.remove(uuid);
                if (!found) return;
                for (Spot spot : spots.values()) {
                    if (uuid.equals(showing.get(spot.id()))) {
                        showing.remove(spot.id());
                        update(spot);
                    }
                }
            });
        });
    }

    private static Component labelFor(Spot spot, LeaderboardManager.Entry entry) {
        TextColor rankColour = switch (spot.rank()) {
            case 1 -> TextColor.color(0xFFC83D);
            case 2 -> TextColor.color(0xC9D3DD);
            case 3 -> TextColor.color(0xD08A4B);
            default -> NamedTextColor.AQUA;
        };
        Component rank = Component.text("#" + spot.rank() + " ", rankColour, TextDecoration.BOLD);
        if (entry == null) {
            return Component.text()
                    .append(rank)
                    .append(Component.text("Nobody yet", NamedTextColor.DARK_GRAY))
                    .build();
        }
        long value = LeaderboardManager.valueOf(spot.board(), entry);
        return Component.text()
                .append(rank)
                .append(Component.text(entry.name() == null ? "?" : entry.name(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(RollFormat.abbreviate(value) + " "
                        + LeaderboardManager.unitOf(spot.board()), NamedTextColor.GRAY))
                .build();
    }

    /** Two motes orbiting the leader. Sent per viewer, never to the world. */
    private void sparkle(Location at, float angle) {
        World world = at.getWorld();
        double orbit = 0.45 * scale;
        double a = angle * 2.0;
        Location one = at.clone().add(Math.cos(a) * orbit, 0.1, Math.sin(a) * orbit);
        Location two = at.clone().add(-Math.cos(a) * orbit, 0.1, -Math.sin(a) * orbit);
        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) > 32 * 32) continue;
            viewer.spawnParticle(Particle.WAX_ON, one, 1, 0, 0, 0, 0);
            viewer.spawnParticle(Particle.WAX_ON, two, 1, 0, 0, 0, 0);
        }
    }
}
