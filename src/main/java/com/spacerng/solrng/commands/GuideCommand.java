package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.quest.Quest;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /guide — the whole starting run in chat, so a player can see where the
 * boss bar is taking them rather than only the next step.
 */
public class GuideCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public GuideCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have a guide.");
            return true;
        }

        // Catch anything already done before showing the list.
        plugin.getQuestManager().check(player);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var quests = plugin.getQuestManager().getQuests();
        Quest current = plugin.getQuestManager().current(player, data);

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "SPACERNG STARTING GUIDE "
                + ChatColor.DARK_GRAY + plugin.getQuestManager().completedCount(data) + "/" + quests.size());
        player.sendMessage("");

        for (Quest quest : quests) {
            boolean done = data.hasCompletedQuest(quest.getId());
            boolean active = current != null && current.getId().equals(quest.getId());

            String prefix = done ? ChatColor.GREEN + "✔ "
                    : active ? ChatColor.YELLOW + "" + ChatColor.BOLD + "▶ "
                    : ChatColor.DARK_GRAY + "• ";
            String name = done ? ChatColor.DARK_GRAY + quest.getDisplay()
                    : active ? ChatColor.WHITE + quest.getDisplay()
                    : ChatColor.GRAY + quest.getDisplay();

            String progress = "";
            if (active && quest.getAmount() > 1) {
                progress = ChatColor.DARK_GRAY + "  ("
                        + Math.min(plugin.getQuestManager().progress(player, data, quest), quest.getAmount())
                        + "/" + quest.getAmount() + ")";
            }
            player.sendMessage(prefix + name + progress);

            // Only the step you're on gets its how-to, so the list stays
            // readable instead of turning into a wall of instructions.
            if (active && !quest.getHint().isEmpty()) {
                player.sendMessage(ChatColor.DARK_GRAY + "    " + quest.getHint());
            }
        }

        if (current == null) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Guide complete — you've seen it all.");
        }
        player.sendMessage("");
        return true;
    }
}
