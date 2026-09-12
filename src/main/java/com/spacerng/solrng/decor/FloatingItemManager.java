package com.spacerng.solrng.decor;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Slowly-rotating item displays anchored around spawn - the "hero"
 * decoration used to mark a shop, a portal, a lootbox stand. Each spot
 * carries an item that spins around its own Y axis with a gentle bob,
 * and an optional label floating above it.
 *
 * Stored in {@code floatingitems.yml} rather than in {@code config.yml}
 * so admins can add and remove them at will without a config reload
 * fight, exactly the way crates and floating heads work.
 */
public class FloatingItemManager {

    private static final int FRAME_TICKS = 2;
    private static final float ANGLE_PER_FRAME = (float) (Math.PI / 90);
    private static final int BOB_PERIOD_TICKS = 120;
    private static final float BOB_AMPLITUDE = 0.15f;
    private static final float SCALE = 1.2f;
    private static final float VIEW_RANGE = 4.0f;

    /** One placement: an id, where it goes, the item, and an optional label. */
    public record Spot(String id, Location at, Material material, String label) { }

    private final SolRNGPlugin plugin;
    private final File file;
    private final NamespacedKey tagKey;
    private final Map<String, Spot> spots = new LinkedHashMap<>();
    private final Map<String, ItemDisplay> items = new HashMap<>();
    private final Map<String, TextDisplay> labels = new HashMap<>();
    private int frame = 0;
    private int taskId = -1;

    public FloatingItemManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "floatingitems.yml");
        this.tagKey = SolRNGPlugin.key("floating_item_id");
    }

    /**
     * Reads the placement file, sweeps up any old display entities the
     * previous run left behind, and starts the animation timer.
     */
    public void load(FileConfiguration ignored) {
        despawnAll();
        spots.clear();

        if (!file.exists()) {
            plugin.saveResource("floatingitems.yml", false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("spots");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) continue;
                Spot spot = readSpot(id, s);
                if (spot != null) spots.put(id, spot);
            }
        }
        plugin.getLogger().info("Loaded " + spots.size() + " floating item(s).");
    }

    private Spot readSpot(String id, ConfigurationSection s) {
        String worldName = s.getString("world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Floating item '" + id + "' points at missing world " + worldName);
            return null;
        }
        Location at = new Location(world,
                s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
        Material material;
        try {
            material = Material.valueOf(s.getString("material", "DIAMOND").toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Floating item '" + id + "' has bad material: "
                    + s.getString("material"));
            return null;
        }
        String label = s.getString("label", "");
        return new Spot(id, at, material, label);
    }

    /** Starts the tick. Called from onEnable after the config load. */
    public void start() {
        if (taskId != -1) return;
        // Sweep any strays the crash-safe way: entities in loaded chunks
        // near each spot that carry our tag are ours - remove and respawn.
        for (Spot spot : spots.values()) sweep(spot);
        for (Spot spot : spots.values()) spawn(spot);

        taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick,
                FRAME_TICKS, FRAME_TICKS).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        despawnAll();
    }

    private void tick() {
        frame++;
        float angle = ANGLE_PER_FRAME * frame;
        double t = 2.0 * Math.PI * (frame % BOB_PERIOD_TICKS) / BOB_PERIOD_TICKS;
        float bob = (float) (Math.sin(t) * BOB_AMPLITUDE);

        for (Map.Entry<String, ItemDisplay> entry : items.entrySet()) {
            ItemDisplay display = entry.getValue();
            if (display == null || !display.isValid()) continue;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(FRAME_TICKS);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, bob, 0f),
                    new Quaternionf().rotationY(angle),
                    new Vector3f(SCALE, SCALE, SCALE),
                    new Quaternionf()));
        }
    }

    private void spawn(Spot spot) {
        ItemDisplay display = spot.at().getWorld().spawn(spot.at(),
                ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setViewRange(VIEW_RANGE);
                    d.setItemStack(new ItemStack(spot.material()));
                });
        items.put(spot.id(), display);

        if (spot.label() != null && !spot.label().isEmpty()) {
            Location labelAt = spot.at().clone().add(0, 1.0, 0);
            TextDisplay text = spot.at().getWorld().spawn(labelAt, TextDisplay.class, td -> {
                td.setPersistent(false);
                td.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setShadowed(true);
                td.setBrightness(new Display.Brightness(15, 15));
                td.setViewRange(VIEW_RANGE);
                td.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(
                                org.bukkit.ChatColor.translateAlternateColorCodes('&', spot.label())));
            });
            labels.put(spot.id(), text);
        }
    }

    /** Removes any display entities near the spot that carry our tag. */
    private void sweep(Spot spot) {
        for (Entity entity : spot.at().getWorld().getNearbyEntities(spot.at(), 2, 3, 2)) {
            if (!(entity instanceof Display)) continue;
            String tag = entity.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
            if (spot.id().equals(tag)) entity.remove();
        }
    }

    private void despawn(String id) {
        ItemDisplay display = items.remove(id);
        if (display != null && display.isValid()) display.remove();
        TextDisplay text = labels.remove(id);
        if (text != null && text.isValid()) text.remove();
    }

    private void despawnAll() {
        for (String id : new ArrayList<>(items.keySet())) despawn(id);
        for (String id : new ArrayList<>(labels.keySet())) despawn(id);
    }

    // ------------------------------------------------------------ admin API

    /** Adds a spot at the location, saves and respawns. */
    public boolean add(String id, Location at, Material material, String label) {
        if (spots.containsKey(id)) return false;
        Spot spot = new Spot(id, at.clone(), material, label);
        spots.put(id, spot);
        sweep(spot);
        spawn(spot);
        save();
        return true;
    }

    /** Removes a spot by id. */
    public boolean remove(String id) {
        Spot spot = spots.remove(id);
        if (spot == null) return false;
        despawn(id);
        save();
        return true;
    }

    public Map<String, Spot> getSpots() {
        return spots;
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Spot spot : spots.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("world", spot.at().getWorld().getName());
            row.put("x", spot.at().getX());
            row.put("y", spot.at().getY());
            row.put("z", spot.at().getZ());
            row.put("material", spot.material().name());
            if (spot.label() != null && !spot.label().isEmpty()) row.put("label", spot.label());
            out.put(spot.id(), row);
        }
        yml.createSection("spots", out);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save floatingitems.yml: " + ex.getMessage());
        }
    }
}
