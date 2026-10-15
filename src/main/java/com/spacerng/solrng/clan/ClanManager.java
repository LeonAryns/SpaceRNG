package com.spacerng.solrng.clan;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Clans (V393, Leon): make one, invite up to five players (ten with the
 * member slot upgrade), and climb a clan level with the same XP the
 * battle pass pays: every roll by its rarity, every crop farmed.
 *
 * Everything lives in clans.yml. XP changes on every roll, so the file
 * is written when something structural changes (a member, a name) and
 * otherwise once a minute while anything is dirty, and on disable.
 *
 * The level curve is open ended, unlike the pass: level L to L+1 costs
 * clans.level-xp x clans.level-growth^L.
 */
public final class ClanManager {

    /** An invite waits this long. */
    private static final long INVITE_MILLIS = 5 * 60 * 1000L;

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<String, Clan> clans = new HashMap<>();
    private final Map<UUID, String> byMember = new HashMap<>();
    /** Invitee to (clan id, when it runs out). */
    private final Map<UUID, Map<String, Long>> invites = new HashMap<>();
    private boolean dirty;

    public ClanManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        load();
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) save();
        }, 1200L, 1200L);
    }

    // ------------------------------------------------------------ lookup

    public Clan of(UUID player) {
        String id = byMember.get(player);
        return id == null ? null : clans.get(id);
    }

    public Clan byName(String name) {
        if (name == null) return null;
        return clans.get(idFor(name));
    }

    public java.util.Collection<Clan> all() {
        return clans.values();
    }

    private static String idFor(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ levels

    public int levelOf(Clan clan) {
        long xp = clan.xp();
        int level = 0;
        while (level < 10000) {
            long need = xpFor(level);
            if (xp < need) break;
            xp -= need;
            level++;
        }
        return level;
    }

    /** XP banked toward the next level. */
    public long xpIntoLevel(Clan clan) {
        long xp = clan.xp();
        int level = 0;
        while (level < 10000) {
            long need = xpFor(level);
            if (xp < need) return xp;
            xp -= need;
            level++;
        }
        return 0L;
    }

    /** What level {@code level} to {@code level + 1} costs. */
    public long xpFor(int level) {
        double base = plugin.getConfig().getDouble("clans.level-xp", 50000);
        double growth = plugin.getConfig().getDouble("clans.level-growth", 1.1);
        return Math.max(1L, Math.round(base * Math.pow(growth, level)));
    }

    /** Clans ranked by level, then XP. */
    public List<Clan> top() {
        List<Clan> list = new ArrayList<>(clans.values());
        list.sort(Comparator.comparingLong(Clan::xp).reversed());
        return list;
    }

    public int rankOf(Clan clan) {
        return top().indexOf(clan) + 1;
    }

    /** Members a clan may hold: the base plus one per member slot upgrade. */
    public int slots(Clan clan) {
        return plugin.getConfig().getInt("clans.base-members", 5) + clan.upgrade("SLOTS");
    }

    /**
     * The battle pass XP a member earned goes to their clan as well (V393).
     * The base amount, before the member's own pass multiplier, so a clan
     * levels on how its members play rather than on their skill trees.
     */
    public void addXp(UUID player, long amount) {
        Clan clan = of(player);
        if (clan == null || amount <= 0) return;
        int before = levelOf(clan);
        clan.addXp(amount);
        dirty = true;
        int after = levelOf(clan);
        if (after > before) announceLevel(clan, after);
    }

    private void announceLevel(Clan clan, int level) {
        for (UUID member : clan.members()) {
            org.bukkit.entity.Player online = plugin.getServer().getPlayer(member);
            if (online == null) continue;
            online.sendMessage(org.bukkit.ChatColor.AQUA + "" + org.bukkit.ChatColor.BOLD + "CLAN "
                    + org.bukkit.ChatColor.RESET + org.bukkit.ChatColor.WHITE + clan.name()
                    + org.bukkit.ChatColor.GRAY + " reached level " + org.bukkit.ChatColor.YELLOW + level
                    + org.bukkit.ChatColor.GRAY + ". " + org.bukkit.ChatColor.GRAY + "+1 clan point.");
            online.playSound(online.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
        }
    }

    // ------------------------------------------------------------ actions

    public enum Result {
        DONE, ALREADY_IN_CLAN, NOT_IN_CLAN, NAME_TAKEN, BAD_NAME, NOT_OWNER, FULL, NO_INVITE,
        NOT_MEMBER, OWNER_CANNOT_LEAVE, SELF
    }

    /** 3 to 16 letters, digits or underscores. */
    public static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    public Result create(UUID owner, String name) {
        if (of(owner) != null) return Result.ALREADY_IN_CLAN;
        if (!validName(name)) return Result.BAD_NAME;
        if (clans.containsKey(idFor(name))) return Result.NAME_TAKEN;
        Clan clan = new Clan(idFor(name), name, owner, System.currentTimeMillis());
        clans.put(clan.id(), clan);
        byMember.put(owner, clan.id());
        save();
        return Result.DONE;
    }

    public Result invite(UUID from, UUID target) {
        Clan clan = of(from);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (!clan.isOwner(from)) return Result.NOT_OWNER;
        if (from.equals(target)) return Result.SELF;
        if (of(target) != null) return Result.ALREADY_IN_CLAN;
        if (clan.members().size() >= slots(clan)) return Result.FULL;
        invites.computeIfAbsent(target, k -> new HashMap<>())
                .put(clan.id(), System.currentTimeMillis() + INVITE_MILLIS);
        return Result.DONE;
    }

    /** The clans that have a live invite out to this player. */
    public List<Clan> invitesFor(UUID player) {
        List<Clan> out = new ArrayList<>();
        Map<String, Long> mine = invites.get(player);
        if (mine == null) return out;
        long now = System.currentTimeMillis();
        mine.entrySet().removeIf(e -> e.getValue() < now || !clans.containsKey(e.getKey()));
        for (String id : mine.keySet()) out.add(clans.get(id));
        return out;
    }

    public Result join(UUID player, String name) {
        if (of(player) != null) return Result.ALREADY_IN_CLAN;
        Clan clan = byName(name);
        if (clan == null || !invitesFor(player).contains(clan)) return Result.NO_INVITE;
        if (clan.members().size() >= slots(clan)) return Result.FULL;
        clan.members().add(player);
        byMember.put(player, clan.id());
        invites.remove(player);
        save();
        return Result.DONE;
    }

    public Result leave(UUID player) {
        Clan clan = of(player);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (clan.isOwner(player)) return Result.OWNER_CANNOT_LEAVE;
        clan.members().remove(player);
        byMember.remove(player);
        save();
        return Result.DONE;
    }

    public Result kick(UUID owner, UUID target) {
        Clan clan = of(owner);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (!clan.isOwner(owner)) return Result.NOT_OWNER;
        if (owner.equals(target)) return Result.SELF;
        if (!clan.members().contains(target)) return Result.NOT_MEMBER;
        clan.members().remove(target);
        byMember.remove(target);
        save();
        return Result.DONE;
    }

    public Result transfer(UUID owner, UUID target) {
        Clan clan = of(owner);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (!clan.isOwner(owner)) return Result.NOT_OWNER;
        if (owner.equals(target)) return Result.SELF;
        if (!clan.members().contains(target)) return Result.NOT_MEMBER;
        clan.setOwner(target);
        save();
        return Result.DONE;
    }

    public Result disband(UUID owner) {
        Clan clan = of(owner);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (!clan.isOwner(owner)) return Result.NOT_OWNER;
        for (UUID member : clan.members()) byMember.remove(member);
        clans.remove(clan.id());
        for (Map<String, Long> mine : invites.values()) mine.remove(clan.id());
        save();
        return Result.DONE;
    }

    // ------------------------------------------------------------ file

    private void load() {
        clans.clear();
        byMember.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("clans");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Clan clan = new Clan(id, s.getString("name", id), UUID.fromString(s.getString("owner", "")),
                        s.getLong("created", 0L));
                for (String raw : s.getStringList("members")) clan.members().add(UUID.fromString(raw));
                clan.setXp(s.getLong("xp", 0L));
                ConfigurationSection up = s.getConfigurationSection("upgrades");
                if (up != null) {
                    for (String key : up.getKeys(false)) clan.upgrades().put(key, up.getInt(key));
                }
                clans.put(id, clan);
                for (UUID member : clan.members()) byMember.put(member, id);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipped a broken clan '" + id + "' in clans.yml.");
            }
        }
        plugin.getLogger().info("Loaded " + clans.size() + " clans.");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Clan clan : clans.values()) {
            String path = "clans." + clan.id() + ".";
            yml.set(path + "name", clan.name());
            yml.set(path + "owner", clan.owner().toString());
            List<String> members = new ArrayList<>();
            for (UUID member : clan.members()) members.add(member.toString());
            yml.set(path + "members", members);
            yml.set(path + "xp", clan.xp());
            yml.set(path + "created", clan.created());
            clan.upgrades().forEach((key, level) -> yml.set(path + "upgrades." + key, level));
        }
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save clans.yml: " + ex.getMessage());
        }
    }

    public void markDirty() {
        dirty = true;
    }
}
