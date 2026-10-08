package com.spacerng.solrng.pet;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The egg hatching (V238), the last piece of the pets blueprint.
 *
 * The pet is already decided and saved when this starts; the animation
 * only decides when the player finds out. Three acts on one tick counter:
 *
 *   wobble   0.00 to 0.78  the egg floats in front of the player and rocks,
 *                          harder and faster, cracking at three beats with
 *                          the pitch climbing; dust in the egg's colours
 *                          gathers round it
 *   hatch    0.78          one frame: the egg is gone, a flash and a burst
 *                          in the PET's colours, the pet's sound, the title
 *   settle   0.78 to 1.00  the pet spins where the egg was, grows into
 *                          place, then everything is taken down
 *
 * The egg's tier sets how long the wobble lasts (a Supernova egg is the
 * longest wait); the pet's rarity sets how big the hatch is. Nothing in
 * the wobble depends on the pet, so it gives nothing away.
 *
 * Everything is the hatcher's alone: the displays are hidden from
 * everybody else and every particle and sound goes to one player. On
 * Bedrock the displays are skipped (Geyser cannot draw item displays) and
 * the particles, sounds and title carry it. Displays are non persistent and
 * carry the aura tag, so the aura sweep clears anything a crash leaves.
 */
public final class PetHatch {

    private static final Map<UUID, PetHatch> RUNNING = new HashMap<>();
    private static final double HATCH_AT = 0.78;

    private final SolRNGPlugin plugin;
    private final Player player;
    private final PetEgg egg;
    private final int tier;
    private final PetType pet;
    private final boolean isNew;
    // V359: a duplicate says which copy it is. It used to say the
    // rarity level a duplicate bought, and rarity levels are gone.
    private final int copies;
    private final Runnable after;
    private final int duration;
    private final boolean bedrock;

    private final Location centre;
    private ItemDisplay eggDisplay;
    private ItemDisplay petDisplay;
    private BukkitTask task;
    private int elapsed;
    private boolean hatched;

    private PetHatch(SolRNGPlugin plugin, Player player, PetEgg egg, int tier, PetType pet,
                     boolean isNew, int copies, Runnable after) {
        this.plugin = plugin;
        this.player = player;
        this.egg = egg;
        this.tier = tier;
        this.pet = pet;
        this.isNew = isNew;
        this.copies = copies;
        this.after = after;
        this.duration = 70 + 20 * Math.max(0, Math.min(3, tier) - 1); // 3.5 s, 4.5 s, 5.5 s
        this.bedrock = com.spacerng.solrng.platform.Bedrock.is(player);
        // Two blocks ahead at eye height, flat, with yaw and pitch zeroed:
        // a display copies the rotation of wherever it is spawned.
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector ahead = eye.getDirection().setY(0);
        if (ahead.lengthSquared() < 1.0E-4) ahead = new org.bukkit.util.Vector(0, 0, 1);
        ahead.normalize().multiply(2.2);
        Location at = eye.clone().add(ahead).add(0, -0.35, 0);
        at.setYaw(0f);
        at.setPitch(0f);
        this.centre = at;
    }

    public static boolean isHatching(Player player) {
        return RUNNING.containsKey(player.getUniqueId());
    }

    /** Starts one; {@code after} runs once the pet is shown (chat, menus). */
    public static void start(SolRNGPlugin plugin, Player player, PetEgg egg, int tier, PetManager.Made made,
                             Runnable after) {
        PetHatch running = RUNNING.remove(player.getUniqueId());
        if (running != null) running.stop();
        PetHatch hatch = new PetHatch(plugin, player, egg, tier, made.type(), made.isNew(),
                made.pet().copies(), after);
        RUNNING.put(player.getUniqueId(), hatch);
        hatch.begin();
    }

    /** For onDisable: nothing is left floating after a reload. */
    public static void stopAll() {
        for (PetHatch hatch : RUNNING.values().toArray(new PetHatch[0])) hatch.stop();
        RUNNING.clear();
    }

    private void begin() {
        if (!bedrock) {
            eggDisplay = spawnItem(new ItemStack(egg.icon()), 0.9f);
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (!player.isOnline()) {
                    stop();
                    return;
                }
                frame();
                if (++elapsed > duration) stop();
            } catch (Throwable t) {
                plugin.getLogger().warning("Pet hatch failed for " + player.getName() + " (" + t + ").");
                stop();
            }
        }, 0L, 1L);
    }

    private void frame() {
        double p = (double) elapsed / duration;
        if (p < HATCH_AT) {
            wobble(p / HATCH_AT);
        } else if (!hatched) {
            hatched = true;
            hatch();
        } else {
            settle((p - HATCH_AT) / (1.0 - HATCH_AT));
        }
    }

    // ---------------------------------------------------------------
    // Act one: the wobble
    // ---------------------------------------------------------------

    private void wobble(double t) {
        Color[] colours = colours(egg.colors());
        // Rocking: amplitude and speed both climb, so the last second
        // shakes and the first only sways.
        if (eggDisplay != null && eggDisplay.isValid()) {
            double speed = 0.15 + t * 0.9;
            double angle = Math.sin(elapsed * speed) * (0.08 + t * 0.42);
            float bob = (float) (Math.sin(elapsed * 0.12) * 0.05);
            eggDisplay.setTransformation(new Transformation(
                    new Vector3f(0f, bob, 0f),
                    new Quaternionf(new AxisAngle4f((float) angle, 0f, 0f, 1f)),
                    new Vector3f(0.9f), new Quaternionf()));
            eggDisplay.setInterpolationDelay(0);
            eggDisplay.setInterpolationDuration(1);
        }
        // Dust drawn in toward the egg from a shrinking ring, every second
        // tick: cheap, and it reads as the egg pulling something in.
        if (elapsed % 2 == 0) {
            double radius = 1.6 - t * 1.0;
            int points = 6 + (int) (t * 6);
            double spin = elapsed * 0.18;
            for (int i = 0; i < points; i++) {
                double a = spin + (Math.PI * 2 / points) * i;
                Location at = centre.clone().add(Math.cos(a) * radius, Math.sin(a * 2) * 0.25, Math.sin(a) * radius);
                Color c = colours[i % colours.length];
                player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.0f + (float) t));
            }
        }
        // A soft climbing tick under it, the tension dial.
        if (elapsed % 6 == 0) {
            player.playSound(centre, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, (float) (0.6 + t * 1.3));
        }
        // Three cracks, at a quarter, half and three quarters of the
        // wobble, louder and higher each time.
        int hatchFrame = (int) (duration * HATCH_AT);
        for (int k = 1; k <= 3; k++) {
            if (elapsed == Math.round(hatchFrame * k / 4.0)) crack(k);
        }
    }

    private void crack(int which) {
        Sound sound = egg.icon() == org.bukkit.Material.SNIFFER_EGG ? Sound.BLOCK_SNIFFER_EGG_CRACK
                : Sound.ENTITY_TURTLE_EGG_CRACK;
        player.playSound(centre, sound, 0.6f + which * 0.2f, 0.8f + which * 0.2f);
        player.spawnParticle(Particle.ITEM, centre, 6 + which * 4, 0.15, 0.15, 0.15, 0.08, new ItemStack(egg.icon()));
        player.spawnParticle(Particle.CLOUD, centre, 2 + which, 0.1, 0.1, 0.1, 0.02);
    }

    // ---------------------------------------------------------------
    // Act two: the hatch, one frame
    // ---------------------------------------------------------------

    private void hatch() {
        if (eggDisplay != null && eggDisplay.isValid()) eggDisplay.remove();
        Rarity rarity = pet.rarity();
        int size = Math.max(0, rarity.ordinal()); // Common 0 to Divine 6
        Color[] colours = colours(pet.colors());

        player.spawnParticle(Particle.FLASH, centre, 1, 0, 0, 0, 0, colours[colours.length - 1]);
        Sound hatchSound = egg.icon() == org.bukkit.Material.SNIFFER_EGG ? Sound.BLOCK_SNIFFER_EGG_HATCH
                : Sound.ENTITY_TURTLE_EGG_HATCH;
        player.playSound(centre, hatchSound, 1.0f, 1.0f);
        // Impact low, body mid, sparkle high (the aura skill's three layers).
        player.playSound(centre, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.playSound(centre, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
        if (size >= Rarity.EPIC.ordinal()) player.playSound(centre, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
        if (size >= Rarity.MYTHICAL.ordinal()) player.playSound(centre, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
        if (rarity == Rarity.DIVINE) player.playSound(centre, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.6f);

        // A sphere of the pet's colours, wider for rarer pets, thrown
        // outward with count 0 so every mote flies along its own line.
        int rings = 4 + size;
        int perRing = 10 + size * 3;
        float speed = 0.18f + size * 0.05f;
        for (int r = 1; r <= rings; r++) {
            double phi = Math.PI * r / (rings + 1);
            for (int i = 0; i < perRing; i++) {
                double a = (Math.PI * 2 / perRing) * i + r;
                double dx = Math.sin(phi) * Math.cos(a);
                double dy = Math.cos(phi);
                double dz = Math.sin(phi) * Math.sin(a);
                player.spawnParticle(Particle.END_ROD, centre, 0, dx, dy, dz, speed * 0.6);
            }
        }
        for (int i = 0; i < 24 + size * 8; i++) {
            Color c = colours[i % colours.length];
            player.spawnParticle(Particle.DUST, centre, 1, 0.5 + size * 0.1, 0.5, 0.5 + size * 0.1, 0,
                    new Particle.DustOptions(c, 1.6f));
        }
        if (size >= Rarity.LEGENDARY.ordinal()) {
            player.spawnParticle(Particle.TOTEM_OF_UNDYING, centre, 30 + size * 10, 0.3, 0.3, 0.3, 0.5);
        }

        if (!bedrock) {
            petDisplay = spawnItem(new ItemStack(pet.icon()), 0.1f);
        }
        String name = Lore.gradient(pet.display(), true, pet.stops());
        player.sendTitle(name, isNew
                ? ChatColor.GRAY + "a new " + ChatColor.WHITE + rarity.displayName() + ChatColor.GRAY + " pet"
                : ChatColor.GRAY + "copy " + ChatColor.GREEN + copies, 0, 50, 15);
        if (after != null) after.run();
    }

    // ---------------------------------------------------------------
    // Act three: the pet settles
    // ---------------------------------------------------------------

    private void settle(double t) {
        if (petDisplay != null && petDisplay.isValid()) {
            // Grows out of the burst with a small overshoot, then holds.
            double grow = t < 0.35 ? t / 0.35 * 1.25 : 1.25 - Math.min(1.0, (t - 0.35) / 0.25) * 0.25;
            float scale = (float) (1.1 * grow);
            float spin = (float) (elapsed * 0.12);
            petDisplay.setTransformation(new Transformation(
                    new Vector3f(0f, (float) (Math.sin(elapsed * 0.15) * 0.06), 0f),
                    new Quaternionf(new AxisAngle4f(spin, 0f, 1f, 0f)),
                    new Vector3f(scale), new Quaternionf()));
            petDisplay.setInterpolationDelay(0);
            petDisplay.setInterpolationDuration(1);
        }
        if (elapsed % 3 == 0) {
            Color[] colours = colours(pet.colors());
            player.spawnParticle(Particle.DUST, centre, 2, 0.35, 0.35, 0.35, 0,
                    new Particle.DustOptions(colours[ThreadLocalRandom.current().nextInt(colours.length)], 0.8f));
        }
    }

    // ---------------------------------------------------------------

    private ItemDisplay spawnItem(ItemStack item, float scale) {
        ItemDisplay spawned = centre.getWorld().spawn(centre, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(0);
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale), new Quaternionf()));
            display.getPersistentDataContainer().set(SolRNGPlugin.key("solrng_aura"), PersistentDataType.BYTE, (byte) 1);
        });
        // Shown once it exists; hidden from everybody else from the start.
        player.showEntity(plugin, spawned);
        return spawned;
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!hatched) {
            // Cut short before the pet showed: it is already saved, so the
            // player still gets told what came out.
            hatched = true;
            if (after != null && player.isOnline()) after.run();
        }
        if (eggDisplay != null && eggDisplay.isValid()) eggDisplay.remove();
        if (petDisplay != null && petDisplay.isValid()) petDisplay.remove();
        RUNNING.remove(player.getUniqueId(), this);
    }

    private static Color[] colours(java.util.List<String> hexes) {
        if (hexes == null || hexes.isEmpty()) return new Color[]{Color.fromRGB(199, 125, 255)};
        Color[] out = new Color[hexes.size()];
        for (int i = 0; i < hexes.size(); i++) {
            String clean = hexes.get(i).trim();
            if (clean.startsWith("#")) clean = clean.substring(1);
            int rgb;
            try {
                rgb = Integer.parseInt(clean, 16);
            } catch (NumberFormatException ex) {
                rgb = 0xC77DFF;
            }
            // Never pure white: it reads as a glitch rather than light.
            if (rgb == 0xFFFFFF) rgb = 0xFFFCE0;
            out[i] = Color.fromRGB(rgb);
        }
        return out;
    }
}
