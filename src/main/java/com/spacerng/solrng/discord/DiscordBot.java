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
        if (!plugin.getConfig().getBoolean("discord.bot.enabled", true)) return;
        if (token.isEmpty()) {
            plugin.getLogger().info("Discord bot: no discord.bot.token set, so the bot stays offline.");
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                jda = JDABuilder.createLight(token,
                                GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT)
                        .setMemberCachePolicy(MemberCachePolicy.NONE)
                        .addEventListeners(this)
                        .build();
            } catch (Throwable t) {
                plugin.getLogger().warning("Discord bot: could not log in (" + t.getMessage()
                        + "). Check discord.bot.token, and that Server Members Intent and Message"
                        + " Content Intent are switched on in the Developer Portal.");
            }
        });
    }

    @Override
    public void onReady(ReadyEvent event) {
        Guild guild = guild();
        if (guild == null) {
            plugin.getLogger().warning("Discord bot: logged in, but it is not in any server. Invite it first.");
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

    @Override
    public void shutdown() {
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
        return "Linked to **" + safe(name) + "**. Your Linked rank and rewards follow in a few seconds.";
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
            say.accept("The bot is not connected to Discord. Check discord.bot.token and the console.");
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
     * Makes the rank roles in Discord and remembers their ids. A role that
     * already exists by name is adopted rather than duplicated, so running
     * it twice is safe. A bot can only hand out roles BELOW its own.
     */
    @Override
    public void setupRoles(Consumer<String> say) {
        Guild guild = guild();
        if (!ready || guild == null) {
            say.accept("The bot is not connected to Discord. Check discord.bot.token and the console.");
            return;
        }
        Member self = guild.getSelfMember();
        if (!self.hasPermission(Permission.MANAGE_ROLES)) {
            say.accept("The bot has no Manage Roles permission in Discord.");
            return;
        }
        for (var tier : plugin.getRankManager().tiers()) {
            String id = tier.id();
            if (rankRoles.containsKey(id)) {
                Role existing = guild.getRoleById(rankRoles.get(id));
                if (existing != null) {
                    say.accept("Already have a role for " + id + ": " + existing.getName());
                    continue;
                }
            }
            String name = strip(tier.display());
            List<Role> byName = guild.getRolesByName(name, true);
            if (!byName.isEmpty()) {
                adopt(id, byName.get(0), say, "adopted");
                continue;
            }
            int colour = colourOf(tier.colors().isEmpty() ? "#FFFFFF" : tier.colors().get(0));
            guild.createRole().setName(name).setColor(colour).setHoisted(true).queue(
                    role -> adopt(id, role, say, "created"),
                    error -> say.accept("Could not create the role for " + id + ": " + error.getMessage()));
        }
    }

    private void adopt(String rank, Role role, Consumer<String> say, String what) {
        rankRoles.put(rank, role.getId());
        madeRoles.put(rank, role.getId());
        if (rank.equals("linked") && plugin.getConfig().getString("discord.bot.linked-role", "").isBlank()) {
            linkedRole = role.getId();
        }
        saveMade();
        say.accept(what + " the role " + role.getName() + " for " + rank + ".");
        plugin.getServer().getScheduler().runTask(plugin, this::syncAll);
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
