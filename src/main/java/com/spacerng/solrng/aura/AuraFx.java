package com.spacerng.solrng.aura;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * What an accent draws with for one frame: the wearer, the players allowed
 * to see it, and the aura's colours. Every call goes to each viewer on its
 * own, never through the world, so the aura opt-out in /options holds.
 * Offsets are relative to the wearer's feet.
 */
public final class AuraFx {

    private final Player wearer;
    private final List<Player> audience;
    final Color color;
    final Color soft;

    // Whether the wearer's own copy leaves out what is in their face.
    private final boolean clearFace;

    AuraFx(Player wearer, List<Player> audience, Color color) {
        this(wearer, audience, color, false);
    }

    AuraFx(Player wearer, List<Player> audience, Color color, boolean clearFace) {
        this.clearFace = clearFace;
        this.wearer = wearer;
        this.audience = audience;
        this.color = color;
        this.soft = AuraParts.softer(color);
    }

    private Location at(Vector offset) {
        return wearer.getLocation().add(offset);
    }

    /**
     * Whether this viewer gets a particle at this offset. The wearer, in
     * "out of your way", does not get the ones in their own face (V217):
     * the same band the pieces keep out of, above the knees and within
     * arm's reach and a bit. Everybody else sees all of it.
     */
    private boolean sees(Player viewer, Vector offset) {
        if (!clearFace || !viewer.equals(wearer)) return true;
        double out = Math.hypot(offset.getX(), offset.getZ());
        return offset.getY() <= 1.0 || out >= 3.2;
    }

    public void dust(Vector offset, Color colour, float size) {
        Location where = at(offset);
        Particle.DustOptions options = new Particle.DustOptions(colour, size);
        for (Player viewer : audience) {
            if (!sees(viewer, offset)) continue;
            viewer.spawnParticle(Particle.DUST, where, 1, 0.0, 0.0, 0.0, 0.0, options);
        }
    }

    /** A dust mote that fades from one colour to another over its life. */
    public void fade(Vector offset, Color from, Color to, float size) {
        Location where = at(offset);
        Particle.DustTransition transition = new Particle.DustTransition(from, to, size);
        for (Player viewer : audience) {
            if (!sees(viewer, offset)) continue;
            viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, where, 1, 0.0, 0.0, 0.0, 0.0, transition);
        }
    }

    public void spark(Particle particle, Vector offset) {
        Location where = at(offset);
        for (Player viewer : audience) {
            if (!sees(viewer, offset)) continue;
            viewer.spawnParticle(particle, where, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Count 0 turns the offset into a direction. Enchant glyphs sent this
     * way start at {@code offset + direction} and fly into {@code offset}.
     */
    public void stream(Particle particle, Vector offset, Vector direction, double speed) {
        Location where = at(offset);
        for (Player viewer : audience) {
            if (!sees(viewer, offset)) continue;
            viewer.spawnParticle(particle, where, 0, direction.getX(), direction.getY(), direction.getZ(), speed);
        }
    }
}
