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

    public DustManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        announce = config.getBoolean("pets.dust.announce", true);
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
        data.addCosmicDust(1L);
        found(player, Currency.COSMIC_DUST, data.getCosmicDust());
        return 1L;
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
        found(player, Currency.FARM_DUST, data.getFarmDust());
        return fell;
    }

    /**
     * The line a player sees when dust falls. Quiet on purpose: dust is
     * rare but rolling is fast, and a title every time would be in the
     * way of the roll it interrupted.
     */
    private void found(Player player, Currency currency, long held) {
        if (!announce || player == null || !player.isOnline()) return;
        String text = ChatColor.GRAY + currency.mark() + " " + ChatColor.GRAY + "You found "
                + currency.amount(1L) + ChatColor.GRAY + ". You hold " + currency.amount(held)
                + ChatColor.GRAY + ".";
        Component line = LegacyComponentSerializer.legacySection().deserialize(text);
        player.sendActionBar(line);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f);
    }
}
