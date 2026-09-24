package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraParts;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The circle drawn on the ground under a roll while its aura runs.
 *
 * Everything else about the reveal is particles, and particles have one
 * weakness: past twenty blocks a few hundred bright specks read as weather
 * rather than as a shape. A solid ring does not. This is a summoning
 * circle painted in the rarity's colour that opens under the roller, holds
 * while the strands wind up, is dragged inward with the implosion and is
 * thrown outward on the bang.
 *
 * The pieces ride the player as passengers, the same way a worn aura does,
 * so they follow a roller who walks off without the server moving
 * anything. They carry the aura tag, so the startup sweep clears anything
 * a crash leaves behind, and they honour the same per rarity switch in
 * /options that the particles do.
 *
 * Only Epic and up ever roll an aura, so this is built a few times a day
 * on a busy server rather than constantly.
 */
final class RollCircle {

    private static final int OUTER = 10;
    private static final int INNER = 6;
    private static final int EVERY = 4;

    private final SolRNGPlugin plugin;
    private final Player player;
    private final Rarity rarity;
    // Not final since V196: a reveal climbs the rarity ladder while its
    // odds counter runs, and the circle has to climb with it or the floor
    // stays Epic violet under a Divine.
    private Color color;
    private Color soft;
    private float maxRadius;
    private final List<Display> pieces = new ArrayList<>();
    private long lastSent = -EVERY;

    RollCircle(SolRNGPlugin plugin, Player player, Rarity rarity, Color color, double maxRadius) {
        this.plugin = plugin;
        this.player = player;
        this.rarity = rarity;
        this.color = color;
        this.soft = AuraParts.softer(color);
        this.maxRadius = (float) maxRadius;
    }

    /** Opens it closed, at nothing. The first tick grows it. */
    void start() {
        AuraParts parts = plugin.getAuraManager().parts();
        for (int i = 0; i < OUTER; i++) {
            pieces.add(parts.plate(player, color, 235, ring(OUTER, i, 0.01f, 0.0, 0.12f)));
        }
        for (int i = 0; i < INNER; i++) {
            pieces.add(parts.plate(player, soft, 175, ring(INNER, i, 0.01f, 0.0, 0.08f)));
        }
        for (Display piece : pieces) {
            player.addPassenger(piece);
        }
        refreshAudience();
    }

    /**
     * Repaints and resizes it for the next rung of the ladder.
     *
     * The plates are repainted where they stand rather than respawned.
     * Rebuilding would put every segment back at the pose {@link #start}
     * draws, in the middle of a turn, so the circle would jump backwards
     * on the exact frame it is meant to be growing. The new radius is left
     * to the next {@link #tick}, a frame or two away, which the client
     * glides into like every other step.
     */
    void restyle(Color colour, double maxRadius) {
        this.color = colour;
        this.soft = AuraParts.softer(colour);
        this.maxRadius = (float) maxRadius;
        for (int i = 0; i < pieces.size(); i++) {
            Display piece = pieces.get(i);
            if (!(piece instanceof org.bukkit.entity.TextDisplay plate) || !plate.isValid()) continue;
            boolean outer = i < OUTER;
            Color paint = outer ? color : soft;
            plate.setBackgroundColor(Color.fromARGB(outer ? 235 : 175,
                    paint.getRed(), paint.getGreen(), paint.getBlue()));
        }
        lastSent = -EVERY;
    }

    /** Hidden from anybody who switched this rarity's aura off, like the particles. */
    void refreshAudience() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean allowed = plugin.getPlayerDataManager()
                    .get(viewer.getUniqueId()).isAuraEnabled(rarity);
            for (Display piece : pieces) {
                if (!piece.isValid()) continue;
                if (allowed) viewer.showEntity(plugin, piece);
                else viewer.hideEntity(plugin, piece);
            }
        }
    }

    /**
     * {@code progress} runs 0 to 1 over the whole build-up and
     * {@code implodeFrom} is where the strands stop winding and collapse.
     *
     * The circle opens over the first third, holds wide while the strands
     * do the work, then is pulled in with them. It turns the whole way, and
     * faster as it tightens, so the last second before the bang reads as
     * something being wound up rather than run down.
     */
    void tick(long elapsed, double progress, double implodeFrom) {
        if (elapsed - lastSent < EVERY) return;
        lastSent = elapsed;
        float open = (float) Math.min(1.0, progress / 0.3);
        float eased = 1f - (1f - open) * (1f - open);
        float radius = maxRadius * eased;
        double spin = elapsed * 2.0;
        if (progress >= implodeFrom) {
            double p = (progress - implodeFrom) / (1.0 - implodeFrom);
            radius = maxRadius * (float) (1.0 - 0.75 * p);
            spin = elapsed * (2.0 + 6.0 * p);
        }
        draw(radius, spin, EVERY * 2);
    }

    /** The bang: thrown outward to twice the width and thinned away. */
    void detonate() {
        draw(maxRadius * 2.1f, 0.0, 8);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::stop, 10L);
    }

    void stop() {
        for (Display piece : pieces) {
            if (piece.isValid()) piece.remove();
        }
        pieces.clear();
    }

    private void draw(float radius, double spin, int ticks) {
        for (int i = 0; i < OUTER; i++) {
            Display piece = pieces.get(i);
            if (piece.isValid()) AuraParts.moveTo(piece, ring(OUTER, i, radius, spin, 0.12f), ticks);
        }
        for (int i = 0; i < INNER; i++) {
            Display piece = pieces.get(OUTER + i);
            if (piece.isValid()) {
                AuraParts.moveTo(piece, ring(INNER, i, radius * 0.62f, -spin * 1.6, 0.08f), ticks);
            }
        }
    }

    /** One segment of the circle, just off the ground under the roller. */
    private Matrix4f ring(int segments, int i, float radius, double spin, float thickness) {
        return AuraParts.ringSegment(segments, i, radius, spin, AuraParts.FEET + 0.02f, thickness, 0.8f);
    }
}
