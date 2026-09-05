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

        inv.setItem(PROGRESS_SLOT, progressPanel(plugin, player, pass, data, current));
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
        ChatColor accent = cleared ? ChatColor.GREEN : active ? ChatColor.YELLOW : ChatColor.DARK_GRAY;

        ItemStack item = new ItemStack(cleared ? Material.LIME_STAINED_GLASS_PANE
                : active ? Material.YELLOW_STAINED_GLASS_PANE
                         : Material.GRAY_STAINED_GLASS_PANE);
        item.setAmount(Math.max(1, Math.min(64, rung.level())));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(accent, "Level " + rung.level()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state(cleared ? "cleared" : active ? "in progress" : "locked"));
        lore.add("");
        if (cleared) {
            lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "Reached  "
                    + ChatColor.GREEN + Lore.TICK);
        } else if (active) {
            long into = pass.xpIntoLevel(data);
            lore.add(Lore.requirement("XP", String.format("%,d", into),
                    String.format("%,d", rung.xpRequired()), false));
            lore.add(Lore.bar(rung.xpRequired() <= 0 ? 1.0 : (double) into / rung.xpRequired()));
        } else {
            lore.add(Lore.stat(ChatColor.DARK_GRAY, "Costs", String.format("%,d", rung.xpRequired()) + " XP"));
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
            ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta emptyMeta = empty.getItemMeta();
            emptyMeta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, premium ? "Premium" : "Free"));
            emptyMeta.setLore(List.of(Lore.state("empty"), "",
                    ChatColor.DARK_GRAY + Lore.BULLET + " Nothing on this rung."));
            empty.setItemMeta(emptyMeta);
            return empty;
        }

        boolean claimed = data.hasClaimedPass(track, rung.level());
        boolean earned = current >= rung.level();
        boolean locked = premium && !data.isPassPremium();
        ChatColor accent = premium ? ChatColor.LIGHT_PURPLE : ChatColor.GREEN;

        Material material = claimed ? Material.LIME_DYE
                : locked ? Material.IRON_BARS
                : earned ? (premium ? Material.PURPLE_SHULKER_BOX : Material.CHEST)
                         : Material.GRAY_DYE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(claimed ? ChatColor.GREEN : accent,
                (premium ? "Premium" : "Free") + " " + rung.level()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state(premium ? "premium track" : "free track"));
        lore.add("");
        lore.add(Lore.section(accent, "Reward"));
        lore.addAll(pass.describeLines(reward));
        if (!reward.note().isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + reward.note());
        }
        lore.add("");
        if (claimed) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "CLAIMED");
        } else if (locked) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "LOCKED");
            lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Unlock the premium track below.");
        } else if (!earned) {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "LOCKED");
            lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Reach level "
                    + ChatColor.YELLOW + rung.level());
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

    private static ItemStack progressPanel(SolRNGPlugin plugin, Player player, PassManager pass,
                                           PlayerData data, int current) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        meta.setDisplayName(Lore.title(ChatColor.GOLD, player.getName()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state(pass.getSeasonName()));
        lore.add("");
        lore.add(Lore.stat(ChatColor.GOLD, "Level", current + " / " + pass.getMaxLevel()));
        if (current < pass.getMaxLevel()) {
            long into = pass.xpIntoLevel(data);
            long needed = pass.xpForNextLevel(data);
            lore.add(Lore.bar(needed <= 0 ? 1.0 : (double) into / needed));
            lore.add(Lore.stat(ChatColor.AQUA, "Next level",
                    String.format("%,d", Math.max(0, needed - into)) + " XP"));
        } else {
            lore.add(Lore.bar(1.0));
            lore.add(Lore.line(ChatColor.GREEN, "Season complete."));
        }
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Earning XP"));
        lore.add(Lore.line(ChatColor.AQUA, "Every roll — rarer pays more."));
        lore.add(Lore.line(ChatColor.AQUA, "Every crop you harvest."));
        double bonus = plugin.getSkillTreeManager()
                .totalOf(data, com.spacerng.solrng.player.SkillNode.Effect.PASS_XP);
        if (bonus > 0) {
            lore.add(Lore.stat(ChatColor.GREEN, "Skill bonus", "+" + Math.round(bonus * 100) + "% XP"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack premiumPanel(PassManager pass, PlayerData data) {
        boolean owned = data.isPassPremium();
        ItemStack item = new ItemStack(owned ? Material.AMETHYST_SHARD : Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Premium Pass"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("season upgrade"));
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "What it does"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Opens the second reward track."));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "Back-pays every level you"));
        lore.add(Lore.line(ChatColor.LIGHT_PURPLE, "have already cleared."));
        lore.add("");
        if (owned) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "UNLOCKED");
        } else {
            boolean affordable = data.getPoints() >= pass.getPremiumCost();
            lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Information"));
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Cost: " + Currency.CREDITS.price(pass.getPremiumCost(), affordable));
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + ChatColor.DARK_GRAY + "You have "
                    + Currency.CREDITS.amount(data.getPoints()));
            lore.add("");
            lore.add(affordable
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
        meta.setDisplayName(Lore.title(waiting > 0 ? ChatColor.YELLOW : ChatColor.DARK_GRAY, "Claim All"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("collect"));
        lore.add("");
        if (waiting > 0) {
            lore.add(Lore.stat(ChatColor.YELLOW, "Waiting",
                    waiting + " reward" + (waiting == 1 ? "" : "s")));
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO COLLECT");
        } else {
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Nothing to collect right now.");
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(waiting > 0 ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack navButton(Material material, String label, int shownPage, int total) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + label);
        meta.setLore(List.of(Lore.stat(ChatColor.AQUA, "Page", shownPage + " / " + total)));
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
