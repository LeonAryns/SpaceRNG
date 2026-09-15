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
 * The finder already has their own roll animation, so this is built around
 * them rather than on them: a galaxy of the rarity's colour sixty blocks
 * across turning over the ground, pillars climbing at its rim, a crown of
 * light overhead and sparks through the air. Nothing marks the finder, so
 * from the ground it isn't obvious who it is until the banner and the
 * title say so. The crown closes to a point over the last stretch and the
 * last tenth goes quiet, so the burst lands on silence.
 *
 * Five seconds for Legendary, seven for Mythical, ten for Divine. Drawn per
 * viewer within 160 blocks, honouring the rarity's aura switch, and forced,
 * since a client only draws normal particles within 32 blocks. Everyone
 * who hasn't muted the rarity hears the bells and sees the action bar.
 */
final class FirstTenBuildUp {

    private static final double VIEW = 160.0;

    private final SolRNGPlugin plugin;
    private final Rarity rarity;
    private final UUID finder;
    private final Runnable burst;
    private final long length;
    private final Particle.DustTransition swirl;
    private final Particle.DustOptions rim;
    private final Particle.DustOptions crown;
    private final Location origin;
    private final double turn;
    private final Component bar;
    private long frame = 0L;
    private BukkitTask task;

    FirstTenBuildUp(SolRNGPlugin plugin, Rarity rarity, UUID finder, Runnable burst) {
        this.plugin = plugin;
        this.rarity = rarity;
        this.finder = finder;
        this.burst = burst;
        this.length = switch (rarity) {
            case DIVINE -> 200L;
            case MYTHICAL -> 140L;
            default -> 100L;
        };
        Color base = RollAura.colorFor(rarity);
        // Warm near-white, never pure white, which reads as a glitch.
        Color light = mix(base, Color.fromRGB(255, 250, 235), 0.55);
        this.swirl = new Particle.DustTransition(base, light, 2.2f);
        this.rim = new Particle.DustOptions(base, 2.0f);
        this.crown = new Particle.DustOptions(light, 2.8f);
        Player player = finder == null ? null : Bukkit.getPlayer(finder);
        if (player == null) {
            this.origin = null;
        } else {
            Location at = player.getLocation();
            at.setYaw(0f);
            at.setPitch(0f);
            this.origin = at;
        }
        this.turn = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        this.bar = LegacyComponentSerializer.legacySection().deserialize(
                plugin.getRarityManager().styleBold(rarity, "✦ A Server First " + rarity.displayName() + " is coming ✦"));
    }

    void start() {
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
            long half = (length / 4) * 2;
            long threeQuarters = (length * 3 / 8) * 2;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PlayerData data = plugin.getPlayerDataManager().get(viewer.getUniqueId());
                boolean own = viewer.getUniqueId().equals(finder);
                if (!own && !data.isBroadcastEnabled(rarity)) continue;

                if (!hush) viewer.sendActionBar(bar);
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
        double shrink = 1.0 - 0.3 * progress;
        double spin = turn + frame * (0.02 + 0.07 * progress);

        // A galaxy over the ground: five arms curling in, filling out as it builds.
        int arms = 5;
        int perArm = 8 + (int) (16 * progress);
        for (int arm = 0; arm < arms; arm++) {
            for (int i = 0; i < perArm; i++) {
                double t = (double) i / (perArm - 1);
                double radius = (8.0 + 26.0 * t) * shrink;
                double angle = spin + Math.PI * 2 * arm / arms + t * 2.6;
                double y = 0.4 + Math.sin(t * Math.PI) * (0.8 + 3.0 * progress);
                viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION,
                        origin.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius),
                        1, 0.0, 0.0, 0.0, 0.0, swirl, true);
            }
        }

        // Pillars climbing at the rim, turning slowly the other way.
        int pillars = 10;
        double rimRadius = 32.0 * shrink;
        double height = 3.0 + 24.0 * progress;
        for (int k = 0; k < pillars; k++) {
            double angle = -spin * 0.35 + Math.PI * 2 * k / pillars;
            Location foot = origin.clone().add(Math.cos(angle) * rimRadius, 0, Math.sin(angle) * rimRadius);
            for (double h = 0.0; h < height; h += 2.0) {
                viewer.spawnParticle(Particle.DUST, foot.clone().add(0, h, 0), 1, 0.15, 0.2, 0.15, 0.0, rim, true);
            }
            viewer.spawnParticle(Particle.END_ROD, foot.clone().add(0, height, 0), 0, 0.0, 1.0, 0.0, 0.2, null, true);
        }

        // A crown overhead that closes to a point before the burst.
        double pull = progress < 0.75 ? 1.0 : Math.max(0.05, 1.0 - (progress - 0.75) / 0.15);
        double crownRadius = 24.0 * shrink * pull;
        double crownY = 14.0 + 10.0 * progress;
        int points = 40;
        for (int i = 0; i < points; i++) {
            double angle = spin * 1.6 + Math.PI * 2 * i / points;
            viewer.spawnParticle(Particle.DUST,
                    origin.clone().add(Math.cos(angle) * crownRadius, crownY + Math.sin(angle * 4 + spin) * 0.6,
                            Math.sin(angle) * crownRadius),
                    1, 0.0, 0.0, 0.0, 0.0, crown, true);
        }

        // Sparks through the air, thicker as it nears.
        int sparks = 6 + (int) (30 * progress);
        for (int i = 0; i < sparks; i++) {
            double angle = random.nextDouble(Math.PI * 2);
            double radius = random.nextDouble(34.0) * shrink;
            viewer.spawnParticle(random.nextBoolean() ? Particle.FIREWORK : Particle.ELECTRIC_SPARK,
                    origin.clone().add(Math.cos(angle) * radius, random.nextDouble(1.0, 16.0), Math.sin(angle) * radius),
                    0, random.nextDouble(-0.3, 0.3), random.nextDouble(0.2, 1.0), random.nextDouble(-0.3, 0.3),
                    0.3, null, true);
        }
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
        burst.run();
    }
}
