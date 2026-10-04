package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.nova.NovaCoreManager;
import com.spacerng.solrng.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /novacore - the ladder drawn as a path you can actually trace.
 *
 * The tiers snake through the menu rather than filling rows left to right,
 * so the climb reads as a route with a start and an end instead of a
 * spreadsheet. Colour carries the state: green behind you, yellow ahead,
 * and a glinting yellow pane on the rung you're about to attempt.
 */
public class NovaCoreGui {

    /**
     * The route, in (column, row) order - 1-indexed, converted to slots as
     * (row-1)*9 + (column-1). Tier 1 is the first entry.
     */
    private static final int[] PATH_SLOTS = {
            37, 28, 19, 10,   // (2,5) (2,4) (2,3) (2,2)  - up the left side
            11, 12,           // (3,2) (4,2)              - across the top
            21, 30, 39,       // (4,3) (4,4) (4,5)        - back down
            40, 41,           // (5,5) (6,5)              - across the bottom
            32, 23, 14,       // (6,4) (6,3) (6,2)        - up again
            15, 16,           // (7,2) (8,2)              - across
            25, 34, 43,       // (8,3) (8,4) (8,5)        - down the right
            44                // (9,5)                    - the last rung
    };

    public static final int FORGE_SLOT = 49;
    private static final int INFO_SLOT = 45;
    // V331: cores are a balance now, so the menu has to say how many you
    // have, where they come from and what one is worth. Those are three
    // different questions and they get three blocks: the purse, the
    // ladder you are on, and the forge itself.
    private static final int PURSE_SLOT = 46;
    private static final int PROGRESS_SLOT = 53;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        NovaCoreHolder holder = new NovaCoreHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, MenuStyle.title("Nova Core", "#FF7AD9", "#C77DFF"));
        holder.setInventory(inv);

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NovaCoreManager nova = plugin.getNovaCoreManager();
        // V331: an old Nova Core item becomes balance the moment the
        // screen that spends it is opened.
        nova.absorb(player);
        int tier = data.getNovaTier();

        int rungs = Math.min(nova.getMaxTier(), PATH_SLOTS.length);
        for (int t = 1; t <= rungs; t++) {
            inv.setItem(PATH_SLOTS[t - 1], buildTier(nova, t, tier));
        }

        inv.setItem(INFO_SLOT, buildInfo(plugin, data, nova, tier));
        inv.setItem(PURSE_SLOT, buildPurse(plugin, player, data, nova));
        inv.setItem(PROGRESS_SLOT, buildProgress(plugin, data, nova, tier));
        inv.setItem(FORGE_SLOT, buildForge(plugin, player, data, nova, tier));
        MenuStyle.apply(inv, MenuStyle.Palette.PURPLE);
        return inv;
    }

    private static ItemStack buildTier(NovaCoreManager nova, int tier, int current) {
        boolean cleared = tier <= current;
        boolean next = tier == current + 1;
        boolean checkpoint = nova.isCheckpoint(tier);

        // Checkpoints keep their own icon at every state - they're the part
        // of the route worth planning around.
        Material material;
        if (checkpoint) {
            material = cleared ? Material.ENDER_EYE : Material.ENDER_PEARL;
        } else if (cleared) {
            material = Material.GREEN_STAINED_GLASS_PANE;
        } else {
            material = Material.YELLOW_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(cleared ? ChatColor.GREEN : next ? ChatColor.YELLOW : ChatColor.GRAY,
                "Tier " + tier + (checkpoint ? " " + Lore.SPARK : "")));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "Holding this tier"));
        // One line per thing it multiplies, each in that thing's own
        // colour. "1.50x Luck, Money and Coins" was one grey sentence
        // doing three jobs, and none of them stood out.
        String times = String.format("%.2f", nova.multiplierAt(tier)) + "x";
        lore.add(Lore.stat(ChatColor.GREEN, "Luck", times));
        lore.add(Lore.stat(Currency.MONEY.colour(), "Money", times));
        lore.add(Lore.stat(Currency.COINS.colour(), "Coins", times));
        if (checkpoint) {
            lore.add(Lore.line(ChatColor.AQUA, "Checkpoint - a shatter never"));
            lore.add(Lore.line(ChatColor.AQUA, "drops you below here."));
        }
        lore.add("");
        if (cleared) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Forged");
        } else if (next) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Next up");
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Locked");
        }

        meta.setLore(lore);
        // Only the rung you're attempting glints, so the eye lands on it.
        meta.setEnchantmentGlintOverride(next ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * What the Nova Core IS, in the order somebody new reads it: what it
     * does for them, what a forge costs, what it risks, and where the
     * checkpoints sit. The numbers for their own climb are on the "Your
     * climb" block; this one is the rules.
     */
    private static ItemStack buildInfo(SolRNGPlugin plugin, PlayerData data, NovaCoreManager nova, int tier) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(nova.styledName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A ladder you push your luck up.");
        lore.add("");
        lore.add(Lore.section(ChatColor.LIGHT_PURPLE, "How it works"));
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, ChatColor.GRAY + "One Nova Core per forge"));
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, ChatColor.GRAY + (nova.getFlatChance() > 0.0
                ? String.format("%.0f%%", nova.getFlatChance() * 100.0) + " to climb a tier, at every tier"
                : "Climb a tier, or fall back")));
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, ChatColor.GRAY + "A miss drops you to your checkpoint"));
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, ChatColor.GRAY + "Every tier held multiplies Luck,"));
        lore.add(Lore.pipe(ChatColor.LIGHT_PURPLE, ChatColor.GRAY + "Money and Coins at once"));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Checkpoints"));
        lore.add(Lore.pipe(ChatColor.AQUA, ChatColor.GRAY + "Tiers " + ChatColor.WHITE + nova.checkpointList()));
        lore.add(Lore.pipe(ChatColor.AQUA, ChatColor.GRAY + "A shatter never drops you below one"));
        lore.add("");
        lore.add(Lore.footnote("The Core Anchor skill can hold a miss."));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /** The Nova Core's own two colours, from its consumable entry. */
    private static final String[] CORE_STOPS = {"#7FE7FF", "#00B0FF"};

    private static ItemStack buildForge(SolRNGPlugin plugin, Player player, PlayerData data,
                                        NovaCoreManager nova, int tier) {
        boolean maxed = tier >= nova.getMaxTier();
        int held = nova.coresHeld(player);
        double luck = plugin.getPrestigeManager().baseLuck(data);
        double chance = nova.chanceAt(tier, luck);
        boolean affordable = held >= 1;

        // A compass, because this button is a gamble on a direction: it is
        // the one thing in the menu you press rather than read.
        ItemStack item = new ItemStack(maxed ? Material.NETHER_STAR : Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        // The Core's own colours, so the button and the thing it eats read
        // as the same object. A gradient belongs on a name and nowhere
        // else in a tooltip: see the menu-design skill.
        meta.setDisplayName(maxed
                ? Lore.gradient("Fully Forged", true, "#B9F6CA", "#00E676")
                : Lore.gradient("Forge Tier " + (tier + 1), true, CORE_STOPS));

        List<String> lore = new ArrayList<>();
        if (maxed) {
            lore.add(Lore.line(ChatColor.GREEN, "There's nothing left to climb."));
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Maxed");
        } else {
            ChatColor odds = chance >= 0.5 ? ChatColor.GREEN : chance >= 0.2 ? ChatColor.YELLOW : ChatColor.RED;
            lore.add(ChatColor.GRAY + "Every tier multiplies your Luck, Money");
            lore.add(ChatColor.GRAY + "and Coins. Miss, and you fall back to");
            lore.add(ChatColor.GRAY + "your last checkpoint.");
            lore.add("");
            lore.add(Lore.section(ChatColor.AQUA, "This attempt"));
            lore.add(Lore.stat(odds, "Success", chance >= 1.0
                    ? "certain" : String.format("%.1f%%", chance * 100.0)));
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Cost: " + (affordable ? ChatColor.WHITE : ChatColor.RED)
                    + "1 Nova Core" + ChatColor.DARK_GRAY + "  (you have " + held + ")");
            lore.add(Lore.stat(ChatColor.RED, "On fail", "back to tier " + nova.checkpointBelow(tier)));
            lore.add("");
            // V322: a flat chance is the same for everybody, so the two
            // lines that explained how Luck moved it would be telling a
            // story the forge no longer follows.
            if (nova.getFlatChance() > 0.0) {
                lore.add(Lore.stat(ChatColor.AQUA, "Every tier",
                        String.format("%.0f%%", nova.getFlatChance() * 100.0)));
                lore.add(ChatColor.DARK_GRAY + "The same coin flip at every tier.");
            } else {
                // Luck multiplies the tier's base chance: no Luck is the base,
                // +100% Luck doubles it, +200% triples it (V158).
                lore.add(Lore.stat(ChatColor.AQUA, "Base chance",
                        String.format("%.1f%%", nova.chanceAt(tier, 0.0) * 100.0)));
                // V285: said "Your Luck x16" next to a +2155% Luck elsewhere, which
                // read as a bug. It is the boost Luck gives the forge, and the
                // Luck behind it leaves out the Nova Core's own multiplier.
                lore.add(Lore.stat(ChatColor.GREEN, "Luck boost",
                        String.format("x%.2f", 1.0 + Math.max(0.0, luck) * nova.getLuckWeight())));
                lore.add(ChatColor.DARK_GRAY + "From +" + Math.round(Math.max(0.0, luck) * 100.0)
                        + "% Luck, without the Nova Core itself.");
            }
            lore.add("");
            if (affordable) {
                lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to forge");
            } else {
                lore.add(ChatColor.RED + "" + ChatColor.BOLD + "No Nova Cores");
                if (!nova.howToGet().isEmpty()) {
                    lore.add(ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Get them from " + nova.howToGet());
                }
            }
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(maxed ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The purse: how many Cores you hold, what one buys, and where the
     * next ones come from. A balance nobody can see is a balance nobody
     * spends, so this block exists the moment Cores stopped being an item
     * you could count in your hand.
     */
    private static ItemStack buildPurse(SolRNGPlugin plugin, Player player, PlayerData data,
                                        NovaCoreManager nova) {
        long held = data.getNovaCores();
        ItemStack item = new ItemStack(held > 0 ? Material.HEART_OF_THE_SEA : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(held > 0 ? ChatColor.AQUA : ChatColor.DARK_GRAY, "Your Nova Cores"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Kept here for you, not in your"));
        lore.add(Lore.line(ChatColor.GRAY, "inventory. One is spent per forge."));
        lore.add("");
        lore.add(Lore.stat(held > 0 ? ChatColor.AQUA : ChatColor.RED, "You hold",
                String.format("%,d", held)));
        lore.add(Lore.stat(ChatColor.AQUA, "Forges left", String.format("%,d", held)));
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Where they come from"));
        lore.add(Lore.pipe(ChatColor.AQUA, ChatColor.GRAY + "Crates, every one of them"));
        lore.add(Lore.pipe(ChatColor.AQUA, ChatColor.GRAY + "The Nova Finder hoe enchant"));
        lore.add(Lore.pipe(ChatColor.AQUA, ChatColor.GRAY + "/milestones and the /guide"));
        lore.add("");
        lore.add(Lore.footnote("Nothing else spends them."));
        meta.setLore(lore);
        if (held > 0) meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Where the climb stands: the tier, what it is paying, the checkpoint
     * under you and the one above, and the best you have ever reached.
     *
     * The ladder on the left of the screen says what each rung is worth.
     * This says what YOURS is worth, which is the question somebody opens
     * the menu with.
     */
    private static ItemStack buildProgress(SolRNGPlugin plugin, PlayerData data,
                                           NovaCoreManager nova, int tier) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your climb"));

        String times = String.format("%.2f", nova.multiplierAt(tier)) + "x";
        int below = nova.checkpointBelow(tier);
        int next = 0;
        for (int above = tier + 1; above <= nova.getMaxTier(); above++) {
            if (nova.isCheckpoint(above)) {
                next = above;
                break;
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add(Lore.stat(ChatColor.LIGHT_PURPLE, "Tier", tier + " / " + nova.getMaxTier()));
        lore.add(Lore.bar(nova.getMaxTier() <= 0 ? 0.0 : (double) tier / nova.getMaxTier()));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "What it pays you"));
        lore.add(Lore.stat(ChatColor.GREEN, "Luck", times));
        lore.add(Lore.stat(Currency.MONEY.colour(), "Money", times));
        lore.add(Lore.stat(Currency.COINS.colour(), "Coins", times));
        lore.add("");
        lore.add(Lore.stat(ChatColor.GREEN, "Safety net",
                below <= 0 ? "none yet, a miss drops you to 0" : "tier " + below));
        lore.add(Lore.stat(ChatColor.AQUA, "Next checkpoint",
                next <= 0 ? "none left" : "tier " + next));
        lore.add(Lore.stat(ChatColor.YELLOW, "Best ever", String.valueOf(data.getNovaBestTier())));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(name);
        pane.setItemMeta(meta);
        return pane;
    }
}
