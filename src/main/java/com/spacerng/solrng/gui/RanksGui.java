package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rank.RankManager;
import com.spacerng.solrng.rank.RankTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /ranks: the ladder from Linked to Supernova, what each one gives and what
 * it costs in Credits. Linked is free with a Discord link, so it reads as a
 * step rather than a purchase.
 */
public class RanksGui {

    private static final int SIZE = 45;
    private static final int SELF_SLOT = 4;
    private static final int[] TIER_SLOTS = {19, 21, 23, 25};
    private static final int CREDITS_SLOT = 40;

    public static NamespacedKey rankKey() {
        return SolRNGPlugin.key("solrng_rank");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        RanksHolder holder = new RanksHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Ranks");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        RankManager ranks = plugin.getRankManager();

        ItemStack rail = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || i >= 36 ? rail : filler);

        inv.setItem(SELF_SLOT, selfIcon(plugin, player, data));
        List<RankTier> tiers = ranks.tiers();
        for (int i = 0; i < tiers.size() && i < TIER_SLOTS.length; i++) {
            inv.setItem(TIER_SLOTS[i], tierIcon(plugin, data, tiers.get(i)));
        }
        inv.setItem(CREDITS_SLOT, creditsIcon(data));
        return inv;
    }

    private static ItemStack selfIcon(SolRNGPlugin plugin, Player player, PlayerData data) {
        RankManager ranks = plugin.getRankManager();
        RankTier tier = ranks.rankOf(data);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.GOLD, player.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Rank", ranks.styled(tier)));
        lore.add(Lore.stat(ChatColor.AQUA, "Money, Luck and Speed",
                String.format("%.2f", ranks.multiplierOf(data)) + "x"));
        lore.add(Lore.stat(ChatColor.AQUA, "Vault pages", String.valueOf(ranks.vaultPages(data))));
        if (tier != null && tier.hasKeyall()) {
            long left = ranks.keyallLeft(data);
            lore.add(Lore.stat(ChatColor.GOLD, "Key all",
                    left <= 0 ? "ready, use /keyall" : "in " + RankManager.timeLeft(left)));
        }
        lore.add("");
        lore.add(Lore.footnote("Linked comes free with /discord link."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack tierIcon(SolRNGPlugin plugin, PlayerData data, RankTier tier) {
        RankManager ranks = plugin.getRankManager();
        RankTier current = ranks.rankOf(data);
        boolean owned = current != null && ranks.indexOf(current) >= ranks.indexOf(tier);
        boolean affordable = data.getPoints() >= tier.price();

        Material material = Material.matchMaterial(tier.icon());
        ItemStack item = new ItemStack(material == null ? Material.NETHER_STAR : material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ranks.styled(tier));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What you get"));
        lore.add(Lore.stat(ChatColor.GREEN, "Money, Luck and Speed",
                String.format("%.2f", tier.multiplier()) + "x"));
        if (tier.vaultPages() > 0) {
            lore.add(Lore.stat(ChatColor.AQUA, "Private vaults", tier.vaultPages() + " pages"));
        }
        if (tier.hasKeyall()) {
            var crate = plugin.getCrateManager().get(tier.keyallCrate());
            String name = crate == null ? tier.keyallCrate() : plugin.getCrateManager().keyName(crate);
            lore.add(Lore.stat(ChatColor.GOLD, "Key all", tier.keyallAmount() + "x " + name
                    + ChatColor.DARK_GRAY + " every " + (ranks.keyallCooldownMillis() / 3600_000L) + "h"));
        }
        List<String> commands = new ArrayList<>();
        if (tier.fly()) commands.add("/fly");
        if (tier.nick()) commands.add("/nick");
        if (tier.size()) commands.add("/size");
        if (!commands.isEmpty()) {
            lore.add(Lore.stat(ChatColor.AQUA, "Commands", String.join(", ", commands)));
        }
        if (tier.rgbName()) {
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Name", "a drifting rainbow in tab and chat"));
        }
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Price"));
        if (tier.price() <= 0) {
            lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Free with /discord link."));
        } else {
            lore.add(Lore.line(ChatColor.LIGHT_PURPLE, Currency.CREDITS.price(tier.price(), affordable)));
        }
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Yours");
        } else if (tier.price() <= 0) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Link your Discord");
            lore.add(Lore.line(ChatColor.GRAY, "Use /discord link in game."));
        } else if (affordable) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Credits");
        }
        meta.setLore(lore);
        if (owned) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(rankKey(), PersistentDataType.STRING, tier.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack creditsIcon(PlayerData data) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Credits"));
        meta.setLore(List.of(
                Lore.stat(ChatColor.LIGHT_PURPLE, "You have", Currency.CREDITS.amount(data.getPoints())),
                "",
                Lore.line(ChatColor.GRAY, "Earn them from /milestones, the daily"),
                Lore.line(ChatColor.GRAY, "farming payout and Credit Finder,"),
                Lore.line(ChatColor.GRAY, "or buy them in the store."),
                "",
                Lore.footnote("Ranks, Perk Tickets and boosts all spend Credits.")));
        item.setItemMeta(meta);
        return item;
    }

    /** The rank id on a clicked item, or null. */
    public static String clickedRank(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(rankKey(), PersistentDataType.STRING);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
