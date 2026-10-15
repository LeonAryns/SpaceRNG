package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.clan.Clan;
import com.spacerng.solrng.clan.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /clan (V393, Leon).
 *
 *   main   the clan card on top, the members in two rows of five (open
 *          seats as stone buttons, seats the clan has not bought yet as
 *          locked ones), and the buttons along the bottom
 *   top    every clan by level, rarest first, the way /top reads
 *
 * A player in no clan gets the leaderboard, with how to make one and any
 * invites waiting for them along the bottom.
 */
public final class ClanGui {

    private static final int SIZE = 54;
    private static final int CARD_SLOT = 4;
    private static final int[] MEMBER_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    private static final int[] TOP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int[] INVITE_SLOTS = {37, 38, 39, 40, 41, 42, 43};
    public static final int CHEST_SLOT = 46;
    public static final int UPGRADES_SLOT = 47;
    public static final int TOP_SLOT = 51;
    public static final int LEAVE_SLOT = 52;
    public static final int BACK_SLOT = 45;

    private ClanGui() {
    }

    public static NamespacedKey joinKey() {
        return SolRNGPlugin.key("solrng_clan_join");
    }

    public static String clickedJoin(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        return item.getItemMeta().getPersistentDataContainer().get(joinKey(), PersistentDataType.STRING);
    }

    /** /clan: your clan, or the leaderboard when you have none. */
    public static Inventory open(SolRNGPlugin plugin, Player player) {
        Clan clan = plugin.getClanManager().of(player.getUniqueId());
        return clan == null ? top(plugin, player) : main(plugin, player, clan);
    }

    public static Inventory main(SolRNGPlugin plugin, Player player, Clan clan) {
        ClanManager clans = plugin.getClanManager();
        ClanHolder holder = new ClanHolder(ClanHolder.View.MAIN);
        Inventory inv = Bukkit.createInventory(holder, SIZE, MenuStyle.title("Clan", "#80D8FF", "#448AFF"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(CARD_SLOT, card(plugin, clan, true));

        List<UUID> members = new ArrayList<>(clan.members());
        // The owner first, then everyone in the order they joined.
        members.remove(clan.owner());
        members.add(0, clan.owner());
        int seats = clans.slots(clan);
        for (int i = 0; i < MEMBER_SLOTS.length; i++) {
            if (i < members.size()) {
                inv.setItem(MEMBER_SLOTS[i], memberHead(clan, members.get(i)));
            } else if (i < seats) {
                inv.setItem(MEMBER_SLOTS[i], seat(clan.isOwner(player.getUniqueId()), true));
            } else {
                inv.setItem(MEMBER_SLOTS[i], seat(false, false));
            }
        }

        inv.setItem(TOP_SLOT, topButton(plugin, clan));
        inv.setItem(LEAVE_SLOT, leaveButton(clan.isOwner(player.getUniqueId())));
        MenuStyle.apply(inv, MenuStyle.Palette.BLUE);
        return inv;
    }

    /** Every clan by level. */
    public static Inventory top(SolRNGPlugin plugin, Player player) {
        ClanManager clans = plugin.getClanManager();
        Clan mine = clans.of(player.getUniqueId());
        ClanHolder holder = new ClanHolder(ClanHolder.View.TOP);
        Inventory inv = Bukkit.createInventory(holder, SIZE, MenuStyle.title("Clan Top", "#80D8FF", "#448AFF"));
        holder.setInventory(inv);
        fill(inv);

        List<Clan> ranked = clans.top();
        for (int i = 0; i < TOP_SLOTS.length && i < ranked.size(); i++) {
            inv.setItem(TOP_SLOTS[i], card(plugin, ranked.get(i), ranked.get(i) == mine));
        }
        if (ranked.isEmpty()) {
            ItemStack none = new ItemStack(Material.STONE_BUTTON);
            ItemMeta meta = none.getItemMeta();
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "No clans yet"));
            meta.setLore(List.of(Lore.line(ChatColor.GRAY, "Be the first: /clan create <name>")));
            none.setItemMeta(meta);
            inv.setItem(22, none);
        }

        if (mine == null) {
            inv.setItem(CARD_SLOT, howTo(plugin));
            List<Clan> invites = clans.invitesFor(player.getUniqueId());
            for (int i = 0; i < INVITE_SLOTS.length && i < invites.size(); i++) {
                inv.setItem(INVITE_SLOTS[i], invite(plugin, invites.get(i)));
            }
        } else {
            inv.setItem(BACK_SLOT, back());
        }
        MenuStyle.apply(inv, MenuStyle.Palette.BLUE);
        return inv;
    }

    // ------------------------------------------------------------ items

    /** A clan as one card: its name, rank, level, XP and members. */
    static ItemStack card(SolRNGPlugin plugin, Clan clan, boolean yours) {
        ClanManager clans = plugin.getClanManager();
        int level = clans.levelOf(clan);
        long into = clans.xpIntoLevel(clan);
        long need = clans.xpFor(level);

        ItemStack item = new ItemStack(Material.BLUE_BANNER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient(clan.name(), true, "#80D8FF", "#448AFF"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Clan, #" + clans.rankOf(clan) + " by level");
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "Level", String.valueOf(level)));
        lore.add(Lore.stat(ChatColor.AQUA, "XP", RollFormatShort.of(into) + " / " + RollFormatShort.of(need)));
        lore.add(Lore.bar(need <= 0 ? 0.0 : into / (double) need));
        lore.add(Lore.stat(ChatColor.AQUA, "Members", clan.members().size() + " / " + clans.slots(clan)));
        lore.add(Lore.stat(ChatColor.AQUA, "Owner", nameOf(clan.owner())));
        lore.add("");
        lore.add(Lore.footnote("Members earn it XP the way the"));
        lore.add(Lore.footnote("battle pass does: rolls and crops."));
        meta.setLore(lore);
        if (yours) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack memberHead(Clan clan, UUID uuid) {
        OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
        boolean online = member.isOnline();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(member);
        meta.setDisplayName(Lore.title(online ? ChatColor.GREEN : ChatColor.GRAY, nameOf(uuid)));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + (clan.isOwner(uuid) ? "Owner" : "Member"));
        lore.add("");
        lore.add(Lore.stat(online ? ChatColor.GREEN : ChatColor.DARK_GRAY, "Status", online ? "Online" : "Offline"));
        meta.setLore(lore);
        if (clan.isOwner(uuid)) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack seat(boolean owner, boolean open) {
        ItemStack item = new ItemStack(Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, open ? "Open seat" : "Locked seat"));
        List<String> lore = new ArrayList<>();
        if (open) {
            lore.add(Lore.line(ChatColor.GRAY, "Room for one more member."));
            if (owner) {
                lore.add("");
                lore.add(Lore.footnote("Invite with /clan invite <player>"));
            }
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
            lore.add(Lore.line(ChatColor.GRAY, "Buy a member slot in the clan upgrades"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack topButton(SolRNGPlugin plugin, Clan clan) {
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.GOLD, "Clan leaderboard"));
        meta.setLore(List.of(
                ChatColor.DARK_GRAY + "Every clan by level",
                "",
                Lore.stat(ChatColor.AQUA, "Your clan", "#" + plugin.getClanManager().rankOf(clan)),
                "",
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open"));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack leaveButton(boolean owner) {
        ItemStack item = new ItemStack(owner ? Material.TNT : Material.OAK_DOOR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.RED, owner ? "Disband clan" : "Leave clan"));
        meta.setLore(owner
                ? List.of(Lore.line(ChatColor.GRAY, "Ends the clan for everyone."),
                        Lore.line(ChatColor.GRAY, "Hand it over first with"),
                        Lore.line(ChatColor.GRAY, "/clan transfer <player> to keep it."),
                        "",
                        ChatColor.RED + "" + ChatColor.BOLD + "Shift click to disband")
                : List.of(Lore.line(ChatColor.GRAY, "You can be invited back."),
                        "",
                        ChatColor.RED + "" + ChatColor.BOLD + "Shift click to leave"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack howTo(SolRNGPlugin plugin) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "No clan yet"));
        meta.setLore(List.of(
                ChatColor.DARK_GRAY + "Clans",
                "",
                Lore.line(ChatColor.GRAY, "Team up with up to "
                        + plugin.getConfig().getInt("clans.base-members", 5) + " players and"),
                Lore.line(ChatColor.GRAY, "level a clan together."),
                "",
                Lore.section(ChatColor.YELLOW, "Commands"),
                Lore.line(ChatColor.GRAY, "/clan create <name>, /clan join <name>")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack invite(SolRNGPlugin plugin, Clan clan) {
        ItemStack item = card(plugin, clan, false);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Invited you");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to join");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.getPersistentDataContainer().set(joinKey(), PersistentDataType.STRING, clan.name());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack back() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Your clan"));
        meta.setLore(List.of(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to go back"));
        item.setItemMeta(meta);
        return item;
    }

    static String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? "Unknown" : name;
    }

    static void fill(Inventory inv) {
        ItemStack pane = MenuStyle.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    /** "12.4K" for XP numbers. */
    static final class RollFormatShort {
        static String of(long value) {
            return com.spacerng.solrng.rarity.RollFormat.abbreviate(value);
        }
    }
}
