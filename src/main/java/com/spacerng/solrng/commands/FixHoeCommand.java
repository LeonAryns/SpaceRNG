package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /fixhoe (V320): hands back a Farmer's Hoe somebody has lost.
 *
 * The enchants come back with it and that needed no work at all, which is
 * the part worth knowing. A hoe's enchants are DERIVED from the farming
 * tree every time the item is built, never stored on the item, so
 * {@code createBoundHoe(data)} already produces a hoe carrying everything
 * the owner has bought. A replacement is not a blank tool.
 *
 * It refuses anybody who has not unlocked farming, because then the hoe
 * is not lost, it is unearned. And it refuses anybody who still has one,
 * so it can never be used to stack up spares: the hoe is soulbound but
 * two of them in one inventory is still two.
 *
 * /fixhoe on its own fixes the sender. Staff can name a player, or sweep
 * every player online with "all", which is what Leon asked for: find who
 * is missing one rather than waiting to be told.
 */
public class FixHoeCommand implements CommandExecutor, TabCompleter {

    private static final String FARMING_UNLOCK_NODE = "farming_unlock";

    private final SolRNGPlugin plugin;

    public FixHoeCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        boolean staff = sender.hasPermission("solrng.admin");

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "From the console, name a player or use "
                        + ChatColor.YELLOW + "/fixhoe all" + ChatColor.RED + ".");
                return true;
            }
            fix(sender, player, true);
            return true;
        }

        if (!staff) {
            sender.sendMessage(ChatColor.RED + "You can only fix your own hoe.");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            sweep(sender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + args[0] + " is not online.");
            return true;
        }
        fix(sender, target, false);
        return true;
    }

    /**
     * Every player online who is missing one, in one pass.
     *
     * Reports the three outcomes separately rather than a single count:
     * "nobody was missing one" and "everybody got one" look the same in a
     * total, and they mean opposite things about whether there is a bug
     * still to find.
     */
    private void sweep(CommandSender sender) {
        List<String> given = new ArrayList<>();
        int had = 0;
        int unearned = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            if (data == null || !data.hasUnlocked(FARMING_UNLOCK_NODE)) {
                unearned++;
                continue;
            }
            if (plugin.getFarmingManager().hasBoundHoe(player)) {
                had++;
                continue;
            }
            handOver(player);
            given.add(player.getName());
        }

        sender.sendMessage("");
        sender.sendMessage(Lore.header("FIX HOE"));
        sender.sendMessage(ChatColor.GRAY + "Already had one: " + ChatColor.AQUA + had);
        sender.sendMessage(ChatColor.GRAY + "No farming yet: " + ChatColor.AQUA + unearned);
        sender.sendMessage(ChatColor.GRAY + "Given a hoe: " + ChatColor.GREEN + given.size()
                + (given.isEmpty() ? "" : ChatColor.DARK_GRAY + "  " + String.join(", ", given)));
        sender.sendMessage("");
    }

    private void fix(CommandSender sender, Player target, boolean self) {
        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        String who = self ? "You have" : target.getName() + " has";

        if (data == null || !data.hasUnlocked(FARMING_UNLOCK_NODE)) {
            sender.sendMessage(ChatColor.RED + who + " not unlocked farming yet.");
            if (self) {
                sender.sendMessage(ChatColor.GRAY + "Buy " + ChatColor.YELLOW + "Farming Unlocked"
                        + ChatColor.GRAY + " in " + ChatColor.YELLOW + "/skilltree"
                        + ChatColor.GRAY + " and the hoe comes with it.");
            }
            return;
        }
        if (plugin.getFarmingManager().hasBoundHoe(target)) {
            sender.sendMessage(ChatColor.YELLOW + who + " a hoe already.");
            sender.sendMessage(ChatColor.GRAY + "Check the inventory and the ender chest, "
                    + "and " + ChatColor.YELLOW + "/stash" + ChatColor.GRAY + " if it was full.");
            return;
        }

        handOver(target);
        if (self) {
            sender.sendMessage(ChatColor.GREEN + "Here is your hoe, with every enchant you own.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " their hoe back.");
        }
    }

    /**
     * Through Stash, not addItem: a player whose inventory is full is
     * exactly the player most likely to have lost the hoe in the first
     * place, and dropping the replacement on the floor would lose it
     * again.
     */
    private void handOver(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        com.spacerng.solrng.player.Stash.give(plugin, player,
                plugin.getFarmingManager().createBoundHoe(data));
        player.sendMessage(ChatColor.GREEN + "Your " + ChatColor.YELLOW + "Farmer's Hoe"
                + ChatColor.GREEN + " is back, with every enchant you have bought.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission("solrng.admin")) return List.of();
        List<String> names = new ArrayList<>();
        names.add("all");
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        String partial = args[0].toLowerCase(java.util.Locale.ROOT);
        names.removeIf(name -> !name.toLowerCase(java.util.Locale.ROOT).startsWith(partial));
        return names;
    }
}
