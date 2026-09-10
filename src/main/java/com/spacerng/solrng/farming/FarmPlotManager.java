package com.spacerng.solrng.farming;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Currency;
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
 * changes - it stays the marker crop forever, breaks against it are
 * cancelled, and block physics on it are suppressed - so the field can't
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
     * And it has to be INVISIBLE. A harvest cancels the break, and a
     * cancelled break makes the server send the real block back to the
     * client in the same tick, before the plugin can answer with AIR. For
     * that one tick the player sees whatever the marker is: a torchflower,
     * then a nether wart. Structure void renders as nothing at all, so the
     * resync lands and the plot simply looks empty. It is also terminal,
     * needs nothing under it, breaks instantly, and nobody builds with it,
     * which keeps the WORLD a usable record of where the farm is for
     * /rngadmin farmscan.
     */
    private static final Material MARKER = Material.STRUCTURE_VOID;

    /**
     * What plots used to be. A scan accepts these too, on request, and
     * restore() converts any of them standing on a registered plot, so a
     * field built under an older marker heals itself as players walk it.
     */
    private static final java.util.Set<Material> LEGACY_MARKERS = java.util.Set.of(
            Material.WHEAT, Material.TORCHFLOWER_CROP, Material.TORCHFLOWER, Material.NETHER_WART);

    // What the newest crop paid, so Nuke can price itself off a real
    // harvest instead of guessing at the player's multipliers.
    private long lastCropTokens = 1L;

    // Proc tuning, all from config.
    private int lightningRadius = 5;
    private int lightningBolts = 5;
    private long nukeCrops = 10_000L;
    private long momentumDecayMillis = 60_000L;
    private double gambaMultiplier = 3.0;
    private long gambaSeconds = 60L;
    private String keyFinderReward = "crate_key";

    // One plot per player is worth many times the others. Per player,
    // because every farmer already sees their own crop on every tile - a
    // shared golden plot would be a race, and this is meant to be a reason
    // to keep looking around your own field.
    private final Map<UUID, Location> golden = new HashMap<>();
    private double goldenMultiplier = 10.0;
    private boolean goldenEnabled = true;
    private double prospectorShare = 0.02;
    private double goldenTouchMultiplier = 2.0;
    private long goldenTouchSeconds = 15L;
    private double gemRushMultiplier = 3.0;
    private long gemRushSeconds = 15L;
    private long echoRepeats = 8L;
    private double alchemyShare = 0.15;
    private double alchemyRate = 0.001;
    private int cascadeRadius = 4;
    private long cascadeGems = 2L;
    private long stormPayout = 25L;
    private int meteorRadius = 3;
    private long supernovaCoins = 250L;
    private int supernovaAttempts = 3;
    private long supernovaGems = 40L;
    private java.util.List<String> potionFinderRewards = java.util.List.of();

    private static String trimTimes(double value) {
        String text = String.format("%.2f", value);
        if (text.endsWith(".00")) text = text.substring(0, text.length() - 3);
        else if (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text + "x";
    }

    // Momentum has no stack cap any more - the ceiling is the Momentum
    // enchant's level, which is a thing you buy rather than a constant
    // nobody could see.

    private final SolRNGPlugin plugin;
    private final NamespacedKey plotItemKey;
    private final File plotFile;

    private final Map<String, CropType> crops = new LinkedHashMap<>();
    private final Set<Location> plots = new HashSet<>();
    // Per player, the plots they've harvested and the tick they come back.
    private final Map<UUID, Map<Location, Long>> harvested = new HashMap<>();
    // Momentum: how many harvests in the current unbroken run, and when the
    // last one landed. Kept in memory only - a streak is a session thing.
    private final Map<UUID, long[]> momentum = new HashMap<>(); // {streak, lastMillis}

    private int regrowTicks = 60;
    private int regrowFloorTicks = 2;
    private String shardsNode = "";
    // Both sounds are configured rather than hard-coded, and both can be
    // switched off per player from the hoe menu - a farm is the one place
    // in this plugin somebody might sit for an hour straight.
    private org.bukkit.Sound harvestSound = org.bukkit.Sound.BLOCK_CROP_BREAK;
    private float harvestPitch = 1.4f;
    private org.bukkit.Sound procSound = org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME;
    private float procPitch = 1.7f;
    private int blastBaseRadius = 1;
    private int blastLevelsPerRadius = 150;
    private int blastMaxRadius = 7;
    private double momentumPerThousand = 0.01;
    private double momentumPerLevelCap = 0.05;
    private long momentumIdleMillis = 30_000L;

    public FarmPlotManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.plotItemKey = SolRNGPlugin.key( "solrng_farm_plot");
        this.plotFile = new File(plugin.getDataFolder(), "farmplots.yml");
    }

    // ------------------------------------------------------------- config

    public void load(FileConfiguration config) {
        momentumDecayMillis = Math.max(1000L,
                config.getLong("farming.momentum.decay-seconds", 60L) * 1000L);
        goldenEnabled = config.getBoolean("farming.golden-crop.enabled", true);
        goldenMultiplier = config.getDouble("farming.golden-crop.multiplier", 10.0);
        prospectorShare = config.getDouble("farming.procs.prospector-share", 0.02);
        goldenTouchMultiplier = config.getDouble("farming.procs.golden-touch-multiplier", 2.0);
        goldenTouchSeconds = config.getLong("farming.procs.golden-touch-seconds", 15L);
        gemRushMultiplier = config.getDouble("farming.procs.gem-rush-multiplier", 3.0);
        gemRushSeconds = config.getLong("farming.procs.gem-rush-seconds", 15L);
        echoRepeats = config.getLong("farming.procs.echo-repeats", 8L);
        alchemyShare = config.getDouble("farming.procs.alchemy-share", 0.15);
        alchemyRate = config.getDouble("farming.procs.alchemy-rate", 0.001);
        cascadeRadius = config.getInt("farming.procs.cascade-radius", 4);
        cascadeGems = config.getLong("farming.procs.cascade-gems", 2L);
        stormPayout = config.getLong("farming.procs.storm-payout", 25L);
        meteorRadius = config.getInt("farming.procs.meteor-radius", 3);
        supernovaCoins = config.getLong("farming.procs.supernova-coins", 250L);
        supernovaAttempts = config.getInt("farming.procs.supernova-nova-attempts", 3);
        supernovaGems = config.getLong("farming.procs.supernova-gems", 40L);
        lightningRadius = Math.max(1, config.getInt("farming.procs.lightning-radius", 5));
        lightningBolts = Math.max(1, config.getInt("farming.procs.lightning-bolts", 5));
        nukeCrops = Math.max(1L, config.getLong("farming.procs.nuke-crops", 10000L));
        gambaMultiplier = Math.max(1.0, config.getDouble("farming.procs.gamba-multiplier", 3.0));
        gambaSeconds = Math.max(1L, config.getLong("farming.procs.gamba-seconds", 60L));
        keyFinderReward = config.getString("farming.procs.key-finder-reward", "crate_key");
        potionFinderRewards = config.getStringList("farming.procs.potion-finder-rewards");

        crops.clear();
        // Ticks first, seconds as the fallback. A whole second was the
        // smallest regrow that could be expressed, which put a hard ceiling
        // of one harvest per plot per second on the entire farm no matter
        // what the hoe said.
        regrowTicks = Math.max(1, config.getInt("farming.regrow-ticks",
                Math.max(1, config.getInt("farming.regrow-seconds", 3)) * 20));
        regrowFloorTicks = Math.max(1, config.getInt("farming.regrow-floor-ticks", 2));
        shardsNode = config.getString("farming.shards-node", "");
        harvestSound = soundOf(config.getString("farming.sounds.harvest"), org.bukkit.Sound.BLOCK_CROP_BREAK);
        harvestPitch = (float) config.getDouble("farming.sounds.harvest-pitch", 1.4);
        procSound = soundOf(config.getString("farming.sounds.enchant-proc"),
                org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME);
        procPitch = (float) config.getDouble("farming.sounds.enchant-proc-pitch", 1.7);
        blastBaseRadius = Math.max(1, config.getInt("farming.blast.base-radius", 1));
        blastLevelsPerRadius = Math.max(1, config.getInt("farming.blast.levels-per-radius", 150));
        blastMaxRadius = Math.max(1, config.getInt("farming.blast.max-radius", 7));
        momentumPerThousand = config.getDouble("farming.momentum.per-thousand-crops", 0.01);
        momentumPerLevelCap = config.getDouble("farming.momentum.per-level-cap", 0.05);
        momentumIdleMillis = Math.max(1L, config.getLong("farming.momentum.idle-seconds", 30L)) * 1000L;

        ConfigurationSection section = config.getConfigurationSection("farming.crop-types");
        if (section != null) {
            int order = 0;
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) continue;
                Material material = Material.matchMaterial(c.getString("material", id));
                if (material == null) {
                    plugin.getLogger().warning("Unknown crop material for '" + id + "'.");
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
        plugin.getLogger().info("Loaded " + crops.size() + " farm crop types.");
        loadPlots();
    }

    /** A named sound, or the fallback if config names one that doesn't exist. */
    private org.bukkit.Sound soundOf(String name, org.bukkit.Sound fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return org.bukkit.Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown farming sound '" + name + "', using "
                    + fallback.name() + ".");
            return fallback;
        }
    }

    /** The crop coming up. Quiet on purpose - it fires several times a second. */
    private void playHarvest(Player player, PlayerData data) {
        if (!data.isFarmSoundEnabled()) return;
        player.playSound(player.getLocation(), harvestSound, 0.35f, harvestPitch);
    }

    /**
     * An enchant firing. Louder and higher than the harvest, because the
     * whole point is that it stands out from the sound you're already
     * hearing constantly.
     */
    private void playProc(Player player, PlayerData data, float pitch) {
        if (!data.isEnchantSoundEnabled()) return;
        player.playSound(player.getLocation(), procSound, 0.8f, pitch);
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

    /**
     * Removes every plot and the block under it.
     *
     * The blocks go as well as the registry: leaving the markers standing
     * would mean the next farmscan puts the whole field straight back.
     */
    public int clearAll() {
        int removed = 0;
        for (Location plot : new java.util.ArrayList<>(plots)) {
            World world = plot.getWorld();
            if (world != null && world.isChunkLoaded(plot.getBlockX() >> 4, plot.getBlockZ() >> 4)) {
                Block block = plot.getBlock();
                if (block.getType() == MARKER || LEGACY_MARKERS.contains(block.getType())) {
                    block.setType(Material.AIR, false);
                }
            }
            removed++;
        }
        plots.clear();
        harvested.clear();
        golden.clear();
        savePlots();
        renderAll();
        return removed;
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
     * field built before the marker changed gets recovered - at the cost
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
                    if (type != MARKER && !(includeLegacy && LEGACY_MARKERS.contains(type))) continue;
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
        // Wall clock, not world time. The regrow is scheduled in ticks and
        // the render used to test against getFullTime(), so any moment the
        // two drifted apart - lag, a frozen world, a reload - the crop was
        // drawn back by the scheduler and then wiped again by the next
        // render two seconds later. Both ends read the same clock now.
        long now = System.currentTimeMillis();
        World world = player.getWorld();
        double rangeSq = 64 * 64;

        for (Location plot : plots) {
            if (plot.getWorld() == null || !plot.getWorld().equals(world)) continue;
            if (plot.distanceSquared(player.getLocation()) > rangeSq) continue;

            // Heal before drawing. Sending a crop for a plot whose real
            // block is gone paints a ghost the client believes in but the
            // server doesn't: it looks farmable and produces no break event
            // at all, so it can't be harvested OR removed. That's what made
            // plots "disappear" - they were still listed, still drawn, and
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
     * apart - a piston, an explosion, world edit, a rollback, or the world
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
        long now = System.currentTimeMillis();
        if (mine.getOrDefault(plot, 0L) > now) return false; // still regrowing for them

        HoeEnchantManager hoe = plugin.getHoeEnchantManager();
        int regrow = regrowTicksFor(data);
        mine.put(plot, now + regrow * 50L); // ticks to milliseconds

        // The whole payout - tool, Coin Greed, boosts, Nova Core, prestige
        // and the general tree - is defined once in StatSources, so /stats
        // quotes the same number this pays. Momentum is handed in rather
        // than read there: it's earned live and resets when you stop.
        double multiplier = com.spacerng.solrng.stats.StatSources
                .coins(plugin, data, momentumMultiplier(player, hoe, data, chain)).total();
        // Per-crop yield skills stack on top, so specialising in one crop
        // is a real choice against raising every crop a little.
        double cropYield = plugin.getSkillTreeManager()
                .multiplierOf(data, com.spacerng.solrng.player.SkillNode.Effect.CROP_YIELD, crop.getId());
        multiplier *= cropYield;
        long tokens = Math.round(crop.getTokens() * multiplier);

        // The golden crop pays many times over and then moves somewhere
        // else in the field, so there is always exactly one to look for.
        boolean wasGolden = goldenEnabled && plot.equals(golden.get(player.getUniqueId()));
        if (wasGolden) {
            tokens = Math.round(tokens * goldenMultiplierFor(data));
        }

        double gemMultiplier = plugin.getSkillTreeManager()
                .multiplierOf(data, com.spacerng.solrng.player.SkillNode.Effect.GEM_MULTIPLIER)
                * cropYield * data.boostMultiplier("GEMS");
        long shards = shardsUnlocked(data)
                ? Math.round(crop.getShards() * gemMultiplier) : 0L;

        double shardGreed = hoe.powerOf(data, "SHARD_GREED")
                + plugin.getPrestigeManager().upgradeTotal(data,
                        com.spacerng.solrng.player.PrestigeUpgrade.Effect.SHARD_BONUS);
        boolean gemProc = shardsUnlocked(data) && shardGreed > 0
                && ThreadLocalRandom.current().nextDouble() < shardGreed;
        if (gemProc) {
            shards += 1;
        }

        // Fortune was removed. The flag survives only so the announce
        // signature does not have to change in the same commit.
        boolean fortune = false;
        if (chain && gemProc) {
            playProc(player, data, procPitch);
        }

        if (tokens > 0) {
            data.addTokens(tokens);
            // Coin Factory pays back a minute of earnings, so the earnings
            // have to be remembered as they happen.
            data.trackCoins(tokens);
            lastCropTokens = tokens;
        }
        if (shards > 0) data.addShards(shards);
        data.addCropsHarvested(1L);
        plugin.getPassManager().awardHarvest(player, data, 1L);

        // ONE TICK LATER, not now.
        //
        // The break event was cancelled, and a cancelled BlockBreakEvent
        // makes the server re-send the real block to the client at the end
        // of the tick. Sending AIR inside the event means the resync lands
        // on top of it and the player watches the crop turn into the bare
        // torchflower marker instead of vanishing. A tick later, the
        // resync has already happened and the AIR is what sticks.
        final CropType regrown = crop;
        final BlockData nothing = Bukkit.createBlockData(Material.AIR);
        // Now AND next tick. The immediate one covers the ordinary case;
        // the deferred one wins the race against the resync a cancelled
        // BlockBreakEvent triggers at the end of the tick, which would
        // otherwise repaint the bare marker on top of the empty plot.
        player.sendBlockChange(plot, nothing);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendBlockChange(plot, nothing);
            }
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plots.contains(plot)) {
                player.sendBlockChange(plot, grownData(regrown.getMaterial()));
            }
        }, regrow);

        if (wasGolden) {
            Location next = moveGolden(player.getUniqueId());
            EnchantFx.pulse(plugin, player, plot, false);
            player.playSound(plot, org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.6f);
            player.playSound(plot, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
            sendActionBar(player, ChatColor.GOLD + "" + ChatColor.BOLD + "Golden crop  "
                    + ChatColor.RESET + ChatColor.GRAY + trimTimes(goldenMultiplierFor(data))
                    + " for " + Currency.COINS.amount(tokens)
                    + (next == null ? "" : ChatColor.DARK_GRAY + "  a new one is out there"));
        }

        if (chain) {
            playHarvest(player, data);
            rollBonusEnchants(player, data, hoe, plot);
            announce(player, tokens, shards, fortune);
        }
        plugin.getScoreboardManager().update(player);
        return true;
    }

    /**
     * Speed shortens the regrow wait. The tool tier no longer does: a
     * tier is two multipliers now, Coins and enchant proc, and adding a
     * third thing it quietly did was how nobody could tell what a tier
     * was worth.
     */
    /**
     * How long this player crops take to come back, in ticks.
     *
     * The floor used to be a hard-coded 10 ticks. With the base at 20 that
     * meant Speed stopped paying anything at all past 50 percent, and the
     * fastest farm in the game was two crops a second. The floor is a
     * config value now and sits at 2 ticks, so the enchant is worth the
     * levels and a swing never waits on the block.
     */
    private int regrowTicksFor(PlayerData data) {
        double faster = plugin.getHoeEnchantManager().powerOf(data, "SPEED");
        return (int) Math.max(regrowFloorTicks,
                Math.round(regrowTicks * (1.0 - Math.min(0.90, faster))));
    }

    /**
     * Momentum: an unbroken run of harvests builds a multiplier that is
     * gone the moment you stop. It rewards staying in the field rather
     * than clicking a plot every few minutes, which is the behaviour a
     * farm wants.
     *
     * The enchant sets the CEILING, the field fills it. That split is the
     * whole point: it's the one farm bonus you can't simply buy, and the
     * boss bar exists so nobody is quietly sitting on a number they can't
     * see.
     */
    private double momentumMultiplier(Player player, HoeEnchantManager hoe, PlayerData data, boolean chain) {
        int level = hoe.levelOf(data, "MOMENTUM");
        double cap = momentumPerLevelCap * level;
        if (cap <= 0) {
            plugin.getMomentumBar().hide(player.getUniqueId());
            return 1.0;
        }

        // {crops, last harvest millis, the peak to drain from}
        long[] state = momentum.computeIfAbsent(player.getUniqueId(), k -> new long[]{0L, 0L, 0L});
        long now = System.currentTimeMillis();
        if (chain) {
            state[0] = (now - state[1] > momentumIdleMillis) ? 1L : state[0] + 1L;
            state[1] = now;
        }

        double bonus = Math.min(cap, (state[0] / 1000.0) * momentumPerThousand);
        if (chain) {
            plugin.getMomentumBar().update(player, state[0], bonus, cap);
        }
        return 1.0 + bonus;
    }

    /**
     * Ends any run that has gone quiet and takes its bar down. Run on a
     * timer rather than from a "stopped farming" event, because there
     * isn't one - you stop by simply not doing anything.
     */
    /**
     * Momentum drains rather than vanishing.
     *
     * It used to be deleted the moment you stopped, which meant the number
     * you had built was gone before you could look at it and the bar
     * disappeared mid-glance. Now the bar stays and the multiplier slides
     * from where it was down to nothing over a minute, so stopping is a
     * decision with a visible cost rather than a light switch.
     */
    public void expireMomentum() {
        long now = System.currentTimeMillis();
        HoeEnchantManager hoe = plugin.getHoeEnchantManager();

        java.util.Iterator<Map.Entry<UUID, long[]>> it = momentum.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, long[]> entry = it.next();
            long[] state = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }

            PlayerData data = plugin.getPlayerDataManager().get(entry.getKey());
            double cap = momentumPerLevelCap * hoe.levelOf(data, "MOMENTUM");
            if (cap <= 0) {
                it.remove();
                plugin.getMomentumBar().hide(entry.getKey());
                continue;
            }

            long idleFor = now - state[1];
            if (idleFor <= momentumIdleMillis) {
                // Still swinging. Keep the peak in step with the run so the
                // drain starts from wherever they actually got to.
                state[2] = state[0];
            } else {
                double left = 1.0 - (double) (idleFor - momentumIdleMillis) / momentumDecayMillis;
                if (left <= 0.0) {
                    it.remove();
                    plugin.getMomentumBar().hide(entry.getKey());
                    continue;
                }
                state[0] = Math.round(state[2] * left);
            }

            double bonus = Math.min(cap, (state[0] / 1000.0) * momentumPerThousand);
            plugin.getMomentumBar().update(player, state[0], bonus, cap);
        }
    }

    /**
     * Every proc rolls here, once per harvested plot.
     *
     * This is the hottest path in the plugin - it runs for every block a
     * player breaks, and a blast sweep multiplies that - so the work per
     * roll stays a comparison unless the roll actually lands.
     */
    private void rollBonusEnchants(Player player, PlayerData data, HoeEnchantManager hoe, Location plot) {
        double blast = hoe.powerOf(data, "BLAST_HARVEST");
        if (blast > 0 && ThreadLocalRandom.current().nextDouble() < blast) {
            // One radius step per 150 levels, hard-capped. The old rate was
            // one per three, which at a maxed 1000 levels would have swept
            // a 669-block-wide square out of a single click.
            int radius = Math.min(blastMaxRadius,
                    blastBaseRadius + hoe.levelOf(data, "BLAST_HARVEST") / blastLevelsPerRadius);
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
                if (data.isEnchantSoundEnabled()) {
                    player.playSound(plot, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
                }
                sendActionBar(player, ChatColor.RED + "" + ChatColor.BOLD + "Blast  "
                        + ChatColor.RESET + ChatColor.GRAY + swept + " extra crops");
            }
        }

        double lightning = hoe.powerOf(data, "LIGHTNING");
        if (lightning > 0 && ThreadLocalRandom.current().nextDouble() < lightning) {
            int struck = 0;
            for (int attempt = 0; attempt < lightningBolts; attempt++) {
                int dx = ThreadLocalRandom.current().nextInt(-lightningRadius, lightningRadius + 1);
                int dz = ThreadLocalRandom.current().nextInt(-lightningRadius, lightningRadius + 1);
                Location near = normalise(plot.clone().add(dx, 0, dz));
                if (!plots.contains(near)) continue;
                // Effect lightning, never the real thing: the real one sets
                // fires and kills whoever is standing in the field.
                player.getWorld().strikeLightningEffect(near.clone().add(0.5, 0, 0.5));
                if (harvest(player, near, false)) struck++;
            }
            if (struck > 0) {
                sendActionBar(player, ChatColor.YELLOW + "" + ChatColor.BOLD + "Lightning  "
                        + ChatColor.RESET + ChatColor.GRAY + struck + " crops struck");
            }
        }

        double nuke = hoe.powerOf(data, "NUKE");
        if (nuke > 0 && ThreadLocalRandom.current().nextDouble() < nuke) {
            // Credited, not iterated. Ten thousand real block updates in one
            // tick is a freeze; ten thousand crops of payout is a moment.
            long paid = Math.max(1L, lastCropTokens) * nukeCrops;
            data.addTokens(paid);
            data.trackCoins(paid);
            data.addCropsHarvested(nukeCrops);
            plugin.getPassManager().awardHarvest(player, data, nukeCrops);
            player.getWorld().createExplosion(plot.clone().add(0.5, 1.0, 0.5), 3.0f, false, false);
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Nuke  "
                    + ChatColor.RESET + ChatColor.GRAY + String.format("%,d", nukeCrops)
                    + " crops vaporised for " + Currency.COINS.amount(paid) + ChatColor.GRAY + ".");
            playProc(player, data, 0.5f);
        }

        double factory = hoe.powerOf(data, "COIN_FACTORY");
        if (factory > 0 && ThreadLocalRandom.current().nextDouble() < factory) {
            long minute = data.coinsInLastMinute();
            if (minute > 0) {
                data.addTokens(minute);
                player.sendMessage(Currency.COINS.colour() + "" + ChatColor.BOLD + "Coin Factory  "
                        + ChatColor.RESET + ChatColor.GRAY + "the last minute again: "
                        + Currency.COINS.amount(minute));
                playProc(player, data, 1.5f);
            }
        }

        double key = hoe.powerOf(data, "KEY_FINDER");
        if (key > 0 && ThreadLocalRandom.current().nextDouble() < key) {
            var found = plugin.getConsumableManager().get(keyFinderReward);
            if (found != null) {
                plugin.getConsumableManager().give(player, found, 1);
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Key found  "
                        + ChatColor.RESET + ChatColor.GRAY + "something was buried under that one.");
                playProc(player, data, 1.7f);
            }
        }

        double potion = hoe.powerOf(data, "POTION_FINDER");
        if (potion > 0 && !potionFinderRewards.isEmpty()
                && ThreadLocalRandom.current().nextDouble() < potion) {
            String id = potionFinderRewards.get(
                    ThreadLocalRandom.current().nextInt(potionFinderRewards.size()));
            var found = plugin.getConsumableManager().get(id);
            if (found != null) {
                plugin.getConsumableManager().give(player, found, 1);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Potion found  "
                        + ChatColor.RESET + ChatColor.GRAY + found.display());
                playProc(player, data, 1.4f);
            }
        }

        double gamba = hoe.powerOf(data, "GAMBA");
        if (gamba > 0 && ThreadLocalRandom.current().nextDouble() < gamba) {
            // One of three, never all three: the roll IS the enchant.
            String[] keys = {"TOKENS", "GEMS", "ENCHANT_PROC"};
            String[] names = {"Coins", "Gems", "enchant procs"};
            int pick = ThreadLocalRandom.current().nextInt(keys.length);
            data.applyBoost(keys[pick], gambaMultiplier, gambaSeconds * 1000L);
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Gamba  "
                    + ChatColor.RESET + ChatColor.GRAY + "it landed on "
                    + ChatColor.WHITE + names[pick] + ChatColor.GRAY + ", "
                    + ChatColor.GOLD + trimTimes(gambaMultiplier)
                    + ChatColor.GRAY + " for " + gambaSeconds + "s.");
            playProc(player, data, 2.0f);
        }

        rollWealthEnchants(player, data, hoe, plot);

        double credit = hoe.powerOf(data, "CREDIT_FINDER");
        if (credit > 0 && ThreadLocalRandom.current().nextDouble() < credit) {
            data.addPoints(1L);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Credit found  "
                    + ChatColor.RESET + ChatColor.GRAY + "+1 Credit from the soil.");
            playProc(player, data, 1.2f);
        }

        double nova = hoe.powerOf(data, "NOVA_FINDER");
        if (nova > 0 && ThreadLocalRandom.current().nextDouble() < nova) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Nova spark  "
                    + ChatColor.RESET + ChatColor.GRAY + "A free Nova Core forge attempt.");
            playProc(player, data, 0.9f);
            plugin.getNovaCoreManager().attempt(player, data, false);
        }
    }

    /**
     * The ten wealth enchants.
     *
     * All of them are about Coins or Gems, and all of them are seen only
     * by the player who earned it. Twenty farmers on one field would
     * otherwise be twenty overlapping effects in everyone else's face.
     *
     * Payouts are priced off lastCropTokens rather than a flat number, so
     * they respect every multiplier the player has and never go stale when
     * something is retuned.
     */
    private void rollWealthEnchants(Player player, PlayerData data, HoeEnchantManager hoe, Location plot) {
        long base = Math.max(1L, lastCropTokens);

        double prospector = hoe.powerOf(data, "PROSPECTOR");
        if (prospector > 0 && ThreadLocalRandom.current().nextDouble() < prospector) {
            long gems = Math.max(1L, Math.round(base * prospectorShare));
            data.addShards(gems);
            EnchantFx.pulse(plugin, player, plot, true);
            sendActionBar(player, ChatColor.AQUA + "" + ChatColor.BOLD + "Prospector  "
                    + ChatColor.RESET + ChatColor.GRAY + "+" + gems + " Gems");
        }

        double golden = hoe.powerOf(data, "GOLDEN_TOUCH");
        if (golden > 0 && ThreadLocalRandom.current().nextDouble() < golden) {
            data.applyBoost("TOKENS", goldenTouchMultiplier, goldenTouchSeconds * 1000L);
            EnchantFx.coinStorm(plugin, player, (int) (goldenTouchSeconds * 20L));
            player.sendMessage(Currency.COINS.colour() + "" + ChatColor.BOLD + "Golden Touch  "
                    + ChatColor.RESET + ChatColor.GRAY + "Coins x" + trimTimes(goldenTouchMultiplier)
                    + " for " + goldenTouchSeconds + "s.");
        }

        double rush = hoe.powerOf(data, "GEM_RUSH");
        if (rush > 0 && ThreadLocalRandom.current().nextDouble() < rush) {
            data.applyBoost("GEMS", gemRushMultiplier, gemRushSeconds * 1000L);
            EnchantFx.gemRush(plugin, player, (int) (gemRushSeconds * 20L));
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Gem Rush  "
                    + ChatColor.RESET + ChatColor.GRAY + "Gems x" + trimTimes(gemRushMultiplier)
                    + " for " + gemRushSeconds + "s.");
        }

        double echo = hoe.powerOf(data, "HARVEST_ECHO");
        if (echo > 0 && ThreadLocalRandom.current().nextDouble() < echo) {
            long paid = base * echoRepeats;
            data.addTokens(paid);
            data.trackCoins(paid);
            EnchantFx.pulse(plugin, player, plot, false);
            sendActionBar(player, ChatColor.GOLD + "" + ChatColor.BOLD + "Echo x" + echoRepeats
                    + "  " + ChatColor.RESET + ChatColor.GRAY + Currency.COINS.amount(paid));
        }

        double alchemy = hoe.powerOf(data, "ALCHEMY");
        if (alchemy > 0 && ThreadLocalRandom.current().nextDouble() < alchemy) {
            long spent = Math.round(data.getTokens() * alchemyShare);
            long gems = Math.round(spent * alchemyRate);
            if (gems > 0 && data.spendTokens(spent)) {
                data.addShards(gems);
                EnchantFx.transmute(plugin, player, plot);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Alchemy  "
                        + ChatColor.RESET + ChatColor.GRAY + Currency.COINS.amount(spent)
                        + ChatColor.GRAY + " into " + ChatColor.AQUA + gems + " Gems"
                        + ChatColor.GRAY + ".");
            }
        }

        double cascade = hoe.powerOf(data, "GEM_CASCADE");
        if (cascade > 0 && ThreadLocalRandom.current().nextDouble() < cascade) {
            Location from = plot;
            long gems = 0L;
            for (int hop = 0; hop < 6; hop++) {
                int dx = ThreadLocalRandom.current().nextInt(-cascadeRadius, cascadeRadius + 1);
                int dz = ThreadLocalRandom.current().nextInt(-cascadeRadius, cascadeRadius + 1);
                Location next = normalise(from.clone().add(dx, 0, dz));
                if (!plots.contains(next)) continue;
                EnchantFx.arc(player, from, next, true);
                gems += cascadeGems;
                from = next;
            }
            if (gems > 0) {
                data.addShards(gems);
                sendActionBar(player, ChatColor.AQUA + "" + ChatColor.BOLD + "Cascade  "
                        + ChatColor.RESET + ChatColor.GRAY + "+" + gems + " Gems");
            }
        }

        double storm = hoe.powerOf(data, "COIN_STORM");
        if (storm > 0 && ThreadLocalRandom.current().nextDouble() < storm) {
            long paid = base * stormPayout;
            data.addTokens(paid);
            data.trackCoins(paid);
            EnchantFx.coinStorm(plugin, player, 40);
            player.sendMessage(Currency.COINS.colour() + "" + ChatColor.BOLD + "Coin Storm  "
                    + ChatColor.RESET + ChatColor.GRAY + Currency.COINS.amount(paid)
                    + ChatColor.GRAY + " out of the sky.");
        }

        double meteor = hoe.powerOf(data, "METEOR");
        if (meteor > 0 && ThreadLocalRandom.current().nextDouble() < meteor) {
            EnchantFx.meteor(plugin, player, plot, meteorRadius);
            int hit = sweep(player, plot, meteorRadius);
            if (hit > 0) {
                long paid = base * hit;
                data.addTokens(paid);
                data.trackCoins(paid);
                sendActionBar(player, ChatColor.RED + "" + ChatColor.BOLD + "Meteor  "
                        + ChatColor.RESET + ChatColor.GRAY + hit + " crops, paid twice");
            }
        }

        double hole = hoe.powerOf(data, "BLACK_HOLE");
        if (hole > 0 && ThreadLocalRandom.current().nextDouble() < hole) {
            EnchantFx.blackHole(plugin, player, plot, blastMaxRadius);
            int pulled = sweep(player, plot, blastMaxRadius);
            if (pulled > 0) {
                long paid = base * pulled * 2L;
                data.addTokens(paid);
                data.trackCoins(paid);
                data.addShards(pulled);
                player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Black Hole  "
                        + ChatColor.RESET + ChatColor.GRAY + pulled + " crops swallowed for "
                        + Currency.COINS.amount(paid) + ChatColor.GRAY + " and " + ChatColor.AQUA
                        + pulled + " Gems" + ChatColor.GRAY + ".");
            }
        }

        double nova = hoe.powerOf(data, "SUPERNOVA");
        if (nova > 0 && ThreadLocalRandom.current().nextDouble() < nova) {
            long coins = base * supernovaCoins;
            data.addTokens(coins);
            data.trackCoins(coins);
            data.addShards(supernovaGems);
            EnchantFx.supernova(plugin, player, plot);
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Supernova");
            player.sendMessage(ChatColor.GRAY + "  " + Currency.COINS.amount(coins)
                    + ChatColor.GRAY + " and " + ChatColor.AQUA + supernovaGems + " Gems"
                    + ChatColor.GRAY + " out of a single crop.");
            // Nova Finder gives you one free climb. Supernova is the same
            // idea several times over, which is what makes it the thing
            // you graduate to rather than a separate lottery.
            for (int i = 0; i < supernovaAttempts; i++) {
                plugin.getNovaCoreManager().attempt(player, data, false);
            }
            player.sendMessage(ChatColor.AQUA + "  " + supernovaAttempts
                    + " free Nova Core climbs on top.");
            player.sendMessage("");
        }
    }

    // ------------------------------------------------------- golden crop

    /**
     * The golden plot for one player, chosen at random and moved every
     * time it is harvested.
     *
     * Picked lazily rather than assigned on join, so it costs nothing
     * until somebody actually farms, and it re-picks itself if the plot it
     * was sitting on is removed.
     */
    public Location goldenPlot(UUID uuid) {
        if (!goldenEnabled || plots.isEmpty()) return null;
        Location current = golden.get(uuid);
        if (current != null && plots.contains(current)) return current;
        return moveGolden(uuid);
    }

    private Location moveGolden(UUID uuid) {
        if (plots.isEmpty()) {
            golden.remove(uuid);
            return null;
        }
        List<Location> pool = new ArrayList<>(plots);
        Location picked = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        golden.put(uuid, picked);
        return picked;
    }

    /** What a golden crop is worth, skills included. */
    public double goldenMultiplierFor(PlayerData data) {
        return goldenMultiplier + plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.GOLDEN_CROP);
    }

    public void forgetGolden(UUID uuid) {
        golden.remove(uuid);
    }

    /**
     * The shimmer over a player's golden crop.
     *
     * Player-sided, like every other effect on the farm: it is only their
     * golden plot, and twenty farmers would otherwise light up twenty
     * tiles for everybody.
     */
    public void tickGolden() {
        if (!goldenEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location plot = golden.get(player.getUniqueId());
            if (plot == null || !plots.contains(plot)) continue;
            if (plot.getWorld() == null || !plot.getWorld().equals(player.getWorld())) continue;
            if (plot.distanceSquared(player.getLocation()) > 64 * 64) continue;

            Location at = plot.clone().add(0.5, 0.6, 0.5);
            player.spawnParticle(org.bukkit.Particle.DUST, at, 4, 0.22, 0.35, 0.22, 0.0,
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 199, 44), 1.1f));
            player.spawnParticle(org.bukkit.Particle.END_ROD, at, 1, 0.1, 0.2, 0.1, 0.0);
        }
    }

    /** Harvests every plot inside a radius. Returns how many landed. */
    private int sweep(Player player, Location centre, int radius) {
        int swept = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                Location near = normalise(centre.clone().add(dx, 0, dz));
                if (!plots.contains(near)) continue;
                if (harvest(player, near, false)) swept++;
            }
        }
        return swept;
    }

    private void announce(Player player, long tokens, long shards, boolean fortune) {
        // Sound is handled by playHarvest/playProc so the two can be
        // switched off independently; this line is text only.
        StringBuilder reward = new StringBuilder();
        reward.append(Currency.COINS.numberColour()).append("+").append(String.format("%,d", tokens)).append(" Coins");
        if (shards > 0) {
            reward.append(ChatColor.GRAY).append("  ").append(ChatColor.AQUA)
                    .append("+").append(String.format("%,d", shards)).append(" Gems");
        }
        if (fortune) {
            reward.append(ChatColor.GRAY).append("  ").append(ChatColor.GOLD).append(ChatColor.BOLD)
                    .append("FORTUNE x2");
        }
        sendActionBar(player, reward.toString());
    }

    private void sendActionBar(Player player, String text) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(text));
    }

    public void forget(UUID uuid) {
        harvested.remove(uuid);
        momentum.remove(uuid);
        plugin.getMomentumBar().hide(uuid);
    }

    /** Crops in the run currently going, or 0 when there isn't one. */
    public long momentumStacks(UUID uuid) {
        long[] state = momentum.get(uuid);
        if (state == null) return 0L;
        return System.currentTimeMillis() - state[1] > momentumIdleMillis ? 0L : state[0];
    }

    // ----------------------------------------------------------- the item

    /** The placeable block an admin puts down to build the farm. */
    /**
     * The admin's Farm Plot item.
     *
     * A hay block, because it has to go down ANYWHERE. Torchflower seeds
     * were the obvious choice and the wrong one: seeds only plant on
     * tilled farmland, so on any other block the click did nothing at all
     * and the item looked broken. The hay is swapped for the marker crop
     * a tick after it lands, so what you place is not what you get, but
     * it always places.
     */
    public ItemStack createPlotItem(int amount) {
        ItemStack item = new ItemStack(Material.HAY_BLOCK, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Farm Plot");
        meta.setLore(List.of(
                ChatColor.GRAY + "Place anywhere to add a tile to the",
                ChatColor.GRAY + "shared farm. Everyone sees their own crop.",
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
        plugin.getLogger().info("Loaded " + plots.size() + " farm plots.");
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
            plugin.getLogger().warning("Couldn't save farm plots: " + ex.getMessage());
        }
    }
}
