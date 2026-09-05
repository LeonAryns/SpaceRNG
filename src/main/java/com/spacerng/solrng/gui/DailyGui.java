package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.daily.DailyManager;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /daily — the streak as a run of days you can see all of at once.
 *
 * Same shape as the Nova Core board and deliberately without its
 * checkpoints: the whole tension of a streak is that there's no safety
 * net, so drawing one would be a lie.
 */
public class DailyGui {

    private static final int[] DAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    public static final int CLAIM_SLOT = 49;
    private static final int INFO_SLOT = 45;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        DailyHolder holder = new DailyHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.GOLD + "" + ChatColor.BOLD + "Daily Streak");
        holder.setInventory(inv);

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        DailyManager daily = plugin.getDailyManager();
        daily.refresh(data);

        int streak = data.getDailyStreak();
        int next = daily.nextDay(data);
        boolean ready = daily.canClaim(data);

        List<DailyManager.Day> days = daily.getDays();
        for (int i = 0; i < days.size() && i < DAY_SLOTS.length; i++) {
            inv.setItem(DAY_SLOTS[i], buildDay(daily, days.get(i), streak, next, ready));
        }

        inv.setItem(INFO_SLOT, buildInfo(plugin, data, daily, streak));
        inv.setItem(CLAIM_SLOT, buildClaim(daily, data, next, ready));
        return inv;
    }

    private static ItemStack buildDay(DailyManager daily, DailyManager.Day day, int streak, int next,
                                      boolean ready) {
        boolean claimed = day.day() <= streak;
        boolean current = day.day() == next;
        boolean claimable = current && ready;

        Material material = claimed ? Material.LIME_STAINED_GLASS_PANE
                : claimable ? Material.YELLOW_STAINED_GLASS_PANE
                : current ? Material.ORANGE_STAINED_GLASS_PANE
                : Material.GRAY_STAINED_GLASS_PANE;

        ChatColor accent = claimed ? ChatColor.GREEN : claimable ? ChatColor.YELLOW : ChatColor.GRAY;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(accent, "Day " + day.day()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state(claimed ? "claimed" : claimable ? "ready" : current ? "in progress" : "locked"));
        lore.add("");
        if (!day.note().isEmpty()) {
            lore.add(ChatColor.GRAY + day.note());
            lore.add("");
        }
        String reward = daily.rewardText(day);
        if (!reward.isEmpty()) {
            lore.add(accent + Lore.BULLET + " " + ChatColor.GRAY + "Reward: " + reward);
            lore.add("");
        }
        if (claimed) {
            lore.add(ChatColor.GREEN + Lore.TICK + " Already claimed");
        } else if (claimable) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK THE CHEST TO CLAIM");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Keep your streak alive to reach it.");
        }

        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(claimable ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfo(SolRNGPlugin plugin, PlayerData data, DailyManager daily, int streak) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Your Streak"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("daily"));
        lore.add("");
        lore.add(ChatColor.GRAY + "One claim a day. Miss a day and the");
        lore.add(ChatColor.GRAY + "streak starts over — there's no net.");
        lore.add("");
        lore.add(Lore.stat(ChatColor.GOLD, "Streak", streak + "/" + daily.length()));
        lore.add(Lore.stat(ChatColor.AQUA, "Lifetime claims", String.format("%,d", data.getDailyTotalClaims())));
        lore.add(Lore.stat(ChatColor.YELLOW, "Next claim", daily.timeUntilNext(data)));
        lore.add("");
        lore.add(Lore.bar(daily.length() == 0 ? 0 : (double) streak / daily.length()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildClaim(DailyManager daily, PlayerData data, int next, boolean ready) {
        ItemStack item = new ItemStack(ready ? Material.CHEST : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ready
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "CLAIM DAY " + next
                : ChatColor.RED + "" + ChatColor.BOLD + "ALREADY CLAIMED TODAY");

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("daily"));
        lore.add("");
        if (ready) {
            String reward = daily.getDays().isEmpty() ? "" : daily.rewardText(daily.getDays().get(next - 1));
            if (!reward.isEmpty()) {
                lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "You get: " + reward);
                lore.add("");
            }
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "CLICK TO CLAIM");
        } else {
            lore.add(Lore.stat(ChatColor.YELLOW, "Back in", daily.timeUntilNext(data)));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Come back tomorrow to keep the run.");
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(ready ? Boolean.TRUE : null);
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
}
