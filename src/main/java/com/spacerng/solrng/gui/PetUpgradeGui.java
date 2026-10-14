package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.pet.PetInstance;
import com.spacerng.solrng.pet.PetManager;
import com.spacerng.solrng.pet.PetType;
import com.spacerng.solrng.pet.PetUpgrades;
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
 * One pet: what it gives, and the three things you do to it.
 *
 * The card in the middle is what the pet pays right now. Under it sit
 * the level, the stat and shiny.
 *
 * V359, Leon's rework. The rarity ladder and the tier ladder are gone,
 * and with them the upgrade that could eat the payment on a failure.
 * What is left is one level, 1 to 10, bought with Cosmic Dust and always
 * taking, and a button that points the pet's multiplier at whichever
 * stat the player wants. The stat is free and can be changed as often as
 * they like, which is the whole point: what a pet is worth is its rarity
 * and its level, where it pays is their choice.
 */
public class PetUpgradeGui {

    private static final int SIZE = 45;
    private static final int CARD_SLOT = 13;
    private static final int LEVEL_SLOT = 29;
    private static final int SHINY_SLOT = 33;
    private static final int BACK_SLOT = 40;

    public static NamespacedKey actionKey() {
        return SolRNGPlugin.key("solrng_pet_action");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, String petId) {
        PetManager pets = plugin.getPetManager();
        PetType type = pets.get(petId);
        PetUpgradeHolder holder = new PetUpgradeHolder(petId);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                MenuStyle.title((type == null ? "Pet" : stripped(type.display())), "#80DEEA", "#26C6DA"));
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i >= 18 && i < 27 ? rail : filler);

        PetInstance owned = type == null ? null : data.getPet(type.id());
        if (type == null || owned == null) {
            inv.setItem(CARD_SLOT, missing());
            inv.setItem(BACK_SLOT, back());
            MenuStyle.apply(inv, MenuStyle.Palette.GREY);
            return inv;
        }

        inv.setItem(CARD_SLOT, card(plugin, type, owned));
        inv.setItem(LEVEL_SLOT, levelButton(plugin, data, owned));
        inv.setItem(SHINY_SLOT, shinyButton(plugin, player, data, type, owned));
        inv.setItem(BACK_SLOT, back());
        // Bedrock (V223): a menu there cannot tell a left click from a
        // right one, so a click on the pets screen always opens this one,
        // and wearing a pet happens here instead.
        if (com.spacerng.solrng.platform.Bedrock.is(player)) {
            inv.setItem(WEAR_SLOT, wearButton(plugin, data, type));
        }
        MenuStyle.apply(inv, MenuStyle.Palette.GREY);
        return inv;
    }

    private static final int WEAR_SLOT = 22;

    /** Wear or take off this pet, for Bedrock players. */
    private static ItemStack wearButton(SolRNGPlugin plugin, PlayerData data, PetType type) {
        boolean worn = data.getEquippedPets().contains(type.id());
        boolean full = !worn && data.getEquippedPets().size() >= plugin.getPetManager().slots(data);
        ItemStack item = new ItemStack(worn ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(worn ? ChatColor.GREEN : ChatColor.AQUA, worn ? "Worn" : "Wear"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Gives " + type.boostText() + " while worn."));
        lore.add("");
        if (worn) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to take off");
        } else if (full) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Slots full");
            lore.add(Lore.line(ChatColor.GRAY, "Take one off on the pets screen"));
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to wear");
        }
        meta.setLore(lore);
        if (worn) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(actionKey(), PersistentDataType.STRING, "wear");
        item.setItemMeta(meta);
        return item;
    }

    /** What the pet is worth as it stands. */
    private static ItemStack card(SolRNGPlugin plugin, PetType type, PetInstance owned) {
        PetUpgrades upgrades = plugin.getPetManager().upgrades();
        double multiplier = upgrades.multiplier(owned);

        ItemStack item = type.item();
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(PetsGui.name(type, owned));

        List<String> lore = new ArrayList<>();
        if (!type.blurb().isBlank()) lore.add(Lore.line(ChatColor.GRAY, type.blurb()));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Now"));
        lore.add(Lore.pipe(ChatColor.GREEN, type.boostText(multiplier, owned.statOr(type))));
        lore.add(Lore.stat(ChatColor.AQUA, "Level", owned.level() + " / " + upgrades.maxLevel()));
        lore.add(Lore.stat(ChatColor.AQUA, "Boosting", PetType.statName(owned.statOr(type))));
        lore.add(Lore.stat(ChatColor.AQUA, "Shiny", owned.shiny() ? Lore.TICK : Lore.CROSS));
        if (owned.copies() > 1) {
            lore.add(Lore.stat(ChatColor.AQUA, "Copies", String.valueOf(owned.copies())));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Fresh", type.boostText(1.0, owned.statOr(type))));
        lore.add(Lore.stat(ChatColor.AQUA, "Grown by", trim(multiplier) + "x"));
        if (upgrades.capped(owned)) {
            lore.add("");
            lore.add(Lore.line(ChatColor.GRAY, "This pet is against the ceiling."));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The one ladder a pet has: level, paid in Cosmic Dust.
     *
     * Dust is the point. Leon asked for pets to grow off the Cosmic Dust
     * skills, so the thing that levels a pet is the thing those skills
     * pay out, and there is nothing here that can fail and swallow the
     * payment.
     */
    private static ItemStack levelButton(SolRNGPlugin plugin, PlayerData data, PetInstance owned) {
        PetUpgrades upgrades = plugin.getPetManager().upgrades();
        boolean maxed = owned.level() >= upgrades.maxLevel();
        long cost = plugin.getPetManager().levelCost(data, owned);
        boolean canPay = data.getCosmicDust() >= cost;

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Level up"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Every level is "
                + ChatColor.WHITE + Math.round(upgrades.levelStep() * 100.0) + "% more"));
        lore.add(Lore.line(ChatColor.GRAY, "multiplier. It always takes."));
        lore.add("");
        if (maxed) {
            lore.add(Lore.stat(ChatColor.AQUA, "Level", owned.level() + " / " + upgrades.maxLevel()));
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else {
            lore.add(Lore.upgrade(ChatColor.LIGHT_PURPLE, "Level",
                    String.valueOf(owned.level()), String.valueOf(owned.level() + 1)));
            lore.add(Lore.stat(ChatColor.AQUA, "Cost", Currency.COSMIC_DUST.price(cost, canPay)));
            lore.add(Lore.stat(ChatColor.AQUA, "You hold",
                    Currency.COSMIC_DUST.amount(data.getCosmicDust())));
            lore.add("");
            lore.add(canPay
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to level up"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Cosmic Dust");
            if (!canPay) lore.add(Lore.line(ChatColor.GRAY, "Cosmic Dust falls out of rolling"));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey(), PersistentDataType.STRING, "level");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack shinyButton(SolRNGPlugin plugin, Player player, PlayerData data,
                                         PetType type, PetInstance owned) {
        PetUpgrades upgrades = plugin.getPetManager().upgrades();
        // V388, Leon: paid in shinies, 1 for a Common and doubling with
        // every rarity up, instead of Gems.
        long cost = upgrades.shinyCost(type.rarity());
        long have = com.spacerng.solrng.player.DropWallet.shinies(plugin, player, data);
        boolean canPay = have >= cost;

        ItemStack item = new ItemStack(Material.GLOW_INK_SAC);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Make it shiny"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "A shiny pet carries the spark"));
        lore.add(Lore.line(ChatColor.GRAY, "and pays more on top of"));
        lore.add(Lore.line(ChatColor.GRAY, "everything else."));
        lore.add("");
        if (owned.shiny()) {
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Worth",
                    trim(1.0 + upgrades.shinyBonus()) + "x what it was"));
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Already shiny");
        } else {
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Worth",
                    trim(1.0 + upgrades.shinyBonus()) + "x what it is now"));
            lore.add(Lore.stat(canPay ? ChatColor.YELLOW : ChatColor.RED, "Cost",
                    (canPay ? ChatColor.WHITE : ChatColor.RED) + String.valueOf(cost)
                            + (cost == 1 ? " shiny " : " shinies ") + Lore.SPARK));
            lore.add(Lore.stat(ChatColor.AQUA, "You have", String.format("%,d", have)));
            lore.add("");
            if (canPay) {
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade");
            } else {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough shinies");
                lore.add(Lore.footnote("Common 1, and double for each rarity up."));
            }
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey(), PersistentDataType.STRING, "shiny");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack missing() {
        ItemStack item = new ItemStack(Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "No pet"));
        meta.setLore(List.of(Lore.line(ChatColor.GRAY, "This one is not in your collection.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack back() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Back"));
        meta.setLore(List.of(Lore.line(ChatColor.GRAY, "Back to your pets.")));
        item.setItemMeta(meta);
        return item;
    }

    /** The action on a clicked button, or null. */
    public static String clickedAction(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey(), PersistentDataType.STRING);
    }

    public static boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    private static String trim(double value) {
        String text = String.format("%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    /** An inventory title takes legacy codes only, so the gradient goes. */
    private static String stripped(String display) {
        return ChatColor.stripColor(display);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
