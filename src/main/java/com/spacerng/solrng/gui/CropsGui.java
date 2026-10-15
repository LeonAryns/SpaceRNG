package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.farming.CropType;
import com.spacerng.solrng.farming.CropUpgrades;
import com.spacerng.solrng.farming.FarmPlotManager;
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
 * /crops: pick the crop your farm grows, and spend crop coins on the
 * upgrades every crop shares (V391).
 *
 *   top      your crop coins, with the crops farmed that made them
 *   row 2    the crops, a ladder left to right
 *   row 4    the upgrades
 *
 * Changing crop repaints every plot in range for you alone; nobody else's
 * field changes.
 */
public class CropsGui {

    private static final int SIZE = 45;
    private static final int COINS_SLOT = 4;
    private static final int[] UPGRADE_SLOTS = {28, 30, 32, 34};

    public static NamespacedKey cropKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_crop_id");
    }

    public static NamespacedKey upgradeKey() {
        return SolRNGPlugin.key("solrng_crop_upgrade");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        CropsHolder holder = new CropsHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, MenuStyle.title("Crops", "#B9F6CA", "#00C853"));
        holder.setInventory(inv);

        ItemStack filler = MenuStyle.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) inv.setItem(slot, filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        FarmPlotManager farm = plugin.getFarmPlotManager();
        CropType selected = farm.cropFor(data);

        // V277: one centred row while the crops fit in it, so six read as a
        // ladder rather than as a list that stopped early.
        List<CropType> crops = new ArrayList<>(farm.getCrops().values());
        int[] slots = centred(Math.min(7, crops.size()));
        for (int i = 0; i < crops.size() && i < slots.length; i++) {
            CropType crop = crops.get(i);
            inv.setItem(slots[i], cropIcon(plugin, data, farm, crop,
                    selected != null && selected.getId().equals(crop.getId()), i + 1, crops.size()));
        }

        inv.setItem(COINS_SLOT, coinsCard(plugin, data));
        for (int i = 0; i < CropUpgrades.ALL.size() && i < UPGRADE_SLOTS.length; i++) {
            inv.setItem(UPGRADE_SLOTS[i], upgradeIcon(plugin, data, CropUpgrades.ALL.get(i)));
        }
        MenuStyle.apply(inv, MenuStyle.Palette.GREEN);
        return inv;
    }

    /** The middle of row 2 for n crops, with the centre left open when n is even. */
    private static int[] centred(int n) {
        int[][] rows = {
                {}, {13}, {12, 14}, {12, 13, 14}, {11, 12, 14, 15},
                {11, 12, 13, 14, 15}, {10, 11, 12, 14, 15, 16}, {10, 11, 12, 13, 14, 15, 16}};
        return rows[Math.max(0, Math.min(7, n))];
    }

    /** "129 crop coins", with the crops farmed that made them underneath. */
    private static ItemStack coinsCard(SolRNGPlugin plugin, PlayerData data) {
        long balance = CropUpgrades.balance(plugin, data);
        long per = CropUpgrades.cropsPerCoin(plugin);
        long harvested = data.getCropsHarvested();
        long toNext = per - harvested % per;

        ItemStack item = new ItemStack(Material.HAY_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, String.format("%,d", balance)
                + (balance == 1 ? " crop coin" : " crop coins")));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + String.format("%,d", harvested) + " crops farmed");
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, "One crop coin for every "
                + Lore.key(String.format("%,d", per)) + ChatColor.GRAY + " crops."));
        lore.add(Lore.line(ChatColor.GRAY, "Spend them on the upgrades below."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Next coin", String.format("%,d", toNext) + " crops"));
        lore.add(Lore.bar((per - toNext) / (double) per));
        lore.add(Lore.stat(ChatColor.AQUA, "Spent", String.format("%,d", CropUpgrades.spent(data))));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack upgradeIcon(SolRNGPlugin plugin, PlayerData data, CropUpgrades.Stat stat) {
        int level = CropUpgrades.levelOf(data, stat);
        int max = CropUpgrades.maxLevel(plugin, data);
        int perPct = (int) Math.round(CropUpgrades.perLevel(plugin) * 100);
        boolean available = CropUpgrades.available(plugin, data, stat);
        boolean maxed = level >= max;
        boolean canPay = CropUpgrades.balance(plugin, data) >= 1;

        ItemStack item = new ItemStack(available ? stat.icon() : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(available ? stat.colour() : ChatColor.DARK_GRAY, stat.display()));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Every crop");
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, stat.pitch()));
        lore.add(Lore.line(ChatColor.GRAY, "+" + perPct + "% a level, one crop coin each."));
        lore.add("");
        lore.add(Lore.stat(stat.colour(), "Level", String.format("%,d", level) + " / " + String.format("%,d", max)));
        lore.add(Lore.bar(max <= 0 ? 0.0 : level / (double) max));
        lore.add(Lore.stat(stat.colour(), "Now", "+" + level * perPct + "%"));
        if (!maxed) lore.add(Lore.stat(ChatColor.DARK_GRAY, "Next", "+" + (level + 1) * perPct + "%"));
        lore.add("");
        if (!available) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            CropType needs = plugin.getFarmPlotManager().getCrop(stat.requires());
            lore.add(Lore.line(ChatColor.GRAY, "Opens with "
                    + (needs == null ? stat.requires() : needs.getDisplay())));
        } else if (maxed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
            lore.add(Lore.footnote("Every crop you unlock adds 100 levels."));
        } else if (canPay) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade");
            lore.add(Lore.footnote("Shift click buys all you can."));
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough crop coins");
        }
        meta.setLore(lore);
        if (maxed) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(upgradeKey(), PersistentDataType.STRING, stat.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The upgrade on a clicked item, or null. */
    public static CropUpgrades.Stat clickedUpgrade(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(upgradeKey(), PersistentDataType.STRING);
        if (id == null) return null;
        for (CropUpgrades.Stat stat : CropUpgrades.ALL) {
            if (stat.id().equals(id)) return stat;
        }
        return null;
    }

    private static ItemStack cropIcon(SolRNGPlugin plugin, PlayerData data, FarmPlotManager farm,
                                      CropType crop, boolean selected, int place, int of) {
        boolean unlocked = farm.isUnlocked(data, crop);

        // The real crop even while locked (V277): grey dye said nothing
        // about what you are working toward.
        ItemStack icon = new ItemStack(seedItem(crop.getMaterial()));
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(
                selected ? ChatColor.GREEN : unlocked ? ChatColor.YELLOW : ChatColor.DARK_GRAY,
                crop.getDisplay()));
        meta.setEnchantmentGlintOverride(selected ? Boolean.TRUE : null);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Crop " + place + " of " + of);
        lore.add("");
        // V391, Leon: what a crop pays on its own, and what it pays you
        // with every stat you have (before Momentum and the golden crop).
        lore.add(Lore.section(ChatColor.GREEN, "Earns per crop"));
        lore.add(Lore.stat(ChatColor.GOLD, "Coins", Currency.COINS.exact(crop.getTokens())
                + ChatColor.DARK_GRAY + "  " + Lore.ARROW + " " + Currency.COINS.amount(
                        Math.round(farm.coinsPerCrop(data, crop)))));
        double baseGems = farm.gemsFor(crop);
        if (baseGems > 0) {
            if (farm.shardsUnlocked(data)) {
                double mine = farm.gemsPerCrop(data, crop);
                lore.add(Lore.stat(ChatColor.AQUA, "Gems", Currency.GEMS.exact(baseGems)
                        + ChatColor.DARK_GRAY + "  " + Lore.ARROW + " "
                        + (mine >= 1000 ? Currency.GEMS.amount(Math.round(mine)) : Currency.GEMS.exact(
                                Math.round(mine * 10.0) / 10.0))));
            } else {
                lore.add(Lore.stat(ChatColor.DARK_GRAY, "Gems", ChatColor.DARK_GRAY
                        + String.valueOf(baseGems == Math.rint(baseGems) ? (long) baseGems : baseGems)
                        + " (buy Gem Earnings)"));
            }
        }
        lore.add(Lore.footnote("Left is the crop, right is you."));
        // V392, Leon: the crop's pet egg and its drop chance, "(0.1%)".
        var egg = plugin.getPetManager().upgrades().eggForCrop(crop.getId());
        if (egg != null) {
            lore.add("");
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Pet egg", Lore.gradient(egg.display(), false, egg.stops())
                    + ChatColor.DARK_GRAY + " (" + ChatColor.AQUA
                    + eggPercent(plugin.getPetManager().eggDropChance(data)) + ChatColor.DARK_GRAY + ")"));
        }
        lore.add("");
        if (selected) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Growing now");
        } else if (unlocked) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to plant");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            long at = farm.unlockAt(crop);
            if (at > 0) {
                // V279: opens with farming, no price.
                long have = Math.min(at, data.getCropsHarvested());
                lore.add(Lore.stat(ChatColor.AQUA, "Crops farmed",
                        String.format("%,d", have) + " / " + String.format("%,d", at)));
                lore.add(Lore.bar(have / (double) at));
            } else {
                lore.add(Lore.line(ChatColor.GRAY, "Unlock it in /farmtree"));
            }
        }

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(cropKey(plugin), PersistentDataType.STRING, crop.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    /** "0.1%", with as many places as a small chance needs. */
    private static String eggPercent(double chance) {
        double value = chance * 100.0;
        String text = value >= 1 ? String.format("%.1f", value)
                : value >= 0.1 ? String.format("%.2f", value) : String.format("%.3f", value);
        // Trailing zeros say nothing: 0.10% reads as 0.1%.
        if (text.contains(".")) text = text.replaceAll("0+$", "").replaceAll("[.]$", "");
        return text + "%";
    }

    /** Crops aren't obtainable as items, so show the seed/food equivalent. */
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
