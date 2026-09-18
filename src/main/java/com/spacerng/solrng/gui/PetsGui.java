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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * /pets: the slots on top, the forge next to them, every pet underneath.
 *
 * A pet rides in one of the aura's slots and pays a percentage on one
 * stat, so the menu answers two questions at this level: what is in my
 * slots, and what would this one give me instead. Everything about
 * growing a pet lives one click deeper in {@link PetUpgradeGui}, because
 * wearing and upgrading are different jobs and a tooltip that tries to
 * do both stops being readable.
 *
 * A left click wears a pet, a right click opens it for upgrading.
 */
public class PetsGui {

    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int FORGE_SLOT = 8;
    private static final int[] SLOT_SLOTS = {11, 13, 15};
    private static final int FIRST_PET = 27;

    public static NamespacedKey petKey() {
        return SolRNGPlugin.key("solrng_pet");
    }

    public static NamespacedKey forgeKey() {
        return SolRNGPlugin.key("solrng_pet_forge");
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
        inv.setItem(FORGE_SLOT, forgeIcon(plugin, data));

        List<PetType> equipped = pets.equipped(data);
        int open = pets.slots(data);
        for (int slot = 0; slot < PetManager.MAX_SLOTS; slot++) {
            inv.setItem(SLOT_SLOTS[slot], slotIcon(plugin, data,
                    slot < equipped.size() ? equipped.get(slot) : null, slot, slot < open));
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

        PetManager pets = plugin.getPetManager();
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Pets circle you in your aura's"));
        lore.add(Lore.line(ChatColor.GRAY, "slots and each one pays a"));
        lore.add(Lore.line(ChatColor.GRAY, "percentage on a single stat."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Owned",
                data.getOwnedPets().size() + " / " + pets.getTypes().size()));
        lore.add(Lore.stat(ChatColor.AQUA, "Worn",
                Math.min(data.getEquippedPets().size(), pets.slots(data)) + " / " + pets.slots(data)));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Dust"));
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, Currency.COSMIC_DUST.amount(data.getCosmicDust())));
        lore.add(Lore.pipe(ChatColor.GREEN, Currency.FARM_DUST.amount(data.getFarmDust())));
        lore.add("");
        if (pets.slots(data) < PetManager.MAX_SLOTS) {
            lore.add(Lore.footnote("More slots come from /skilltree."));
        } else {
            lore.add(Lore.footnote("Your boosts are listed in /stats."));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The forge: ten Cosmic Dust turned into a pet. It sits in the top row
     * next to the slots rather than in the grid below, because it is the
     * one thing here that makes something new.
     */
    private static ItemStack forgeIcon(SolRNGPlugin plugin, PlayerData data) {
        PetManager pets = plugin.getPetManager();
        PetUpgrades upgrades = pets.upgrades();
        long cost = upgrades.makeCost();
        boolean canPay = data.getCosmicDust() >= cost;
        double chance = plugin.getDustManager().cosmicChance(data);

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Make a pet"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Cosmic Dust falls rarely while"));
        lore.add(Lore.line(ChatColor.GRAY, "you roll. Spend it here for a"));
        lore.add(Lore.line(ChatColor.GRAY, "pet you do not have yet."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Cost", Currency.COSMIC_DUST.price(cost, canPay)));
        lore.add(Lore.stat(ChatColor.AQUA, "You hold", Currency.COSMIC_DUST.amount(data.getCosmicDust())));
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, "A pet you already own gains a"));
        lore.add(Lore.line(ChatColor.GRAY, "rarity instead, so the dust is"));
        lore.add(Lore.line(ChatColor.GRAY, "never wasted."));
        lore.add("");
        if (chance <= 0.0) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Unlock Cosmic Dust in /skilltree"));
        } else if (canPay) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to make one");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Cosmic Dust");
        }
        meta.setLore(lore);
        if (canPay && chance > 0.0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(forgeKey(), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack slotIcon(SolRNGPlugin plugin, PlayerData data,
                                      PetType pet, int slot, boolean open) {
        if (!open) {
            ItemStack item = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "Slot " + (slot + 1)));
            meta.setLore(List.of(
                    Lore.line(ChatColor.GRAY, "Not open yet."),
                    "",
                    ChatColor.RED + "" + ChatColor.BOLD + "Locked",
                    Lore.line(ChatColor.GRAY, "Buy Pet Slots in /skilltree")));
            item.setItemMeta(meta);
            return item;
        }
        if (pet == null) {
            ItemStack item = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "Slot " + (slot + 1)));
            meta.setLore(List.of(
                    Lore.line(ChatColor.GRAY, "Empty."),
                    "",
                    Lore.footnote("Click a pet below to wear it.")));
            item.setItemMeta(meta);
            return item;
        }

        PetInstance owned = data.getPet(pet.id());
        double multiplier = plugin.getPetManager().upgrades().multiplier(owned);
        ItemStack item = new ItemStack(pet.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name(pet, owned));
        meta.setLore(List.of(
                Lore.stat(ChatColor.AQUA, "Slot", String.valueOf(slot + 1)),
                Lore.stat(ChatColor.GREEN, "Giving", pet.boostText(multiplier)),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to take it off"));
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(petKey(), PersistentDataType.STRING, pet.id());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack petIcon(SolRNGPlugin plugin, PlayerData data, PetType pet) {
        PetManager pets = plugin.getPetManager();
        PetInstance owned = data.getPet(pet.id());
        boolean worn = data.getEquippedPets().contains(pet.id());

        ItemStack item = new ItemStack(owned != null ? pet.icon() : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(owned != null ? name(pet, owned) : ChatColor.DARK_GRAY + "???");

        List<String> lore = new ArrayList<>();
        if (owned != null) {
            double multiplier = pets.upgrades().multiplier(owned);
            if (!pet.blurb().isBlank()) lore.add(Lore.line(ChatColor.GRAY, pet.blurb()));
            lore.add(Lore.stat(ChatColor.GREEN, "Gives", pet.boostText(multiplier)));
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity",
                    owned.rarity() + " / " + pets.upgrades().maxRarity()));
            lore.add(Lore.stat(ChatColor.AQUA, "Tier",
                    owned.tier() + " / " + pets.upgrades().maxTier()));
            if (owned.shiny()) lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Shiny", Lore.SPARK));
            lore.add("");
            if (worn) {
                lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Worn");
                lore.add(Lore.line(ChatColor.GRAY, "Left click to take it off"));
            } else if (data.getEquippedPets().size() >= pets.slots(data)) {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Slots full");
                lore.add(Lore.line(ChatColor.GRAY, "Take one off above first"));
            } else {
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Left click to wear");
            }
            lore.add(Lore.footnote("Right click to upgrade it."));
        } else {
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity", pet.rarity().displayName()));
            lore.add(Lore.stat(ChatColor.AQUA, "Gives", pet.boostText()));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Make pets from Cosmic Dust"));
            lore.add(Lore.line(ChatColor.GRAY, "with the star above"));
        }
        meta.setLore(lore);
        if (worn) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        if (owned != null) meta.getPersistentDataContainer().set(petKey(), PersistentDataType.STRING, pet.id());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * A pet's name, with a shiny one marked by the spark rather than by a
     * colour. The gradient is the pet's own and has to stay recognisable.
     */
    static String name(PetType pet, PetInstance owned) {
        String text = owned != null && owned.shiny()
                ? Lore.SPARK + " " + pet.display()
                : pet.display();
        return Lore.gradient(text, true, pet.stops());
    }

    /** The pet id on a clicked item, or null when it carries none. */
    public static String clickedPet(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(petKey(), PersistentDataType.STRING);
    }

    /** True when the clicked item is the forge star. */
    public static boolean clickedForge(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(forgeKey(), PersistentDataType.INTEGER);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
