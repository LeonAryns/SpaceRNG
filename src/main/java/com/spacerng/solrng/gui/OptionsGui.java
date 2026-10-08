package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
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
 * /options.
 *
 * V310: the three per-rarity groups became three ladders. The screen used
 * to carry sixteen switches, four reveal auras, four announcements and
 * seven drop messages, and Leon's note was that it read as clutter. Each
 * group asked one question with a tier for an answer, so each is one
 * {@link Stepper} block now: left click raises the floor, right click
 * lowers it, and the tooltip prints the whole ladder.
 *
 * The per-rarity switches in PlayerData are still the storage, so nothing
 * that reads them changed. The ladder only ever writes a contiguous run.
 */
public class OptionsGui {

    /** The lowest tier a reveal aura or an announcement can be pinned to. */
    private static final Rarity SHOW_FLOOR = Rarity.EPIC;
    /** Your own drop lines go all the way down: Common is the noisy one. */
    private static final Rarity DROP_FLOOR = Rarity.COMMON;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        OptionsHolder holder = new OptionsHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, MenuStyle.title("Options", "#80D8FF", "#536DFE"));
        holder.setInventory(inv);

        ItemStack filler = pane();
        for (int slot = 0; slot < 36; slot++) {
            inv.setItem(slot, filler);
        }

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        // V358: Bedrock has no right click in a menu, so every ladder here
        // says so in its own footnote rather than promising a step back.
        boolean bedrock = com.spacerng.solrng.platform.Bedrock.is(player);

        inv.setItem(OptionsHolder.SOUND_SLOT, toggleItem(Material.NOTE_BLOCK,
                "Rolling Sound", data.isRollSoundEnabled(),
                "The click track while a roll counts down."));
        inv.setItem(OptionsHolder.ANIMATION_SLOT, Stepper.item(plugin, Material.ITEM_FRAME,
                "Rolling Animation", PlayerData.ANIMATION_FLOOR, data.getRollAnimationStep(),
                "Every roll",
                List.of("The names flashing on screen and the",
                        "reveal that follows. Below your step a",
                        "drop lands at the speed of any roll."), bedrock));
        inv.setItem(OptionsHolder.WORN_AURA_SLOT, toggleItem(Material.AMETHYST_CLUSTER,
                "Worn Auras", data.isWornAurasVisible(),
                "The auras players wear with an", "Epic or rarer tag, yours too."));
        inv.setItem(OptionsHolder.OWN_AURA_SLOT, ownAuraItem(data.getOwnAuraView()));

        inv.setItem(OptionsHolder.AURA_STEP_SLOT, Stepper.item(plugin, Material.FIREWORK_ROCKET,
                "Reveal Auras", SHOW_FLOOR, auraStep(data), "Every tier",
                List.of("The build-up and burst for a drop,",
                        "yours and everyone else's."), bedrock));
        inv.setItem(OptionsHolder.SHOUT_STEP_SLOT, Stepper.item(plugin, Material.BELL,
                "Announcements", SHOW_FLOOR, shoutStep(data), "Every tier",
                List.of("Other players' drops announced in",
                        "your chat. Yours are always shown."), bedrock));
        inv.setItem(OptionsHolder.DROP_STEP_SLOT, Stepper.item(plugin, Material.PAPER,
                "Your Drop Messages", DROP_FLOOR, dropStep(data), "Every tier",
                List.of("Your own drops printed in chat. The",
                        "drop is still yours either way."), bedrock));

        MenuStyle.apply(inv, MenuStyle.Palette.BLUE);

        return inv;
    }

    /** Which step each ladder is sitting on, read back off the switches. */
    public static int auraStep(PlayerData data) {
        return Stepper.indexFromFlags(SHOW_FLOOR, data::isAuraEnabled);
    }

    public static int shoutStep(PlayerData data) {
        return Stepper.indexFromFlags(SHOW_FLOOR, data::isBroadcastEnabled);
    }

    public static int dropStep(PlayerData data) {
        return Stepper.indexFromFlags(DROP_FLOOR, data::isDropMessageEnabled);
    }

    public static void setAuraStep(PlayerData data, int step) {
        Stepper.applyToFlags(SHOW_FLOOR, step, data::setAuraEnabled);
    }

    public static void setShoutStep(PlayerData data, int step) {
        Stepper.applyToFlags(SHOW_FLOOR, step, data::setBroadcastEnabled);
    }

    public static void setDropStep(PlayerData data, int step) {
        Stepper.applyToFlags(DROP_FLOOR, step, data::setDropMessageEnabled);
    }

    public static int auraSteps() {
        return Stepper.steps(SHOW_FLOOR);
    }

    public static int dropSteps() {
        return Stepper.steps(DROP_FLOOR);
    }

    /**
     * How you see your own worn aura. Out of your way is the default: the
     * floor, the sky, the back and anything far enough out that you look
     * through it stay, and only what would sit on your nose in first person
     * goes. Everyone else sees all of it whichever you pick.
     */
    private static ItemStack ownAuraItem(String mode) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        String state = switch (mode) {
            case "full" -> ChatColor.GREEN.toString() + ChatColor.BOLD + "Everything";
            case "hidden" -> ChatColor.RED.toString() + ChatColor.BOLD + "Hidden";
            default -> ChatColor.YELLOW.toString() + ChatColor.BOLD + "Out of your way";
        };
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, "Your Own Aura") + ChatColor.DARK_GRAY + " - " + state);
        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What you see of yours"));
        lore.add(Lore.line(ChatColor.AQUA, "Out of your way: ground and sky,"));
        lore.add(Lore.line(ChatColor.AQUA, "nothing in front of your eyes."));
        lore.add(Lore.line(ChatColor.AQUA, "Everything: all of it, orbits too."));
        lore.add(Lore.line(ChatColor.AQUA, "Hidden: none of it."));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Others always see your whole aura.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to change");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack toggleItem(Material material, String label, boolean on, String... description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.YELLOW, label) + ChatColor.DARK_GRAY + " - "
                + (on ? ChatColor.GREEN.toString() + ChatColor.BOLD + "On"
                      : ChatColor.RED.toString() + ChatColor.BOLD + "Off"));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.section(ChatColor.AQUA, "What it controls"));
        for (String line : description) {
            lore.add(Lore.line(ChatColor.AQUA, line));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to toggle");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(on ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
