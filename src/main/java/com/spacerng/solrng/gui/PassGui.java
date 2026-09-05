package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.pass.PassManager;
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
 * The Battle Pass board (/pass).
 *
 * Nine levels across, two tracks down: the free row sits directly under
 * the level markers and the premium row directly under that, so a level
 * reads as one vertical column of "what this rung is worth" rather than
 * as two separate lists you have to line up by eye.
 */
public class PassGui {

    private static final int LEVEL_ROW = 0;    // slots 0-8
    private static final int FREE_ROW = 9;     // slots 9-17
    private static final int PREMIUM_ROW = 18; // slots 18-26
    private static final int DIVIDER_ROW = 27; // slots 27-35
    private static final int NAV_ROW = 36;     // slots 36-44

    private static final int PREV_SLOT = 36;
    private static final int PROGRESS_SLOT = 40;
    private static final int PREMIUM_SLOT = 38;
    private static final int CLAIM_ALL_SLOT = 42;
    private static final int NEXT_SLOT = 44;
    private static final int PER_PAGE = 9;

    public static NamespacedKey levelKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_pass_level");
    }

    public static NamespacedKey trackKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_pass_track");
    }

    public static int prevSlot() {
        return PREV_SLOT;
    }

    public static int nextSlot() {
        return NEXT_SLOT;
    }

    public static int premiumSlot() {
        return PREMIUM_SLOT;
    }

    public static int claimAllSlot() {
        return CLAIM_ALL_SLOT;
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, int page) {
        PassManager pass = plugin.getPassManager();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        pass.syncSeason(data);

        int totalPages = Math.max(1, (int) Math.ceil(pass.getMaxLevel() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        PassHolder holder = new PassHolder();
        holder.setPage(page);
        Inventory inv = Bukkit.createInventory(holder, 45,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Battle Pass"
                        + ChatColor.GRAY + " — " + pass.getSeasonName());
        holder.setInventory(inv);

        ItemStack divider = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inv.setItem(DIVIDER_ROW + i, divider);
            inv.setItem(NAV_ROW + i, divider);
        }

        int current = pass.levelOf(data);
        for (int column = 0; column < PER_PAGE; column++) {
            int level = page * PER_PAGE + column + 1;
            if (level > pass.getMaxLevel()) {
                inv.setItem(LEVEL_ROW + column, divider);
                inv.setItem(FREE_ROW + column, divider);
                inv.setItem(PREMIUM_ROW + column, divider);
                continue;
            }
            PassManager.Level rung = pass.getLevels().get(level - 1);
            inv.setItem(LEVEL_ROW + column, levelMarker(pass, data, rung, current));
            inv.setItem(FREE_ROW + column, rewardIcon(plugin, pass, data, rung, PassManager.FREE, current));
            inv.setItem(PREMIUM_ROW + column, rewardIcon(plugin, pass, data, rung, PassManager.PREMIUM, current));
        }

        inv.setItem(PROGRESS_SLOT, progressPanel(plugin, pass, data, current));
        inv.setItem(PREMIUM_SLOT, premiumPanel(pass, data));
        inv.setItem(CLAIM_ALL_SLOT, claimAllPanel(pass, data));
        if (page > 0) {
            inv.setItem(PREV_SLOT, navButton(Material.SPECTRAL_ARROW, "◀ Previous", page, totalPages));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, navButton(Material.ARROW, "Next ▶", page + 2, totalPages));
        }
        return inv;
    }

    /**
     * The level number itself. Green once cleared, yellow for the one
     * being worked on, grey for everything still ahead — the same three
     * states the skill tree uses, so the colours mean the same thing
     * everywhere in the plugin.
     */
    private static ItemStack levelMarker(PassManager pass, PlayerData data, PassManager.Level rung, int current) {
        boolean cleared = current >= rung.level();
        boolean active = current + 1 == rung.level();

        ItemStack item = new ItemStack(cleared ? Material.LIME_STAINED_GLASS_PANE
                : active ? Material.YELLOW_STAINED_GLASS_PANE
                         : Material.GRAY_STAINED_GLASS_PANE);
        item.setAmount(Math.max(1, Math.min(64, rung.level())));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((cleared ? ChatColor.GREEN : active ? ChatColor.YELLOW : ChatColor.DARK_GRAY)
                + "" + ChatColor.BOLD + "LEVEL " + rung.level());

        List<String> lore = new ArrayList<>();
        if (cleared) {
            lore.add(ChatColor.GREEN + "✔ Reached");
        } else if (active) {
            long into = pass.xpIntoLevel(data);
            lore.add(Lore.bar(rung.xpRequired() <= 0 ? 1.0 : (double) into / rung.xpRequired()));
            lore.add(ChatColor.GRAY + String.format("%,d", into) + ChatColor.DARK_GRAY + " / "
                    + ChatColor.GRAY + String.format("%,d", rung.xpRequired()) + " XP");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Locked");
            lore.add(ChatColor.DARK_GRAY + String.format("%,d", rung.xpRequired()) + " XP to clear");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack rewardIcon(SolRNGPlugin plugin, PassManager pass, PlayerData data,
                                        PassManager.Level rung, String track, int current) {
        boolean premium = PassManager.PREMIUM.equals(track);
        PassManager.Reward reward = premium ? rung.premium() : rung.free();

        if (reward.isEmpty()) {
            ItemStack empty = pane(Material.GRAY_STAINED_GLASS_PANE,
                    ChatColor.DARK_GRAY + (premium ? "No premium reward" : "No free reward"));
            return empty;
        }

        boolean claimed = data.hasClaimedPass(track, rung.level());
        boolean earned = current >= rung.level();
        boolean locked = premium && !data.isPassPremium();

        Material material = claimed ? Material.LIME_DYE
                : locked ? Material.IRON_BARS
                : earned ? (premium ? Material.PURPLE_SHULKER_BOX : Material.CHEST)
                         : Material.GRAY_DYE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((premium ? ChatColor.LIGHT_PURPLE : ChatColor.GREEN) + "" + ChatColor.BOLD
                + (premium ? "PREMIUM" : "FREE") + ChatColor.RESET + ChatColor.GRAY + " · Level " + rung.level());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + (premium ? "PREMIUM TRACK" : "FREE TRACK"));
        lore.add("");
        lore.add(ChatColor.GRAY + "Reward: " + pass.describe(reward));
        if (!reward.note().isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + reward.note());
        }
        lore.add("");
        if (claimed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "CLAIMED");
        } else if (locked) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "PREMIUM ONLY");
            lore.add(ChatColor.DARK_GRAY + "Unlock the premium track below.");
        } else if (!earned) {
            lore.add(ChatColor.RED + "▎ " + ChatColor.GRAY + "Reach level " + ChatColor.RED + rung.level());
        } else {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO CLAIM");
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(!claimed && earned && !locked ? Boolean.TRUE : null);
        meta.getPersistentDataContainer().set(levelKey(plugin), PersistentDataType.INTEGER, rung.level());
        meta.getPersistentDataContainer().set(trackKey(plugin), PersistentDataType.STRING, track);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack progressPanel(SolRNGPlugin plugin, PassManager pass, PlayerData data, int current) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "YOUR PASS");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + pass.getSeasonName().toUpperCase());
        lore.add("");
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Level: " + ChatColor.YELLOW + current
                + ChatColor.GRAY + "/" + ChatColor.YELLOW + pass.getMaxLevel());
        if (current < pass.getMaxLevel()) {
            long into = pass.xpIntoLevel(data);
            long needed = pass.xpForNextLevel(data);
            lore.add(Lore.bar(needed <= 0 ? 1.0 : (double) into / needed));
            lore.add(ChatColor.DARK_GRAY + "▎ " + ChatColor.GRAY + String.format("%,d", needed - into)
                    + " XP to level " + (current + 1));
        } else {
            lore.add(ChatColor.GREEN + "▎ Season complete.");
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Rolls and harvests both earn XP —");
        lore.add(ChatColor.DARK_GRAY + "the rarer the roll, the more it pays.");
        double bonus = plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.PASS_XP);
        if (bonus > 0) {
            lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Skill bonus: " + ChatColor.AQUA
                    + "+" + Math.round(bonus * 100) + "% XP");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack premiumPanel(PassManager pass, PlayerData data) {
        boolean owned = data.isPassPremium();
        ItemStack item = new ItemStack(owned ? Material.AMETHYST_SHARD : Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "PREMIUM PASS");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "SEASON UPGRADE");
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "UNLOCKED");
            lore.add(ChatColor.GRAY + "Every premium reward is yours to claim.");
        } else {
            lore.add(ChatColor.GRAY + "Opens the second reward track for the");
            lore.add(ChatColor.GRAY + "whole season — including every level");
            lore.add(ChatColor.GRAY + "you have already passed.");
            lore.add("");
            lore.add(ChatColor.LIGHT_PURPLE + "▎ " + ChatColor.GRAY + "Price: " + ChatColor.LIGHT_PURPLE
                    + String.format("%,d", pass.getPremiumCost()) + " Credits");
            lore.add(ChatColor.DARK_GRAY + "▎ You have " + ChatColor.LIGHT_PURPLE
                    + String.format("%,d", data.getPoints()) + " Credits");
            lore.add("");
            lore.add(data.getPoints() >= pass.getPremiumCost()
                    ? ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO UNLOCK"
                    : ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH CREDITS");
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(owned ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack claimAllPanel(PassManager pass, PlayerData data) {
        int waiting = 0;
        for (int level = 1; level <= pass.getMaxLevel(); level++) {
            if (pass.canClaim(data, level, PassManager.FREE)) waiting++;
            if (pass.canClaim(data, level, PassManager.PREMIUM)) waiting++;
        }

        ItemStack item = new ItemStack(waiting > 0 ? Material.HOPPER : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((waiting > 0 ? ChatColor.YELLOW : ChatColor.DARK_GRAY) + "" + ChatColor.BOLD
                + "CLAIM ALL");
        meta.setLore(waiting > 0
                ? List.of(ChatColor.GRAY + "" + waiting + " reward" + (waiting == 1 ? "" : "s") + " waiting.",
                          "",
                          ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO COLLECT")
                : List.of(ChatColor.DARK_GRAY + "Nothing to collect right now."));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navButton(Material material, String label, int shownPage, int total) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + label);
        meta.setLore(List.of(ChatColor.GRAY + "Page " + shownPage + "/" + total));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
