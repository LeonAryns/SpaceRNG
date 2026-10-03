package com.spacerng.solrng.pet;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Where the two dusts come from.
 *
 * Cosmic Dust falls while rolling, Farm Dust while harvesting, and
 * neither falls at all until its tree has been paid for. That is the
 * whole gate: the node is the unlock and its levels are the chance, so a
 * player who has not bought in sees no dust and is told where to buy it
 * rather than wondering why nothing drops.
 *
 * The two chances are deliberately not the same number. A roll is a
 * deliberate act a player does a few hundred times an evening; a crop is
 * something a good farmer takes eight thousand of inside a boss window.
 * Put both at one in a hundred and the farm axis is finished in a day
 * while the rolling axis never moves, so Farm Dust is far rarer per
 * event and the two still land in the same place per hour.
 */
public class DustManager {

    private final SolRNGPlugin plugin;

    private boolean announce = true;
    // V314: how much one Cosmic Dust find is worth. A range rather than a
    // flat one, Leon's call, with the Cosmic Yield skill adding to the top
    // so levels buy the size of a find as well as how often one lands.
    private long cosmicMin = 1L;
    private long cosmicMax = 2L;

    public DustManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        announce = config.getBoolean("pets.dust.announce", true);
        cosmicMin = Math.max(1L, config.getLong("pets.dust.cosmic-min", 1L));
        cosmicMax = Math.max(cosmicMin, config.getLong("pets.dust.cosmic-max", 2L));
    }

    /** The top of this player's Cosmic Dust range, skill included. */
    public long cosmicTop(PlayerData data) {
        double extra = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.COSMIC_DUST_AMOUNT);
        return Math.max(cosmicMin, cosmicMax + Math.max(0L, Math.round(extra)));
    }

    public long cosmicFloor() {
        return cosmicMin;
    }

    /**
     * How much one find is worth, rolled fresh each time.
     *
     * Uniform between the floor and the top, so a level bought is felt
     * on every find rather than only on the lucky ones, and the find
     * still reads as a find rather than as a fixed wage.
     */
    private long cosmicAmount(PlayerData data) {
        long top = cosmicTop(data);
        if (top <= cosmicMin) return cosmicMin;
        return cosmicMin + ThreadLocalRandom.current().nextLong(top - cosmicMin + 1L);
    }

    /** The chance of one Cosmic Dust on a roll, 0 when the skill is unbought. */
    public double cosmicChance(PlayerData data) {
        double total = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.COSMIC_DUST_CHANCE);
        return Math.min(1.0, total > 0.0 ? total : 0.0);
    }

    /** The chance of one Farm Dust on a harvested crop, 0 when unbought. */
    public double farmChance(PlayerData data) {
        double total = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.FARM_DUST_CHANCE);
        return Math.min(1.0, total > 0.0 ? total : 0.0);
    }

    /**
     * Rolls for Cosmic Dust after a roll lands. Returns how much fell, so
     * the caller can fold it into whatever else it is already saying.
     */
    public long onRoll(Player player, PlayerData data) {
        if (!plugin.getPetManager().isEnabled()) return 0L;
        double chance = cosmicChance(data);
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble() >= chance) return 0L;
        long fell = cosmicAmount(data);
        data.addCosmicDust(fell);
        found(player, Currency.COSMIC_DUST, fell, data.getCosmicDust());
        return fell;
    }

    /**
     * Rolls for Farm Dust over a number of crops at once. Harvests arrive
     * one crop at a time normally, but a nuke enchant hands over hundreds
     * in a single call and rolling once for the lot would quietly pay a
     * fraction of what it should.
     */
    public long onHarvest(Player player, PlayerData data, long crops) {
        if (!plugin.getPetManager().isEnabled() || crops <= 0L) return 0L;
        double chance = farmChance(data);
        if (chance <= 0.0) return 0L;

        long fell = 0L;
        if (crops <= 64L) {
            for (long i = 0; i < crops; i++) {
                if (ThreadLocalRandom.current().nextDouble() < chance) fell++;
            }
        } else {
            // A nuke can be thousands of crops. Looping that per block break
            // is work on the main thread for no extra fairness, so past a
            // point the expected number is paid directly and only the
            // remainder is actually rolled.
            long guaranteed = (long) (crops * chance);
            double remainder = crops * chance - guaranteed;
            fell = guaranteed + (ThreadLocalRandom.current().nextDouble() < remainder ? 1L : 0L);
        }
        if (fell <= 0L) return 0L;

        data.addFarmDust(fell);
        found(player, Currency.FARM_DUST, fell, data.getFarmDust());
        return fell;
    }

    /**
     * A golden crop is a Farm Dust find of its own (V160): on top of the
     * normal per-crop roll it has farming.golden-crop.farm-dust-chance of
     * dropping one. It needs Farm Dust unlocked in /farmtree like every
     * other source, so it speeds the grind up without skipping the tree.
     */
    public long onGoldenCrop(Player player, PlayerData data) {
        if (!plugin.getPetManager().isEnabled() || farmChance(data) <= 0.0) return 0L;
        double chance = plugin.getConfig().getDouble("farming.golden-crop.farm-dust-chance", 0.05);
        if (chance <= 0.0 || ThreadLocalRandom.current().nextDouble() >= chance) return 0L;
        data.addFarmDust(1L);
        found(player, Currency.FARM_DUST, 1L, data.getFarmDust());
        return 1L;
    }

    /**
     * The line a player sees when dust falls. Quiet on purpose: dust is
     * rare but rolling is fast, and a title every time would be in the
     * way of the roll it interrupted.
     */
    private void found(Player player, Currency currency, long fell, long held) {
        if (!announce || player == null || !player.isOnline()) return;
        String text = ChatColor.GRAY + currency.mark() + " " + ChatColor.GRAY + "You found "
                + currency.amount(fell) + ChatColor.GRAY + ". You hold " + currency.amount(held)
                + ChatColor.GRAY + ".";
        Component line = LegacyComponentSerializer.legacySection().deserialize(text);
        // V314: claims the bar for three seconds. The level-up hint fires
        // every three on its own timer and used to paint straight over
        // this, which under Auto Roll meant the line was gone before it
        // could be read. See gui/ActionBar.
        com.spacerng.solrng.gui.ActionBar.send(player, line, 3000L);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f);
    }
}
