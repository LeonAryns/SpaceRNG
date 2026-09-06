package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.stats.StatSources;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /stats — every derived number, and where it came from.
 *
 * The overview answers "what am I at"; one click answers "why". That
 * second question is the one a progression server never usually answers,
 * and not answering it is what makes an upgrade feel like it did nothing.
 *
 * Individual skills and enchants stay out of it deliberately. A player
 * doesn't need to know that Luck VII exists — they need to know that
 * skills are worth +340% and armor is worth +60%, so they know which
 * menu to go and spend in next.
 */
public class StatsGui {

    private record Card(int slot, Material icon, ChatColor accent, StatSources.Id id) {
    }

    // Top row: what a roll does. Bottom row: what it pays, and the farm.
    private static final Card[] CARDS = {
            new Card(20, Material.RABBIT_FOOT, ChatColor.GREEN, StatSources.Id.LUCK),
            new Card(22, Material.SUGAR, ChatColor.AQUA, StatSources.Id.SPEED),
            new Card(24, Material.NAUTILUS_SHELL, ChatColor.LIGHT_PURPLE, StatSources.Id.SHINY),
            new Card(38, Material.EMERALD, ChatColor.GREEN, StatSources.Id.MONEY),
            new Card(40, Material.GOLD_INGOT, ChatColor.GOLD, StatSources.Id.COINS),
            new Card(42, Material.ENCHANTED_BOOK, ChatColor.LIGHT_PURPLE, StatSources.Id.ENCHANT),
    };

    private static final int HEAD_SLOT = 4;
    private static final int BACK_SLOT = 49;
    private static final int TOP_CONTRIBUTORS = 3;

    // Where a breakdown lays its parts out: two clean rows of seven.
    private static final int[] PART_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
    };

    public static NamespacedKey statKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_stat_id");
    }

    // ---------------------------------------------------------- overview

    public static Inventory overview(SolRNGPlugin plugin, UUID target, String targetName) {
        StatsHolder holder = new StatsHolder(target, targetName, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Stats" + ChatColor.DARK_GRAY + " — " + targetName);
        holder.setInventory(inv);
        frame(inv);

        PlayerData data = plugin.getPlayerDataManager().get(target);
        inv.setItem(HEAD_SLOT, head(plugin, target, targetName, data));
        for (Card card : CARDS) {
            inv.setItem(card.slot(), summaryCard(plugin, data, card));
        }
        return inv;
    }

    /**
     * The card for one stat: the number, then the three things doing most
     * of the work. Three, because the point is "where is this coming
     * from", and a full list belongs on the page you get by clicking.
     */
    private static ItemStack summaryCard(SolRNGPlugin plugin, PlayerData data, Card card) {
        StatSources.Stat stat = StatSources.of(plugin, data, card.id());

        ItemStack item = new ItemStack(card.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(card.accent(), stat.name()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(card.accent(), stat.blurb()));
        lore.add("");
        lore.add(card.accent() + Lore.BULLET + " " + ChatColor.GRAY + "Total: "
                + card.accent() + ChatColor.BOLD + format(stat.format(), stat.total()));
        lore.add("");

        List<StatSources.Part> ranked = new ArrayList<>(stat.parts());
        ranked.removeIf(StatSources.Part::idle);
        ranked.sort((a, b) -> Double.compare(weight(b), weight(a)));

        if (ranked.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Nothing is feeding this yet.");
        } else {
            lore.add(Lore.section(ChatColor.GOLD, "Biggest sources"));
            for (int i = 0; i < Math.min(TOP_CONTRIBUTORS, ranked.size()); i++) {
                lore.add(partLine(ranked.get(i), stat.format(), ChatColor.WHITE));
            }
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to break it down");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(statKey(plugin), PersistentDataType.STRING, card.id().name());
        item.setItemMeta(meta);
        return item;
    }

    // --------------------------------------------------------- breakdown

    public static Inventory breakdown(SolRNGPlugin plugin, UUID target, String targetName,
                                      StatSources.Id id) {
        StatsHolder holder = new StatsHolder(target, targetName, id);
        PlayerData data = plugin.getPlayerDataManager().get(target);
        StatSources.Stat stat = StatSources.of(plugin, data, id);

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.AQUA + "" + ChatColor.BOLD + stat.name() + ChatColor.DARK_GRAY + " — " + targetName);
        holder.setInventory(inv);
        frame(inv);

        inv.setItem(HEAD_SLOT, totalCard(stat, accentOf(id)));

        // Parts are shown in the order they're applied, idle ones
        // included. A source sitting at zero is the most useful thing on
        // the page: it's the one you haven't bought yet.
        double running = 0.0;
        for (int i = 0; i < stat.parts().size() && i < PART_SLOTS.length; i++) {
            StatSources.Part part = stat.parts().get(i);
            running = part.op() == StatSources.Op.ADD ? running + part.value() : running * part.value();
            inv.setItem(PART_SLOTS[i], partCard(part, stat, i + 1, running, accentOf(id)));
        }

        inv.setItem(BACK_SLOT, back(targetName));
        return inv;
    }

    private static ItemStack totalCard(StatSources.Stat stat, ChatColor accent) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(accent, stat.name()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(accent, stat.blurb()));
        lore.add("");
        lore.add(accent + Lore.BULLET + " " + ChatColor.GRAY + "Total: "
                + accent + ChatColor.BOLD + format(stat.format(), stat.total()));
        if (!stat.note().isEmpty()) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + stat.note());
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Sources apply left to right, top row first.");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + ChatColor.GREEN + "+" + ChatColor.DARK_GRAY
                + " adds to the pile, " + ChatColor.AQUA + "x" + ChatColor.DARK_GRAY + " scales the lot.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One source. It carries the running total after itself, which is what
     * turns a list of numbers into an explanation — you can see the moment
     * a multiplier stops being worth less than the flat bonus below it.
     */
    private static ItemStack partCard(StatSources.Part part, StatSources.Stat stat,
                                      int step, double running, ChatColor accent) {
        boolean idle = part.idle();
        boolean adds = part.op() == StatSources.Op.ADD;

        ItemStack item = new ItemStack(idle ? Material.GRAY_DYE
                : adds ? Material.LIME_DYE : Material.LIGHT_BLUE_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(idle ? ChatColor.DARK_GRAY : accent, part.label()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.GOLD, "Step " + step));
        lore.add(partLine(part, stat.format(), idle ? ChatColor.DARK_GRAY : ChatColor.WHITE));
        lore.add(Lore.stat(ChatColor.GRAY, "Running total", format(stat.format(), running)));
        lore.add("");
        if (idle) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Nothing yet");
            lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + part.hint());
        } else {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Active");
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + part.hint());
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String partLine(StatSources.Part part, StatSources.Format format, ChatColor value) {
        boolean adds = part.op() == StatSources.Op.ADD;
        ChatColor mark = adds ? ChatColor.GREEN : ChatColor.AQUA;
        String shown = adds
                // An additive share of a chance is easier to read as the
                // raw figure than as "1 in 100" of itself.
                ? (format == StatSources.Format.PERCENT ? signedPercent(part.value())
                        : trim(part.value()))
                : "x" + trim(part.value());
        return mark + Lore.BULLET + " " + ChatColor.GRAY + part.label() + ": " + value + shown;
    }

    // ------------------------------------------------------------ pieces

    private static ItemStack head(SolRNGPlugin plugin, UUID target, String targetName, PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(target);
            skull.setOwningPlayer(owner);
        }
        meta.setDisplayName(Lore.title(ChatColor.AQUA, targetName));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "At a glance"));
        lore.add(Lore.stat(ChatColor.GREEN, "Luck",
                signedPercent(StatSources.luck(plugin, data, true).total())));
        lore.add(Lore.stat(ChatColor.AQUA, "Speed",
                trim(StatSources.speed(plugin, data).total()) + "x"));
        lore.add(Lore.stat(ChatColor.YELLOW, "Prestige", String.valueOf(data.getPrestige())));
        lore.add(Lore.stat(ChatColor.YELLOW, "Level", String.valueOf(data.getLevel())));
        lore.add(Lore.stat(ChatColor.AQUA, "Rolls", String.format("%,d", data.getTotalRolls())));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Every number below is worked out live.");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Retune a skill and this moves with it.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack back(String targetName) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, "Back"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every stat for " + targetName + "."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to go back"));
        item.setItemMeta(meta);
        return item;
    }

    private static void frame(Inventory inv) {
        ItemStack rim = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int column = slot % 9;
            int row = slot / 9;
            inv.setItem(slot, row == 0 || row == 5 || column == 0 || column == 8 ? rim : fill);
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    // ----------------------------------------------------------- numbers

    private static ChatColor accentOf(StatSources.Id id) {
        for (Card card : CARDS) {
            if (card.id() == id) return card.accent();
        }
        return ChatColor.AQUA;
    }

    /** How much of the final number a part is responsible for, roughly. */
    private static double weight(StatSources.Part part) {
        return part.op() == StatSources.Op.ADD ? part.value() : part.value() - 1.0;
    }

    private static String format(StatSources.Format format, double value) {
        return switch (format) {
            case PERCENT -> signedPercent(value);
            case CHANCE -> value <= 0.0 ? "never"
                    : "1 in " + String.format("%,d", Math.round(1.0 / value));
            default -> trim(value) + "x";
        };
    }

    private static String signedPercent(double value) {
        return (value < 0 ? "-" : "+") + trim(Math.abs(value) * 100.0) + "%";
    }

    private static String trim(double value) {
        String text = String.format("%.2f", value);
        if (text.endsWith(".00")) return text.substring(0, text.length() - 3);
        if (text.endsWith("0")) return text.substring(0, text.length() - 1);
        return text;
    }
}
