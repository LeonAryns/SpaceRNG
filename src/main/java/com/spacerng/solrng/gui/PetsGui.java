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
 *   index    every pet there is, the whole rarity ladder for the stat it
 *            boosts, and the autotrash switch, on the Perk Index's shape
 *
 * A pet rides in one of the aura's slots and multiplies one stat, so the
 * menu answers two questions at this level: what is in my slots, and what
 * would this one give me instead. Everything about
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
                MenuStyle.title("Pets", "#80DEEA", "#26C6DA"));
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

        var eggs = pets.upgrades().eggs();
        int[] eggSlots = eggs.size() == 1 ? new int[]{FORGE_SLOT}
                : eggs.size() == 2 ? new int[]{30, 32} : new int[]{29, 31, 33};
        for (int i = 0; i < eggs.size() && i < eggSlots.length; i++) {
            inv.setItem(eggSlots[i], eggIcon(plugin, data, eggs.get(i), i + 1));
        }
        // The row under the eggs fills toward the next egg you can open
        // but cannot pay for yet.
        long cost = pets.upgrades().makeCost();
        for (var egg : eggs) {
            if (data.getPrestige() < egg.minPrestige()) break;
            cost = egg.cost();
            if (data.getCosmicDust() < egg.cost()) break;
        }
        for (int i = 0; i < PROGRESS_SLOTS.length; i++) {
            inv.setItem(PROGRESS_SLOTS[i], progressBlock(data.getCosmicDust(), cost, i));
        }
        MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
        return inv;
    }

    /** The pets you own, to wear and to upgrade. */
    public static Inventory storage(SolRNGPlugin plugin, Player player) {
        PetsHolder holder = new PetsHolder(PetsHolder.View.STORAGE);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                MenuStyle.title("Pet Storage", "#80DEEA", "#26C6DA"));
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
                    ChatColor.DARK_GRAY + "Nothing hatched",
                    "",
                    Lore.line(ChatColor.GRAY, "Cosmic Dust drops while you roll."),
                    Lore.line(ChatColor.GRAY, "Spend it on an egg on the pets"),
                    Lore.line(ChatColor.GRAY, "screen for your first pet.")));
            none.setItemMeta(meta);
            inv.setItem(31, none);
        }
        MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
        return inv;
    }

    /** Where the seven rarity cards sit, one row, centred. */
    private static final int[] RARITY_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    /**
     * The index, by rarity (V327).
     *
     * Leon asked for the rarities and nothing else: one card per rarity
     * saying what a pet of that rarity multiplies by, rather than
     * forty-two cards split by which stat they happen to boost. The six
     * pets inside a rarity are listed on the card, so the collection is
     * still readable, but the number a player came for is the rarity's.
     */
    public static Inventory index(SolRNGPlugin plugin, Player player) {
        PetsHolder holder = new PetsHolder(PetsHolder.View.INDEX);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                MenuStyle.title("Pet Index", "#80DEEA", "#26C6DA"));
        holder.setInventory(inv);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        fillSubScreen(inv);

        int index = 0;
        for (com.spacerng.solrng.rarity.Rarity rarity : com.spacerng.solrng.rarity.Rarity.values()) {
            if (!plugin.getPetManager().hasRarity(rarity)) continue;
            if (index >= RARITY_SLOTS.length) break;
            inv.setItem(RARITY_SLOTS[index++], rarityIcon(plugin, data, rarity));
        }
        MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
        return inv;
    }

    /** The rarity a clicked index card carries, or null. */
    public static String clickedRarity(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(rarityKey(), PersistentDataType.STRING);
    }

    public static NamespacedKey rarityKey() {
        return SolRNGPlugin.key("solrng_pet_rarity");
    }

    /** One rarity card, written in {@link PetLore}'s shape (V340). */
    private static ItemStack rarityIcon(SolRNGPlugin plugin, PlayerData data,
                                        com.spacerng.solrng.rarity.Rarity rarity) {
        return PetLore.rarityCard(plugin, data, rarity);
    }

    private static void fillSubScreen(Inventory inv) {
        ItemStack rail = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 ? rail : filler);
        ItemStack back = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Pets"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Your slots and the eggs."),
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
                ChatColor.DARK_GRAY + "The pets you have hatched",
                "",
                Lore.line(ChatColor.GRAY, "Left click one to wear it,"),
                Lore.line(ChatColor.GRAY, "right click it to grow it."),
                "",
                Lore.stat(ChatColor.AQUA, "Owned", data.getOwnedPets().size() + " / " + pets.getTypes().size()),
                Lore.stat(ChatColor.AQUA, "Worn",
                        Math.min(data.getEquippedPets().size(), pets.slots(data)) + " / " + pets.slots(data)),
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
                ChatColor.DARK_GRAY + "Every pet there is",
                "",
                Lore.line(ChatColor.GRAY, "One card per rarity: what a pet"),
                Lore.line(ChatColor.GRAY, "of it multiplies by, which pets"),
                Lore.line(ChatColor.GRAY, "carry it, and autotrash."),
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

    private static ItemStack infoIcon(SolRNGPlugin plugin, Player player, PlayerData data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Your pets"));

        PetManager pets = plugin.getPetManager();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "How pets work");
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, "A worn pet stands behind you and"));
        lore.add(Lore.line(ChatColor.GRAY, "multiplies one stat. Its rarity"));
        lore.add(Lore.line(ChatColor.GRAY, "sets how big that starts, and"));
        lore.add(Lore.line(ChatColor.GRAY, "every rarity and tier step above"));
        lore.add(Lore.line(ChatColor.GRAY, "multiplies it again."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Owned",
                data.getOwnedPets().size() + " / " + pets.getTypes().size()));
        lore.add(Lore.stat(ChatColor.AQUA, "Worn",
                Math.min(data.getEquippedPets().size(), pets.slots(data)) + " / " + pets.slots(data)));
        lore.add("");
        // V313: eggs are bought with Cosmic Dust and every upgrade is
        // paid in Gems, so the panel has to name all three or a player
        // reads the dust and wonders why the upgrade is still red.
        lore.add(Lore.section(ChatColor.YELLOW, "What pets cost"));
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, Currency.COSMIC_DUST.amount(data.getCosmicDust())));
        lore.add(Lore.pipe(ChatColor.AQUA, Currency.GEMS.amount(data.getShards())));
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
    /**
     * One egg: Cosmic Dust turned into a pet. Since V226 there are three,
     * and a dearer egg buys better odds on the rare pets rather than a
     * different pool. The odds on the tooltip are the live ones for this
     * player, with maxed pets already taken out.
     */
    private static ItemStack eggIcon(SolRNGPlugin plugin, PlayerData data,
                                     com.spacerng.solrng.pet.PetEgg egg, int tier) {
        PetManager pets = plugin.getPetManager();
        long cost = egg.cost();
        boolean canPay = data.getCosmicDust() >= cost;
        boolean prestigeOk = data.getPrestige() >= egg.minPrestige();
        double chance = plugin.getDustManager().cosmicChance(data);

        ItemStack item = new ItemStack(egg.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient(egg.display(), true, egg.stops()));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Tier " + tier + " egg");
        lore.add("");
        lore.add(Lore.line(ChatColor.GRAY, "Hatches one pet. Its rarity is"));
        lore.add(Lore.line(ChatColor.GRAY, "rolled first and decides the"));
        lore.add(Lore.line(ChatColor.GRAY, "multiplier, its stat after."));
        lore.add(Lore.line(ChatColor.GRAY, "A dearer egg is the same pets at"));
        lore.add(Lore.line(ChatColor.GRAY, "better odds, never other pets."));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Chance per hatch"));
        for (var entry : pets.odds(data, egg).entrySet()) {
            lore.add(Lore.stat(ChatColor.AQUA, entry.getKey().displayName(), percent(entry.getValue())));
        }
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Price", Currency.COSMIC_DUST.price(cost, canPay)));
        lore.add("");
        if (!prestigeOk) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Reach Prestige " + egg.minPrestige() + " in /prestige"));
        } else if (chance <= 0.0 && data.getCosmicDust() < cost) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Unlock Cosmic Dust in /skilltree"));
        } else if (canPay) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to hatch");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough Cosmic Dust");
        }
        meta.setLore(lore);
        if (canPay && prestigeOk) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(forgeKey(), PersistentDataType.STRING, egg.id());
        item.setItemMeta(meta);
        return item;
    }

    private static String percent(double chance) {
        double value = chance * 100.0;
        return (value >= 10 ? String.format("%.1f", value) : value >= 1 ? String.format("%.2f", value)
                : String.format("%.3f", value)) + "%";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.format("%,d", (long) value) : String.valueOf(value);
    }

    private static ItemStack slotIcon(SolRNGPlugin plugin, PlayerData data,
                                      PetType pet, int slot, boolean open) {
        if (!open) {
            ItemStack item = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "Slot " + (slot + 1)));
            meta.setLore(List.of(
                    ChatColor.DARK_GRAY + "A pet you wear",
                    "",
                    Lore.line(ChatColor.GRAY, "Every open slot is one more pet"),
                    Lore.line(ChatColor.GRAY, "multiplying a stat at once."),
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
                    ChatColor.DARK_GRAY + "Open, and empty",
                    "",
                    Lore.line(ChatColor.GRAY, "A pet in here multiplies one of"),
                    Lore.line(ChatColor.GRAY, "your stats for as long as it"),
                    Lore.line(ChatColor.GRAY, "stays worn."),
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
        PetInstance instance = data.getPet(pet.id());
        meta.setLore(List.of(
                ChatColor.DARK_GRAY + pet.rarity().displayName() + " pet, slot " + (slot + 1),
                "",
                Lore.line(ChatColor.GRAY, "Worn, so it is multiplying your"),
                Lore.line(ChatColor.GRAY, pet.statName() + " right now."),
                "",
                Lore.stat(ChatColor.GREEN, pet.statName(), pet.multiText(multiplier)),
                Lore.stat(ChatColor.AQUA, "Rarity",
                        instance.rarity() + " / " + plugin.getPetManager().upgrades().maxRarity()),
                Lore.stat(ChatColor.AQUA, "Tier",
                        instance.tier() + " / " + plugin.getPetManager().upgrades().maxTier()),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to take it off"));
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(petKey(), PersistentDataType.STRING, pet.id());
        item.setItemMeta(meta);
        return item;
    }

    /** One pet card, written in {@link PetLore}'s shape (V340). */
    private static ItemStack petIcon(SolRNGPlugin plugin, PlayerData data, PetType pet, boolean bedrock) {
        return PetLore.card(plugin, data, pet, bedrock);
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

    /** The egg id on a clicked egg, or null when it is not one. */
    public static String clickedEgg(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(forgeKey(), PersistentDataType.STRING);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
