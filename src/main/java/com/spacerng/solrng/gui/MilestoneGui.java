package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.milestone.MilestoneTrack;
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
 * /milestones. The landing page is one icon per track, centred; clicking a
 * track opens its ladder as a grid of panes — green for reached, red for
 * still to go — laid out left to right, top to bottom, seven wide, with
 * paging for long ladders.
 *
 * Panes rather than icons because the shape of the grid is the
 * information: a glance tells you how far along a track you are without
 * reading a single number.
 */
public class MilestoneGui {

    private static final int[] TRACK_SLOTS = {20, 22, 24, 30, 32}; // up to five, centred
    private static final int TIER_START = 10;   // row 2, column 2
    private static final int TIERS_PER_ROW = 7;
    private static final int TIER_ROWS = 4;
    private static final int PAGE_SIZE = TIERS_PER_ROW * TIER_ROWS;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    public static NamespacedKey trackKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_milestone_track");
    }

    /** The landing page: pick a track. */
    public static Inventory buildRoot(SolRNGPlugin plugin, Player player) {
        MilestoneHolder holder = new MilestoneHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Player Milestones");
        holder.setInventory(inv);

        fill(inv, filler());

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int i = 0;
        for (MilestoneTrack track : plugin.getMilestoneManager().getTracks().values()) {
            if (i >= TRACK_SLOTS.length) break;
            inv.setItem(TRACK_SLOTS[i], buildTrackIcon(plugin, player, data, track));
            i++;
        }
        return inv;
    }

    /** One track's ladder. */
    public static Inventory buildTrack(SolRNGPlugin plugin, Player player, String trackId, int page) {
        MilestoneTrack track = plugin.getMilestoneManager().get(trackId);
        if (track == null) return buildRoot(plugin, player);

        MilestoneHolder holder = new MilestoneHolder();
        holder.setTrackId(trackId);

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Milestones » " + track.getDisplay());
        holder.setInventory(inv);
        fill(inv, filler());

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        long progress = plugin.getMilestoneManager().progress(player, data, trackId);

        List<MilestoneTrack.Tier> tiers = track.getTiers();
        int totalPages = Math.max(1, (int) Math.ceil(tiers.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        holder.setPage(page);

        int from = page * PAGE_SIZE;
        int to = Math.min(tiers.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            int offset = i - from;
            int slot = TIER_START + (offset / TIERS_PER_ROW) * 9 + (offset % TIERS_PER_ROW);
            inv.setItem(slot, buildTierPane(plugin, track, tiers.get(i), progress));
        }

        inv.setItem(BACK_SLOT, button(Material.PAINTING, ChatColor.YELLOW + "◀ Back",
                ChatColor.GRAY + "Return to the milestone list"));
        inv.setItem(INFO_SLOT, buildTrackIcon(plugin, player, data, track));
        if (page > 0) {
            inv.setItem(PREV_SLOT, button(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "◀ Previous",
                    ChatColor.GRAY + "Page " + page + "/" + totalPages));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, button(Material.ARROW, ChatColor.YELLOW + "Next ▶",
                    ChatColor.GRAY + "Page " + (page + 2) + "/" + totalPages));
        }
        return inv;
    }

    private static ItemStack buildTrackIcon(SolRNGPlugin plugin, Player player, PlayerData data,
                                            MilestoneTrack track) {
        long progress = plugin.getMilestoneManager().progress(player, data, track.getId());
        int done = track.completedCount(progress);
        int total = track.getTiers().size();

        ItemStack icon = new ItemStack(track.getIcon());
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + track.getDisplay());

        List<String> lore = new ArrayList<>();
        if (!track.getDescription().isEmpty()) {
            lore.add(ChatColor.GRAY + track.getDescription());
            lore.add("");
        }
        lore.add(ChatColor.GRAY + "Progress: " + ChatColor.WHITE + String.format("%,d", progress)
                + ChatColor.GRAY + " " + track.getUnit());
        lore.add(ChatColor.GRAY + "Completed: " + (done == total ? ChatColor.GREEN : ChatColor.YELLOW)
                + done + ChatColor.GRAY + "/" + total);

        MilestoneTrack.Tier next = track.nextTier(progress);
        if (next != null) {
            lore.add("");
            lore.add(ChatColor.GRAY + "Next: " + ChatColor.WHITE + String.format("%,d", next.threshold())
                    + ChatColor.GRAY + " " + track.getUnit()
                    + ChatColor.DARK_GRAY + " (" + String.format("%,d", next.threshold() - progress) + " to go)");
        } else {
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "TRACK COMPLETE");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to view");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(trackKey(plugin), PersistentDataType.STRING, track.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack buildTierPane(SolRNGPlugin plugin, MilestoneTrack track,
                                           MilestoneTrack.Tier tier, long progress) {
        boolean reached = progress >= tier.threshold();

        ItemStack pane = new ItemStack(reached
                ? Material.LIME_STAINED_GLASS_PANE
                : Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName((reached ? ChatColor.GREEN : ChatColor.RED) + "" + ChatColor.BOLD
                + String.format("%,d", tier.threshold()) + " " + track.getUnit());

        List<String> lore = new ArrayList<>();
        String reward = plugin.getMilestoneManager().rewardText(tier);
        if (!reward.isEmpty()) {
            lore.add(ChatColor.GRAY + "Reward: " + reward);
            lore.add("");
        }
        if (reached) {
            lore.add(ChatColor.GREEN + "✔ Reached");
        } else {
            lore.add(ChatColor.GRAY + "Progress: " + ChatColor.WHITE + String.format("%,d", progress)
                    + ChatColor.GRAY + "/" + String.format("%,d", tier.threshold()));
            lore.add(ChatColor.RED + String.format("%,d", tier.threshold() - progress) + " to go");
        }
        meta.setLore(lore);
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static void fill(Inventory inv, ItemStack pane) {
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, pane);
        }
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    public static int backSlot() {
        return BACK_SLOT;
    }

    public static int prevSlot() {
        return PREV_SLOT;
    }

    public static int nextSlot() {
        return NEXT_SLOT;
    }
}
