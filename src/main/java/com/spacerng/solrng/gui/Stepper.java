package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * One block that steps through a ladder of rarities (V310).
 *
 * /options used to draw sixteen separate switches: four reveal auras,
 * four announcements and seven drop messages, one per rarity each. Leon's
 * words were "dan is het niet zo druk". Every one of those groups asks the
 * same question - from which tier up do I want this - so each becomes a
 * single item: left click steps the floor up a tier, right click steps it
 * back, and the whole ladder is printed in the tooltip with the current
 * step marked, so the setting is readable without clicking it.
 *
 * The ladder is an index rather than a rarity, because it has two ends
 * that are not rarities: step 0 is everything and the last step is off.
 * Index 1 through band.size() are "this tier and up". It is built from
 * {@link Rarity#values()}, so a new top rarity joins every ladder in the
 * plugin by existing.
 *
 * It wraps on purpose. Bedrock cannot tell a left click from a right one
 * (see {@code platform.Bedrock}), so stepping forward past the last step
 * has to land back on the first or a Bedrock player could never undo an
 * accidental click.
 */
public final class Stepper {

    private Stepper() {
    }

    /** The rarities this ladder can sit on, cheapest first. */
    public static List<Rarity> band(Rarity lowest) {
        List<Rarity> band = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            if (rarity.ordinal() >= lowest.ordinal()) band.add(rarity);
        }
        return band;
    }

    /** How many positions the ladder has: everything, each tier, off. */
    public static int steps(Rarity lowest) {
        return band(lowest).size() + 2;
    }

    /** Index 0 is everything, the last index is off, between them a tier. */
    public static int next(int index, int steps, boolean back) {
        int moved = back ? index - 1 : index + 1;
        if (moved < 0) return steps - 1;
        if (moved >= steps) return 0;
        return moved;
    }

    /** The lowest rarity this step still covers, or null when it is off. */
    public static Rarity floorAt(Rarity lowest, int index) {
        List<Rarity> band = band(lowest);
        if (index <= 0) return band.isEmpty() ? null : band.get(0);
        if (index > band.size()) return null;
        return band.get(index - 1);
    }

    /**
     * Whether this step still covers that rarity.
     *
     * Step 0 covers everything, including tiers below the ladder's own
     * floor. That matters for the animation ladder, whose floor is Epic
     * while the thing it gates is asked about every Common too: reading
     * step 0 as "Epic and up" would have hidden the reel on an ordinary
     * roll for every player who never touched the setting.
     */
    public static boolean covers(Rarity lowest, int index, Rarity rarity) {
        if (index <= 0) return true;
        List<Rarity> band = band(lowest);
        if (index > band.size()) return false;
        return rarity.ordinal() >= band.get(index - 1).ordinal();
    }

    /**
     * Which step a set of per-rarity switches is sitting on.
     *
     * The switches stay the storage, so nothing that reads them has to
     * change. A ladder can only express a contiguous run, so a mixed set
     * left over from the old sixteen-switch screen reads as the lowest
     * tier that is on and normalises itself the first time it is clicked.
     */
    public static int indexFromFlags(Rarity lowest, Predicate<Rarity> on) {
        List<Rarity> band = band(lowest);
        for (int i = 0; i < band.size(); i++) {
            if (on.test(band.get(i))) return i == 0 ? 0 : i + 1;
        }
        return band.size() + 1;
    }

    /** Writes a step back onto the per-rarity switches. */
    public static void applyToFlags(Rarity lowest, int index, BiConsumer<Rarity, Boolean> set) {
        for (Rarity rarity : band(lowest)) {
            set.accept(rarity, covers(lowest, index, rarity));
        }
    }

    /** The name of one step, coloured by the rarity it names. */
    public static String stepLabel(SolRNGPlugin plugin, Rarity lowest, int index, String everything) {
        List<Rarity> band = band(lowest);
        if (index <= 0) return everything;
        if (index > band.size()) return "Off";
        Rarity rarity = band.get(index - 1);
        return rarity.displayName() + " and up";
    }

    /**
     * The block itself.
     *
     * The ladder is listed in full because Leon asked for the step to be
     * visible ("en dit moet ook te zien zijn"), and a state tag alone
     * cannot say what the next click does. The current step is the only
     * coloured line, so the tooltip scans as one mark in a grey column.
     */
    public static ItemStack item(SolRNGPlugin plugin, Material icon, String label, Rarity lowest,
                                 int index, String everything, List<String> description,
                                 boolean bedrock) {
        List<String> steps = new ArrayList<>();
        for (int step = 0; step < steps(lowest); step++) {
            steps.add(stepLabel(plugin, lowest, step, everything));
        }
        return ladder(icon, label, index, steps, "Shown from", description, bedrock);
    }

    /**
     * The two lines under any ladder in the plugin (V358).
     *
     * Geyser sends every menu click as a left click, so a Bedrock player
     * has no step back, and a footnote telling them to right-click sends
     * them hunting for a button their client cannot press. Dantey reported
     * it as options he could not change. They are told the ladder wraps
     * instead, which is the way back they do have.
     */
    public static List<String> footer(boolean bedrock) {
        return List.of(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to step up",
                Lore.footnote(bedrock ? "It wraps round to the start." : "Right-click steps back."));
    }

    /**
     * The ladder as a breadcrumb over as few lines as it takes.
     *
     * One line per step is the clearest shape and it is what /options
     * uses, but /index's tier block already spends ten lines on what the
     * selected tier has collected and what finishing it pays, and a
     * tooltip past about twenty lines is clipped by the client at both
     * ends (MC-26757). So the steps sit side by side there instead, which
     * costs two lines whatever the ladder grows to.
     */
    public static List<String> breadcrumb(List<String> stepLabels, int index, int perLine) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int onThisLine = 0;
        for (int step = 0; step < stepLabels.size(); step++) {
            if (onThisLine > 0) line.append(ChatColor.DARK_GRAY).append(" · ");
            line.append(step == index
                    ? ChatColor.WHITE.toString() + ChatColor.BOLD + stepLabels.get(step)
                    : ChatColor.DARK_GRAY + stepLabels.get(step));
            if (++onThisLine >= perLine || step == stepLabels.size() - 1) {
                lines.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + line);
                line.setLength(0);
                onThisLine = 0;
            }
        }
        return lines;
    }

    /**
     * The renderer, for a ladder whose steps are not rarities too.
     *
     * /index steps through a tier FILTER rather than a floor, so its steps
     * read "Common" and not "Common and up", but it is the same block and
     * the same two clicks and it has to look the same.
     */
    public static ItemStack ladder(Material icon, String label, int index, List<String> stepLabels,
                                   String sectionHeader, List<String> description, boolean bedrock) {
        int bounded = Math.max(0, Math.min(index, stepLabels.size() - 1));
        String current = stepLabels.get(bounded);
        ChatColor colour = bounded == 0 ? ChatColor.GREEN
                : "Off".equals(current) ? ChatColor.RED
                : ChatColor.YELLOW;

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, label) + ChatColor.DARK_GRAY + " - "
                + colour + ChatColor.BOLD + current);

        List<String> lore = new ArrayList<>();
        if (!description.isEmpty()) {
            lore.add(Lore.section(ChatColor.AQUA, "What it controls"));
            for (String line : description) {
                lore.add(Lore.line(ChatColor.AQUA, line));
            }
            lore.add("");
        }
        lore.add(Lore.section(ChatColor.YELLOW, sectionHeader));
        for (int step = 0; step < stepLabels.size(); step++) {
            String name = stepLabels.get(step);
            if (step == bounded) {
                lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.WHITE + name
                        + ChatColor.GREEN + "  " + Lore.TICK);
            } else {
                lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + name);
            }
        }
        lore.add("");
        lore.addAll(footer(bedrock));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(bounded == 0 ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }
}
