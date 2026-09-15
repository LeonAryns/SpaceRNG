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

/** Admin tools for what players see: roll auras, worn auras, shiny, First 10, lore, odds and text. */
final class ShowcaseAdmin extends AdminTools {

    ShowcaseAdmin(SolRNGPlugin plugin) {
        super(plugin);
    }

    /**
     * Replays the rarity's real build-up at its real length (Epic 3s,
     * Legendary 5s, Mythical 10s) and then the burst, so the effect can be
     * judged without waiting for one to land.
     */
    boolean doAura(CommandSender sender, String[] args) {
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

    /**
     * Tries an aura concept on yourself, in a rarity's colours, so the looks
     * can be judged in game before any of them is tied to tags.
     */
    boolean doAuraTest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can wear an aura.");
            return true;
        }
        String concepts = String.join("|", com.spacerng.solrng.aura.AuraConcepts.KEYS);
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin auratest <" + concepts + "|off> [rarity] [accent]");
            return true;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (key.equals("list")) {
            sender.sendMessage(ChatColor.AQUA + "Aura concepts:");
            com.spacerng.solrng.aura.AuraConcepts.DESCRIPTIONS.forEach((name, line) ->
                    sender.sendMessage(ChatColor.YELLOW + " " + name + ChatColor.GRAY + "  " + line));
            sender.sendMessage(ChatColor.AQUA + "Accents: " + ChatColor.GRAY
                    + String.join(", ", com.spacerng.solrng.aura.AuraAccent.KEYS));
            return true;
        }
        if (key.equals("off")) {
            plugin.getAuraManager().endTest(player);
            sender.sendMessage(ChatColor.GRAY + "Aura removed.");
            return true;
        }
        Rarity rarity = Rarity.LEGENDARY;
        if (args.length >= 3) {
            rarity = parseRarity(sender, args[2]);
            if (rarity == null) return true;
        }
        com.spacerng.solrng.aura.AuraAccent accent = com.spacerng.solrng.aura.AuraAccent.NONE;
        if (args.length >= 4) {
            accent = com.spacerng.solrng.aura.AuraAccent.parse(args[3]);
            if (accent == null) {
                sender.sendMessage(ChatColor.RED + "Unknown accent. Try "
                        + String.join("|", com.spacerng.solrng.aura.AuraAccent.KEYS) + ".");
                return true;
            }
        }
        if (!plugin.getAuraManager().show(player, key, rarity, accent)) {
            sender.sendMessage(ChatColor.RED + "Unknown concept. Try " + concepts + ".");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "Wearing " + ChatColor.WHITE + key + ChatColor.GREEN + " in "
                + plugin.getRarityManager().style(rarity, rarity.displayName()) + ChatColor.GREEN
                + " colours. " + ChatColor.GRAY + "/rngadmin auratest off to remove.");
        return true;
    }

    /**
     * Hands out the same drop once per lore style, so the layouts can be
     * compared by hovering in the inventory. The stack size is the style's
     * number, which is why a stackable item is picked. The samples carry
     * no drop tags, so they can't be converted or passed off as real.
     */
    boolean doLoreStyles(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can receive the samples.");
            return true;
        }
        Rarity rarity = Rarity.LEGENDARY;
        if (args.length >= 2) {
            rarity = parseRarity(sender, args[1]);
            if (rarity == null) return true;
        }
        boolean shiny = args.length >= 3 && args[2].equalsIgnoreCase("shiny");

        RollableItem item = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            RollableItem candidate = randomItemOf(rarity);
            if (candidate == null) break;
            item = candidate;
            if (candidate.getMaterial().getMaxStackSize() >= 8) break;
        }
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "No items configured for " + rarity.displayName() + ".");
            return true;
        }

        String name = com.spacerng.solrng.rarity.RollFormat.displayName(plugin, item, shiny);
        com.spacerng.solrng.rarity.LoreStyle current = com.spacerng.solrng.rarity.LoreStyle.configured(plugin);
        sender.sendMessage(ChatColor.AQUA + "Lore styles for " + name + ChatColor.GRAY + ", stack size is the number:");
        int number = 0;
        for (com.spacerng.solrng.rarity.LoreStyle style : com.spacerng.solrng.rarity.LoreStyle.values()) {
            number++;
            org.bukkit.inventory.ItemStack sample = new org.bukkit.inventory.ItemStack(item.getMaterial(), number);
            org.bukkit.inventory.meta.ItemMeta meta = sample.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(style.build(plugin, item, shiny));
            if (shiny) meta.setEnchantmentGlintOverride(Boolean.TRUE);
            sample.setItemMeta(meta);
            player.getInventory().addItem(sample);
            sender.sendMessage(ChatColor.YELLOW + " " + number + "  " + ChatColor.WHITE + style.key()
                    + ChatColor.GRAY + "  " + style.summary()
                    + (style == current ? ChatColor.GREEN + "  (current)" : ""));
        }
        sender.sendMessage(ChatColor.GRAY + "Add " + ChatColor.YELLOW + "shiny" + ChatColor.GRAY
                + " to see the shiny version. Pick one with " + ChatColor.YELLOW + "roll-item.lore-style"
                + ChatColor.GRAY + " in config.yml, then /rngadmin reload.");
        return true;
    }

    /**
     * Server First 10 tools. list shows who holds each spot, reset frees a
     * rarity's spots again, and preview plays the whole server-wide event
     * without recording anything, because waiting for a real Legendary is
     * not a test plan.
     */
    boolean doFirsts(CommandSender sender, String[] args) {
        com.spacerng.solrng.firsts.FirstTenManager firsts = plugin.getFirstTenManager();
        String usage = ChatColor.RED + "Usage: /rngadmin firsts <list | reset <rarity|all> | preview <rarity>>";
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list" -> {
                List<Rarity> tracked = firsts.trackedRarities();
                if (tracked.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "Server First 10 is switched off in config.");
                }
                for (Rarity rarity : tracked) {
                    List<com.spacerng.solrng.firsts.FirstTenManager.Entry> held = firsts.entries(rarity);
                    sender.sendMessage(plugin.getRarityManager().style(rarity, rarity.displayName())
                            + ChatColor.GRAY + "  " + held.size() + " / " + firsts.slots());
                    for (int i = 0; i < held.size(); i++) {
                        sender.sendMessage(ChatColor.DARK_GRAY + "  #" + (i + 1) + " "
                                + ChatColor.YELLOW + held.get(i).name()
                                + ChatColor.GRAY + "  " + held.get(i).item());
                    }
                }
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(usage);
                    return true;
                }
                if (args[2].equalsIgnoreCase("all")) {
                    firsts.resetAll();
                    sender.sendMessage(ChatColor.GREEN + "Every First 10 spot is free again.");
                } else {
                    Rarity rarity = parseRarity(sender, args[2]);
                    if (rarity == null) return true;
                    firsts.reset(rarity);
                    sender.sendMessage(ChatColor.GREEN + "The " + rarity.displayName() + " First 10 spots are free again.");
                }
            }
            case "preview" -> {
                if (args.length < 3) {
                    sender.sendMessage(usage);
                    return true;
                }
                Rarity rarity = parseRarity(sender, args[2]);
                if (rarity == null) return true;
                RollableItem item = randomItemOf(rarity);
                if (item == null) {
                    sender.sendMessage(ChatColor.RED + "No items configured for " + rarity.displayName() + ".");
                    return true;
                }
                firsts.preview(sender instanceof Player player ? player : null, item, args.length >= 4 && args[3].equalsIgnoreCase("shiny"));
            }
            default -> sender.sendMessage(usage);
        }
        return true;
    }

    /**
     * Makes the target's next roll come out shiny, so the pre-roll can be
     * watched without waiting for a 1 in 2,500. The roll itself still
     * starts the normal way, by clicking or through Auto Roll.
     */
    boolean doShiny(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;
        plugin.getRollListener().forceShinyNext(target.getUniqueId());
        sender.sendMessage(ChatColor.AQUA + "The next roll for " + target.getName() + " comes out shiny.");
        return true;
    }

    /** A genuine roll result, forced to a tier. */
    boolean doRoll(CommandSender sender, String[] args) {
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

    /** Replays the join banner, for tuning it without rejoining. */
    boolean doWelcome(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;
        plugin.getWelcomeManager().send(target);
        sender.sendMessage(ChatColor.GREEN + "Replayed the welcome for " + target.getName() + ".");
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
    boolean doGradient(CommandSender sender, String[] args) {
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
     * The diagnostic behind "is 1 in 250 really 1 in 250?". Odds in
     * config.yml are relative WEIGHTS: an item's real chance is its own
     * 1/odds divided by the sum of every item's 1/odds. That sum only
     * equals 1.0 if the table was authored to add up, so this prints the
     * factor everything is off by, plus each tier's true share.
     */
    boolean doOdds(CommandSender sender, String[] args) {
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

    /**
     * Prints what every placeholder currently resolves to for the sender.
     * This is the fast way to tell a TAB configuration problem from a
     * plugin one: if the values show up here, the data is fine and the
     * fault is in TAB's config or its PlaceholderAPI hook.
     */
    boolean doPlaceholders(CommandSender sender) {
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

    /**
     * Every tag odds style for one sample drop of each rarity, as the name
     * and odds lines would read above a head. With a style name, switches
     * the server to it and rebuilds every tag that is showing.
     */
    boolean doTagStyles(CommandSender sender, String[] args) {
        String pick = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (RollFormat.TAG_ODDS_STYLES.contains(pick)) {
            plugin.getConfig().set("tag.odds-style", pick);
            plugin.saveConfig();
            for (Player online : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.getPlayerDataManager().get(online.getUniqueId());
                RollableItem tagged = data.getEquippedTagItemKey() == null ? null
                        : plugin.getRarityManager().findByDisplayName(data.getEquippedTagItemKey());
                if (tagged == null) continue;
                plugin.getTagManager().showHologram(online, RollFormat.displayName(plugin, tagged),
                        RollFormat.tagOdds(plugin, tagged));
            }
            sender.sendMessage(ChatColor.GREEN + "Tag odds now use the " + pick + " style.");
            return true;
        }

        String current = plugin.getConfig().getString("tag.odds-style", "gradient");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Tag odds styles");
        for (String style : RollFormat.TAG_ODDS_STYLES) {
            sender.sendMessage(ChatColor.YELLOW + style + (style.equals(current) ? ChatColor.GREEN + " (current)" : ""));
            for (Rarity rarity : Rarity.values()) {
                RollableItem sample = randomItemOf(rarity);
                if (sample == null) continue;
                sender.sendMessage("  " + RollFormat.displayName(plugin, sample) + ChatColor.DARK_GRAY + "  /  "
                        + RollFormat.tagOdds(rarity, sample.getOdds(), style));
            }
        }
        sender.sendMessage(ChatColor.GRAY + "Pick one with " + ChatColor.YELLOW + "/rngadmin tagstyles <style>");
        return true;
    }
}
