package com.spacerng.solrng.roll;

import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The "something big is coming" effect. When a roll is going to land Epic
 * or better, the player is wrapped in a rising, tightening helix of
 * rarity-coloured particles that everyone nearby can see, building for the
 * whole roll and bursting on the reveal.
 *
 * It deliberately shows the RARITY and nothing else — the colour tells the
 * server that something good is being unboxed, but not what, so the reveal
 * still lands. This is the visual counterpart of the chat broadcast, which
 * only fires after the fact.
 *
 * Particles are spawned through the World (not Player#spawnParticle), so
 * they render for every player in range rather than only the roller.
 */
public final class RollAura {

    private RollAura() {
    }

    /** Epic and up get the aura; anything below rolls quietly. */
    public static boolean isBigDrop(Rarity rarity) {
        return rarity != null && rarity.ordinal() >= Rarity.EPIC.ordinal();
    }

    private static Color colorFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> Color.fromRGB(255, 60, 60);
            case LEGENDARY -> Color.fromRGB(255, 170, 0);
            default -> Color.fromRGB(168, 85, 247); // Epic
        };
    }

    private static Particle accentFor(Rarity rarity) {
        return switch (rarity) {
            case MYTHICAL -> Particle.DRAGON_BREATH;
            case LEGENDARY -> Particle.FLAME;
            default -> Particle.END_ROD; // Epic
        };
    }

    /**
     * One frame of the build-up. progress runs 0.0 -> 1.0 across the roll;
     * frame is just a monotonically rising counter used to spin the helix.
     *
     * The helix tightens and speeds up as progress climbs, so the effect
     * visibly winds toward the reveal instead of looping flat.
     */
    public static void tick(Player player, Rarity rarity, double progress, int frame) {
        if (!isBigDrop(rarity)) return;

        World world = player.getWorld();
        Location base = player.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(colorFor(rarity), 1.2f);

        // Three strands, evenly spaced around the player.
        int strands = 3;
        double spin = frame * (0.30 + progress * 0.45);
        double radius = 1.5 - (1.0 * progress); // winds inward

        for (int i = 0; i < strands; i++) {
            double angle = spin + (i * (Math.PI * 2 / strands));
            // Each strand climbs the player's height on its own loop.
            double height = ((frame * 0.10) + (i * 0.7)) % 2.2;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location point = base.clone().add(x, height, z);

            world.spawnParticle(Particle.DUST, point, 2, 0.03, 0.03, 0.03, 0.0, dust);
            // The accent is sparse early and thickens near the reveal.
            if (frame % Math.max(1, (int) (5 - progress * 4)) == 0) {
                world.spawnParticle(accentFor(rarity), point, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // A rising pling every few frames — the audio version of the same
        // wind-up, so players who aren't looking still notice.
        if (frame % 4 == 0) {
            float pitch = (float) Math.min(2.0, 0.6 + progress * 1.4);
            world.playSound(base, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, pitch);
        }
    }

    /** The burst, fired the moment the drop is revealed. */
    public static void reveal(Player player, Rarity rarity) {
        if (!isBigDrop(rarity)) return;

        World world = player.getWorld();
        Location base = player.getLocation().add(0, 1.0, 0);
        Particle.DustOptions dust = new Particle.DustOptions(colorFor(rarity), 1.6f);

        world.spawnParticle(Particle.DUST, base, 120, 0.7, 0.9, 0.7, 0.0, dust);
        world.spawnParticle(accentFor(rarity), base, 50, 0.4, 0.7, 0.4, 0.25);

        switch (rarity) {
            case MYTHICAL -> {
                world.spawnParticle(Particle.EXPLOSION, base, 3, 0.3, 0.3, 0.3, 0.0);
                world.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
                world.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
            }
            case LEGENDARY -> {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, base, 40, 0.4, 0.6, 0.4, 0.3);
                world.playSound(base, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                world.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.2f);
            }
            default -> {
                world.spawnParticle(Particle.WITCH, base, 60, 0.5, 0.7, 0.5, 0.1);
                world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.5f);
            }
        }
    }
}
