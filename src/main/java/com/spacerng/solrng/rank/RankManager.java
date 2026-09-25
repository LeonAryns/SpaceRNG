package com.spacerng.solrng.rank;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranks: Linked from a Discord link, then Comet, Nova and Supernova bought
 * with Credits. A rank multiplies Money, Luck and Speed, opens private
 * vault pages, and unlocks commands and cosmetics.
 *
 * A player counts as the rank in their save file, or the free Linked one
 * while their Discord is linked, whichever is higher. Everything about a
 * rank comes from config, so a price or a perk is a config change.
 */
public class RankManager {

    private final SolRNGPlugin plugin;
    private final Map<String, RankTier> tiers = new LinkedHashMap<>();
    private boolean enabled = true;
    private boolean flyCommand = true;
    private boolean nickCommand = true;
    private boolean sizeCommand = true;
    private long keyallCooldownMillis = 8L * 60L * 60L * 1000L;
    private double sizeMin = 0.5;
    private double sizeMax = 1.5;
    private BukkitTask nameTask;

    public RankManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        tiers.clear();
        enabled = config.getBoolean("ranks.enabled", true);
        flyCommand = config.getBoolean("ranks.commands.fly", true);
        nickCommand = config.getBoolean("ranks.commands.nick", true);
        sizeCommand = config.getBoolean("ranks.commands.size", true);
        keyallCooldownMillis = Math.max(0L, config.getLong("ranks.keyall-cooldown-hours", 8L)) * 3600_000L;
        sizeMin = config.getDouble("ranks.size.min", 0.5);
        sizeMax = config.getDouble("ranks.size.max", 1.5);

        ConfigurationSection section = config.getConfigurationSection("ranks.tiers");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(id);
                if (t == null) continue;
                List<String> colors = t.getStringList("colors");
                if (colors.isEmpty()) colors = List.of("#FFFFFF");
                // The badge letter. A rank with nothing set wears the first
                // letter of its own name, which is what Leon asked for and
                // means a rank added later needs no extra key to look right.
                String display = t.getString("display", id);
                String letter = t.getString("letter", display.isEmpty()
                        ? "?" : display.substring(0, 1).toUpperCase(Locale.ROOT));
                tiers.put(id.toLowerCase(Locale.ROOT), new RankTier(id.toLowerCase(Locale.ROOT),
                        display, colors, t.getString("tag", ""), letter,
                        t.getString("icon", "NETHER_STAR"),
                        Math.max(0L, t.getLong("price-credits", 0L)),
                        Math.max(1.0, t.getDouble("multiplier", 1.0)),
                        t.getString("keyall-crate", "").toLowerCase(Locale.ROOT),
                        Math.max(0, t.getInt("keyall-amount", 0)),
                        Math.max(0, t.getInt("vault-pages", 0)),
                        t.getBoolean("fly", false), t.getBoolean("nick", false),
                        t.getBoolean("size", false), t.getBoolean("rgb-name", false)));
            }
        }
        plugin.getLogger().info("Loaded " + tiers.size() + " ranks.");
    }

    public boolean isEnabled() { return enabled; }
    public boolean flyCommand() { return flyCommand; }
    public boolean nickCommand() { return nickCommand; }
    public boolean sizeCommand() { return sizeCommand; }
    public double sizeMin() { return sizeMin; }
    public double sizeMax() { return sizeMax; }
    public long keyallCooldownMillis() { return keyallCooldownMillis; }

    /** The ladder, cheapest first, in config order. */
    public List<RankTier> tiers() {
        return new ArrayList<>(tiers.values());
    }

    public RankTier tier(String id) {
        return id == null ? null : tiers.get(id.toLowerCase(Locale.ROOT));
    }

    /** Where a rank sits on the ladder, or -1 for no rank. */
    public int indexOf(RankTier tier) {
        if (tier == null) return -1;
        int i = 0;
        for (String id : tiers.keySet()) {
            if (id.equals(tier.id())) return i;
            i++;
        }
        return -1;
    }

    /** The first rank on the ladder, the one a Discord link grants. */
    public RankTier linkedTier() {
        for (RankTier tier : tiers.values()) return tier;
        return null;
    }

    /**
     * What each rank says for itself in /ranks when config gives it no
     * blurb. Two lines at most: the pitch is the one line of voice a rank
     * gets, and a third pushes the price off the bottom of the tooltip.
     */
    private static final Map<String, List<String>> DEFAULT_BLURBS = Map.of(
            "linked", List.of("Link your Discord and the server knows you.",
                    "It is also what switches your aura on."),
            "comet", List.of("The first step up, and the one that pays",
                    "for itself while you are away from the keyboard."),
            "nova", List.of("Fly over your farm, wear whatever name you",
                    "like, and roll with a quarter more of everything."),
            "supernova", List.of("The top of the ladder. The biggest aura",
                    "on the server, and a name that drifts to match."));

    /** The two line pitch under a rank's name in /ranks, from config or the default above. */
    public List<String> blurbOf(RankTier tier) {
        if (tier == null) return List.of();
        List<String> lines = plugin.getConfig().getStringList("ranks.tiers." + tier.id() + ".blurb");
        if (lines.isEmpty()) {
            lines = DEFAULT_BLURBS.getOrDefault(tier.id(),
                    List.of("Unlocks everything below it, and keeps it."));
        }
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }

    /** The rank a player counts as: bought, or Linked while their Discord is linked. */
    public RankTier rankOf(PlayerData data) {
        if (!enabled) return null;
        RankTier bought = tier(data.getRank());
        RankTier linked = plugin.getLinkedAccountManager().isLinked(data.getUuid()) ? linkedTier() : null;
        if (bought == null) return linked;
        if (linked == null) return bought;
        return indexOf(bought) >= indexOf(linked) ? bought : linked;
    }

    public RankTier rankOf(Player player) {
        return rankOf(plugin.getPlayerDataManager().get(player.getUniqueId()));
    }

    /** What the rank multiplies Money, Luck and Speed by. */
    public double multiplierOf(PlayerData data) {
        RankTier tier = rankOf(data);
        return tier == null ? 1.0 : tier.multiplier();
    }

    public boolean has(PlayerData data, String perk) {
        RankTier tier = rankOf(data);
        if (tier == null) return false;
        return switch (perk) {
            case "fly" -> tier.fly();
            case "nick" -> tier.nick();
            case "size" -> tier.size();
            case "rgb" -> tier.rgbName();
            default -> false;
        };
    }

    public int vaultPages(PlayerData data) {
        RankTier tier = rankOf(data);
        return tier == null ? 0 : tier.vaultPages();
    }

    /** The rank name in its own colours, with its tag. */
    public String styled(RankTier tier) {
        if (tier == null) return ChatColor.DARK_GRAY + "No rank";
        String name = tier.tag().isEmpty() ? tier.display() : tier.display() + " " + tier.tag();
        return tier.colors().size() > 1 ? Lore.gradient(name, true, tier.colors().toArray(new String[0]))
                : ChatColor.WHITE + name;
    }

    /**
     * The rank badge that goes in front of a name in tab and in chat:
     * one letter in brackets, the letter in the rank's own colours and
     * the brackets left dark so the letter is what the eye lands on.
     *
     * A letter rather than the symbol tab used to carry (V187). A symbol
     * says "this player has something"; a letter says which, which is the
     * whole point of selling four of them.
     */
    public String badgeOf(RankTier tier) {
        return badgeOf(tier, null);
    }

    /**
     * The same badge, painted in {@code stops} when a player has picked a
     * name colour (V190). A Supernova who paints their name gold and then
     * wears a purple letter in front of it looks like two people.
     */
    public String badgeOf(RankTier tier, String[] stops) {
        if (tier == null || tier.letter().isEmpty()) return "";
        String[] colours = stops != null && stops.length > 0
                ? stops : tier.colors().toArray(new String[0]);
        String letter = colours.length > 1
                ? Lore.gradient(tier.letter(), true, colours)
                : Lore.gradient(tier.letter(), true, colours[0]);
        return ChatColor.DARK_GRAY + "[" + letter + ChatColor.DARK_GRAY + "] ";
    }

    /** The badge for one player, which is the tier's unless they paint their own name. */
    public String badgeOf(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        String[] stops = plugin.getCosmeticManager() == null
                ? null : plugin.getCosmeticManager().stopsFor(data);
        return badgeOf(rankOf(data), stops);
    }

    /** The tag alone, in the rank colours. Kept for anywhere the symbol still reads better. */
    public String tagOf(RankTier tier) {
        if (tier == null || tier.tag().isEmpty()) return "";
        return (tier.colors().size() > 1
                ? Lore.gradient(tier.tag(), true, tier.colors().toArray(new String[0]))
                : ChatColor.WHITE + tier.tag()) + " ";
    }

    /**
     * Buys a rank with Credits. Refuses one the player already outranks and
     * a price they cannot pay.
     */
    public boolean buy(Player player, PlayerData data, RankTier tier) {
        if (tier == null || tier.price() <= 0) return false;
        RankTier current = rankOf(data);
        if (current != null && indexOf(current) >= indexOf(tier)) return false;
        if (!data.spendPoints(tier.price())) return false;
        data.setRank(tier.id());
        refreshName(player);
        // The Discord role follows the rank the moment it is bought,
        // rather than the next time they log in.
        if (plugin.getDiscordBot() != null) plugin.getDiscordBot().syncRoles(player);
        Bukkit.broadcastMessage(Lore.gradient("SpaceRNG", true, "#B388FF", "#40C4FF") + ChatColor.DARK_GRAY + " » "
                + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " is now "
                + styled(tier) + ChatColor.GRAY + ".");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        return true;
    }

    // ------------------------------------------------------------- key all

    /** Milliseconds left on this player key all, 0 when it is ready. */
    public long keyallLeft(PlayerData data) {
        long since = System.currentTimeMillis() - data.getKeyallAt();
        return Math.max(0L, keyallCooldownMillis - since);
    }

    public static String timeLeft(long millis) {
        long seconds = millis / 1000L;
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    // ------------------------------------------------------- names in tab

    public void start() {
        stop();
        // Only animated names need a timer, and one step a second reads as a
        // drift rather than a flicker. One packet per rainbow player a second.
        nameTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
                if (has(data, "rgb")) refreshName(player);
            }
        }, 40L, 20L);
    }

    public void stop() {
        if (nameTask != null) nameTask.cancel();
        nameTask = null;
    }

    /**
     * The name in the tab list and in chat: the rank tag, then the nick or
     * the player name, in the rank colours. Supernova drifts through the
     * rainbow instead.
     */
    public void refreshName(Player player) {
        Component component = LegacyComponentSerializer.legacySection().deserialize(fullName(player));
        player.playerListName(component);
        player.displayName(component);
    }

    /**
     * The badge, the name in the rank's colours and the cosmetic title, as
     * legacy text. Also %spacerng_name%: the TAB plugin writes the tab list
     * itself and throws away the name set above, so the rank gradient only
     * shows in tab when TAB's format asks for this.
     */
    public String fullName(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        RankTier tier = rankOf(data);
        String title = plugin.getCosmeticManager() == null
                ? "" : plugin.getCosmeticManager().suffixOf(data);
        String[] stops = plugin.getCosmeticManager() == null
                ? null : plugin.getCosmeticManager().stopsFor(data);
        return badgeOf(tier, stops) + coloredName(player) + title;
    }

    /**
     * The nick or player name in the rank's colours and nothing else: a
     * gradient across the rank's stops, the drifting rainbow for a rank
     * with rgb-name, white without a rank. Chat uses this (V173), where the
     * rank shows only as the colour of the name.
     */
    public String coloredName(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        RankTier tier = rankOf(data);
        String name = data.getNick() == null || data.getNick().isEmpty() ? player.getName() : data.getNick();
        // The top rank paints its own name (V188), and it MOVES: the
        // gradient walks one way along the name for ever, one step a
        // second off the refresh task that already exists for this.
        //
        // With nothing picked it is the rank's OWN colours rather than the
        // rainbow it used to be. Leon asked for exactly that: "ik heb
        // liever de supernova color gradient". The rainbow said nothing
        // about which rank it was and fought every other colour on the
        // screen.
        if (has(data, "rgb")) {
            String[] picked = plugin.getCosmeticManager() == null
                    ? null : plugin.getCosmeticManager().stopsFor(data);
            String[] stops = picked != null ? picked
                    : (tier != null && !tier.colors().isEmpty()
                            ? tier.colors().toArray(new String[0]) : null);
            if (stops == null) return Lore.rainbow(name);
            // A lap every four seconds, from the wall clock so every
            // player's name is in step with every other one.
            double phase = (System.currentTimeMillis() % 4000L) / 4000.0;
            return stops.length == 1 ? Lore.gradient(name, false, stops)
                    : Lore.drift(name, false, phase, stops);
        }
        if (tier != null && tier.colors().size() > 1) {
            return Lore.gradient(name, false, tier.colors().toArray(new String[0]));
        }
        if (tier != null && tier.colors().size() == 1) {
            return Lore.gradient(name, false, tier.colors().get(0));
        }
        return ChatColor.WHITE + name;
    }
}
