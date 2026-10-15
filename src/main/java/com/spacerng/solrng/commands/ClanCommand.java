package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.clan.Clan;
import com.spacerng.solrng.clan.ClanManager;
import com.spacerng.solrng.gui.ClanGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /clan (V393, Leon). With no arguments it opens the clan screen; the
 * rest are the actions, each answering in one line.
 */
public class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "create", "invite", "join", "leave", "kick", "transfer", "disband", "top", "info");

    private final SolRNGPlugin plugin;

    public ClanCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players are in clans.");
            return true;
        }
        ClanManager clans = plugin.getClanManager();
        if (args.length == 0) {
            player.openInventory(ClanGui.open(plugin, player));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String arg = args.length > 1 ? args[1] : null;
        UUID me = player.getUniqueId();

        switch (sub) {
            case "create" -> {
                if (arg == null) {
                    usage(player, "/clan create <name>");
                    return true;
                }
                ClanManager.Result result = clans.create(me, arg);
                if (result == ClanManager.Result.DONE) {
                    player.sendMessage(ChatColor.GREEN + "Clan " + ChatColor.WHITE + arg + ChatColor.GREEN
                            + " made. " + ChatColor.GRAY + "Invite players with "
                            + ChatColor.YELLOW + "/clan invite <player>" + ChatColor.GRAY + ".");
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
                } else {
                    fail(player, result);
                }
            }
            case "invite" -> {
                Player target = arg == null ? null : Bukkit.getPlayerExact(arg);
                if (target == null) {
                    usage(player, "/clan invite <online player>");
                    return true;
                }
                ClanManager.Result result = clans.invite(me, target.getUniqueId());
                if (result == ClanManager.Result.DONE) {
                    Clan clan = clans.of(me);
                    player.sendMessage(ChatColor.GREEN + "Invited " + ChatColor.WHITE + target.getName()
                            + ChatColor.GREEN + ". " + ChatColor.GRAY + "It lasts five minutes.");
                    target.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "CLAN " + ChatColor.RESET
                            + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " invited you to "
                            + ChatColor.WHITE + clan.name() + ChatColor.GRAY + ". Type "
                            + ChatColor.YELLOW + "/clan join " + clan.name() + ChatColor.GRAY + ".");
                    target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
                } else {
                    fail(player, result);
                }
            }
            case "join" -> {
                if (arg == null) {
                    usage(player, "/clan join <clan>");
                    return true;
                }
                join(player, arg);
            }
            case "leave" -> {
                Clan clan = clans.of(me);
                ClanManager.Result result = clans.leave(me);
                if (result == ClanManager.Result.DONE) {
                    player.sendMessage(ChatColor.GRAY + "You left " + ChatColor.WHITE + clan.name() + ChatColor.GRAY + ".");
                    tell(clan, ChatColor.WHITE + player.getName() + ChatColor.GRAY + " left the clan.");
                } else {
                    fail(player, result);
                }
            }
            case "kick" -> {
                UUID target = uuidOf(arg);
                if (target == null) {
                    usage(player, "/clan kick <member>");
                    return true;
                }
                Clan clan = clans.of(me);
                ClanManager.Result result = clans.kick(me, target);
                if (result == ClanManager.Result.DONE) {
                    tell(clan, ChatColor.WHITE + arg + ChatColor.GRAY + " was removed from the clan.");
                    Player kicked = Bukkit.getPlayer(target);
                    if (kicked != null) kicked.sendMessage(ChatColor.RED + "You were removed from "
                            + ChatColor.WHITE + clan.name() + ChatColor.RED + ".");
                } else {
                    fail(player, result);
                }
            }
            case "transfer" -> {
                UUID target = uuidOf(arg);
                if (target == null) {
                    usage(player, "/clan transfer <member>");
                    return true;
                }
                Clan clan = clans.of(me);
                ClanManager.Result result = clans.transfer(me, target);
                if (result == ClanManager.Result.DONE) {
                    tell(clan, ChatColor.WHITE + arg + ChatColor.GRAY + " owns the clan now.");
                } else {
                    fail(player, result);
                }
            }
            case "disband" -> {
                Clan clan = clans.of(me);
                if (clan != null && clan.isOwner(me) && (arg == null || !arg.equalsIgnoreCase("confirm"))) {
                    player.sendMessage(ChatColor.RED + "This ends " + clan.name() + " for everyone. "
                            + ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/clan disband confirm" + ChatColor.GRAY + ".");
                    return true;
                }
                disband(player);
            }
            case "top" -> player.openInventory(ClanGui.top(plugin, player));
            case "info" -> {
                Clan clan = arg == null ? clans.of(me) : clans.byName(arg);
                if (clan == null) {
                    player.sendMessage(ChatColor.RED + (arg == null ? "You are not in a clan." : "No clan called " + arg + "."));
                    return true;
                }
                List<String> names = new ArrayList<>();
                for (UUID member : clan.members()) {
                    OfflinePlayer off = Bukkit.getOfflinePlayer(member);
                    names.add((off.isOnline() ? ChatColor.GREEN : ChatColor.GRAY) + (off.getName() == null ? "?" : off.getName()));
                }
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + clan.name() + ChatColor.RESET
                        + ChatColor.GRAY + "  level " + ChatColor.YELLOW + clans.levelOf(clan)
                        + ChatColor.GRAY + ", #" + clans.rankOf(clan));
                player.sendMessage(ChatColor.GRAY + "Members: " + String.join(ChatColor.DARK_GRAY + ", ", names));
            }
            default -> help(player);
        }
        return true;
    }

    /** Joining, shared with the invite card in the menu. */
    public static void join(SolRNGPlugin plugin, Player player, String name) {
        ClanManager clans = plugin.getClanManager();
        ClanManager.Result result = clans.join(player.getUniqueId(), name);
        if (result == ClanManager.Result.DONE) {
            Clan clan = clans.of(player.getUniqueId());
            for (UUID member : clan.members()) {
                Player online = Bukkit.getPlayer(member);
                if (online != null) online.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "CLAN "
                        + ChatColor.RESET + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " joined "
                        + ChatColor.WHITE + clan.name() + ChatColor.GRAY + ".");
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
        } else {
            fail(player, result);
        }
    }

    private void join(Player player, String name) {
        join(plugin, player, name);
    }

    /** Disbanding, shared with the menu's shift click. */
    public static void disband(SolRNGPlugin plugin, Player player) {
        ClanManager clans = plugin.getClanManager();
        Clan clan = clans.of(player.getUniqueId());
        List<UUID> members = clan == null ? List.of() : new ArrayList<>(clan.members());
        ClanManager.Result result = clans.disband(player.getUniqueId());
        if (result == ClanManager.Result.DONE) {
            for (UUID member : members) {
                Player online = Bukkit.getPlayer(member);
                if (online != null) online.sendMessage(ChatColor.RED + "Clan " + ChatColor.WHITE + clan.name()
                        + ChatColor.RED + " was disbanded.");
            }
        } else {
            fail(player, result);
        }
    }

    private void disband(Player player) {
        disband(plugin, player);
    }

    /** Leaving, shared with the menu's shift click. */
    public static void leave(SolRNGPlugin plugin, Player player) {
        ClanManager clans = plugin.getClanManager();
        Clan clan = clans.of(player.getUniqueId());
        ClanManager.Result result = clans.leave(player.getUniqueId());
        if (result == ClanManager.Result.DONE) {
            player.sendMessage(ChatColor.GRAY + "You left " + ChatColor.WHITE + clan.name() + ChatColor.GRAY + ".");
            for (UUID member : clan.members()) {
                Player online = Bukkit.getPlayer(member);
                if (online != null) online.sendMessage(ChatColor.WHITE + player.getName()
                        + ChatColor.GRAY + " left the clan.");
            }
        } else {
            fail(player, result);
        }
    }

    private void tell(Clan clan, String line) {
        if (clan == null) return;
        for (UUID member : clan.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) online.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "CLAN " + ChatColor.RESET + line);
        }
    }

    private static void fail(Player player, ClanManager.Result result) {
        String line = switch (result) {
            case ALREADY_IN_CLAN -> "Already in a clan.";
            case NOT_IN_CLAN -> "You are not in a clan.";
            case NAME_TAKEN -> "That name is taken.";
            case BAD_NAME -> "A clan name is 3 to 16 letters, digits or underscores.";
            case NOT_OWNER -> "Only the clan owner can do that.";
            case FULL -> "The clan is full. More seats come from the clan upgrades.";
            case NO_INVITE -> "You have no invite from that clan.";
            case NOT_MEMBER -> "That player is not in your clan.";
            case OWNER_CANNOT_LEAVE -> "The owner cannot leave. Transfer the clan or disband it.";
            case SELF -> "Not on yourself.";
            default -> "That did not work.";
        };
        player.sendMessage(ChatColor.RED + line);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }

    private static UUID uuidOf(String name) {
        if (name == null) return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(off.getName())) return off.getUniqueId();
        }
        return null;
    }

    private static void usage(Player player, String line) {
        player.sendMessage(ChatColor.RED + "Use " + ChatColor.YELLOW + line + ChatColor.RED + ".");
    }

    private void help(Player player) {
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "CLANS");
        player.sendMessage(ChatColor.YELLOW + "/clan" + ChatColor.GRAY + "  your clan, or the leaderboard");
        player.sendMessage(ChatColor.YELLOW + "/clan create <name>" + ChatColor.GRAY + "  start one");
        player.sendMessage(ChatColor.YELLOW + "/clan invite <player>" + ChatColor.GRAY + "  owner only");
        player.sendMessage(ChatColor.YELLOW + "/clan join <clan>" + ChatColor.GRAY + "  accept an invite");
        player.sendMessage(ChatColor.YELLOW + "/clan leave, kick, transfer, disband, top, info");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(sub);
            return out;
        }
        if (args.length == 2 && sender instanceof Player player) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String typed = args[1].toLowerCase(Locale.ROOT);
            ClanManager clans = plugin.getClanManager();
            switch (sub) {
                case "invite" -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getName().toLowerCase(Locale.ROOT).startsWith(typed)) out.add(online.getName());
                    }
                }
                case "kick", "transfer" -> {
                    Clan clan = clans.of(player.getUniqueId());
                    if (clan != null) {
                        for (UUID member : clan.members()) {
                            String name = Bukkit.getOfflinePlayer(member).getName();
                            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(name);
                        }
                    }
                }
                case "join" -> {
                    for (Clan clan : clans.invitesFor(player.getUniqueId())) out.add(clan.name());
                }
                case "info" -> {
                    for (Clan clan : clans.all()) {
                        if (clan.id().startsWith(typed)) out.add(clan.name());
                    }
                }
                default -> {
                }
            }
        }
        return out;
    }
}
