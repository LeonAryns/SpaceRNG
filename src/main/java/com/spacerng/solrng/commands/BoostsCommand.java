package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.consumable.Consumable;
import com.spacerng.solrng.consumable.ConsumableManager;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /boosts — everything currently running on you, and when it stops.
 *
 * A timed multiplier the player can't check is one they can't plan
 * around, which is most of what a potion is for.
 */
public class BoostsCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public BoostsCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        boolean any = false;

        player.sendMessage("");
        player.sendMessage(Lore.header("Active Boosts"));

        for (Consumable.Effect effect : Consumable.Effect.values()) {
            if (effect.instant() || effect.charged()) continue;
            double multiplier = data.boostMultiplier(effect.key());
            if (multiplier <= 1.0) continue;
            any = true;
            player.sendMessage(Lore.stat(colourOf(effect), labelOf(effect),
                    ConsumableManager.trim(multiplier) + "x  "
                            + ChatColor.GRAY + timeLeft(data.boostRemainingMillis(effect.key()))));
        }

        if (data.getRollCharges() > 0) {
            any = true;
            player.sendMessage(Lore.stat(ChatColor.LIGHT_PURPLE, "Charged rolls",
                    data.getRollCharges() + " left at "
                            + ConsumableManager.trim(data.getRollChargeMultiplier()) + "x Luck"));
        }

        // The global boost isn't yours, but it's multiplying your Luck
        // right now, so leaving it out would make the numbers not add up.
        if (plugin.getBoostManager().isActive()) {
            any = true;
            player.sendMessage(Lore.stat(ChatColor.LIGHT_PURPLE, "Server boost",
                    com.spacerng.solrng.boost.BoostManager.formatMultiplier(
                            plugin.getBoostManager().multiplier())
                            + "  " + ChatColor.GRAY + plugin.getBoostManager().timeLeftText()));
        }

        if (!any) {
            player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET + " Nothing running right now.");
            player.sendMessage(ChatColor.DARK_GRAY + Lore.BULLET + " Potions come from /milestones, /pass and crates.");
        }
        player.sendMessage("");
        return true;
    }

    private String labelOf(Consumable.Effect effect) {
        return switch (effect) {
            case LUCK -> "Luck";
            case SPEED -> "Speed";
            case TOKENS -> "Coins";
            case ENCHANT_PROC -> "Enchant chance";
            default -> effect.name();
        };
    }

    private ChatColor colourOf(Consumable.Effect effect) {
        return switch (effect) {
            case LUCK -> ChatColor.GREEN;
            case SPEED -> ChatColor.YELLOW;
            case TOKENS -> ChatColor.GOLD;
            case ENCHANT_PROC -> ChatColor.LIGHT_PURPLE;
            default -> ChatColor.AQUA;
        };
    }

    /** "24m 10s" — seconds only matter once it's nearly gone. */
    private String timeLeft(long millis) {
        long seconds = millis / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long rest = seconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + rest + "s";
        return rest + "s";
    }
}
