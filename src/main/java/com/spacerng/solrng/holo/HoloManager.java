package com.spacerng.solrng.holo;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.crate.Crate;
import com.spacerng.solrng.gui.Lore;
import com.spacerng.solrng.leaderboard.LeaderboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Text and heads floating in the world, drawn by the plugin itself:
 *
 *   PANEL  the text over an NPC: a big title, a rule, a short description,
 *          another rule and a call to action, from holograms.panels
 *   BOARD  a leaderboard: title, the top ten with each player's head drawn
 *          inside the text, refreshed on a timer
 *   CRATE  a crate as a big floating head with a panel above it; the block
 *          under the head is an invisible barrier registered as the crate,
 *          so clicking it goes through CrateListener like any crate
 *
 * Every piece is its own TextDisplay, which is what lets a title be far
 * bigger than the lines under it; a hologram plugin draws a whole block of
 * text at one size. Spots live in holograms.yml. Entities are never saved
 * into the world: they vanish with their chunk, come back when it loads,
 * and anything tagged from an earlier run is swept on start.
 */
public final class HoloManager {

    public enum Kind { PANEL, BOARD, CRATE, LEADER }

    public record Spot(String id, Kind kind, String key, Location at, float yaw, ItemStack head) {
    }

    private static final long FRAME_TICKS = 20L;
    // How tall one line of text is at scale 1, in blocks, and the air between pieces.
    private static final double LINE = 0.25;
    private static final double GAP = 0.06;

    private static final Map<String, String> BOARD_COLOURS = Map.of(
            "index", "#80DEEA", "shiny", "#F48FB1", "rolls", "#FFF59D", "coins", "#FFD54F",
            "money", "#A5D6A7", "farming_total", "#C5E1A5", "farming", "#FFCC80", "prestige", "#CE93D8");
    private static final Map<String, String> BOARD_NAMES = Map.of(
            "index", "Total Index", "shiny", "Shiny Index", "rolls", "Rolls", "coins", "Coins",
            "money", "Money", "farming_total", "Crops", "farming", "Daily Farming", "prestige", "Prestige");
    private static final Map<String, String> BOARD_UNITS = Map.of(
            "index", "drops", "shiny", "shinies", "rolls", "rolls", "coins", "Coins",
            "money", "Money", "farming_total", "crops", "farming", "crops", "prestige", "prestige");

    private final SolRNGPlugin plugin;
    private final File file;
    private final NamespacedKey tagKey;
    private final Map<String, Spot> spots = new LinkedHashMap<>();
    // Spots in worlds that weren't loaded when holograms.yml was read, kept for the next save.
    private final Map<String, Map<String, Object>> unresolved = new LinkedHashMap<>();
    private final Map<String, List<Display>> drawn = new HashMap<>();
    private final Map<String, TextDisplay> boardBodies = new HashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();

    private float viewRange = 1.0f;
    private float titleScale = 2.4f;
    private float textScale = 1.1f;
    private double panelHeight = 2.3;
    private float crateHeadScale = 2.6f;
    private double crateHeadLift = 0.15;
    private double crateSpinDegrees = 90.0;
    private double crateBob = 0.12;
    private final Map<String, ItemDisplay> crateHeads = new HashMap<>();
    // A podium's three heads, name tags and whose heads they show, indexed by rank (#1, #2, #3).
    private static final double PODIUM_YOU_Y = 0.1;
    private static final double PODIUM_READ_RANGE = 48.0;
    // How much bigger than panel text the podium's pieces are, and how far #1 stands above #2 and #3.
    private static final double PODIUM_TAG = 1.45;
    private static final double PODIUM_TEXT = 1.25;
    private static final double PODIUM_TITLE = 1.8;
    private static final double PODIUM_FIRST_LIFT = 0.9;
    private float podiumHeadScale = 3.2f;
    private float podiumTextScale = 2.0f;
    private double podiumSpacing = 2.5;
    private final Map<String, ItemDisplay[]> podiumHeads = new HashMap<>();
    private final Map<String, TextDisplay[]> podiumTags = new HashMap<>();
    private final Map<String, java.util.UUID[]> podiumShown = new HashMap<>();
    private final Map<String, TextDisplay> podiumTimers = new HashMap<>();
    // Each reader's own "YOU" line under a podium, shown to that reader only.
    private final Map<String, Map<java.util.UUID, TextDisplay>> podiumYou = new HashMap<>();
    private long boardRefreshTicks = 1200L;

    private BukkitTask task;
    private long ticks;
    private long nextBoardRefresh;

    public HoloManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
        this.tagKey = SolRNGPlugin.key("holo");
        loadSpots();
    }

    public void load(FileConfiguration config) {
        viewRange = (float) config.getDouble("holograms.view-range", 1.0);
        titleScale = (float) config.getDouble("holograms.title-scale", 2.4);
        textScale = (float) config.getDouble("holograms.text-scale", 1.1);
        panelHeight = config.getDouble("holograms.panel-height", 2.3);
        crateHeadScale = (float) config.getDouble("holograms.crate-head-scale", 2.6);
        crateHeadLift = config.getDouble("holograms.crate-head-lift", 0.15);
        // Past about 170 degrees an update, interpolation takes the short way round and spins backwards.
        crateSpinDegrees = Math.max(0.0, Math.min(170.0, config.getDouble("holograms.crate-spin-degrees", 90.0)));
        crateBob = config.getDouble("holograms.crate-bob", 0.12);
        podiumHeadScale = (float) config.getDouble("holograms.podium-head-scale", 3.2);
        // The podium has its own text size. It is read from across the
        // whole spawn rather than from in front of an NPC, so tying it to
        // the panel size meant one of the two was always wrong.
        podiumTextScale = (float) config.getDouble("holograms.podium-text-scale", 2.0);
        podiumSpacing = config.getDouble("holograms.podium-spacing", 4.5);
        boardRefreshTicks = Math.max(200L, config.getLong("holograms.board-refresh-seconds", 60L) * 20L);
        // New text only reaches entities drawn after it, so take everything
        // down and let the next frame draw it fresh.
        despawnAll();
        nextBoardRefresh = 0L;
    }

    // ---------------------------------------------------------- lifecycle

    public void start() {
        if (task != null) task.cancel();
        sweep();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::frame, 40L, FRAME_TICKS);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        despawnAll();
    }

    private void sweep() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(ItemDisplay.class, TextDisplay.class)) {
                if (entity.getPersistentDataContainer().has(tagKey, PersistentDataType.STRING)) entity.remove();
            }
        }
    }

    // --------------------------------------------------------------- spots

    public Set<String> panelIds() {
        ConfigurationSection panels = plugin.getConfig().getConfigurationSection("holograms.panels");
        return panels == null ? Set.of() : panels.getKeys(false);
    }

    /** A panel above where an admin stands, facing the way they look. */
    public void placePanel(String panel, Location feet) {
        add(Kind.PANEL, panel, feet.clone().add(0, panelHeight, 0), feet.getYaw(), null);
    }

    /** A board two blocks in front of an admin, turned to face them. */
    public void placeBoard(String board, Location eye) {
        Vector ahead = eye.getDirection().setY(0);
        if (ahead.lengthSquared() < 0.01) {
            double yaw = Math.toRadians(eye.getYaw());
            ahead = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Location at = eye.clone().add(ahead.normalize().multiply(2.0));
        at.setY(eye.getY() - 1.4);
        add(Kind.BOARD, board, at, eye.getYaw() + 180f, null);
    }

    /** A crate's head and text over the given block. */
    public void placeCrate(String crateId, Block block, float yaw, ItemStack head) {
        add(Kind.CRATE, crateId, block.getLocation().add(0.5, 0.0, 0.5), yaw, head);
    }

    /** A board's top three as a podium three blocks in front of an admin, turned to face them. */
    public void placeLeader(String board, Location eye) {
        Vector ahead = eye.getDirection().setY(0);
        if (ahead.lengthSquared() < 0.01) {
            double yaw = Math.toRadians(eye.getYaw());
            ahead = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Location at = eye.clone().add(ahead.normalize().multiply(3.0));
        at.setY(eye.getY() - 1.62);
        add(Kind.LEADER, board, at, eye.getYaw() + 180f, null);
    }

    /** Removes the crate spot on this block, and the barrier under it. */
    public boolean removeCrate(Block block) {
        String found = null;
        for (Spot spot : spots.values()) {
            if (spot.kind() == Kind.CRATE && spot.at().getWorld() != null
                    && spot.at().getWorld().equals(block.getWorld())
                    && spot.at().getBlockX() == block.getX() && spot.at().getBlockY() == block.getY()
                    && spot.at().getBlockZ() == block.getZ()) {
                found = spot.id();
                break;
            }
        }
        if (found == null) return false;
        spots.remove(found);
        despawn(found);
        if (block.getType() == Material.BARRIER) block.setType(Material.AIR);
        saveSpots();
        return true;
    }

    /** Removes panels and boards near a spot. Crates only go with /rngadmin crate remove. */
    public int removeNear(Location at, double radius) {
        List<String> doomed = new ArrayList<>();
        for (Spot spot : spots.values()) {
            if (spot.kind() == Kind.CRATE || spot.at().getWorld() == null) continue;
            if (spot.at().getWorld().equals(at.getWorld()) && spot.at().distanceSquared(at) <= radius * radius) {
                doomed.add(spot.id());
            }
        }
        for (String id : doomed) {
            spots.remove(id);
            despawn(id);
        }
        if (!doomed.isEmpty()) saveSpots();
        return doomed.size();
    }

    public Collection<Spot> list() {
        return spots.values();
    }

    private void add(Kind kind, String key, Location at, float yaw, ItemStack head) {
        String base = kind.name().toLowerCase(Locale.ROOT) + "-" + key;
        int n = 1;
        while (spots.containsKey(base + "-" + n) || unresolved.containsKey(base + "-" + n)) n++;
        Spot spot = new Spot(base + "-" + n, kind, key.toLowerCase(Locale.ROOT),
                new Location(at.getWorld(), at.getX(), at.getY(), at.getZ()), yaw, head);
        spots.put(spot.id(), spot);
        saveSpots();
        if (at.getWorld() != null) draw(spot);
    }

    private void loadSpots() {
        spots.clear();
        unresolved.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yml.getConfigurationSection("spots");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;
            World world = Bukkit.getWorld(s.getString("world", ""));
            if (world == null) {
                unresolved.put(id, s.getValues(false));
                continue;
            }
            try {
                Kind kind = Kind.valueOf(s.getString("kind", "PANEL").toUpperCase(Locale.ROOT));
                Location at = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
                spots.put(id, new Spot(id, kind, s.getString("key", ""), at, (float) s.getDouble("yaw"),
                        s.getItemStack("head")));
            } catch (IllegalArgumentException ignored) {
                // A hand-edited entry that doesn't parse is dropped, not fatal.
            }
        }
    }

    private void saveSpots() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Map<String, Object>> entry : unresolved.entrySet()) {
            yml.createSection("spots." + entry.getKey(), entry.getValue());
        }
        for (Spot spot : spots.values()) {
            String path = "spots." + spot.id() + ".";
            yml.set(path + "kind", spot.kind().name());
            yml.set(path + "key", spot.key());
            yml.set(path + "world", spot.at().getWorld() == null ? "" : spot.at().getWorld().getName());
            yml.set(path + "x", spot.at().getX());
            yml.set(path + "y", spot.at().getY());
            yml.set(path + "z", spot.at().getZ());
            yml.set(path + "yaw", (double) spot.yaw());
            if (spot.head() != null) yml.set(path + "head", spot.head());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Couldn't save holograms.yml: " + ex.getMessage());
        }
    }

    // --------------------------------------------------------------- frame

    private void frame() {
        try {
            ticks += FRAME_TICKS;
            boolean refresh = ticks >= nextBoardRefresh;
            if (refresh) nextBoardRefresh = ticks + boardRefreshTicks;

            for (Spot spot : spots.values()) {
                World world = spot.at().getWorld();
                boolean loaded = world != null
                        && world.isChunkLoaded(spot.at().getBlockX() >> 4, spot.at().getBlockZ() >> 4);
                List<Display> pieces = drawn.get(spot.id());
                if (!loaded) {
                    if (pieces != null) despawn(spot.id());
                    continue;
                }
                boolean alive = pieces != null && !pieces.isEmpty();
                if (alive) {
                    for (Display piece : pieces) {
                        if (!piece.isValid()) {
                            alive = false;
                            break;
                        }
                    }
                }
                if (!alive) {
                    despawn(spot.id());
                    draw(spot);
                } else if (refresh && spot.kind() == Kind.BOARD) {
                    TextDisplay body = boardBodies.get(spot.id());
                    if (body != null && body.isValid()) body.text(boardBody(spot.key()));
                } else if (spot.kind() == Kind.LEADER) {
                    tickPodium(spot, refresh || ticks % 100 == 0);
                }
                if (spot.kind() == Kind.CRATE) spinCrate(spot.id());
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Hologram frame failed: " + ex);
        }
    }

    /**
     * One step of a crate head's float: a turn and a bob, set once a frame
     * with the client gliding between them, so it moves smoothly at the
     * cost of one update a second.
     */
    private void spinCrate(String id) {
        ItemDisplay head = crateHeads.get(id);
        if (head == null || !head.isValid()) return;
        double seconds = ticks / 20.0;
        float angle = (float) Math.toRadians((seconds * crateSpinDegrees) % 360.0);
        float bob = (float) (Math.sin(seconds * Math.PI / 4.0) * crateBob);
        head.setInterpolationDelay(0);
        head.setInterpolationDuration((int) FRAME_TICKS);
        head.setTransformation(new Transformation(new Vector3f(0f, bob, 0f), new Quaternionf().rotateY(angle),
                new Vector3f(crateHeadScale, crateHeadScale, crateHeadScale), new Quaternionf()));
    }

    private void despawn(String id) {
        crateHeads.remove(id);
        podiumHeads.remove(id);
        podiumTags.remove(id);
        podiumShown.remove(id);
        podiumTimers.remove(id);
        Map<java.util.UUID, TextDisplay> readers = podiumYou.remove(id);
        if (readers != null) {
            for (TextDisplay line : readers.values()) {
                if (line.isValid()) line.remove();
            }
        }
        List<Display> pieces = drawn.remove(id);
        if (pieces != null) {
            for (Display piece : pieces) {
                if (piece.isValid()) piece.remove();
            }
        }
        boardBodies.remove(id);
    }

    private void despawnAll() {
        for (String id : new ArrayList<>(drawn.keySet())) despawn(id);
    }

    // ---------------------------------------------------------------- draw

    private void draw(Spot spot) {
        List<Display> pieces = new ArrayList<>();
        drawn.put(spot.id(), pieces);
        switch (spot.kind()) {
            case PANEL -> drawPanel(spot, pieces);
            case BOARD -> drawBoard(spot, pieces);
            case CRATE -> drawCrate(spot, pieces);
            case LEADER -> drawLeader(spot, pieces);
        }
    }

    private void drawPanel(Spot spot, List<Display> pieces) {
        ConfigurationSection panel = plugin.getConfig().getConfigurationSection("holograms.panels." + spot.key());
        if (panel == null) {
            stack(spot, spot.at(), parse("<red>Unknown panel: " + spot.key()), List.of(), null, pieces);
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (String line : panel.getStringList("lines")) lines.add(parse(line));
        String click = panel.getString("click", plugin.getConfig().getString("holograms.click", ""));
        stack(spot, spot.at(), parse(panel.getString("title", spot.key())), lines,
                click == null || click.isBlank() ? null : parse(click), pieces);
    }

    private void drawCrate(Spot spot, List<Display> pieces) {
        // A head model sits in the lower half of its box, so at scale s its
        // bottom is s/2 below the display: lift by that much, plus the float.
        double headY = crateHeadScale / 2.0 + crateHeadLift;
        Location centre = spot.at().clone().add(0, headY, 0);
        centre.setYaw(spot.yaw());
        centre.setPitch(0f);
        ItemStack head = spot.head() == null ? new ItemStack(Material.PLAYER_HEAD) : spot.head().clone();
        ItemDisplay display = centre.getWorld().spawn(centre, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(head);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(crateHeadScale, crateHeadScale, crateHeadScale), new Quaternionf()));
            d.setViewRange(viewRange);
            d.setShadowRadius(0.6f);
            d.setShadowStrength(0.6f);
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
        });
        pieces.add(display);
        crateHeads.put(spot.id(), display);

        Crate crate = plugin.getCrateManager().get(spot.key());
        String name = crate == null ? spot.key() : crate.display();
        String title = gradient(crate == null ? List.of() : crate.colors(),
                "<b>" + name.toUpperCase(Locale.ROOT) + "</b>");
        List<Component> lines = new ArrayList<>();
        List<String> raw = plugin.getConfig().getStringList("crates.types." + spot.key() + ".description");
        if (raw.isEmpty()) {
            raw = List.of("<white>Right-click with a <#FFD54F>key</#FFD54F> to open",
                    "<gray>Left-click to see what's inside");
        }
        for (String line : raw) lines.add(parse(line));
        String click = plugin.getConfig().getString("holograms.click", "");
        stack(spot, spot.at().clone().add(0, headY + crateBob + 0.3, 0), parse(title), lines,
                click == null || click.isBlank() ? null : parse(click), pieces);
    }

    private void drawBoard(Spot spot, List<Display> pieces) {
        String board = spot.key();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("holograms.boards." + board);
        String colour = s == null ? BOARD_COLOURS.getOrDefault(board, "#FFD54F")
                : s.getString("color", BOARD_COLOURS.getOrDefault(board, "#FFD54F"));
        String titleRaw = s != null && s.contains("title") ? s.getString("title")
                : "<" + colour + "><b>" + BOARD_NAMES.getOrDefault(board, board).toUpperCase(Locale.ROOT) + "</b>";

        Location y = spot.at().clone();
        long seconds = boardRefreshTicks / 20L;
        String every = seconds % 60 == 0 ? (seconds == 60 ? "EVERY MINUTE" : "EVERY " + seconds / 60 + " MINUTES")
                : "EVERY " + seconds + " SECONDS";
        TextDisplay footer = text(spot, y, parse("<dark_gray>UPDATES " + every), textScale * 0.9f);
        pieces.add(footer);
        y.add(0, LINE * textScale * 0.9 + GAP * 2, 0);

        Component body = boardBody(board);
        int rows = Math.max(1, PlainTextComponentSerializer.plainText().serialize(body).split("\n", -1).length);
        TextDisplay bodyDisplay = text(spot, y, body, textScale);
        pieces.add(bodyDisplay);
        boardBodies.put(spot.id(), bodyDisplay);
        y.add(0, rows * LINE * textScale + GAP * 2, 0);

        pieces.add(text(spot, y, parse("<dark_gray><b>LEADERBOARD</b>"), textScale * 1.1f));
        y.add(0, LINE * textScale * 1.1 + GAP, 0);
        pieces.add(text(spot, y, parse(titleRaw), titleScale * 0.85f));
    }

    /**
     * A board's podium, like the one Leon picked from another server: the
     * top three as heads side by side, #1 in the middle, #3 on the left and
     * #2 on the right as a reader faces it, each with its name, value and
     * payout above. Under them the title, the payout countdown ticking every
     * second, and a line each reader sees only for themselves: their own
     * place and value.
     */
    private void drawLeader(Spot spot, List<Display> pieces) {
        String board = spot.key();
        Vector right = podiumRight(spot);
        List<LeaderboardManager.Entry> top = podiumTop(board);
        double small = podiumTextScale * PODIUM_TEXT;
        double timerY = PODIUM_YOU_Y + 2 * LINE * small;
        double titleY = timerY + LINE * small + GAP;
        double headY = titleY + LINE * podiumTextScale * PODIUM_TITLE + 0.15 + podiumHeadScale / 2.0;

        ItemDisplay[] heads = new ItemDisplay[3];
        TextDisplay[] tags = new TextDisplay[3];
        java.util.UUID[] shown = new java.util.UUID[3];
        for (int rank = 0; rank < 3; rank++) {
            double side = rank == 0 ? 0.0 : rank == 1 ? podiumSpacing : -podiumSpacing;
            // #1 stands a step above the other two, like the top of a podium.
            Location centre = spot.at().clone().add(right.clone().multiply(side))
                    .add(0, headY + (rank == 0 ? PODIUM_FIRST_LIFT : 0.0), 0);
            centre.setYaw(spot.yaw());
            centre.setPitch(0f);
            LeaderboardManager.Entry entry = rank < top.size() ? top.get(rank) : null;
            java.util.UUID uuid = entry == null ? null : entry.uuid();
            ItemDisplay head = centre.getWorld().spawn(centre, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(leaderHead(uuid));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(podiumHeadScale, podiumHeadScale, podiumHeadScale), new Quaternionf()));
                d.setViewRange(viewRange);
                d.setBrightness(new Display.Brightness(15, 15));
                d.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
            });
            pieces.add(head);
            heads[rank] = head;
            shown[rank] = uuid;
            // A head fills the lower half of its box, so its top sits at the display.
            Location tagAt = centre.clone().add(0, 0.2, 0);
            TextDisplay tag = text(spot, tagAt, podiumTag(board, rank, entry),
                    podiumTextScale * (float) PODIUM_TAG);
            pieces.add(tag);
            tags[rank] = tag;
        }
        podiumHeads.put(spot.id(), heads);
        podiumTags.put(spot.id(), tags);
        podiumShown.put(spot.id(), shown);

        TextDisplay timer = text(spot, spot.at().clone().add(0, timerY, 0), podiumTimer(board), (float) small);
        pieces.add(timer);
        podiumTimers.put(spot.id(), timer);
        pieces.add(text(spot, spot.at().clone().add(0, titleY, 0), parse(podiumTitle(board)),
                podiumTextScale * (float) PODIUM_TITLE));
    }

    /** Once a second: the countdown, now and then the top three, and every reader's own line. */
    private void tickPodium(Spot spot, boolean refreshTop) {
        String board = spot.key();
        TextDisplay timer = podiumTimers.get(spot.id());
        if (timer != null && timer.isValid() && board.equals("farming")) timer.text(podiumTimer(board));
        if (refreshTop) {
            List<LeaderboardManager.Entry> top = podiumTop(board);
            ItemDisplay[] heads = podiumHeads.get(spot.id());
            TextDisplay[] tags = podiumTags.get(spot.id());
            java.util.UUID[] shown = podiumShown.get(spot.id());
            if (heads != null && tags != null && shown != null) {
                for (int rank = 0; rank < 3; rank++) {
                    LeaderboardManager.Entry entry = rank < top.size() ? top.get(rank) : null;
                    java.util.UUID uuid = entry == null ? null : entry.uuid();
                    if (tags[rank] != null && tags[rank].isValid()) tags[rank].text(podiumTag(board, rank, entry));
                    if (!java.util.Objects.equals(uuid, shown[rank]) && heads[rank] != null && heads[rank].isValid()) {
                        heads[rank].setItemStack(leaderHead(uuid));
                        shown[rank] = uuid;
                    }
                }
            }
        }
        tickPodiumReaders(spot);
    }

    /** Gives every player near a podium their own "YOU" line, and takes it down when they leave. */
    private void tickPodiumReaders(Spot spot) {
        World world = spot.at().getWorld();
        if (world == null) return;
        Map<java.util.UUID, TextDisplay> lines = podiumYou.computeIfAbsent(spot.id(), id -> new HashMap<>());
        java.util.Set<java.util.UUID> near = new java.util.HashSet<>();
        Map<java.util.UUID, Integer> places = null;
        for (org.bukkit.entity.Player reader : world.getPlayers()) {
            if (reader.getLocation().distanceSquared(spot.at()) > PODIUM_READ_RANGE * PODIUM_READ_RANGE) continue;
            if (places == null) {
                // One sort for everyone reading this podium this second, not one each.
                places = new HashMap<>();
                List<LeaderboardManager.Entry> all = plugin.getLeaderboardManager().top(spot.key(), Integer.MAX_VALUE);
                for (int i = 0; i < all.size(); i++) places.put(all.get(i).uuid(), i + 1);
            }
            near.add(reader.getUniqueId());
            Component content = podiumYouLine(spot.key(), reader, places);
            TextDisplay line = lines.get(reader.getUniqueId());
            if (line == null || !line.isValid()) {
                line = privateText(spot, spot.at().clone().add(0, PODIUM_YOU_Y, 0), content,
                        podiumTextScale * (float) PODIUM_TEXT);
                reader.showEntity(plugin, line);
                lines.put(reader.getUniqueId(), line);
            } else {
                line.text(content);
            }
        }
        lines.entrySet().removeIf(entry -> {
            if (near.contains(entry.getKey()) && entry.getValue().isValid()) return false;
            if (entry.getValue().isValid()) entry.getValue().remove();
            return true;
        });
    }

    /** The reader's right as they face the podium. */
    private static Vector podiumRight(Spot spot) {
        double yaw = Math.toRadians(spot.yaw());
        return new Vector(Math.cos(yaw), 0, Math.sin(yaw));
    }

    private List<LeaderboardManager.Entry> podiumTop(String board) {
        List<LeaderboardManager.Entry> top = new ArrayList<>();
        for (LeaderboardManager.Entry entry : plugin.getLeaderboardManager().top(board, 3)) {
            if (LeaderboardManager.valueOf(board, entry) > 0) top.add(entry);
        }
        return top;
    }

    private String podiumTitle(String board) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("holograms.boards." + board);
        if (s != null && s.contains("title")) return s.getString("title", board);
        String name = board.equals("farming") ? "Farming Payouts" : BOARD_NAMES.getOrDefault(board, board);
        return "<#FFE9A8><b>" + name.toUpperCase(Locale.ROOT) + "</b>";
    }

    private String podiumUnit(String board) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("holograms.boards." + board);
        if (s != null && s.contains("unit")) return s.getString("unit", "");
        if (board.startsWith("farming")) return "Farmed";
        String unit = BOARD_UNITS.getOrDefault(board, "");
        return unit.isEmpty() ? "" : Character.toUpperCase(unit.charAt(0)) + unit.substring(1);
    }

    /** "#1 Name", "294.4K Farmed" and, on the daily board, "+ 150 Credits (Daily)". */
    private Component podiumTag(String board, int rank, LeaderboardManager.Entry entry) {
        List<Component> rows = new ArrayList<>();
        if (entry == null) {
            rows.add(parse("<#FFE9A8><b>#" + (rank + 1) + "</b></#FFE9A8> <dark_gray>Nobody yet"));
        } else {
            rows.add(Component.text()
                    .append(parse("<#FFE9A8><b>#" + (rank + 1) + "</b></#FFE9A8> "))
                    .append(Component.text(entry.name(), NamedTextColor.WHITE))
                    .build());
            rows.add(parse("<white>" + Lore.shorten(LeaderboardManager.valueOf(board, entry)) + " <gray>"
                    + podiumUnit(board)));
        }
        if (board.equals("farming")) {
            List<Integer> payouts = plugin.getConfig().getIntegerList("leaderboard.farming.credit-payouts");
            if (rank < payouts.size()) {
                rows.add(parse("<#E879F9>+ " + String.format("%,d", payouts.get(rank))
                        + " Credits</#E879F9> <gray>(Daily)"));
            }
        }
        return Component.join(JoinConfiguration.newlines(), rows);
    }

    private Component podiumTimer(String board) {
        if (!board.equals("farming")) return Component.empty();
        long seconds = plugin.getLeaderboardManager().secondsUntilReset();
        return parse("<gray>Resets in <white>" + seconds / 3600 + "h " + (seconds % 3600) / 60 + "m "
                + seconds % 60 + "s");
    }

    /** "YOU: #4,951 Name: 0", for the reader alone. */
    private Component podiumYouLine(String board, org.bukkit.entity.Player reader, Map<java.util.UUID, Integer> places) {
        LeaderboardManager.Entry mine = plugin.getLeaderboardManager().entryOf(reader.getUniqueId());
        long value = mine == null ? 0L : LeaderboardManager.valueOf(board, mine);
        Integer place = places.get(reader.getUniqueId());
        String placeText = place == null || value <= 0 ? "-" : String.format("%,d", place);
        return Component.text()
                .append(parse("<#FFE9A8><b>YOU:</b></#FFE9A8> <gold>#" + placeText + "</gold> "))
                .append(Component.text(reader.getName(), NamedTextColor.WHITE))
                .append(parse("<gray>: <white>" + String.format("%,d", value)))
                .build();
    }

    /** A text piece nobody sees until it is shown to them. */
    private TextDisplay privateText(Spot spot, Location at, Component content, float scale) {
        Location spawnAt = at.clone();
        spawnAt.setYaw(spot.yaw());
        spawnAt.setPitch(0f);
        return spawnAt.getWorld().spawn(spawnAt, TextDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.text(content);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(2000);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setViewRange(viewRange);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
        });
    }

    private static ItemStack leaderHead(java.util.UUID uuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (uuid != null && head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            head.setItemMeta(skull);
        }
        return head;
    }

    /** Picks up the spots of a world that loaded after the plugin did; the next frame draws them. */
    public void resolveWorld(World world) {
        for (var it = unresolved.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Map<String, Object> s = entry.getValue();
            if (!world.getName().equals(String.valueOf(s.get("world")))) continue;
            try {
                Kind kind = Kind.valueOf(String.valueOf(s.getOrDefault("kind", "PANEL")).toUpperCase(Locale.ROOT));
                Location at = new Location(world, number(s.get("x")), number(s.get("y")), number(s.get("z")));
                ItemStack head = s.get("head") instanceof ItemStack stack ? stack : null;
                spots.put(entry.getKey(), new Spot(entry.getKey(), kind, String.valueOf(s.getOrDefault("key", "")),
                        at, (float) number(s.get("yaw")), head));
                it.remove();
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** The top ten as text, each row with the player's head in front of the name. */
    private Component boardBody(String board) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("holograms.boards." + board);
        String colour = s == null ? BOARD_COLOURS.getOrDefault(board, "#FFD54F")
                : s.getString("color", BOARD_COLOURS.getOrDefault(board, "#FFD54F"));
        String unit = s == null ? BOARD_UNITS.getOrDefault(board, "")
                : s.getString("unit", BOARD_UNITS.getOrDefault(board, ""));
        TextColor valueColour = TextColor.fromHexString(colour);
        if (valueColour == null) valueColour = NamedTextColor.GOLD;

        List<Component> rows = new ArrayList<>();
        int place = 0;
        for (LeaderboardManager.Entry entry : plugin.getLeaderboardManager().top(board, 10)) {
            long value = LeaderboardManager.valueOf(board, entry);
            if (value <= 0) continue;
            place++;
            rows.add(Component.text()
                    .append(Component.text("#" + place + " ", place == 1 ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                    .append(Component.object(ObjectContents.playerHead(entry.uuid())))
                    .append(Component.text(" " + entry.name(), NamedTextColor.WHITE))
                    .append(Component.text("  →  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(Lore.shorten(value), valueColour))
                    .append(Component.text(unit.isEmpty() ? "" : " " + unit, NamedTextColor.GRAY))
                    .build());
        }
        // Always ten rows, so a new board already stands at its full height
        // instead of starting as one line near the ground and growing.
        for (int empty = place + 1; empty <= 10; empty++) {
            rows.add(Component.text("#" + empty + "  ...", NamedTextColor.DARK_GRAY));
        }
        return Component.join(JoinConfiguration.newlines(), rows);
    }

    /**
     * Title, rule, lines, rule and call to action, stacked upward from the
     * base: a text display grows up from where it stands, so the bottom
     * piece goes down first.
     */
    private void stack(Spot spot, Location base, Component title, List<Component> lines, Component click,
                       List<Display> pieces) {
        Location y = base.clone();
        int widest = width(title) / 2;
        for (Component line : lines) widest = Math.max(widest, width(line));
        if (click != null) widest = Math.max(widest, width(click));
        Component rule = Component.text(" ".repeat(widest / 4 + 4), NamedTextColor.DARK_GRAY)
                .decorate(TextDecoration.STRIKETHROUGH);

        if (click != null) {
            pieces.add(text(spot, y, click, textScale));
            y.add(0, LINE * textScale + GAP, 0);
        }
        if (!lines.isEmpty()) {
            pieces.add(text(spot, y, rule, textScale));
            y.add(0, LINE * textScale * 0.5 + GAP, 0);
            pieces.add(text(spot, y, Component.join(JoinConfiguration.newlines(), lines), textScale));
            y.add(0, lines.size() * LINE * textScale + GAP, 0);
            pieces.add(text(spot, y, rule, textScale));
            y.add(0, LINE * textScale * 0.5 + GAP, 0);
        }
        pieces.add(text(spot, y, title, titleScale));
    }

    private TextDisplay text(Spot spot, Location at, Component content, float scale) {
        Location spawnAt = at.clone();
        spawnAt.setYaw(spot.yaw());
        spawnAt.setPitch(0f);
        // Boards hang flat like a sign, facing where they were placed from;
        // panels turn to whoever reads them.
        Display.Billboard billboard = spot.kind() == Kind.BOARD ? Display.Billboard.FIXED : Display.Billboard.CENTER;
        return spawnAt.getWorld().spawn(spawnAt, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(content);
            d.setBillboard(billboard);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(2000);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setViewRange(viewRange);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
            d.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, spot.id());
        });
    }

    private Component parse(String raw) {
        try {
            return mini.deserialize(raw);
        } catch (RuntimeException ex) {
            return Component.text(raw);
        }
    }

    /** Roughly how wide a line renders, in font pixels. */
    private static int width(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        int widest = 0;
        for (String line : plain.split("\n", -1)) widest = Math.max(widest, line.length() * 6);
        return widest;
    }

    private static String gradient(List<String> colours, String inner) {
        if (colours.size() >= 2) return "<gradient:" + String.join(":", colours) + ">" + inner + "</gradient>";
        if (colours.size() == 1) return "<" + colours.get(0) + ">" + inner;
        return "<gold>" + inner;
    }
}
