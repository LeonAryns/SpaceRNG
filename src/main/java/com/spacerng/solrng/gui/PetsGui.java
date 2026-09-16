package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.pet.PetManager;
import com.spacerng.solrng.pet.PetType;
import com.spacerng.solrng.player.PlayerData;
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
 * /pets: the three slots on top, every pet underneath.
 *
 * A pet rides in one of the aura's slots and pays a percentage on one
 * stat, so the menu only ever has to answer two questions: what is in my
 * slots, and what would this one give me instead. The slots are drawn as
 * their own row because a player swapping pets is thinking about the
 * three together, not about one at a time.
 */
public class PetsGui {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int[] SLOT_SLOTS = {11, 13, 15};
    private static final int FIRST_PET = 27;

    public static NamespacedKey petKey() {
        return SolRNGPlugin.key("solrng_pet");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PetsHolder holder = new PetsHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Pets");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PetManager pets = plugin.getPetManager();

        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || (i >= 18 && i < 27) ? rail : filler);

        inv.setItem(INFO_SLOT, infoIcon(plugin, player, data));

        List<PetType> equipped = pets.equipped(data);
        for (int slot = 0; slot < PetManager.SLOTS; slot++) {
            inv.setItem(SLOT_SLOTS[slot], slotIcon(slot < equipped.size() ? equipped.get(slot) : null, slot));
        }

        int index = 0;
        for (PetType pet : pets.getTypes().values()) {
            int slot = FIRST_PET + index;
            if (slot >= SIZE) break;
            inv.setItem(slot, petIcon(plugin, data, pet));
            index++;
        }
        return inv;
    }

    private static ItemStack infoIcon(SolRNGPlugin plugin, Player player, PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Your pets"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Pets circle you in your aura's"));
        lore.add(Lore.line(ChatColor.GRAY, "three slots and each one pays a"));
        lore.add(Lore.line(ChatColor.GRAY, "percentage on a single stat."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Found",
                data.getOwnedPets().size() + " / " + plugin.getPetManager().getTypes().size()));
        lore.add(Lore.stat(ChatColor.AQUA, "Worn",
                data.getEquippedPets().size() + " / " + PetManager.SLOTS));
        lore.add("");
        lore.add(Lore.footnote("Your boosts are listed in /stats."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack slotIcon(PetType pet, int slot) {
        if (pet == null) {
            ItemStack item = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "Slot " + (slot + 1)));
            meta.setLore(List.of(
                    Lore.line(ChatColor.GRAY, "Empty."),
                    "",
                    ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Click a pet below to wear it"));
            item.setItemMeta(meta);
            return item;
        }
        ItemStack item = new ItemStack(pet.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient(pet.display(), true, pet.stops()));
        meta.setLore(List.of(
                Lore.stat(ChatColor.AQUA, "Slot", String.valueOf(slot + 1)),
                Lore.stat(ChatColor.GREEN, "Giving", pet.boostText()),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to take it off"));
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(petKey(), PersistentDataType.STRING, pet.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack petIcon(SolRNGPlugin plugin, PlayerData data, PetType pet) {
        boolean owned = data.ownsPet(pet.id());
        boolean worn = data.getEquippedPets().contains(pet.id());

        ItemStack item = new ItemStack(owned ? pet.icon() : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(owned
                ? Lore.gradient(pet.display(), true, pet.stops())
                : ChatColor.DARK_GRAY + "???");

        List<String> lore = new ArrayList<>();
        if (owned) {
            if (!pet.blurb().isBlank()) lore.add(Lore.line(ChatColor.GRAY, pet.blurb()));
            lore.add(Lore.stat(ChatColor.GREEN, "Gives", pet.boostText()));
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity", pet.rarity().displayName()));
            lore.add("");
            if (worn) {
                lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Worn");
                lore.add(Lore.footnote("Click to take it off."));
            } else if (data.getEquippedPets().size() >= PetManager.SLOTS) {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Slots full");
                lore.add(Lore.line(ChatColor.GRAY, "Take one off above first."));
            } else {
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to wear");
            }
        } else {
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity", pet.rarity().displayName()));
            lore.add(Lore.stat(ChatColor.AQUA, "Gives", pet.boostText()));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "How these are found is not"));
            lore.add(Lore.line(ChatColor.GRAY, "decided yet. Soon."));
        }
        meta.setLore(lore);
        if (worn) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        if (owned) meta.getPersistentDataContainer().set(petKey(), PersistentDataType.STRING, pet.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The pet id on a clicked item, or null when it carries none. */
    public static String clickedPet(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(petKey(), PersistentDataType.STRING);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
