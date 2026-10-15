package com.spacerng.solrng.event;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one day rarest drop board (V398, Leon). Every Sunday from 00:00 to
 * 23:59 Amsterdam time (CET or CEST, whichever is on), each player's
 * single rarest roll counts. When it ends the top three are paid Credits
 * (500, 250, 100 by default) and stay on the board until the next one.
 *
 * Rarest means the highest label; a shiny counts as its label times
 * 1 / shiny.chance (100), the way a shiny actually is that much rarer.
 * Entries live in rarest-drop.yml with the date of the day they belong
 * to, so a reload mid-day loses nothing and last week's never mix in.
 */
public final class RarestDropEvent {

    /** One player's best roll of the day. */
    public record Entry(UUID uuid, String name, String item, String rarity, long odds, boolean shiny,
                        double score) {
    }

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<UUID, Entry> best = new HashMap<>();
    /** The date of the Sunday the entries belong to. */
    private String day = "";
    private boolean paid;

    public RarestDropEvent(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rarest-drop.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 200L, 200L);
    }

    // ------------------------------------------------------------ time

    private ZoneId zone() {
        return ZoneId.of(plugin.getConfig().getString("rarest-drop.zone", "Europe/Amsterdam"));
    }

    private DayOfWeek weekday() {
        try {
            return DayOfWeek.valueOf(plugin.getConfig().getString("rarest-drop.day", "SUNDAY").toUpperCase());
        } catch (IllegalArgumentException ex) {
            return DayOfWeek.SUNDAY;
        }
    }

    /** This week's event day, or the next one when this week's is past. */
    private LocalDate eventDay(ZonedDateTime now) {
        LocalDate date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(weekday()));
        return date;
    }

    private ZonedDateTime start(LocalDate date) {
        return date.atStartOfDay(zone());
    }

    private ZonedDateTime end(LocalDate date) {
        return date.atTime(LocalTime.of(23, 59, 59)).atZone(zone());
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("rarest-drop.enabled", true);
    }

    public boolean isActive() {
        if (!enabled()) return false;
        ZonedDateTime now = ZonedDateTime.now(zone());
        LocalDate date = eventDay(now);
        return !now.isBefore(start(date)) && !now.isAfter(end(date));
    }

    /** Seconds until the board closes while it runs, or until it opens. */
    public long secondsLeft() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        LocalDate date = eventDay(now);
        ZonedDateTime target = isActive() ? end(date) : start(date);
        return Math.max(0L, java.time.Duration.between(now, target).getSeconds());
    }

    public List<Integer> payouts() {
        List<Integer> payouts = plugin.getConfig().getIntegerList("rarest-drop.payouts");
        return payouts.isEmpty() ? List.of(500, 250, 100) : payouts;
    }

    // ------------------------------------------------------------ rolls

    /** Called for every real roll that lands, the extra ones from Speed too. */
    public void record(Player player, RollableItem item, boolean shiny) {
        if (!isActive()) return;
        String today = eventDay(ZonedDateTime.now(zone())).toString();
        if (!today.equals(day)) {
            best.clear();
            day = today;
            paid = false;
        }
        double shinyFactor = 1.0 / Math.max(1e-9, plugin.getConfig().getDouble("shiny.chance", 0.01));
        double score = item.getOdds() * (shiny ? shinyFactor : 1.0);
        Entry had = best.get(player.getUniqueId());
        if (had != null && had.score() >= score) return;
        best.put(player.getUniqueId(), new Entry(player.getUniqueId(), player.getName(),
                item.getDisplayName(), item.getRarity().name(), item.getOdds(), shiny, score));
        dirty = true;
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

    // ------------------------------------------------------------ payout

    private boolean dirty;

    private void tick() {
        if (isActive()) {
            String today = eventDay(ZonedDateTime.now(zone())).toString();
            if (!today.equals(day)) {
                best.clear();
                day = today;
                paid = false;
                dirty = true;
            }
        }
        if (dirty) save();
        if (day.isEmpty() || paid) return;
        ZonedDateTime now = ZonedDateTime.now(zone());
        if (now.isAfter(end(LocalDate.parse(day)))) payOut();
    }

    /** Pays the top three and says so. Also /rngadmin rarest end. */
    public void payOut() {
        if (paid) return;
        paid = true;
        List<Entry> winners = top(3);
        List<Integer> payouts = payouts();
        Bukkit.broadcast(net.kyori.adventure.text.Component.text(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                + "RAREST DROP " + ChatColor.RESET + ChatColor.GRAY + "The day is over. The winners:"));
        for (int i = 0; i < winners.size(); i++) {
            Entry entry = winners.get(i);
            long credits = i < payouts.size() ? payouts.get(i) : 0;
            Bukkit.broadcast(net.kyori.adventure.text.Component.text(ChatColor.YELLOW + "#" + (i + 1) + " "
                    + ChatColor.WHITE + entry.name() + ChatColor.GRAY + "  " + label(entry)
                    + ChatColor.GRAY + "  " + Currency.CREDITS.amount(credits)));
            if (credits <= 0) continue;
            Player online = Bukkit.getPlayer(entry.uuid());
            if (online != null) {
                plugin.getPlayerDataManager().get(entry.uuid()).addPoints(credits);
                online.sendMessage(Currency.CREDITS.colour() + "" + ChatColor.BOLD + "+"
                        + Currency.CREDITS.amount(credits) + ChatColor.RESET + ChatColor.GRAY
                        + " for #" + (i + 1) + " on the rarest drop board.");
                plugin.getScoreboardManager().update(online);
            } else {
                plugin.getPlayerDataManager().awardOffline(entry.uuid(), credits);
            }
        }
        save();
    }

    /** "Dragon Egg 1 in 188K", with "Shiny" in front for a shiny. */
    public String label(Entry entry) {
        return (entry.shiny() ? ChatColor.LIGHT_PURPLE + "Shiny " : "") + ChatColor.WHITE + entry.item()
                + ChatColor.GRAY + " 1 in " + com.spacerng.solrng.rarity.RollFormat.abbreviate(entry.odds());
    }

    // ------------------------------------------------------------ file

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        day = yml.getString("day", "");
        paid = yml.getBoolean("paid", false);
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
