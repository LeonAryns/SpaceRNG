package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The shared farm, seen differently by everybody standing on it.
 *
 * A "farm plot" is a real block in the world that every player is shown a
 * DIFFERENT crop at, using per-player block changes. The world block never
 * changes — it stays the marker crop forever, breaks against it are
 * cancelled, and block physics on it are suppressed — so the field can't
 * drift out of sync, can't be griefed into a hole, and doesn't need a
 * schematic paste to reset.
 *
 * That means the same physical field can be wheat for a new player and
 * nether wart for someone deep in the skill tree at the same moment, and
 * neither of them sees the other's harvest.
 *
 * Harvest state is per-player too: breaking a plot hides it for that
 * player alone and schedules its return.
 */
public class FarmPlotManager {

    /**
     * What the plot actually is server-side. Wheat is deliberate: it's
     * non-solid, so players walk through the field the way they expect,
     * and it's breakable, so BlockBreakEvent fires and can be intercepted.
     * A solid marker would block movement; a barrier can't be broken at
     * all, so no harvest event would ever arrive.
     */
    private static final Material MARKER = Material.WHEAT;

    private final SolRNGPlugin plugin;
    private final NamespacedKey plotItemKey;
    private final File plotFile;

    private final Map<String, CropType> crops = new LinkedHashMap<>();
    private final Set<Location> plots = new HashSet<>();
    // Per player, the plots they've harvested and the tick they come back.
    private final Map<UUID, Map<Location, Long>> harvested = new HashMap<>();

    private int regrowTicks = 60;
    private String shardsNode = "";

    public FarmPlotManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.plotItemKey = new NamespacedKey(plugin, "solrng_farm_plot");
        this.plotFile = new File(plugin.getDataFolder(), "farmplots.yml");
    }

    // ------------------------------------------------------------- config

    public void load(FileConfiguration config) {
        crops.clear();
        regrowTicks = Math.max(1, config.getInt("farming.regrow-seconds", 3)) * 20;
        shardsNode = config.getString("farming.shards-node", "");

        ConfigurationSection section = config.getConfigurationSection("farming.crop-types");
        if (section != null) {
            int order = 0;
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) continue;
                Material material = Material.matchMaterial(c.getString("material", id));
                if (material == null) {
                    plugin.getLogger().warning("[SolRNG] Unknown crop material for '" + id + "'.");
                    continue;
                }
                crops.put(id.toUpperCase(), new CropType(id.toUpperCase(),
                        c.getString("display", id),
                        material,
                        c.getLong("tokens", 1L),
                        c.getLong("shards", 0L),
                        c.getString("requires-node", ""),
                        order++));
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded " + crops.size() + " farm crop types.");
        loadPlots();
    }

    public Map<String, CropType> getCrops() {
        return crops;
    }

    public CropType getCrop(String id) {
        return id == null ? null : crops.get(id.toUpperCase());
    }

    /** The crop a player is currently growing, falling back to the first configured one. */
    public CropType cropFor(PlayerData data) {
        CropType crop = getCrop(data.getSelectedCrop());
        if (crop != null) return crop;
        return crops.isEmpty() ? null : crops.values().iterator().next();
    }

    /**
     * Free crops are always available; the rest need their skill node, or
     * an admin grant via /rngadmin crops.
     */
    public boolean isUnlocked(PlayerData data, CropType crop) {
        if (crop.isFree()) return true;
        if (data.hasUnlockedCrop(crop.getId())) return true;
        return data.hasUnlocked(crop.getRequiresNode());
    }

    /** Shards only pay once the farming shard node (or an admin grant) says so. */
    public boolean shardsUnlocked(PlayerData data) {
        if (data.isCropShardsUnlocked()) return true;
        return !shardsNode.isEmpty() && data.hasUnlocked(shardsNode);
    }

    public int getRegrowTicks() {
        return regrowTicks;
    }

    // -------------------------------------------------------------- plots

    public boolean isPlot(Location location) {
        return plots.contains(normalise(location));
    }

    public int plotCount() {
        return plots.size();
    }

    /** Registers a block as part of the farm and makes it the marker crop. */
    public void addPlot(Block block) {
        Location key = normalise(block.getLocation());
        plots.add(key);

        block.setType(MARKER, false);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable, false);
        }
        savePlots();
    }

    public boolean removePlot(Location location) {
        Location key = normalise(location);
        if (!plots.remove(key)) return false;
        savePlots();
        if (key.getWorld() != null) {
            key.getBlock().setType(Material.AIR, false);
        }
        return true;
    }

    private Location normalise(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    // ------------------------------------------------------------ display

    /**
     * Shows this player their own version of every plot near them. Called
     * on join, on a crop change, and on a repeating task so plots appear
     * as they walk into range.
     */
    public void render(Player player) {
        if (plots.isEmpty()) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        CropType crop = cropFor(data);
        if (crop == null) return;

        BlockData grown = grownData(crop.getMaterial());
        BlockData air = Bukkit.createBlockData(Material.AIR);
        Map<Location, Long> mine = harvested.get(player.getUniqueId());
        long now = player.getWorld().getFullTime();
        World world = player.getWorld();
        double rangeSq = 64 * 64;

        for (Location plot : plots) {
            if (plot.getWorld() == null || !plot.getWorld().equals(world)) continue;
            if (plot.distanceSquared(player.getLocation()) > rangeSq) continue;

            boolean gone = mine != null && mine.getOrDefault(plot, 0L) > now;
            player.sendBlockChange(plot, gone ? air : grown);
        }
    }

    public void renderAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            render(player);
        }
    }

    private BlockData grownData(Material material) {
        BlockData data = Bukkit.createBlockData(material);
        if (data instanceof Ageable ageable) {
            ageable.setAge(ageable.getMaximumAge());
        }
        return data;
    }

    // ----------------------------------------------------------- harvest

    /**
     * Pays out one harvest and hides that plot from this player until it
     * regrows. Returns false if it's already been taken.
     */
    public boolean harvest(Player player, Location location) {
        Location plot = normalise(location);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        CropType crop = cropFor(data);
        if (crop == null) return false;

        Map<Location, Long> mine = harvested.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        long now = player.getWorld().getFullTime();
        if (mine.getOrDefault(plot, 0L) > now) return false; // still regrowing for them

        mine.put(plot, now + regrowTicks);

        long tokens = Math.round(crop.getTokens() * data.getFarmTokenMultiplier());
        long shards = shardsUnlocked(data) ? crop.getShards() : 0L;
        if (tokens > 0) data.addTokens(tokens);
        if (shards > 0) data.addShards(shards);
        data.addCropsHarvested(1L);

        player.sendBlockChange(plot, Bukkit.createBlockData(Material.AIR));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plots.contains(plot)) {
                player.sendBlockChange(plot, grownData(crop.getMaterial()));
            }
        }, regrowTicks);

        String reward = ChatColor.YELLOW + "+" + String.format("%,d", tokens) + " Tokens";
        if (shards > 0) {
            reward += ChatColor.GRAY + "  " + ChatColor.AQUA + "+" + String.format("%,d", shards) + " Shards";
        }
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(reward));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.6f);
        plugin.getScoreboardManager().update(player);
        return true;
    }

    public void forget(UUID uuid) {
        harvested.remove(uuid);
    }

    // ----------------------------------------------------------- the item

    /** The placeable block an admin puts down to build the farm. */
    public ItemStack createPlotItem(int amount) {
        ItemStack item = new ItemStack(Material.HAY_BLOCK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Farm Plot");
        meta.setLore(List.of(
                ChatColor.GRAY + "Place to add a tile to the shared farm.",
                ChatColor.GRAY + "Every player sees their own crop here.",
                "",
                ChatColor.DARK_GRAY + "Admin tool"));
        meta.getPersistentDataContainer().set(plotItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPlotItem(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(plotItemKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------ storage

    private void loadPlots() {
        plots.clear();
        if (!plotFile.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(plotFile);
        for (String raw : yml.getStringList("plots")) {
            String[] parts = raw.split(";");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            try {
                plots.add(new Location(world,
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        plugin.getLogger().info("[SolRNG] Loaded " + plots.size() + " farm plots.");
    }

    public void savePlots() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> raw = new ArrayList<>();
        for (Location plot : plots) {
            if (plot.getWorld() == null) continue;
            raw.add(plot.getWorld().getName() + ";" + plot.getBlockX() + ";" + plot.getBlockY() + ";" + plot.getBlockZ());
        }
        yml.set("plots", raw);
        try {
            yml.save(plotFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[SolRNG] Couldn't save farm plots: " + ex.getMessage());
        }
    }
}
