package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.milestone.MilestoneTrack;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.RollFormat;
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
 * /milestones — one screen, not two. The tracks live as tabs across the
 * top row with a gap between each, so switching between them never leaves
 * the menu; the track you're looking at carries an enchant glint.
 *
 * Tiers below are green/red panes laid out seven wide. The shape of the
 * grid is the information: a glance tells you how far along a track you
 * are without reading a single number.
 */
public class MilestoneGui {

    // Top row, one empty slot between each — four tracks land on 1/3/5/7.
    private static final int[] TAB_SLOTS = {1, 3, 5, 7};
    private static final int TIER_START = 19;   // row 3, column 2
    private static final int TIERS_PER_ROW = 7;
    private static final int TIER_ROWS = 3;
    private static final int PAGE_SIZE = TIERS_PER_ROW * TIER_ROWS;
    private static final int PREV_SLOT = 48;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 50;

    // Thin block-drawing glyphs. Minecraft's unicode font pages cover
    // U+2500-U+25FF, so these render without a resource pack.
    private static final String BULLET = "▎";  // ▎ the lore bullet
    private static final String BAR = "▌";     // ▌ one notch of the progress bar
    private static final int BAR_LENGTH = 20;

    public static NamespacedKey trackKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_milestone_track");
    }

    /** Identifies a claimable rung, stored as "track:index". */
    public static NamespacedKey tierKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_milestone_tier");
    }

    /** Opens on the first track, or on whichever one was asked for. */
    public static Inventory build(SolRNGPlugin plugin, Player player, String trackId, int page) {
        var tracks = plugin.getMilestoneManager().getTracks();
        if (tracks.isEmpty()) {
            MilestoneHolder empty = new MilestoneHolder();
            Inventory inv = Bukkit.createInventory(empty, 54, ChatColor.DARK_PURPLE + "Milestones");
            empty.setInventory(inv);
            return inv;
        }

        MilestoneTrack track = trackId == null ? null : tracks.get(trackId);
        if (track == null) track = tracks.values().iterator().next();

        MilestoneHolder holder = new MilestoneHolder();
        holder.setTrackId(track.getId());

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Milestones " + ChatColor.DARK_GRAY + "» "
                        + ChatColor.WHITE + track.getDisplay());
        holder.setInventory(inv);

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }
        // A dark band under the tabs so they read as their own strip.
        for (int slot = 9; slot < 18; slot++) {
            inv.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE));
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        int i = 0;
        for (MilestoneTrack tab : tracks.values()) {
            if (i >= TAB_SLOTS.length) break;
            inv.setItem(TAB_SLOTS[i], buildTab(plugin, player, data, tab, tab.getId().equals(track.getId())));
            i++;
        }

        long progress = plugin.getMilestoneManager().progress(player, data, track.getId());
        List<MilestoneTrack.Tier> tiers = track.getTiers();
        int totalPages = Math.max(1, (int) Math.ceil(tiers.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        holder.setPage(page);

        int from = page * PAGE_SIZE;
        int to = Math.min(tiers.size(), from + PAGE_SIZE);
        for (int t = from; t < to; t++) {
            int offset = t - from;
            int slot = TIER_START + (offset / TIERS_PER_ROW) * 9 + (offset % TIERS_PER_ROW);
            inv.setItem(slot, buildTierPane(plugin, data, track, tiers.get(t), progress));
        }

        inv.setItem(INFO_SLOT, buildSummary(plugin, track, progress));
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

    /** A track tab. The one you're viewing glints. */
    private static ItemStack buildTab(SolRNGPlugin plugin, Player player, PlayerData data,
                                      MilestoneTrack track, boolean active) {
        long progress = plugin.getMilestoneManager().progress(player, data, track.getId());
        int done = track.completedCount(progress);
        int total = track.getTiers().size();

        ItemStack icon = new ItemStack(track.getIcon());
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(active ? ChatColor.GREEN : ChatColor.YELLOW, track.getDisplay()));

        List<String> lore = new ArrayList<>();
        if (!track.getDescription().isEmpty()) {
            lore.add(Lore.section(ChatColor.YELLOW, "About"));
            lore.add(Lore.line(ChatColor.YELLOW, track.getDescription()));
            lore.add("");
        }
        lore.add(Lore.section(ChatColor.AQUA, "Information"));
        lore.add(Lore.stat(done == total ? ChatColor.GREEN : ChatColor.AQUA,
                "Completed", done + " / " + total));
        lore.add(Lore.stat(ChatColor.AQUA, "Progress",
                String.format("%,d", progress) + " " + track.getUnit()));
        lore.add(Lore.bar(total == 0 ? 0.0 : (double) done / total));
        int ready = plugin.getMilestoneManager().claimableCount(player, data, track);
        if (ready > 0) {
            lore.add(Lore.stat(ChatColor.GOLD, "Waiting",
                    ready + " reward" + (ready == 1 ? "" : "s") + " to claim"));
        }
        lore.add("");
        lore.add(active
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "VIEWING"
                : ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO VIEW");

        meta.setLore(lore);
        // Glint marks the open tab without changing its icon, so the row
        // stays readable as a set of the same thing.
        meta.setEnchantmentGlintOverride(active ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(trackKey(plugin), PersistentDataType.STRING, track.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * One rung. Reads as a card: the goal, the category, what it asks of
     * you, what it pays, and how far along you are with a filled bar.
     */
    private static ItemStack buildTierPane(SolRNGPlugin plugin, PlayerData data, MilestoneTrack track,
                                           MilestoneTrack.Tier tier, long progress) {
        boolean reached = progress >= tier.threshold();
        boolean claimed = data.hasClaimedMilestone(track.keyFor(tier));
        boolean claimable = reached && !claimed;
        ChatColor accent = claimed ? ChatColor.GREEN : claimable ? ChatColor.YELLOW : ChatColor.RED;

        // Three states, three icons: a lodestone for a rung still out of
        // reach, a lime pane for one earned and waiting, a green pane for
        // one already spent. A claimable rung also glints, so a full menu
        // shows what there is to collect without reading anything.
        ItemStack pane = new ItemStack(claimed
                ? Material.GREEN_STAINED_GLASS_PANE
                : claimable ? Material.LIME_STAINED_GLASS_PANE : Material.LODESTONE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(Lore.title(accent,
                String.format("%,d", tier.threshold()) + " " + track.getUnit()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "Goal"));
        lore.add(Lore.line(ChatColor.AQUA, track.getVerb() + " " + String.format("%,d", tier.threshold())
                + " " + track.getUnit() + "."));
        lore.add("");
        if (tier.tokens() > 0 || tier.shards() > 0 || tier.money() > 0) {
            lore.add(Lore.section(ChatColor.GOLD, "Reward"));
            if (tier.tokens() > 0) {
                lore.add(Currency.TOKENS.colour() + Lore.BULLET + " " + Currency.TOKENS.amount(tier.tokens()));
            }
            if (tier.shards() > 0) {
                lore.add(Currency.GEMS.colour() + Lore.BULLET + " " + Currency.GEMS.amount(tier.shards()));
            }
            if (tier.money() > 0) {
                lore.add(Currency.COINS.colour() + Lore.BULLET + " "
                        + Currency.COINS.amount(Math.round(tier.money())));
            }
            lore.add("");
        }
        lore.add(Lore.section(ChatColor.AQUA, "Progress"));
        lore.add(Lore.requirement(track.getUnit(),
                String.format("%,d", Math.min(progress, tier.threshold())),
                String.format("%,d", tier.threshold()), progress >= tier.threshold()));
        lore.add(Lore.bar(tier.threshold() <= 0 ? 1.0 : (double) progress / tier.threshold()));
        lore.add("");
        if (claimed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "CLAIMED");
        } else if (claimable) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO CLAIM");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "LOCKED");
            lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY
                    + String.format("%,d", tier.threshold() - progress) + " " + track.getUnit() + " to go");
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(claimable ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(tierKey(plugin), PersistentDataType.STRING,
                track.getId() + ":" + tier.index());
        pane.setItemMeta(meta);
        return pane;
    }

    /** "▌▌▌▌▌▌░░░░" in green over grey. */
    private static String progressBar(long progress, long threshold) {
        int filled = threshold <= 0 ? BAR_LENGTH
                : (int) Math.min(BAR_LENGTH, Math.round((double) progress / threshold * BAR_LENGTH));
        return ChatColor.GREEN + BAR.repeat(filled) + ChatColor.DARK_GRAY + BAR.repeat(BAR_LENGTH - filled);
    }

    private static ItemStack buildSummary(SolRNGPlugin plugin, MilestoneTrack track, long progress) {
        int done = track.completedCount(progress);
        int total = track.getTiers().size();

        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + track.getDisplay().toUpperCase());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "MILESTONES");
        lore.add("");
        lore.add(ChatColor.YELLOW + BULLET + " " + ChatColor.GRAY + "Tier: " + ChatColor.AQUA + done
                + ChatColor.GRAY + "/" + ChatColor.AQUA + total);
        lore.add(ChatColor.YELLOW + BULLET + " " + ChatColor.GRAY + "Total: " + ChatColor.WHITE
                + String.format("%,d", progress) + ChatColor.GRAY + " " + track.getUnit());

        MilestoneTrack.Tier next = track.nextTier(progress);
        lore.add("");
        if (next == null) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "TRACK COMPLETE");
        } else {
            lore.add(ChatColor.YELLOW + BULLET + " " + ChatColor.GRAY + "Next: " + ChatColor.WHITE
                    + String.format("%,d", next.threshold()) + ChatColor.GRAY + " " + track.getUnit());
            lore.add(ChatColor.YELLOW + BULLET + " " + ChatColor.GRAY + "To go: " + ChatColor.WHITE
                    + String.format("%,d", next.threshold() - progress));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    public static int prevSlot() {
        return PREV_SLOT;
    }

    public static int nextSlot() {
        return NEXT_SLOT;
    }
}
