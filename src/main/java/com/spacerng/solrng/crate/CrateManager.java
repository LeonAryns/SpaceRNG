package com.spacerng.solrng.crate;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.consumable.Consumable;
import com.spacerng.solrng.consumable.ConsumableManager;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Crates: the types from config, where they stand in the world, and
 * everything that happens when a key meets one.
 *
 * A crate is a block, not an NPC. An admin points at any block and names
 * the crate it should be; the plugin remembers the coordinates in
 * crates.yml, the same way the farm remembers its plots. Keys are ordinary
 * consumables, so milestones, the pass and Key Finder can hand them out
 * without knowing crates exist.
 */
public class CrateManager {

    private static final int INVENTORY_STORAGE = 36;

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final Map<Location, String> placed = new LinkedHashMap<>();
    // Lines for worlds that weren't loaded when crates.yml was read. Kept
    // verbatim so the next save does not quietly delete them.
    private final List<String> unresolved = new ArrayList<>();
    private final Map<UUID, CrateSpin> spins = new HashMap<>();

    private int spinSteps = 32;
    private int slowestGap = 7;
    private int quickOpenMax = 25;

    public CrateManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        loadPlacements();
    }

    // ------------------------------------------------------------- config

    public void load(FileConfiguration config) {
        crates.clear();
        spinSteps = Math.max(8, config.getInt("crates.spin-steps", 32));
        slowestGap = Math.max(1, config.getInt("crates.slowest-gap-ticks", 7));
        quickOpenMax = Math.max(1, config.getInt("crates.quick-open-max", 25));

        ConfigurationSection types = config.getConfigurationSection("crates.types");
        if (types == null) {
            plugin.getLogger().info("No crates configured.");
            return;
        }
        for (String rawId : types.getKeys(false)) {
            ConfigurationSection c = types.getConfigurationSection(rawId);
            if (c == null) continue;
            String id = rawId.toLowerCase(Locale.ROOT);

            List<CrateReward> rewards = new ArrayList<>();
            int line = 0;
            for (Map<?, ?> raw : c.getMapList("rewards")) {
                line++;
                try {
                    CrateReward reward = parseReward(raw);
                    if (reward == null) {
                        plugin.getLogger().warning("Crate '" + id + "' reward " + line
                                + " has no weight or no reward type, skipped.");
                    } else {
                        rewards.add(reward);
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Crate '" + id + "' reward " + line
                            + " is malformed: " + ex.getMessage());
                }
            }
            if (rewards.isEmpty()) {
                plugin.getLogger().warning("Crate '" + id + "' has no valid rewards, skipped.");
                continue;
            }
            crates.put(id, new Crate(id, c.getString("display", rawId), c.getStringList("colors"),
                    c.getString("key", id + "_key").toLowerCase(Locale.ROOT),
                    c.getString("key-source", ""), List.copyOf(rewards),
                    c.getDouble("jackpot-below", 0.02)));
        }
        plugin.getLogger().info("Loaded " + crates.size() + " crates.");
    }

    /**
     * One reward line. Public because the boss event pays out of a
     * table written in exactly this vocabulary, and two parsers for
     * one format is how the two drift apart.
     */
    public CrateReward parseReward(Map<?, ?> raw) {
        double weight = number(raw.get("weight"), 0.0);
        if (weight <= 0.0) return null;
        long amount = Math.max(1L, (long) number(raw.get("amount"), 1.0));
        Material icon = raw.get("icon") == null ? null : Material.matchMaterial(String.valueOf(raw.get("icon")));
        String name = raw.get("name") == null ? null : String.valueOf(raw.get("name"));

        if (raw.containsKey("coins")) return currency(CrateReward.Type.COINS, raw.get("coins"), weight, icon, name);
        if (raw.containsKey("gems")) return currency(CrateReward.Type.GEMS, raw.get("gems"), weight, icon, name);
        if (raw.containsKey("money")) return currency(CrateReward.Type.MONEY, raw.get("money"), weight, icon, name);
        if (raw.containsKey("credits")) {
            return currency(CrateReward.Type.CREDITS, raw.get("credits"), weight, icon, name);
        }
        if (raw.containsKey("tickets")) {
            return new CrateReward(CrateReward.Type.TICKETS, "",
                    Math.max(1L, (long) number(raw.get("tickets"), 1.0)), weight, icon, name);
        }
        // A boost is three numbers in one line: which stat, how much, and
        // for how long. The stat and the percentage share `target` so the
        // record does not grow a field only one type would ever use.
        if (raw.containsKey("boost")) {
            String stat = String.valueOf(raw.get("boost")).toUpperCase(Locale.ROOT);
            double percent = number(raw.get("percent"), 0.0);
            long minutes = Math.max(1L, (long) number(raw.get("minutes"), 15.0));
            if (percent <= 0.0) return null;
            return new CrateReward(CrateReward.Type.BOOST, stat + ":" + trimPercent(percent),
                    minutes, weight, icon, name);
        }
        // Forever, not for a while. Luck is a percentage, Speed is the
        // flat points the rest of the plugin counts Speed in.
        if (raw.containsKey("permanent")) {
            String stat = String.valueOf(raw.get("permanent")).toUpperCase(Locale.ROOT);
            double value = number(raw.get("percent"), number(raw.get("points"), 0.0));
            if (value <= 0.0) return null;
            return new CrateReward(CrateReward.Type.PERMANENT, stat + ":" + trimPercent(value),
                    1L, weight, icon, name);
        }
        if (raw.containsKey("consumable")) {
            return new CrateReward(CrateReward.Type.CONSUMABLE,
                    String.valueOf(raw.get("consumable")).toLowerCase(Locale.ROOT), amount, weight, icon, name);
        }
        if (raw.containsKey("drop")) {
            Rarity rarity = Rarity.valueOf(String.valueOf(raw.get("drop")).toUpperCase(Locale.ROOT));
            return new CrateReward(CrateReward.Type.DROP, rarity.name(), amount, weight, icon, name);
        }
        return null;
    }

    /** "20" rather than "20.0", so the label reads like a person wrote it. */
    private static String trimPercent(double value) {
        String text = String.format("%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private static CrateReward currency(CrateReward.Type type, Object value, double weight,
                                        Material icon, String name) {
        return new CrateReward(type, "", Math.max(1L, (long) number(value, 0.0)), weight, icon, name);
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public Map<String, Crate> getAll() {
        return crates;
    }

    public Crate get(String id) {
        return id == null ? null : crates.get(id.toLowerCase(Locale.ROOT));
    }

    public int quickOpenMax() {
        return quickOpenMax;
    }

    // ---------------------------------------------------------- placement

    private void loadPlacements() {
        placed.clear();
        unresolved.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String line : yml.getStringList("placed")) {
            String[] parts = line.split(";");
            if (parts.length != 5) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) {
                unresolved.add(line);
                continue;
            }
            try {
                placed.put(new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])), parts[4].toLowerCase(Locale.ROOT));
            } catch (NumberFormatException ignored) {
                // A hand-edited line that doesn't parse is dropped, not fatal.
            }
        }
    }

    /** Picks up the crates of a world that loaded after the plugin did. */
    public void resolveWorld(World world) {
        for (java.util.Iterator<String> it = unresolved.iterator(); it.hasNext(); ) {
            String[] parts = it.next().split(";");
            if (parts.length != 5 || !parts[0].equals(world.getName())) continue;
            try {
                placed.put(new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])), parts[4].toLowerCase(Locale.ROOT));
                it.remove();
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void savePlacements() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> lines = new ArrayList<>(unresolved);
        for (Map.Entry<Location, String> entry : placed.entrySet()) {
            Location at = entry.getKey();
            lines.add(at.getWorld().getName() + ";" + at.getBlockX() + ";" + at.getBlockY() + ";"
                    + at.getBlockZ() + ";" + entry.getValue());
        }
        yml.set("placed", lines);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save crates.yml: " + ex.getMessage());
        }
    }

    private static Location key(Location at) {
        return new Location(at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    /** The crate standing on this block, or null. */
    public Crate crateAt(Block block) {
        if (block == null) return null;
        String id = placed.get(key(block.getLocation()));
        return id == null ? null : crates.get(id);
    }

    public void place(Block block, Crate crate) {
        placed.put(key(block.getLocation()), crate.id());
        savePlacements();
    }

    public boolean remove(Block block) {
        boolean had = placed.remove(key(block.getLocation())) != null;
        if (had) savePlacements();
        return had;
    }

    public Map<Location, String> placements() {
        return Collections.unmodifiableMap(placed);
    }

    // --------------------------------------------------------------- keys

    /** The crate a consumable id opens, or null when it isn't a key. */
    public Crate crateForKey(String consumableId) {
        for (Crate crate : crates.values()) {
            if (crate.keyId().equals(consumableId)) return crate;
        }
        return null;
    }

    private boolean isKeyFor(ItemStack stack, Crate crate) {
        if (stack == null) return false;
        Consumable consumable = plugin.getConsumableManager().from(stack);
        return consumable != null && consumable.id().equals(crate.keyId());
    }

    public int keysHeld(Player player, Crate crate) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (int slot = 0; slot < INVENTORY_STORAGE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isKeyFor(stack, crate)) count += stack.getAmount();
        }
        return count;
    }

    /** All or nothing: a partial take never happens. */
    private boolean takeKeys(Player player, Crate crate, int amount) {
        if (keysHeld(player, crate) < amount) return false;
        PlayerInventory inventory = player.getInventory();
        int left = amount;
        for (int slot = 0; slot < INVENTORY_STORAGE && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isKeyFor(stack, crate)) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
            left -= take;
        }
        return true;
    }

    public String keyName(Crate crate) {
        Consumable key = plugin.getConsumableManager().get(crate.keyId());
        return key == null ? ChatColor.YELLOW + crate.keyId() : plugin.getConsumableManager().styledName(key);
    }

    public void noKey(Player player, Crate crate) {
        player.sendMessage(ChatColor.RED + "You need a " + keyName(crate) + ChatColor.RED
                + " to open the " + styledName(crate) + ChatColor.RED + ".");
        if (!crate.keySource().isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  " + crate.keySource());
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }

    // ------------------------------------------------------------ opening

    public void open(Player player, Block block, Crate crate) {
        open(player, block.getLocation().add(0.5, 1.0, 0.5), crate);
    }

    /** True once this crate stands somewhere in the world. */
    public boolean isPlaced(Crate crate) {
        return placed.containsValue(crate.id());
    }

    /**
     * The same opening, at a point rather than at a block.
     *
     * A box handed out by an event has to be openable where the player is
     * standing, or a reward nobody can reach is not a reward.
     */
    public void open(Player player, org.bukkit.Location at, Crate crate) {
        if (spins.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Finish the crate you are already opening first.");
            return;
        }
        if (!takeKeys(player, crate, 1)) {
            noKey(player, crate);
            return;
        }
        // Decided before a single frame is drawn. The spin is a show put on
        // for a result that already exists, so nothing the menu does can
        // change what comes out.
        CrateSpin spin = new CrateSpin(plugin, this, player, crate, crate.pick(),
                at, spinSteps, slowestGap);
        spins.put(player.getUniqueId(), spin);
        spin.start();
    }

    /**
     * Opens a stack of keys at once, with no spin.
     *
     * Somebody holding forty keys wants the forty rewards, not forty
     * animations. The burst still plays once, and anything rare enough to
     * be a jackpot is still announced one by one.
     */
    public void quickOpen(Player player, Block block, Crate crate) {
        if (spins.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Finish the crate you are already opening first.");
            return;
        }
        int amount = Math.min(quickOpenMax, keysHeld(player, crate));
        if (amount <= 0 || !takeKeys(player, crate, amount)) {
            noKey(player, crate);
            return;
        }

        Map<CrateReward, Integer> won = new LinkedHashMap<>();
        boolean jackpot = false;
        for (int i = 0; i < amount; i++) {
            CrateReward reward = crate.pick();
            grant(player, crate, reward, false);
            won.merge(reward, 1, Integer::sum);
            jackpot |= crate.isJackpot(reward);
        }

        List<Map.Entry<CrateReward, Integer>> rows = new ArrayList<>(won.entrySet());
        rows.sort(Comparator.comparingDouble((Map.Entry<CrateReward, Integer> e) -> crate.chanceOf(e.getKey())));

        player.sendMessage("");
        player.sendMessage(styledName(crate) + ChatColor.GRAY + "  opened " + ChatColor.WHITE + amount + "x");
        for (Map.Entry<CrateReward, Integer> row : rows) {
            boolean rare = crate.isJackpot(row.getKey());
            player.sendMessage((rare ? ChatColor.GOLD : ChatColor.YELLOW) + Lore.BULLET + " "
                    + ChatColor.WHITE + row.getValue() + "x " + ChatColor.RESET + label(row.getKey())
                    + ChatColor.DARK_GRAY + "  (" + chanceText(crate.chanceOf(row.getKey())) + ")");
        }
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
        CrateFx.burst(plugin, player, block.getLocation().add(0.5, 1.0, 0.5), crate, jackpot);
    }

    /** Pays one reward. `tell` is false inside a quick open, which summarises instead. */
    public void grant(Player player, Crate crate, CrateReward reward, boolean tell) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        switch (reward.type()) {
            case COINS -> data.addTokens(reward.amount());
            case GEMS -> data.addShards(reward.amount());
            case CREDITS -> data.addPoints(reward.amount());
            case MONEY -> {
                var registration = Bukkit.getServicesManager()
                        .getRegistration(net.milkbowl.vault.economy.Economy.class);
                if (registration != null) registration.getProvider().depositPlayer(player, reward.amount());
            }
            case CONSUMABLE -> {
                Consumable consumable = plugin.getConsumableManager().get(reward.target());
                if (consumable != null) {
                    plugin.getConsumableManager().give(player, consumable, (int) Math.min(64, reward.amount()));
                } else {
                    plugin.getLogger().warning("Crate '" + crate.id() + "' pays unknown consumable '"
                            + reward.target() + "'.");
                }
            }
            case TICKETS -> data.setPerkTickets(data.getPerkTickets() + reward.amount());
            case BOOST -> data.applyBoost(reward.boostStat(),
                    1.0 + reward.boostPercent() / 100.0, reward.amount() * 60_000L);
            case PERMANENT -> {
                if (reward.permanentStat().equals("SPEED")) {
                    data.addBonusSpeed(reward.permanentAmount());
                } else {
                    // Luck is written as a percentage and stored as the
                    // fraction the flat Luck pile is kept in.
                    data.addBonusLuck(reward.permanentAmount() / 100.0);
                }
            }
            case DROP -> giveDrops(player, data, Rarity.valueOf(reward.target()), reward.amount());
        }

        if (tell) {
            player.sendMessage(styledName(crate) + ChatColor.GRAY + "  You won " + ChatColor.RESET + label(reward)
                    + ChatColor.DARK_GRAY + "  (" + chanceText(crate.chanceOf(reward)) + ")");
        }
        if (crate.isJackpot(reward)) {
            String line = styledName(crate) + ChatColor.GRAY + "  " + ChatColor.WHITE + player.getName()
                    + ChatColor.GRAY + " won " + ChatColor.RESET + label(reward) + ChatColor.DARK_GRAY
                    + "  (" + chanceText(crate.chanceOf(reward)) + ")";
            for (Player online : Bukkit.getOnlinePlayers()) {
                // The opener already got their own line unless this was a
                // quick open, which prints no per-reward lines.
                if (!online.equals(player) || !tell) online.sendMessage(line);
            }
        }
        plugin.getScoreboardManager().update(player);
    }

    /**
     * Drops of one rarity, picked with the same weights the roll uses, so
     * the chase entries inside a band stay as hard to get from a crate as
     * they are from a Starforge.
     */
    private void giveDrops(Player player, PlayerData data, Rarity rarity, long amount) {
        List<RollableItem> pool = new ArrayList<>();
        double total = 0.0;
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            if (item.getRarity() != rarity) continue;
            pool.add(item);
            total += item.getRollWeight();
        }
        if (pool.isEmpty() || total <= 0.0) return;

        for (int i = 0; i < Math.min(64, amount); i++) {
            double roll = ThreadLocalRandom.current().nextDouble() * total;
            RollableItem drop = pool.get(pool.size() - 1);
            double cumulative = 0.0;
            for (RollableItem item : pool) {
                cumulative += item.getRollWeight();
                if (roll < cumulative) {
                    drop = item;
                    break;
                }
            }
            if (!data.hasDiscovered(drop.getDisplayName())) {
                data.markDiscovered(drop.getDisplayName());
                plugin.getFoundCounts().record(drop.getDisplayName());
            }
            ItemStack item = plugin.getRollListener().buildTaggedItem(drop);
            com.spacerng.solrng.player.Stash.give(plugin, player, item);
        }
    }

    // ------------------------------------------------------------ display

    public String styledName(Crate crate) {
        return crate.colors().isEmpty()
                ? ChatColor.GOLD + "" + ChatColor.BOLD + crate.display()
                : plugin.getRarityManager().buildStyle(crate.colors(), true, false, false).apply(crate.display());
    }

    /** "25K Coins", "3x Luck Potion", "2x Rare drops". */
    public String label(CrateReward reward) {
        if (reward.name() != null && !reward.name().isBlank()) {
            return ChatColor.translateAlternateColorCodes('&', reward.name());
        }
        return switch (reward.type()) {
            case COINS -> Currency.COINS.amount(reward.amount());
            case GEMS -> Currency.GEMS.amount(reward.amount());
            case MONEY -> Currency.MONEY.amount(reward.amount());
            case CREDITS -> Currency.CREDITS.amount(reward.amount());
            case TICKETS -> ChatColor.AQUA + "" + reward.amount()
                    + (reward.amount() == 1 ? " Perk Ticket" : " Perk Tickets");
            case BOOST -> ChatColor.GREEN + "+" + trimPercent(reward.boostPercent()) + "% "
                    + boostName(reward.boostStat()) + ChatColor.GRAY + " for " + reward.amount() + "m";
            case PERMANENT -> ChatColor.LIGHT_PURPLE + "+" + trimPercent(reward.permanentAmount())
                    + (reward.permanentStat().equals("SPEED") ? " Speed" : "% Luck")
                    + ChatColor.GRAY + " permanently";
            case CONSUMABLE -> {
                Consumable consumable = plugin.getConsumableManager().get(reward.target());
                String name = consumable == null
                        ? ChatColor.LIGHT_PURPLE + reward.target()
                        : plugin.getConsumableManager().styledName(consumable);
                yield (reward.amount() > 1 ? ChatColor.WHITE + "" + reward.amount() + "x " : "") + name;
            }
            case DROP -> {
                Rarity rarity = Rarity.valueOf(reward.target());
                yield ChatColor.WHITE + "" + reward.amount() + "x "
                        + plugin.getRarityManager().style(rarity, rarity.displayName())
                        + ChatColor.GRAY + (reward.amount() == 1 ? " drop" : " drops");
            }
        };
    }

    /** The picture of a reward, for the preview and the reel. Never redeemable. */
    public ItemStack icon(Crate crate, CrateReward reward) {
        ConsumableManager consumables = plugin.getConsumableManager();
        Consumable consumable = reward.type() == CrateReward.Type.CONSUMABLE
                ? consumables.get(reward.target()) : null;
        boolean counted = reward.type() == CrateReward.Type.CONSUMABLE || reward.type() == CrateReward.Type.DROP;
        int stack = (int) Math.max(1L, Math.min(64L, counted ? reward.amount() : 1L));

        ItemStack item = reward.icon() == null && consumable != null
                ? consumables.build(consumable, stack)
                : new ItemStack(reward.icon() != null ? reward.icon() : defaultIcon(reward), stack);

        ItemMeta meta = item.getItemMeta();
        // A picture in a menu, not the reward: without this a consumable's
        // icon would carry the tag that makes it redeemable.
        meta.getPersistentDataContainer().remove(consumables.idKey());
        meta.setDisplayName(label(reward));

        List<String> lore = new ArrayList<>();
        if (crate.isJackpot(reward)) {
            lore.add(Lore.state("jackpot"));
            lore.add("");
        }
        lore.add(Lore.stat(ChatColor.AQUA, "Chance", chanceText(crate.chanceOf(reward))));
        lore.add(Lore.footnote(describe(reward, consumable)));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String describe(CrateReward reward, Consumable consumable) {
        return switch (reward.type()) {
            case COINS -> "Paid straight into your Coins.";
            case GEMS -> "Paid straight into your Gems.";
            case MONEY -> "Paid straight into your Money.";
            case CREDITS -> "Paid straight into your Credits.";
            case TICKETS -> "Perk rolls, spent in /perks.";
            case BOOST -> "Runs on top of everything else you have.";
            case PERMANENT -> "Yours forever, on top of everything else.";
            case CONSUMABLE -> consumable == null || consumable.description().isEmpty()
                    ? "Lands in your inventory." : consumable.description();
            case DROP -> "Random drops of that rarity, logged in your index.";
        };
    }

    private static Material defaultIcon(CrateReward reward) {
        return switch (reward.type()) {
            case COINS -> Material.RAW_GOLD;
            case GEMS -> Material.PRISMARINE_CRYSTALS;
            case MONEY -> Material.EMERALD;
            case CREDITS -> Material.AMETHYST_SHARD;
            case TICKETS -> Material.NAME_TAG;
            case BOOST -> Material.EXPERIENCE_BOTTLE;
            case PERMANENT -> Material.NETHER_STAR;
            case CONSUMABLE -> Material.PAPER;
            case DROP -> switch (Rarity.valueOf(reward.target())) {
                case COMMON -> Material.COBBLESTONE;
                case UNCOMMON -> Material.IRON_INGOT;
                case RARE -> Material.GOLD_INGOT;
                case EPIC -> Material.DIAMOND;
                case LEGENDARY -> Material.NETHERITE_INGOT;
                case MYTHICAL -> Material.NETHER_STAR;
                default -> Material.CONDUIT;
            };
        };
    }

    /** A fraction as a percentage with only the decimals it needs: 12.5%, 0.4%, 0.05%. */
    /** The boost system's key as a player reads it. */
    public static String boostName(String stat) {
        return switch (stat) {
            case "TOKENS" -> "Coins";
            case "GEMS" -> "Gems";
            case "ENCHANT_PROC" -> "Enchant Proc";
            case "MONEY" -> "Money";
            case "SPEED" -> "Speed";
            default -> "Luck";
        };
    }

    public static String chanceText(double fraction) {
        double shown = fraction * 100.0;
        if (shown <= 0.0) return "0%";
        int decimals = 0;
        while (decimals < 4 && shown * Math.pow(10, decimals) < 10.0) decimals++;
        String text = String.format(Locale.ROOT, "%." + decimals + "f", shown);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "");
            if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        }
        return text + "%";
    }

    // --------------------------------------------------------- lifecycle

    void spinEnded(UUID uuid) {
        spins.remove(uuid);
    }

    /** Ends a player's spin without the show, paying out. For logouts. */
    public void endSpin(UUID uuid) {
        CrateSpin spin = spins.get(uuid);
        if (spin != null) spin.finish(false, false);
    }

    /** Pays every spin still running. Called on shutdown, before saving. */
    public void finishAll() {
        for (CrateSpin spin : new ArrayList<>(spins.values())) {
            spin.finish(false, false);
        }
    }
}
