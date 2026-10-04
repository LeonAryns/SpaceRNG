package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.pet.PetInstance;
import com.spacerng.solrng.pet.PetManager;
import com.spacerng.solrng.pet.PetType;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * How a pet reads on a tooltip, in the shape the perk cards use (V340).
 *
 * Leon asked for these to read like the perk index and the drop index,
 * which are the two best tooltips in the plugin, and they are good for
 * the same three reasons:
 *
 *   1. a dark grey subtitle under the name saying WHAT the thing is,
 *      before anything about numbers
 *   2. one short sentence of plain English saying what it does to you,
 *      with the stat it touches in white
 *   3. every number as a {@link Lore#stat} line underneath, so the block
 *      scans as a column of coloured marks rather than as prose
 *
 * The rules a player has to learn about pets are the same for all
 * forty-two of them: a pet multiplies ONE stat, its rarity decides how
 * big that multiplier starts, and every rarity and tier step above that
 * multiplies it again. So the card says those once, in the same words
 * everywhere, instead of each card explaining itself differently.
 */
public final class PetLore {

    private PetLore() {
    }

    /** "1.45x" as every pet card and the index write it. */
    static String multi(double value) {
        return String.format("%.2f", value) + "x";
    }

    /**
     * One pet, owned or not.
     *
     * The unowned half is deliberately not a blank: it says what the pet
     * would be worth if it hatched, because that is the only reason to
     * look at a pet you do not have.
     */
    public static ItemStack card(SolRNGPlugin plugin, PlayerData data, PetType pet, boolean bedrock) {
        PetManager pets = plugin.getPetManager();
        PetInstance owned = data.getPet(pet.id());
        boolean worn = data.getEquippedPets().contains(pet.id());
        double fresh = 1.0 + pet.bonus();
        double grown = 1.0 + pet.bonus() * pets.upgrades().topMultiplier();

        ItemStack item = new ItemStack(owned != null ? pet.icon() : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(owned != null
                ? PetsGui.name(pet, owned)
                : Lore.title(ChatColor.DARK_GRAY, "???"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + pet.rarity().displayName() + " pet");
        lore.add("");
        if (!pet.blurb().isBlank()) {
            lore.add(Lore.line(ChatColor.GRAY, pet.blurb()));
        }
        lore.add(ChatColor.GRAY + "Multiplies your " + ChatColor.WHITE + pet.statName()
                + ChatColor.GRAY + " while worn,");
        lore.add(ChatColor.GRAY + "and nothing else.");
        lore.add("");

        if (owned != null) {
            double multiplier = pets.upgrades().multiplier(owned);
            lore.add(Lore.section(ChatColor.GOLD, "Your boost"));
            lore.add(Lore.stat(ChatColor.GREEN, pet.statName(), pet.multiText(multiplier)));
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity", owned.rarity() + " / " + pets.upgrades().maxRarity()));
            lore.add(Lore.stat(ChatColor.AQUA, "Tier", owned.tier() + " / " + pets.upgrades().maxTier()));
            if (owned.shiny()) {
                lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Shiny", Lore.SPARK + " worth more again"));
            }
            // V324: duplicates. The spare copies are the ones a shift
            // click throws away; the copy you wear is never one of them.
            if (owned.copies() > 1) {
                lore.add(Lore.stat(ChatColor.AQUA, "Copies", owned.copies() + " " + ChatColor.DARK_GRAY
                        + "(" + owned.spare() + " spare)"));
            }
            lore.add(Lore.stat(ChatColor.DARK_GRAY, "Fully grown", multi(grown)));
            lore.add("");
            if (bedrock) {
                // One click opens the pet on Bedrock, where wearing it is a
                // button of its own (V223).
                if (worn) lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Worn");
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open");
                lore.add(Lore.footnote("Wear it and upgrade it in there."));
            } else if (worn) {
                lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Worn");
                lore.add(Lore.line(ChatColor.GRAY, "Left click to take it off"));
            } else if (data.getEquippedPets().size() >= pets.slots(data)) {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Slots full");
                lore.add(Lore.line(ChatColor.GRAY, "Take one off on the pets screen"));
            } else {
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Left click to wear");
            }
            if (!bedrock) lore.add(Lore.footnote("Right click to grow it."));
            if (!bedrock && owned.spare() > 0) {
                lore.add(Lore.footnote("Shift click throws one copy away."));
            }
        } else {
            lore.add(Lore.stat(ChatColor.AQUA, "Fresh", multi(fresh)));
            lore.add(Lore.stat(ChatColor.AQUA, "Fully grown", multi(grown)));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not hatched yet");
            lore.add(Lore.line(ChatColor.GRAY, "Hatch eggs on the pets screen"));
        }

        meta.setLore(lore);
        if (worn) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        if (owned != null) {
            meta.getPersistentDataContainer().set(PetsGui.petKey(), PersistentDataType.STRING, pet.id());
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One rarity in the index: what every pet of it is worth, which six
     * carry it, and whether their copies are being thrown away.
     *
     * Autotrash is per rarity rather than per pet, because with six pets
     * in every rarity "I never want Commons" is the thing anybody
     * actually means. It still writes the same per pet set underneath, so
     * a pet switched on its own stays switched.
     */
    public static ItemStack rarityCard(SolRNGPlugin plugin, PlayerData data, Rarity rarity) {
        PetManager pets = plugin.getPetManager();
        double bonus = pets.upgrades().bonusFor(rarity);
        double top = pets.upgrades().topMultiplier();

        List<PetType> inRarity = new ArrayList<>();
        for (PetType pet : pets.getTypes().values()) {
            if (pet.rarity() == rarity) inRarity.add(pet);
        }
        int found = 0;
        int trashed = 0;
        for (PetType pet : inRarity) {
            if (data.getPet(pet.id()) != null) found++;
            if (data.isPetAutoTrash(pet.id())) trashed++;
        }
        boolean allTrashed = !inRarity.isEmpty() && trashed == inRarity.size();

        ItemStack item = new ItemStack(inRarity.isEmpty() ? Material.STONE_BUTTON : inRarity.get(0).icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getRarityManager().styleBold(rarity, rarity.displayName() + " pets"));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "" + inRarity.size() + " pets, one stat each");
        lore.add("");
        lore.add(ChatColor.GRAY + "Every " + rarity.displayName() + " pet multiplies");
        lore.add(ChatColor.GRAY + "its own stat, and a rarer pet");
        lore.add(ChatColor.GRAY + "multiplies it harder.");
        lore.add("");
        lore.add(Lore.stat(ChatColor.GREEN, "Fresh", multi(1.0 + bonus)));
        lore.add(Lore.stat(ChatColor.GREEN, "Fully grown", multi(1.0 + bonus * top)));
        lore.add("");
        for (PetType pet : inRarity) {
            PetInstance owned = data.getPet(pet.id());
            lore.add(Lore.stat(owned != null ? ChatColor.GREEN : ChatColor.DARK_GRAY,
                    pet.statName(), ChatColor.stripColor(pet.display())
                            + (owned != null ? "  " + ChatColor.GREEN + Lore.TICK : "")));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Found", found + " / " + inRarity.size()));
        lore.add(allTrashed
                ? ChatColor.RED + "" + ChatColor.BOLD + "Autotrash on"
                : trashed > 0
                        ? ChatColor.RED + "" + ChatColor.BOLD + "Autotrash on for " + trashed
                        : ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Autotrash off");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD
                + (allTrashed ? "Click to keep them again" : "Click to throw spare copies away"));

        meta.setLore(lore);
        if (found >= inRarity.size() && !inRarity.isEmpty()) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(PetsGui.rarityKey(), PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        return item;
    }
}
