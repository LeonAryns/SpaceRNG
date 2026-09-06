package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
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
 * /shop — one door to every shop in the plugin.
 *
 * Every shop already has its own command, but a player who hasn't found
 * them can't know that, and an NPC at spawn can only point at one thing.
 * This is that one thing.
 *
 * Locked shops are shown rather than hidden, with the skill that opens
 * them named on the card. Knowing a shop exists and what stands between
 * you and it is most of what makes a hub worth opening.
 */
public class ShopGui {

    /**
     * One shop. `node` is the skill that unlocks it — blank means always
     * open — and `command` is what a click runs.
     */
    private record Entry(int slot, Material icon, ChatColor accent, String name, String node,
                         String command, String[] blurb) {
    }

    private static final Entry[] SHOPS = {
            new Entry(10, Material.NETHER_STAR, ChatColor.LIGHT_PURPLE, "Starforge", "", "starforge",
                    new String[]{"Forge a better Starforge.",
                                 "More base Luck on every roll."}),
            new Entry(12, Material.IRON_CHESTPLATE, ChatColor.AQUA, "Armor", "armor_unlock", "armor",
                    new String[]{"Buy armor that adds Luck and",
                                 "Speed while you wear it."}),
            new Entry(14, Material.BREWING_STAND, ChatColor.LIGHT_PURPLE, "Brewing Shelf",
                    "potion_unlock", "potion",
                    new String[]{"Draughts of Luck and Speed,",
                                 "brewed from your own drops."}),
            new Entry(16, Material.CHEST, ChatColor.GREEN, "Convert", "convert_unlock", "convert",
                    new String[]{"Turn loose drops into stored",
                                 "ones you can spend anywhere."}),
            new Entry(29, Material.HEART_OF_THE_SEA, ChatColor.DARK_AQUA, "Artifacts",
                    "artifact_unlock", "",
                    new String[]{"Something is being built here."}),
            new Entry(31, Material.WHEAT, ChatColor.GOLD, "Farmer's Hoe", "farming_unlock", "crops",
                    new String[]{"Pick what the field grows for",
                                 "you. Right-click the hoe to",
                                 "upgrade its enchants."}),
            new Entry(33, Material.WRITABLE_BOOK, ChatColor.GOLD, "Battle Pass", "pass_unlock", "pass",
                    new String[]{"A season of rewards, earned",
                                 "by rolling and by farming."}),
            new Entry(35, Material.SUNFLOWER, ChatColor.LIGHT_PURPLE, "Store", "", "buy",
                    new String[]{"Spend Credits on a global",
                                 "Luck boost or the premium pass."}),
    };

    private static final int SELF_SLOT = 49;

    public static NamespacedKey commandKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_shop_command");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Shops");
        holder.setInventory(inv);

        ItemStack frame = pane(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            inv.setItem(slot, row == 0 || row == 5 || column == 0 || column == 8 ? frame : fill);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        for (Entry shop : SHOPS) {
            inv.setItem(shop.slot(), buildCard(plugin, data, shop));
        }
        inv.setItem(SELF_SLOT, buildSelf(plugin, player, data));
        return inv;
    }

    private static ItemStack buildCard(SolRNGPlugin plugin, PlayerData data, Entry shop) {
        boolean unlocked = shop.node().isEmpty() || data.hasUnlocked(shop.node());
        boolean built = !shop.command().isEmpty();

        ItemStack item = new ItemStack(shop.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(unlocked ? shop.accent() : ChatColor.DARK_GRAY, shop.name()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What's inside"));
        for (String line : shop.blurb()) {
            lore.add(Lore.line(ChatColor.AQUA, line));
        }
        lore.add("");

        if (!built) {
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "COMING SOON");
        } else if (!unlocked) {
            var node = plugin.getSkillTreeManager().get(shop.node());
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "LOCKED");
            lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Buy " + ChatColor.YELLOW
                    + (node == null ? shop.node() : node.getDisplay())
                    + ChatColor.GRAY + " in " + ChatColor.YELLOW + "/skilltree");
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO OPEN");
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " /" + shop.command());
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(unlocked && built ? Boolean.TRUE : null);
        if (unlocked && built) {
            meta.getPersistentDataContainer().set(commandKey(plugin), PersistentDataType.STRING, shop.command());
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildSelf(SolRNGPlugin plugin, Player player, PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        meta.setDisplayName(Lore.title(ChatColor.GOLD, player.getName()));
        meta.setLore(List.of(
                Lore.section(ChatColor.GOLD, "Your wallet"),
                Currency.MONEY.colour() + Lore.BULLET + " " + Currency.MONEY.amount(balanceOf(player)),
                Currency.COINS.colour() + Lore.BULLET + " " + Currency.COINS.amount(data.getTokens()),
                Currency.GEMS.colour() + Lore.BULLET + " " + Currency.GEMS.amount(data.getShards()),
                Currency.CREDITS.colour() + Lore.BULLET + " " + Currency.CREDITS.amount(data.getPoints()),
                "",
                ChatColor.DARK_GRAY + Lore.BULLET + " Drops buy armor, potions and Starforges.",
                ChatColor.DARK_GRAY + Lore.BULLET + " Credits only come from the store."));
        item.setItemMeta(meta);
        return item;
    }

    private static long balanceOf(Player player) {
        var registration = Bukkit.getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (registration == null) return 0L;
        return Math.round(registration.getProvider().getBalance(player));
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
