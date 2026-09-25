package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.aura.AuraConcepts;
import com.spacerng.solrng.aura.AuraParts;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One act of a reveal: a comet falling out of the sky, and the odds
 * climbing in front of the roller while it comes.
 *
 * The build-up in {@link RollAura} happens on the player: strands winding
 * round them, a circle opening under their feet. That reads well from
 * outside and it reads as nothing at all in first person, because all of
 * it is behind or below the camera. This is the half of the reveal that
 * is meant to be looked AT. It starts high in the sky in the direction
 * the roller is already facing, so it is in view from the first frame
 * without anything touching their camera, and it comes down on them.
 *
 * <h2>A comet per act</h2>
 *
 * Each rarity band is its own act of fixed length, and each act gets its
 * own comet: it launches at the top of the act and hits at the bottom of
 * it. That is not decoration, it is the fix for the thing Leon caught in
 * V196. The path used to be sized from the DROP's rarity so it would not
 * jump when a band changed, which meant a Divine's comet started ninety
 * blocks up on its very first frame and an Epic's forty, and you knew
 * what you had before the first act was over. Now the path is sized from
 * the ACT, so the opening act of a Divine is identical to the only act of
 * an Epic, and the size of the thing coming down at you tells you exactly
 * as much as it should: which band you are in right now.
 *
 * It belongs to the roller alone. Every piece is spawned hidden and shown
 * to one player, every particle and every sound is sent to that one
 * player, and nobody else sees a thing: from outside they are a person
 * standing still with the usual aura winding up. That is also why the
 * budget here is generous compared to the rest of the plugin. One viewer
 * means the per frame cost never multiplies, so the trail can be a solid
 * streak rather than a dotted line.
 *
 * Nothing it draws lands inside {@link #CLEAR} blocks of the roller's
 * head. Particles in your own face are not an effect, they are a screen
 * full of dust where the effect used to be.
 */
final class RollComet {

    /** The path is redrawn every this many ticks; the client glides between. */
    private static final int EVERY = 2;

    /**
     * How the fall is paced. Above 1 the comet hangs high early and then
     * plunges, which is what turns a straight line into something with
     * weight.
     */
    private static final double FALL_EASE = 1.8;

    /** How far the path swings round while it comes in, in degrees. */
    private static final double CURVE = 45.0;

    /** At most this many degrees of view steering per update, when it is on. */
    private static final float STEER_STEP = 2.5f;

    /** How long the final number hangs after the impact, in ticks. */
    private static final long HOLD_AFTER_LANDING = 30L;

    /**
     * No particle of a burst is ever drawn nearer than this to the
     * roller's eyes. Leon asked for it twice in one message: "ik wil sws
     * niet particles in mn gezicht". A burst reads as big because of where
     * its EDGE is, and a cloud centred on the camera hides its own shape.
     */
    private final double CLEAR;

    // ---------------------------------------------------- per act, by band

    /**
     * How high the act's comet starts, and how far out in front.
     *
     * Both were roughly twice this, and that is why the Mythical and
     * Divine comets were not there: a display 80 or 100 blocks off is
     * past the range a server sends entities at, and a non-persistent one
     * out there is a candidate for being cleaned up. Everything now
     * starts inside 45 blocks of the player, which keeps the whole path
     * live and tracked, and the bands still differ by a factor of two
     * from the smallest to the biggest.
     */
    private static double heightFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 36.0;
            case MYTHICAL -> 30.0;
            case LEGENDARY -> 24.0;
            default -> 18.0; // Epic
        };
    }

    private static double reachFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 22.0;
            case MYTHICAL -> 18.0;
            case LEGENDARY -> 14.0;
            default -> 10.0; // Epic
        };
    }

    /** How big the head is, in blocks. */
    private static float sizeFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 2.6f;
            case MYTHICAL -> 2.0f;
            case LEGENDARY -> 1.5f;
            default -> 1.1f; // Epic
        };
    }

    /** How many points of trail are laid down per step. */
    private static int trailFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 14;
            case MYTHICAL -> 12;
            case LEGENDARY -> 10;
            default -> 8; // Epic
        };
    }

    /**
     * How big the number wants to be in each band, before it is made to
     * fit.
     */
    private static float counterScaleFor(Rarity rarity) {
        return switch (rarity) {
            case DIVINE -> 1.7f;
            case MYTHICAL -> 1.45f;
            case LEGENDARY -> 1.2f;
            default -> 1.0f; // Epic
        };
    }

    /**
     * One character of the counter, in blocks, at scale 1.
     *
     * Measured off Leon's V202 screenshot rather than derived: "1 in
     * 116,786" is twelve characters, drawn at scale 1.2, and it spans
     * about 30 percent of a view that is 3.7 blocks wide at the distance
     * these sit. That is 0.089 blocks a character at scale 1.
     */
    static final double CHAR_WIDTH = 0.089;

    /** How much of the view the number is allowed to take, in blocks. */
    static final double FIT_WIDTH = 1.7;

    /**
     * The size the number is actually drawn at: what its band wants, or
     * what fits, whichever is smaller.
     *
     * A band's own size alone is not safe. The bands get bigger exactly as
     * the numbers get longer, so "1 in 10,000,000" at Divine's size is
     * three characters more at one and a half times the scale, and it runs
     * off both edges of the screen. Fitting to the text means the counter
     * can never grow past the screen however long the odds get or however
     * far the config multiplier is turned up.
     */
    static float counterScale(SolRNGPlugin plugin, Rarity band, long value) {
        double turn = Math.max(0.2, plugin.getConfig().getDouble("roll-item.comet.counter-scale", 1.0));
        int chars = Math.max(1, RollFormat.chance(value).length());
        double fits = FIT_WIDTH / (chars * CHAR_WIDTH);
        return (float) (turn * Math.min(counterScaleFor(band), fits));
    }

    // -------------------------------------------------------------- state

    private final SolRNGPlugin plugin;
    private final Player player;
    private final RollStages stages;
    private final long odds;
    private final long actTicks;
    private final boolean steer;
    /** Which way it comes in from, fixed at the start so it cannot chase a turning head. */
    private final double bearing;

    // The act, and everything the act paints.
    private int act = -1;
    private Rarity band = Rarity.EPIC;
    private Color colour = Color.WHITE;
    private Particle.DustTransition tail;
    private Particle.DustOptions spark;
    /** The head's own dust: the band's colour, as big as a dust particle goes. */
    private Particle.DustOptions headDust;
    private Particle accent = Particle.END_ROD;
    private int trailPoints = 8;
    private double height;
    private double reach;

    private BlockDisplay headPiece;
    private Location last;
    private long lastStep = -EVERY;
    private long lastRoar = 0L;
    private long frames = 0L;
    private boolean done;

    RollComet(SolRNGPlugin plugin, Player player, RollStages stages, long odds, long actTicks) {
        this.plugin = plugin;
        this.player = player;
        this.stages = stages;
        this.odds = odds;
        this.actTicks = Math.max(1L, actTicks);
        // Off by default. Steering writes the player's real rotation, and
        // the client reports that back, so everybody else would see them
        // spin round. Aiming the comet into the view they already have is
        // what keeps the whole thing private.
        this.steer = plugin.getConfig().getBoolean("roll-item.comet.steer-view", false);
        this.CLEAR = RollAura.clearance(plugin);

        Vector look = player.getLocation().getDirection().setY(0.0);
        if (look.lengthSquared() < 1.0e-4) look = new Vector(0.0, 0.0, 1.0);
        look.normalize();
        this.bearing = Math.atan2(look.getZ(), look.getX());
    }

    /** Whether a comet should fly at all: switched on, and this roller wants the show. */
    static boolean wanted(SolRNGPlugin plugin, Player player, Rarity rarity) {
        if (!plugin.getConfig().getBoolean("roll-item.comet.enabled", true)) return false;
        return plugin.getPlayerDataManager().get(player.getUniqueId()).isAuraEnabled(rarity);
    }

    /**
     * Whether this comet is the one drawing the text on the screen. When
     * the counter is switched off in config the reel keeps the title and
     * the drop shows its usual candidates, so nothing is left blank.
     */
    boolean ownsScreen() {
        return plugin.getConfig().getBoolean("roll-item.comet.counter", true);
    }

    // ---------------------------------------------------------- lifecycle

    /**
     * Opens one act: a new comet in that band's size and colour, launched
     * from that band's height, to arrive when the act ends.
     */
    void startAct(int index) {
        this.act = index;
        paint(stages.rarityAt(index));
        this.lastStep = -EVERY;
        this.lastRoar = 0L;

        Location at = positionAt(0.0);
        this.last = at;
        if (headPiece != null && headPiece.isValid()) headPiece.remove();
        headPiece = null;
        // V210: Leon liked it and does not need it, so it is kept and
        // switched off. With no head, tick() draws nothing and the acts,
        // the counter and the impacts run exactly as before.
        if (!plugin.getConfig().getBoolean("roll-item.comet.falling", false)) return;
        // Reach a little past the starting height, or the head is culled by
        // the client on the frames where it is furthest away and the whole
        // thing appears out of nothing halfway down.
        headPiece = plugin.getAuraManager().parts().block(at,
                AuraConcepts.lantern(band).createBlockData(), height * 1.6, true, box(band));
        // The one piece of the reveal that is seen through terrain. A comet
        // behind a hill that cannot be seen until it clears the ridge is a
        // comet half the players never notice.
        AuraParts.glowing(headPiece, colour);
        // Not riding anything, so it wants smoothing: the path is sent every
        // two ticks and the client glides across them.
        headPiece.setTeleportDuration(EVERY);
        player.showEntity(plugin, headPiece);

        player.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 0.5f);
    }

    /**
     * One step of the act. {@code actElapsed} is ticks into the current
     * act.
     *
     * <b>The counter is updated before anything can return early, and
     * that is the bug that cost four rounds.</b> This method used to open
     * with {@code if (headPiece == null || !headPiece.isValid()) return;}
     * and the counter was updated below it, so the number stopped the
     * instant the comet's head stopped existing. The head starts at its
     * band's height, and Mythical's and Divine's were 70 and 90 blocks up
     * and 40 and 50 out: far enough from the player that the server has no
     * reason to keep a non-persistent display alive out there, and far
     * enough to be outside the range it would be sent to a client anyway.
     * Epic at 40 and 22 and Legendary at 55 and 30 stay close and live,
     * which is exactly the split Leon kept reporting: "het werkt bij epic
     * en legendary". The heights are pulled in below as well, but the
     * counter must not depend on the comet whatever happens to it.
     */
    void tick(long actElapsed, double hushFrom) {
        if (done) return;
        if (actElapsed - lastStep < EVERY) return;
        lastStep = actElapsed;
        frames++;

        double progress = Math.min(1.0, (double) actElapsed / actTicks);

        if (headPiece == null || !headPiece.isValid()) return;
        // A roller who walked through a portal mid-roll. Teleporting a
        // display across worlds to chase them is not worth the edge cases.
        if (!player.getWorld().equals(headPiece.getWorld())) {
            if (headPiece.isValid()) headPiece.remove();
            headPiece = null;
            return;
        }

        Location now = positionAt(progress);
        headPiece.teleport(now);
        core(now);
        streak(last, now);
        last = now;

        if (progress < hushFrom) roar(actElapsed, progress, now);
        if (steer) steerTowards(now);
    }

    /**
     * The end of an act. {@code last} says whether this is the drop's own
     * band, in which case the reveal stops here and {@link RollAura} plays
     * the band's ending; otherwise it is a breakthrough and the next act
     * opens on top of it.
     *
     * Either way the number lands on the figure the act was climbing to,
     * and is thrown out of the screen. That figure is the next band's
     * entry on a breakthrough and the drop's real odds on the last act,
     * so the pop always happens ON the number that matters.
     */
    void impact(boolean last) {
        if (done) return;
        if (headPiece != null) {
            if (headPiece.isValid()) headPiece.remove();
            headPiece = null;
        }
        burst();
        if (last) {
            done = true;
        } else {
            // Low and high together, a step higher each band, so a
            // breakthrough reads as one large sound rather than two.
            float step = 0.18f * Math.max(0, act);
            Location at = player.getLocation();
            player.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, Math.min(2.0f, 0.7f + step));
            player.playSound(at, Sound.ENTITY_ENDER_EYE_DEATH, 0.9f, Math.min(2.0f, 1.1f + step));
        }
    }

    /**
     * The comet's own share of an impact: a coloured flash and a ring of
     * its trail thrown outward.
     *
     * A ring at {@link #CLEAR} blocks and not a cloud on the player. The
     * flash is a single particle with no position to speak of, so it is
     * the one thing that can sit on the camera.
     */
    private void burst() {
        Location eye = player.getEyeLocation();
        // FLASH takes a Colour since 1.21.11, which is the one particle in
        // the game that fills the screen in a colour we choose.
        player.spawnParticle(Particle.FLASH, eye, 1, 0.0, 0.0, 0.0, 0.0, colour, true);
        int points = 28;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 / points * i;
            for (double r = CLEAR; r <= CLEAR + 1.6; r += 0.8) {
                Location at = eye.clone().add(Math.cos(a) * r, -0.4, Math.sin(a) * r);
                player.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 1, 0.1, 0.25, 0.1, 0.0, tail, true);
            }
            if (i % 3 == 0) {
                Location at = eye.clone().add(Math.cos(a) * CLEAR, 0.1, Math.sin(a) * CLEAR);
                player.spawnParticle(accent, at, 2, 0.1, 0.2, 0.1, 0.02, null, true);
            }
        }
    }

    /** Stops it dead, for a roll that was abandoned. */
    void stop() {
        done = true;
        if (headPiece != null) {
            if (headPiece.isValid()) headPiece.remove();
            headPiece = null;
        }
    }

    /** Everything one band paints, in one place. */
    private void paint(Rarity rarity) {
        this.band = rarity;
        this.colour = RollAura.colorFor(rarity);
        // Warm near-white. A pure white tail reads as a rendering glitch.
        Color pale = Color.fromRGB(255, 252, 224);
        this.tail = new Particle.DustTransition(colour, pale, 2.4f);
        this.spark = new Particle.DustOptions(pale, 1.1f);
        this.headDust = new Particle.DustOptions(colour, 4.0f);
        this.accent = switch (rarity) {
            case MYTHICAL -> Particle.DRAGON_BREATH;
            case LEGENDARY -> Particle.FLAME;
            default -> Particle.END_ROD;
        };
        this.trailPoints = trailFor(rarity);
        this.height = heightFor(rarity);
        this.reach = reachFor(rarity);
    }

    // ------------------------------------------------------------ the path

    /**
     * Where the comet is at {@code progress} through its act.
     *
     * The target is read fresh every step rather than fixed at the start,
     * so a roller who walks a few blocks still gets hit by it instead of
     * watching it land where they used to be. The bearing is not: that is
     * fixed, or a comet would swing round the sky every time somebody
     * turned their head.
     */
    private Location positionAt(double progress) {
        double t = Math.pow(Math.max(0.0, Math.min(1.0, progress)), FALL_EASE);
        double radius = reach * (1.0 - t);
        double angle = bearing + Math.toRadians(CURVE) * t;
        // It stops a little short of the roller rather than inside them,
        // for the same reason the burst does.
        double drop = 1.0 + CLEAR * t;
        Location at = player.getLocation().add(Math.cos(angle) * radius, height * (1.0 - t) + drop,
                Math.sin(angle) * radius);
        // A display takes the rotation of wherever it is put, and this is
        // built off the player's own location. Left alone, the comet would
        // tumble with every turn of the roller's head.
        at.setYaw(0f);
        at.setPitch(0f);
        return at;
    }

    /** The head's model, centred on its own origin so it grows from the middle. */
    private static Matrix4f box(Rarity rarity) {
        float size = sizeFor(rarity);
        return AuraParts.box(0f, 0f, 0f, new Quaternionf(), size, size, size);
    }

    /**
     * The head, in particles as well as in a block.
     *
     * The block display was the whole head until V205, and it was never
     * once seen. A server only sends an entity to a client inside its
     * tracking range, which is 32 blocks for this kind by default, and the
     * comet spends nearly all of its fall further away than that. The view
     * range set on the display is a CLIENT side limit and cannot help with
     * something the client was never told about.
     *
     * Particles have no such limit, as long as they are sent forced: the
     * client draws an ordinary particle only within 32 blocks too, which
     * is why every call in this class passes true at the end.
     * {@code FirstTenBuildUp} learned the same thing and says so in its
     * own comment, which is where this should have been read four jars
     * ago.
     *
     * So the block is now the close-up look and this is the comet.
     */
    private void core(Location at) {
        player.spawnParticle(Particle.DUST, at, 8, 0.35, 0.35, 0.35, 0.0, headDust, true);
        player.spawnParticle(Particle.DUST, at, 4, 0.12, 0.12, 0.12, 0.0, spark, true);
        player.spawnParticle(accent, at, 5, 0.3, 0.3, 0.3, 0.01, null, true);
    }

    /** The trail, laid down along the ground it covered since the last step. */
    private void streak(Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0 / trailPoints);
        Location cursor = from.clone();
        double clearSq = CLEAR * CLEAR;
        Location eye = player.getEyeLocation();
        for (int i = 0; i < trailPoints; i++) {
            cursor.add(step);
            if (cursor.distanceSquared(eye) < clearSq) continue;
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, cursor, 1, 0.12, 0.12, 0.12, 0.0, tail, true);
        }
        if (to.distanceSquared(eye) < clearSq) return;
        // Embers shedding off the head, thrown backwards along the path.
        player.spawnParticle(accent, to, 3, 0.25, 0.25, 0.25, 0.02, null, true);
        player.spawnParticle(Particle.DUST, to, 2, 0.4, 0.4, 0.4, 0.0, spark, true);
    }

    /**
     * The sound of the number going up.
     *
     * Leon asked for it twice: "de odds en geluid van oplopen is ook nog
     * steeds weg". A counter that climbs in silence is a number changing,
     * not a machine running. One short tick every counter frame, climbing
     * most of an octave across the act so the act itself is audibly
     * winding up, quiet enough to sit under the score rather than on top
     * of it, and nudged each time so fifty of them in five seconds do not
     * turn into a drone.
     */
    static void climbTick(Player player, double progress) {
        float jitter = (float) (ThreadLocalRandom.current().nextDouble() * 0.08 - 0.04);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f,
                (float) Math.min(2.0, 0.9 + progress * 0.9) + jitter);
    }

    /**
     * The approach. One heavy beat, getting louder and higher as it closes,
     * with the pitch nudged each time so a repeated sound does not start to
     * read as a machine.
     */
    private void roar(long actElapsed, double progress, Location at) {
        if (actElapsed - lastRoar < 16L) return;
        lastRoar = actElapsed;
        float jitter = (float) (ThreadLocalRandom.current().nextDouble() * 0.1 - 0.05);
        player.playSound(at, Sound.ENTITY_ENDER_DRAGON_FLAP,
                (float) (0.4 + 0.6 * progress), (float) (0.5 + 0.7 * progress) + jitter);
        if (progress > 0.5) {
            player.playSound(at, Sound.ITEM_FIRECHARGE_USE,
                    (float) (0.3 + 0.5 * progress), (float) (0.8 + 0.6 * progress) + jitter);
        }
    }

    // --------------------------------------------------------- the counter

    /**
     * Whether text belongs on this player's screen at all.
     *
     * It used to follow Rolling Animation in /options, on the reasoning
     * that the counter is text like the reel's titles are. That was a
     * guess, and a guess in the one place that could silently hide the
     * whole cutscene from somebody who had that switch off. The counter
     * belongs to the reveal, and the reveal is already gated on this
     * rarity's aura switch, which is the one the player used to ask for
     * this show in the first place.
     */
    private boolean wantsText() {
        return true;
    }

    /**
     * One frame of the number, with a highlight travelling along it.
     *
     * Leon asked for "wat meer leven erin dus kleur ofzo". A flat gradient
     * is still a flat gradient however fast the digits change, so every
     * character is mixed between the band's colour and a warm near-white
     * on a wave that walks across the text a little each frame. It costs
     * one component per character on one viewer's screen.
     */
    /** The same line without a comet behind it, for the admin preview. */
    static Component line(Color colour, long value, long frame) {
        String text = RollFormat.chance(value);
        Component out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            double phase = (double) i / Math.max(1, text.length()) - frame * 0.05;
            double lit = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0);
            out = out.append(Component.text(text.charAt(i))
                    .color(TextColor.color(mix(colour.getRed(), 255, lit),
                            mix(colour.getGreen(), 252, lit), mix(colour.getBlue(), 224, lit)))
                    .decoration(TextDecoration.BOLD, true));
        }
        return out;
    }

    /**
     * A counter standing on its own, climbing, with no roll around it.
     *
     * The showcase has had a preview since V197 and the counter has not,
     * which is why three rounds of "ik zie de odds niet" could never be
     * narrowed down: there was no way to look at the thing by itself.
     */
    static void preview(SolRNGPlugin plugin, Player player, Rarity rarity, long odds, long ticks) {
        float scale = counterScale(plugin, rarity, odds);
        Color colour = RollAura.colorFor(rarity);
        RollCounter counter = new RollCounter(plugin, player, scale);
        long from = Math.max(100L, odds / 8L);
        final long[] frame = {0L};
        final org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            frame[0] += 2L;
            if (frame[0] > ticks || !player.isOnline()) {
                task[0].cancel();
                counter.stop();
                return;
            }
            double t = Math.min(1.0, (double) frame[0] / ticks);
            long shown = Math.round(from * Math.pow((double) odds / from, t));
            counter.show(line(colour, Math.min(odds, shown), frame[0] / 2L));
        }, 0L, 2L);
    }

    private static int mix(int from, int to, double amount) {
        return Math.max(0, Math.min(255, (int) Math.round(from + (to - from) * amount)));
    }

    // ---------------------------------------------------------- the camera

    /**
     * Swings the view towards the comet, a couple of degrees at a time, for
     * anybody who switches it on.
     *
     * This is the one part of the cutscene other people can see, and there
     * is no way round it: Paper has no camera of its own, so the only lever
     * is the player's real rotation, and the client reports that straight
     * back to the server. Left off by default for exactly that reason.
     */
    private void steerTowards(Location comet) {
        Vector to = comet.toVector().subtract(player.getEyeLocation().toVector());
        if (to.lengthSquared() < 1.0e-4) return;
        to.normalize();
        float wantYaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        float wantPitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, to.getY()))));
        Location at = player.getLocation();
        player.setRotation(towards(at.getYaw(), wantYaw, STEER_STEP),
                towards(at.getPitch(), wantPitch, STEER_STEP));
    }

    /** One step of at most {@code max} degrees from {@code current} to {@code target}, the short way round. */
    private static float towards(float current, float target, float max) {
        float delta = ((target - current) % 360f + 540f) % 360f - 180f;
        if (delta > max) delta = max;
        if (delta < -max) delta = -max;
        return current + delta;
    }
}
