package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.pet.PetEgg;
import com.spacerng.solrng.pet.PetManager;
import com.spacerng.solrng.pet.PetType;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
 * The egg screen (V366), built to Leon's screenshot.
 *
 *   top row      every egg, one per Prestige step, seven to a page with
 *                arrows at the ends; the one you are looking at glints
 *   middle       the seven pets in this egg, five over two, a found one
 *                as itself and a missing one as a "???" barrier, each
 *                with its multiplier and its live chance
 *   bottom row   back to /pets, your Pet Luck, Open 1, 3 and 9, and
 *                auto open on the right for linked accounts
 *
 * A click on a found pet marks it to be thrown away whenever it hatches
 * again (auto delete). Everything is paid in Gems.
 */
public class PetEggGui {

    private static final int SIZE = 54;
    private static final int[] PET_SLOTS = {20, 21, 22, 23, 24, 30, 32};
    private static final int[] EGG_TABS = {1, 2, 3, 4, 5, 6, 7};
    private static final int[] BUY_SLOTS = {48, 49, 50};
    public static final int PREV_EGGS = 0;
    public static final int NEXT_EGGS = 8;
    public static final int BACK_SLOT = 45;
    public static final int LUCK_SLOT = 46;
    public static final int AUTO_SLOT = 52;

    public static NamespacedKey buyKey() {
        return SolRNGPlugin.key("solrng_egg_buy");
    }

    public static NamespacedKey tabKey() {
        return SolRNGPlugin.key("solrng_egg_tab");
    }

    public static NamespacedKey trashKey() {
        return SolRNGPlugin.key("solrng_egg_trash");
    }

    /** How many eggs this button buys, or 0 when it is not a buy button. */
    public static int clickedBuy(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return 0;
        Integer count = item.getItemMeta().getPersistentDataContainer()
                .get(buyKey(), PersistentDataType.INTEGER);
        return count == null ? 0 : count;
    }

    /** The egg a clicked tab or page arrow leads to, or null. */
    public static String clickedTab(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(tabKey(), PersistentDataType.STRING);
    }

    /** The pet a clicked card marks for auto delete, or null. */
    public static String clickedTrash(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(trashKey(), PersistentDataType.STRING);
    }

    /** The first egg, for the placed egg and anything that does not name one. */
    public static String firstEgg(SolRNGPlugin plugin) {
        var eggs = plugin.getPetManager().upgrades().eggs();
        return eggs.isEmpty() ? "" : eggs.get(0).id();
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, String eggId) {
        PetManager pets = plugin.getPetManager();
        var eggs = pets.upgrades().eggs();
        PetEgg egg = pets.upgrades().egg(eggId);
        if (egg == null && !eggs.isEmpty()) egg = eggs.get(0);
        PetEggHolder holder = new PetEggHolder(egg == null ? eggId : egg.id());
        Inventory inv = Bukkit.createInventory(holder, SIZE, MenuStyle.title(
                "Egg: " + (egg == null ? "?" : ChatColor.stripColor(egg.display()).replace(" Egg", "")),
                "#C77DFF", "#7FDBFF"));
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || i >= 45 ? rail : filler);
        inv.setItem(BACK_SLOT, back());
        if (egg == null) {
            MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
            return inv;
        }

        // The egg row, seven to a page, on the page that holds this egg.
        int at = egg.index();
        int page = at / EGG_TABS.length;
        int from = page * EGG_TABS.length;
        for (int i = 0; i < EGG_TABS.length && from + i < eggs.size(); i++) {
            inv.setItem(EGG_TABS[i], eggTab(plugin, data, eggs.get(from + i), from + i == at));
        }
        if (page > 0) {
            inv.setItem(PREV_EGGS, pageArrow(Material.SPECTRAL_ARROW, eggs.get(from - 1).id(), "Earlier eggs"));
        }
        if (from + EGG_TABS.length < eggs.size()) {
            inv.setItem(NEXT_EGGS, pageArrow(Material.ARROW, eggs.get(from + EGG_TABS.length).id(), "Later eggs"));
        }

        for (int i = 0; i < PET_SLOTS.length && i < Rarity.values().length; i++) {
            inv.setItem(PET_SLOTS[i], petCard(plugin, data, egg, Rarity.values()[i]));
        }

        List<Integer> bulk = plugin.getConfig().getIntegerList("pets.eggs.bulk");
        if (bulk.isEmpty()) bulk = List.of(1, 3, 9);
        for (int i = 0; i < BUY_SLOTS.length && i < bulk.size(); i++) {
            inv.setItem(BUY_SLOTS[i], buyButton(plugin, data, egg, Math.max(1, bulk.get(i))));
        }
        inv.setItem(LUCK_SLOT, luckCard(plugin, data));
        inv.setItem(AUTO_SLOT, autoButton(plugin, player, data));
        MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
        return inv;
    }

    private static final String RULE = ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH
            + "                         ";

    /**
     * One egg in the top row, in the shape of Leon's second screenshot:
     * the name, a rule, the price, a rule, and the Prestige it needs.
     */
    private static ItemStack eggTab(SolRNGPlugin plugin, PlayerData data, PetEgg egg, boolean current) {
        boolean reached = data.getPrestige() >= egg.minPrestige();
        boolean canPay = data.getShards() >= egg.cost();
        int found = plugin.getPetManager().foundIn(data, egg);
        int total = egg.pets().size();

        ItemStack item = new ItemStack(egg.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient(egg.display(), true, egg.stops()));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Egg");
        lore.add(RULE);
        lore.add(ChatColor.GRAY + " Price: " + Currency.GEMS.price(egg.cost(), canPay && reached));
        lore.add(RULE);
        lore.add(ChatColor.GRAY + " Found: " + (found >= total ? ChatColor.GREEN : ChatColor.AQUA)
                + found + ChatColor.DARK_GRAY + " / " + ChatColor.AQUA + total);
        lore.add("");
        lore.add((reached ? ChatColor.GREEN : ChatColor.RED) + "Prestige required: "
                + ChatColor.DARK_GRAY + "[" + (reached ? ChatColor.GREEN : ChatColor.YELLOW)
                + egg.minPrestige() + ChatColor.DARK_GRAY + "]");
        lore.add("");
        lore.add(current ? ChatColor.GREEN + "" + ChatColor.BOLD + "Viewing"
                : ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to view");
        meta.setLore(lore);
        if (current) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(tabKey(), PersistentDataType.STRING, egg.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pageArrow(Material material, String eggId, String label) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, label));
        meta.setLore(List.of(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to turn"));
        meta.getPersistentDataContainer().set(tabKey(), PersistentDataType.STRING, eggId);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One pet in the egg, in the shape of Leon's screenshot: its name, its
     * rarity, the multiplier with the stat it starts on, the live chance,
     * and auto delete on a click. A pet never found is a "???" barrier
     * that still prints its chance.
     */
    private static ItemStack petCard(SolRNGPlugin plugin, PlayerData data, PetEgg egg, Rarity rarity) {
        PetManager pets = plugin.getPetManager();
        String id = egg.petAt(rarity);
        PetType type = id == null ? null : pets.get(id);
        if (type == null) return pane(Material.BLACK_STAINED_GLASS_PANE);

        boolean found = data.getPetsFound().contains(type.id());
        boolean trash = data.isPetAutoTrash(type.id());
        double chance = pets.liveChance(data, egg, rarity);
        String rarityName = plugin.getRarityManager().style(rarity, rarity.displayName());

        ItemStack item = new ItemStack(found ? type.icon() : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(found
                ? Lore.gradient(type.display(), true, type.stops())
                : Lore.title(ChatColor.DARK_GRAY, "???"));

        List<String> lore = new ArrayList<>();
        lore.add(rarityName);
        lore.add("");
        lore.add(Lore.stat(ChatColor.YELLOW, "Multi", ChatColor.GREEN + type.multiText(1.0)
                + ChatColor.GRAY + " (" + type.statName() + ")"));
        lore.add(Lore.stat(ChatColor.YELLOW, "Chance", ChatColor.AQUA + percent(chance)
                + ChatColor.DARK_GRAY + "  1 in " + oneIn(chance)));
        lore.add("");
        if (!found) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Open this egg to find it"));
        } else if (trash) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Auto delete on");
            lore.add(Lore.footnote("Click to keep it again."));
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to auto delete");
        }
        meta.setLore(lore);
        if (found) meta.getPersistentDataContainer().set(trashKey(), PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Open 1, 3 or 9, in the shape of Leon's screenshot. */
    private static ItemStack buyButton(SolRNGPlugin plugin, PlayerData data, PetEgg egg, int count) {
        PetManager pets = plugin.getPetManager();
        long cost = egg.cost() * count;
        boolean canPay = data.getShards() >= cost;
        boolean reached = data.getPrestige() >= egg.minPrestige();
        boolean room = !pets.storageFull(data);

        ItemStack item = new ItemStack(Material.TURTLE_EGG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, count == 1 ? "Open egg" : "Open eggs")
                + ChatColor.GRAY + " (x" + count + ")");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Utility");
        lore.add("");
        lore.add(Lore.stat(ChatColor.YELLOW, "Cost", Currency.GEMS.price(cost, canPay)));
        lore.add(Lore.stat(pets.storageFull(data) ? ChatColor.RED : ChatColor.AQUA, "Storage",
                pets.held(data) + " / " + pets.storage()));
        lore.add("");
        if (!reached) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Reach Prestige " + egg.minPrestige() + " in /prestige"));
        } else if (!room) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Storage full");
            lore.add(Lore.line(ChatColor.GRAY, "Nothing opens until you make room"));
        } else if (!canPay) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Gems");
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(buyKey(), PersistentDataType.INTEGER, count);
        item.setItemMeta(meta);
        item.setAmount(Math.max(1, Math.min(64, count)));
        return item;
    }

    /** Auto open: linked accounts only, Leon's gate. */
    private static ItemStack autoButton(SolRNGPlugin plugin, Player player, PlayerData data) {
        boolean linked = plugin.getLinkedAccountManager().isLinked(player.getUniqueId());
        boolean on = linked && data.isPetAutoOpen();

        ItemStack item = new ItemStack(linked
                ? (on ? Material.LIME_DYE : Material.GRAY_DYE) : Material.IRON_BARS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(on ? ChatColor.GREEN : ChatColor.GRAY, "Auto open")
                + ChatColor.DARK_GRAY + " - " + (on ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "A click keeps buying and opening"));
        lore.add(Lore.line(ChatColor.GRAY, "until the Gems or the room run out."));
        lore.add("");
        if (!linked) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Link your account first");
            lore.add(Lore.line(ChatColor.GRAY, "Run " + ChatColor.YELLOW + "/link"
                    + ChatColor.GRAY + " to switch this on"));
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD
                    + (on ? "Click to switch off" : "Click to switch on"));
        }
        meta.setLore(lore);
        if (on) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /** What the pet index is paying right now. */
    static ItemStack luckCard(SolRNGPlugin plugin, PlayerData data) {
        PetManager pets = plugin.getPetManager();
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Pet Luck"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, Lore.key("Every pet you find") + " tilts every egg"));
        lore.add(Lore.line(ChatColor.GRAY, "toward its rarer pets."));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Where it comes from"));
        lore.add(Lore.stat(ChatColor.AQUA, "Pets found",
                pets.found(data) + " / " + pets.catalogue()));
        lore.add(Lore.stat(ChatColor.AQUA, "Eggs complete",
                pets.eggsCompleted(data) + " / " + pets.upgrades().eggs().size()));
        lore.add("");
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, "+"
                + Math.round(pets.petLuck(data) * 100.0) + "% Pet Luck"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack back() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Pets"));
        meta.setLore(List.of(Lore.line(ChatColor.GRAY, "Your slots and your pets."), "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to go back"));
        item.setItemMeta(meta);
        return item;
    }

    /** "0.001%", with enough decimals that a Divine never reads as zero. */
    static String percent(double chance) {
        double pct = chance * 100.0;
        if (pct >= 10) return String.format("%.1f", pct) + "%";
        if (pct >= 1) return String.format("%.2f", pct) + "%";
        if (pct >= 0.01) return String.format("%.3f", pct) + "%";
        return String.format("%.5f", pct) + "%";
    }

    static String oneIn(double chance) {
        if (chance <= 0) return "never";
        return String.format("%,d", Math.max(1L, Math.round(1.0 / chance)));
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
