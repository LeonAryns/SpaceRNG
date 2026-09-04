package com.spacerng.solrng;

import com.spacerng.solrng.commands.ArmorCommand;
import com.spacerng.solrng.commands.ConvertCommand;
import com.spacerng.solrng.commands.IndexCommand;
import com.spacerng.solrng.commands.OptionsCommand;
import com.spacerng.solrng.commands.PrestigeCommand;
import com.spacerng.solrng.commands.RngAdminCommand;
import com.spacerng.solrng.commands.RngCoreCommand;
import com.spacerng.solrng.commands.SkillTreeCommand;
import com.spacerng.solrng.commands.StarforgeCommand;
import com.spacerng.solrng.commands.TagCommand;
import com.spacerng.solrng.farming.FarmingListener;
import com.spacerng.solrng.farming.FarmingManager;
import com.spacerng.solrng.listeners.ChatListener;
import com.spacerng.solrng.listeners.GuiListener;
import com.spacerng.solrng.listeners.JoinQuitListener;
import com.spacerng.solrng.listeners.RollListener;
import com.spacerng.solrng.player.ArmorManager;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.PlayerDataManager;
import com.spacerng.solrng.player.PrestigeManager;
import com.spacerng.solrng.player.SkillTreeManager;
import com.spacerng.solrng.placeholder.SolRNGExpansion;
import com.spacerng.solrng.rarity.RarityManager;
import com.spacerng.solrng.scoreboard.ScoreboardManager;
import com.spacerng.solrng.spawn.SpawnManager;
import com.spacerng.solrng.starforge.StarforgeManager;
import com.spacerng.solrng.tag.TagManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SolRNGPlugin extends JavaPlugin {

    private RarityManager rarityManager;
    private SkillTreeManager skillTreeManager;
    private PlayerDataManager playerDataManager;
    private PrestigeManager prestigeManager;
    private ArmorManager armorManager;
    private TagManager tagManager;
    private RollListener rollListener;
    private ScoreboardManager scoreboardManager;
    private FarmingManager farmingManager;
    private SpawnManager spawnManager;
    private StarforgeManager starforgeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.rarityManager = new RarityManager(getLogger());
        this.skillTreeManager = new SkillTreeManager(getLogger());
        this.playerDataManager = new PlayerDataManager(this);
        this.prestigeManager = new PrestigeManager(this);
        this.armorManager = new ArmorManager(this);
        this.tagManager = new TagManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.farmingManager = new FarmingManager(this);
        this.spawnManager = new SpawnManager(this);
        this.starforgeManager = new StarforgeManager(this);

        reloadAll();

        this.rollListener = new RollListener(this);
        getServer().getPluginManager().registerEvents(rollListener, this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(this), this);

        getCommand("rngcore").setExecutor(new RngCoreCommand(this));
        getCommand("skilltree").setExecutor(new SkillTreeCommand(this));
        getCommand("convert").setExecutor(new ConvertCommand(this));
        getCommand("tag").setExecutor(new TagCommand(this));
        getCommand("index").setExecutor(new IndexCommand(this));
        getCommand("prestige").setExecutor(new PrestigeCommand(this));
        getCommand("armor").setExecutor(new ArmorCommand(this));
        getCommand("options").setExecutor(new OptionsCommand(this));
        getCommand("starforge").setExecutor(new StarforgeCommand(this));
        RngAdminCommand adminCommand = new RngAdminCommand(this);
        getCommand("rngadmin").setExecutor(adminCommand);
        getCommand("rngadmin").setTabCompleter(adminCommand);

        startAutoRollTask();
        startScoreboardRefreshTask();
        startArmorRefreshTask();
        registerPlaceholderExpansion();

        getLogger().info("SolRNG enabled.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        getLogger().info("SolRNG disabled, player data saved.");
    }

    public void reloadAll() {
        reloadConfig();
        rarityManager.load(getConfig());
        skillTreeManager.load(getConfig());
        prestigeManager.load(getConfig());
        armorManager.load(getConfig());
        farmingManager.load(getConfig());
        starforgeManager.load(getConfig());
    }

    /**
     * Auto Roll just presses right-click for the player: it starts a real
     * roll whenever they aren't already mid-roll. Going through startRoll
     * rather than granting instantly means auto-rolls get the same
     * animation, sounds and notifications as manual ones, and the cadence
     * comes out right for free since the roll itself takes exactly as
     * long as the player's Speed says it should.
     */
    private void startAutoRollTask() {
        final long periodTicks = 5L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                PlayerData data = playerDataManager.get(player.getUniqueId());
                if (!data.isAutoRollEnabled()) continue;
                if (!starforgeManager.isHolding(player)) continue; // refresh task turns the flag off
                if (rollListener.isRolling(player.getUniqueId())) continue;

                rollListener.startRoll(player);
            }
        }, periodTicks, periodTicks);
    }

    public RarityManager getRarityManager() {
        return rarityManager;
    }

    public SkillTreeManager getSkillTreeManager() {
        return skillTreeManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public PrestigeManager getPrestigeManager() {
        return prestigeManager;
    }

    public ArmorManager getArmorManager() {
        return armorManager;
    }

    public TagManager getTagManager() {
        return tagManager;
    }

    public RollListener getRollListener() {
        return rollListener;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public FarmingManager getFarmingManager() {
        return farmingManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public StarforgeManager getStarforgeManager() {
        return starforgeManager;
    }

    /**
     * Keeps every online player's Money / Tokens / Credits sidebar up to
     * date — covers changes from converting items, admin commands, or
     * another plugin (Vault economy) moving their Money balance.
     */
    private void startScoreboardRefreshTask() {
        getServer().getScheduler().runTaskTimer(this, () -> scoreboardManager.updateAll(), 20L, 20L);
    }

    /**
     * Recomputes the bonuses that depend on what a player currently has
     * equipped — worn armor and the held Starforge. Runs four times a
     * second so picking the Starforge up or putting it away shows on the
     * scoreboard more or less instantly.
     */
    private void startArmorRefreshTask() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            armorManager.refreshWornBonuses();
            starforgeManager.refreshHeldBonuses();
        }, 5L, 5L);
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new SolRNGExpansion(this).register();
        getLogger().info("[SolRNG] Registered PlaceholderAPI expansion: %solrng_tag%, %solrng_tag_plain%, "
                + "%solrng_prestige%, %solrng_prestige_roman%, %solrng_prestige_badge%, %solrng_level% (and more - see config.yml).");
    }
}
