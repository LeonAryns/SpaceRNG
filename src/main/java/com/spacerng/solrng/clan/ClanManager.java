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
        double base = plugin.getConfig().getDouble("clans.level-xp", 10000);
        double growth = plugin.getConfig().getDouble("clans.level-growth", 1.03);
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

    // ------------------------------------------------------------ upgrades

    /**
     * One clan upgrade (V394, Leon). Bought with clan points, one point
     * per clan level. Every level costs a little more than the last:
     * base-cost + cost-step x level, rounded, at least 1.
     */
    public record Upgrade(String id, String display, org.bukkit.Material icon, org.bukkit.ChatColor colour,
                          String pitch, int max, double base, double step, double perLevel) {
    }

    public static final List<Upgrade> UPGRADES = List.of(
            new Upgrade("SLOTS", "Member Slot", org.bukkit.Material.ARMOR_STAND, org.bukkit.ChatColor.AQUA,
                    "One more seat in the clan.", 5, 10, 5, 1),
            new Upgrade("LUCK", "Clan Luck", org.bukkit.Material.RABBIT_FOOT, org.bukkit.ChatColor.GREEN,
                    "Every member's Luck, +0.01x a level.", 100, 1, 0.2, 0.01),
            new Upgrade("MONEY", "Clan Money", org.bukkit.Material.EMERALD, org.bukkit.ChatColor.GREEN,
                    "Every member's Money, +0.01x a level.", 100, 1, 0.2, 0.01),
            new Upgrade("SPEED", "Clan Speed", org.bukkit.Material.SUGAR, org.bukkit.ChatColor.AQUA,
                    "Every member's Speed, +0.01x a level.", 100, 1, 0.2, 0.01),
            new Upgrade("BOND", "Clan Bond", org.bukkit.Material.LEAD, org.bukkit.ChatColor.LIGHT_PURPLE,
                    "2x Luck while a clan member is within 10 blocks.", 1, 25, 0, 1));

    public Upgrade upgrade(String id) {
        for (Upgrade upgrade : UPGRADES) if (upgrade.id().equals(id)) return upgrade;
        return null;
    }

    private double tuned(Upgrade upgrade, String key, double fallback) {
        return plugin.getConfig().getDouble("clans.upgrades." + upgrade.id() + "." + key, fallback);
    }

    public int maxOf(Upgrade upgrade) {
        return (int) tuned(upgrade, "max-level", upgrade.max());
    }

    public double perLevel(Upgrade upgrade) {
        return tuned(upgrade, "per-level", upgrade.perLevel());
    }

    /** What level {@code level} to {@code level + 1} of an upgrade costs. */
    public long costAt(Upgrade upgrade, int level) {
        return Math.max(1L, Math.round(tuned(upgrade, "base-cost", upgrade.base())
                + tuned(upgrade, "cost-step", upgrade.step()) * level));
    }

    public long spentPoints(Clan clan) {
        long total = 0;
        for (Upgrade upgrade : UPGRADES) {
            for (int level = 0; level < clan.upgrade(upgrade.id()); level++) total += costAt(upgrade, level);
        }
        return total;
    }

    /** Points to spend: one per clan level, minus what is already spent. */
    public long points(Clan clan) {
        return Math.max(0L, levelOf(clan) - spentPoints(clan));
    }

    public Result buyUpgrade(UUID player, Upgrade upgrade) {
        Clan clan = of(player);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (!clan.isOwner(player)) return Result.NOT_OWNER;
        int level = clan.upgrade(upgrade.id());
        if (level >= maxOf(upgrade)) return Result.MAXED;
        if (points(clan) < costAt(upgrade, level)) return Result.TOO_POOR;
        clan.upgrades().put(upgrade.id(), level + 1);
        save();
        return Result.DONE;
    }

    /** 1 + per-level x level of a clan stat upgrade for this player, 1.0 outside a clan. */
    public double multiplier(UUID player, String id) {
        Clan clan = player == null ? null : of(player);
        Upgrade upgrade = upgrade(id);
        if (clan == null || upgrade == null) return 1.0;
        return 1.0 + perLevel(upgrade) * clan.upgrade(id);
    }

    /**
     * Clan Bond (V394, Leon): 2x Luck while another member of the clan is
     * within 10 blocks every way (a cube, not a sphere).
     */
    public double bond(UUID player) {
        Clan clan = player == null ? null : of(player);
        if (clan == null || clan.upgrade("BOND") <= 0) return 1.0;
        org.bukkit.entity.Player me = plugin.getServer().getPlayer(player);
        if (me == null) return 1.0;
        int range = plugin.getConfig().getInt("clans.bond-range", 10);
        org.bukkit.Location at = me.getLocation();
        for (UUID member : clan.members()) {
            if (member.equals(player)) continue;
            org.bukkit.entity.Player other = plugin.getServer().getPlayer(member);
            if (other == null || !other.getWorld().equals(me.getWorld())) continue;
            org.bukkit.Location there = other.getLocation();
            if (Math.abs(there.getX() - at.getX()) <= range && Math.abs(there.getY() - at.getY()) <= range
                    && Math.abs(there.getZ() - at.getZ()) <= range) {
                return plugin.getConfig().getDouble("clans.bond-multiplier", 2.0);
            }
        }
        return 1.0;
    }

    // ------------------------------------------------------------ chest

    private final Map<String, org.bukkit.inventory.Inventory> chests = new HashMap<>();

    /**
     * The clan chest (V394): one shared inventory per clan, so two members
     * looking at it see each other's changes live. Written back to the
     * clan when it closes.
     */
    public org.bukkit.inventory.Inventory chest(Clan clan) {
        return chests.computeIfAbsent(clan.id(), id -> {
            ClanChestHolder holder = new ClanChestHolder(id);
            org.bukkit.inventory.Inventory inv = plugin.getServer().createInventory(holder, 54,
                    com.spacerng.solrng.gui.MenuStyle.title("Clan Chest", "#80D8FF", "#448AFF"));
            holder.setInventory(inv);
            inv.setContents(clan.chest().clone());
            return inv;
        });
    }

    /** Copies the shared chest back onto the clan and saves. */
    public void storeChest(String clanId) {
        Clan clan = clans.get(clanId);
        org.bukkit.inventory.Inventory inv = chests.get(clanId);
        if (clan == null || inv == null) return;
        org.bukkit.inventory.ItemStack[] contents = inv.getContents();
        for (int i = 0; i < clan.chest().length; i++) {
            clan.chest()[i] = i < contents.length ? contents[i] : null;
        }
        save();
    }

    // ------------------------------------------------------------ actions

    public enum Result {
        DONE, ALREADY_IN_CLAN, NOT_IN_CLAN, NAME_TAKEN, BAD_NAME, NOT_OWNER, FULL, NO_INVITE,
        NOT_MEMBER, OWNER_CANNOT_LEAVE, SELF, MAXED, TOO_POOR
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
        refreshTab(owner);
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
        refreshTab(player);
        return Result.DONE;
    }

    public Result leave(UUID player) {
        Clan clan = of(player);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (clan.isOwner(player)) return Result.OWNER_CANNOT_LEAVE;
        clan.members().remove(player);
        byMember.remove(player);
        closeChestFor(clan, player);
        save();
        refreshTab(player);
        return Result.DONE;
    }

    /** A player who left or was kicked loses the chest window at once. */
    private void closeChestFor(Clan clan, UUID player) {
        org.bukkit.inventory.Inventory open = chests.get(clan.id());
        if (open == null) return;
        for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(open.getViewers())) {
            if (viewer.getUniqueId().equals(player)) viewer.closeInventory();
        }
    }

    public Result kick(UUID owner, UUID target) {
        Clan clan = of(owner);
        if (clan == null) return Result.NOT_IN_CLAN;
        if (!clan.isOwner(owner)) return Result.NOT_OWNER;
        if (owner.equals(target)) return Result.SELF;
        if (!clan.members().contains(target)) return Result.NOT_MEMBER;
        clan.members().remove(target);
        byMember.remove(target);
        closeChestFor(clan, target);
        save();
        refreshTab(target);
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
        // V394: the chest goes back to the owner rather than with the clan.
        org.bukkit.inventory.Inventory open = chests.remove(clan.id());
        if (open != null) {
            for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(open.getViewers())) viewer.closeInventory();
            org.bukkit.inventory.ItemStack[] contents = open.getContents();
            for (int i = 0; i < clan.chest().length; i++) clan.chest()[i] = i < contents.length ? contents[i] : null;
        }
        org.bukkit.entity.Player ownerOnline = plugin.getServer().getPlayer(owner);
        for (org.bukkit.inventory.ItemStack stack : clan.chest()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (ownerOnline != null) com.spacerng.solrng.player.Stash.give(plugin, ownerOnline, stack);
        }
        for (UUID member : clan.members()) byMember.remove(member);
        for (UUID member : clan.members()) refreshTab(member);
        clans.remove(clan.id());
        for (Map<String, Long> mine : invites.values()) mine.remove(clan.id());
        save();
        return Result.DONE;
    }

    /**
     * The Sunday reset (V406, Leon): every clan gone, with its members,
     * levels, upgrades and chest. Nothing is handed back from the chests,
     * because carrying items through the reset is what they would be for.
     * Returns how many clans there were.
     */
    public int wipeAll() {
        for (org.bukkit.inventory.Inventory open : new ArrayList<>(chests.values())) {
            for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(open.getViewers())) viewer.closeInventory();
        }
        chests.clear();
        int count = clans.size();
        List<UUID> members = new ArrayList<>(byMember.keySet());
        clans.clear();
        byMember.clear();
        invites.clear();
        for (UUID member : members) refreshTab(member);
        save();
        return count;
    }

    // ------------------------------------------------------------ file

    /** V402: the clan tag sits in tab, so a membership change redraws it. */
    private void refreshTab(UUID player) {
        org.bukkit.entity.Player online = plugin.getServer().getPlayer(player);
        if (online != null) plugin.getRankManager().refreshName(online);
    }

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
                List<?> chest = s.getList("chest");
                if (chest != null) {
                    for (int i = 0; i < chest.size() && i < clan.chest().length; i++) {
                        if (chest.get(i) instanceof org.bukkit.inventory.ItemStack stack) clan.chest()[i] = stack;
                    }
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
            yml.set(path + "chest", java.util.Arrays.asList(clan.chest()));
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
