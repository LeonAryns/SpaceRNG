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

/** Admin tools that change a player: currencies, drops, skills, crops, tiers, boosts and resets. */
final class PlayerAdmin extends AdminTools {

    PlayerAdmin(SolRNGPlugin plugin) {
        super(plugin);
    }

    /** /rngadmin starforge [tier] [player] */
    boolean doStarforge(CommandSender sender, String[] args) {
        String tierId = null;
        String playerName = null;
        if (args.length >= 2) {
            // Second arg is a tier if it names one, otherwise a player.
            if (plugin.getStarforgeManager().get(args[1].toUpperCase(Locale.ROOT)) != null) {
                tierId = args[1].toUpperCase(Locale.ROOT);
                if (args.length >= 3) playerName = args[2];
            } else {
                playerName = args[1];
            }
        }

        Player target = resolve(sender, playerName);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        StarforgeTier tier = tierId != null
                ? plugin.getStarforgeManager().get(tierId)
                : plugin.getStarforgeManager().tierOf(data);
        if (tier == null) {
            sender.sendMessage(ChatColor.RED + "No such Starforge tier.");
            return true;
        }

        // Naming a tier explicitly also grants it, so the Luck matches the
        // item you were just handed.
        if (tierId != null) {
            data.setStarforgeTier(tier.getId());
        }
        target.getInventory().addItem(plugin.getStarforgeManager().create(tier));
        target.sendMessage(ChatColor.GREEN + "You received " + tier.styledDisplay() + ChatColor.GREEN + ".");
        if (!target.equals(sender)) {
            sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " "
                    + tier.styledDisplay() + ChatColor.GREEN + ".");
        }
        return true;
    }

    /** /rngadmin reset &lt;player&gt; confirm */
    boolean doReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin reset <player> confirm");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or offline.");
            return true;
        }
        // Destructive and unrecoverable, so it takes an explicit second step.
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ChatColor.RED + "This wipes " + target.getName()
                    + "'s levels, prestige, index, skills, armor, drops, Starforge,"
                    + " Coins, Gems, Credits, Money, playtime, crops farmed"
                    + " and their whole inventory.");
            sender.sendMessage(ChatColor.RED + "Run " + ChatColor.YELLOW + "/rngadmin reset "
                    + target.getName() + " confirm" + ChatColor.RED + " if you're sure.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        plugin.getRollListener().cancelRoll(uuid);
        PlayerData fresh = plugin.getPlayerDataManager().reset(uuid);

        // Coins, Gems and Credits live on PlayerData and go with it. Money
        // does not: it lives in the economy plugin, so a reset that only
        // touched the save file left the richest thing about the account
        // untouched.
        var economyReg = Bukkit.getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyReg != null) {
            var economy = economyReg.getProvider();
            double balance = economy.getBalance(target);
            if (balance > 0) economy.withdrawPlayer(target, balance);
        }

        // Playtime is a vanilla statistic, so it lives on the player and
        // not in the save file the reset just deleted.
        try {
            target.setStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE, 0);
        } catch (Exception ignored) {
            // Some server setups refuse statistic writes. Not worth failing
            // the whole reset over.
        }
        // The leaderboard keeps its own copy of the numbers, and a wiped
        // account still sitting at the top of a board is worse than useless.
        plugin.getLeaderboardManager().forget(uuid);

        target.getInventory().clear();
        target.getEnderChest().clear();

        // Put the live state back in sync with the wiped data.
        plugin.getTagManager().clearTag(target, fresh);
        StarforgeTier basic = plugin.getStarforgeManager().tierOf(fresh);
        if (basic != null) {
            plugin.getStarforgeManager().replaceHeldStarforge(target, basic);
        }
        target.getInventory().addItem(
                com.spacerng.solrng.roll.RollItemFactory.create(plugin, 1));
        plugin.getScoreboardManager().update(target);

        target.sendMessage(ChatColor.RED + "Your SpaceRNG progress has been reset.");
        sender.sendMessage(ChatColor.GREEN + "Reset " + target.getName() + " to a new account.");
        return true;
    }

    /** /rngadmin give &lt;currency&gt; &lt;amount&gt; [player] */
    boolean doGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin give <money|coins|gems|credits> <amount> [player]");
            return true;
        }
        Long amount = parseAmount(sender, args[2]);
        if (amount == null) return true;

        Player target = resolve(sender, args.length >= 4 ? args[3] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        String currency = args[1].toLowerCase(Locale.ROOT);
        switch (currency) {
            // The old names still work, so anything scripted against them
              // (a web store callback, a console macro) keeps running.
            case "money" -> {
                var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
                if (registration == null) {
                    sender.sendMessage(ChatColor.RED + "No Vault economy is installed, so Money can't be given.");
                    return true;
                }
                registration.getProvider().depositPlayer(target, amount);
            }
            case "coins", "tokens" -> data.addTokens(amount);
            case "gems", "shards" -> data.addShards(amount);
            case "credits" -> data.addPoints(amount);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown currency. Use money, coins, gems or credits.");
                return true;
            }
        }

        plugin.getScoreboardManager().update(target);
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " "
                + String.format("%,d", amount) + " " + currency + ".");
        return true;
    }

    /**
     * /rngadmin drops - real items in the inventory.
     * /rngadmin bank  - stored drops in the /convert bank.
     */
    boolean doDrops(CommandSender sender, String[] args, boolean toBank) {
        String verb = toBank ? "bank" : "drops";
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin " + verb + " <rarity|all> <amount> [player]");
            return true;
        }
        Long amount = parseAmount(sender, args[2]);
        if (amount == null) return true;

        Player target = resolve(sender, args.length >= 4 ? args[3] : null);
        if (target == null) return true;

        List<Rarity> rarities = new ArrayList<>();
        if (args[1].equalsIgnoreCase("all")) {
            rarities.addAll(List.of(Rarity.values()));
        } else {
            Rarity rarity = parseRarity(sender, args[1]);
            if (rarity == null) return true;
            rarities.add(rarity);
        }

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        for (Rarity rarity : rarities) {
            if (toBank) {
                data.addBankedDrops(rarity, amount);
                continue;
            }
            RollableItem item = randomItemOf(rarity);
            if (item == null) {
                sender.sendMessage(ChatColor.RED + "No items configured for " + rarity.displayName() + ".");
                continue;
            }
            giveStacks(target, plugin.getRollListener().buildTaggedItem(item), amount);
        }

        plugin.getScoreboardManager().update(target);
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + String.format("%,d", amount)
                + (toBank ? " stored " : " ") + "drop(s) of " + args[1].toLowerCase(Locale.ROOT) + ".");
        return true;
    }

    boolean doUnlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin unlock <node|all> [player]");
            return true;
        }
        Player target = resolve(sender, args.length >= 3 ? args[2] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        int granted = 0;

        if (args[1].equalsIgnoreCase("all")) {
            for (SkillNode node : plugin.getSkillTreeManager().getNodes().values()) {
                grantNode(data, node);
                granted++;
            }
        } else {
            SkillNode node = plugin.getSkillTreeManager().get(args[1]);
            if (node == null) {
                sender.sendMessage(ChatColor.RED + "No skill node with that id.");
                return true;
            }
            grantNode(data, node);
            granted = 1;
        }

        plugin.getScoreboardManager().update(target);
        sender.sendMessage(ChatColor.GREEN + "Unlocked " + granted + " node(s) for " + target.getName() + ".");
        return true;
    }

    /**
     * Hands out a redeemable. This is also the hook a crate plugin uses -
     * a crate reward is just this command with the winner's name on it.
     */
    boolean doConsumable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin consumable <id> [amount] [player]");
            sender.sendMessage(ChatColor.DARK_GRAY + "Ids: "
                    + String.join(", ", plugin.getConsumableManager().getAll().keySet()));
            return true;
        }
        var consumable = plugin.getConsumableManager().get(args[1]);
        if (consumable == null) {
            sender.sendMessage(ChatColor.RED + "No consumable with that id. Known: "
                    + String.join(", ", plugin.getConsumableManager().getAll().keySet()));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                return true;
            }
        }

        Player target = resolve(sender, args.length >= 4 ? args[3] : null);
        if (target == null) return true;

        plugin.getConsumableManager().give(target, consumable, amount);
        target.sendMessage(ChatColor.GREEN + "You received " + ChatColor.WHITE + amount + "x "
                + ChatColor.LIGHT_PURPLE + consumable.display() + ChatColor.GREEN + ".");
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + amount + "x "
                + consumable.display() + ".");
        return true;
    }

    /**
     * A replacement Farmer's Hoe. The hoe is bound and undroppable and
     * holds no state of its own, so handing out another one costs nothing
     * - which is exactly why losing one shouldn't be a problem worth
     * solving any other way.
     */
    boolean doHoe(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        target.getInventory().addItem(plugin.getFarmingManager().createBoundHoe(data));
        target.sendMessage(ChatColor.GREEN + "Here's a Farmer's Hoe \u2014 bound to you.");
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a Farmer's Hoe.");
        return true;
    }

    /**
     * Every node in every tree, maxed, free. This is the one command you
     * want before testing a menu: with ~110 nodes across two trees,
     * buying them by hand to see what a finished tree looks like isn't a
     * realistic thing to ask.
     */
    boolean doUnlockAll(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        int granted = 0;
        for (SkillNode node : plugin.getSkillTreeManager().getNodes().values()) {
            grantNode(data, node);
            granted++;
        }

        plugin.getScoreboardManager().update(target);
        plugin.getFarmingManager().refreshHoe(target, data);
        sender.sendMessage(ChatColor.GREEN + "Maxed " + granted + " skill node(s) for "
                + target.getName() + ".");
        sender.sendMessage(ChatColor.DARK_GRAY + "Luck is now +"
                + String.format("%.2f", plugin.getPrestigeManager().effectiveLuck(data) * 100.0)
                + "%, Speed " + Math.round(data.getEffectiveRollSpeedMultiplier() * 100) + ".");
        return true;
    }

    /**
     * The other half of the pair: strips every node so the tree can be
     * walked from the root again. Stats are derived from node levels now,
     * so clearing the levels IS clearing the stats - there's nothing left
     * behind to reset separately.
     */
    boolean doLockAll(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        int cleared = data.getUnlockedNodes().size() + data.getNodeLevels().size();
        data.getUnlockedNodes().clear();
        data.getNodeLevels().clear();
        // These three are the side effects a purchase writes directly, so
        // they're the only ones that need undoing by hand.
        data.setAutoRollEnabled(false);
        data.setFarmTokenMultiplier(1.0);
        data.setCropShardsUnlocked(false);
        data.setSkillSpeedBonus(0.0);

        plugin.getScoreboardManager().update(target);
        sender.sendMessage(ChatColor.GREEN + "Cleared " + cleared + " skill entr(ies) for "
                + target.getName() + ".");
        return true;
    }

    /** /rngadmin crops &lt;unlock|lock&gt; &lt;crop|shards|all&gt; [player] */
    boolean doCrops(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin crops <unlock|lock> <crop|gems|all> [player]");
            return true;
        }
        boolean unlock = args[1].equalsIgnoreCase("unlock");
        if (!unlock && !args[1].equalsIgnoreCase("lock")) {
            sender.sendMessage(ChatColor.RED + "Use unlock or lock.");
            return true;
        }

        Player target = resolve(sender, args.length >= 4 ? args[3] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        var farm = plugin.getFarmPlotManager();
        String what = args[2].toLowerCase(Locale.ROOT);

        if (what.equals("gems") || what.equals("shards")) {
            data.setCropShardsUnlocked(unlock);
        } else if (what.equals("all")) {
            data.setCropShardsUnlocked(unlock);
            for (String cropId : farm.getCrops().keySet()) {
                if (unlock) data.getUnlockedCrops().add(cropId);
                else data.getUnlockedCrops().remove(cropId);
            }
        } else {
            var crop = farm.getCrop(what);
            if (crop == null) {
                sender.sendMessage(ChatColor.RED + "No crop with that id.");
                return true;
            }
            if (unlock) data.getUnlockedCrops().add(crop.getId());
            else data.getUnlockedCrops().remove(crop.getId());
        }

        farm.render(target);
        sender.sendMessage(ChatColor.GREEN + (unlock ? "Unlocked " : "Locked ") + what + " for " + target.getName() + ".");
        return true;
    }

    /** /rngadmin milestones &lt;check|reset&gt; [player] */
    boolean doMilestones(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin milestones <check|reset> [player]");
            return true;
        }
        Player target = resolve(sender, args.length >= 3 ? args[2] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        if (args[1].equalsIgnoreCase("reset")) {
            // Clears the claim ledger only - progress itself is derived, so
            // every already-earned tier re-announces on the next check.
            data.getClaimedMilestones().clear();
            sender.sendMessage(ChatColor.GREEN + "Cleared claimed milestones for " + target.getName() + ".");
            return true;
        }

        plugin.getMilestoneManager().check(target);
        for (var track : plugin.getMilestoneManager().getTracks().values()) {
            long progress = plugin.getMilestoneManager().progress(target, data, track.getId());
            sender.sendMessage(ChatColor.GRAY + track.getDisplay() + ": " + ChatColor.WHITE
                    + String.format("%,d", progress) + ChatColor.GRAY + " " + track.getUnit()
                    + ChatColor.DARK_GRAY + " (" + track.completedCount(progress) + "/"
                    + track.getTiers().size() + " tiers)");
        }
        return true;
    }

    /** /rngadmin boost &lt;level&gt; [minutes] - force the global boost on for testing. */
    boolean doBoost(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GRAY + "Boost: " + ChatColor.LIGHT_PURPLE
                    + com.spacerng.solrng.boost.BoostManager.formatMultiplier(plugin.getBoostManager().multiplier())
                    + ChatColor.GRAY + ", " + ChatColor.WHITE + plugin.getBoostManager().timeLeftText()
                    + ChatColor.GRAY + " left. Usage: /rngadmin boost <level> [minutes]");
            return true;
        }
        Long level = parseAmount(sender, args[1]);
        if (level == null) return true;

        int minutes = 15;
        if (args.length >= 3) {
            Long parsed = parseAmount(sender, args[2]);
            if (parsed == null) return true;
            minutes = (int) Math.max(1L, parsed);
        }

        plugin.getBoostManager().force((int) Math.min(level, plugin.getBoostManager().getMaxLevel()), minutes,
                sender.getName());
        plugin.getLuckBarManager().updateAll();
        sender.sendMessage(ChatColor.GREEN + "Boost set to "
                + com.spacerng.solrng.boost.BoostManager.formatMultiplier(plugin.getBoostManager().multiplier())
                + " for " + minutes + " minute(s).");
        return true;
    }

    /** /rngadmin nova &lt;tier&gt; [player] */
    boolean doNova(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin nova <tier> [player]");
            return true;
        }
        Long tier = parseAmount(sender, args[1]);
        if (tier == null) return true;

        Player target = resolve(sender, args.length >= 3 ? args[2] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        int clamped = (int) Math.min(tier, plugin.getNovaCoreManager().getMaxTier());
        data.setNovaTier(clamped);
        if (clamped > data.getNovaBestTier()) data.setNovaBestTier(clamped);
        plugin.getLuckBarManager().update(target);
        plugin.getScoreboardManager().update(target);
        sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + " to Nova Core tier " + clamped + " ("
                + String.format("%.2f", plugin.getNovaCoreManager().multiplierAt(clamped)) + "x Luck).");
        return true;
    }
}
