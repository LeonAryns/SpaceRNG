package com.spacerng.solrng.commands;

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

/**
 * The operator toolbox. Everything here exists so a live server can be
 * inspected and stress-tested without editing configs or grinding: hand
 * out every currency, spawn drops of any rarity, force a roll of a chosen
 * rarity, replay the Epic+ reveal aura on demand, wipe an account back to
 * new, and print what the odds table actually resolves to.
 */
public class RngAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "reload", "setspawn", "starforge", "reset", "give", "drops",
            "bank", "aura", "roll", "unlock", "unlockall", "lockall", "odds", "farmblock", "farmscan",
            "hoe", "consumable", "gradient", "welcome", "crops", "farmclear",
            "milestones", "farmfill", "boost", "nova", "placeholders", "payout", "crate", "tophead", "help");
    private static final List<String> CURRENCIES = List.of("money", "coins", "gems", "credits");

    private final SolRNGPlugin plugin;
    private final Random random = new Random();

    public RngAdminCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("solrng.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> doReload(sender);
            case "setspawn" -> doSetSpawn(sender);
            case "starforge" -> doStarforge(sender, args);
            case "reset" -> doReset(sender, args);
            case "give" -> doGive(sender, args);
            case "drops" -> doDrops(sender, args, false);
            case "bank" -> doDrops(sender, args, true);
            case "aura" -> doAura(sender, args);
            case "roll" -> doRoll(sender, args);
            case "unlock" -> doUnlock(sender, args);
            case "unlockall" -> doUnlockAll(sender, args);
            case "hoe" -> doHoe(sender, args);
            case "consumable" -> doConsumable(sender, args);
            case "gradient" -> doGradient(sender, args);
            case "welcome" -> doWelcome(sender, args);
            case "farmscan" -> doFarmScan(sender, args);
            case "farmclear" -> doFarmClear(sender, args);
            case "lockall" -> doLockAll(sender, args);
            case "odds" -> doOdds(sender, args);
            case "farmblock" -> doFarmBlock(sender, args);
            case "crops" -> doCrops(sender, args);
            case "milestones" -> doMilestones(sender, args);
            case "farmfill" -> doFarmFill(sender, args);
            case "boost" -> doBoost(sender, args);
            case "nova" -> doNova(sender, args);
            case "placeholders" -> doPlaceholders(sender);
            case "payout" -> doPayout(sender);
            case "crate" -> doCrate(sender, args);
            case "tophead" -> doTopHead(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    // ------------------------------------------------------------------ help

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "SolRNG admin");
        line(sender, "reload", "", "Reload config.yml");
        line(sender, "setspawn", "", "Set the join/spawn point to where you stand");
        line(sender, "starforge", "[tier] [player]", "Give a Starforge (defaults to the tier they own)");
        line(sender, "reset", "<player> confirm", "Wipe a player back to a brand-new account");
        line(sender, "give", "<money|coins|gems|credits> <amount> [player]", "Top up a currency");
        line(sender, "drops", "<rarity|all> <amount> [player]", "Physical rolled drops in the inventory");
        line(sender, "bank", "<rarity|all> <amount> [player]", "Stored drops (the /convert bank)");
        line(sender, "aura", "<epic|legendary|mythical|divine> [player]", "Replay the full reveal build-up + burst");
        line(sender, "roll", "<rarity> [player]", "Force a real roll result of that rarity");
        line(sender, "unlock", "<node|all> [player]", "Grant one skill tree node");
        line(sender, "unlockall", "[player]", "Max out every skill in every tree");
        line(sender, "hoe", "[player]", "Hand out a bound Farmer's Hoe");
        line(sender, "consumable", "<id> [amount] [player]", "Hand out a potion, charge or grant");
        line(sender, "gradient", "<#hex,#hex,...> <text>", "Build a gradient for Citizens / DecentHolograms");
        line(sender, "welcome", "[player]", "Replay the join banner");
        line(sender, "farmscan", "[radius] [legacy]", "Re-register farm plots by scanning the world");
        line(sender, "farmclear", "confirm", "Remove every farm plot, everywhere");
        line(sender, "lockall", "[player]", "Wipe every skill, to test the tree from scratch");
        line(sender, "odds", "[rarity]", "Label vs. true odds, and each tier's real share");
        line(sender, "farmblock", "[amount]", "Farm Plot blocks - place to build the shared farm");
        line(sender, "crops", "<unlock|lock> <crop|gems|all> [player]", "Grant or revoke crops");
        line(sender, "milestones", "<check|reset> [player]", "Force a check, or wipe claimed tiers");
        line(sender, "farmfill", "<radius> [confirm]", "Fill a square of farm plots around you");
        line(sender, "boost", "<level> [minutes]", "Force the global Luck boost on");
        line(sender, "nova", "<tier> [player]", "Set a Nova Core tier");
        line(sender, "placeholders", "", "What every %spacerng_% placeholder resolves to right now");
        line(sender, "payout", "", "Run the farming payout now and reset the period");
        line(sender, "crate", "<set|remove|list|key|preview>", "Place crates and hand out keys");
        line(sender, "tophead", "<set|podium|remove|clear|list>", "Floating heads for a leaderboard");
    }

    private void line(CommandSender sender, String sub, String args, String description) {
        sender.sendMessage(ChatColor.YELLOW + "/rngadmin " + sub + " " + ChatColor.GRAY + args
                + ChatColor.DARK_GRAY + " - " + description);
    }

    // ------------------------------------------------------------ basic ops

    private boolean doReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(ChatColor.GREEN + "SolRNG config reloaded.");
        return true;
    }

    private boolean doSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can set spawn.");
            return true;
        }
        plugin.getSpawnManager().setSpawn(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Spawn set. Every player now teleports here on join.");
        return true;
    }

    /** /rngadmin starforge [tier] [player] */
    private boolean doStarforge(CommandSender sender, String[] args) {
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
    private boolean doReset(CommandSender sender, String[] args) {
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
                com.spacerng.solrng.item.RollItemFactory.create(plugin, 1));
        plugin.getScoreboardManager().update(target);

        target.sendMessage(ChatColor.RED + "Your SpaceRNG progress has been reset.");
        sender.sendMessage(ChatColor.GREEN + "Reset " + target.getName() + " to a new account.");
        return true;
    }

    // ----------------------------------------------------------- currencies

    /** /rngadmin give &lt;currency&gt; &lt;amount&gt; [player] */
    private boolean doGive(CommandSender sender, String[] args) {
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

    // ---------------------------------------------------------------- drops

    /**
     * /rngadmin drops - real items in the inventory.
     * /rngadmin bank  - stored drops in the /convert bank.
     */
    private boolean doDrops(CommandSender sender, String[] args, boolean toBank) {
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

    /** Splits a bulk amount into stack-sized chunks, overflowing to the ground. */
    private void giveStacks(Player target, ItemStack template, long amount) {
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

    // ----------------------------------------------------------------- aura

    /**
     * Replays the rarity's real build-up at its real length (Epic 3s,
     * Legendary 5s, Mythical 10s) and then the burst, so the effect can be
     * judged without waiting for one to land.
     */
    private boolean doAura(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin aura <epic|legendary|mythical|divine> [player]");
            return true;
        }
        Rarity rarity = parseRarity(sender, args[1]);
        if (rarity == null) return true;
        if (!RollAura.isBigDrop(rarity)) {
            sender.sendMessage(ChatColor.RED + "Only Epic and above have a reveal aura.");
            return true;
        }

        Player target = resolve(sender, args.length >= 3 ? args[2] : null);
        if (target == null) return true;

        RollAura aura = RollAura.start(plugin, target, rarity);
        if (aura == null) return true;

        // Reveal exactly when the build-up finishes, same as a real roll.
        long duration = RollAura.durationTicks(rarity);
        plugin.getServer().getScheduler().runTaskLater(plugin, aura::reveal, duration);

        sender.sendMessage(ChatColor.GREEN + "Playing the " + rarity.displayName() + " reveal aura on "
                + target.getName() + ChatColor.GRAY + " (" + String.format("%.0f", duration / 20.0) + "s).");
        return true;
    }

    // ----------------------------------------------------------------- roll

    /** A genuine roll result, forced to a tier. */
    private boolean doRoll(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin roll <rarity> [player]");
            return true;
        }
        Rarity rarity = parseRarity(sender, args[1]);
        if (rarity == null) return true;

        Player target = resolve(sender, args.length >= 3 ? args[2] : null);
        if (target == null) return true;

        RollableItem item = randomItemOf(rarity);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "No items configured for " + rarity.displayName() + ".");
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        // Goes through the real grant path, so discovery, Money, the chat
        // line and the broadcast all fire exactly as they would in play.
        plugin.getRollListener().grantRoll(target, data, item, false);
        RollAura burst = RollAura.start(plugin, target, rarity);
        if (burst != null) burst.reveal();
        plugin.getScoreboardManager().update(target);
        return true;
    }

    // --------------------------------------------------------------- unlock

    private boolean doUnlock(CommandSender sender, String[] args) {
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

    /** Replays the join banner, for tuning it without rejoining. */
    private boolean doWelcome(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;
        plugin.getWelcomeManager().send(target);
        sender.sendMessage(ChatColor.GREEN + "Replayed the welcome for " + target.getName() + ".");
        return true;
    }

    /**
     * Wipes the field.
     *
     * Destructive and unrecoverable except by rebuilding, so it takes the
     * word confirm. The blocks go too, not just the registry: leaving the
     * markers behind would mean farmscan puts every plot straight back.
     */
    private boolean doFarmClear(CommandSender sender, String[] args) {
        var farm = plugin.getFarmPlotManager();
        int count = farm.plotCount();
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(ChatColor.RED + "This removes all " + count + " farm plots.");
            sender.sendMessage(ChatColor.RED + "Run " + ChatColor.YELLOW
                    + "/rngadmin farmclear confirm" + ChatColor.RED + " if you're sure.");
            return true;
        }
        int removed = farm.clearAll();
        sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " farm plots.");
        return true;
    }

    /**
     * Prints a gradient as colour codes, ready to paste into another
     * plugin.
     *
     * Citizens and DecentHolograms both accept "&#RRGGBB" and neither has
     * a gradient tag, so the only way to get one into an NPC name or a
     * hologram line is a code per character. This writes it, shows what it
     * will look like, and puts it in the chat box to copy - which beats
     * counting characters by hand.
     */
    private boolean doGradient(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin gradient <#hex,#hex,...> <text>");
            sender.sendMessage(ChatColor.DARK_GRAY + "e.g. /rngadmin gradient #FFD54F,#FF8F00 Armorer");
            return true;
        }

        String[] stops = args[1].split(",");
        for (int i = 0; i < stops.length; i++) {
            String stop = stops[i].trim();
            if (!stop.startsWith("#")) stop = "#" + stop;
            if (!stop.matches("#[0-9A-Fa-f]{6}")) {
                sender.sendMessage(ChatColor.RED + "'" + stops[i] + "' isn't a #RRGGBB colour.");
                return true;
            }
            stops[i] = stop.toUpperCase(Locale.ROOT);
        }

        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        String codes = com.spacerng.solrng.gui.Lore.gradientCodes(text, stops);

        sender.sendMessage("");
        sender.sendMessage(com.spacerng.solrng.gui.Lore.header("Gradient"));
        sender.sendMessage(ChatColor.GRAY + "Preview: "
                + com.spacerng.solrng.gui.Lore.gradient(text, stops));
        sender.sendMessage(ChatColor.GRAY + "Stops:   " + ChatColor.WHITE + String.join(" -> ", stops));

        if (sender instanceof Player player) {
            net.kyori.adventure.text.Component line = net.kyori.adventure.text.Component
                    .text(codes, net.kyori.adventure.text.format.NamedTextColor.WHITE)
                    .hoverEvent(net.kyori.adventure.text.Component
                            .text("Click to put it in your chat box, then copy it."))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(codes));
            player.sendMessage(ChatColor.GRAY + "Click to copy:");
            player.sendMessage(line);
        } else {
            sender.sendMessage(codes);
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "Paste into /npc rename, a DecentHolograms line, or TAB.");
        sender.sendMessage("");
        return true;
    }

    /**
     * Hands out a redeemable. This is also the hook a crate plugin uses -
     * a crate reward is just this command with the winner's name on it.
     */
    private boolean doConsumable(CommandSender sender, String[] args) {
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
    private boolean doHoe(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        target.getInventory().addItem(plugin.getFarmingManager().createBoundHoe(data));
        target.sendMessage(ChatColor.GREEN + "Here's a Farmer's Hoe \u2014 bound to you.");
        sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a Farmer's Hoe.");
        return true;
    }

    /**
     * Rebuilds the farm plot registry from the blocks actually in the
     * world. farmplots.yml is a cache; the field itself is the record, so
     * a lost data folder costs this one command.
     */
    private boolean doFarmScan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Run this in-game, standing near the farm.");
            return true;
        }
        int radius = 48;
        if (args.length >= 2) {
            try {
                radius = Math.max(1, Math.min(128, Integer.parseInt(args[1])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Radius must be a number.");
                return true;
            }
        }
        boolean legacy = args.length >= 3 && args[2].equalsIgnoreCase("legacy");

        int before = plugin.getFarmPlotManager().plotCount();
        int found = plugin.getFarmPlotManager().scan(player.getLocation(), radius, legacy);
        plugin.getFarmPlotManager().renderAll();

        sender.sendMessage(ChatColor.GREEN + "Scanned " + radius + " blocks around you: "
                + ChatColor.WHITE + found + ChatColor.GREEN + " new plot(s), "
                + ChatColor.WHITE + (before + found) + ChatColor.GREEN + " total.");
        if (!legacy) {
            sender.sendMessage(ChatColor.DARK_GRAY + "Add \"legacy\" to also pick up old wheat plots "
                    + "(and any real wheat in range).");
        }
        return true;
    }

    /**
     * Every node in every tree, maxed, free. This is the one command you
     * want before testing a menu: with ~110 nodes across two trees,
     * buying them by hand to see what a finished tree looks like isn't a
     * realistic thing to ask.
     */
    private boolean doUnlockAll(CommandSender sender, String[] args) {
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
    private boolean doLockAll(CommandSender sender, String[] args) {
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

    /**
     * Marks a node owned without charging for it. Leveled nodes go straight
     * to max - half a Luck skill isn't a useful thing to hand out for
     * testing.
     */
    private void grantNode(PlayerData data, SkillNode node) {
        if (node.isLeveled()) {
            data.setNodeLevel(node.getId(), node.getMaxLevel());
        }
        data.getUnlockedNodes().add(node.getId());
        // Luck, Speed and every other magnitude are read back out of the
        // node levels, so setting the level IS granting the stat. Only the
        // one-way switches need anything applied.
        plugin.getSkillTreeManager().applySideEffects(data, node);
    }

    // ----------------------------------------------------------------- odds

    /**
     * The diagnostic behind "is 1 in 250 really 1 in 250?". Odds in
     * config.yml are relative WEIGHTS: an item's real chance is its own
     * 1/odds divided by the sum of every item's 1/odds. That sum only
     * equals 1.0 if the table was authored to add up, so this prints the
     * factor everything is off by, plus each tier's true share.
     */
    private boolean doOdds(CommandSender sender, String[] args) {
        List<RollableItem> items = plugin.getRarityManager().getItems();
        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No items are configured.");
            return true;
        }

        double weightSum = 0.0;
        for (RollableItem item : items) {
            weightSum += item.getRollWeight();
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Odds check "
                + ChatColor.GRAY + "(" + items.size() + " items)");
        sender.sendMessage(ChatColor.GRAY + "Total roll weight: " + ChatColor.YELLOW
                + String.format("%.4f", weightSum) + ChatColor.GRAY + "  (1.0000 is expected)");
        sender.sendMessage(ChatColor.GRAY + "Rarities marked true-odds roll at their label."
                + " The rest split what is left by share.");

        Rarity filter = args.length >= 2 ? parseRarity(sender, args[1]) : null;
        if (args.length >= 2 && filter == null) return true;

        for (Rarity rarity : Rarity.values()) {
            double share = 0.0;
            int count = 0;
            for (RollableItem item : items) {
                if (item.getRarity() != rarity) continue;
                share += item.getRollWeight();
                count++;
            }
            if (count == 0) continue;
            sender.sendMessage(plugin.getRarityManager().style(rarity, rarity.displayName())
                    + ChatColor.DARK_GRAY + " x" + count + ChatColor.GRAY + " - "
                    + ChatColor.WHITE + String.format("%.4f%%", 100.0 * share / weightSum)
                    + ChatColor.GRAY + " of rolls");
        }

        if (filter != null) {
            sender.sendMessage(ChatColor.GRAY + "Label -> true odds:");
            for (RollableItem item : items) {
                if (item.getRarity() != filter) continue;
                long trueOdds = Math.round(weightSum / Math.max(1e-18, item.getRollWeight()));
                sender.sendMessage(ChatColor.DARK_GRAY + " - " + RollFormat.displayName(plugin, item)
                        + ChatColor.GRAY + "  " + RollFormat.chance(item.getOdds())
                        + ChatColor.DARK_GRAY + " -> " + ChatColor.WHITE + RollFormat.chance(trueOdds));
            }
        }
        return true;
    }

    // ----------------------------------------------------------- the farm

    /**
     * Hands over Farm Plot blocks. Place one to add a tile to the shared
     * field; sneak-break one to remove it again.
     */
    private boolean doFarmBlock(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 3 ? args[2] : null);
        if (target == null) return true;

        int amount = 16;
        if (args.length >= 2) {
            Long parsed = parseAmount(sender, args[1]);
            if (parsed == null) return true;
            amount = (int) Math.min(64L, parsed);
        }

        target.getInventory().addItem(plugin.getFarmPlotManager().createPlotItem(amount));
        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " Farm Plot block(s). "
                + ChatColor.GRAY + "Place to add, sneak-break to remove. "
                + plugin.getFarmPlotManager().plotCount() + " placed so far.");
        return true;
    }

    /** /rngadmin crops &lt;unlock|lock&gt; &lt;crop|shards|all&gt; [player] */
    private boolean doCrops(CommandSender sender, String[] args) {
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
    private boolean doMilestones(CommandSender sender, String[] args) {
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

    /**
     * Fills a square of farm plots centred on where you stand, at your own
     * feet level. Placing a field by hand is fine for a demo and miserable
     * for a real one.
     */
    private boolean doFarmFill(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Stand where you want the field.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin farmfill <radius> [confirm]");
            return true;
        }

        Long parsed = parseAmount(sender, args[1]);
        if (parsed == null) return true;
        int radius = (int) Math.min(20L, parsed);

        int side = radius * 2 + 1;
        int total = side * side;
        // Big fills rewrite a lot of blocks and are tedious to undo, so
        // anything past a modest patch takes a second confirmation.
        if (total > 121 && (args.length < 3 || !args[2].equalsIgnoreCase("confirm"))) {
            sender.sendMessage(ChatColor.RED + "That's " + side + "x" + side + " = " + total + " plots.");
            sender.sendMessage(ChatColor.RED + "Run " + ChatColor.YELLOW + "/rngadmin farmfill "
                    + radius + " confirm" + ChatColor.RED + " if you're sure.");
            return true;
        }

        var farm = plugin.getFarmPlotManager();
        var origin = player.getLocation().getBlock();
        int placed = 0;
        int skipped = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                var block = origin.getRelative(dx, 0, dz);
                if (farm.isPlot(block.getLocation())) {
                    skipped++;
                    continue;
                }
                // Only claim empty space - never overwrite someone's build.
                if (!block.getType().isAir() && !block.isReplaceable()) {
                    skipped++;
                    continue;
                }
                farm.addPlot(block);
                placed++;
            }
        }
        farm.renderAll();
        sender.sendMessage(ChatColor.GREEN + "Placed " + placed + " plot(s)"
                + (skipped > 0 ? ChatColor.GRAY + ", skipped " + skipped + " occupied block(s)" : "")
                + ChatColor.GRAY + ". " + farm.plotCount() + " total.");
        return true;
    }

    /** /rngadmin boost &lt;level&gt; [minutes] - force the global boost on for testing. */
    private boolean doBoost(CommandSender sender, String[] args) {
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
    private boolean doNova(CommandSender sender, String[] args) {
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

    /**
     * Prints what every placeholder currently resolves to for the sender.
     * This is the fast way to tell a TAB configuration problem from a
     * plugin one: if the values show up here, the data is fine and the
     * fault is in TAB's config or its PlaceholderAPI hook.
     */
    private boolean doPlaceholders(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Placeholders are per-player.");
            return true;
        }
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            sender.sendMessage(ChatColor.RED + "PlaceholderAPI isn't installed, so nothing is registered.");
            return true;
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "SolRNG placeholders");
        for (String key : List.of("tag", "tag_plain", "tag_name", "tag_odds", "tag_multiplier",
                "prestige", "prestige_roman", "prestige_badge", "level", "level_number",
                "potion_rolls", "potion_luck", "potion_speed", "potion_active", "roll_charges")) {
            String value = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%spacerng_" + key + "%");
            boolean unresolved = value.equals("%spacerng_" + key + "%");
            sender.sendMessage(ChatColor.YELLOW + "%spacerng_" + key + "%" + ChatColor.DARK_GRAY + " -> "
                    + (unresolved ? ChatColor.RED + "UNRESOLVED" : ChatColor.WHITE + "[" + value + ChatColor.WHITE + "]"));
        }
        sender.sendMessage(ChatColor.GRAY + "Empty is normal for tag placeholders with no tag equipped.");
        return true;
    }

    /** Forces the farming payout, for testing the podium without waiting. */
    private boolean doPayout(CommandSender sender) {
        plugin.getLeaderboardManager().runPayout();
        plugin.getLeaderboardManager().saveIndex();
        sender.sendMessage(ChatColor.GREEN + "Farming payout run and period reset.");
        return true;
    }

    // ---------------------------------------------------------------- utils

    private RollableItem randomItemOf(Rarity rarity) {
        List<RollableItem> pool = new ArrayList<>();
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            if (item.getRarity() == rarity) pool.add(item);
        }
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    private Rarity parseRarity(CommandSender sender, String raw) {
        try {
            return Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + "Unknown rarity '" + raw + "'.");
            return null;
        }
    }

    private Long parseAmount(CommandSender sender, String raw) {
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

    /** Named player, or the sender when no name was given. */
    // ---------------------------------------------------------------- crates

    private boolean doCrate(CommandSender sender, String[] args) {
        var crates = plugin.getCrateManager();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Stand in game and look at a block to place a crate.");
                    return true;
                }
                var crate = args.length >= 3 ? crates.get(args[2]) : null;
                if (crate == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin crate set <crate>");
                    sender.sendMessage(ChatColor.DARK_GRAY + "Crates: " + String.join(", ", crates.getAll().keySet()));
                    return true;
                }
                org.bukkit.block.Block block = player.getTargetBlockExact(6);
                if (block == null || block.getType().isAir()) {
                    sender.sendMessage(ChatColor.RED + "Look at the block that should become the crate.");
                    return true;
                }
                crates.place(block, crate);
                sender.sendMessage(ChatColor.GREEN + "That "
                        + block.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + " is now the " + crates.styledName(crate) + ChatColor.GREEN + ".");
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Stand in game and look at the crate.");
                    return true;
                }
                org.bukkit.block.Block block = player.getTargetBlockExact(6);
                sender.sendMessage(block != null && crates.remove(block)
                        ? ChatColor.GREEN + "Crate removed. The block itself stays."
                        : ChatColor.RED + "The block you are looking at is not a crate.");
            }
            case "list" -> {
                if (crates.placements().isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No crates placed yet.");
                }
                for (var entry : crates.placements().entrySet()) {
                    var at = entry.getKey();
                    sender.sendMessage(ChatColor.YELLOW + entry.getValue() + ChatColor.GRAY + " at "
                            + at.getWorld().getName() + " " + at.getBlockX() + ", " + at.getBlockY()
                            + ", " + at.getBlockZ());
                }
                sender.sendMessage(ChatColor.DARK_GRAY + "Types: " + String.join(", ", crates.getAll().keySet()));
            }
            case "key" -> {
                var crate = args.length >= 3 ? crates.get(args[2]) : null;
                if (crate == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin crate key <crate> [amount] [player]");
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Amount must be a number.");
                        return true;
                    }
                }
                Player target = resolve(sender, args.length >= 5 ? args[4] : null);
                if (target == null) return true;
                var key = plugin.getConsumableManager().get(crate.keyId());
                if (key == null) {
                    sender.sendMessage(ChatColor.RED + "The key '" + crate.keyId()
                            + "' is not a consumable in config.yml.");
                    return true;
                }
                plugin.getConsumableManager().give(target, key, amount);
                target.sendMessage(ChatColor.GREEN + "You received " + ChatColor.WHITE + amount + "x "
                        + crates.keyName(crate) + ChatColor.GREEN + ".");
                if (!target.equals(sender)) {
                    sender.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " " + amount + "x "
                            + crates.keyName(crate) + ChatColor.GREEN + ".");
                }
            }
            case "preview" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can open a preview.");
                    return true;
                }
                var crate = args.length >= 3 ? crates.get(args[2]) : null;
                if (crate == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin crate preview <crate>");
                    return true;
                }
                player.openInventory(com.spacerng.solrng.crate.CratePreviewGui.build(plugin, player, crate));
            }
            default -> {
                line(sender, "crate set", "<crate>", "Turn the block you are looking at into a crate");
                line(sender, "crate remove", "", "Stop the block you are looking at being a crate");
                line(sender, "crate list", "", "Every placed crate");
                line(sender, "crate key", "<crate> [amount] [player]", "Hand out keys");
                line(sender, "crate preview", "<crate>", "Open a crate's reward list");
            }
        }
        return true;
    }

    // ------------------------------------------------------------- top heads

    private boolean doTopHead(CommandSender sender, String[] args) {
        var heads = plugin.getTopHeadManager();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        String board = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "set", "podium" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Stand where the head should float.");
                    return true;
                }
                if (!com.spacerng.solrng.leaderboard.TopHeadManager.isBoard(board)) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin tophead " + action + " <board>"
                            + (action.equals("set") ? " <rank>" : ""));
                    sender.sendMessage(ChatColor.DARK_GRAY + "Boards: " + String.join(", ",
                            com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS));
                    return true;
                }
                String title = com.spacerng.solrng.leaderboard.LeaderboardManager.titleOf(board);
                if (action.equals("podium")) {
                    heads.podium(board, player.getEyeLocation());
                    sender.sendMessage(ChatColor.GREEN + "Podium for " + title + " placed. #1 floats at your eyes, "
                            + "#2 to your right and #3 to your left.");
                    return true;
                }
                int rank = parseRank(args.length >= 4 ? args[3] : "1");
                if (rank < 1) {
                    sender.sendMessage(ChatColor.RED + "Rank must be 1 to 10.");
                    return true;
                }
                heads.set(board, rank, player.getEyeLocation());
                sender.sendMessage(ChatColor.GREEN + "#" + rank + " of " + title + " now floats at your eyes.");
            }
            case "remove" -> {
                int rank = parseRank(args.length >= 4 ? args[3] : "");
                if (!com.spacerng.solrng.leaderboard.TopHeadManager.isBoard(board) || rank < 1) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin tophead remove <board> <rank>");
                    return true;
                }
                sender.sendMessage(heads.remove(board, rank)
                        ? ChatColor.GREEN + "Removed."
                        : ChatColor.RED + "There is no head for that board and rank.");
            }
            case "clear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Stand near the heads you want gone.");
                    return true;
                }
                int removed = heads.removeNear(player.getLocation(), 5.0);
                sender.sendMessage(ChatColor.GREEN + "Removed " + removed + " head" + (removed == 1 ? "" : "s")
                        + " within 5 blocks.");
            }
            case "list" -> {
                if (heads.list().isEmpty()) sender.sendMessage(ChatColor.GRAY + "No heads placed yet.");
                for (var spot : heads.list()) {
                    var at = spot.at();
                    sender.sendMessage(ChatColor.YELLOW + spot.board() + " #" + spot.rank() + ChatColor.GRAY + " at "
                            + at.getWorld().getName() + " " + at.getBlockX() + ", " + at.getBlockY()
                            + ", " + at.getBlockZ());
                }
            }
            default -> {
                line(sender, "tophead set", "<board> <rank>", "A head for one rank, at your eyes");
                line(sender, "tophead podium", "<board>", "#1, #2 and #3 around you in one go");
                line(sender, "tophead remove", "<board> <rank>", "Take one head down");
                line(sender, "tophead clear", "", "Every head within 5 blocks");
                line(sender, "tophead list", "", "Every placed head");
            }
        }
        return true;
    }

    private static int parseRank(String raw) {
        try {
            int rank = Integer.parseInt(raw);
            return rank >= 1 && rank <= 10 ? rank : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private List<String> crateTab(String[] args) {
        List<String> ids = new ArrayList<>(plugin.getCrateManager().getAll().keySet());
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        return switch (args.length) {
            case 2 -> partial(args[1], List.of("set", "remove", "list", "key", "preview"));
            case 3 -> List.of("set", "key", "preview").contains(action) ? partial(args[2], ids) : List.of();
            case 4 -> action.equals("key") ? partial(args[3], List.of("1", "5", "10")) : List.of();
            case 5 -> action.equals("key") ? partial(args[4], playerNames()) : List.of();
            default -> List.of();
        };
    }

    private List<String> topHeadTab(String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        return switch (args.length) {
            case 2 -> partial(args[1], List.of("set", "podium", "remove", "clear", "list"));
            case 3 -> List.of("set", "podium", "remove").contains(action)
                    ? partial(args[2], com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS) : List.of();
            case 4 -> List.of("set", "remove").contains(action) ? partial(args[3], List.of("1", "2", "3")) : List.of();
            default -> List.of();
        };
    }

    private Player resolve(CommandSender sender, String name) {
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

    // ------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("solrng.admin")) return List.of();

        if (args.length == 1) return partial(args[0], SUBCOMMANDS);
        if (args.length == 2 && (args[0].equalsIgnoreCase("unlockall")
                || args[0].equalsIgnoreCase("lockall") || args[0].equalsIgnoreCase("hoe"))) {
            return partial(args[1], playerNames());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("farmscan")) {
            return partial(args[1], List.of("16", "48", "96"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("farmscan")) {
            return partial(args[2], List.of("legacy"));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("crate")) return crateTab(args);
        if (sub.equals("tophead")) return topHeadTab(args);
        if (args.length == 2) {
            return switch (sub) {
                case "give" -> partial(args[1], CURRENCIES);
                case "drops", "bank" -> partial(args[1], withAll(rarityNames()));
                case "aura" -> partial(args[1], List.of("epic", "legendary", "mythical", "divine"));
                case "roll", "odds" -> partial(args[1], rarityNames());
                case "unlock" -> partial(args[1], withAll(nodeIds()));
                case "consumable" -> partial(args[1],
                        new ArrayList<>(plugin.getConsumableManager().getAll().keySet()));
                case "gradient" -> partial(args[1], List.of(
                        "#F6D6FF,#B15CFF", "#FFD54F,#FF8F00", "#B0BEC5,#78909C",
                        "#B9F6CA,#00C853", "#E1BEE7,#8E24AA"));
                case "starforge" -> partial(args[1], tierIds());
                case "reset" -> partial(args[1], playerNames());
                case "crops" -> partial(args[1], List.of("unlock", "lock"));
                case "milestones" -> partial(args[1], List.of("check", "reset"));
                case "farmblock" -> partial(args[1], List.of("1", "16", "64"));
                case "farmfill" -> partial(args[1], List.of("2", "5", "10", "20"));
                case "boost" -> partial(args[1], List.of("1", "2", "3", "4", "5"));
                case "nova" -> partial(args[1], List.of("0", "5", "10", "25"));
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "reset" -> partial(args[2], List.of("confirm"));
                case "farmfill" -> partial(args[2], List.of("confirm"));
                case "nova" -> partial(args[2], playerNames());
                case "give", "drops", "bank" -> partial(args[2], List.of("1", "10", "100", "1000"));
                case "aura", "roll", "unlock", "starforge", "milestones", "farmblock" ->
                        partial(args[2], playerNames());
                case "consumable" -> partial(args[2], List.of("1", "3", "5"));
                case "crops" -> partial(args[2], cropOptions());
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("consumable")) {
            return partial(args[3], playerNames());
        }
        if (args.length == 4 && (sub.equals("give") || sub.equals("drops") || sub.equals("bank")
                || sub.equals("crops"))) {
            return partial(args[3], playerNames());
        }
        return List.of();
    }

    private List<String> partial(String typed, List<String> options) {
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(option);
        }
        return out;
    }

    private List<String> withAll(List<String> base) {
        List<String> out = new ArrayList<>(base);
        out.add("all");
        return out;
    }

    private List<String> rarityNames() {
        List<String> out = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) out.add(rarity.name().toLowerCase(Locale.ROOT));
        return out;
    }

    private List<String> nodeIds() {
        return new ArrayList<>(plugin.getSkillTreeManager().getNodes().keySet());
    }

    private List<String> tierIds() {
        return new ArrayList<>(plugin.getStarforgeManager().getTiers().keySet());
    }

    private List<String> cropOptions() {
        List<String> out = new ArrayList<>();
        for (String id : plugin.getFarmPlotManager().getCrops().keySet()) {
            out.add(id.toLowerCase(Locale.ROOT));
        }
        out.add("gems");
        out.add("all");
        return out;
    }

    private List<String> playerNames() {
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) out.add(player.getName());
        return out;
    }
}
