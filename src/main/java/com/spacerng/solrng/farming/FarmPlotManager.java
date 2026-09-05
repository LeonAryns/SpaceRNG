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
import java.util.concurrent.ThreadLocalRandom;

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
     * What the plot actually is server-side.
     *
     * It has to be non-solid, so players walk through the field the way
     * they expect, and breakable, so BlockBreakEvent fires and can be
     * intercepted. A solid marker would block movement; a barrier can't be
     * broken at all, so no harvest event would ever arrive.
     *
     * Torchflower crop on top of that because it is a block nobody builds
     * with. That matters for one reason: it makes the WORLD a usable
     * record of where the farm is. farmplots.yml is only a cache, and
     * /rngadmin farmscan can rebuild it by looking for this block — so
     * losing the plugin's data folder costs one command, not the field.
     * Wheat could never work that way; every wheat block on the server
     * would look like a plot.
     */
    private static final Material MARKER = Material.TORCHFLOWER_CROP;

    /**
     * What plots used to be. A scan accepts these too, on request, so a
     * field built before the marker changed can still be recovered.
     */
    private static final Material LEGACY_MARKER = Material.WHEAT;

    /** Momentum stops compounding here, so a long session can't run away. */
    private static final long MOMENTUM_CAP = 50L;

    private final SolRNGPlugin plugin;
    private final NamespacedKey plotItemKey;
    private final File plotFile;

    private final Map<String, CropType> crops = new LinkedHashMap<>();
    private final Set<Location> plots = new HashSet<>();
    // Per player, the plots they've harvested and the tick they come back.
    private final Map<UUID, Map<Location, Long>> harvested = new HashMap<>();
    // Momentum: how many harvests in the current unbroken run, and when the
    // last one landed. Kept in memory only — a streak is a session thing.
    private final Map<UUID, long[]> momentum = new HashMap<>(); // {streak, lastMillis}

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

    public Material markerMaterial() {
        return MARKER;
    }

    /**
     * Rebuilds the registry from the world: every marker block inside the
     * radius becomes a plot again.
     *
     * `includeLegacy` also picks up the old wheat marker, which is how a
     * field built before the marker changed gets recovered — at the cost
     * of catching any real wheat inside the box, so it's opt-in.
     *
     * Returns how many plots were newly registered.
     */
    public int scan(Location centre, int radius, boolean includeLegacy) {
        World world = centre.getWorld();
        if (world == null) return 0;

        int found = 0;
        int cx = centre.getBlockX();
        int cy = centre.getBlockY();
        int cz = centre.getBlockZ();
        int minY = Math.max(world.getMinHeight(), cy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + radius);

        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type != MARKER && !(includeLegacy && type == LEGACY_MARKER)) continue;
                    Location key = normalise(block.getLocation());
                    if (!plots.add(key)) continue;
                    if (type != MARKER) block.setType(MARKER, false);
                    found++;
                }
            }
        }
        if (found > 0) savePlots();
        return found;
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

            // Heal before drawing. Sending a crop for a plot whose real
            // block is gone paints a ghost the client believes in but the
            // server doesn't: it looks farmable and produces no break event
            // at all, so it can't be harvested OR removed. That's what made
            // plots "disappear" — they were still listed, still drawn, and
            // completely inert.
            if (!restore(plot)) continue;

            boolean gone = mine != null && mine.getOrDefault(plot, 0L) > now;
            player.sendBlockChange(plot, gone ? air : grown);
        }
    }

    /**
     * Puts the marker block back if something removed it. Returns false
     * only when the chunk isn't loaded, in which case there's nothing to
     * fix yet and nothing to draw either.
     *
     * Plots are a list of coordinates, not blocks, so the two can drift
     * apart — a piston, an explosion, world edit, a rollback, or the world
     * simply not having been saved. Re-asserting is cheaper and far more
     * robust than trying to intercept every way a block can die.
     */
    private boolean restore(Location plot) {
        World world = plot.getWorld();
        if (world == null) return false;
        if (!world.isChunkLoaded(plot.getBlockX() >> 4, plot.getBlockZ() >> 4)) return false;

        Block block = plot.getBlock();
        if (block.getType() == MARKER) return true;

        block.setType(MARKER, false);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable, false);
        }
        return true;
    }

    /**
     * Re-asserts every plot in a loaded chunk. Run once shortly after
     * startup so a field is whole again before anyone reaches it.
     */
    public int healAll() {
        int healed = 0;
        for (Location plot : plots) {
            World world = plot.getWorld();
            if (world == null) continue;
            if (!world.isChunkLoaded(plot.getBlockX() >> 4, plot.getBlockZ() >> 4)) continue;
            if (plot.getBlock().getType() == MARKER) continue;
            if (restore(plot)) healed++;
        }
        return healed;
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
        return harvest(player, location, true);
    }

    /**
     * Pays out one harvest and hides that plot from this player until it
     * regrows. Returns false if it's already been taken.
     *
     * {@code chain} is false for plots swept up by Blast Harvest, which
     * stops one explosion from triggering another and keeps the effect from
     * cascading across the whole field.
     */
    public boolean harvest(Player player, Location location, boolean chain) {
        Location plot = normalise(location);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        CropType crop = cropFor(data);
        if (crop == null) return false;

        Map<Location, Long> mine = harvested.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        long now = player.getWorld().getFullTime();
        if (mine.getOrDefault(plot, 0L) > now) return false; // still regrowing for them

        HoeEnchantManager hoe = plugin.getHoeEnchantManager();
        int regrow = regrowTicksFor(data);
        mine.put(plot, now + regrow);

        // Token Greed and Momentum both scale the base payout; Fortune
        // doubles whatever comes out of that.
        double multiplier = data.getFarmTokenMultiplier()
                + hoe.powerOf(data, "TOKEN_GREED")
                + momentumBonus(player, hoe, data, chain);

        // Same universal multiplier the Nova Core gives Luck and Money.
        multiplier *= plugin.getNovaCoreManager().multiplierAt(data.getNovaTier());
        // Token Master from the prestige upgrades rides on top.
        multiplier += plugin.getPrestigeManager().upgradeTotal(data,
                com.spacerng.solrng.player.PrestigeUpgrade.Effect.TOKEN_BONUS);
        // The general tree's Tokens skills multiply on top of everything
        // the farm tree already stacked, rather than adding into the same
        // pile — two trees feeding one number additively would make the
        // later, far more expensive nodes feel like nothing.
        multiplier *= plugin.getSkillTreeManager()
                .multiplierOf(data, com.spacerng.solrng.player.SkillNode.Effect.TOKEN_GAIN);
        // Per-crop yield skills stack on top, so specialising in one crop
        // is a real choice against raising every crop a little.
        double cropYield = plugin.getSkillTreeManager()
                .multiplierOf(data, com.spacerng.solrng.player.SkillNode.Effect.CROP_YIELD, crop.getId());
        multiplier *= cropYield;
        long tokens = Math.round(crop.getTokens() * multiplier);

        double gemMultiplier = plugin.getSkillTreeManager()
                .multiplierOf(data, com.spacerng.solrng.player.SkillNode.Effect.GEM_MULTIPLIER) * cropYield;
        long shards = shardsUnlocked(data)
                ? Math.round(crop.getShards() * gemMultiplier) : 0L;

        double shardGreed = hoe.powerOf(data, "SHARD_GREED")
                + plugin.getPrestigeManager().upgradeTotal(data,
                        com.spacerng.solrng.player.PrestigeUpgrade.Effect.SHARD_BONUS);
        if (shardsUnlocked(data) && shardGreed > 0 && ThreadLocalRandom.current().nextDouble() < shardGreed) {
            shards += 1;
        }

        boolean fortune = ThreadLocalRandom.current().nextDouble() < hoe.powerOf(data, "FORTUNE");
        if (fortune) {
            tokens *= 2;
            shards *= 2;
        }

        if (tokens > 0) data.addTokens(tokens);
        if (shards > 0) data.addShards(shards);
        data.addCropsHarvested(1L);
        plugin.getPassManager().awardHarvest(player, data, 1L);

        player.sendBlockChange(plot, Bukkit.createBlockData(Material.AIR));
        final CropType regrown = crop;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plots.contains(plot)) {
                player.sendBlockChange(plot, grownData(regrown.getMaterial()));
            }
        }, regrow);

        if (chain) {
            rollBonusEnchants(player, data, hoe, plot);
            announce(player, tokens, shards, fortune);
        }
        plugin.getScoreboardManager().update(player);
        return true;
    }

    /** Green Thumb shortens how long a plot stays gone for that player. */
    private int regrowTicksFor(PlayerData data) {
        double faster = plugin.getHoeEnchantManager().powerOf(data, "GREEN_THUMB");
        return (int) Math.max(10, Math.round(regrowTicks * (1.0 - Math.min(0.8, faster))));
    }

    /**
     * Momentum: an unbroken run of harvests builds a bonus that decays the
     * moment you stop. It rewards staying in the field rather than clicking
     * a plot every few minutes, which is the behaviour a farm wants.
     */
    private double momentumBonus(Player player, HoeEnchantManager hoe, PlayerData data, boolean chain) {
        double perStack = hoe.powerOf(data, "MOMENTUM");
        if (perStack <= 0) return 0.0;

        long[] state = momentum.computeIfAbsent(player.getUniqueId(), k -> new long[]{0L, 0L});
        long now = System.currentTimeMillis();
        if (chain) {
            // More than five seconds idle and the run is over.
            state[0] = (now - state[1] > 5_000L) ? 1L : Math.min(state[0] + 1L, MOMENTUM_CAP);
            state[1] = now;
        }
        return perStack * state[0];
    }

    /** Blast Harvest, Credit Finder and Nova Finder all roll here. */
    private void rollBonusEnchants(Player player, PlayerData data, HoeEnchantManager hoe, Location plot) {
        double blast = hoe.powerOf(data, "BLAST_HARVEST");
        if (blast > 0 && ThreadLocalRandom.current().nextDouble() < blast) {
            int radius = 1 + hoe.levelOf(data, "BLAST_HARVEST") / 3;
            int swept = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Location near = plot.clone().add(dx, 0, dz);
                    if (!plots.contains(normalise(near))) continue;
                    if (harvest(player, near, false)) swept++;
                }
            }
            if (swept > 0) {
                player.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, plot.clone().add(0.5, 0.5, 0.5),
                        2, 0.4, 0.2, 0.4, 0.0);
                player.playSound(plot, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
                sendActionBar(player, ChatColor.RED + "" + ChatColor.BOLD + "BLAST! "
                        + ChatColor.RESET + ChatColor.GRAY + swept + " extra crops");
            }
        }

        double credit = hoe.powerOf(data, "CREDIT_FINDER");
        if (credit > 0 && ThreadLocalRandom.current().nextDouble() < credit) {
            data.addPoints(1L);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "CREDIT FOUND! "
                    + ChatColor.RESET + ChatColor.GRAY + "+1 Credit from the soil.");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
        }

        double nova = hoe.powerOf(data, "NOVA_FINDER");
        if (nova > 0 && ThreadLocalRandom.current().nextDouble() < nova) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "NOVA SPARK! "
                    + ChatColor.RESET + ChatColor.GRAY + "A free Nova Core forge attempt.");
            plugin.getNovaCoreManager().attempt(player, data, false);
        }
    }

    private void announce(Player player, long tokens, long shards, boolean fortune) {
        StringBuilder reward = new StringBuilder();
        reward.append(ChatColor.YELLOW).append("+").append(String.format("%,d", tokens)).append(" Tokens");
        if (shards > 0) {
            reward.append(ChatColor.GRAY).append("  ").append(ChatColor.AQUA)
                    .append("+").append(String.format("%,d", shards)).append(" Gems");
        }
        if (fortune) {
            reward.append(ChatColor.GRAY).append("  ").append(ChatColor.GOLD).append(ChatColor.BOLD)
                    .append("FORTUNE x2");
        }
        sendActionBar(player, reward.toString());
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.6f);
    }

    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(text));
    }

    public void forget(UUID uuid) {
        harvested.remove(uuid);
        momentum.remove(uuid);
    }

    /** Momentum stacks currently held, for the hoe's tooltip. */
    public long momentumStacks(UUID uuid) {
        long[] state = momentum.get(uuid);
        if (state == null) return 0L;
        return System.currentTimeMillis() - state[1] > 5_000L ? 0L : state[0];
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
