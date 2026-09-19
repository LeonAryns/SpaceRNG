package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.stats.StatSources;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Show-off tags in chat (V162): [item] becomes the held item with its
 * real tooltip on hover, and [luck], [speed], [money], [coins], [shiny],
 * [enchant], [prestige], [index], [rolls] and [stats] become the player's
 * own numbers, the same ones /stats shows.
 *
 * This works on the chat message as a component, so the item keeps its
 * hover, and it only touches the message: the tag prefix and any chat
 * format plugin still do their part of the line.
 */
public class ChatTagsListener implements Listener {

    private static final Pattern TAG = Pattern.compile(
            "(?i)\\[(item|hand|luck|speed|money|coins|shiny|enchant|prestige|index|rolls|stats)\\]");
    // A line full of tags is spam with numbers in it.
    private static final int MAX_TAGS = 4;

    private final SolRNGPlugin plugin;

    public ChatTagsListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null) return;
        event.message(event.message().replaceText(TextReplacementConfig.builder()
                .match(TAG)
                .times(MAX_TAGS)
                .replacement((match, builder) -> render(player, data,
                        match.group(1).toLowerCase(Locale.ROOT)))
                .build()));
    }

    private Component render(Player player, PlayerData data, String tag) {
        return switch (tag) {
            case "item", "hand" -> item(player);
            case "luck" -> stat(data, StatSources.Id.LUCK, "Luck", NamedTextColor.GREEN);
            case "speed" -> stat(data, StatSources.Id.SPEED, "Speed", NamedTextColor.YELLOW);
            case "money" -> stat(data, StatSources.Id.MONEY, "Money", NamedTextColor.GREEN);
            case "coins" -> stat(data, StatSources.Id.COINS, "Coins", NamedTextColor.GOLD);
            case "shiny" -> stat(data, StatSources.Id.SHINY, "Shiny", NamedTextColor.AQUA);
            case "enchant" -> stat(data, StatSources.Id.ENCHANT, "Enchant Proc", NamedTextColor.LIGHT_PURPLE);
            case "prestige" -> box("Prestige", String.valueOf(data.getPrestige()), NamedTextColor.GOLD);
            case "index" -> box("Index", data.getDiscoveredItems().size() + "/"
                    + plugin.getRarityManager().getItems().size(), NamedTextColor.AQUA);
            case "rolls" -> box("Rolls", String.format("%,d", data.getTotalRolls()), NamedTextColor.AQUA);
            default -> all(data);
        };
    }

    /** The held item by name, with the real tooltip on hover, like shift-clicking it into chat. */
    private Component item(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) return Component.text("[nothing]", NamedTextColor.DARK_GRAY);
        Component name = hand.getItemMeta() != null && hand.getItemMeta().hasDisplayName()
                ? hand.getItemMeta().displayName()
                : Component.translatable(hand.translationKey());
        Component amount = hand.getAmount() > 1
                ? Component.text(" x" + hand.getAmount(), NamedTextColor.WHITE) : Component.empty();
        return Component.text("[", NamedTextColor.WHITE).append(name == null ? Component.text("?") : name)
                .append(amount).append(Component.text("]", NamedTextColor.WHITE))
                .hoverEvent(hand.asHoverEvent());
    }

    private Component stat(PlayerData data, StatSources.Id id, String label, TextColor colour) {
        return box(label, format(StatSources.of(plugin, data, id)), colour);
    }

    /** Every stat at once, short in the line and in full on hover. */
    private Component all(PlayerData data) {
        Component hover = Component.text("Stats", NamedTextColor.LIGHT_PURPLE)
                .append(line("Luck", format(StatSources.of(plugin, data, StatSources.Id.LUCK)), NamedTextColor.GREEN))
                .append(line("Speed", format(StatSources.of(plugin, data, StatSources.Id.SPEED)), NamedTextColor.YELLOW))
                .append(line("Money", format(StatSources.of(plugin, data, StatSources.Id.MONEY)), NamedTextColor.GREEN))
                .append(line("Coins", format(StatSources.of(plugin, data, StatSources.Id.COINS)), NamedTextColor.GOLD))
                .append(line("Shiny", format(StatSources.of(plugin, data, StatSources.Id.SHINY)), NamedTextColor.AQUA))
                .append(line("Enchant Proc", format(StatSources.of(plugin, data, StatSources.Id.ENCHANT)),
                        NamedTextColor.LIGHT_PURPLE))
                .append(line("Prestige", String.valueOf(data.getPrestige()), NamedTextColor.GOLD))
                .append(line("Index", data.getDiscoveredItems().size() + "/"
                        + plugin.getRarityManager().getItems().size(), NamedTextColor.AQUA))
                .append(line("Rolls", String.format("%,d", data.getTotalRolls()), NamedTextColor.AQUA));
        return Component.text("[", NamedTextColor.WHITE)
                .append(Component.text("Stats", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("]", NamedTextColor.WHITE))
                .hoverEvent(hover);
    }

    private static Component line(String label, String value, TextColor colour) {
        return Component.newline().append(Component.text(label + ": ", NamedTextColor.WHITE))
                .append(Component.text(value, colour));
    }

    private static Component box(String label, String value, TextColor colour) {
        return Component.text("[", NamedTextColor.WHITE)
                .append(Component.text(label + ": ", NamedTextColor.WHITE))
                .append(Component.text(value, colour))
                .append(Component.text("]", NamedTextColor.WHITE));
    }

    /** The same reading /stats gives: +150% for Luck, 150 for Speed, 1.50x for the rest. */
    public static String format(StatSources.Stat stat) {
        double v = stat.total();
        return switch (stat.id()) {
            case LUCK -> "+" + String.format("%,.2f", v * 100.0) + "%";
            case SPEED -> String.format("%,d", Math.round(v * 100.0));
            case SHINY -> String.format("%.4f", v * 100.0) + "%";
            default -> String.format("%,.2f", v) + "x";
        };
    }
}
