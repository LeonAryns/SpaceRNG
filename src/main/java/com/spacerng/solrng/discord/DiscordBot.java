package com.spacerng.solrng.discord;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.stats.StatSources;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The SpaceRNG bot, running inside the plugin since V230.
 *
 * Until V229 this rode on DiscordSRV's connection, and DiscordSRV ships a
 * JDA too old to open a modal, which is the one thing Leon asked for: a
 * button under the link card that asks for the code. So the plugin now
 * logs in with its own token (discord.bot.token) on JDA 5, shaded into the
 * jar and relocated. DiscordSRV is only needed for chat between the game
 * and Discord, and can use the same token.
 *
 * What it does:
 *   - the link card with a Link account button; the button opens a box for
 *     the code /link printed in game. A code posted on its own in the link
 *     channel, and the /link slash command, do the same.
 *   - roles that follow the rank and the link, both ways: a link from the
 *     Discord side gives the Linked rank in game (the rank is derived from
 *     the link), and every rank change moves the Discord role.
 *   - /stats, /top, /online, /boss and /link.
 *   - any card from discord.cards, posted as the bot and edited in place.
 *
 * Everything that reads game state runs on the main thread and hands only
 * the finished answer back to Discord. JDA is shut down in onDisable, or a
 * hot reload would leave the old bot connected beside the new one.
 */
public class DiscordBot extends ListenerAdapter implements BotHooks {

    private static final String LINK_BUTTON = "spacerng:link";
    private static final String LINK_MODAL = "spacerng:link-modal";
    private static final String LINK_FIELD = "code";

    private final SolRNGPlugin plugin;
    private final LinkStore links;
    private volatile JDA jda;
    private volatile boolean ready;
    // What is wrong right now, in words Leon can act on. Shown by
    // /rngadmin discord status and by every command that needs the bot.
    private volatile String problem = "Not started yet.";
    // The line under the bot's name in Discord (V241): "3 on ...".
    private org.bukkit.scheduler.BukkitTask statusTask;
    private String lastStatus = "";

    // Config
    private String token = "";
    private String guildId = "";
    private String commandChannel = "";
    private String linkedRole = "";
    private final Map<String, String> rankRoles = new LinkedHashMap<>();
    private boolean removeOldRoles = true;
    private String linkChannel = "";
    private String cardsChannel = "";
    private String linkButton = "Link account";

    // Roles this plugin made itself and the cards it posted, in their own
    // file. Writing them into config.yml would mean rewriting it, and
    // Bukkit's writer throws away every comment in the file.
    private final java.io.File file;
    private final Map<String, String> madeRoles = new LinkedHashMap<>();
    private final Map<String, String> posted = new LinkedHashMap<>();

    public DiscordBot(SolRNGPlugin plugin, LinkStore links) {
        this.plugin = plugin;
        this.links = links;
        this.file = new java.io.File(plugin.getDataFolder(), "discord.yml");
        load();
    }

    public final void load() {
        var config = plugin.getConfig();
        token = config.getString("discord.bot.token", "").trim();
        guildId = config.getString("discord.bot.guild-id", "").trim();
        commandChannel = config.getString("discord.bot.command-channel", "").trim();
        linkedRole = config.getString("discord.bot.linked-role", "").trim();
        removeOldRoles = config.getBoolean("discord.bot.remove-old-roles", true);
        linkChannel = config.getString("discord.bot.link-channel", "").trim();
        cardsChannel = config.getString("discord.bot.cards-channel", "").trim();
        linkButton = config.getString("discord.bot.link-button", "Link account");
        rankRoles.clear();
        madeRoles.clear();
        posted.clear();
        if (file.exists()) {
            var yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            var made = yml.getConfigurationSection("rank-roles");
            if (made != null) {
                for (String rank : made.getKeys(false)) {
                    String id = made.getString(rank, "").trim();
                    if (!id.isEmpty()) madeRoles.put(rank.toLowerCase(Locale.ROOT), id);
                }
            }
            rankRoles.putAll(madeRoles);
            String linked = yml.getString("linked-role", "").trim();
            if (!linked.isEmpty() && linkedRole.isEmpty()) linkedRole = linked;
            var cards = yml.getConfigurationSection("posted");
            if (cards != null) {
                for (String card : cards.getKeys(false)) posted.put(card, cards.getString(card, ""));
            }
        }
        // Anything written by hand in config wins over what was made.
        ConfigurationSection section = config.getConfigurationSection("discord.bot.rank-roles");
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                String id = section.getString(rank, "").trim();
                if (!id.isEmpty()) rankRoles.put(rank.toLowerCase(Locale.ROOT), id);
            }
        }
        if (linkedRole.isEmpty() && rankRoles.containsKey("linked")) linkedRole = rankRoles.get("linked");
    }

    private synchronized void saveMade() {
        var yml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, String> entry : madeRoles.entrySet()) {
            yml.set("rank-roles." + entry.getKey(), entry.getValue());
        }
        String madeLinked = madeRoles.get("linked");
        if (madeLinked != null) yml.set("linked-role", madeLinked);
        for (Map.Entry<String, String> entry : posted.entrySet()) {
            yml.set("posted." + entry.getKey(), entry.getValue());
        }
        try {
            yml.save(file);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Discord bot: could not save discord.yml (" + ex.getMessage() + ").");
        }
    }

    // ---------------------------------------------------------------
    // Connecting
    // ---------------------------------------------------------------

    /**
     * Logs in off the main thread. Without a token nothing happens, which is
     * the state of every server until Leon pastes one in.
     */
    public void start() {
        if (!plugin.getConfig().getBoolean("discord.bot.enabled", true)) {
            problem = "discord.bot.enabled is false in config.yml.";
            return;
        }
        if (token.isEmpty()) {
            problem = "No token: discord.bot.token in plugins/SpaceRNG/config.yml is empty.";
            plugin.getLogger().info("Discord bot: no discord.bot.token set, so the bot stays offline.");
            return;
        }
        problem = "Logging in...";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                jda = JDABuilder.createLight(token,
                                GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT)
                        .setMemberCachePolicy(MemberCachePolicy.NONE)
                        .addEventListeners(this)
                        .build();
            } catch (Throwable t) {
                problem = "Login failed: " + t.getMessage()
                        + ". Is the token right? Copy it again from Developer Portal, Bot, Reset Token.";
                plugin.getLogger().warning("Discord bot: " + problem);
            }
        });
    }

    /** Logs out and in again with whatever config says now. */
    @Override
    public void restart() {
        shutdown();
        load();
        start();
    }

    @Override
    public void onReady(ReadyEvent event) {
        connectGuild();
    }

    /** Invited while already running: no restart needed. */
    @Override
    public void onGuildJoin(net.dv8tion.jda.api.events.guild.GuildJoinEvent event) {
        if (!ready) connectGuild();
    }

    /**
     * Discord closed the connection. The close code says why, and the two
     * that matter here each have one fix.
     */
    @Override
    public void onShutdown(net.dv8tion.jda.api.events.session.ShutdownEvent event) {
        ready = false;
        var code = event.getCloseCode();
        if (code == null) return;
        if (code == net.dv8tion.jda.api.requests.CloseCode.DISALLOWED_INTENTS) {
            problem = "Discord refused the intents. Developer Portal, your app, Bot: switch on Server Members"
                    + " Intent and Message Content Intent, Save, then /rngadmin discord restart.";
        } else if (code == net.dv8tion.jda.api.requests.CloseCode.AUTHENTICATION_FAILED) {
            problem = "The token is wrong. Reset it in the Developer Portal and paste the new one.";
        } else {
            problem = "Discord closed the connection: " + code.getMeaning();
        }
        plugin.getLogger().warning("Discord bot: " + problem);
    }

    private void connectGuild() {
        Guild guild = guild();
        if (guild == null) {
            problem = "Logged in, but the bot is not in your Discord server. Invite it with the"
                    + " OAuth2 URL Generator link (scopes bot and applications.commands).";
            plugin.getLogger().warning("Discord bot: " + problem);
            return;
        }
        List<CommandData> commands = List.of(
                Commands.slash("stats", "Somebody's SpaceRNG stats")
                        .addOption(OptionType.STRING, "player", "Whose stats, defaults to your own", false),
                Commands.slash("top", "A SpaceRNG leaderboard")
                        .addOption(OptionType.STRING, "board", "Which board, defaults to farming", false),
                Commands.slash("online", "Who is playing right now"),
                Commands.slash("boss", "The boss event, and when the next one is"),
                Commands.slash("link", "Link your Minecraft account")
                        .addOption(OptionType.STRING, "code", "The code /link gave you in game", false));
        // One at a time rather than updateCommands(), which replaces the
        // whole list and would wipe any other bot's commands on a shared app.
        for (CommandData command : commands) {
            guild.upsertCommand(command).queue(null, error -> plugin.getLogger().warning(
                    "Discord bot: could not register /" + command.getName() + " (" + error.getMessage()
                            + "). Invite the bot with the applications.commands scope."));
        }
        ready = true;
        problem = "";
        startStatus();
        plugin.getLogger().info("Discord bot: online in " + guild.getName() + ", "
                + commands.size() + " slash commands sent.");
        plugin.getServer().getScheduler().runTask(plugin, this::syncAll);
    }

    private Guild guild() {
        JDA current = jda;
        if (current == null) return null;
        if (!guildId.isEmpty()) {
            Guild guild = current.getGuildById(guildId);
            if (guild != null) return guild;
        }
        return current.getGuilds().isEmpty() ? null : current.getGuilds().get(0);
    }

    /**
     * Keeps the bot's custom status on the player count, like the bot Leon
     * pointed at ("73 on MoneyMC.net"). Checked every 30 seconds and only
     * sent when it changed, because Discord limits presence updates to
     * five a minute.
     */
    private void startStatus() {
        String format = plugin.getConfig().getString("discord.bot.status", "{online} on spacerng.minehut.gg");
        if (format == null || format.isBlank() || statusTask != null) return;
        statusTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            JDA current = jda;
            if (current == null) return;
            int online = Bukkit.getOnlinePlayers().size();
            String text = format.replace("{online}", String.valueOf(online))
                    .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
            if (text.equals(lastStatus)) return;
            lastStatus = text;
            try {
                current.getPresence().setActivity(net.dv8tion.jda.api.entities.Activity.customStatus(text));
            } catch (Throwable ignored) {
                // Presence is cosmetic; never worth an error.
            }
        }, 20L, 600L);
    }

    @Override
    public void shutdown() {
        if (statusTask != null) {
            statusTask.cancel();
            statusTask = null;
        }
        lastStatus = "";
        JDA current = jda;
        jda = null;
        ready = false;
        if (current == null) return;
        try {
            current.removeEventListener(this);
            current.shutdownNow();
        } catch (Throwable ignored) {
            // On the way down, with Discord already gone, this is noise.
        }
    }

    // ---------------------------------------------------------------
    // Linking
    // ---------------------------------------------------------------

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!LINK_BUTTON.equals(event.getComponentId())) return;
        if (links.playerOf(event.getUser().getId()) != null) {
            UUID uuid = links.playerOf(event.getUser().getId());
            event.reply("Your Discord is already linked to **" + safe(Bukkit.getOfflinePlayer(uuid).getName())
                    + "**.").setEphemeral(true).queue();
            return;
        }
        TextInput code = TextInput.create(LINK_FIELD, "Your code from /link in game", TextInputStyle.SHORT)
                .setPlaceholder("123456")
                .setMinLength(6)
                .setMaxLength(6)
                .setRequired(true)
                .build();
        event.replyModal(Modal.create(LINK_MODAL, "Link your Minecraft account")
                .addComponents(ActionRow.of(code))
                .build()).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!LINK_MODAL.equals(event.getModalId())) return;
        var value = event.getValue(LINK_FIELD);
        String answer = tryLink(event.getUser().getId(), value == null ? "" : value.getAsString());
        event.reply(answer).setEphemeral(true).queue();
    }

    /**
     * Links the Discord user to whoever /link gave this code to. Called on a
     * JDA thread; the game side of it is handed to the main thread.
     */
    private String tryLink(String discordId, String code) {
        String clean = code == null ? "" : code.trim();
        if (links.playerOf(discordId) != null) {
            return "Your Discord is already linked to **"
                    + safe(Bukkit.getOfflinePlayer(links.playerOf(discordId)).getName()) + "**.";
        }
        if (!clean.matches("\\d{6}")) return "A code is six numbers. Type `/link` in game to get one.";
        UUID uuid = links.redeem(clean);
        if (uuid == null) return "That code is not valid or ran out. Type `/link` in game for a new one.";
        links.link(uuid, discordId);
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getLinkedAccountManager().checkNow();
            syncRoles(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) plugin.getRankManager().refreshName(online);
        });
        double luck = plugin.getLinkedAccountManager().luckBonus();
        return "\u2705 Linked to **" + safe(name) + "**."
                + (luck > 0 ? " You now roll with **+" + LinkedAccountManager.percent(luck)
                        + " Luck** in game, for as long as you stay linked." : "");
    }

    /**
     * A code posted on its own in the link channel links too. Everything
     * posted there is removed, so the card stays the only thing in it, and
     * the answer goes away after fifteen seconds.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (linkChannel.isEmpty() || !linkChannel.equals(event.getChannel().getId())) return;
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;
        String text = event.getMessage().getContentRaw().trim();
        String userId = event.getAuthor().getId();
        String reply = text.matches("\\d{6}") ? tryLink(userId, text)
                : "Post only the code, or press **" + linkButton + "** on the card above.";
        event.getMessage().delete().queue(null, error -> { });
        event.getChannel().sendMessage("<@" + userId + "> " + reply).queue(
                sent -> sent.delete().queueAfter(15, TimeUnit.SECONDS, null, error -> { }), error -> { });
    }

    // ---------------------------------------------------------------
    // Slash commands
    // ---------------------------------------------------------------

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String name = event.getName();
        if (name.equals("link")) {
            String code = option(event, "code");
            if (code != null) {
                event.reply(tryLink(event.getUser().getId(), code)).setEphemeral(true).queue();
            } else {
                event.replyEmbeds(linkEmbed()).addActionRow(Button.success(LINK_BUTTON, linkButton))
                        .setEphemeral(true).queue();
            }
            return;
        }
        if (!commandChannel.isEmpty() && !commandChannel.equals(event.getChannel().getId())) {
            event.reply("Use the commands channel for that.").setEphemeral(true).queue();
            return;
        }
        String argument = option(event, "player");
        String board = option(event, "board");
        String userId = event.getUser().getId();
        event.deferReply().queue();

        // Player data belongs to the server thread.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            MessageEmbed embed;
            try {
                embed = switch (name) {
                    case "stats" -> statsEmbed(userId, argument);
                    case "top" -> topEmbed(board);
                    case "online" -> onlineEmbed();
                    case "boss" -> bossEmbed();
                    default -> simple("Unknown command", "That command is not one of mine.");
                };
            } catch (Throwable t) {
                plugin.getLogger().warning("Discord bot: /" + name + " failed (" + t + ").");
                embed = simple("Something went wrong", "The server could not answer that one.");
            }
            event.getHook().editOriginalEmbeds(embed).queue(null, error -> { });
        });
    }

    private String option(SlashCommandInteractionEvent event, String key) {
        OptionMapping mapping = event.getOption(key);
        return mapping == null ? null : mapping.getAsString();
    }

    private MessageEmbed statsEmbed(String discordId, String wanted) {
        UUID uuid = null;
        String name = wanted;
        if (wanted == null || wanted.isBlank()) {
            uuid = links.playerOf(discordId);
            if (uuid == null) {
                return simple("Not linked", "Link your account first, or name a player: `/stats player:Name`.");
            }
            name = Bukkit.getOfflinePlayer(uuid).getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(wanted);
            if (offline != null) uuid = offline.getUniqueId();
        }
        if (uuid == null) return simple("Never seen", "Nobody called " + safe(name) + " has played here.");

        PlayerData data = plugin.getPlayerDataManager().get(uuid);
        EmbedBuilder embed = new EmbedBuilder().setTitle(safe(name)).setColor(0xC77DFF);
        embed.addField("Luck", percent(StatSources.of(plugin, data, StatSources.Id.LUCK).total()), true);
        embed.addField("Speed", String.format("%.2f", StatSources.of(plugin, data, StatSources.Id.SPEED).total()), true);
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
        List<String> rows = new ArrayList<>();
        int place = 1;
        for (var entry : plugin.getLeaderboardManager().top(board, 10)) {
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
        if (online.isEmpty()) return simple("Nobody online", "The server is quiet right now.");
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
        if (boss.isActive()) return simple("A boss is up right now", "Get in game and start harvesting.");
        long minutes = boss.minutesToNext();
        return simple("No boss right now", minutes < 0
                ? "The timer is off at the moment."
                : "The next one is due in about " + minutes + " minutes.");
    }

    private MessageEmbed linkEmbed() {
        ConfigurationSection card = plugin.getConfig().getConfigurationSection("discord.cards.link");
        if (card == null) return simple("How to link", "Type `/link` in game, then press the button and enter the code.");
        return cardEmbed(card);
    }

    // ---------------------------------------------------------------
    // Cards
    // ---------------------------------------------------------------

    /** One of the config cards as an embed, the same shape the webhook posts. */
    private MessageEmbed cardEmbed(ConfigurationSection card) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(fill(card.getString("title", "SpaceRNG")))
                .setColor(colourOf(card.getString("color", "#C77DFF")));
        List<String> description = card.getStringList("description");
        if (description.isEmpty() && card.isString("description")) description = List.of(card.getString("description"));
        if (!description.isEmpty()) embed.setDescription(fill(String.join("\n", description)));
        for (Map<?, ?> raw : card.getMapList("fields")) {
            Object name = raw.get("name");
            Object value = raw.get("value");
            if (name == null || value == null) continue;
            String text = value instanceof List<?> lines
                    ? String.join("\n", lines.stream().map(String::valueOf).toList())
                    : String.valueOf(value);
            embed.addField(fill(String.valueOf(name)), fill(text), raw.get("inline") == Boolean.TRUE);
        }
        String footer = card.getString("footer", "");
        if (!footer.isBlank()) embed.setFooter(fill(footer));
        String image = card.getString("image", "");
        if (!image.isBlank()) embed.setImage(image);
        String thumbnail = card.getString("thumbnail", "");
        if (!thumbnail.isBlank()) embed.setThumbnail(thumbnail);
        return embed.build();
    }

    /** {link-channel} becomes a clickable mention of the link channel. */
    private String fill(String text) {
        if (text == null) return "";
        return text.replace("{link-channel}", linkChannel.isEmpty() ? "the link channel" : "<#" + linkChannel + ">")
                .replace("{link-button}", linkButton);
    }

    /**
     * Posts a config card as the bot, or edits the copy it posted before.
     * A card with link-button: true carries the Link account button. With
     * no channel named, the link card goes to the link channel and any
     * other card to cards-channel.
     */
    @Override
    public void postCard(String id, String channelId, Consumer<String> say) {
        Guild guild = guild();
        if (!ready || guild == null) {
            say.accept("The bot is not connected. " + problem);
            return;
        }
        ConfigurationSection card = plugin.getConfig().getConfigurationSection("discord.cards." + id);
        if (card == null) {
            say.accept("There is no card called " + id + " under discord.cards.");
            return;
        }
        String previous = posted.getOrDefault(id, "");
        String[] parts = previous.split(":", 2);
        String wanted = channelId != null && !channelId.isBlank() ? channelId.trim()
                : parts.length == 2 ? parts[0]
                : id.equals("link") && !linkChannel.isEmpty() ? linkChannel : cardsChannel;
        if (wanted.isEmpty()) {
            say.accept("Name a channel id, or set discord.bot.cards-channel in config.");
            return;
        }
        TextChannel channel = guild.getTextChannelById(wanted);
        if (channel == null) {
            say.accept("The bot cannot see a text channel with id " + wanted + ".");
            return;
        }
        MessageEmbed embed = cardEmbed(card);
        boolean button = card.getBoolean("link-button", id.equals("link"));
        List<ActionRow> rows = button ? List.of(ActionRow.of(Button.success(LINK_BUTTON, linkButton))) : List.of();
        Runnable fresh = () -> channel.sendMessageEmbeds(embed).setComponents(rows).queue(message -> {
            posted.put(id, channel.getId() + ":" + message.getId());
            saveMade();
            say.accept("Posted " + id + " in #" + channel.getName() + ".");
        }, error -> say.accept("Could not post: " + error.getMessage()
                + ". The bot needs Send Messages and Embed Links there."));
        if (parts.length == 2 && parts[0].equals(channel.getId())) {
            channel.editMessageEmbedsById(parts[1], embed).setComponents(rows).queue(
                    message -> say.accept("Updated " + id + " in #" + channel.getName() + "."),
                    error -> fresh.run());
        } else {
            fresh.run();
        }
    }

    // ---------------------------------------------------------------
    // Roles
    // ---------------------------------------------------------------

    /**
     * One role the server should have, from discord.roles in config, in
     * the order they should stand in the list, top first.
     */
    private record RoleSpec(String key, String name, String emoji, List<String> colors,
                            boolean hoist, String rank, boolean auto) {
        String fullName() {
            return emoji.isEmpty() ? name : emoji + " " + name;
        }
    }

    private List<RoleSpec> roleSpecs() {
        List<RoleSpec> specs = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("discord.roles");
        if (section == null) return specs;
        for (String key : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(key);
            if (r == null) continue;
            String rank = r.getString("rank", "").toLowerCase(Locale.ROOT);
            List<String> colors = r.getStringList("colors");
            if (colors.isEmpty() && !rank.isEmpty() && plugin.getRankManager().tier(rank) != null) {
                colors = plugin.getRankManager().tier(rank).colors();
            }
            if (colors.isEmpty()) colors = List.of("#B0BEC5");
            specs.add(new RoleSpec(key.toLowerCase(Locale.ROOT), r.getString("name", key), r.getString("emoji", ""),
                    colors, r.getBoolean("hoist", true), rank, r.getBoolean("auto", false)));
        }
        return specs;
    }

    /**
     * Builds the server's roles from discord.roles: finds each one by the
     * id it had, or by its name with or without the emoji, or makes it,
     * then gives it its name, emoji, colour and place in the list. A role
     * adopted by name keeps its members, so DiscordSRV's old Linked role
     * simply becomes ours. Running it twice is safe.
     *
     * Colours: a gradient when the server has Discord's enhanced role
     * colours (a boosted server), otherwise the middle of the gradient as
     * one colour. The emoji also goes on as the role icon when the server
     * is boosted far enough to allow icons.
     */
    @Override
    public void setupRoles(Consumer<String> say) {
        Guild guild = guild();
        if (!ready || guild == null) {
            say.accept("The bot is not connected. " + problem);
            return;
        }
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            say.accept("The bot has no Manage Roles permission in Discord.");
            return;
        }
        List<RoleSpec> specs = roleSpecs();
        if (specs.isEmpty()) {
            say.accept("There is no discord.roles section in config.yml.");
            return;
        }
        if (!guild.getFeatures().contains("ENHANCED_ROLE_COLORS")) {
            say.accept("This server cannot show role gradients yet: Discord only allows them on a boosted"
                    + " server with enhanced role colours. Every role gets one colour from its gradient"
                    + " for now; run setup again once the server has it and they turn into gradients.");
        }
        List<java.util.concurrent.CompletableFuture<Role>> found = new ArrayList<>();
        for (RoleSpec spec : specs) {
            Role role = findRole(guild, spec);
            if (role != null) {
                found.add(java.util.concurrent.CompletableFuture.completedFuture(role));
            } else {
                found.add(guild.createRole().setName(spec.fullName()).submit());
            }
        }
        java.util.concurrent.CompletableFuture.allOf(found.toArray(new java.util.concurrent.CompletableFuture[0]))
                .whenComplete((ignored, error) -> {
                    List<Role> roles = new ArrayList<>();
                    for (int i = 0; i < specs.size(); i++) {
                        Role role = found.get(i).getNow(null);
                        if (role == null || found.get(i).isCompletedExceptionally()) {
                            say.accept("Could not make " + specs.get(i).fullName() + ".");
                            continue;
                        }
                        style(guild, specs.get(i), role, say);
                        remember(specs.get(i), role);
                        roles.add(role);
                    }
                    saveMade();
                    order(guild, roles, say);
                    giveAutoRoles(guild);
                    say.accept("Roles done: " + roles.size() + " of " + specs.size() + ".");
                    plugin.getServer().getScheduler().runTask(plugin, this::syncAll);
                });
    }

    private Role findRole(Guild guild, RoleSpec spec) {
        String stored = madeRoles.get("role:" + spec.key());
        if (stored == null && !spec.rank().isEmpty()) stored = madeRoles.get(spec.rank());
        if (stored != null && guild.getRoleById(stored) != null) return guild.getRoleById(stored);
        for (String name : List.of(spec.fullName(), spec.name())) {
            List<Role> byName = guild.getRolesByName(name, true);
            if (!byName.isEmpty()) return byName.get(0);
        }
        return null;
    }

    private void style(Guild guild, RoleSpec spec, Role role, Consumer<String> say) {
        if (!guild.getSelfMember().canInteract(role)) {
            say.accept(role.getName() + " sits above the bot's own role. Drag the bot's role to the top and run setup again.");
            return;
        }
        int solid = colourOf(spec.colors().get(spec.colors().size() / 2));
        var manager = role.getManager().setName(spec.fullName()).setColor(solid).setHoisted(spec.hoist());
        boolean icons = guild.getFeatures().contains("ROLE_ICONS");
        if (icons && !spec.emoji().isEmpty()) {
            try {
                manager = manager.setIcon(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode(spec.emoji()));
            } catch (Throwable ignored) {
                // Not every emoji is allowed as an icon; the name still has it.
            }
        }
        manager.queue(null, error -> say.accept("Could not style " + spec.fullName() + ": " + error.getMessage()));
        if (guild.getFeatures().contains("ENHANCED_ROLE_COLORS")) {
            // Not in JDA 5 yet, so the one field is sent by hand. A role
            // with a single colour fades into a lighter copy of it, so
            // every role is a gradient.
            int first = colourOf(spec.colors().get(0));
            int last = spec.colors().size() >= 2 ? colourOf(spec.colors().get(spec.colors().size() - 1)) : lighter(first);
            var colors = net.dv8tion.jda.api.utils.data.DataObject.empty()
                    .put("primary_color", first)
                    .put("secondary_color", last);
            var body = net.dv8tion.jda.api.utils.data.DataObject.empty().put("colors", colors);
            new net.dv8tion.jda.internal.requests.RestActionImpl<Void>(guild.getJDA(),
                    net.dv8tion.jda.api.requests.Route.Roles.MODIFY_ROLE.compile(guild.getId(), role.getId()), body)
                    .queue(null, error -> { });
        }
    }

    private void remember(RoleSpec spec, Role role) {
        madeRoles.put("role:" + spec.key(), role.getId());
        if (!spec.rank().isEmpty()) {
            madeRoles.put(spec.rank(), role.getId());
            rankRoles.put(spec.rank(), role.getId());
            if (spec.rank().equals("linked") && plugin.getConfig().getString("discord.bot.linked-role", "").isBlank()) {
                linkedRole = role.getId();
            }
        }
    }

    /**
     * Puts our roles straight under the bot's own, in config order, and
     * leaves everything else where it was relative to each other.
     */
    private void order(Guild guild, List<Role> ours, Consumer<String> say) {
        try {
            Role top = guild.getSelfMember().getRoles().isEmpty() ? null : guild.getSelfMember().getRoles().get(0);
            List<Role> current = new ArrayList<>(guild.getRoles());
            current.remove(guild.getPublicRole());
            List<Role> desired = new ArrayList<>();
            for (Role role : current) {
                if (top != null && role.getPosition() >= top.getPosition() && !ours.contains(role)) desired.add(role);
            }
            desired.addAll(ours);
            for (Role role : current) if (!desired.contains(role)) desired.add(role);
            guild.modifyRolePositions(false)
                    .sortOrder(java.util.Comparator.comparingInt(desired::indexOf))
                    .queue(null, error -> say.accept("Could not reorder the roles: " + error.getMessage()));
        } catch (Throwable t) {
            say.accept("Could not reorder the roles: " + t.getMessage());
        }
    }

    /** Roles with auto: true (Member) go to everybody who does not have them. */
    private void giveAutoRoles(Guild guild) {
        List<Role> auto = autoRoles(guild);
        if (auto.isEmpty()) return;
        guild.loadMembers().onSuccess(members -> {
            for (Member member : members) {
                if (member.getUser().isBot()) continue;
                for (Role role : auto) {
                    if (!member.getRoles().contains(role)) guild.addRoleToMember(member, role).queue(null, error -> { });
                }
            }
        });
    }

    private List<Role> autoRoles(Guild guild) {
        List<Role> auto = new ArrayList<>();
        for (RoleSpec spec : roleSpecs()) {
            if (!spec.auto()) continue;
            String id = madeRoles.get("role:" + spec.key());
            Role role = id == null ? null : guild.getRoleById(id);
            if (role != null) auto.add(role);
        }
        return auto;
    }

    /** Somebody new joins the Discord: they get Member straight away. */
    @Override
    public void onGuildMemberJoin(net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent event) {
        if (event.getUser().isBot()) return;
        for (Role role : autoRoles(event.getGuild())) {
            event.getGuild().addRoleToMember(event.getMember(), role).queue(null, error -> { });
        }
    }

    /**
     * Every role that is not one of ours: not @everyone, not a bot's own
     * managed role, and below the bot so it could be removed at all.
     */
    private List<Role> strangers(Guild guild) {
        java.util.Set<String> ours = new java.util.HashSet<>();
        for (Map.Entry<String, String> entry : madeRoles.entrySet()) {
            if (entry.getKey().startsWith("role:")) ours.add(entry.getValue());
        }
        List<Role> strangers = new ArrayList<>();
        for (Role role : guild.getRoles()) {
            if (role.isPublicRole() || role.isManaged() || ours.contains(role.getId())) continue;
            if (!guild.getSelfMember().canInteract(role)) continue;
            strangers.add(role);
        }
        return strangers;
    }

    /**
     * /rngadmin discord cleanup lists what would go; with confirm it goes.
     * Deleting a role cannot be undone, which is why the list comes first.
     */
    @Override
    public void cleanupRoles(boolean confirm, Consumer<String> say) {
        Guild guild = guild();
        if (!ready || guild == null) {
            say.accept("The bot is not connected. " + problem);
            return;
        }
        if (madeRoles.keySet().stream().noneMatch(k -> k.startsWith("role:"))) {
            say.accept("Run /rngadmin discord setup first, so it knows which roles are ours.");
            return;
        }
        List<Role> strangers = strangers(guild);
        if (strangers.isEmpty()) {
            say.accept("Nothing to remove: every role left is ours, a bot's, or above the bot.");
            return;
        }
        List<String> names = new ArrayList<>();
        for (Role role : strangers) names.add(role.getName());
        if (!confirm) {
            say.accept("These " + strangers.size() + " roles would be deleted: " + String.join(", ", names));
            say.accept("Roles above the bot's own and bots' roles are left alone.");
            say.accept("Type /rngadmin discord cleanup confirm to delete them. This cannot be undone.");
            return;
        }
        for (Role role : strangers) {
            role.delete().queue(done -> say.accept("Deleted " + role.getName() + "."),
                    error -> say.accept("Could not delete " + role.getName() + ": " + error.getMessage()));
        }
    }

    @Override
    public void syncAll() {
        for (Player player : Bukkit.getOnlinePlayers()) syncRoles(player);
    }

    @Override
    public void syncRoles(Player player) {
        syncRoles(player.getUniqueId());
    }

    /** Main thread only: it reads player data. */
    @Override
    public void syncRoles(UUID uuid) {
        Guild guild = guild();
        if (!ready || guild == null) return;
        try {
            String discordId = links.discordOf(uuid);
            if (discordId == null) return;
            PlayerData data = plugin.getPlayerDataManager().get(uuid);
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
            // The Linked role is also a rank role; a linked Supernova keeps it.
            unwanted.removeAll(wanted);
            if (wanted.isEmpty() && unwanted.isEmpty()) return;
            guild.retrieveMemberById(discordId).queue(member -> {
                for (Role role : wanted) {
                    if (!member.getRoles().contains(role)) guild.addRoleToMember(member, role).queue(null, error -> { });
                }
                for (Role role : unwanted) {
                    if (member.getRoles().contains(role)) guild.removeRoleFromMember(member, role).queue(null, error -> { });
                }
            }, error -> {
                // Linked, but not in the server any more.
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Discord bot: role sync failed for " + uuid + " (" + t + ").");
        }
    }

    /** Takes the Linked role off an account that was just unlinked. */
    @Override
    public void clearRoles(String discordId) {
        Guild guild = guild();
        if (!ready || guild == null || discordId == null) return;
        List<Role> all = new ArrayList<>();
        if (!linkedRole.isEmpty() && guild.getRoleById(linkedRole) != null) all.add(guild.getRoleById(linkedRole));
        for (String id : rankRoles.values()) {
            Role role = guild.getRoleById(id);
            if (role != null && !all.contains(role)) all.add(role);
        }
        guild.retrieveMemberById(discordId).queue(member -> {
            for (Role role : all) {
                if (member.getRoles().contains(role)) guild.removeRoleFromMember(member, role).queue(null, error -> { });
            }
        }, error -> { });
    }

    @Override
    public boolean isOnline() {
        return ready && jda != null;
    }

    /** Everything worth knowing when the bot does not answer. */
    @Override
    public void status(Consumer<String> say) {
        JDA current = jda;
        say.accept("Token: " + (token.isEmpty() ? "empty" : "set (" + token.length() + " characters)"));
        say.accept("Connection: " + (current == null ? "none" : current.getStatus().name()));
        if (current != null && current.getStatus() == JDA.Status.CONNECTED) {
            List<String> names = new ArrayList<>();
            for (Guild guild : current.getGuilds()) names.add(guild.getName() + " (" + guild.getId() + ")");
            say.accept("Servers: " + (names.isEmpty() ? "none, invite the bot" : String.join(", ", names)));
            say.accept("Bot account: " + current.getSelfUser().getName());
        }
        say.accept(ready ? "Ready." : "Problem: " + problem);
    }

    // ---------------------------------------------------------------
    // Small pieces
    // ---------------------------------------------------------------

    private static MessageEmbed simple(String title, String text) {
        return new EmbedBuilder().setTitle(title).setDescription(text).setColor(0xC77DFF).build();
    }

    private static String percent(double value) {
        return "+" + String.format("%.1f", value * 100.0) + "%";
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

    /** Halfway to white, for the second stop of a one colour role. */
    private static int lighter(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((r + (255 - r) / 2) << 16) | ((g + (255 - g) / 2) << 8) | (b + (255 - b) / 2);
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
