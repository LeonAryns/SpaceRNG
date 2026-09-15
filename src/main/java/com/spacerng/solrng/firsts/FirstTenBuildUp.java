package com.spacerng.solrng.firsts;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.roll.RollAura;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * The seconds before a Server First 10 banner. Everyone who hasn't muted
 * the rarity feels it coming: a ring of the rarity's colour closing in
 * around them at eye height, motes rising thicker as it nears, a column
 * climbing over the finder, a bell rising in pitch and a line on the action
 * bar. The last tenth goes quiet, so the burst lands on silence.
 *
 * Five seconds for Legendary, seven for Mythical, ten for Divine: scale
 * follows rarity. Particles go per viewer and honour the rarity's aura
 * switch; the far ones are forced, since a client only draws normal
 * particles within 32 blocks.
 */
final class FirstTenBuildUp {

    private final SolRNGPlugin plugin;
    private final Rarity rarity;
    private final UUID finder;
    private final Runnable burst;
    private final long length;
    private final Particle.DustOptions dust;
    private final Location origin;
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
        this.dust = new Particle.DustOptions(RollAura.colorFor(rarity), 2.4f);
        Player player = finder == null ? null : Bukkit.getPlayer(finder);
        this.origin = player == null ? null : player.getLocation();
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
                if (!data.isAuraEnabled(rarity)) continue;

                Location eye = viewer.getEyeLocation();
                // The ring closes from fourteen blocks out to three, turning as it comes.
                double radius = 14.0 - 11.0 * progress;
                double spin = frame * 0.08;
                int points = 28;
                for (int i = 0; i < points; i++) {
                    double angle = spin + Math.PI * 2 / points * i;
                    viewer.spawnParticle(Particle.DUST,
                            eye.clone().add(Math.cos(angle) * radius, -0.6 + Math.sin(angle * 3 + spin) * 0.3,
                                    Math.sin(angle) * radius),
                            1, 0.0, 0.0, 0.0, 0.0, dust, true);
                }
                viewer.spawnParticle(Particle.END_ROD, eye.clone().add(0, -1.5, 0), (int) (2 + 10 * progress),
                        4.0, 0.5, 4.0, 0.04, null, true);

                // A column of the colour climbing over the finder, visible across the spawn.
                if (origin != null && origin.getWorld() != null && origin.getWorld().equals(viewer.getWorld())) {
                    for (double y = 0.0; y < 30.0 * progress; y += 1.5) {
                        viewer.spawnParticle(Particle.DUST, origin.clone().add(0, y, 0), 1, 0.2, 0.1, 0.2, 0.0, dust, true);
                    }
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("First 10 build-up failed: " + ex);
            finish();
        }
    }

    private void finish() {
        if (task == null) return;
        task.cancel();
        task = null;
        burst.run();
    }
}
