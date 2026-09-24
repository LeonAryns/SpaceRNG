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

        // A real drop of that rarity stands behind the preview, so the
        // counter climbs to a number the server actually pays rather than
        // sitting blank, and the ladder is the real one for this player.
        RollableItem sample = randomItemOf(rarity);
        long odds = sample == null ? 0L : sample.getOdds();
        com.spacerng.solrng.roll.RollStages stages = com.spacerng.solrng.roll.RollStages.of(plugin,
                plugin.getPlayerDataManager().get(target.getUniqueId()), rarity, odds);
        long actTicks = RollAura.actTicks(plugin);
        long duration = RollAura.durationTicks(stages, actTicks);
        if (duration <= 0L) {
            sender.sendMessage(ChatColor.RED + target.getName() + " has that rarity's aura switched off "
                    + "in /options, so there is nothing to show them.");
            return true;
        }

        RollAura aura = RollAura.start(plugin, target, rarity, odds, stages, actTicks);
        if (aura == null) return true;

        // Reveal exactly when the last act finishes, same as a real roll.
        plugin.getServer().getScheduler().runTaskLater(plugin, aura::reveal, duration);

        sender.sendMessage(ChatColor.GREEN + "Playing the " + rarity.displayName() + " reveal on "
                + target.getName() + ChatColor.GRAY + " (" + stages.acts() + " acts, "
                + String.format("%.0f", duration / 20.0) + "s).");
        return true;
    }

    /**
     * Hands over the question mark head on its own.
     *
     * It is here because V196 shipped a head nobody could see and there
     * was no way to tell whether the texture was wrong or the thing
     * holding it was. Holding the item answers that in one click.
     */
    boolean doHead(CommandSender sender, String[] args) {
        Player target = resolve(sender, args.length >= 2 ? args[1] : null);
        if (target == null) return true;
        target.getInventory().addItem(com.spacerng.solrng.roll.MysteryHead.item(plugin));
        sender.sendMessage(ChatColor.GREEN + "Gave the mystery head to " + target.getName()
                + ChatColor.GRAY + ". If it is a plain Steve head, the texture in "
                + "roll-item.comet.mystery-head is what is wrong.");
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
        // A test obeys the own-aura setting from V184 on, so say which one
        // is running. Before this a test ignored it, which is how the
        // setting came to look broken from the only seat it was judged in.
        String view = plugin.getPlayerDataManager().get(player.getUniqueId()).getOwnAuraView();
        sender.sendMessage(ChatColor.GRAY + "Your own view is " + ChatColor.YELLOW + switch (view) {
            case "full" -> "Everything";
            case "hidden" -> "Hidden";
            default -> "Out of your way";
        } + ChatColor.GRAY + "; change it in /options.");
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
        // No flight time: this plays the burst straight away, and a comet
        // that spawned and landed in the same tick would be a flash of a
        // block in the sky.
        RollAura burst = RollAura.start(plugin, target, rarity, item.getOdds(), null, 0L);
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
    /**
     * "/rngadmin icon money" shows a sidebar icon as configured;
     * "/rngadmin icon minecraft:items|minecraft:item/emerald" shows any
     * sprite, so a new icon can be tried before it goes in the config.
     * A missing sprite shows as the purple and black missing texture.
     */
    boolean doIcon(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /rngadmin icon <name|atlas|sprite>");
            return true;
        }
        String spec = args[1];
        net.kyori.adventure.text.Component icon;
        if (spec.contains(":")) {
            try {
                String[] split = spec.split("\\|", 2);
                icon = net.kyori.adventure.text.Component.object(split.length == 2
                        ? net.kyori.adventure.text.object.ObjectContents.sprite(
                                net.kyori.adventure.key.Key.key(split[0]), net.kyori.adventure.key.Key.key(split[1]))
                        : net.kyori.adventure.text.object.ObjectContents.sprite(
                                net.kyori.adventure.key.Key.key(split[0])));
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Not a valid sprite name: " + ex.getMessage());
                return true;
            }
        } else {
            icon = com.spacerng.solrng.gui.Icons.sprite(plugin, spec);
            if (icon == null) {
                sender.sendMessage(ChatColor.RED + "No icon called " + spec + " in scoreboard.icons.");
                return true;
            }
        }
        sender.sendMessage(net.kyori.adventure.text.Component.text("Icon: ",
                net.kyori.adventure.text.format.NamedTextColor.WHITE).append(icon)
                .append(net.kyori.adventure.text.Component.text("  " + spec,
                        net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        return true;
    }

    boolean doOdds(CommandSender sender, String[] args) {
        List<RollableItem> items = plugin.getRarityManager().getItems();
        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No items are configured.");
            return true;
        }

        // "/rngadmin odds [rarity] [luck%]": a bare number is the Luck to
        // check at, "me" is the sender's own Luck, so the real chances at
        // any Luck can be read off instead of guessed.
        double luck = 0.0;
        Rarity filter = null;
        for (int a = 1; a < args.length; a++) {
            String arg = args[a].replace("%", "");
            if (arg.equalsIgnoreCase("me") && sender instanceof Player self) {
                luck = plugin.getPrestigeManager().effectiveLuck(
                        plugin.getPlayerDataManager().get(self.getUniqueId()));
                continue;
            }
            try {
                luck = Double.parseDouble(arg) / 100.0;
                continue;
            } catch (NumberFormatException ignored) {
                // not a number, so it names a rarity
            }
            filter = parseRarity(sender, args[a]);
            if (filter == null) return true;
        }

        double[] weights = plugin.getRarityManager().weightsAt(luck, null);
        double weightSum = 0.0;
        for (double weight : weights) weightSum += weight;

        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Odds check "
                + ChatColor.WHITE + "at +" + String.format("%,.0f", luck * 100.0) + "% Luck "
                + ChatColor.GRAY + "(" + items.size() + " items)");
        sender.sendMessage(ChatColor.GRAY + "Epic and up: the label divided by (1 + Luck)."
                + " Common is what is left over.");

        for (Rarity rarity : Rarity.values()) {
            double share = 0.0;
            int count = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getRarity() != rarity) continue;
                share += weights[i];
                count++;
            }
            if (count == 0) continue;
            sender.sendMessage(plugin.getRarityManager().style(rarity, rarity.displayName())
                    + ChatColor.DARK_GRAY + " x" + count + ChatColor.GRAY + " - "
                    + ChatColor.WHITE + (share <= 0.0 ? "never"
                            : RollFormat.chance(Math.round(weightSum / share)))
                    + ChatColor.GRAY + String.format(" (%.4f%% of rolls)", 100.0 * share / weightSum));
        }

        if (filter != null) {
            sender.sendMessage(ChatColor.GRAY + "Label -> real odds at this Luck:");
            for (int i = 0; i < items.size(); i++) {
                RollableItem item = items.get(i);
                if (item.getRarity() != filter) continue;
                long trueOdds = Math.round(weightSum / Math.max(1e-18, weights[i]));
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

        String current = plugin.getConfig().getString("tag.odds-style", "dots");
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

    /**
     * One chat line per menu theme; hovering it shows a sample skill card
     * written in that theme. With a theme name, switches every menu to it.
     */
    boolean doMenuStyles(CommandSender sender, String[] args) {
        String pick = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (pick.equals("preview")) return previewMenu(sender, args);
        for (com.spacerng.solrng.gui.Lore.Theme theme : com.spacerng.solrng.gui.Lore.Theme.values()) {
            if (!theme.key().equals(pick)) continue;
            plugin.getConfig().set("menu-style", theme.key());
            plugin.saveConfig();
            com.spacerng.solrng.gui.Lore.setTheme(theme);
            sender.sendMessage(ChatColor.GREEN + "Menus now use the " + theme.key() + " style. Reopen a menu to see it.");
            return true;
        }

        var current = com.spacerng.solrng.gui.Lore.theme();
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Menu styles " + ChatColor.GRAY + "(hover a style)");
        for (com.spacerng.solrng.gui.Lore.Theme theme : com.spacerng.solrng.gui.Lore.Theme.values()) {
            ItemStack sample;
            com.spacerng.solrng.gui.Lore.setTheme(theme);
            try {
                sample = sampleSkillCard();
            } finally {
                com.spacerng.solrng.gui.Lore.setTheme(current);
            }
            String text = ChatColor.YELLOW + theme.key() + ChatColor.GRAY + "  " + theme.summary()
                    + (theme == current ? ChatColor.GREEN + "  (current)" : "");
            var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
            var line = legacy.deserialize(text).hoverEvent(sample.asHoverEvent());
            // Buttons that open the real menu in this theme.
            for (String menu : List.of("skilltree", "prestige", "index")) {
                line = line.append(legacy.deserialize("  " + ChatColor.AQUA + "[" + menu + "]")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                                "/rngadmin menustyles preview " + theme.key() + " " + menu))
                        .hoverEvent(net.kyori.adventure.text.Component.text("Open " + menu + " in " + theme.key())));
            }
            sender.sendMessage(line);
        }
        sender.sendMessage(ChatColor.GRAY + "Hover a style for a card, click a menu to open it in that style, or "
                + ChatColor.YELLOW + "/rngadmin menustyles <style>" + ChatColor.GRAY + " to switch.");
        return true;
    }

    /** A made-up skill card using every shared text shape, for comparing themes. */
    private static ItemStack sampleSkillCard() {
        ItemStack item = new ItemStack(org.bukkit.Material.RABBIT_FOOT);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(com.spacerng.solrng.gui.Lore.title(ChatColor.GREEN, "Luck II"));
        List<String> lines = new ArrayList<>();
        lines.add(com.spacerng.solrng.gui.Lore.section(ChatColor.GREEN, "Effect"));
        lines.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.GREEN, "Per level", "+8% Luck"));
        lines.add(com.spacerng.solrng.gui.Lore.stat(ChatColor.AQUA, "Level", "3 / 10"));
        lines.add(com.spacerng.solrng.gui.Lore.upgrade(ChatColor.GREEN, "Total", "+24%", "+32%"));
        lines.add("");
        lines.add(com.spacerng.solrng.gui.Lore.section(ChatColor.YELLOW, "Requirements"));
        lines.add(com.spacerng.solrng.gui.Lore.requirement("Money", "12K", "28K", false));
        lines.add(com.spacerng.solrng.gui.Lore.line(ChatColor.AQUA, "Unlocks Shiny Boost I next."));
        lines.add("");
        lines.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to upgrade");
        lines.add(com.spacerng.solrng.gui.Lore.footnote("Shift-click buys as many as you can"));
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    /** The hoe's tooltip in every style, built from your own hoe. */
    boolean doHoeStyles(CommandSender sender, String[] args) {
        var farming = plugin.getFarmingManager();
        PlayerData data = sender instanceof Player p ? plugin.getPlayerDataManager().get(p.getUniqueId()) : null;
        return styleSamples(sender, args, "Hoe", "hoe-style", "hoestyles",
                com.spacerng.solrng.farming.FarmingManager.HOE_STYLES,
                style -> {
                    ItemStack hoe = farming.createBoundHoe(data);
                    var meta = hoe.getItemMeta();
                    meta.setLore(farming.hoeLore(data, style));
                    hoe.setItemMeta(meta);
                    return hoe;
                },
                () -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        farming.refreshHoe(online, plugin.getPlayerDataManager().get(online.getUniqueId()));
                    }
                });
    }

    /** One enchant card in every style, built from your own levels. */
    boolean doEnchantStyles(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Run this in game; the samples use your own enchants.");
            return true;
        }
        var hoe = plugin.getHoeEnchantManager();
        var enchant = hoe.get(args.length >= 3 ? args[2] : "TOKEN_GREED");
        if (enchant == null) enchant = hoe.getEnchants().values().iterator().next();
        final var shown = enchant;
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        return styleSamples(sender, args, "Enchant card", "enchant-style", "enchantstyles",
                com.spacerng.solrng.gui.HoeGui.ENCHANT_STYLES,
                style -> {
                    org.bukkit.Material icon = org.bukkit.Material.matchMaterial(shown.icon());
                    ItemStack item = new ItemStack(icon == null ? org.bukkit.Material.ENCHANTED_BOOK : icon);
                    var meta = item.getItemMeta();
                    meta.setDisplayName(com.spacerng.solrng.gui.Lore.title(ChatColor.YELLOW, shown.display()));
                    meta.setLore(com.spacerng.solrng.gui.HoeGui.enchantLore(plugin, data, hoe, shown, style));
                    item.setItemMeta(meta);
                    return item;
                }, null);
    }

    /** The Nova Core item in every consumable style; keys and potions follow the same style. */
    boolean doNovaStyles(CommandSender sender, String[] args) {
        var consumables = plugin.getConsumableManager();
        var core = consumables.get(args.length >= 3 ? args[2] : "nova_core");
        if (core == null) {
            sender.sendMessage(ChatColor.RED + "No consumable called " + (args.length >= 3 ? args[2] : "nova_core") + ".");
            return true;
        }
        return styleSamples(sender, args, "Consumable", "consumable-style", "novastyles",
                com.spacerng.solrng.consumable.ConsumableManager.CONSUMABLE_STYLES,
                style -> {
                    ItemStack item = consumables.build(core, 1);
                    var meta = item.getItemMeta();
                    meta.setLore(consumables.itemLore(core, style));
                    item.setItemMeta(meta);
                    return item;
                }, null);
    }

    /**
     * Shared by every *styles command: one chat line per style that shows
     * the sample item on hover, or with a style name, switch the server to it.
     */
    private boolean styleSamples(CommandSender sender, String[] args, String what, String configKey, String command,
                                 List<String> styles, java.util.function.Function<String, ItemStack> sample,
                                 Runnable onSwitch) {
        String pick = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (styles.contains(pick)) {
            plugin.getConfig().set(configKey, pick);
            plugin.saveConfig();
            if (onSwitch != null) onSwitch.run();
            sender.sendMessage(ChatColor.GREEN + what + " tooltips now use the " + pick
                    + " style. New items and reopened menus show it.");
            return true;
        }
        String current = plugin.getConfig().getString(configKey, "classic");
        sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + what + " styles " + ChatColor.GRAY + "(hover a style)");
        for (String style : styles) {
            String text = ChatColor.YELLOW + style + (style.equals(current) ? ChatColor.GREEN + "  (current)" : "")
                    + ChatColor.AQUA + "  [hover]";
            var line = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text);
            ItemStack item = sample.apply(style);
            sender.sendMessage(item == null ? line : line.hoverEvent(item.asHoverEvent()));
        }
        sender.sendMessage(ChatColor.GRAY + "Pick one with " + ChatColor.YELLOW + "/rngadmin " + command + " <style>");
        return true;
    }

    /** Your own standings card in /leaderboards, in every style. */
    boolean doStandingStyles(CommandSender sender, String[] args) {
        return styleSamples(sender, args, "Standings", "standings-style", "standingstyles",
                com.spacerng.solrng.gui.LeaderboardGui.STANDINGS_STYLES,
                style -> sender instanceof Player player
                        ? com.spacerng.solrng.gui.LeaderboardGui.selfItem(plugin, player, style) : null,
                null);
    }

    /** Every menu /rngadmin menustyles preview can open in a theme. */
    static final List<String> PREVIEW_MENUS = List.of("skilltree", "farmtree", "prestige", "index", "pass", "shop",
            "leaderboards", "options", "hoe", "novacore", "daily", "armor", "starforge", "crops", "buy", "potion",
            "perks", "stash");

    /**
     * Opens a real menu built in one theme, so the difference shows on a
     * whole screen instead of one card. Anything clicked inside rebuilds in
     * the server's current theme.
     */
    private boolean previewMenu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Run this in game to open the preview.");
            return true;
        }
        var current = com.spacerng.solrng.gui.Lore.theme();
        var theme = args.length >= 3 ? com.spacerng.solrng.gui.Lore.Theme.parse(args[2]) : current;
        String menu = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "skilltree";
        if (!PREVIEW_MENUS.contains(menu)) {
            sender.sendMessage(ChatColor.RED + "Menus: " + String.join(", ", PREVIEW_MENUS));
            return true;
        }
        org.bukkit.inventory.Inventory inventory;
        com.spacerng.solrng.gui.Lore.setTheme(theme);
        try {
            inventory = switch (menu) {
                case "farmtree" -> com.spacerng.solrng.gui.SkillTreeGui.build(plugin, player, "farmtree", 0);
                case "prestige" -> com.spacerng.solrng.gui.PrestigeGui.build(plugin, player);
                case "index" -> com.spacerng.solrng.gui.IndexGui.build(plugin, player, null, 0);
                case "pass" -> com.spacerng.solrng.gui.PassGui.build(plugin, player, 0);
                case "shop" -> com.spacerng.solrng.gui.ShopGui.build(plugin, player);
                case "leaderboards" -> com.spacerng.solrng.gui.LeaderboardGui.build(plugin, player);
                case "options" -> com.spacerng.solrng.gui.OptionsGui.build(plugin, player);
                case "hoe" -> com.spacerng.solrng.gui.HoeGui.build(plugin, player);
                case "novacore" -> com.spacerng.solrng.gui.NovaCoreGui.build(plugin, player);
                case "daily" -> com.spacerng.solrng.gui.DailyGui.build(plugin, player);
                case "armor" -> com.spacerng.solrng.gui.ArmorGui.build(plugin, player);
                case "starforge" -> com.spacerng.solrng.gui.StarforgeGui.build(plugin, player);
                case "crops" -> com.spacerng.solrng.gui.CropsGui.build(plugin, player);
                case "buy" -> com.spacerng.solrng.gui.BuyGui.build(plugin, player);
                case "potion" -> com.spacerng.solrng.gui.PotionGui.build(plugin, player);
                case "perks" -> com.spacerng.solrng.gui.PerkRollerGui.build(plugin, player);
                case "stash" -> com.spacerng.solrng.gui.StashGui.build(plugin, player);
                default -> com.spacerng.solrng.gui.SkillTreeGui.build(plugin, player, "skilltree", 0);
            };
        } finally {
            com.spacerng.solrng.gui.Lore.setTheme(current);
        }
        player.openInventory(inventory);
        sender.sendMessage(ChatColor.GRAY + "Previewing " + ChatColor.YELLOW + theme.key() + ChatColor.GRAY
                + " on " + menu + ". Clicking inside rebuilds it in the current style.");
        return true;
    }
}
