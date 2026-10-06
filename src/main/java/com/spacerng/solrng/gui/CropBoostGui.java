package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.farming.CropBoosts;
import com.spacerng.solrng.farming.CropType;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * The board behind one crop in /crops (V353). Four stats, bought with
 * Coins, levelled on THIS crop only.
 *
 * The farm tree used to carry a Yield node for every crop and they could
 * all be bought from a menu by somebody who had never unlocked the crop,
 * which is what Leon asked to be rid of. Standing in your own field and
 * paying for the crop you are actually growing is the same progression
 * with the thing it is about put back.
 */
public class CropBoostGui {

    private static final int BACK_SLOT = 36;
    private static final int[] FIVE = {20, 21, 22, 23, 24};

    public static NamespacedKey statKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_crop_stat");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, String cropId) {
        CropBoostHolder holder = new CropBoostHolder();
        holder.setCropId(cropId);
        Inventory inv = Bukkit.createInventory(holder, 45,
                MenuStyle.title("Crop Boosts", "#B9F6CA", "#00C853"));
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        CropType crop = plugin.getFarmPlotManager().getCrop(cropId);

        List<CropBoosts.Stat> stats = CropBoosts.all(plugin);
        int[] slots = slotsFor(stats.size());
        for (int i = 0; i < stats.size() && i < slots.length; i++) {
            inv.setItem(slots[i], statIcon(plugin, data, crop, stats.get(i)));
        }

        inv.setItem(13, cropPanel(plugin, data, crop, stats));
        inv.setItem(BACK_SLOT, backButton());
        MenuStyle.apply(inv, MenuStyle.Palette.GREEN);
        return inv;
    }

    public static int backSlot() {
        return BACK_SLOT;
    }

    /** The middle of the row, so four stats read as a set rather than a list. */
    private static int[] slotsFor(int n) {
        return switch (Math.max(0, Math.min(5, n))) {
            case 1 -> new int[]{22};
            case 2 -> new int[]{21, 23};
            case 3 -> new int[]{21, 22, 23};
            case 4 -> new int[]{20, 21, 23, 24};
            case 5 -> FIVE;
            default -> new int[0];
        };
    }

    private static ItemStack statIcon(SolRNGPlugin plugin, PlayerData data,
                                      CropType crop, CropBoosts.Stat stat) {
        String cropId = crop == null ? "" : crop.getId();
        int level = CropBoosts.levelOf(data, cropId, stat.id());
        boolean maxed = level >= stat.maxLevel();
        long cost = stat.costAt(level);
        boolean affordable = data.getTokens() >= cost;

        ItemStack item = new ItemStack(stat.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(maxed ? ChatColor.GREEN
                : level > 0 ? ChatColor.YELLOW : ChatColor.AQUA, stat.display()));
        meta.setEnchantmentGlintOverride(maxed ? Boolean.TRUE : null);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + (crop == null ? "Crop boost" : crop.getDisplay() + " only"));
        lore.add("");
        if (!stat.blurb().isEmpty()) {
            lore.add(Lore.line(ChatColor.GRAY, stat.blurb()));
            lore.add("");
        }
        lore.add(Lore.section(ChatColor.AQUA, "Your boost"));
        lore.add(Lore.stat(ChatColor.AQUA, "Level", level + " / " + stat.maxLevel()));
        lore.add(Lore.stat(stat.colour(), "Now",
                String.format("%.2f", stat.multiplier(level)) + "x"));
        if (!maxed) {
            lore.add(Lore.upgrade(stat.colour(), "Next",
                    String.format("%.2f", stat.multiplier(level)) + "x",
                    String.format("%.2f", stat.multiplier(level + 1)) + "x"));
        }
        lore.add(Lore.barMinimal(level / (double) stat.maxLevel()));
        lore.add("");
        if (!maxed) {
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Cost: " + Currency.COINS.price(cost, affordable));
        }
        lore.add("");
        if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else if (affordable) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade");
            lore.add(Lore.footnote("Shift-click to buy as many as you can"));
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Coins");
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(statKey(plugin), PersistentDataType.STRING, stat.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack cropPanel(SolRNGPlugin plugin, PlayerData data,
                                       CropType crop, List<CropBoosts.Stat> stats) {
        ItemStack item = new ItemStack(crop == null ? Material.WHEAT : seedItem(crop.getMaterial()));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, crop == null ? "Your Crop" : crop.getDisplay()));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Crop upgrades");
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, "These levels sit on " + ChatColor.WHITE
                + (crop == null ? "this crop" : crop.getDisplay()) + ChatColor.GRAY + " alone."));
        lore.add(Lore.line(ChatColor.GRAY, "Switch crop and you switch boosts."));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Spent here"));
        int total = 0;
        int cap = 0;
        for (CropBoosts.Stat stat : stats) {
            total += CropBoosts.levelOf(data, crop == null ? "" : crop.getId(), stat.id());
            cap += stat.maxLevel();
        }
        lore.add(Lore.stat(ChatColor.AQUA, "Levels", total + " / " + cap));
        lore.add(Lore.stat(Currency.COINS.colour(), "Your Coins",
                Currency.COINS.amount(data.getTokens())));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ Back");
        meta.setLore(List.of(Lore.line(ChatColor.GRAY, "Back to your crops.")));
        item.setItemMeta(meta);
        return item;
    }

    /** Crops are not obtainable as items, so show the seed or food equivalent. */
    private static Material seedItem(Material cropBlock) {
        return switch (cropBlock) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> cropBlock.isItem() ? cropBlock : Material.WHEAT_SEEDS;
        };
    }
}
