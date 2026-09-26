package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.pet.PetInstance;
import com.spacerng.solrng.pet.PetManager;
import com.spacerng.solrng.pet.PetType;
import com.spacerng.solrng.pet.PetUpgrades;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
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
 * One pet, and the three things you can spend on it.
 *
 * The card in the middle is what the pet gives right now. The three
 * buttons under it are the three ways it grows, and they are deliberately
 * not interchangeable: rarity is Cosmic Dust out of rolling and always
 * takes, tier is Farm Dust out of farming and can fail, shiny is a
 * one-off that needs you to have actually found a shiny of the pet's own
 * rarity.
 *
 * The tier button always shows its chance before you press it. An upgrade
 * that can eat your dust has to say so up front, every time.
 */
public class PetUpgradeGui {

    private static final int SIZE = 45;
    private static final int CARD_SLOT = 13;
    private static final int RARITY_SLOT = 29;
    private static final int TIER_SLOT = 31;
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
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Pet"
                        + (type == null ? "" : ChatColor.GRAY + " - " + stripped(type.display())));
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i >= 18 && i < 27 ? rail : filler);

        PetInstance owned = type == null ? null : data.getPet(type.id());
        if (type == null || owned == null) {
            inv.setItem(CARD_SLOT, missing());
            inv.setItem(BACK_SLOT, back());
            return inv;
        }

        inv.setItem(CARD_SLOT, card(plugin, type, owned));
        inv.setItem(RARITY_SLOT, rarityButton(plugin, data, owned));
        inv.setItem(TIER_SLOT, tierButton(plugin, data, owned));
        inv.setItem(SHINY_SLOT, shinyButton(plugin, data, type, owned));
        inv.setItem(BACK_SLOT, back());
        // Bedrock (V223): a menu there cannot tell a left click from a
        // right one, so a click on the pets screen always opens this one,
        // and wearing a pet happens here instead.
        if (com.spacerng.solrng.platform.Bedrock.is(player)) {
            inv.setItem(WEAR_SLOT, wearButton(plugin, data, type));
        }
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

        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(PetsGui.name(type, owned));

        List<String> lore = new ArrayList<>();
        if (!type.blurb().isBlank()) lore.add(Lore.line(ChatColor.GRAY, type.blurb()));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Now"));
        lore.add(Lore.pipe(ChatColor.GREEN, type.boostText(multiplier)));
        lore.add(Lore.stat(ChatColor.AQUA, "Rarity", owned.rarity() + " / " + upgrades.maxRarity()));
        lore.add(Lore.stat(ChatColor.AQUA, "Tier", owned.tier() + " / " + upgrades.maxTier()));
        lore.add(Lore.stat(ChatColor.AQUA, "Shiny", owned.shiny() ? Lore.TICK : Lore.CROSS));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Base", type.boostText()));
        lore.add(Lore.stat(ChatColor.AQUA, "Multiplier", trim(multiplier) + "x"));
        if (upgrades.capped(owned)) {
            lore.add("");
            lore.add(Lore.line(ChatColor.GRAY, "This pet is against the ceiling."));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack rarityButton(SolRNGPlugin plugin, PlayerData data, PetInstance owned) {
        PetUpgrades upgrades = plugin.getPetManager().upgrades();
        boolean maxed = owned.rarity() >= upgrades.maxRarity();
        long cost = upgrades.rarityCost(owned.rarity());
        boolean canPay = data.getCosmicDust() >= cost;

        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Raise rarity"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Rarity is the roller's half of"));
        lore.add(Lore.line(ChatColor.GRAY, "a pet. It always takes."));
        lore.add("");
        if (maxed) {
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity", owned.rarity() + " / " + upgrades.maxRarity()));
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else {
            lore.add(Lore.upgrade(ChatColor.LIGHT_PURPLE, "Rarity",
                    String.valueOf(owned.rarity()), String.valueOf(owned.rarity() + 1)));
            lore.add(Lore.stat(ChatColor.AQUA, "Cost", Currency.COSMIC_DUST.price(cost, canPay)));
            lore.add(Lore.stat(ChatColor.AQUA, "You hold", Currency.COSMIC_DUST.amount(data.getCosmicDust())));
            lore.add("");
            lore.add(canPay
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Cosmic Dust");
            if (!canPay) lore.add(Lore.line(ChatColor.GRAY, "Cosmic Dust falls while you roll"));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey(), PersistentDataType.STRING, "rarity");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack tierButton(SolRNGPlugin plugin, PlayerData data, PetInstance owned) {
        PetUpgrades upgrades = plugin.getPetManager().upgrades();
        boolean maxed = owned.tier() >= upgrades.maxTier();
        long cost = upgrades.tierCost(owned.tier());
        boolean canPay = data.getFarmDust() >= cost;
        double bonus = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.PET_TIER_CHANCE);
        double chance = upgrades.tierChance(owned.tier(), bonus);

        ItemStack item = new ItemStack(Material.WHEAT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GREEN, "Raise tier"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Tier is the farmer's half. It"));
        lore.add(Lore.line(ChatColor.GRAY, "can fail, and the dust is spent"));
        lore.add(Lore.line(ChatColor.GRAY, "either way."));
        lore.add("");
        if (maxed) {
            lore.add(Lore.stat(ChatColor.AQUA, "Tier", owned.tier() + " / " + upgrades.maxTier()));
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else {
            lore.add(Lore.upgrade(ChatColor.GREEN, "Tier",
                    String.valueOf(owned.tier()), String.valueOf(owned.tier() + 1)));
            lore.add(Lore.stat(ChatColor.AQUA, "Chance", Math.round(chance * 100.0) + "%"));
            lore.add(Lore.bar(chance));
            lore.add(Lore.stat(ChatColor.AQUA, "Cost", Currency.FARM_DUST.price(cost, canPay)));
            lore.add(Lore.stat(ChatColor.AQUA, "You hold", Currency.FARM_DUST.amount(data.getFarmDust())));
            lore.add("");
            lore.add(canPay
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to try"
                    : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Farm Dust");
            if (!canPay) lore.add(Lore.line(ChatColor.GRAY, "Farm Dust falls while you harvest"));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey(), PersistentDataType.STRING, "tier");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack shinyButton(SolRNGPlugin plugin, PlayerData data,
                                         PetType type, PetInstance owned) {
        PetUpgrades upgrades = plugin.getPetManager().upgrades();
        long cost = upgrades.shinyCost();
        boolean canPay = data.getCosmicDust() >= cost;
        boolean hasShiny = plugin.getRarityManager().foundIn(data, type.rarity(), true) > 0;

        ItemStack item = new ItemStack(Material.GLOW_INK_SAC);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Make it shiny"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "A shiny pet carries the spark"));
        lore.add(Lore.line(ChatColor.GRAY, "and pays more on top of"));
        lore.add(Lore.line(ChatColor.GRAY, "everything else."));
        lore.add("");
        if (owned.shiny()) {
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Bonus",
                    "+" + Math.round(upgrades.shinyBonus() * 100.0) + "%"));
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Already shiny");
        } else {
            lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Adds",
                    "+" + Math.round(upgrades.shinyBonus() * 100.0) + "%"));
            lore.add(Lore.stat(ChatColor.AQUA, "Cost", Currency.COSMIC_DUST.price(cost, canPay)));
            lore.add(Lore.requirement("Shiny " + type.rarity().displayName() + " found",
                    hasShiny ? "1" : "0", "1", hasShiny));
            lore.add("");
            if (!hasShiny) {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
                lore.add(Lore.line(ChatColor.GRAY, "Find a shiny "
                        + type.rarity().displayName() + " first"));
            } else if (canPay) {
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade");
            } else {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Cosmic Dust");
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
