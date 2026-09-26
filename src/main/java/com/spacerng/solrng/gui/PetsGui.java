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
 * /pets, three screens since V215, laid out the way Leon asked:
 *
 *   main     storage top left, the index beside it, your head top right,
 *            the three slots, and "Make a pet" in the middle with five
 *            blocks under it that turn green as the Cosmic Dust for the
 *            next pet comes in, a fifth of the price each
 *   storage  the pets you own: left click wears one, right click opens it
 *            for upgrading
 *   index    every pet there is and what it gives, fresh and at its best,
 *            like the Perk Index
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
    private static final int STORAGE_SLOT = 0;
    private static final int INDEX_SLOT = 1;
    private static final int INFO_SLOT = 8;
    private static final int[] SLOT_SLOTS = {11, 13, 15};
    private static final int FORGE_SLOT = 31;
    private static final int[] PROGRESS_SLOTS = {38, 39, 40, 41, 42};
    // Storage and index: the back arrow top left, pets from the second row.
    private static final int BACK_SLOT = 0;
    private static final int FIRST_PET = 9;

    public static boolean isStorageButton(int slot) { return slot == STORAGE_SLOT; }
    public static boolean isIndexButton(int slot) { return slot == INDEX_SLOT; }
    public static boolean isBack(int slot) { return slot == BACK_SLOT; }

    public static NamespacedKey petKey() {
        return SolRNGPlugin.key("solrng_pet");
    }

    public static NamespacedKey forgeKey() {
        return SolRNGPlugin.key("solrng_pet_forge");
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PetsHolder holder = new PetsHolder(PetsHolder.View.MAIN);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Pets");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PetManager pets = plugin.getPetManager();

        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 || (i >= 18 && i < 27) ? rail : filler);

        inv.setItem(STORAGE_SLOT, storageIcon(plugin, data));
        inv.setItem(INDEX_SLOT, indexButton(plugin, data));
        inv.setItem(INFO_SLOT, infoIcon(plugin, player, data));

        List<PetType> equipped = pets.equipped(data);
        int open = pets.slots(data);
        for (int slot = 0; slot < PetManager.MAX_SLOTS; slot++) {
            inv.setItem(SLOT_SLOTS[slot], slotIcon(plugin, data,
                    slot < equipped.size() ? equipped.get(slot) : null, slot, slot < open));
        }

        inv.setItem(FORGE_SLOT, forgeIcon(plugin, data));
        long cost = pets.upgrades().makeCost();
        for (int i = 0; i < PROGRESS_SLOTS.length; i++) {
            inv.setItem(PROGRESS_SLOTS[i], progressBlock(data.getCosmicDust(), cost, i));
        }
        return inv;
    }

    /** The pets you own, to wear and to upgrade. */
    public static Inventory storage(SolRNGPlugin plugin, Player player) {
        PetsHolder holder = new PetsHolder(PetsHolder.View.STORAGE);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Pet Storage");
        holder.setInventory(inv);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PetManager pets = plugin.getPetManager();
        fillSubScreen(inv);

        int slot = FIRST_PET;
        for (PetType pet : pets.getTypes().values()) {
            if (slot >= SIZE) break;
            if (data.getPet(pet.id()) == null) continue;
            inv.setItem(slot++, petIcon(plugin, data, pet, com.spacerng.solrng.platform.Bedrock.is(player)));
        }
        if (slot == FIRST_PET) {
            ItemStack none = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = none.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "No pets yet"));
            meta.setLore(List.of(
                    Lore.line(ChatColor.GRAY, "Make your first one from"),
                    Lore.line(ChatColor.GRAY, "Cosmic Dust on the pets screen.")));
            none.setItemMeta(meta);
            inv.setItem(31, none);
        }
        return inv;
    }

    /** Every pet there is and what it gives, found or not. */
    public static Inventory index(SolRNGPlugin plugin, Player player) {
        PetsHolder holder = new PetsHolder(PetsHolder.View.INDEX);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Pet Index");
        holder.setInventory(inv);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        fillSubScreen(inv);

        int slot = FIRST_PET;
        for (PetType pet : plugin.getPetManager().getTypes().values()) {
            if (slot >= SIZE) break;
            inv.setItem(slot++, indexIcon(plugin, data, pet));
        }
        return inv;
    }

    private static void fillSubScreen(Inventory inv) {
        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 ? rail : filler);
        ItemStack back = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Pets"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Your slots and the forge."),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to go back"));
        back.setItemMeta(meta);
        inv.setItem(BACK_SLOT, back);
    }

    private static ItemStack storageIcon(SolRNGPlugin plugin, PlayerData data) {
        PetManager pets = plugin.getPetManager();
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Pet Storage"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every pet you own. Wear one or"),
                Lore.line(ChatColor.GRAY, "open it to upgrade."),
                "",
                Lore.stat(ChatColor.AQUA, "Owned", data.getOwnedPets().size() + " / " + pets.getTypes().size()),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack indexButton(SolRNGPlugin plugin, PlayerData data) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Pet Index"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every pet there is and the"),
                Lore.line(ChatColor.GRAY, "boost each one gives."),
                "",
                Lore.stat(ChatColor.AQUA, "Found",
                        data.getOwnedPets().size() + " / " + plugin.getPetManager().getTypes().size()),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * One fifth of the way to the next pet. Green once that fifth of the
     * price is held, red until then, so the row fills left to right as the
     * dust comes in. Blocks for now; Leon means to swap in heads.
     */
    private static ItemStack progressBlock(long dust, long cost, int index) {
        long step = Math.max(1L, (long) Math.ceil(cost / 5.0));
        long needed = Math.min(cost, step * (index + 1));
        boolean filled = dust >= needed;
        ItemStack item = new ItemStack(filled ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(filled ? ChatColor.GREEN : ChatColor.RED, (index + 1) + " / 5"));
        meta.setLore(List.of(
                Lore.stat(filled ? ChatColor.GREEN : ChatColor.RED, "Needs",
                        Currency.COSMIC_DUST.amount(needed)),
                Lore.stat(ChatColor.AQUA, "You hold", Currency.COSMIC_DUST.amount(Math.min(dust, cost)))));
        item.setItemMeta(meta);
        return item;
    }

    /** A pet as the index shows it: what it gives new, and at its very best. */
    private static ItemStack indexIcon(SolRNGPlugin plugin, PlayerData data, PetType pet) {
        PetManager pets = plugin.getPetManager();
        PetInstance owned = data.getPet(pet.id());
        ItemStack item = new ItemStack(pet.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name(pet, owned));
        List<String> lore = new ArrayList<>();
        if (!pet.blurb().isBlank()) {
            lore.add(Lore.line(ChatColor.GRAY, pet.blurb()));
            lore.add("");
        }
        lore.add(Lore.section(ChatColor.YELLOW, "Boost"));
        lore.add(Lore.stat(ChatColor.AQUA, "New", pet.boostText()));
        lore.add(Lore.stat(ChatColor.AQUA, "At its best", pet.boostText(pets.upgrades().topMultiplier())));
        if (owned != null) {
            lore.add(Lore.stat(ChatColor.GREEN, "Yours", pet.boostText(pets.upgrades().multiplier(owned))));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Rarity", pet.rarity().displayName()));
        lore.add("");
        if (owned != null) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Found");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not found yet");
            lore.add(Lore.line(ChatColor.GRAY, "Make pets from Cosmic Dust"));
        }
        meta.setLore(lore);
        if (owned != null) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
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
     * The forge: Cosmic Dust turned into a pet, a hundred since V215. It
     * sits in the middle of the screen with the dust row under it, because
     * it is the one thing here that makes something new.
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
                    Lore.footnote("Wear one from Pet Storage.")));
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

    private static ItemStack petIcon(SolRNGPlugin plugin, PlayerData data, PetType pet, boolean bedrock) {
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
            if (!bedrock) lore.add(Lore.footnote("Right click to upgrade it."));
        } else {
            lore.add(Lore.stat(ChatColor.AQUA, "Rarity", pet.rarity().displayName()));
            lore.add(Lore.stat(ChatColor.AQUA, "Gives", pet.boostText()));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Make pets from Cosmic Dust"));
            lore.add(Lore.line(ChatColor.GRAY, "on the pets screen"));
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
