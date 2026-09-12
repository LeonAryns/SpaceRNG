package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.perk.PerkInstance;
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
import java.util.List;
import java.util.UUID;

/**
 * The vault menu - shows every perk this player owns and lets them
 * equip up to `loadout-slots` at once.
 *
 * Layout, distinct from every other menu in the plugin so a player
 * knows at a glance where they are:
 *   Row 1: the equipped loadout, one clickable slot per loadout position
 *          plus a Roll button, on a magenta glass rail.
 *   Row 2: divider of light-blue glass panes.
 *   Rows 3-5: paginated grid of vaulted perks. Clicking one equips or
 *          unequips it.
 *   Row 6: prev / next / back-to-roller.
 */
public class PerkVaultGui {

    private static final int SIZE = 54;
    private static final int LOADOUT_ROW = 0;
    private static final int ROLLER_SLOT = 8;
    private static final int VAULT_START = 18;
    private static final int VAULT_END = 44;
    private static final int PAGE_SIZE = VAULT_END - VAULT_START + 1;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    public static NamespacedKey perkIdKey(SolRNGPlugin plugin) {
        return SolRNGPlugin.key("solrng_perk_id");
    }

    public static int prevSlot() { return PREV_SLOT; }
    public static int nextSlot() { return NEXT_SLOT; }
    public static int rollerSlot() { return ROLLER_SLOT; }
    public static int vaultStart() { return VAULT_START; }
    public static int vaultEnd() { return VAULT_END; }
    public static int loadoutRow() { return LOADOUT_ROW; }

    public static Inventory build(SolRNGPlugin plugin, Player player, int page) {
        PerkVaultHolder holder = new PerkVaultHolder();
        holder.setPage(page);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Perk Vault");
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PerkManager perks = plugin.getPerkManager();

        // Rails: row 1 magenta, row 2 aqua divider, row 6 dark magenta.
        ItemStack magenta = pane(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemStack aqua = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack purple = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) inv.setItem(i, magenta);
        for (int i = 9; i < 18; i++) inv.setItem(i, aqua);
        for (int i = 45; i < 54; i++) inv.setItem(i, purple);
        for (int i = 18; i < 45; i++) inv.setItem(i, filler);

        // Equipped loadout row: N slots centred on 1..7, with Roll on 8.
        int slots = perks.loadoutSlots();
        int start = Math.max(1, (9 - slots) / 2);
        List<PerkInstance> equipped = data.getEquippedPerks();
        for (int i = 0; i < slots; i++) {
            int slot = start + i;
            PerkInstance perk = i < equipped.size() ? equipped.get(i) : null;
            inv.setItem(slot, perk == null ? emptySlotIcon(i + 1)
                    : perkIcon(plugin, perk, true, false));
        }
        inv.setItem(ROLLER_SLOT, rollerLinkIcon(data));

        // Vault contents
        List<PerkInstance> vault = data.getPerkVault();
        int totalPages = Math.max(1, (int) Math.ceil(vault.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        int from = page * PAGE_SIZE;
        int to = Math.min(vault.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            PerkInstance perk = vault.get(i);
            boolean equippedFlag = data.isPerkEquipped(perk.id());
            inv.setItem(VAULT_START + (i - from), perkIcon(plugin, perk, equippedFlag, true));
        }
        if (vault.isEmpty()) {
            inv.setItem(31, emptyVaultIcon());
        }

        // Nav
        if (page > 0) inv.setItem(PREV_SLOT, navIcon(Material.SPECTRAL_ARROW,
                "Previous", "Page " + page + " / " + totalPages));
        if (page < totalPages - 1) inv.setItem(NEXT_SLOT, navIcon(Material.ARROW,
                "Next", "Page " + (page + 2) + " / " + totalPages));

        return inv;
    }

    private static ItemStack perkIcon(SolRNGPlugin plugin, PerkInstance perk,
                                      boolean equipped, boolean inVault) {
        PerkManager perks = plugin.getPerkManager();
        ItemStack item = new ItemStack(perk.type().icon());
        ItemMeta meta = item.getItemMeta();

        String name = plugin.getRarityManager().style(perk.tier(), perk.display())
                + ChatColor.GRAY + " " + perk.roman();
        meta.setDisplayName(ChatColor.DARK_GRAY + "「 " + ChatColor.RESET
                + ChatColor.BOLD + name + ChatColor.RESET + ChatColor.DARK_GRAY + " 」");

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(perk.type().colour(), perk.type().label()));
        for (var entry : perks.statsOf(perk).entrySet()) {
            PerkStat stat = entry.getKey();
            lore.add(stat.colour() + Lore.BULLET + " " + ChatColor.GRAY + stat.label() + ": "
                    + ChatColor.WHITE + stat.format(entry.getValue()));
        }
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Details"));
        lore.add(Lore.stat(perk.tier() == Rarity.DIVINE ? ChatColor.LIGHT_PURPLE : ChatColor.AQUA,
                "Tier", perk.tier().displayName()));
        lore.add(Lore.stat(ChatColor.YELLOW, "Level", perk.roman()));
        lore.add("");
        if (equipped) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD
                    + (inVault ? "Equipped - click to unequip" : "Equipped"));
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

    private static ItemStack emptyVaultIcon() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
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
