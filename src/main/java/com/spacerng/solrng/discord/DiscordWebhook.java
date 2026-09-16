package com.spacerng.solrng.discord;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import com.spacerng.solrng.roll.RollAura;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Posts the server's big moments to a Discord channel through a webhook:
 * rare drops, shinies, Server First 10 spots and the daily farming payout.
 *
 * DiscordSRV relays chat, joins and deaths, but these announcements are
 * sent to players one by one rather than through chat, so it never sees
 * them. A webhook needs no bot and no DiscordSRV, only a URL from the
 * channel's settings, and posts an embed in the rarity's colour.
 *
 * Requests run on HttpClient's own threads, so a slow Discord never holds
 * up a tick. A failure is logged once, not on every drop.
 */
public final class DiscordWebhook {

    private static final char QUOTE = 34;
    private static final char BACKSLASH = 92;
    private static final char NEWLINE = 10;

    private final SolRNGPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private String url = "";
    private String username = "SpaceRNG";
    private final Set<Rarity> dropRarities = EnumSet.noneOf(Rarity.class);
    private boolean shiny = true;
    private boolean firstTen = true;
    private boolean payout = true;
    private volatile boolean warned = false;

    public DiscordWebhook(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        url = config.getString("discord.webhook-url", "").trim();
        username = config.getString("discord.username", "SpaceRNG");
        dropRarities.clear();
        if (config.contains("discord.announce.drop-rarities")) {
            for (String raw : config.getStringList("discord.announce.drop-rarities")) {
                try {
                    dropRarities.add(Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("discord.announce.drop-rarities has an unknown rarity: " + raw);
                }
            }
        } else {
            dropRarities.add(Rarity.MYTHICAL);
            dropRarities.add(Rarity.DIVINE);
        }
        shiny = config.getBoolean("discord.announce.shiny", true);
        firstTen = config.getBoolean("discord.announce.first-ten", true);
        payout = config.getBoolean("discord.announce.farming-payout", true);
        warned = false;
    }

    public boolean isEnabled() {
        return url.startsWith("https://");
    }

    /** A roll worth telling Discord about: one of the listed rarities, or any shiny. */
    public void drop(String player, RollableItem item, boolean isShiny) {
        if (!isEnabled()) return;
        if (!dropRarities.contains(item.getRarity()) && !(isShiny && shiny)) return;
        String rarity = item.getRarity().displayName();
        post((isShiny ? "Shiny " : "") + rarity + " drop",
                bold(player) + " rolled " + bold(plain(item.getDisplayName())) + " at "
                        + RollFormat.chance(item.getOdds()) + ".",
                isShiny ? 0x3CE8FF : colour(item.getRarity()));
    }

    /** A Server First 10 spot, never a preview. */
    public void firstTen(String player, RollableItem item, boolean isShiny, int place, int slots) {
        if (!isEnabled() || !firstTen) return;
        Rarity rarity = item.getRarity();
        String article = "AEIOU".indexOf(rarity.name().charAt(0)) >= 0 ? "an " : "a ";
        int left = Math.max(0, slots - place);
        post("Server First " + slots,
                bold(player) + " is " + bold("#" + place) + " of the first " + slots + " to find " + article
                        + rarity.displayName() + " drop: " + bold((isShiny ? "Shiny " : "") + plain(item.getDisplayName()))
                        + "." + (left > 0 ? " " + left + (left == 1 ? " spot left." : " spots left.")
                        : " Every spot is taken."),
                colour(rarity));
    }

    /** The daily farming payout, one line per winner. */
    public void payout(List<String> lines) {
        if (!isEnabled() || !payout || lines.isEmpty()) return;
        post("Daily farming payout", String.join(String.valueOf(NEWLINE), lines), 0xFFB300);
    }

    /**
     * One of the cards: the server embed, the how-to-link embed, or
     * anything else Leon writes under discord.cards, posted on command
     * rather than on an event.
     *
     * Written straight out of config rather than out of code, because the
     * whole point of it is that Leon edits the wording and posts it again
     * without a new jar. Fields are Discord's own, which is what gives the
     * card its headed sections instead of one wall of text.
     */
    public boolean card(FileConfiguration config, String id) {
        if (!isEnabled()) return false;
        String path = "discord.cards." + id;
        if (!config.contains(path)) return false;
        String title = config.getString(path + ".title", "SpaceRNG");
        String description = String.join(String.valueOf(NEWLINE),
                config.getStringList(path + ".description"));
        int colour = parseColour(config.getString(path + ".color", "#3BA55D"));

        StringBuilder fields = new StringBuilder();
        for (Map<?, ?> raw : config.getMapList(path + ".fields")) {
            Object name = raw.get("name");
            Object value = raw.get("value");
            if (name == null || value == null) continue;
            String text = value instanceof List<?> lines
                    ? String.join(String.valueOf(NEWLINE), lines.stream().map(String::valueOf).toList())
                    : String.valueOf(value);
            if (fields.length() > 0) fields.append(',');
            fields.append('{').append(key("name")).append(json(String.valueOf(name))).append(',')
                    .append(key("value")).append(json(text)).append(',')
                    .append(key("inline")).append(raw.get("inline") == Boolean.TRUE).append('}');
        }

        StringBuilder embed = new StringBuilder("{")
                .append(key("title")).append(json(title)).append(',')
                .append(key("color")).append(colour);
        if (!description.isBlank()) {
            embed.append(',').append(key("description")).append(json(description));
        }
        if (fields.length() > 0) {
            embed.append(',').append(key("fields")).append('[').append(fields).append(']');
        }
        embed.append('}');

        send("{" + key("username") + json(username) + "," + key("embeds") + "[" + embed + "]}");
        return true;
    }

    /** "#RRGGBB" or a plain number, falling back to Discord's green. */
    private static int parseColour(String raw) {
        String clean = raw == null ? "" : raw.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        try {
            return Integer.parseInt(clean, 16);
        } catch (NumberFormatException ex) {
            return 0x3BA55D;
        }
    }

    private void post(String title, String description, int colour) {
        send("{" + key("username") + json(username) + "," + key("embeds") + "[{"
                + key("title") + json(title) + "," + key("description") + json(description) + ","
                + key("color") + colour + "}]}");
    }

    private void send(String body) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException ex) {
            warnOnce("the URL is not valid");
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                warnOnce(error.getMessage());
            } else if (response.statusCode() >= 300) {
                warnOnce("HTTP " + response.statusCode());
            }
        });
    }

    private void warnOnce(String reason) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("Discord webhook post failed (" + reason + "). Check discord.webhook-url.");
    }

    public static String bold(String text) {
        return "**" + text + "**";
    }

    private static String plain(String text) {
        String stripped = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
        return stripped == null ? text : stripped;
    }

    private static int colour(Rarity rarity) {
        return RollAura.colorFor(rarity).asRGB();
    }

    private static String key(String name) {
        return QUOTE + name + QUOTE + ":";
    }

    /** A JSON string literal, with quotes, backslashes and line breaks escaped. */
    private static String json(String text) {
        StringBuilder out = new StringBuilder().append(QUOTE);
        for (char c : text.toCharArray()) {
            if (c == QUOTE || c == BACKSLASH) {
                out.append(BACKSLASH).append(c);
            } else if (c == NEWLINE) {
                out.append(BACKSLASH).append('n');
            } else if (c < 32) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.append(QUOTE).toString();
    }
}
