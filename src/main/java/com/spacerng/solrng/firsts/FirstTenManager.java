package com.spacerng.solrng.firsts;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import com.spacerng.solrng.roll.RollAura;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server First 10. The first players to roll a tracked rarity each take a
 * numbered spot, and the whole server is told: a banner in chat, a toast,
 * the rarity's colour drifting down around every online player and a
 * pillar over spawn.
 *
 * Spots live in firsts.yml and are the only record; a player's status is
 * read from here rather than copied onto their player file. Only the real
 * roll path calls {@link #onRoll}, so an admin roll can never take a spot.
 */
public final class FirstTenManager {

    public record Entry(UUID uuid, String name, String item, long at) {
    }

    /** A spot a player holds: the rarity, and their place in it. */
    public record Spot(Rarity rarity, int place) {
    }

    private final SolRNGPlugin plugin;
    private final File file;
    private final Map<Rarity, List<Entry>> entries = new EnumMap<>(Rarity.class);

    public FirstTenManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "firsts.yml");
        load();
    }

    // ------------------------------------------------------------- settings

    public List<Rarity> trackedRarities() {
        List<Rarity> tracked = new ArrayList<>();
        if (!plugin.getConfig().getBoolean("first-ten.enabled", true)) return tracked;
        List<String> names = plugin.getConfig().getStringList("first-ten.rarities");
        if (names.isEmpty()) names = List.of("LEGENDARY", "MYTHICAL", "DIVINE");
        for (String name : names) {
            try {
                Rarity rarity = Rarity.valueOf(name.trim().toUpperCase(Locale.ROOT));
                if (!tracked.contains(rarity)) tracked.add(rarity);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("first-ten.rarities has an unknown rarity: " + name);
            }
        }
        tracked.sort(Comparator.naturalOrder());
        return tracked;
    }

    public int slots() {
        return Math.max(1, plugin.getConfig().getInt("first-ten.slots", 10));
    }

    // ---------------------------------------------------------------- reads

    public List<Entry> entries(Rarity rarity) {
        return List.copyOf(entries.getOrDefault(rarity, List.of()));
    }

    /** Every spot this player holds, rarest first, then best place. */
    public List<Spot> spotsOf(UUID uuid) {
        List<Spot> spots = new ArrayList<>();
        for (Map.Entry<Rarity, List<Entry>> row : entries.entrySet()) {
            List<Entry> list = row.getValue();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).uuid().equals(uuid)) spots.add(new Spot(row.getKey(), i + 1));
            }
        }
        spots.sort(Comparator.comparing(Spot::rarity, Comparator.<Rarity>reverseOrder())
                .thenComparingInt(Spot::place));
        return spots;
    }

    // --------------------------------------------------------------- writes

    /**
     * A real roll just landed. Takes a spot if the rarity is tracked, one is
     * still free and this player doesn't already hold one in it, then
     * schedules the event for after the reveal has played out.
     */
    public void onRoll(Player player, RollableItem item, boolean shiny, long delayTicks) {
        if (!wouldTake(player, item)) return;
        Rarity rarity = item.getRarity();
        List<Entry> list = entries.computeIfAbsent(rarity, key -> new ArrayList<>());

        list.add(new Entry(player.getUniqueId(), player.getName(), item.getDisplayName(),
                System.currentTimeMillis()));
        int place = list.size();
        save();

        UUID roller = player.getUniqueId();
        String name = player.getName();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> buildUpThen(rarity, item.getMaterial(), roller,
                        () -> announce(roller, name, item, shiny, place, false)),
                Math.max(1L, delayTicks));
    }

    /**
     * Whether this drop takes a spot: the rarity is tracked, one is still
     * free and this player doesn't already hold one in it. Asked before the
     * drop's own chat line too, because a First announces itself and the
     * ordinary "just found" line on top of it said the same thing twice
     * (V213).
     */
    public boolean wouldTake(Player player, RollableItem item) {
        Rarity rarity = item.getRarity();
        if (!trackedRarities().contains(rarity)) return false;
        List<Entry> list = entries.getOrDefault(rarity, List.of());
        if (list.size() >= slots()) return false;
        // One spot per player per rarity, so one lucky streak can't eat the list.
        for (Entry held : list) {
            if (held.uuid().equals(player.getUniqueId())) return false;
        }
        return true;
    }

    /** Plays the whole event without recording anything. */
    public void preview(Player viewer, RollableItem item, boolean shiny) {
        int place = Math.min(slots(), entries.getOrDefault(item.getRarity(), List.of()).size() + 1);
        UUID roller = viewer == null ? null : viewer.getUniqueId();
        String name = viewer == null ? "Console" : viewer.getName();
        buildUpThen(item.getRarity(), item.getMaterial(), roller,
                () -> announce(roller, name, item, shiny, place, true), true);
    }

    /** Every tracked rarity gets the build-up (V216); it was Legendary and up only. */
    private void buildUpThen(Rarity rarity, org.bukkit.Material drop, UUID roller, Runnable event) {
        buildUpThen(rarity, drop, roller, event, false);
    }

    /**
     * @param forced a preview, so whoever asked for it sees the run-up
     *               whether or not they have this rarity's aura switched
     *               off in /options. Without this a preview looked
     *               instant to the one person watching it.
     */
    private void buildUpThen(Rarity rarity, org.bukkit.Material drop, UUID roller, Runnable event,
                             boolean forced) {
        // Every rarity the list tracks. Leon's server tracks Epic as well,
        // and an Epic First went straight to its banner, which is what
        // "de preview is instant" was.
        FirstTenBuildUp build = new FirstTenBuildUp(plugin, rarity, drop, roller, event);
        if (forced) build.force();
        build.start();
    }

    public void reset(Rarity rarity) {
        entries.remove(rarity);
        save();
    }

    public void resetAll() {
        entries.clear();
        save();
    }

    // ---------------------------------------------------------------- event

    /**
     * The banner, framed so it can't be mistaken for an ordinary drop line:
     *
     *   ------------------------------------------   rule, rarity colour
     *   ✦ SERVER FIRST 10 ✦ (preview)
     *   Leon is #1 of the first 10 to find a Divine
     *   Drop: ✦ First Star ✦
     *   ▬▬▬▬▬▬▬▬▬▬  9 spots left
     *   ------------------------------------------
     *
     * The rarity's colour carries the frame, the place and the rarity word;
     * everything else stays grey so those three are what the eye lands on.
     */
    private void announce(UUID roller, String name, RollableItem item, boolean shiny, int place, boolean preview) {
        Rarity rarity = item.getRarity();
        int slots = slots();
        int left = Math.max(0, slots - place);
        var rarities = plugin.getRarityManager();
        String article = "AEIOU".indexOf(rarity.name().charAt(0)) >= 0 ? "an " : "a ";

        String rule = colourOf(rarity) + ChatColor.STRIKETHROUGH + " ".repeat(52);
        String header = rarities.styleHeading(rarity, "✦ SERVER FIRST " + slots + " ✦")
                + (preview ? ChatColor.DARK_GRAY + " (preview)" : "");
        String line = ChatColor.YELLOW + name + ChatColor.GRAY + " is "
                + rarities.styleHeading(rarity, "#" + place)
                + ChatColor.GRAY + " of the first " + ChatColor.WHITE + slots
                + ChatColor.GRAY + " to find " + article + rarities.styleHeading(rarity, rarity.displayName());
        String drop = ChatColor.GRAY + "Drop: " + RollFormat.displayName(plugin, item, shiny);
        // The spots as a meter: taken in the rarity's colour, free in grey.
        String meter = rarities.styleHeading(rarity, "▬".repeat(place)) + ChatColor.DARK_GRAY + "▬".repeat(left);
        String footer = meter + "  " + (left > 0
                ? ChatColor.WHITE + "" + left + ChatColor.GRAY + (left == 1 ? " spot left" : " spots left")
                : ChatColor.RED + "Every spot is taken");
        Component banner = LegacyComponentSerializer.legacySection()
                .deserialize(rule + "\n" + header + "\n" + line + "\n" + drop + "\n" + footer + "\n" + rule);

        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean own = online.getUniqueId().equals(roller);
            // The mute is for other people's news; the roller always hears their own.
            if (!own && !plugin.getPlayerDataManager().get(online.getUniqueId()).isBroadcastEnabled(rarity)) {
                continue;
            }
            online.sendMessage(banner);
            // Three sounds at once, spread across the range so they read as
            // one large sound rather than as three: the hit low, the body
            // in the middle, the sparkle on top.
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            online.playSound(online.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.7f);
            online.playSound(online.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!online.isOnline()) return;
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                online.playSound(online.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.7f, 1.6f);
            }, 12L);

            if (plugin.getPlayerDataManager().get(online.getUniqueId()).isAuraEnabled(rarity)) {
                // Half a second of dark, so the flash lands on nothing and
                // the title arrives out of it. Short enough that nobody
                // standing in the farm loses a harvest over it.
                online.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 10, 0, false, false, false));
                online.spawnParticle(Particle.FLASH,
                        online.getEyeLocation().add(online.getLocation().getDirection().multiply(2.0)),
                        1, 0.0, 0.0, 0.0, 0.0, RollAura.colorFor(rarity), true);
            }

            // Two titles, not one. The first says what happened, the second
            // says who and what, and a title that tries to say all of it at
            // once is a title nobody finishes reading.
            String place1 = rarities.styleHeading(rarity, "#" + place + " of " + slots);
            online.showTitle(Title.title(
                    LegacyComponentSerializer.legacySection().deserialize(place1),
                    LegacyComponentSerializer.legacySection().deserialize(
                            rarities.styleHeading(rarity, rarity.displayName().toUpperCase(Locale.ROOT))
                                    + ChatColor.GRAY + "  server first"),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1600), Duration.ofMillis(300))));

            String who = own ? ChatColor.GREEN + "You found it" : ChatColor.YELLOW + name;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!online.isOnline()) return;
                online.showTitle(Title.title(
                        LegacyComponentSerializer.legacySection().deserialize(who),
                        LegacyComponentSerializer.legacySection()
                                .deserialize(RollFormat.displayName(plugin, item, shiny)),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(400))));
            }, 42L);
        }
        Bukkit.getConsoleSender().sendMessage(banner);
        if (!preview) plugin.getDiscordWebhook().firstTen(name, item, shiny, place, slots);
        new Rain(rarity).start();
    }

    /**
     * The colour codes a rarity's style starts with, for drawing something
     * of its own in that colour. Only the colour: bold, italic and
     * underline made the rule thick, Divine's most of all (V216).
     */
    private String colourOf(Rarity rarity) {
        String styled = plugin.getRarityManager().style(rarity, "|");
        StringBuilder codes = new StringBuilder();
        for (int i = 0; i + 1 < styled.length() && styled.charAt(i) == ChatColor.COLOR_CHAR; i += 2) {
            char code = Character.toLowerCase(styled.charAt(i + 1));
            if (code == 'x' || "0123456789abcdef".indexOf(code) >= 0) codes.append(styled, i, i + 2);
        }
        return codes.toString();
    }

    /**
     * The server-wide part. Every online player who hasn't muted the rarity
     * or switched its aura off gets a sky of its colour falling about fifty
     * blocks in every direction, with fireworks bursting somewhere in it
     * once a second, and anyone within 96 blocks of spawn also sees a
     * pillar there. Sent per viewer, so each client only receives its own
     * particles; the far ones are forced, since a client otherwise draws
     * particles only within 32 blocks.
     */
    private final class Rain {
        private final Rarity rarity;
        private final long length;
        private final int density;
        private final Particle.DustOptions dust;
        private final BlockData fall;
        private long frame = 0L;
        private BukkitTask task;

        Rain(Rarity rarity) {
            this.rarity = rarity;
            // Scale follows rarity, as everywhere else: Divine is always the biggest.
            this.length = switch (rarity) {
                case DIVINE -> 140L;
                case MYTHICAL -> 100L;
                default -> 80L;
            };
            this.density = switch (rarity) {
                case DIVINE -> 12;
                case MYTHICAL -> 9;
                default -> 6;
            };
            // Big dust: at 1.4 the far-off specks were too small to read as anything.
            this.dust = new Particle.DustOptions(RollAura.colorFor(rarity), 2.6f);
            this.fall = switch (rarity) {
                case DIVINE -> Material.QUARTZ_BLOCK.createBlockData();
                case MYTHICAL -> Material.REDSTONE_BLOCK.createBlockData();
                default -> Material.GOLD_BLOCK.createBlockData();
            };
        }

        void start() {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 4L);
        }

        private void tick() {
            frame += 4L;
            if (frame > length) {
                task.cancel();
                return;
            }
            try {
                // Thins out over the last quarter so it settles instead of cutting off.
                int count = (double) frame / length > 0.75 ? Math.max(1, density / 2) : density;
                Location spawn = plugin.getSpawnManager().getSpawn();
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    PlayerData data = plugin.getPlayerDataManager().get(viewer.getUniqueId());
                    if (!data.isBroadcastEnabled(rarity) || !data.isAuraEnabled(rarity)) continue;

                    Location at = viewer.getLocation();
                    viewer.spawnParticle(Particle.FALLING_DUST, at.clone().add(0, 18, 0), count * 6,
                            25.0, 4.0, 25.0, 0.0, fall, true);
                    viewer.spawnParticle(Particle.DUST, at.clone().add(0, 8, 0), count * 3,
                            25.0, 6.0, 25.0, 0.0, dust, true);
                    // A close layer too, so it's thick right where the player is looking.
                    viewer.spawnParticle(Particle.DUST, at.clone().add(0, 3, 0), count * 2,
                            7.0, 2.5, 7.0, 0.0, dust, true);
                    viewer.spawnParticle(Particle.END_ROD, at.clone().add(0, 4, 0), count,
                            8.0, 3.0, 8.0, 0.02, null, true);
                    if (frame % 20 == 0) burst(viewer, at);

                    if (spawn != null && spawn.getWorld() != null && spawn.getWorld().equals(viewer.getWorld())
                            && spawn.distanceSquared(viewer.getLocation()) < 96.0 * 96.0) {
                        pillar(viewer, spawn);
                    }
                }
            } catch (RuntimeException ex) {
                task.cancel();
                plugin.getLogger().warning("First 10 event failed: " + ex);
            }
        }

        /** Three fireworks in the rarity's colour somewhere in the fifty block sky. */
        private void burst(Player viewer, Location at) {
            java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                Location pop = at.clone().add(random.nextDouble(-45, 45), random.nextDouble(14, 26),
                        random.nextDouble(-45, 45));
                viewer.spawnParticle(Particle.FIREWORK, pop, 60, 0.0, 0.0, 0.0, 0.35, null, true);
                viewer.spawnParticle(Particle.DUST, pop, 40, 2.5, 2.5, 2.5, 0.0, dust, true);
            }
            viewer.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 0.8f,
                    0.8f + random.nextFloat() * 0.4f);
        }

        private void pillar(Player viewer, Location spawn) {
            for (double y = 0.0; y < 24.0; y += 1.5) {
                viewer.spawnParticle(Particle.DUST, spawn.clone().add(0, y, 0), 1, 0.25, 0.1, 0.25, 0.0, dust);
            }
            double spin = frame * 0.15;
            for (int i = 0; i < 12; i++) {
                double angle = spin + (Math.PI * 2 / 12) * i;
                viewer.spawnParticle(Particle.DUST,
                        spawn.clone().add(Math.cos(angle) * 3.0, 0.2, Math.sin(angle) * 3.0),
                        1, 0.0, 0.0, 0.0, 0.0, dust);
            }
        }
    }

    // ------------------------------------------------------------- storage

    public void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Rarity rarity : Rarity.values()) {
            List<Map<?, ?>> rows = yml.getMapList(rarity.name());
            if (rows.isEmpty()) continue;
            List<Entry> list = new ArrayList<>();
            for (Map<?, ?> row : rows) {
                try {
                    list.add(new Entry(UUID.fromString(String.valueOf(row.get("uuid"))),
                            String.valueOf(row.get("name")),
                            String.valueOf(row.get("item")),
                            row.get("at") instanceof Number number ? number.longValue() : 0L));
                } catch (IllegalArgumentException ignored) {
                }
            }
            entries.put(rarity, list);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<Rarity, List<Entry>> row : entries.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Entry entry : row.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("uuid", entry.uuid().toString());
                map.put("name", entry.name());
                map.put("item", entry.item());
                map.put("at", entry.at());
                list.add(map);
            }
            yml.set(row.getKey().name(), list);
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save firsts.yml: " + ex.getMessage());
        }
    }
}
