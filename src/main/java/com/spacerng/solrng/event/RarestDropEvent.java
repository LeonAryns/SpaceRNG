package com.spacerng.solrng.event;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.rarity.RollableItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rarest drop battle (V398, reworked in V406 for Leon's Sunday).
 *
 * At 00:00 Amsterdam time on the event day (CET or CEST, whichever is
 * on) the battle starts, and when that day is `rarest-drop.reset-on`
 * every player is reset first (AccountReset.everyone). From then on, for
 * `length-hours` (24), each player's single rarest roll counts. When the
 * clock runs out the top three are paid Credits (500, 250, 100 by
 * default) and stay on the board.
 *
 * The start is a moment kept in rarest-drop.yml, not a time of day
 * worked out again on every read. So a server that was down at midnight
 * starts the battle (and the reset) the moment it is back, still on the
 * event day, and gives it the full 24 hours; a reload in the middle
 * changes nothing; and the reset runs at most once per date, because the
 * date is written down before it runs.
 *
 * Rarest means the highest label; a shiny counts as its label times
 * 1 / shiny.chance (100), the way a shiny actually is that much rarer.
 */
public final class RarestDropEvent {

    /** One player's best roll of the battle. */
    public record Entry(UUID uuid, String name, String item, String rarity, long odds, boolean shiny,
                        double score) {
    }

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<UUID, Entry> best = new HashMap<>();
    /** The event date the entries belong to. */
    private String day = "";
    /** When this battle started and when it ends, epoch millis; 0 is not started. */
    private long startedAt;
    private long endsAt;
    private boolean paid;
    /** The last date whose reset of every player has run. */
    private String resetDone = "";
    private boolean dirty;

    public RarestDropEvent(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rarest-drop.yml");
        load();
        // Every second: the start lands within a second of midnight.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------ time

    public ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("rarest-drop.zone", "Europe/Amsterdam"));
        } catch (RuntimeException ex) {
            return ZoneId.of("Europe/Amsterdam");
        }
    }

    private DayOfWeek weekday() {
        try {
            return DayOfWeek.valueOf(plugin.getConfig().getString("rarest-drop.day", "SUNDAY").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DayOfWeek.SUNDAY;
        }
    }

    /** The date of the battle: rarest-drop.only-on when set, else this week's day or the next one. */
    public LocalDate eventDay(ZonedDateTime now) {
        // V404, Leon: a one-off. With rarest-drop.only-on set to a date the
        // battle runs on that day alone and never again.
        String only = plugin.getConfig().getString("rarest-drop.only-on", "");
        if (only != null && !only.isBlank()) {
            try {
                return LocalDate.parse(only.trim());
            } catch (java.time.format.DateTimeParseException ex) {
                plugin.getLogger().warning("rarest-drop.only-on is not a date (YYYY-MM-DD): " + only);
            }
        }
        return now.toLocalDate().with(TemporalAdjusters.nextOrSame(weekday()));
    }

    private long startOf(LocalDate date) {
        return date.atStartOfDay(zone()).toInstant().toEpochMilli();
    }

    private long lengthMillis() {
        double hours = plugin.getConfig().getDouble("rarest-drop.length-hours", 24.0);
        return (long) (Math.max(0.1, hours) * 3_600_000L);
    }

    /** The date whose start also resets every player, or blank for none (V406). */
    public String resetOn() {
        String raw = plugin.getConfig().getString("rarest-drop.reset-on", "");
        return raw == null ? "" : raw.trim();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("rarest-drop.enabled", true);
    }

    /** True while the battle runs and rolls count. */
    public boolean isActive() {
        if (!enabled() || startedAt <= 0 || paid) return false;
        long now = System.currentTimeMillis();
        return now >= startedAt && now < endsAt;
    }

    /** True once a battle has started, so its board is shown to everybody (V406). */
    public boolean hasStarted() {
        return startedAt > 0;
    }

    /** True once the last battle is over and paid. */
    public boolean isOver() {
        return startedAt > 0 && paid;
    }

    /** Millis of the next start still to come, or 0 when none is coming. */
    public long nextStart() {
        if (!enabled()) return 0L;
        LocalDate date = eventDay(ZonedDateTime.now(zone()));
        if (startedFor(date)) return 0L;
        long start = startOf(date);
        // Past its start but not yet begun: the tick picks it up within a second.
        return System.currentTimeMillis() < start + lengthMillis() ? start : 0L;
    }

    /** Whether the reset of every player is still to run with the next start. */
    public boolean resetArmed() {
        LocalDate date = eventDay(ZonedDateTime.now(zone()));
        return nextStart() > 0 && date.toString().equals(resetOn()) && !date.toString().equals(resetDone);
    }

    private boolean startedFor(LocalDate date) {
        return startedAt > 0 && date.toString().equals(day);
    }

    /** Seconds until the battle ends while it runs, or until it starts. 0 when neither. */
    public long secondsLeft() {
        long now = System.currentTimeMillis();
        if (isActive()) return Math.max(0L, (endsAt - now) / 1000L);
        long next = nextStart();
        return next > 0 ? Math.max(0L, (next - now) / 1000L) : 0L;
    }

    /** "5h 12m 3s", with days in front when there are any. */
    public static String clock(long seconds) {
        return (seconds >= 86400 ? seconds / 86400 + "d " : "") + (seconds % 86400) / 3600 + "h "
                + (seconds % 3600) / 60 + "m " + seconds % 60 + "s";
    }

    /** A moment as Amsterdam time, for the admin: "Sun 18 Oct 00:00". */
    public String when(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(zone())
                .format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm", java.util.Locale.ENGLISH));
    }

    public long startedAt() {
        return startedAt;
    }

    public long endsAt() {
        return endsAt;
    }

    public String resetDone() {
        return resetDone;
    }

    public List<Integer> payouts() {
        List<Integer> payouts = plugin.getConfig().getIntegerList("rarest-drop.payouts");
        return payouts.isEmpty() ? List.of(500, 250, 100) : payouts;
    }

    // ------------------------------------------------------------ rolls

    /** Called for every real roll that lands, the extra ones from Speed too. */
    public void record(Player player, RollableItem item, boolean shiny) {
        if (!isActive()) return;
        double score = scoreOf(item.getOdds(), shiny);
        Entry had = best.get(player.getUniqueId());
        if (had != null && had.score() >= score) return;
        best.put(player.getUniqueId(), new Entry(player.getUniqueId(), player.getName(),
                item.getDisplayName(), item.getRarity().name(), item.getOdds(), shiny, score));
        dirty = true;
    }

    /** How rare a roll really is: its label, times 1 / shiny.chance for a shiny. */
    public double scoreOf(long odds, boolean shiny) {
        double shinyFactor = 1.0 / Math.max(1e-9, plugin.getConfig().getDouble("shiny.chance", 0.01));
        return odds * (shiny ? shinyFactor : 1.0);
    }

    public List<Entry> top(int count) {
        List<Entry> all = new ArrayList<>(best.values());
        all.sort(Comparator.comparingDouble(Entry::score).reversed());
        return all.subList(0, Math.min(count, all.size()));
    }

    public Entry of(UUID uuid) {
        return best.get(uuid);
    }

    public int placeOf(UUID uuid) {
        List<Entry> all = top(Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) if (all.get(i).uuid().equals(uuid)) return i + 1;
        return 0;
    }

    // ------------------------------------------------------------ the clock

    private void tick() {
        try {
            long now = System.currentTimeMillis();
            // The end first, so a battle that ran out while the server was
            // down is paid before anything new begins.
            if (startedAt > 0 && !paid && now >= endsAt) payOut();

            if (enabled()) {
                LocalDate date = eventDay(ZonedDateTime.now(zone()));
                long start = startOf(date);
                // Within the event's own length of its start only: a server
                // that slept through the whole day does not reset everyone
                // on Monday.
                if (!startedFor(date) && now >= start && now < start + lengthMillis()) begin(date);
            }
            if (dirty) save();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Rarest drop tick failed: " + ex);
        }
    }

    /** The battle starts: the reset when today is the reset date, then a clean board. */
    private void begin(LocalDate date) {
        String today = date.toString();
        boolean reset = today.equals(resetOn()) && !today.equals(resetDone);

        // Written down before anything happens, so even a crash halfway
        // through can never start the battle, or the reset, a second time.
        day = today;
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + lengthMillis();
        paid = false;
        best.clear();
        if (reset) resetDone = today;
        save();

        if (reset) {
            plugin.getLogger().warning("Rarest drop: " + today + " is the reset date. Resetting every player now.");
            try {
                com.spacerng.solrng.player.AccountReset.everyone(plugin);
            } catch (Throwable ex) {
                plugin.getLogger().severe("Rarest drop: the reset failed partway: " + ex);
            }
        }
        plugin.getLogger().info("Rarest drop battle started, ends " + when(endsAt) + " " + zone() + ".");

        announceStart(reset);
        if (plugin.getHoloManager() != null) plugin.getHoloManager().redrawRarest();
    }

    private void announceStart(boolean reset) {
        List<Integer> payouts = payouts();
        String prizes = prizeLine(payouts);
        long hours = Math.round(lengthMillis() / 3_600_000.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage("");
            if (reset) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "RESET " + ChatColor.RESET
                        + ChatColor.WHITE + "Everyone starts again from zero. "
                        + ChatColor.GRAY + "Your rank, Credits, Battle Pass and milestones are kept.");
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "RAREST DROP " + ChatColor.RESET
                    + ChatColor.WHITE + "The battle is on for " + hours + " hours. "
                    + ChatColor.GRAY + "Your single rarest roll counts.");
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "RAREST DROP " + ChatColor.RESET
                    + prizes);
            player.sendMessage("");
            player.showTitle(net.kyori.adventure.title.Title.title(
                    legacy(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Rarest Drop"),
                    legacy(ChatColor.WHITE + (reset ? "Everyone is reset. " : "") + hours
                            + " hours: your rarest roll wins Credits"),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(500),
                            java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(1))));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    /** "#1 500 Credits ✪  #2 250 Credits ✪  #3 100 Credits ✪" */
    private static String prizeLine(List<Integer> payouts) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < payouts.size(); i++) {
            if (i > 0) line.append("  ");
            line.append(ChatColor.YELLOW).append("#").append(i + 1).append(" ")
                    .append(Currency.CREDITS.amount(payouts.get(i)));
        }
        return line.toString();
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    /** A player joining while the battle runs hears it is on and how long is left. */
    public void onJoin(Player player) {
        if (!isActive()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !isActive()) return;
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "RAREST DROP " + ChatColor.RESET
                    + ChatColor.WHITE + clock(secondsLeft()) + " left. " + ChatColor.GRAY
                    + "Your single rarest roll counts. " + prizeLine(payouts()));
        }, 60L);
    }

    // ------------------------------------------------------------ payout

    /** Pays the top three and says so. */
    public void payOut() {
        if (paid) return;
        paid = true;
        save();
        List<Entry> winners = top(3);
        List<Integer> payouts = payouts();
        Bukkit.broadcast(legacy(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                + "RAREST DROP " + ChatColor.RESET + ChatColor.WHITE + "The battle is over. The winners:"));
        if (winners.isEmpty()) {
            Bukkit.broadcast(legacy(ChatColor.GRAY + "Nobody rolled while it ran."));
        }
        for (int i = 0; i < winners.size(); i++) {
            Entry entry = winners.get(i);
            long credits = i < payouts.size() ? payouts.get(i) : 0;
            Bukkit.broadcast(legacy(ChatColor.YELLOW + "#" + (i + 1) + " "
                    + ChatColor.WHITE + entry.name() + ChatColor.GRAY + "  " + label(entry)
                    + ChatColor.GRAY + "  " + Currency.CREDITS.amount(credits)));
            if (credits <= 0) continue;
            Player online = Bukkit.getPlayer(entry.uuid());
            if (online != null) {
                plugin.getPlayerDataManager().get(entry.uuid()).addPoints(credits);
                online.sendMessage(Currency.CREDITS.colour() + "" + ChatColor.BOLD + "+"
                        + Currency.CREDITS.amount(credits) + ChatColor.RESET + ChatColor.GRAY
                        + " for #" + (i + 1) + " in the rarest drop battle.");
                plugin.getScoreboardManager().update(online);
            } else {
                plugin.getPlayerDataManager().awardOffline(entry.uuid(), credits);
            }
            plugin.getLogger().info("Rarest drop: #" + (i + 1) + " " + entry.name() + " paid " + credits + " Credits.");
        }
    }

    /** "Dragon Egg 1 in 188K", with "Shiny" in front for a shiny. */
    public String label(Entry entry) {
        return (entry.shiny() ? ChatColor.AQUA + "Shiny " : "") + ChatColor.WHITE + entry.item()
                + ChatColor.GRAY + " 1 in " + com.spacerng.solrng.rarity.RollFormat.abbreviate(
                        Math.round(entry.score()));
    }

    // ------------------------------------------------------------ file

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        day = yml.getString("day", "");
        paid = yml.getBoolean("paid", false);
        startedAt = yml.getLong("started-at", 0L);
        endsAt = yml.getLong("ends-at", 0L);
        resetDone = yml.getString("reset-done", "");
        ConfigurationSection entries = yml.getConfigurationSection("entries");
        if (entries == null) return;
        for (String key : entries.getKeys(false)) {
            ConfigurationSection s = entries.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID uuid = UUID.fromString(key);
                best.put(uuid, new Entry(uuid, s.getString("name", "?"), s.getString("item", "?"),
                        s.getString("rarity", "COMMON"), s.getLong("odds"), s.getBoolean("shiny"),
                        s.getDouble("score")));
            } catch (IllegalArgumentException ignored) {
                // Not a player id.
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("day", day);
        yml.set("paid", paid);
        yml.set("started-at", startedAt);
        yml.set("ends-at", endsAt);
        yml.set("reset-done", resetDone);
        for (Entry entry : best.values()) {
            String path = "entries." + entry.uuid() + ".";
            yml.set(path + "name", entry.name());
            yml.set(path + "item", entry.item());
            yml.set(path + "rarity", entry.rarity());
            yml.set(path + "odds", entry.odds());
            yml.set(path + "shiny", entry.shiny());
            yml.set(path + "score", entry.score());
        }
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save rarest-drop.yml: " + ex.getMessage());
        }
    }
}
