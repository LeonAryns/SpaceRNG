package com.spacerng.solrng.commands.admin;

import static com.spacerng.solrng.commands.admin.AdminTools.line;

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
            "milestones", "farmfill", "boost", "nova", "placeholders", "payout", "crate", "tophead", "floatingitem", "shiny", "firsts", "lorestyles", "tagstyles", "menustyles", "hoestyles", "standingstyles", "enchantstyles", "novastyles", "auratest", "holo", "help");
    private static final List<String> CURRENCIES = List.of("money", "coins", "gems", "credits", "luck", "speed");

    private final SolRNGPlugin plugin;
    private final PlayerAdmin players;
    private final ShowcaseAdmin showcase;
    private final WorldAdmin world;

    public RngAdminCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.players = new PlayerAdmin(plugin);
        this.showcase = new ShowcaseAdmin(plugin);
        this.world = new WorldAdmin(plugin);
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
            case "setspawn" -> world.doSetSpawn(sender);
            case "starforge" -> players.doStarforge(sender, args);
            case "reset" -> players.doReset(sender, args);
            case "give" -> players.doGive(sender, args);
            case "drops" -> players.doDrops(sender, args, false);
            case "bank" -> players.doDrops(sender, args, true);
            case "aura" -> showcase.doAura(sender, args);
            case "roll" -> showcase.doRoll(sender, args);
            case "shiny" -> showcase.doShiny(sender, args);
            case "firsts" -> showcase.doFirsts(sender, args);
            case "lorestyles" -> showcase.doLoreStyles(sender, args);
            case "tagstyles" -> showcase.doTagStyles(sender, args);
            case "menustyles" -> showcase.doMenuStyles(sender, args);
            case "hoestyles" -> showcase.doHoeStyles(sender, args);
            case "standingstyles" -> showcase.doStandingStyles(sender, args);
            case "enchantstyles" -> showcase.doEnchantStyles(sender, args);
            case "novastyles" -> showcase.doNovaStyles(sender, args);
            case "auratest" -> showcase.doAuraTest(sender, args);
            case "unlock" -> players.doUnlock(sender, args);
            case "unlockall" -> players.doUnlockAll(sender, args);
            case "hoe" -> players.doHoe(sender, args);
            case "consumable" -> players.doConsumable(sender, args);
            case "gradient" -> showcase.doGradient(sender, args);
            case "welcome" -> showcase.doWelcome(sender, args);
            case "farmscan" -> world.doFarmScan(sender, args);
            case "farmclear" -> world.doFarmClear(sender, args);
            case "lockall" -> players.doLockAll(sender, args);
            case "odds" -> showcase.doOdds(sender, args);
            case "farmblock" -> world.doFarmBlock(sender, args);
            case "crops" -> players.doCrops(sender, args);
            case "milestones" -> players.doMilestones(sender, args);
            case "farmfill" -> world.doFarmFill(sender, args);
            case "boost" -> players.doBoost(sender, args);
            case "nova" -> players.doNova(sender, args);
            case "placeholders" -> showcase.doPlaceholders(sender);
            case "payout" -> world.doPayout(sender);
            case "crate" -> world.doCrate(sender, args);
            case "holo" -> world.doHolo(sender, args);
            case "tophead" -> world.doTopHead(sender, args);
            case "floatingitem" -> world.doFloatingItem(sender, args);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    // ------------------------------------------------------------------ help

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "SpaceRNG admin");
        line(sender, "reload", "", "Reload config.yml");
        line(sender, "setspawn", "", "Set the join/spawn point to where you stand");
        line(sender, "starforge", "[tier] [player]", "Give a Starforge (defaults to the tier they own)");
        line(sender, "reset", "<player> confirm", "Wipe a player back to a brand-new account");
        line(sender, "give", "<money|coins|gems|credits|luck|speed> <amount> [player]",
                "Top up a currency, or add permanent Luck or Speed in percent");
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
        line(sender, "crate", "<place|set|remove|list|key|preview>", "Place crates and hand out keys");
        line(sender, "holo", "<panel|board|leader|remove|list>", "NPC text, leaderboard walls and #1 heads");
        line(sender, "floatingitem", "<add|remove|list>", "Rotating item displays anchored at a spot");
        line(sender, "tophead", "<set|podium|remove|clear|list>", "Floating heads for a leaderboard");
        line(sender, "shiny", "[player]", "Make the next roll shiny");
        line(sender, "firsts", "<list|reset|preview> [rarity]", "Server First 10 spots");
        line(sender, "lorestyles", "[rarity] [shiny]", "One sample drop in every lore style");
        line(sender, "tagstyles", "[style]", "Tag odds styles side by side, or switch to one");
        line(sender, "menustyles", "[style]", "Hover each menu theme to compare, or switch to one");
        line(sender, "hoestyles", "[style]", "The hoe's tooltip in every style");
        line(sender, "standingstyles", "[style]", "Your standings card in /leaderboards in every style");
        line(sender, "enchantstyles", "[style] [enchant]", "An enchant card in every style");
        line(sender, "novastyles", "[style] [consumable]", "The Nova Core and other consumables in every style");
        line(sender, "auratest", "<look|off|list> [rarity] [accent]", "Wear a worn aura look to test it");
    }

    private boolean doReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(ChatColor.GREEN + "SolRNG config reloaded.");
        return true;
    }

    private List<String> crateTab(String[] args) {
        List<String> ids = new ArrayList<>(plugin.getCrateManager().getAll().keySet());
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        return switch (args.length) {
            case 2 -> partial(args[1], List.of("place", "set", "remove", "list", "key", "preview"));
            case 3 -> List.of("place", "set", "key", "preview").contains(action) ? partial(args[2], ids) : List.of();
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

        if (args.length == 4 && args[0].equalsIgnoreCase("auratest")) {
            return partial(args[3], com.spacerng.solrng.aura.AuraAccent.KEYS);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("crate")) return crateTab(args);
        if (sub.equals("tophead")) return topHeadTab(args);
        if (sub.equals("menustyles") && args.length >= 3 && args[1].equalsIgnoreCase("preview")) {
            if (args.length == 3) {
                return partial(args[2], java.util.Arrays.stream(com.spacerng.solrng.gui.Lore.Theme.values())
                        .map(com.spacerng.solrng.gui.Lore.Theme::key).toList());
            }
            return args.length == 4 ? partial(args[3], ShowcaseAdmin.PREVIEW_MENUS) : List.of();
        }
        if (sub.equals("holo")) {
            if (args.length == 2) return partial(args[1], List.of("panel", "board", "leader", "remove", "list"));
            if (args.length == 3 && args[1].equalsIgnoreCase("leader")) {
                return partial(args[2], com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("panel")) {
                return partial(args[2], new ArrayList<>(plugin.getHoloManager().panelIds()));
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("board")) {
                return partial(args[2], com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS);
            }
            return List.of();
        }
        if (args.length == 2) {
            return switch (sub) {
                case "give" -> partial(args[1], CURRENCIES);
                case "drops", "bank" -> partial(args[1], withAll(rarityNames()));
                case "aura" -> partial(args[1], List.of("epic", "legendary", "mythical", "divine"));
                case "shiny" -> partial(args[1], playerNames());
                case "firsts" -> partial(args[1], List.of("list", "reset", "preview"));
                case "lorestyles" -> partial(args[1], rarityNames());
                case "tagstyles" -> partial(args[1], RollFormat.TAG_ODDS_STYLES);
                case "menustyles" -> partial(args[1], java.util.stream.Stream.concat(
                        java.util.Arrays.stream(com.spacerng.solrng.gui.Lore.Theme.values())
                                .map(com.spacerng.solrng.gui.Lore.Theme::key), java.util.stream.Stream.of("preview")).toList());
                case "hoestyles" -> partial(args[1], com.spacerng.solrng.farming.FarmingManager.HOE_STYLES);
                case "standingstyles" -> partial(args[1], com.spacerng.solrng.gui.LeaderboardGui.STANDINGS_STYLES);
                case "enchantstyles" -> partial(args[1], com.spacerng.solrng.gui.HoeGui.ENCHANT_STYLES);
                case "novastyles" -> partial(args[1], com.spacerng.solrng.consumable.ConsumableManager.CONSUMABLE_STYLES);
                case "auratest" -> partial(args[1], java.util.stream.Stream.concat(com.spacerng.solrng.aura.AuraConcepts.KEYS.stream(), java.util.stream.Stream.of("off", "list")).toList());
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
                case "auratest" -> partial(args[2], List.of("epic", "legendary", "mythical", "divine"));
                case "firsts" -> partial(args[2], args[1].equalsIgnoreCase("reset") ? withAll(rarityNames()) : rarityNames());
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
