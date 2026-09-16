package com.spacerng.solrng.discord;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.stats.StatSources;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Role;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionMapping;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Discord side of the server: slash commands, and roles that follow
 * what a player has in game.
 *
 * It is not a bot of its own. DiscordSRV already runs one, already holds
 * the connection and already knows which Discord account belongs to which
 * player, so this rides on that instead of asking Leon to host a second
 * program and keep a second token alive. The commands appear under his
 * existing bot.
 *
 * Everything here is written against the JDA that DiscordSRV ships
 * relocated inside its own jar, and the whole class is only ever loaded
 * when the DiscordSRV plugin is present. Every call into Discord is
 * wrapped, because an outage on their side must never reach a player
 * standing in the farm.
 *
 * Work that touches the game runs on the main thread and is handed back
 * to Discord afterwards: a slash command arrives on a JDA thread, and
 * reading player data from there would be a race with every roll on the
 * server.
 */
public class DiscordBot extends ListenerAdapter implements BotHooks {

    private final SolRNGPlugin plugin;
    private boolean commandsRegistered;
    private BukkitTask connectTask;

    // Config
    private boolean enabled;
    private String commandChannel = "";
    private String linkedRole = "";
    private final Map<String, String> rankRoles = new LinkedHashMap<>();
    private boolean removeOldRoles = true;

    public DiscordBot(SolRNGPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public final void load() {
        var config = plugin.getConfig();
        enabled = config.getBoolean("discord.bot.enabled", true);
        commandChannel = config.getString("discord.bot.command-channel", "").trim();
        linkedRole = config.getString("discord.bot.linked-role", "").trim();
        removeOldRoles = config.getBoolean("discord.bot.remove-old-roles", true);
        rankRoles.clear();
        ConfigurationSection section = config.getConfigurationSection("discord.bot.rank-roles");
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                String id = section.getString(rank, "").trim();
                if (!id.isEmpty()) rankRoles.put(rank.toLowerCase(Locale.ROOT), id);
            }
        }
    }

    /**
     * Waits for DiscordSRV to finish connecting, then registers.
     *
     * Polled rather than listened for: DiscordSRV's own ready event has
     * moved between versions, and a check every five seconds for two
     * minutes costs nothing and cannot break on an upgrade.
     */
    public void start() {
        if (!enabled) return;
        connectTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tries = 0;

            @Override
            public void run() {
                if (commandsRegistered || ++tries > 24) {
                    if (connectTask != null) connectTask.cancel();
                    return;
                }
                JDA jda = jda();
                if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) return;
                register(jda);
                if (connectTask != null) connectTask.cancel();
            }
        }, 200L, 100L);
    }

    private void register(JDA jda) {
        try {
            jda.addEventListener(this);
            Guild guild = DiscordSRV.getPlugin().getMainGuild();
            if (guild == null) {
                plugin.getLogger().warning("Discord bot: DiscordSRV has no main guild, no commands registered.");
                return;
            }
            CommandData stats = new CommandData("stats", "Somebody's SpaceRNG stats")
                    .addOption(OptionType.STRING, "player", "Whose stats, defaults to your own", false);
            CommandData top = new CommandData("top", "A SpaceRNG leaderboard")
                    .addOption(OptionType.STRING, "board", "Which board, defaults to farming", false);
            guild.updateCommands()
                    .addCommands(stats, top,
                            new CommandData("online", "Who is playing right now"),
                            new CommandData("boss", "The boss event, and when the next one is"),
                            new CommandData("link", "How to link your Minecraft account"))
                    .queue(ok -> plugin.getLogger().info("Discord bot: slash commands registered."),
                            error -> plugin.getLogger().warning("Discord bot: could not register commands ("
                                    + error.getMessage()
                                    + "). The bot needs the applications.commands scope on its invite."));
            commandsRegistered = true;
            syncAll();
        } catch (Throwable t) {
            plugin.getLogger().warning("Discord bot: registration failed (" + t + ").");
        }
    }

    @Override
    public void shutdown() {
        if (connectTask != null) connectTask.cancel();
        try {
            JDA jda = jda();
            if (jda != null) jda.removeEventListener(this);
        } catch (Throwable ignored) {
            // On the way down, with Discord already gone, this is noise.
        }
    }

    // ---------------------------------------------------------------
    // Slash commands
    // ---------------------------------------------------------------

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (!commandChannel.isEmpty() && !commandChannel.equals(event.getChannel().getId())) {
            event.reply("Use the commands channel for that.").setEphemeral(true).queue();
            return;
        }
        String name = event.getName();
        String argument = option(event, "player");
        String board = option(event, "board");
        event.deferReply().queue();

        // Player data belongs to the server thread. Everything below is
        // read there and only the finished embed goes back to Discord.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            MessageEmbed embed;
            try {
                embed = switch (name) {
                    case "stats" -> statsEmbed(event.getUser().getId(), argument);
                    case "top" -> topEmbed(board);
                    case "online" -> onlineEmbed();
                    case "boss" -> bossEmbed();
                    case "link" -> linkEmbed();
                    default -> simple("Unknown command", "That command is not one of mine.");
                };
            } catch (Throwable t) {
                plugin.getLogger().warning("Discord bot: /" + name + " failed (" + t + ").");
                embed = simple("Something went wrong", "The server could not answer that one.");
            }
            MessageEmbed finished = embed;
            try {
                event.getHook().editOriginalEmbeds(finished).queue();
            } catch (Throwable ignored) {
                // The interaction expired, which is Discord's problem now.
            }
        });
    }

    private String option(SlashCommandEvent event, String key) {
        OptionMapping mapping = event.getOption(key);
        return mapping == null ? null : mapping.getAsString();
    }

    private MessageEmbed statsEmbed(String discordId, String wanted) {
        UUID uuid = null;
        String name = wanted;
        if (wanted == null || wanted.isBlank()) {
            // No name given: whoever is asking, if they have linked.
            uuid = linkedUuid(discordId);
            if (uuid == null) {
                return simple("Not linked",
                        "Link your account first, or name a player: `/stats player:Name`.");
            }
            name = Bukkit.getOfflinePlayer(uuid).getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(wanted);
            if (offline != null) uuid = offline.getUniqueId();
        }
        if (uuid == null) {
            return simple("Never seen", "Nobody called " + safe(name) + " has played here.");
        }

        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(safe(name))
                .setColor(0xC77DFF);
        embed.addField("Luck", percent(StatSources.of(plugin, data, StatSources.Id.LUCK).total()), true);
        embed.addField("Speed", format(StatSources.of(plugin, data, StatSources.Id.SPEED).total()), true);
        embed.addField("Rolls", String.format("%,d", data.getTotalRolls()), true);
        embed.addField("Index", data.getDiscoveredItems().size() + " found", true);
        embed.addField("Prestige", String.valueOf(data.getPrestige()), true);
        embed.addField("Crops", String.format("%,d", data.getCropsHarvested()), true);
        var rank = plugin.getRankManager().rankOf(data);
        embed.setFooter(rank == null ? "No rank" : "Rank: " + strip(rank.display()));
        return embed.build();
    }

    private MessageEmbed topEmbed(String wanted) {
        String board = wanted == null || wanted.isBlank() ? "farming" : wanted.toLowerCase(Locale.ROOT);
        if (!com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS.contains(board)) {
            return simple("Unknown board", "Pick one of: "
                    + String.join(", ", com.spacerng.solrng.leaderboard.LeaderboardManager.BOARDS));
        }
        var manager = plugin.getLeaderboardManager();
        List<String> rows = new ArrayList<>();
        int place = 1;
        for (var entry : manager.top(board, 10)) {
            rows.add("**" + place + ".** " + safe(entry.name()) + "  "
                    + String.format("%,d", com.spacerng.solrng.leaderboard.LeaderboardManager.valueOf(board, entry)));
            place++;
        }
        return new EmbedBuilder()
                .setTitle(strip(com.spacerng.solrng.leaderboard.LeaderboardManager.titleOf(board)))
                .setDescription(rows.isEmpty() ? "Nobody is on this board yet." : String.join("\n", rows))
                .setColor(0xFFD54F)
                .build();
    }

    private MessageEmbed onlineEmbed() {
        var online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            return simple("Nobody online", "The server is quiet right now.");
        }
        List<String> names = new ArrayList<>();
        for (Player player : online) names.add(safe(player.getName()));
        return new EmbedBuilder()
                .setTitle(online.size() + (online.size() == 1 ? " player online" : " players online"))
                .setDescription(String.join(", ", names))
                .setColor(0x43A047)
                .build();
    }

    private MessageEmbed bossEmbed() {
        var boss = plugin.getBossManager();
        if (boss.isActive()) {
            return simple("A boss is up right now", "Get in game and start harvesting.");
        }
        long minutes = boss.minutesToNext();
        return simple("No boss right now", minutes < 0
                ? "The timer is off at the moment."
                : "The next one is due in about " + minutes + " minutes.");
    }

    private MessageEmbed linkEmbed() {
        ConfigurationSection card = plugin.getConfig().getConfigurationSection("discord.cards.link");
        if (card == null) {
            return simple("How to link", "Type `/discord link` in game and send the code here.");
        }
        return cardEmbed(card);
    }

    /** One of the config cards as an embed, the same shape the webhook posts. */
    private MessageEmbed cardEmbed(ConfigurationSection card) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(card.getString("title", "SpaceRNG"))
                .setColor(colourOf(card.getString("color", "#C77DFF")));
        List<String> description = card.getStringList("description");
        if (!description.isEmpty()) embed.setDescription(String.join("\n", description));
        for (Map<?, ?> raw : card.getMapList("fields")) {
            Object name = raw.get("name");
            Object value = raw.get("value");
            if (name == null || value == null) continue;
            String text = value instanceof List<?> lines
                    ? String.join("\n", lines.stream().map(String::valueOf).toList())
                    : String.valueOf(value);
            embed.addField(String.valueOf(name), text, raw.get("inline") == Boolean.TRUE);
        }
        return embed.build();
    }

    // ---------------------------------------------------------------
    // Roles
    // ---------------------------------------------------------------

    @Override
    public void syncAll() {
        for (Player player : Bukkit.getOnlinePlayers()) syncRoles(player);
    }

    /**
     * Gives the roles the player has earned and takes back the ones they
     * have not.
     *
     * The member lookup is a request rather than a cache read, because a
     * Discord member who has not spoken since the bot started is not in
     * the cache and would silently never get their role.
     */
    @Override
    public void syncRoles(Player player) {
        if (!enabled || !commandsRegistered) return;
        try {
            String discordId = DiscordSRV.getPlugin().getAccountLinkManager()
                    .getDiscordId(player.getUniqueId());
            if (discordId == null) return;
            Guild guild = DiscordSRV.getPlugin().getMainGuild();
            if (guild == null) return;

            PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
            var rank = plugin.getRankManager().rankOf(data);
            String rankId = rank == null ? "" : rank.id();

            List<Role> wanted = new ArrayList<>();
            List<Role> unwanted = new ArrayList<>();
            if (!linkedRole.isEmpty()) {
                Role role = guild.getRoleById(linkedRole);
                if (role != null) wanted.add(role);
            }
            for (Map.Entry<String, String> entry : rankRoles.entrySet()) {
                Role role = guild.getRoleById(entry.getValue());
                if (role == null) continue;
                if (entry.getKey().equals(rankId)) wanted.add(role);
                else if (removeOldRoles) unwanted.add(role);
            }
            if (wanted.isEmpty() && unwanted.isEmpty()) return;

            guild.retrieveMemberById(discordId).queue(member -> apply(guild, member, wanted, unwanted),
                    error -> {
                        // Linked, but not in the guild any more. Nothing to do.
                    });
        } catch (Throwable t) {
            plugin.getLogger().warning("Discord bot: role sync failed for " + player.getName() + " (" + t + ").");
        }
    }

    private void apply(Guild guild, Member member, List<Role> wanted, List<Role> unwanted) {
        try {
            for (Role role : wanted) {
                if (!member.getRoles().contains(role)) guild.addRoleToMember(member, role).queue(null, error -> {
                    // Almost always the bot's own role sitting below this one.
                });
            }
            for (Role role : unwanted) {
                if (member.getRoles().contains(role)) guild.removeRoleFromMember(member, role).queue(null, error -> {
                });
            }
        } catch (Throwable ignored) {
            // Permissions, mostly. Never worth a stack trace on a join.
        }
    }

    // ---------------------------------------------------------------
    // Small pieces
    // ---------------------------------------------------------------

    private UUID linkedUuid(String discordId) {
        try {
            return DiscordSRV.getPlugin().getAccountLinkManager().getUuid(discordId);
        } catch (Throwable t) {
            return null;
        }
    }

    private JDA jda() {
        try {
            return DiscordSRV.getPlugin() == null ? null : DiscordSRV.getPlugin().getJda();
        } catch (Throwable t) {
            return null;
        }
    }

    private static MessageEmbed simple(String title, String text) {
        return new EmbedBuilder().setTitle(title).setDescription(text).setColor(0xC77DFF).build();
    }

    private static String percent(double value) {
        return "+" + String.format("%.1f", value * 100.0) + "%";
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private static String strip(String text) {
        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
        return plain == null ? text : plain;
    }

    /** Markdown a player could have put in their name is not markup here. */
    private static String safe(String text) {
        if (text == null) return "?";
        StringBuilder out = new StringBuilder();
        for (char c : strip(text).toCharArray()) {
            if (c == '*' || c == '_' || c == '`' || c == '~' || c == '|' || c == '\\') out.append('\\');
            out.append(c);
        }
        return out.toString();
    }

    private static int colourOf(String hex) {
        String clean = hex == null ? "" : hex.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        try {
            return Integer.parseInt(clean, 16);
        } catch (NumberFormatException ex) {
            return 0xC77DFF;
        }
    }
}
