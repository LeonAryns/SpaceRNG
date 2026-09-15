package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkInstance;
import com.spacerng.solrng.perk.PerkManager;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The vault menu - shows every perk this player owns and lets them
 * equip up to `loadout-slots` at once.
 *
 * Layout, distinct from every other menu in the plugin so a player
 * knows at a glance where they are:
 *   Row 1: what the loadout adds up to, the equipped loadout, and the
 *          Roller link, on a magenta glass rail.
 *   Row 2: divider of light-blue glass panes.
 *   Rows 3-5: paginated grid of vaulted perks, best first. Clicking one
 *          equips or unequips it.
 *   Row 6: prev / perk index / next.
 */
public class PerkVaultGui {

    private static final int SIZE = 54;
    private static final int SUMMARY_SLOT = 0;
    private static final int ROLLER_SLOT = 8;
    private static final int VAULT_START = 18;
    private static final int VAULT_END = 44;
    private static final int PAGE_SIZE = VAULT_END - VAULT_START + 1;
    private static final int PREV_SLOT = 45;
    private static final int INDEX_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    /** Best tier first, then the biggest bonuses, so the good ones are on page one. */
    private static final Comparator<PerkInstance> BEST_FIRST = Comparator
            .comparing((PerkInstance p) -> p.tier().ordinal()).reversed()
            .thenComparing(Comparator.comparingDouble(PerkInstance::total).reversed());

    public static NamespacedKey perkIdKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_perk_id");
    }

    public static int prevSlot() { return PREV_SLOT; }
    public static int nextSlot() { return NEXT_SLOT; }
    public static int rollerSlot() { return ROLLER_SLOT; }
    public static int indexSlot() { return INDEX_SLOT; }

    public static Inventory build(SolRNGPlugin plugin, Player player, int page) {
        PerkVaultHolder holder = new PerkVaultHolder();
        holder.setPage(page);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Perk Vault");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();
        String style = PerkLore.style(plugin);

        // Rails: row 1 magenta, row 2 aqua divider, row 6 dark magenta.
        ItemStack magenta = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack aqua = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack purple = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, magenta);
        for (int i = 9; i < 18; i++) inv.setItem(i, aqua);
        for (int i = 45; i < 54; i++) inv.setItem(i, purple);
        for (int i = 18; i < 45; i++) inv.setItem(i, filler);

        // Equipped loadout row: N slots centred, the total on 0, Roll on 8.
        inv.setItem(SUMMARY_SLOT, PerkLore.loadoutSummary(plugin, data));
        int slots = perks.loadoutSlots();
        int start = Math.max(1, (9 - slots) / 2);
        List<PerkInstance> equipped = data.getEquippedPerks();
        for (int i = 0; i < slots && start + i < ROLLER_SLOT; i++) {
            PerkInstance perk = i < equipped.size() ? equipped.get(i) : null;
            inv.setItem(start + i, perk == null ? emptySlotIcon(i + 1)
                    : perkIcon(plugin, perk, true, false, style));
        }
        inv.setItem(ROLLER_SLOT, rollerLinkIcon(data));

        // Vault contents, best first. Clicks go by perk id, so the order
        // shown never has to match the order saved.
        List<PerkInstance> vault = new ArrayList<>(data.getPerkVault());
        vault.sort(BEST_FIRST);
        int totalPages = Math.max(1, (int) Math.ceil(vault.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int from = page * PAGE_SIZE;
        int to = Math.min(vault.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            PerkInstance perk = vault.get(i);
            boolean equippedFlag = data.isPerkEquipped(perk.id());
            inv.setItem(VAULT_START + (i - from), perkIcon(plugin, perk, equippedFlag, true, style));
        }
        if (vault.isEmpty()) {
            inv.setItem(31, emptyVaultIcon());
        }

        // Nav
        if (page > 0) inv.setItem(PREV_SLOT, navIcon(Material.SPECTRAL_ARROW,
                "Previous", "Page " + page + " / " + totalPages));
        if (page < totalPages - 1) inv.setItem(NEXT_SLOT, navIcon(Material.ARROW,
                "Next", "Page " + (page + 2) + " / " + totalPages));
        inv.setItem(INDEX_SLOT, indexLinkIcon(data));

        return inv;
    }

    private static ItemStack perkIcon(SolRNGPlugin plugin, PerkInstance perk,
                                      boolean equipped, boolean inVault, String style) {
        ItemStack item = PerkLore.item(plugin, perk, style);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
        lore.add("");
        if (equipped) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Equipped");
            lore.add(Lore.footnote("Click to unequip."));
        } else if (inVault) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to equip");
            lore.add(Lore.footnote("Shift-click to discard."));
        }
        meta.setLore(lore);
        if (equipped) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(perkIdKey(plugin),
                PersistentDataType.STRING, perk.id().toString());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack emptySlotIcon(int index) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GRAY, "Loadout Slot " + index));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Empty."),
                "",
                Lore.footnote("Click a perk below to equip it.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack rollerLinkIcon(PlayerData data) {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perk Roller"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Trade drops for perks."),
                Lore.stat(ChatColor.AQUA, "In your vault", String.valueOf(data.getPerkVault().size())),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack indexLinkIcon(PlayerData data) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Perk Index"));
        int total = com.spacerng.solrng.perk.PerkStat.rollableStats().size()
                * com.spacerng.solrng.rarity.Rarity.values().length;
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "Every perk you can roll."),
                Lore.stat(ChatColor.GREEN, "Found", data.getPerkIndex().size() + " / " + total),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack emptyVaultIcon() {
        ItemStack item = new ItemStack(Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "Empty"));
        meta.setLore(List.of(
                Lore.line(ChatColor.GRAY, "You have no perks yet."),
                "",
                Lore.footnote("Roll one in the Perk Roller.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navIcon(Material material, String label, String sub) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + label);
        meta.setLore(List.of(Lore.line(ChatColor.GRAY, sub)));
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

    /** Reads the perk-id tag off a clicked stack. Null when there isn't one. */
    public static UUID clickedId(SolRNGPlugin plugin, ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(perkIdKey(plugin), PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
