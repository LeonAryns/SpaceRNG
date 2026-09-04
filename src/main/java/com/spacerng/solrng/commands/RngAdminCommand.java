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
            "bank", "aura", "roll", "unlock", "odds", "help");
    private static final List<String> CURRENCIES = List.of("money", "tokens", "shards", "credits");

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
            case "odds" -> doOdds(sender, args);
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
        line(sender, "give", "<money|tokens|shards|credits> <amount> [player]", "Top up a currency");
        line(sender, "drops", "<rarity|all> <amount> [player]", "Physical rolled drops in the inventory");
        line(sender, "bank", "<rarity|all> <amount> [player]", "Stored drops (the /convert bank)");
        line(sender, "aura", "<epic|legendary|mythical> [player]", "Replay the full reveal build-up + burst");
        line(sender, "roll", "<rarity> [player]", "Force a real roll result of that rarity");
        line(sender, "unlock", "<node|all> [player]", "Grant a skill tree node");
        line(sender, "odds", "[rarity]", "Label vs. true odds, and each tier's real share");
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
                    + "'s levels, prestige, index, skills, armor, drops and Starforge.");
            sender.sendMessage(ChatColor.RED + "Run " + ChatColor.YELLOW + "/rngadmin reset "
                    + target.getName() + " confirm" + ChatColor.RED + " if you're sure.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        plugin.getRollListener().cancelRoll(uuid);
        PlayerData fresh = plugin.getPlayerDataManager().reset(uuid);

        // Put the live state back in sync with the wiped data.
        plugin.getTagManager().clearTag(target, fresh);
        StarforgeTier basic = plugin.getStarforgeManager().tierOf(fresh);
        if (basic != null) {
            plugin.getStarforgeManager().replaceHeldStarforge(target, basic);
        }
        plugin.getScoreboardManager().update(target);

        target.sendMessage(ChatColor.RED + "Your SolRNG progress has been reset.");
        sender.sendMessage(ChatColor.GREEN + "Reset " + target.getName() + " to a new account.");
        return true;
    }

    // ----------------------------------------------------------- currencies

    /** /rngadmin give &lt;currency&gt; &lt;amount&gt; [player] */
    private boolean doGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin give <money|tokens|shards|credits> <amount> [player]");
            return true;
        }
        Long amount = parseAmount(sender, args[2]);
        if (amount == null) return true;

        Player target = resolve(sender, args.length >= 4 ? args[3] : null);
        if (target == null) return true;

        PlayerData data = plugin.getPlayerDataManager().get(target.getUniqueId());
        String currency = args[1].toLowerCase(Locale.ROOT);
        switch (currency) {
            case "money" -> {
                var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
                if (registration == null) {
                    sender.sendMessage(ChatColor.RED + "No Vault economy is installed, so Money can't be given.");
                    return true;
                }
                registration.getProvider().depositPlayer(target, amount);
            }
            case "tokens" -> data.addTokens(amount);
            case "shards" -> data.addShards(amount);
            case "credits" -> data.addPoints(amount);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown currency. Use money, tokens, shards or credits.");
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
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin aura <epic|legendary|mythical> [player]");
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

    /**
     * Marks a node owned without charging for it. Leveled nodes go straight
     * to max — half a Luck skill isn't a useful thing to hand out for
     * testing.
     */
    private void grantNode(PlayerData data, SkillNode node) {
        if (node.getMaxLevel() > 1) {
            int missing = node.getMaxLevel() - data.getNodeLevel(node.getId());
            for (int i = 0; i < missing; i++) {
                applyEffect(data, node);
            }
            data.setNodeLevel(node.getId(), node.getMaxLevel());
        } else if (!data.hasUnlocked(node.getId())) {
            applyEffect(data, node);
        }
        data.getUnlockedNodes().add(node.getId());
    }

    private void applyEffect(PlayerData data, SkillNode node) {
        switch (node.getEffect()) {
            case LUCK -> data.addBonusLuck(node.getValue());
            case ROLL_SPEED -> data.setRollSpeedMultiplier(data.getRollSpeedMultiplier() + node.getValue());
            case BONUS_ROLL_CHANCE -> data.addBonusRollChance(node.getValue());
            case AUTO_ROLL -> data.setAutoRollEnabled(true);
            default -> { /* gate flags only */ }
        }
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
            weightSum += 1.0 / item.getOdds();
        }

        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Odds check "
                + ChatColor.GRAY + "(" + items.size() + " items)");
        sender.sendMessage(ChatColor.GRAY + "Sum of 1/odds: " + ChatColor.YELLOW
                + String.format("%.4f", weightSum)
                + ChatColor.GRAY + "  (1.0000 = every label is literally true)");
        sender.sendMessage(ChatColor.GRAY + "Every item is currently " + ChatColor.YELLOW
                + String.format("%.2fx", weightSum) + ChatColor.GRAY + " rarer than its label.");

        Rarity filter = args.length >= 2 ? parseRarity(sender, args[1]) : null;
        if (args.length >= 2 && filter == null) return true;

        for (Rarity rarity : Rarity.values()) {
            double share = 0.0;
            int count = 0;
            for (RollableItem item : items) {
                if (item.getRarity() != rarity) continue;
                share += 1.0 / item.getOdds();
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
                long trueOdds = Math.round(weightSum * item.getOdds());
                sender.sendMessage(ChatColor.DARK_GRAY + " - " + RollFormat.displayName(plugin, item)
                        + ChatColor.GRAY + "  " + RollFormat.chance(item.getOdds())
                        + ChatColor.DARK_GRAY + " -> " + ChatColor.WHITE + RollFormat.chance(trueOdds));
            }
        }
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

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "give" -> partial(args[1], CURRENCIES);
                case "drops", "bank" -> partial(args[1], withAll(rarityNames()));
                case "aura" -> partial(args[1], List.of("epic", "legendary", "mythical"));
                case "roll", "odds" -> partial(args[1], rarityNames());
                case "unlock" -> partial(args[1], withAll(nodeIds()));
                case "starforge" -> partial(args[1], tierIds());
                case "reset" -> partial(args[1], playerNames());
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "reset" -> partial(args[2], List.of("confirm"));
                case "give", "drops", "bank" -> partial(args[2], List.of("1", "10", "100", "1000"));
                case "aura", "roll", "unlock", "starforge" -> partial(args[2], playerNames());
                default -> List.of();
            };
        }
        if (args.length == 4 && (sub.equals("give") || sub.equals("drops") || sub.equals("bank"))) {
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

    private List<String> playerNames() {
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) out.add(player.getName());
        return out;
    }
}
