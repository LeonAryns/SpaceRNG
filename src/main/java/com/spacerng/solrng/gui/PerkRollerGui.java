package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkManager;
import com.spacerng.solrng.perk.PerkStat;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Seven roll buttons in a row, Common to Divine. Each shows what a roll
 * costs, what it can produce and how many new index entries it could
 * still give you. Below them: save rolls, the perk waiting to be saved and
 * the reroll warning. The top rail holds how perks work, the vault and
 * the perk index; the bottom rail shows what your loadout adds up to.
 */
public class PerkRollerGui {

    private static final int SIZE = 45;
    private static final int GUIDE_SLOT = 2;
    private static final int VAULT_SLOT = 4;
    private static final int INDEX_SLOT = 6;
    private static final int NOTICE_SLOT = 13;
    private static final int AUTO_SAVE_SLOT = 28;
    private static final int AMOUNT_SLOT = 29;
    private static final int BUY_SLOT = 30;
    private static final int PENDING_SLOT = 31;
    private static final int CONFIRM_SLOT = 33;
    private static final int LOADOUT_SLOT = 40;

    /** The amounts the buy button cycles through. */
    public static final int[] SAVE_AMOUNTS = {1, 10, 50, 100};

    private static final Map<Rarity, Integer> BUTTON_SLOT = new EnumMap<>(Rarity.class);
    private static final Map<Rarity, Material> BUTTON_ICON = new EnumMap<>(Rarity.class);

    static {
        BUTTON_SLOT.put(Rarity.COMMON, 19);
        BUTTON_SLOT.put(Rarity.UNCOMMON, 20);
        BUTTON_SLOT.put(Rarity.RARE, 21);
        BUTTON_SLOT.put(Rarity.EPIC, 22);
        BUTTON_SLOT.put(Rarity.LEGENDARY, 23);
        BUTTON_SLOT.put(Rarity.MYTHICAL, 24);
        BUTTON_SLOT.put(Rarity.DIVINE, 25);
        BUTTON_ICON.put(Rarity.COMMON, Material.QUARTZ);
        BUTTON_ICON.put(Rarity.UNCOMMON, Material.EMERALD);
        BUTTON_ICON.put(Rarity.RARE, Material.DIAMOND);
        BUTTON_ICON.put(Rarity.EPIC, Material.AMETHYST_CLUSTER);
        BUTTON_ICON.put(Rarity.LEGENDARY, Material.END_CRYSTAL);
        BUTTON_ICON.put(Rarity.MYTHICAL, Material.NETHERITE_INGOT);
        BUTTON_ICON.put(Rarity.DIVINE, Material.DRAGON_HEAD);
    }

    public static NamespacedKey rollTierKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_roll_tier");
    }

    public static int vaultSlot() { return VAULT_SLOT; }
    public static int indexSlot() { return INDEX_SLOT; }
    public static int pendingSlot() { return PENDING_SLOT; }
    public static int autoSaveSlot() { return AUTO_SAVE_SLOT; }
    public static int amountSlot() { return AMOUNT_SLOT; }
    public static int buySlot() { return BUY_SLOT; }
    public static int confirmSlot() { return CONFIRM_SLOT; }

    /** Saving is on and there is nothing left to save with, so rolling is paused. */
    public static boolean outOfSaves(PlayerData data) {
        return data.isPerkAutoSave() && data.getPerkSaveRolls() <= 0;
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        PerkRollerHolder holder = new PerkRollerHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Perk Roller");
        holder.setInventory(inv);

        // Magenta and purple rails top and bottom so this menu never reads
        // as any other in the plugin; a dark middle so the buttons stand out.
        ItemStack magenta = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack purple = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, i % 2 == 0 ? magenta : purple);
        for (int i = 36; i < 45; i++) inv.setItem(i, i % 2 == 0 ? purple : magenta);
        for (int i = 9; i < 36; i++) inv.setItem(i, filler);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();

        inv.setItem(GUIDE_SLOT, guideIcon(plugin));
        inv.setItem(VAULT_SLOT, vaultLinkIcon(data));
        inv.setItem(INDEX_SLOT, PerkVaultGui.indexLinkIcon(data));
        inv.setItem(LOADOUT_SLOT, PerkLore.loadoutSummary(plugin, data));
        if (outOfSaves(data)) inv.setItem(NOTICE_SLOT, outOfSavesNotice());

        inv.setItem(AUTO_SAVE_SLOT, autoSaveIcon(data));
        inv.setItem(AMOUNT_SLOT, amountIcon(data));
        inv.setItem(BUY_SLOT, buyIcon(plugin, data));
        inv.setItem(PENDING_SLOT, pendingIcon(plugin, data));
        inv.setItem(CONFIRM_SLOT, confirmIcon(plugin, data));

        for (Map.Entry<Rarity, Integer> entry : BUTTON_SLOT.entrySet()) {
            Rarity tier = entry.getKey();
            var roll = perks.getRoll(tier);
            if (roll == null) continue;
            inv.setItem(entry.getValue(), rollButton(plugin, data, tier, roll));
        }

        return inv;
    }

    private static ItemStack rollButton(SolRNGPlugin plugin, PlayerData data, Rarity tier,
                                        PerkManager.RollTier roll) {
        PerkManager perks = plugin.getPerkManager();
        Material material = BUTTON_ICON.getOrDefault(tier, Material.NETHER_STAR);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String tierName = plugin.getRarityManager().style(tier, tier.displayName() + " Roll");
        meta.setDisplayName(ChatColor.DARK_GRAY + "「 " + ChatColor.RESET
                + ChatColor.BOLD + tierName + ChatColor.RESET + ChatColor.DARK_GRAY + " 」");

        long have = data.getBankedDrops(roll.costRarity())
                + countPhysical(plugin, data, roll.costRarity());
        boolean affordable = have >= roll.costAmount();

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "Cost"));
        lore.add(Lore.line(affordable ? ChatColor.YELLOW : ChatColor.RED, roll.costAmount() + "x "
                + plugin.getRarityManager().style(roll.costRarity(), roll.costRarity().displayName())
                + ChatColor.GRAY + " drops"));
        lore.add(Lore.stat(ChatColor.DARK_GRAY, "You have", have + " (bank and inventory)"));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "The perk lands at"));
        int newForIndex = 0;
        int possible = 0;
        for (Rarity r : Rarity.values()) {
            double chance = perks.chanceOf(tier, r);
            if (chance <= 0.0) continue;
            for (PerkStat stat : perks.pool()) {
                possible++;
                if (data.bestPerkValue(stat, r) <= 0.0) newForIndex++;
            }
            lore.add(Lore.mark(ChatColor.GRAY) + plugin.getRarityManager().style(r, r.displayName())
                    + ChatColor.DARK_GRAY + "  " + ChatColor.WHITE + PerkIndexGui.percent(chance));
        }
        lore.add("");
        lore.add(Lore.stat(newForIndex > 0 ? ChatColor.GREEN : ChatColor.DARK_GRAY,
                "New for your index", newForIndex + " of " + possible));
        lore.add("");
        if (data.isPerkAutoSave()) {
            lore.add(Lore.line(ChatColor.GREEN, "Auto save is on: straight to your vault."));
        } else if (data.getPendingPerk() != null) {
            lore.add(Lore.line(ChatColor.RED, "Replaces your unsaved "
                    + plugin.getRarityManager().style(data.getPendingPerk().tier(), data.getPendingPerk().display())));
        }
        if (outOfSaves(data)) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Out of save rolls");
            lore.add(Lore.line(ChatColor.GRAY, "Buy save rolls or switch auto save off."));
        } else if (affordable) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to roll");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not enough drops");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(rollTierKey(plugin), PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Shown in the middle of the menu while rolling is paused for want of save rolls. */
    private static ItemStack outOfSavesNotice() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.RED, "Out of save rolls"));
        meta.setLore(List.of(
                Lore.line(ChatColor.RED, "Auto save is on and you have none left,"),
                Lore.line(ChatColor.RED, "so rolling is paused. No perk gets lost."),
                "",
                Lore.line(ChatColor.GRAY, "Buy save rolls below,"),
                Lore.line(ChatColor.GRAY, "or switch auto save off.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack autoSaveIcon(PlayerData data) {
        boolean on = data.isPerkAutoSave();
        ItemStack item = new ItemStack(on ? Material.ENDER_CHEST : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(on ? ChatColor.GREEN : ChatColor.GRAY, "Auto Save"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every roll goes straight to your"),
                Lore.line(ChatColor.GRAY, "vault and uses one save roll."),
                "",
                Lore.stat(on ? ChatColor.GREEN : ChatColor.RED, "State", on ? "On" : "Off"),
                Lore.stat(ChatColor.AQUA, "Save rolls", String.format("%,d", data.getPerkSaveRolls())),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + (on ? "Click to turn off" : "Click to turn on")));
        if (on) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack amountIcon(PlayerData data) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Buy Amount"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "How many save rolls to buy at once."));
        lore.add("");
        for (int amount : SAVE_AMOUNTS) {
            boolean picked = amount == data.getPerkSaveAmount();
            lore.add(Lore.mark(picked ? ChatColor.GREEN : ChatColor.DARK_GRAY)
                    + (picked ? ChatColor.GREEN + "" + ChatColor.BOLD : ChatColor.GRAY + "") + amount + " save rolls");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to change");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buyIcon(SolRNGPlugin plugin, PlayerData data) {
        int amount = data.getPerkSaveAmount();
        long price = amount * plugin.getPerkManager().saveCost();
        boolean affordable = data.getPoints() >= price;
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Buy " + amount + " Save Rolls"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "One save roll keeps one perk."),
                "",
                Lore.stat(ChatColor.LIGHT_PURPLE, "Price", Currency.CREDITS.price(price, affordable)),
                Lore.stat(ChatColor.DARK_GRAY, "You have", Currency.CREDITS.amount(data.getPoints())),
                Lore.stat(ChatColor.AQUA, "Save rolls", String.format("%,d", data.getPerkSaveRolls())),
                "",
                affordable ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to buy"
                        : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Credits"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The last rolled perk, waiting to be saved. Rolling again replaces
     * it, so the tooltip says both what saving costs and what not saving
     * loses.
     */
    private static ItemStack pendingIcon(SolRNGPlugin plugin, PlayerData data) {
        var pending = data.getPendingPerk();
        if (pending == null) {
            ItemStack item = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "No perk waiting"));
            meta.setLore(List.of(
                    Lore.line(ChatColor.GRAY, "With auto save off, your rolled"),
                    Lore.line(ChatColor.GRAY, "perk waits here until you save it.")));
            item.setItemMeta(meta);
            return item;
        }
        long cost = plugin.getPerkManager().saveCost();
        boolean withSaveRoll = data.getPerkSaveRolls() > 0;
        boolean affordable = withSaveRoll || data.getPoints() >= cost;
        ItemStack item = PerkLore.item(plugin, pending, PerkLore.style(plugin));
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Not saved yet"));
        lore.add(Lore.line(ChatColor.RED, "Your next roll replaces it."));
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Save", withSaveRoll ? "1 save roll"
                : Currency.CREDITS.price(cost, affordable)));
        lore.add("");
        lore.add(affordable ? ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to save"
                : ChatColor.RED + "" + ChatColor.BOLD + "Not enough Credits");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /** Which unsaved perk tier makes a roll ask first. Left click goes up, right click down. */
    private static ItemStack confirmIcon(SolRNGPlugin plugin, PlayerData data) {
        Rarity from = data.getPerkConfirmFrom();
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Reroll Warning"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Asks before a roll throws away an"));
        lore.add(Lore.line(ChatColor.GRAY, "unsaved perk of this tier or higher."));
        lore.add("");
        for (Rarity r : Rarity.values()) {
            boolean picked = r == from;
            lore.add(Lore.mark(picked ? ChatColor.GREEN : ChatColor.DARK_GRAY)
                    + plugin.getRarityManager().style(r, r.displayName())
                    + (picked ? ChatColor.GREEN + " and up" : ""));
        }
        lore.add(Lore.mark(from == null ? ChatColor.GREEN : ChatColor.DARK_GRAY)
                + (from == null ? ChatColor.GREEN : ChatColor.GRAY) + "Never ask");
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to change");
        lore.add(Lore.footnote("Right-click to go back."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** How perks work, in one tooltip: saving, tiers, levels and the five types. */
    private static ItemStack guideIcon(SolRNGPlugin plugin) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "How Perks Work"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Trade drops for a random perk."));
        lore.add(Lore.line(ChatColor.GRAY, "Keep it with a save roll ("
                + Currency.CREDITS.amount(perks.saveCost()) + ChatColor.GRAY + "),"));
        lore.add(Lore.line(ChatColor.GRAY, "or your next roll replaces it."));
        lore.add(Lore.line(ChatColor.GRAY, "Equip up to " + perks.loadoutSlots() + " in the vault."));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Tiers"));
        for (Rarity tier : Rarity.values()) {
            int count = perks.statCountFor(tier);
            lore.add(Lore.mark(ChatColor.GRAY) + plugin.getRarityManager().style(tier, tier.displayName())
                    + ChatColor.DARK_GRAY + "  " + ChatColor.WHITE + PerkLore.rangeText(perks, tier)
                    + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + count + (count == 1 ? " stat" : " stats"));
        }
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Each stat rolls its own value."));
        lore.add("");
        lore.add(Lore.section(ChatColor.GREEN, "Stats"));
        for (PerkStat stat : perks.pool()) {
            lore.add(Lore.mark(stat.colour()) + stat.colour() + stat.label()
                    + ChatColor.DARK_GRAY + "  " + ChatColor.GRAY + stat.description());
        }
        lore.add(Lore.line(ChatColor.GREEN, "The same stat on two perks adds up."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static long countPhysical(SolRNGPlugin plugin, PlayerData data, Rarity rarity) {
        var player = Bukkit.getPlayer(data.getUuid());
        if (player == null) return 0L;
        var key = plugin.getRollListener().getRarityKey();
        long total = 0L;
        for (var stack : player.getInventory().getContents()) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String name = stack.getItemMeta().getPersistentDataContainer()
                    .get(key, PersistentDataType.STRING);
            if (name == null) continue;
            try {
                if (Rarity.valueOf(name) == rarity) total += stack.getAmount();
            } catch (IllegalArgumentException ignored) { }
        }
        return total;
    }

    private static ItemStack vaultLinkIcon(PlayerData data) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perk Vault"));
        int equipped = data.getEquippedPerks().size();
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "See and equip your perks."),
                Lore.stat(ChatColor.GREEN, "Equipped", String.valueOf(equipped)),
                Lore.stat(ChatColor.AQUA, "In vault", String.valueOf(data.getPerkVault().size())),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
