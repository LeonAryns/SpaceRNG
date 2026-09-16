package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rank.RankManager;
import com.spacerng.solrng.rank.RankTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /keyall hands every online player the keys the rank pays for, once every
 * cooldown. The whole point is that a rank makes the server happy, not just
 * the buyer, so it is loud and it names who started it.
 */
public class KeyAllCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public KeyAllCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can start a key all.");
            return true;
        }
        RankManager ranks = plugin.getRankManager();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        RankTier tier = ranks.rankOf(data);
        if (tier == null || !tier.hasKeyall()) {
            player.sendMessage(ChatColor.RED + "Comet and up can start a key all. See /ranks.");
            return true;
        }
        long left = ranks.keyallLeft(data);
        if (left > 0) {
            player.sendMessage(ChatColor.RED + "Your key all is ready in "
                    + ChatColor.YELLOW + RankManager.timeLeft(left) + ChatColor.RED + ".");
            return true;
        }
        var crate = plugin.getCrateManager().get(tier.keyallCrate());
        var key = crate == null ? null : plugin.getConsumableManager().get(crate.keyId());
        if (crate == null || key == null) {
            player.sendMessage(ChatColor.RED + "That crate is not set up. Tell Leon.");
            return true;
        }

        data.setKeyallAt(System.currentTimeMillis());
        int given = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.getConsumableManager().give(online, key, tier.keyallAmount());
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            given++;
        }
        Bukkit.broadcastMessage(Lore.gradient("KEY ALL", true, "#FFE082", "#FF8F00") + ChatColor.DARK_GRAY + " » "
                + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " gave everyone "
                + ChatColor.WHITE + tier.keyallAmount() + "x " + plugin.getCrateManager().keyName(crate)
                + ChatColor.GRAY + ".");
        Bukkit.broadcastMessage(ChatColor.DARK_GRAY + "   Thanks to their " + ranks.styled(tier)
                + ChatColor.DARK_GRAY + " rank.");
        player.sendMessage(ChatColor.GREEN + "Key all sent to " + given + " players. Next one in "
                + RankManager.timeLeft(ranks.keyallCooldownMillis()) + ".");
        return true;
    }
}
