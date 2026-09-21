package com.spacerng.solrng.firsts;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.roll.RollAura;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The seconds before a Server First 10 banner, Legendary and up only.
 *
 * The finder already has their own roll animation, so this is spread over
 * the whole area instead of drawn on them: a ring of the rarity's colour
 * runs outward from where they stood when it began, then flashes, bursts,
 * rising sparks and a glittering sky go off at random spots up to a
 * hundred blocks away, thicker as it nears. The spot is fixed when it
 * starts, so nothing follows the finder or points at them, and from the
 * ground nobody can tell who it is until the banner and title say so. The
 * last tenth goes quiet, so the burst lands on silence.
 *
 * Three seconds for Legendary, four for Mythical, six for Divine. Drawn per
 * viewer within 180 blocks, honouring the rarity's aura switch, and forced,
 * since a client only draws normal particles within 32 blocks. Everyone
 * who hasn't muted the rarity hears the bells and sees the action bar.
 */
final class FirstTenBuildUp {

    private static final double VIEW = 180.0;
    private static final double REACH = 100.0;

    private final SolRNGPlugin plugin;
    private final Rarity rarity;
    private final org.bukkit.Material drop;
    private final UUID finder;
    private final Runnable burst;
    private final long length;
    private final Color base;
    private final Particle.DustTransition fade;
    private final Particle.DustOptions dust;
    private final Particle.DustOptions glow;
    private final Location origin;
    private final Component bar;
    private long frame = 0L;
    private BukkitTask task;
    // The shape over the whole thing. Particles read as weather from a
    // distance; a star does not.
    private FirstTenStar star;

    FirstTenBuildUp(SolRNGPlugin plugin, Rarity rarity, org.bukkit.Material drop, UUID finder, Runnable burst) {
        this.plugin = plugin;
        this.rarity = rarity;
        this.drop = drop;
        this.finder = finder;
        this.burst = burst;
        this.length = switch (rarity) {
            // Shortened in V158: Leon found it dragging well past the
            // roll's own reveal. Three, four and six seconds.
            case DIVINE -> 120L;
            case MYTHICAL -> 80L;
            default -> 60L;
        };
        this.base = RollAura.colorFor(rarity);
        // Warm near-white, never pure white, which reads as a glitch.
        Color light = mix(base, Color.fromRGB(255, 250, 235), 0.55);
        this.fade = new Particle.DustTransition(base, light, 2.4f);
        this.dust = new Particle.DustOptions(base, 2.2f);
        this.glow = new Particle.DustOptions(light, 1.6f);
        Player player = finder == null ? null : Bukkit.getPlayer(finder);
        if (player == null) {
            this.origin = null;
        } else {
            // Copied once: the show stays where it started while the finder moves on.
            Location at = player.getLocation().clone();
            at.setYaw(0f);
            at.setPitch(0f);
            this.origin = at;
        }
        this.bar = LegacyComponentSerializer.legacySection().deserialize(
                plugin.getRarityManager().styleBold(rarity, "✦ A Server First " + rarity.displayName() + " is coming ✦"));
        this.title = LegacyComponentSerializer.legacySection().deserialize(
                plugin.getRarityManager().styleBold(rarity, "✦ SERVER FIRST ✦"));
    }

    private final Component title;

    /** "A Mythical is coming" with a filling row of stars under the title. */
    private Component progressLine(double progress) {
        int filled = (int) Math.round(progress * 10);
        String stars = plugin.getRarityManager().style(rarity, "✦".repeat(Math.max(0, filled)))
                + org.bukkit.ChatColor.DARK_GRAY + "✦".repeat(Math.max(0, 10 - filled));
        return LegacyComponentSerializer.legacySection().deserialize(
                org.bukkit.ChatColor.WHITE + "A " + plugin.getRarityManager().style(rarity, rarity.displayName())
                        + org.bukkit.ChatColor.WHITE + " is coming  " + stars);
    }

    void start() {
        if (origin != null) {
            // Bigger and higher the rarer it is, so a Divine is visible
            // from further away than a Legendary without either of them
            // having to be louder.
            double radius = switch (rarity) {
                case DIVINE -> 20.0;
                case MYTHICAL -> 17.0;
                default -> 14.0;
            };
            star = new FirstTenStar(plugin, rarity, drop, origin, 18.0 + radius, radius);
            star.start();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 2L);
    }

    private void tick() {
        frame += 2L;
        if (frame >= length) {
            finish();
            return;
        }
        try {
            double progress = (double) frame / length;
            boolean hush = progress > 0.9;
            if (star != null) {
                star.tick(progress, 4);
                // People walk in and out of range, and somebody who just
                // turned the rarity back on should see the rest of it.
                if (frame % 20 == 0) star.refreshAudience();
            }
            long half = (length / 4) * 2;
            long threeQuarters = (length * 3 / 8) * 2;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.getPlayerDataManager().get(viewer.getUniqueId());
                boolean own = viewer.getUniqueId().equals(finder);
                if (!own && !data.isBroadcastEnabled(rarity)) continue;

                if (!hush) viewer.sendActionBar(bar);
                // The same size of announcement as the reveal, on every
                // screen wherever the viewer stands (V171). Before this the
                // run-up was only an action bar and particles within 180
                // blocks, so most of the server saw nothing until the name
                // landed. The finder is left to their own roll reveal.
                if (!own && frame % 10 == 0) {
                    viewer.showTitle(net.kyori.adventure.title.Title.title(title, progressLine(progress),
                            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                                    java.time.Duration.ofMillis(900), java.time.Duration.ofMillis(250))));
                }
                if (!hush && frame % 8 == 0) {
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f,
                            (float) (0.5 + 1.5 * progress));
                }
                if (!hush && frame % 40 == 0) {
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f,
                            (float) (0.6 + 0.8 * progress));
                }
                if (frame == half || frame == threeQuarters) {
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f,
                            (float) (0.7 + 0.6 * progress));
                }
                if (hush || origin == null || !data.isAuraEnabled(rarity)) continue;
                if (!origin.getWorld().equals(viewer.getWorld())
                        || viewer.getLocation().distanceSquared(origin) > VIEW * VIEW) continue;
                draw(viewer, progress);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("First 10 build-up failed: " + ex);
            finish();
        }
    }

    private void draw(Player viewer, double progress) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // The opening: one ring running outward from where it began, gone by a fifth of the way.
        if (progress < 0.2) {
            double radius = REACH * progress / 0.2;
            int points = 24 + (int) (radius * 1.2);
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                        origin.clone().add(Math.cos(angle) * radius, 0.6, Math.sin(angle) * radius),
                        1, 0.0, 0.2, 0.0, 0.0, fade, true);
            }
        }

        // Bursts at random spots across the whole area, more of them as it builds.
        int bursts = 2 + (int) (10 * progress);
        for (int i = 0; i < bursts; i++) {
            Location at = randomSpot(random, 2.0, 28.0);
            switch (random.nextInt(4)) {
                case 0 -> viewer.spawnParticle(Particle.FLASH, at, 1, 0.0, 0.0, 0.0, 0.0, base, true);
                case 1 -> {
                    viewer.spawnParticle(Particle.DUST, at, 14, 1.3, 1.3, 1.3, 0.0, dust, true);
                    for (int s = 0; s < 6; s++) {
                        viewer.spawnParticle(Particle.FIREWORK, at, 0, random.nextDouble(-1, 1),
                                random.nextDouble(-1, 1), random.nextDouble(-1, 1), 0.35, null, true);
                    }
                }
                case 2 -> {
                    Location ground = at.clone();
                    ground.setY(origin.getY() + random.nextDouble(0.0, 3.0));
                    for (int s = 0; s < 4; s++) {
                        viewer.spawnParticle(Particle.END_ROD, ground, 0, random.nextDouble(-0.08, 0.08),
                                1.0, random.nextDouble(-0.08, 0.08), 0.6 + 0.6 * progress, null, true);
                    }
                }
                default -> viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 10, 2.0, 2.0, 2.0, 0.0, fade, true);
            }
        }

        // A glittering sky over the area, filling in as it nears.
        int glitter = 16 + (int) (44 * progress);
        for (int i = 0; i < glitter; i++) {
            Location at = randomSpot(random, 4.0, 34.0);
            if (random.nextBoolean()) {
                viewer.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, glow, true);
            } else {
                viewer.spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0.2, 0.2, 0.2, 0.0, null, true);
            }
        }
    }

    /** Anywhere in a disc a hundred blocks across the starting spot, spread evenly rather than bunched in the middle. */
    private Location randomSpot(ThreadLocalRandom random, double minY, double maxY) {
        double angle = random.nextDouble(Math.PI * 2);
        double radius = REACH * Math.sqrt(random.nextDouble());
        return origin.clone().add(Math.cos(angle) * radius, random.nextDouble(minY, maxY), Math.sin(angle) * radius);
    }

    private static Color mix(Color a, Color b, double t) {
        return Color.fromRGB(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private void finish() {
        if (task == null) return;
        task.cancel();
        task = null;
        if (star != null) {
            star.finish();
            star = null;
        }
        burst.run();
    }
}
