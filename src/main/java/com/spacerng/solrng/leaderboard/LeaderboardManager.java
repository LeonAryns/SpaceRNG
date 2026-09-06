package com.spacerng.solrng.leaderboard;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leaderboards, and the daily farming payout that hangs off one.
 *
 * PlayerDataManager only holds players who are ONLINE, so a top-10 built
 * from it would quietly be a top-10-of-whoever-happens-to-be-connected.
 * This keeps its own index instead: a small row per player, written every
 * time their data is saved and persisted to leaderboard.yml, so an offline
 * player still holds their place and the board survives a restart.
 *
 * The farming board runs on a period that resets at a fixed hour. Two
 * numbers are tracked per player - lifetime and this-period - because a
 * payout has to be about what you did TODAY, while the all-time board is
 * the one worth bragging about.
 */
public class LeaderboardManager {

    /** One player's row in the index. */
    public record Entry(UUID uuid, String name, long farmedTotal, long farmedPeriod,
                        long rolls, int prestige, int discoveries,
                        int shinies, long coins, long money) {

        /** The same row with the daily counter zeroed. */
        Entry cleared() {
            return new Entry(uuid, name, farmedTotal, 0L, rolls, prestige, discoveries,
                    shinies, coins, money);
        }
    }

    /** Every board, in the order a menu should show them. */
    public static final List<String> BOARDS = List.of(
            "farming", "farming_total", "index", "shiny", "coins", "money", "rolls", "prestige");

    /**
     * The one place a board id turns into a number.
     *
     * This used to be written out three times - in /top, in the
     * placeholders and in the sort - which is three chances for a new
     * board to sort by one thing and display another.
     */
    public static long valueOf(String board, Entry entry) {
        return switch (board) {
            case "farming" -> entry.farmedPeriod();
            case "farming_total" -> entry.farmedTotal();
            case "rolls" -> entry.rolls();
            case "prestige" -> entry.prestige();
            case "shiny" -> entry.shinies();
            case "coins" -> entry.coins();
            case "money" -> entry.money();
            default -> entry.discoveries();
        };
    }

    public static String titleOf(String board) {
        return switch (board) {
            case "farming" -> "Farmers";
            case "farming_total" -> "Farmers (all time)";
            case "rolls" -> "Rollers";
            case "prestige" -> "Prestige";
            case "shiny" -> "Shiny Index";
            case "coins" -> "Coins";
            case "money" -> "Money";
            default -> "Total Index";
        };
    }

    public static String unitOf(String board) {
        return switch (board) {
            case "farming", "farming_total" -> "farmed";
            case "rolls" -> "rolls";
            case "prestige" -> "prestige";
            case "shiny" -> "shinies";
            case "coins" -> "Coins";
            case "money" -> "Money";
            default -> "drops";
        };
    }

    private final SolRNGPlugin plugin;
    private final File file;
    private net.milkbowl.vault.economy.Economy economy;
    private final Map<UUID, Entry> index = new HashMap<>();

    private ZoneId zone = ZoneId.of("UTC");
    private int resetHour = 0;
    private List<Long> payouts = List.of(150L, 75L, 25L);
    private long lastPayoutDay = 0L;

    public LeaderboardManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "leaderboard.yml");
    }

    // ------------------------------------------------------------ config

    public void load(FileConfiguration config) {
        try {
            zone = ZoneId.of(config.getString("leaderboard.timezone", "UTC"));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Bad leaderboard.timezone, using UTC.");
            zone = ZoneId.of("UTC");
        }
        resetHour = Math.max(0, Math.min(23, config.getInt("leaderboard.farming.reset-hour", 0)));

        List<Long> configured = new ArrayList<>();
        for (Object value : config.getList("leaderboard.farming.credit-payouts",
                List.of(150, 75, 25))) {
            try {
                configured.add(Long.parseLong(String.valueOf(value)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (!configured.isEmpty()) payouts = configured;

        loadIndex();
    }

    public List<Long> getPayouts() {
        return payouts;
    }

    // ------------------------------------------------------------- index

    /**
     * Called on every save. Cheap enough to run that often, and doing it
     * there means the index can never be more stale than the save file it
     * mirrors.
     */
    public void record(PlayerData data) {
        String name = Bukkit.getOfflinePlayer(data.getUuid()).getName();
        if (name == null) {
            Entry existing = index.get(data.getUuid());
            name = existing == null ? "Unknown" : existing.name();
        }
        index.put(data.getUuid(), new Entry(data.getUuid(), name,
                data.getCropsHarvested(), data.getCropsThisPeriod(),
                data.getTotalRolls(), data.getPrestige(), data.getDiscoveredItems().size(),
                data.getDiscoveredShiny().size(), data.getTokens(), balanceOf(data.getUuid())));
    }

    /**
     * The Vault balance, snapshotted whenever the player's data is
     * written.
     *
     * The board can't ask Vault while sorting - that would be an economy
     * lookup per player every time a menu redraws - so it reads the number
     * that was true at the last save, which for an online player is never
     * more than a couple of minutes old. With no economy installed the
     * previous figure is kept rather than zeroed, so the board degrades to
     * stale rather than to empty.
     */
    private long balanceOf(UUID uuid) {
        if (economy == null) {
            var registration = Bukkit.getServicesManager()
                    .getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (registration == null) {
                Entry previous = index.get(uuid);
                return previous == null ? 0L : previous.money();
            }
            economy = registration.getProvider();
        }
        try {
            return Math.round(economy.getBalance(Bukkit.getOfflinePlayer(uuid)));
        } catch (Exception ignored) {
            Entry previous = index.get(uuid);
            return previous == null ? 0L : previous.money();
        }
    }

    /** Top rows on a board, highest first. */
    public List<Entry> top(String board, int limit) {
        String id = board.toLowerCase();
        Comparator<Entry> order =
                Comparator.comparingLong((Entry entry) -> valueOf(id, entry)).reversed();

        List<Entry> rows = new ArrayList<>(index.values());
        rows.sort(order);
        return rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    /** A player's 1-based place on a board, or 0 if they aren't on it. */
    public int positionOf(String board, UUID uuid) {
        List<Entry> rows = top(board, Integer.MAX_VALUE);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).uuid().equals(uuid)) return i + 1;
        }
        return 0;
    }

    public Entry entryOf(UUID uuid) {
        return index.get(uuid);
    }

    public int size() {
        return index.size();
    }

    /** The reward for a given 1-based place, or 0 if it's out of the money. */
    public long payoutFor(int place) {
        return place >= 1 && place <= payouts.size() ? payouts.get(place - 1) : 0L;
    }

    // ------------------------------------------------------------ payout

    private long todayEpochDay() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        // The "day" rolls at reset-hour, not midnight, so a payout hour of
        // 18:00 doesn't split a session across two periods.
        return now.getHour() < resetHour ? now.toLocalDate().minusDays(1).toEpochDay()
                : now.toLocalDate().toEpochDay();
    }

    /** Seconds until the next reset, for the hologram's countdown. */
    public long secondsUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.withHour(resetHour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return java.time.Duration.between(now, next).getSeconds();
    }

    /** "9h 15m 10s" */
    public String resetCountdown() {
        long seconds = secondsUntilReset();
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m " + (seconds % 60) + "s";
    }

    /**
     * Pays out and rolls the period over if one is due. Runs on a timer,
     * so it fires whether or not anyone happens to be online at the hour.
     */
    /**
     * Re-reads every player who is online.
     *
     * The index used to be written only when somebody's data was saved,
     * which meant a board sat frozen for as long as they stayed connected
     * - you could farm for an hour and watch the hologram not move.
     * Thirty seconds of lag on a leaderboard is invisible; an hour of it
     * is a broken feature.
     */
    private void refreshOnline() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            record(plugin.getPlayerDataManager().get(online.getUniqueId()));
        }
    }

    public void tick() {
        refreshOnline();

        long today = todayEpochDay();
        if (lastPayoutDay == 0L) {
            // First run on a fresh install: start the clock rather than
            // immediately paying out a period nobody has played.
            lastPayoutDay = today;
            saveIndex();
            return;
        }
        if (today <= lastPayoutDay) return;

        runPayout();
        lastPayoutDay = today;
        saveIndex();
    }

    /** Awards the top places and clears everyone's period counter. */
    public void runPayout() {
        List<Entry> winners = top("farming", payouts.size());

        List<String> banner = new ArrayList<>();
        banner.add("");
        banner.add(ChatColor.GOLD + "" + ChatColor.BOLD + "★ FARMING PAYOUTS ★");
        boolean any = false;
        for (int i = 0; i < winners.size(); i++) {
            Entry entry = winners.get(i);
            if (entry.farmedPeriod() <= 0) continue;

            long reward = payoutFor(i + 1);
            any = true;
            banner.add(ChatColor.YELLOW + "#" + (i + 1) + " " + ChatColor.WHITE + entry.name()
                    + ChatColor.GRAY + "  " + String.format("%,d", entry.farmedPeriod()) + " farmed"
                    + ChatColor.GRAY + "  " + Currency.CREDITS.colour() + "+"
                    + Currency.CREDITS.amount(reward));
            awardCredits(entry.uuid(), reward);
        }
        if (!any) {
            banner.add(ChatColor.GRAY + "Nobody farmed anything this period.");
        }
        banner.add("");

        for (Player online : Bukkit.getOnlinePlayers()) {
            for (String line : banner) online.sendMessage(line);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        resetPeriod();
    }

    /**
     * The prize goes onto the live object for anyone online, and straight
     * into their save file otherwise - a winner who logged off before the
     * hour still gets paid.
     *
     * Credits, deliberately. They're the store's currency everywhere
     * else, and this is the one place free play mints them - a daily
     * board worth real money is the reason to come back tomorrow, and
     * that's worth more than keeping the currency pure.
     */
    private void awardCredits(UUID uuid, long credits) {
        if (credits <= 0) return;

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.getPlayerDataManager().get(uuid).addPoints(credits);
            online.sendMessage(Currency.CREDITS.colour() + "" + ChatColor.BOLD + "+"
                    + Currency.CREDITS.amount(credits)
                    + ChatColor.RESET + ChatColor.GRAY + " from the farming payout.");
            return;
        }
        plugin.getPlayerDataManager().awardOffline(uuid, credits);
    }

    private void resetPeriod() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getPlayerDataManager().get(online.getUniqueId()).setCropsThisPeriod(0L);
        }
        plugin.getPlayerDataManager().clearOfflinePeriods();

        for (Map.Entry<UUID, Entry> row : new HashMap<>(index).entrySet()) {
            index.put(row.getKey(), row.getValue().cleared());
        }
    }

    // ----------------------------------------------------------- storage

    private void loadIndex() {
        index.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        lastPayoutDay = yml.getLong("last-payout-day", 0L);

        var section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String raw : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(raw);
                index.put(uuid, new Entry(uuid,
                        section.getString(raw + ".name", "Unknown"),
                        section.getLong(raw + ".farmed-total", 0L),
                        section.getLong(raw + ".farmed-period", 0L),
                        section.getLong(raw + ".rolls", 0L),
                        section.getInt(raw + ".prestige", 0),
                        section.getInt(raw + ".discoveries", 0),
                        section.getInt(raw + ".shinies", 0),
                        section.getLong(raw + ".coins", 0L),
                        section.getLong(raw + ".money", 0L)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("Leaderboard index holds " + index.size() + " player(s).");
    }

    public void saveIndex() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("last-payout-day", lastPayoutDay);
        for (Entry entry : index.values()) {
            String path = "players." + entry.uuid();
            yml.set(path + ".name", entry.name());
            yml.set(path + ".farmed-total", entry.farmedTotal());
            yml.set(path + ".farmed-period", entry.farmedPeriod());
            yml.set(path + ".rolls", entry.rolls());
            yml.set(path + ".prestige", entry.prestige());
            yml.set(path + ".discoveries", entry.discoveries());
            yml.set(path + ".shinies", entry.shinies());
            yml.set(path + ".coins", entry.coins());
            yml.set(path + ".money", entry.money());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save the leaderboard index: " + ex.getMessage());
        }
    }
}
