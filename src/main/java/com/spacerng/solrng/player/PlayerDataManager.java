package com.spacerng.solrng.player;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerDataManager {

    private final SolRNGPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public PlayerDataManager(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    /**
     * Wipes a player back to a brand-new account: the cached object is
     * replaced and the save file deleted, so nothing can flush the old
     * state back over the top afterwards.
     */
    public PlayerData reset(UUID uuid) {
        cache.remove(uuid);
        File file = fileFor(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Couldn't delete player data file for " + uuid);
        }
        PlayerData fresh = new PlayerData(uuid);
        cache.put(uuid, fresh);
        return fresh;
    }

    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null) {
            save(data);
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            save(data);
        }
    }

    private File fileFor(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }

    private PlayerData load(UUID uuid) {
        File file = fileFor(uuid);
        PlayerData data = new PlayerData(uuid);
        if (!file.exists()) {
            return data;
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        // Luck, Speed and Double Roll used to be added into these fields at
        // purchase time; they're derived from node levels now. A save from
        // before that change would double-count, so the first load after the
        // upgrade drops the baked-in copies. The node levels are untouched,
        // so nothing bought is actually lost - it just gets recomputed.
        boolean derived = yml.getBoolean("derived-skills", false);
        if (derived) {
            data.addBonusLuck(yml.getDouble("luck", 0.0));
            data.setRollSpeedMultiplier(yml.getDouble("roll-speed-multiplier", 1.0));
            data.addBonusRollChance(yml.getDouble("bonus-roll-chance", 0.0));
        }
        data.addPoints(yml.getLong("points", 0L));
        data.addTokens(yml.getLong("tokens", 0L));
        data.addShards(yml.getLong("shards", 0L));
        data.setAutoRollEnabled(yml.getBoolean("auto-roll-enabled", false));
        data.addConverted(Rarity.COMMON, yml.getLong("converted-common", 0L));
        data.addConverted(Rarity.UNCOMMON, yml.getLong("converted-uncommon", 0L));
        data.setTotalRolls(yml.getLong("total-rolls", 0L));
        // A file from before V162 has no count yet: without a prestige every
        // roll so far is this prestige's, otherwise it starts from zero.
        data.setRollsThisPrestige(yml.contains("rolls-this-prestige")
                ? yml.getLong("rolls-this-prestige", 0L)
                : yml.getInt("prestige", 0) == 0 ? yml.getLong("total-rolls", 0L) : 0L);
        data.setLevel(yml.getInt("level", 1));
        data.setPrestige(yml.getInt("prestige", 0));
        data.setRollSoundEnabled(yml.getBoolean("roll-sound-enabled", true));
        data.setRollAnimationEnabled(yml.getBoolean("roll-animation-enabled", true));
        data.setWornAurasVisible(yml.getBoolean("worn-auras-visible", true));
        data.setOwnAuraView(yml.getString("own-aura-view", "ground"));
        data.setFarmSoundEnabled(yml.getBoolean("farm-sound-enabled", true));
        data.setFarmHidePlayers(yml.getBoolean("farm-hide-players", false));
        data.setEnchantSoundEnabled(yml.getBoolean("enchant-sound-enabled", true));
        data.setRollCharges(yml.getLong("roll-charges", 0L), yml.getDouble("roll-charge-multiplier", 1.0));
        data.addBonusSpeed(yml.getDouble("bonus-speed", 0.0));
        data.setPotion(yml.getDouble("potion-luck", 0.0), yml.getDouble("potion-speed", 0.0),
                yml.getLong("potion-rolls", 0L));
        org.bukkit.configuration.ConfigurationSection boosts = yml.getConfigurationSection("boosts");
        if (boosts != null) {
            for (String effect : boosts.getKeys(false)) {
                data.setBoost(effect,
                        boosts.getDouble(effect + ".multiplier", 1.0),
                        boosts.getLong(effect + ".expires", 0L));
            }
        }
        data.setAutoConvertShiny(yml.getBoolean("auto-convert-shiny", false));
        data.setHoeTier(yml.getInt("hoe-tier", 0));
        data.addFreeSkills(yml.getInt("free-skills", 0));
        data.setAbilityReadyAt(yml.getLong("ability-ready-at", 0L));
        data.getDiscoveredShiny().addAll(yml.getStringList("discovered-shiny"));
        for (String rarityName : yml.getStringList("disabled-auras")) {
            try {
                data.setAuraEnabled(Rarity.valueOf(rarityName), false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String rarityName : yml.getStringList("muted-drops")) {
            try {
                data.setDropMessageEnabled(Rarity.valueOf(rarityName), false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String rarityName : yml.getStringList("muted-broadcasts")) {
            try {
                data.setBroadcastEnabled(Rarity.valueOf(rarityName), false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        org.bukkit.configuration.ConfigurationSection shiny = yml.getConfigurationSection("shiny-bank");
        if (shiny != null) {
            for (String rarityName : shiny.getKeys(false)) {
                try {
                    data.addBankedShiny(Rarity.valueOf(rarityName), shiny.getLong(rarityName));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        data.setFarmTokenMultiplier(yml.getDouble("farm-token-multiplier", 1.0));
        data.setStarforgeTier(yml.getString("starforge-tier", "BASIC"));
        data.setCropsHarvested(yml.getLong("crops-harvested", 0L));
        data.setCropsThisPeriod(yml.getLong("crops-this-period", 0L));
        data.setNovaTier(yml.getInt("nova-tier", 0));
        data.setLuckLimitPercent(yml.getInt("luck-limit-percent", 100));
        data.setNovaBestTier(yml.getInt("nova-best-tier", 0));
        data.setSelectedCrop(yml.getString("selected-crop", "WHEAT"));
        data.setCropShardsUnlocked(yml.getBoolean("crop-shards-unlocked", false));
        data.getUnlockedCrops().addAll(yml.getStringList("unlocked-crops"));
        org.bukkit.configuration.ConfigurationSection hoeLevels = yml.getConfigurationSection("hoe-enchants");
        if (hoeLevels != null) {
            for (String id : hoeLevels.getKeys(false)) {
                data.setHoeEnchantLevel(id, hoeLevels.getInt(id));
            }
        }
        data.getClaimedMilestones().addAll(yml.getStringList("claimed-milestones"));
        data.getAnnouncedMilestones().addAll(yml.getStringList("announced-milestones"));
        data.getCompletedQuests().addAll(yml.getStringList("completed-quests"));
        data.setDailyStreak(yml.getInt("daily-streak", 0));
        data.setDailyLastClaimDay(yml.getLong("daily-last-claim-day", 0L));
        data.setDailyTotalClaims(yml.getLong("daily-total-claims", 0L));
        data.setPassSeason(yml.getInt("pass-season", 1));
        data.setPassXp(yml.getLong("pass-xp", 0L));
        data.setPassPremium(yml.getBoolean("pass-premium", false));
        data.getPassClaimed().addAll(yml.getStringList("pass-claimed"));
        org.bukkit.configuration.ConfigurationSection vaults = yml.getConfigurationSection("vaults");
        if (vaults != null) {
            for (String page : vaults.getKeys(false)) {
                try {
                    java.util.List<org.bukkit.inventory.ItemStack> items = new java.util.ArrayList<>();
                    for (Object raw : vaults.getList(page, java.util.List.of())) {
                        items.add(raw instanceof org.bukkit.inventory.ItemStack stack ? stack : null);
                    }
                    data.getVaults().put(Integer.parseInt(page), items);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (Object raw : yml.getList("stash", java.util.List.of())) {
            if (raw instanceof org.bukkit.inventory.ItemStack stack) data.getStash().add(stack);
        }
        data.setPrestigePoints(yml.getInt("prestige-points", 0));
        org.bukkit.configuration.ConfigurationSection upgrades = yml.getConfigurationSection("prestige-upgrades");
        if (upgrades != null) {
            for (String id : upgrades.getKeys(false)) {
                data.setUpgradeLevel(id, upgrades.getInt(id));
            }
        }

        org.bukkit.configuration.ConfigurationSection bank = yml.getConfigurationSection("drop-bank");
        if (bank != null) {
            for (String rarityName : bank.getKeys(false)) {
                try {
                    data.addBankedDrops(Rarity.valueOf(rarityName), bank.getLong(rarityName));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (String node : yml.getStringList("unlocked-nodes")) {
            data.getUnlockedNodes().add(node);
        }
        org.bukkit.configuration.ConfigurationSection nodeLevels = yml.getConfigurationSection("node-levels");
        if (nodeLevels != null) {
            for (String nodeId : nodeLevels.getKeys(false)) {
                data.setNodeLevel(nodeId, nodeLevels.getInt(nodeId));
            }
        }
        for (String itemName : yml.getStringList("discovered-items")) {
            data.getDiscoveredItems().add(itemName);
        }
        for (String rarityName : yml.getStringList("auto-convert-rarities")) {
            try {
                data.getAutoConvertRarities().add(Rarity.valueOf(rarityName));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String entry : yml.getStringList("purchased-armor-tiers")) {
            if (entry.contains(":")) {
                data.getPurchasedArmorTiers().add(entry);
                continue;
            }
            // Pre-V30 saves stored a bare tier id meaning "bought the whole
            // set" - anyone who owned a set keeps all four pieces.
            for (ArmorPiece piece : ArmorPiece.values()) {
                data.getPurchasedArmorTiers().add(ArmorPiece.key(entry, piece));
            }
        }

        String tagItem = yml.getString("tag-item", null);
        String tagRarity = yml.getString("tag-rarity", null);
        if (tagItem != null) {
            data.setEquippedTag(tagItem, tagRarity);
        }

        data.setRespecCount(yml.getInt("respec-count", 0));
        data.setClaimedLinkGift(yml.getBoolean("claimed-link-gift", false));
        String activePerk = yml.getString("perk-active", "");
        if (activePerk.contains(":")) {
            try {
                data.setActivePerk(activePerk.substring(0, activePerk.indexOf(':')),
                        Integer.parseInt(activePerk.substring(activePerk.indexOf(':') + 1)));
            } catch (NumberFormatException ignored) { }
        }
        data.setPerkTickets(yml.getLong("perk-tickets", 0L));
        data.setPerkPity(yml.getInt("perk-pity", 0));
        data.setRank(yml.getString("rank"));
        data.setAuraChoice(yml.getString("aura-choice"));
        // "pets-owned" used to be a plain list of type ids. PetInstance.parse
        // reads both that and the "id:rarity:tier:shiny" form, so a save file
        // written before pets had rarity loads as Rarity 1 Tier 1 instead of
        // being thrown away.
        for (String raw : yml.getStringList("pets-owned")) {
            com.spacerng.solrng.pet.PetInstance pet = com.spacerng.solrng.pet.PetInstance.parse(raw);
            if (pet != null) data.putPet(pet);
        }
        data.getEquippedPets().addAll(yml.getStringList("pets-equipped"));
        data.setCosmicDust(yml.getLong("cosmic-dust", 0L));
        data.setFarmDust(yml.getLong("farm-dust", 0L));
        data.setKeyallAt(yml.getLong("keyall-at", 0L));
        data.setNick(yml.getString("nick"));
        data.getCosmeticTags().addAll(yml.getStringList("cosmetic-tags"));
        data.setWornCosmeticTag(yml.getString("cosmetic-tag"));
        data.setNameColour(yml.getString("name-colour"));
        data.setPlayerSize(yml.getDouble("player-size", 1.0));
        data.getPerkConfirm().addAll(yml.getStringList("perk-confirm"));
        data.getPerkFound().addAll(yml.getStringList("perk-found"));
        // Before V136 perks sat in a vault. Converted once: the best one becomes the
        // player's perk and unused save rolls come back as Credits.
        if (!yml.contains("perk-active")) {
            plugin.getPerkManager().migrateOldVault(data, yml.getStringList("perk-vault"),
                    yml.getLong("perk-save-rolls", 0L));
        }

        return data;
    }

    /**
     * Pays an offline player by editing their save file directly. A farming
     * payout lands at a fixed hour whether or not the winner is connected,
     * and "you only get paid if you happened to be online" is not a rule
     * anybody would accept.
     */
    public void awardOffline(UUID uuid, long credits) {
        awardOffline(uuid, credits, "points", data -> data.addPoints(credits));
    }

    /**
     * A cached player is paid on the object, because the file underneath
     * them is about to be overwritten by their own save and would throw
     * the edit away.
     */
    private void awardOffline(UUID uuid, long amount, String key, Consumer<PlayerData> live) {
        if (amount <= 0) return;

        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            live.accept(cached);
            return;
        }

        File file = fileFor(uuid);
        YamlConfiguration yml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        yml.set(key, yml.getLong(key, 0L) + amount);
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't pay offline player " + uuid + ": " + e.getMessage());
        }
    }

    /** Zeroes the period counter in every save file that isn't loaded. */
    public void clearOfflinePeriods() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String raw = file.getName().substring(0, file.getName().length() - 4);
            try {
                if (cache.containsKey(UUID.fromString(raw))) continue; // handled live
            } catch (IllegalArgumentException ex) {
                continue;
            }

            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            if (yml.getLong("crops-this-period", 0L) == 0L) continue;
            yml.set("crops-this-period", 0L);
            try {
                yml.save(file);
            } catch (IOException ignored) {
            }
        }
    }

    public void save(PlayerData data) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("luck", data.getFlatLuck());
        yml.set("bonus-speed", data.getBonusSpeed());
        yml.set("derived-skills", true);
        yml.set("points", data.getPoints());
        yml.set("tokens", data.getTokens());
        yml.set("hoe-tier", data.getHoeTier());
        yml.set("free-skills", data.getFreeSkills());
        yml.set("ability-ready-at", data.getAbilityReadyAt());
        yml.set("shards", data.getShards());
        yml.set("roll-speed-multiplier", data.getRollSpeedMultiplier());
        yml.set("auto-roll-enabled", data.isAutoRollEnabled());
        yml.set("bonus-roll-chance", data.getBonusRollChance());
        yml.set("converted-common", data.getConvertedCommon());
        yml.set("converted-uncommon", data.getConvertedUncommon());
        yml.set("total-rolls", data.getTotalRolls());
        yml.set("rolls-this-prestige", data.getRollsThisPrestige());
        yml.set("level", data.getLevel());
        yml.set("prestige", data.getPrestige());
        yml.set("roll-sound-enabled", data.isRollSoundEnabled());
        yml.set("roll-animation-enabled", data.isRollAnimationEnabled());
        yml.set("worn-auras-visible", data.isWornAurasVisible());
        yml.set("own-aura-view", data.getOwnAuraView());
        yml.set("farm-sound-enabled", data.isFarmSoundEnabled());
        yml.set("farm-hide-players", data.isFarmHidePlayers());
        yml.set("enchant-sound-enabled", data.isEnchantSoundEnabled());
        yml.set("roll-charges", data.getRollCharges());
        yml.set("potion-luck", data.getPotionLuck());
        yml.set("potion-speed", data.getPotionSpeed());
        yml.set("potion-rolls", data.getPotionRolls());
        yml.set("roll-charge-multiplier", data.getRollChargeMultiplier());
        for (Map.Entry<String, double[]> entry : data.getBoosts().entrySet()) {
            yml.set("boosts." + entry.getKey() + ".multiplier", entry.getValue()[0]);
            yml.set("boosts." + entry.getKey() + ".expires", (long) entry.getValue()[1]);
        }
        yml.set("auto-convert-shiny", data.isAutoConvertShiny());
        yml.set("discovered-shiny", new java.util.ArrayList<>(data.getDiscoveredShiny()));
        java.util.List<String> disabledAuras = new java.util.ArrayList<>();
        for (Rarity r : data.getDisabledAuras()) disabledAuras.add(r.name());
        yml.set("disabled-auras", disabledAuras);
        yml.set("muted-broadcasts", data.getMutedBroadcasts().stream().map(Enum::name).toList());
        yml.set("muted-drops", data.getMutedDrops().stream().map(Enum::name).toList());
        for (Map.Entry<Rarity, Long> entry : data.getShinyBank().entrySet()) {
            yml.set("shiny-bank." + entry.getKey().name(), entry.getValue());
        }
        yml.set("farm-token-multiplier", data.getFarmTokenMultiplier());
        yml.set("starforge-tier", data.getStarforgeTier());
        yml.set("crops-harvested", data.getCropsHarvested());
        yml.set("crops-this-period", data.getCropsThisPeriod());
        yml.set("nova-tier", data.getNovaTier());
        yml.set("luck-limit-percent", data.getLuckLimitPercent());
        yml.set("nova-best-tier", data.getNovaBestTier());
        yml.set("selected-crop", data.getSelectedCrop());
        yml.set("crop-shards-unlocked", data.isCropShardsUnlocked());
        yml.set("unlocked-crops", new java.util.ArrayList<>(data.getUnlockedCrops()));
        for (Map.Entry<String, Integer> entry : data.getHoeEnchantLevels().entrySet()) {
            yml.set("hoe-enchants." + entry.getKey(), entry.getValue());
        }
        yml.set("claimed-milestones", new java.util.ArrayList<>(data.getClaimedMilestones()));
        yml.set("announced-milestones", new java.util.ArrayList<>(data.getAnnouncedMilestones()));
        yml.set("completed-quests", new java.util.ArrayList<>(data.getCompletedQuests()));
        yml.set("daily-streak", data.getDailyStreak());
        yml.set("daily-last-claim-day", data.getDailyLastClaimDay());
        yml.set("daily-total-claims", data.getDailyTotalClaims());
        yml.set("pass-season", data.getPassSeason());
        yml.set("pass-xp", data.getPassXp());
        yml.set("pass-premium", data.isPassPremium());
        yml.set("pass-claimed", new java.util.ArrayList<>(data.getPassClaimed()));
        yml.set("prestige-points", data.getPrestigePoints());
        for (Map.Entry<String, Integer> entry : data.getPrestigeUpgrades().entrySet()) {
            yml.set("prestige-upgrades." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Rarity, Long> entry : data.getDropBank().entrySet()) {
            yml.set("drop-bank." + entry.getKey().name(), entry.getValue());
        }
        yml.set("unlocked-nodes", new java.util.ArrayList<>(data.getUnlockedNodes()));
        for (Map.Entry<String, Integer> entry : data.getNodeLevels().entrySet()) {
            yml.set("node-levels." + entry.getKey(), entry.getValue());
        }
        yml.set("discovered-items", new java.util.ArrayList<>(data.getDiscoveredItems()));
        yml.set("stash", new java.util.ArrayList<>(data.getStash()));
        yml.set("vaults", null);
        for (var entry : data.getVaults().entrySet()) {
            yml.set("vaults." + entry.getKey(), entry.getValue());
        }

        java.util.List<String> rarityNames = new java.util.ArrayList<>();
        for (Rarity r : data.getAutoConvertRarities()) {
            rarityNames.add(r.name());
        }
        yml.set("auto-convert-rarities", rarityNames);
        yml.set("purchased-armor-tiers", new java.util.ArrayList<>(data.getPurchasedArmorTiers()));

        if (data.getEquippedTagItemKey() != null) {
            yml.set("tag-item", data.getEquippedTagItemKey());
            yml.set("tag-rarity", data.getEquippedTagRarity());
        }

        yml.set("respec-count", data.getRespecCount());
        yml.set("claimed-link-gift", data.hasClaimedLinkGift());
        // Always written, even empty, so the vault conversion on load only ever runs once.
        yml.set("perk-active", data.getActivePerkType() == null ? ""
                : data.getActivePerkType() + ":" + data.getActivePerkLevel());
        yml.set("rank", data.getRank());
        yml.set("aura-choice", data.getAuraChoice());
        java.util.List<String> ownedPets = new java.util.ArrayList<>();
        for (com.spacerng.solrng.pet.PetInstance pet : data.getOwnedPets().values()) {
            ownedPets.add(pet.serialise());
        }
        yml.set("pets-owned", ownedPets);
        yml.set("pets-equipped", new java.util.ArrayList<>(data.getEquippedPets()));
        yml.set("cosmic-dust", data.getCosmicDust());
        yml.set("farm-dust", data.getFarmDust());
        yml.set("keyall-at", data.getKeyallAt());
        yml.set("nick", data.getNick());
        yml.set("cosmetic-tags", new ArrayList<>(data.getCosmeticTags()));
        yml.set("cosmetic-tag", data.getWornCosmeticTag());
        yml.set("name-colour", data.getNameColour());
        yml.set("player-size", data.getPlayerSize());
        yml.set("perk-tickets", data.getPerkTickets());
        yml.set("perk-pity", data.getPerkPity());
        yml.set("perk-confirm", new java.util.ArrayList<>(data.getPerkConfirm()));
        yml.set("perk-found", new java.util.ArrayList<>(data.getPerkFound()));
        for (String old : java.util.List.of("perk-vault", "perk-equipped", "perk-pending", "perk-save-rolls",
                "perk-auto-save", "perk-save-amount", "perk-confirm-from", "perk-index")) {
            yml.set(old, null);
        }

        // The index mirrors the save, so it can never be staler than the
        // file it describes.
        plugin.getLeaderboardManager().record(data);

        try {
            yml.save(fileFor(data.getUuid()));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data for " + data.getUuid() + ": " + e.getMessage());
        }
    }
}
