package com.spacerng.solrng.platform;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a player is on Bedrock Edition (V223).
 *
 * Minehut lets Bedrock players in through Geyser, which translates their
 * client to Java on the way in. Most of the game survives the trip, but a
 * few things do not exist on the other side at all: item and block
 * displays, text objects (the sprites and heads drawn inside text), telling
 * a left click from a right click in a menu, and number keys in a menu.
 * Everything that works around one of those asks this class first.
 *
 * Three ways to tell, any one of them is enough:
 * <ul>
 *   <li>Floodgate, the part of Geyser that logs Bedrock players in, gives
 *       them a UUID whose first half is zero. A Java account never has one.
 *   <li>Geyser reports its own name as the client brand.
 *   <li>The Floodgate or Geyser API, when either is installed on this
 *       server, asked by reflection so neither is a dependency.
 * </ul>
 *
 * {@link #force} lets an admin switch Bedrock mode on for their own Java
 * account, so every Bedrock path can be tested without a Bedrock device.
 */
public final class Bedrock {

    private static final Set<UUID> known = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> forced = ConcurrentHashMap.newKeySet();

    // The API lookups, found once. A missing API is remembered too, so a
    // server without Floodgate does not search for it on every call.
    private static volatile boolean apiSearched;
    private static volatile Object floodgateApi;
    private static volatile Method floodgateCheck;
    private static volatile Object geyserApi;
    private static volatile Method geyserCheck;

    private Bedrock() {
    }

    /** True for a Bedrock player, or for an admin who switched Bedrock mode on. */
    public static boolean is(Player player) {
        if (player == null) return false;
        UUID id = player.getUniqueId();
        if (forced.contains(id) || known.contains(id)) return true;
        if (detect(player)) {
            known.add(id);
            return true;
        }
        return false;
    }

    /** True only when the client really is Bedrock, whatever an admin forced. */
    public static boolean isReally(Player player) {
        return player != null && detect(player);
    }

    /** How a player was recognised, for /rngadmin bedrock. */
    public static String how(Player player) {
        if (player == null) return "offline";
        UUID id = player.getUniqueId();
        if (id.getMostSignificantBits() == 0L) return "Floodgate UUID";
        String brand = player.getClientBrandName();
        if (brand != null && brand.toLowerCase(Locale.ROOT).contains("geyser")) return "Geyser client";
        if (askApis(id)) return "Floodgate/Geyser API";
        if (forced.contains(id)) return "forced by an admin";
        return "Java";
    }

    /** Switches Bedrock mode on or off for a Java account. Returns the new state. */
    public static boolean force(Player player, boolean on) {
        if (on) forced.add(player.getUniqueId());
        else forced.remove(player.getUniqueId());
        return on;
    }

    public static boolean isForced(Player player) {
        return player != null && forced.contains(player.getUniqueId());
    }

    /** Forgets a player who left, so a later Java login on a shared UUID is judged afresh. */
    public static void forget(UUID id) {
        known.remove(id);
    }

    private static boolean detect(Player player) {
        UUID id = player.getUniqueId();
        if (id.getMostSignificantBits() == 0L) return true;
        String brand = player.getClientBrandName();
        if (brand != null && brand.toLowerCase(Locale.ROOT).contains("geyser")) return true;
        return askApis(id);
    }

    private static boolean askApis(UUID id) {
        if (!apiSearched) findApis();
        try {
            if (floodgateCheck != null && Boolean.TRUE.equals(floodgateCheck.invoke(floodgateApi, id))) return true;
            if (geyserCheck != null && Boolean.TRUE.equals(geyserCheck.invoke(geyserApi, id))) return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // An API that changed shape is treated as absent.
        }
        return false;
    }

    private static synchronized void findApis() {
        if (apiSearched) return;
        try {
            Class<?> type = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = type.getMethod("getInstance").invoke(null);
            floodgateCheck = type.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            floodgateCheck = null;
        }
        try {
            Class<?> type = Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserApi = type.getMethod("api").invoke(null);
            geyserCheck = type.getMethod("isBedrockPlayer", UUID.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            geyserCheck = null;
        }
        apiSearched = true;
    }
}
