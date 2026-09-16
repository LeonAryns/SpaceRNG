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

/** Admin tools that change the world: spawn, farm plots, the payout, crates, top heads and floating items. */
final class WorldAdmin extends AdminTools {

    WorldAdmin(SolRNGPlugin plugin) {
        super(plugin);
    }

    boolean doSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can set spawn.");
            return true;
        }
        plugin.getSpawnManager().setSpawn(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Spawn set. Every player now teleports here on join.");
        return true;
    }

    /**
     * Wipes the field.
     *
     * Destructive and unrecoverable except by rebuilding, so it takes the
     * word confirm. The blocks go too, not just the registry: leaving the
     * markers behind would mean farmscan puts every plot straight back.
     */
    boolean doFarmClear(CommandSender sender, String[] args) {
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
     * Rebuilds the farm plot registry from the blocks actually in the
     * world. farmplots.yml is a cache; the field itself is the record, so
     * a lost data folder costs this one command.
     */
    boolean doFarmScan(CommandSender sender, String[] args) {
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
     * Hands over Farm Plot blocks. Place one to add a tile to the shared
     * field; sneak-break one to remove it again.
     */
    boolean doFarmBlock(CommandSender sender, String[] args) {
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

    /**
     * Fills a square of farm plots centred on where you stand, at your own
     * feet level. Placing a field by hand is fine for a demo and miserable
     * for a real one.
     */
    boolean doFarmFill(CommandSender sender, String[] args) {
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

    /** Forces the farming payout, for testing the podium without waiting. */
    boolean doPayout(CommandSender sender) {
        plugin.getLeaderboardManager().runPayout();
        plugin.getLeaderboardManager().saveIndex();
        sender.sendMessage(ChatColor.GREEN + "Farming payout run and period reset.");
        return true;
    }

    boolean doCrate(CommandSender sender, String[] args) {
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
                if (block == null || !crates.remove(block)) {
                    sender.sendMessage(ChatColor.RED + "The block you are looking at is not a crate.");
                } else if (plugin.getHoloManager().removeCrate(block)) {
                    sender.sendMessage(ChatColor.GREEN + "Crate removed, with its head and text.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Crate removed. The block itself stays.");
                }
            }
            case "place" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Stand in game, hold a head and look at a block.");
                    return true;
                }
                var crate = args.length >= 3 ? crates.get(args[2]) : null;
                if (crate == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin crate place <crate>");
                    sender.sendMessage(ChatColor.DARK_GRAY + "Crates: " + String.join(", ", crates.getAll().keySet()));
                    return true;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() != org.bukkit.Material.PLAYER_HEAD) {
                    sender.sendMessage(ChatColor.RED + "Hold the player head the crate should look like.");
                    return true;
                }
                org.bukkit.block.Block target = player.getTargetBlockExact(6);
                if (target == null || target.getType().isAir()) {
                    sender.sendMessage(ChatColor.RED + "Look at the block the crate should stand on.");
                    return true;
                }
                org.bukkit.block.Block block = target.getRelative(org.bukkit.block.BlockFace.UP);
                if (!block.getType().isAir()) {
                    sender.sendMessage(ChatColor.RED + "The space on top of that block has to be empty.");
                    return true;
                }
                // An invisible barrier takes the clicks; the head and the text float over it.
                block.setType(org.bukkit.Material.BARRIER);
                crates.place(block, crate);
                ItemStack head = held.clone();
                head.setAmount(1);
                plugin.getHoloManager().placeCrate(crate.id(), block, player.getLocation().getYaw() + 180f, head);
                sender.sendMessage(ChatColor.GREEN + "Placed the " + crates.styledName(crate) + ChatColor.GREEN
                        + ". Right-click opens it, left-click shows the rewards.");
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
            case "keyall" -> {
                // For rank key alls and events: every online player gets the keys at once.
                var crate = args.length >= 3 ? crates.get(args[2]) : null;
                if (crate == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin crate keyall <crate> [amount]");
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
                var key = plugin.getConsumableManager().get(crate.keyId());
                if (key == null) {
                    sender.sendMessage(ChatColor.RED + "The key '" + crate.keyId()
                            + "' is not a consumable in config.yml.");
                    return true;
                }
                int given = 0;
                for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                    plugin.getConsumableManager().give(online, key, amount);
                    online.playSound(online.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                    given++;
                }
                org.bukkit.Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Key All! "
                        + ChatColor.RESET + ChatColor.GRAY + "Everyone online got " + ChatColor.WHITE + amount + "x "
                        + crates.keyName(crate) + ChatColor.GRAY + ".");
                sender.sendMessage(ChatColor.GREEN + "Gave " + given + " players " + amount + "x "
                        + crates.keyName(crate) + ChatColor.GREEN + ".");
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
                line(sender, "crate place", "<crate>", "Hold a head, look at a block: a floating crate with text");
                line(sender, "crate set", "<crate>", "Turn the block you are looking at into a crate");
                line(sender, "crate remove", "", "Stop the block you are looking at being a crate");
                line(sender, "crate list", "", "Every placed crate");
                line(sender, "crate key", "<crate> [amount] [player]", "Hand out keys");
                line(sender, "crate preview", "<crate>", "Open a crate's reward list");
            }
        }
        return true;
    }

    boolean doTopHead(CommandSender sender, String[] args) {
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

    boolean doFloatingItem(CommandSender sender, String[] args) {
        var manager = plugin.getFloatingItemManager();
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin floatingitem <add|remove|list>");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage(ChatColor.RED + "Stand in game to place a floating item.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED
                            + "Usage: /rngadmin floatingitem add <id> <material> [label]");
                    return true;
                }
                String id = args[2];
                org.bukkit.Material material;
                try {
                    material = org.bukkit.Material.valueOf(args[3].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(ChatColor.RED + "Unknown material: " + args[3]);
                    return true;
                }
                StringBuilder label = new StringBuilder();
                for (int i = 4; i < args.length; i++) {
                    if (label.length() > 0) label.append(' ');
                    label.append(args[i]);
                }
                if (!manager.add(id, player.getLocation(), material, label.toString())) {
                    sender.sendMessage(ChatColor.RED + "A floating item with that id already exists.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Placed floating " + material.name()
                        + " as " + ChatColor.YELLOW + id + ChatColor.GREEN + ".");
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin floatingitem remove <id>");
                    return true;
                }
                sender.sendMessage(manager.remove(args[2])
                        ? ChatColor.GREEN + "Removed floating item " + args[2] + "."
                        : ChatColor.RED + "No floating item with that id.");
            }
            case "list" -> {
                var spots = manager.getSpots();
                if (spots.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No floating items placed.");
                    return true;
                }
                sender.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                        + "Floating items " + ChatColor.GRAY + "(" + spots.size() + ")");
                for (var spot : spots.values()) {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + spot.id()
                            + ChatColor.DARK_GRAY + ": " + ChatColor.AQUA + spot.material().name()
                            + ChatColor.DARK_GRAY + " @ " + ChatColor.WHITE
                            + spot.at().getWorld().getName() + " "
                            + String.format("%.1f, %.1f, %.1f",
                                spot.at().getX(), spot.at().getY(), spot.at().getZ()));
                }
            }
            default -> sender.sendMessage(ChatColor.RED
                    + "Unknown subcommand. Try add / remove / list.");
        }
        return true;
    }

    /** Floating text: NPC panels from config, live leaderboards, and taking either down. */
    boolean doHolo(CommandSender sender, String[] args) {
        var holo = plugin.getHoloManager();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!action.equals("list") && !(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Stand in game where the text should go.");
            return true;
        }
        switch (action) {
            case "panel" -> {
                Player player = (Player) sender;
                String id = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (!holo.panelIds().contains(id)) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin holo panel <panel>");
                    sender.sendMessage(ChatColor.DARK_GRAY + "Panels: " + String.join(", ", holo.panelIds()));
                    return true;
                }
                holo.placePanel(id, player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Placed the " + id + " panel above where you stand. "
                        + ChatColor.GRAY + "Edit its text under holograms.panels, then /rngadmin reload.");
            }
            case "board" -> {
                Player player = (Player) sender;
                String board = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (!com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS.contains(board)) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin holo board <board>");
                    sender.sendMessage(ChatColor.DARK_GRAY + "Boards: "
                            + String.join(", ", com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS));
                    return true;
                }
                holo.placeBoard(board, player.getEyeLocation());
                sender.sendMessage(ChatColor.GREEN + "Placed the " + board + " board in front of you, facing you.");
            }
            case "leader" -> {
                Player player = (Player) sender;
                String board = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "farming";
                if (!com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS.contains(board)) {
                    sender.sendMessage(ChatColor.RED + "Usage: /rngadmin holo leader [board]");
                    return true;
                }
                holo.placeLeader(board, player.getEyeLocation());
                sender.sendMessage(ChatColor.GREEN + "Placed the " + board + " podium three blocks in front of you. "
                        + ChatColor.GRAY + "The heads follow the top three.");
            }
            case "remove" -> {
                Player player = (Player) sender;
                double radius = 4.0;
                if (args.length >= 3) {
                    try {
                        radius = Math.max(0.5, Math.min(32.0, Double.parseDouble(args[2])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Radius must be a number.");
                        return true;
                    }
                }
                int removed = holo.removeNear(player.getLocation(), radius);
                sender.sendMessage(removed > 0
                        ? ChatColor.GREEN + "Removed " + removed + (removed == 1 ? " panel or board." : " panels and boards.")
                        : ChatColor.RED + "No panel or board within " + radius + " blocks. Crates go with /rngadmin crate remove.");
            }
            case "list" -> {
                if (holo.list().isEmpty()) sender.sendMessage(ChatColor.GRAY + "Nothing placed yet.");
                for (var spot : holo.list()) {
                    var at = spot.at();
                    sender.sendMessage(ChatColor.YELLOW + spot.kind().name().toLowerCase(Locale.ROOT) + " "
                            + spot.key() + ChatColor.GRAY + " at " + (at.getWorld() == null ? "?" : at.getWorld().getName())
                            + " " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ());
                }
            }
            default -> {
                line(sender, "holo panel", "<panel>", "NPC text from holograms.panels, above where you stand");
                line(sender, "holo board", "<board>", "A leaderboard with heads, in front of you");
                line(sender, "holo leader", "[board]", "The top three as a podium, three blocks in front of you");
                line(sender, "holo remove", "[radius]", "Take down panels and boards near you");
                line(sender, "holo list", "", "Everything placed");
            }
        }
        return true;
    }

    /**
     * The boss event: where it stands, and starting or ending one by hand.
     *
     * The spot is a world name plus coordinates in boss.yml rather than a
     * resolved Location, so a Multiverse world that loads after the plugin
     * is still found when a boss actually starts.
     */
    boolean doBoss(CommandSender sender, String[] args) {
        var boss = plugin.getBossManager();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "here" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Stand where the boss should appear.");
                    return true;
                }
                boss.setSpot(player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Boss spot set. The next boss stands here.");
            }
            case "start" -> {
                if (boss.isActive()) {
                    sender.sendMessage(ChatColor.RED + "A boss is already up. End it with /rngadmin boss stop.");
                    return true;
                }
                String id = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                com.spacerng.solrng.boss.BossType type = boss.getTypes().get(id);
                if (type == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown boss. Types: "
                            + String.join(", ", boss.getTypes().keySet()));
                    return true;
                }
                if (!boss.spawn(type)) {
                    sender.sendMessage(ChatColor.RED + "No spot set and no spawn either. Run /rngadmin boss here.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Started " + type.display() + ".");
            }
            case "process" -> {
                String what = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (what.equals("start")) {
                    if (!boss.startProcess()) {
                        sender.sendMessage(ChatColor.RED + "The boss process is already running.");
                        return true;
                    }
                    sender.sendMessage(ChatColor.GREEN + "Boss process started. The first one is due in "
                            + boss.minutesToNext() + " minutes, and it keeps going across restarts.");
                } else if (what.equals("stop")) {
                    if (!boss.stopProcess()) {
                        sender.sendMessage(ChatColor.RED + "The boss process is not running.");
                        return true;
                    }
                    sender.sendMessage(ChatColor.GREEN + "Boss process stopped. No more bosses until you start it.");
                } else {
                    sender.sendMessage(boss.isRunning()
                            ? ChatColor.GREEN + "The boss process is running. Next one in "
                                    + boss.minutesToNext() + " minutes."
                            : ChatColor.RED + "The boss process is off.");
                    sender.sendMessage(ChatColor.YELLOW + "/rngadmin boss process <start|stop>");
                }
            }
            case "stop" -> {
                if (!boss.cancel()) {
                    sender.sendMessage(ChatColor.RED + "No boss is up.");
                    return true;
                }
                sender.sendMessage(ChatColor.GREEN + "Boss ended. Nobody was paid.");
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "/rngadmin boss here"
                        + ChatColor.GRAY + " - put the boss where you stand");
                sender.sendMessage(ChatColor.YELLOW + "/rngadmin boss start <type>"
                        + ChatColor.GRAY + " - start one now");
                sender.sendMessage(ChatColor.YELLOW + "/rngadmin boss stop"
                        + ChatColor.GRAY + " - end the one that is up");
                sender.sendMessage(ChatColor.YELLOW + "/rngadmin boss process <start|stop>"
                        + ChatColor.GRAY + " - the timer, kept across restarts");
                sender.sendMessage(ChatColor.DARK_GRAY + "Types: "
                        + String.join(", ", boss.getTypes().keySet()));
                sender.sendMessage(ChatColor.DARK_GRAY + (boss.hasSpot()
                        ? "A spot is set." : "No spot set, the server spawn is used.")
                        + (boss.isRunning() ? " The process is running." : " The process is off."));
            }
        }
        return true;
    }
}
