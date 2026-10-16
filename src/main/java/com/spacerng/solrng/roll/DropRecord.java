package com.spacerng.solrng.roll;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * The rarest drop the server has found so far, plain and shiny apart
 * (V404, Leon). A drop goes to chat only when it matches or beats that
 * record, so once somebody finds a Divine only Divines (and better) are
 * announced. Kept in drop-record.yml; the first load starts from the
 * Server First lists, so the record reflects what was already found.
 */
public class DropRecord {

    private final SolRNGPlugin plugin;
    private final File file;
    private Rarity best;
    private Rarity bestShiny;

    public DropRecord(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "drop-record.yml");
        load();
    }

    /**
     * True when this drop matches or beats the record for its kind, and
     * raises the record when it beats it.
     */
    public boolean worthAnnouncing(RollableItem item, boolean shiny) {
        Rarity rarity = item.getRarity();
        Rarity record = shiny ? bestShiny : best;
        if (record != null && rarity.ordinal() < record.ordinal()) return false;
        if (record == null || rarity.ordinal() > record.ordinal()) {
            if (shiny) bestShiny = rarity;
            else best = rarity;
            save();
        }
        return true;
    }

    public Rarity best() {
        return best;
    }

    public Rarity bestShiny() {
        return bestShiny;
    }

    /** For an admin: forget the record so it builds up again. */
    public void reset() {
        best = null;
        bestShiny = null;
        save();
    }

    private void load() {
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            best = parse(yml.getString("best"));
            bestShiny = parse(yml.getString("best-shiny"));
            return;
        }
        seed();
        save();
    }

    /** The highest rarity in the Server First lists, plain and shiny. */
    private void seed() {
        var firsts = plugin.getFirstTenManager();
        if (firsts == null) return;
        for (Rarity rarity : firsts.trackedRarities()) {
            if (!firsts.entries(rarity).isEmpty() && (best == null || rarity.ordinal() > best.ordinal())) {
                best = rarity;
            }
        }
        for (var entry : firsts.shinyEntries()) {
            var item = plugin.getRarityManager().findByDisplayName(entry.item());
            if (item == null) continue;
            if (bestShiny == null || item.getRarity().ordinal() > bestShiny.ordinal()) bestShiny = item.getRarity();
        }
    }

    private static Rarity parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return Rarity.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("best", best == null ? null : best.name());
        yml.set("best-shiny", bestShiny == null ? null : bestShiny.name());
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save drop-record.yml: " + ex.getMessage());
        }
    }
}
