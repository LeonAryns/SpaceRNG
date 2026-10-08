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
 * One egg's own screen (V359), built from Leon's screenshot.
 *
 * The seven pets the egg can hatch sit in a row, one per rarity, Common
 * on the left. A pet already discovered shows what it is and what it is
 * worth. One that has never been found is a "???  Locked" card, and
 * every card, found or not, prints the chance of pulling it. That is
 * only honest because an egg holds exactly one pet per rarity: the
 * rarity roll IS the pet roll, so the number beside a pet is the number
 * the egg actually uses.
 *
 * Under them are the three buy buttons Leon asked for, 1, 3 and 9, and
 * the auto open switch for linked accounts. Everything is paid in Gems.
 */
public class PetEggGui {

    private static final int SIZE = 54;
    private static final int[] PET_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] BUY_SLOTS = {29, 31, 33};
    public static final int CARD_SLOT = 22;
    public static final int AUTO_SLOT = 49;
    public static final int BACK_SLOT = 45;
    public static final int LUCK_SLOT = 53;

    public static NamespacedKey buyKey() {
        return SolRNGPlugin.key("solrng_egg_buy");
    }

    /** How many eggs this button buys, or 0 when it is not a buy button. */
    public static int clickedBuy(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return 0;
        Integer count = item.getItemMeta().getPersistentDataContainer()
                .get(buyKey(), PersistentDataType.INTEGER);
        return count == null ? 0 : count;
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, String eggId) {
        PetManager pets = plugin.getPetManager();
        PetEgg egg = pets.upgrades().egg(eggId);
        PetEggHolder holder = new PetEggHolder(eggId);
        Inventory inv = Bukkit.createInventory(holder, SIZE, MenuStyle.title(
                egg == null ? "Egg" : ChatColor.stripColor(egg.display()), "#C77DFF", "#7FDBFF"));
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, i < 9 || (i >= 18 && i < 27) || i >= 45 ? rail : filler);
        }
        if (egg == null) {
            inv.setItem(BACK_SLOT, back());
            MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
            return inv;
        }

        inv.setItem(CARD_SLOT, eggCard(plugin, data, egg));
        for (int i = 0; i < PET_SLOTS.length && i < Rarity.values().length; i++) {
            inv.setItem(PET_SLOTS[i], petCard(plugin, data, egg, Rarity.values()[i]));
        }

        List<Integer> bulk = plugin.getConfig().getIntegerList("pets.eggs.bulk");
        if (bulk.isEmpty()) bulk = List.of(1, 3, 9);
        for (int i = 0; i < BUY_SLOTS.length && i < bulk.size(); i++) {
            inv.setItem(BUY_SLOTS[i], buyButton(plugin, data, egg, Math.max(1, bulk.get(i))));
        }

        inv.setItem(AUTO_SLOT, autoButton(plugin, player, data));
        inv.setItem(LUCK_SLOT, luckCard(plugin, data));
        inv.setItem(BACK_SLOT, back());
        MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
        return inv;
    }

    /** The egg itself: what it costs, what it needs and how far through it you are. */
    private static ItemStack eggCard(SolRNGPlugin plugin, PlayerData data, PetEgg egg) {
        PetManager pets = plugin.getPetManager();
        boolean reached = data.getPrestige() >= egg.minPrestige();
        boolean canPay = data.getShards() >= egg.cost();
        int found = pets.foundIn(data, egg);
        int total = egg.pets().size();

        ItemStack item = new ItemStack(egg.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient(egg.display(), true, egg.stops()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, Lore.key("Seven pets") + ", one per rarity."));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "This egg"));
        lore.add(Lore.stat(ChatColor.AQUA, "Price", Currency.GEMS.price(egg.cost(), canPay)));
        lore.add(Lore.stat(reached ? ChatColor.GREEN : ChatColor.RED, "Prestige",
                (reached ? "" + Lore.TICK + " " : "") + egg.minPrestige() + " needed"));
        lore.add(Lore.stat(ChatColor.AQUA, "Discovered", found + " / " + total));
        lore.add(Lore.bar(total == 0 ? 0.0 : found / (double) total));
        lore.add("");
        if (found >= total && total > 0) {
            lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.WHITE + "Complete"
                    + ChatColor.GREEN + "  " + Lore.TICK);
            lore.add(Lore.line(ChatColor.GRAY, "It pays its full Pet Luck."));
        } else {
            lore.add(Lore.line(ChatColor.GRAY, "Finding all seven pays"));
            lore.add(Lore.line(ChatColor.GRAY, "+100% Pet Luck on its own."));
        }
        if (!reached) {
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked until Prestige " + egg.minPrestige());
        }
        meta.setLore(lore);
        if (found >= total && total > 0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One pet in the egg, found or not.
     *
     * A pet never found is a barrier called "???" with the chance still
     * printed, which is the shape Leon's screenshot has. The chance is
     * the live one, Pet Luck included, so the card and the roll cannot
     * drift apart.
     */
    private static ItemStack petCard(SolRNGPlugin plugin, PlayerData data, PetEgg egg, Rarity rarity) {
        PetManager pets = plugin.getPetManager();
        String id = egg.petAt(rarity);
        PetType type = id == null ? null : pets.get(id);
        if (type == null) return pane(Material.GRAY_STAINED_GLASS_PANE);

        boolean found = data.getPetsFound().contains(type.id());
        boolean owned = data.ownsPet(type.id());
        double chance = pets.liveChance(data, egg, rarity);

        ItemStack item = new ItemStack(found ? type.icon() : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(found
                ? Lore.gradient(type.display(), true, type.stops())
                : Lore.title(ChatColor.DARK_GRAY, "???"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + plugin.getRarityManager().style(rarity, rarity.displayName())
                + ChatColor.DARK_GRAY + " pet");
        lore.add("");
        if (found) {
            if (!type.blurb().isBlank()) lore.add(Lore.line(ChatColor.GRAY, type.blurb()));
            lore.add(Lore.stat(ChatColor.GREEN, "Fresh", type.boostText()));
        } else {
            lore.add(Lore.line(ChatColor.DARK_GRAY, "Locked"));
            lore.add(Lore.line(ChatColor.DARK_GRAY, "Open the egg to find out."));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Chance", ChatColor.WHITE + percent(chance)));
        lore.add(Lore.stat(ChatColor.AQUA, "One in", ChatColor.WHITE + oneIn(chance)));
        lore.add("");
        lore.add(found
                ? (owned ? ChatColor.GREEN + Lore.BULLET + " " + ChatColor.WHITE + "In your storage"
                        : ChatColor.YELLOW + Lore.BULLET + " " + ChatColor.WHITE + "Discovered, not held")
                : ChatColor.DARK_GRAY + Lore.BULLET + " Never found");
        meta.setLore(lore);
        if (owned) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /** Buy one, three or nine. */
    private static ItemStack buyButton(SolRNGPlugin plugin, PlayerData data, PetEgg egg, int count) {
        PetManager pets = plugin.getPetManager();
        long cost = egg.cost() * count;
        boolean canPay = data.getShards() >= cost;
        boolean reached = data.getPrestige() >= egg.minPrestige();
        boolean room = !pets.storageFull(data);

        ItemStack item = new ItemStack(count >= 9 ? Material.EMERALD_BLOCK
                : count >= 3 ? Material.EMERALD : Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, "Open " + count));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, count == 1
                ? "One egg, one pet." : count + " eggs, one after the other."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Cost", Currency.GEMS.price(cost, canPay)));
        lore.add(Lore.stat(ChatColor.AQUA, "You hold", Currency.GEMS.amount(data.getShards())));
        lore.add(Lore.stat(pets.storageFull(data) ? ChatColor.RED : ChatColor.AQUA, "Storage",
                pets.held(data) + " / " + pets.storage()));
        lore.add("");
        if (!reached) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Prestige " + egg.minPrestige() + " first");
        } else if (!room) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Storage full");
            lore.add(Lore.line(ChatColor.GRAY, "Nothing opens until you make room"));
        } else if (!canPay) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Gems");
            lore.add(Lore.line(ChatColor.GRAY, "Gems come off the farm and from crates"));
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(buyKey(), PersistentDataType.INTEGER, count);
        item.setItemMeta(meta);
        // The stack size is the number on the button, which reads faster
        // than the name does.
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
        meta.setLore(List.of(Lore.line(ChatColor.GRAY, "Your slots and the eggs."), "",
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
