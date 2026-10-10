package com.spacerng.solrng.pet;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.rarity.Rarity;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Opening eggs (V362), from Leon's screenshots: a row of heads floats in
 * front of the player, shakes harder and harder, and then every head turns
 * into the pet that came out of it with its name over it.
 *
 * One head per egg opened, up to nine (three rows of three). A bigger
 * auto open batch shows its nine rarest. The heads are Leon's own skull
 * for now, {@code pets.eggs.open-head}, because he is drawing proper egg
 * heads later.
 *
 * The pets are already decided and saved before this starts; it only
 * decides when the player finds out, so a quit or a reload halfway costs
 * nothing. Everything is the opener's alone: the displays are hidden from
 * everybody else and every particle and sound goes to one player. They
 * are non persistent and carry the aura tag, so the aura sweep clears
 * anything a crash leaves. Bedrock never gets here (Geyser cannot draw an
 * item display); the egg screen keeps its chat line for them.
 */
public final class PetOpenShow {

    private static final Map<UUID, PetOpenShow> RUNNING = new HashMap<>();
    /** At most this many heads; three rows of three. */
    public static final int MAX_SHOWN = 9;
    private static final int SHAKE_TICKS = 40;
    private static final int HOLD_TICKS = 60;
    private static final float HEAD_SIZE = 0.75f;
    private static final float PET_SIZE = 0.8f;
    private static final double SPACING = 1.05;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    // Leon's skull, filled in from Mojang once and then kept, so the
    // first opening after a restart is the only one that might show a
    // plain head while the texture is still on its way.
    private static ItemStack cachedHead;
    private static String cachedFor;
    private static boolean fetching;

    private final SolRNGPlugin plugin;
    private final Player player;
    private final List<PetManager.Made> shown;
    private final Runnable after;
    private final List<Location> spots = new ArrayList<>();
    private final List<ItemDisplay> heads = new ArrayList<>();
    private final List<TextDisplay> names = new ArrayList<>();
    private BukkitTask task;
    private int elapsed;
    private boolean revealed;

    private PetOpenShow(SolRNGPlugin plugin, Player player, List<PetManager.Made> made, Runnable after) {
        this.plugin = plugin;
        this.player = player;
        this.after = after;
        List<PetManager.Made> pick = new ArrayList<>(made);
        if (pick.size() > MAX_SHOWN) {
            pick.sort(Comparator.comparingInt((PetManager.Made m) -> m.type().rarity().ordinal()).reversed());
            pick = new ArrayList<>(pick.subList(0, MAX_SHOWN));
        }
        this.shown = pick;
        layout();
    }

    public static boolean isRunning(Player player) {
        return RUNNING.containsKey(player.getUniqueId());
    }

    /** Starts one; {@code after} runs on the reveal frame (chat lines). */
    public static void start(SolRNGPlugin plugin, Player player, List<PetManager.Made> made, Runnable after) {
        PetOpenShow running = RUNNING.remove(player.getUniqueId());
        if (running != null) running.stop();
        if (made.isEmpty()) {
            if (after != null) after.run();
            return;
        }
        PetOpenShow show = new PetOpenShow(plugin, player, made, after);
        RUNNING.put(player.getUniqueId(), show);
        show.begin();
    }

    /** For onDisable: nothing is left floating after a reload. */
    public static void stopAll() {
        for (PetOpenShow show : RUNNING.values().toArray(new PetOpenShow[0])) show.stop();
        RUNNING.clear();
    }

    /**
     * Three across, centred, three blocks ahead at eye height. More than
     * three stack upward in rows, the first row lowest, so nine is a
     * square the player sees whole without looking up.
     */
    private void layout() {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) forward = new Vector(0, 0, 1);
        forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location base = eye.clone().add(forward.clone().multiply(3.2)).add(0, -0.45, 0);
        int n = shown.size();
        int rows = (n + 2) / 3;
        for (int i = 0; i < n; i++) {
            int row = i / 3;
            int inRow = Math.min(3, n - row * 3);
            int col = i % 3;
            double x = (col - (inRow - 1) / 2.0) * SPACING;
            double y = (row - (rows - 1) / 2.0) * SPACING * 0.95 + (rows > 1 ? 0.35 : 0.0);
            Location at = base.clone().add(right.clone().multiply(x)).add(0, y, 0);
            // A display copies the rotation of wherever it is spawned.
            at.setYaw(0f);
            at.setPitch(0f);
            spots.add(at);
        }
    }

    private void begin() {
        ItemStack head = head(plugin);
        for (Location at : spots) {
            heads.add(spawnItem(at, head, true));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 0.8f, 0.7f);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                if (!player.isOnline()) {
                    stop();
                    return;
                }
                frame();
                if (++elapsed > SHAKE_TICKS + HOLD_TICKS) stop();
            } catch (Throwable t) {
                plugin.getLogger().warning("Egg opening failed for " + player.getName() + " (" + t + ").");
                stop();
            }
        }, 0L, 1L);
    }

    private void frame() {
        if (elapsed < SHAKE_TICKS) {
            shake((double) elapsed / SHAKE_TICKS);
        } else if (!revealed) {
            revealed = true;
            reveal();
        } else {
            settle((double) (elapsed - SHAKE_TICKS) / HOLD_TICKS);
        }
    }

    /** The heads rock, faster and wider; a tick under it climbs in pitch. */
    private void shake(double t) {
        double speed = 0.35 + t * 1.25;
        double amplitude = 0.10 + t * 0.45;
        for (int i = 0; i < heads.size(); i++) {
            ItemDisplay head = heads.get(i);
            if (!head.isValid()) continue;
            double angle = Math.sin(elapsed * speed + i * 0.9) * amplitude;
            float hop = t > 0.7 ? (float) (Math.abs(Math.sin(elapsed * 0.9 + i)) * 0.06) : 0f;
            pose(head, new Quaternionf().rotateY((float) Math.PI).rotateZ((float) angle), HEAD_SIZE, hop);
        }
        if (elapsed % 4 == 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, (float) (0.6 + t * 1.3));
        }
        if (elapsed % 10 == 5) {
            for (Location at : spots) {
                player.spawnParticle(Particle.EGG_CRACK, at, 3, 0.2, 0.2, 0.2, 0.02);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_TURTLE_EGG_CRACK, 0.5f + (float) t * 0.5f,
                    0.8f + (float) t * 0.6f);
        }
    }

    /** One frame: every head becomes its pet, with a burst in its colours. */
    private void reveal() {
        Rarity best = Rarity.COMMON;
        for (int i = 0; i < shown.size() && i < heads.size(); i++) {
            PetManager.Made made = shown.get(i);
            PetType pet = made.type();
            Location at = spots.get(i);
            ItemDisplay head = heads.get(i);
            if (head.isValid()) head.remove();
            heads.set(i, spawnItem(at, new ItemStack(pet.icon()), false));
            names.add(spawnName(at.clone().add(0, 0.62, 0), made));
            if (pet.rarity().ordinal() > best.ordinal()) best = pet.rarity();

            Color[] colours = colours(pet.colors());
            int size = pet.rarity().ordinal();
            player.spawnParticle(Particle.POOF, at, 6, 0.15, 0.15, 0.15, 0.03);
            for (int k = 0; k < 10 + size * 4; k++) {
                Vector dir = Vector.getRandom().subtract(new Vector(0.5, 0.5, 0.5)).normalize();
                player.spawnParticle(Particle.END_ROD, at, 0, dir.getX(), dir.getY(), dir.getZ(),
                        0.08 + size * 0.02);
            }
            player.spawnParticle(Particle.DUST, at, 8 + size * 3, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(colours[k(colours)], 1.4f));
            if (size >= Rarity.LEGENDARY.ordinal()) {
                player.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 25, 0.3, 0.3, 0.3, 0.4);
            }
        }
        // Impact low, body mid, sparkle high; rarer adds a layer.
        Location ear = player.getLocation();
        player.playSound(ear, Sound.ENTITY_TURTLE_EGG_HATCH, 1.0f, 0.9f);
        player.playSound(ear, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        player.playSound(ear, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.7f);
        if (best.ordinal() >= Rarity.EPIC.ordinal()) player.playSound(ear, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        if (best.ordinal() >= Rarity.MYTHICAL.ordinal()) player.playSound(ear, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
        if (best.ordinal() >= Rarity.DIVINE.ordinal()) player.playSound(ear, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.6f);
        if (after != null) after.run();
    }

    /** The pets pop past their size, settle, and turn slowly. */
    private void settle(double t) {
        double grow = t < 0.12 ? 0.4 + t / 0.12 * 0.85 : 1.25 - Math.min(1.0, (t - 0.12) / 0.12) * 0.25;
        for (int i = 0; i < heads.size(); i++) {
            ItemDisplay pet = heads.get(i);
            if (!pet.isValid()) continue;
            float bob = (float) (Math.sin(elapsed * 0.15 + i) * 0.05);
            pose(pet, new Quaternionf().rotateY((float) (Math.sin(elapsed * 0.08 + i) * 0.5)),
                    (float) (PET_SIZE * grow), bob);
        }
    }

    private static void pose(ItemDisplay display, Quaternionf rotation, float size, float lift) {
        display.setTransformation(new Transformation(new Vector3f(0f, lift, 0f), rotation,
                new Vector3f(size), new Quaternionf()));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
    }

    private ItemDisplay spawnItem(Location at, ItemStack item, boolean isHead) {
        ItemDisplay spawned = at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            tag(display);
            display.setBillboard(Display.Billboard.VERTICAL);
            // A skull's face is on the north side of its own model and a
            // billboard turns +Z at the camera: half a turn shows the face.
            display.setTransformation(new Transformation(new Vector3f(),
                    isHead ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf(),
                    new Vector3f(isHead ? HEAD_SIZE : PET_SIZE * 0.4f), new Quaternionf()));
        });
        player.showEntity(plugin, spawned);
        return spawned;
    }

    private TextDisplay spawnName(Location at, PetManager.Made made) {
        PetType pet = made.type();
        String name = made.trashed()
                ? ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + pet.display()
                : Lore.gradient(pet.display(), true, pet.stops());
        String under = plugin.getRarityManager().style(pet.rarity(), pet.rarity().displayName())
                + (made.isNew() ? ChatColor.GREEN + " new" : "");
        TextDisplay spawned = at.getWorld().spawn(at, TextDisplay.class, display -> {
            tag(display);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.text(LEGACY.deserialize(name + "\n" + under));
            display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.8f), new Quaternionf()));
        });
        player.showEntity(plugin, spawned);
        return spawned;
    }

    private static void tag(Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTeleportDuration(0);
        display.getPersistentDataContainer().set(SolRNGPlugin.key("solrng_aura"), PersistentDataType.BYTE, (byte) 1);
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (!revealed) {
            // Cut short before the pets showed: they are already saved,
            // so the player still gets told what came out.
            revealed = true;
            if (after != null && player.isOnline()) after.run();
        }
        for (Entity e : heads) if (e.isValid()) e.remove();
        for (Entity e : names) if (e.isValid()) e.remove();
        RUNNING.remove(player.getUniqueId(), this);
    }

    /**
     * The egg head: the skull named in pets.eggs.open-head. The texture is
     * fetched from Mojang off the main thread once and kept; until it
     * lands the head carries the name alone, which the client resolves on
     * its own.
     */
    static ItemStack head(SolRNGPlugin plugin) {
        String owner = plugin.getConfig().getString("pets.eggs.open-head", "LeonAryns");
        if (cachedHead != null && owner.equalsIgnoreCase(cachedFor)) return cachedHead.clone();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (owner == null || owner.isBlank()) return head;
        PlayerProfile profile = Bukkit.createProfile(owner);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }
        if (!fetching) {
            fetching = true;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                PlayerProfile full = Bukkit.createProfile(owner);
                boolean ok = full.complete(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    fetching = false;
                    if (!ok) return;
                    ItemStack done = new ItemStack(Material.PLAYER_HEAD);
                    if (done.getItemMeta() instanceof SkullMeta meta) {
                        meta.setPlayerProfile(full);
                        done.setItemMeta(meta);
                    }
                    cachedHead = done;
                    cachedFor = owner;
                });
            });
        }
        return head;
    }

    private static int k(Color[] colours) {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(colours.length);
    }

    private static Color[] colours(List<String> hexes) {
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
            out[i] = Color.fromRGB(rgb);
        }
        return out;
    }
}
