package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.boost.BoostManager;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rank.RankManager;
import com.spacerng.solrng.rank.RankTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /buy and /store: everything Credits buy, on one shelf. Rebuilt in V220.
 *
 * Before this the shelf held the boost and the pass next to a "Ranks,
 * coming soon" that had been buyable for weeks, perk tickets were only
 * found by walking into /perks, and the panel said Credits cannot be
 * earned in game, which the farming payout, milestones, daily rewards and
 * the Discord link gift all disprove.
 *
 *    row 0   rail, the store's name in the middle, your Credits top right
 *    row 2   Ranks · Global Luck · Premium Pass · Perk Tickets
 *    row 4   rail, the web store in the middle when a link is set
 *
 * Every product is the description block from the menu-design skill:
 * name, a dark grey subtitle, two lines of pitch, what it gives, the price
 * as a stat line, and one footer. Ranks and perk tickets open their own
 * menus, which already sell them properly; the boost and the pass are
 * bought right here.
 */
public class BuyGui {

    private static final int SIZE = 45;
    private static final int HEADER_SLOT = 4;
    private static final int WALLET_SLOT = 8;
    public static final int RANKS_SLOT = 19;
    public static final int BOOST_SLOT = 21;
    public static final int BATTLEPASS_SLOT = 23;
    public static final int PERKS_SLOT = 25;
    public static final int WEBSTORE_SLOT = 40;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        BuyHolder holder = new BuyHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "SpaceRNG Store");
        holder.setInventory(inv);

        ItemStack rail = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || i >= 36 ? rail : filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        inv.setItem(HEADER_SLOT, header());
        inv.setItem(WALLET_SLOT, wallet(player, data));
        inv.setItem(RANKS_SLOT, ranks(plugin, data));
        inv.setItem(BOOST_SLOT, boost(plugin, data));
        inv.setItem(BATTLEPASS_SLOT, pass(plugin, data));
        inv.setItem(PERKS_SLOT, perkTickets(plugin, data));
        if (!storeUrl(plugin).isEmpty()) inv.setItem(WEBSTORE_SLOT, webStore());
        return inv;
    }

    /** The web store link from config, or "" when none is set. */
    public static String storeUrl(SolRNGPlugin plugin) {
        return plugin.getConfig().getString("buy.store-url", "").trim();
    }

    private static ItemStack header() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.rainbow("SpaceRNG Store"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Everything Credits buy, in one place."),
                Lore.line(ChatColor.GRAY, "Hover a product to see what it gives.")));
        item.setItemMeta(meta);
        return item;
    }

    /** Your Credits, and every way there is to get more. */
    private static ItemStack wallet(Player player, PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your Credits"));
        meta.setLore(List.of(
                Lore.stat(Currency.CREDITS.colour(), "Balance", Currency.CREDITS.exact(data.getPoints())),
                "",
                Lore.section(ChatColor.YELLOW, "Where Credits come from"),
                Lore.line(ChatColor.YELLOW, "The web store"),
                Lore.line(ChatColor.YELLOW, "The daily farming top 3"),
                Lore.line(ChatColor.YELLOW, "Milestones and daily rewards"),
                Lore.line(ChatColor.YELLOW, "Linking your Discord, once")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack ranks(SolRNGPlugin plugin, PlayerData data) {
        RankManager ranks = plugin.getRankManager();
        RankTier current = ranks.rankOf(data);

        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient("Ranks", true, "#F8BBD0", "#AB47BC"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Permanent, bought once");
        lore.add("");
        lore.add(ChatColor.GRAY + "A rank multiplies Money, Luck and");
        lore.add(ChatColor.GRAY + "Speed, and wears a bigger aura.");
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "The ladder"));
        for (RankTier tier : ranks.tiers()) {
            boolean owned = current != null && ranks.indexOf(current) >= ranks.indexOf(tier);
            String price = tier.price() <= 0 ? ChatColor.GREEN + "free when linked"
                    : Currency.CREDITS.amount(tier.price());
            lore.add(Lore.mark(owned ? ChatColor.GREEN : ChatColor.DARK_GRAY) + ranks.styled(tier)
                    + ChatColor.DARK_GRAY + "  " + (owned ? ChatColor.GREEN + Lore.TICK : price));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Yours", current == null ? "none yet" : ranks.styled(current)));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack boost(SolRNGPlugin plugin, PlayerData data) {
        BoostManager boost = plugin.getBoostManager();
        boolean maxed = boost.isMaxed();
        long cost = boost.nextCost();
        boolean affordable = data.getPoints() >= cost;
        int minutes = plugin.getConfig().getInt("boost.duration-seconds", 900) / 60;

        ItemStack item = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = item.getItemMeta();
        // Named after what a click buys right now: "Global 2x Luck".
        meta.setDisplayName(Lore.gradient("Global " + BoostManager.formatMultiplier(boost.nextMultiplier())
                + " Luck", true, "#B9F6CA", "#00C853"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "For the whole server, " + minutes + " minutes");
        lore.add("");
        lore.add(ChatColor.GRAY + "Every roll on the server gets the");
        lore.add(ChatColor.GRAY + "Luck, and everyone sees who bought it.");
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Right now"));
        if (boost.isActive()) {
            lore.add(Lore.stat(ChatColor.GREEN, "Live", BoostManager.formatMultiplier(boost.multiplier())));
            lore.add(Lore.stat(ChatColor.GREEN, "Time left", boost.timeLeftText()));
            if (boost.getBoughtBy() != null) lore.add(Lore.stat(ChatColor.GREEN, "Started by", boost.getBoughtBy()));
        } else {
            lore.add(Lore.line(ChatColor.DARK_GRAY, "No boost running."));
        }
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
            lore.add(Lore.line(ChatColor.GRAY, "Buyable again once it runs out."));
        } else {
            lore.add(Lore.stat(affordable ? ChatColor.YELLOW : ChatColor.RED, "Price", Currency.CREDITS.amount(cost)));
            lore.add(Lore.footnote("Buying while it runs doubles it."));
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Credits");
        }
        meta.setLore(lore);
        if (boost.isActive()) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The premium track, sold here as well as inside /pass. Credits are
     * spent in this menu, so a Credit purchase that only existed somewhere
     * else would be the one thing missing from the shelf.
     */
    private static ItemStack pass(SolRNGPlugin plugin, PlayerData data) {
        com.spacerng.solrng.pass.PassManager pass = plugin.getPassManager();
        boolean owned = data.isPassPremium();
        boolean affordable = data.getPoints() >= pass.getPremiumCost();

        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient("Premium Battle Pass", true, "#FFECB3", "#FFA000"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + ChatColor.stripColor(pass.getSeasonName()) + ", for the whole season");
        lore.add("");
        lore.add(ChatColor.GRAY + "A second reward track, paid out for");
        lore.add(ChatColor.GRAY + "every level, even the ones behind you.");
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Unlocked");
            lore.add(Lore.line(ChatColor.GRAY, "Claim it in /pass."));
        } else {
            lore.add(Lore.stat(affordable ? ChatColor.YELLOW : ChatColor.RED, "Price",
                    Currency.CREDITS.amount(pass.getPremiumCost())));
            lore.add("");
            lore.add(affordable
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Credits");
        }
        meta.setLore(lore);
        if (owned) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack perkTickets(SolRNGPlugin plugin, PlayerData data) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient("Perk Tickets", true, "#D1C4E9", "#7E57C2"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "One ticket is one perk roll");
        lore.add("");
        lore.add(ChatColor.GRAY + "Roll for a perk that boosts one stat,");
        lore.add(ChatColor.GRAY + "all the way up to the Universe perk.");
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Packs"));
        for (Map.Entry<Integer, Long> pack : plugin.getPerkManager().ticketPrices().entrySet()) {
            lore.add(Lore.stat(ChatColor.AQUA, pack.getKey() + "x", Currency.CREDITS.amount(pack.getValue())));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.GREEN, "You have", String.format("%,d", data.getPerkTickets())));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack webStore() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.rainbow("Buy Credits"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Credits come from the web store,"),
                Lore.line(ChatColor.GRAY, "and every purchase helps the server."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click for the link"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
