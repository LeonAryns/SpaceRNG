package com.spacerng.solrng.aura;

import org.bukkit.Particle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A particle layer worn on top of an aura's displays. Kept small on
 * purpose: a worn aura runs for hours next to other players, so every
 * accent stays at a handful of particles per viewer every two ticks.
 */
public enum AuraAccent {
    NONE("none"),
    SPARKLE("sparkle"),
    EMBERS("embers"),
    TRAILS("trails"),
    ENCHANT("enchant"),
    ALL("all");

    public static final List<String> KEYS = Arrays.stream(values()).map(AuraAccent::key).toList();

    private final String key;

    AuraAccent(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static AuraAccent parse(String raw) {
        for (AuraAccent accent : values()) {
            if (accent.key.equalsIgnoreCase(raw)) return accent;
        }
        return null;
    }

    /** Draws one frame; called every 2 ticks. */
    void play(AuraFx fx, AuraConcept concept, long frame, Random random) {
        switch (this) {
            case NONE -> {
            }
            case SPARKLE -> sparkle(fx, concept, frame, random);
            case EMBERS -> embers(fx, frame, random);
            case TRAILS -> trails(fx, concept, frame);
            case ENCHANT -> enchant(fx, random);
            case ALL -> {
                sparkle(fx, concept, frame, random);
                embers(fx, frame, random);
                trails(fx, concept, frame);
            }
        }
    }

    /** Glints popping on a shell around the body, and now and then a spark on one of the stars. */
    private static void sparkle(AuraFx fx, AuraConcept concept, long frame, Random random) {
        if (frame % 2 != 0) return;
        for (int i = 0; i < 2; i++) {
            fx.spark(Particle.END_ROD, onSphere(random, 1.1, 0.9 + random.nextDouble() * 0.5));
        }
        if (frame % 6 == 0) {
            List<Vector> stars = new ArrayList<>();
            concept.stars(frame, stars);
            if (!stars.isEmpty()) fx.spark(Particle.ELECTRIC_SPARK, stars.get(random.nextInt(stars.size())));
        }
    }

    /** Motes in the aura's colour hanging round the body, fading soft, with the odd firefly low down. */
    private static void embers(AuraFx fx, long frame, Random random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 0.3 + random.nextDouble() * 0.6;
        fx.fade(new Vector(Math.cos(angle) * radius, 0.1 + random.nextDouble() * 1.9, Math.sin(angle) * radius),
                fx.color, fx.soft, 0.9f);
        if (frame % 3 == 0) {
            fx.spark(Particle.FIREFLY, new Vector((random.nextDouble() - 0.5) * 1.6,
                    0.3 + random.nextDouble(), (random.nextDouble() - 0.5) * 1.6));
        }
    }

    /** A dust mote dropped where every star is, so each one drags a short comet tail. */
    private static void trails(AuraFx fx, AuraConcept concept, long frame) {
        List<Vector> stars = new ArrayList<>();
        concept.stars(frame, stars);
        for (Vector star : stars) {
            fx.dust(star, fx.soft, 0.8f);
        }
    }

    /** Enchant glyphs flying in from every side and vanishing into the chest. */
    private static void enchant(AuraFx fx, Random random) {
        for (int i = 0; i < 2; i++) {
            fx.stream(Particle.ENCHANT, new Vector(0.0, 1.2, 0.0), onSphere(random, 0.0, 1.6), 1.0);
        }
    }

    private static Vector onSphere(Random random, double centreY, double radius) {
        double u = random.nextDouble() * 2 - 1;
        double theta = random.nextDouble() * Math.PI * 2;
        double ring = Math.sqrt(1 - u * u);
        return new Vector(ring * Math.cos(theta) * radius, centreY + u * radius, ring * Math.sin(theta) * radius);
    }
}
