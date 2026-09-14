package com.spacerng.solrng.commands.admin;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import com.spacerng.solrng.roll.RollAura;
import com.spacerng.solrng.starforge.StarforgeTier;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** What every admin section shares: the plugin, and reading a player, rarity or amount out of arguments. */
abstract class AdminTools {

    final SolRNGPlugin plugin;
    final Random random = new Random();

    AdminTools(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    /** Splits a bulk amount into stack-sized chunks, overflowing to the ground. */
    void giveStacks(Player target, ItemStack template, long amount) {
        int max = Math.max(1, template.getMaxStackSize());
        long remaining = amount;
        while (remaining > 0) {
            int size = (int) Math.min(max, remaining);
            ItemStack stack = template.clone();
            stack.setAmount(size);
            for (ItemStack leftover : target.getInventory().addItem(stack).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
            remaining -= size;
        }
    }

    /**
     * Marks a node owned without charging for it. Leveled nodes go straight
     * to max - half a Luck skill isn't a useful thing to hand out for
     * testing.
     */
    void grantNode(PlayerData data, SkillNode node) {
        if (node.isLeveled()) {
            data.setNodeLevel(node.getId(), node.getMaxLevel());
        }
        data.getUnlockedNodes().add(node.getId());
        // Luck, Speed and every other magnitude are read back out of the
        // node levels, so setting the level IS granting the stat. Only the
        // one-way switches need anything applied.
        plugin.getSkillTreeManager().applySideEffects(data, node);
    }

    RollableItem randomItemOf(Rarity rarity) {
        List<RollableItem> pool = new ArrayList<>();
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            if (item.getRarity() == rarity) pool.add(item);
        }
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    Rarity parseRarity(CommandSender sender, String raw) {
        try {
            return Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Unknown rarity '" + raw + "'.");
            return null;
        }
    }

    Long parseAmount(CommandSender sender, String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                sender.sendMessage(ChatColor.RED + "Amount must be positive.");
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
            return null;
        }
    }

    static int parseRank(String raw) {
        try {
            int rank = Integer.parseInt(raw);
            return rank >= 1 && rank <= 10 ? rank : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    Player resolve(CommandSender sender, String name) {
        if (name != null) {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + name + "' not found or offline.");
            }
            return target;
        }
        if (sender instanceof Player self) return self;
        sender.sendMessage(ChatColor.RED + "Name a player - the console isn't one.");
        return null;
    }

    /** One row of /rngadmin help or of a subcommand's own usage list. */
    static void line(CommandSender sender, String sub, String args, String description) {
        sender.sendMessage(ChatColor.YELLOW + "/rngadmin " + sub + " " + ChatColor.GRAY + args
                + ChatColor.DARK_GRAY + " - " + description);
    }
}
