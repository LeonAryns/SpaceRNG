package com.spacerng.solrng;

import com.spacerng.solrng.commands.ArmorCommand;
import com.spacerng.solrng.commands.ConvertCommand;
import com.spacerng.solrng.commands.IndexCommand;
import com.spacerng.solrng.commands.OptionsCommand;
import com.spacerng.solrng.commands.PrestigeCommand;
import com.spacerng.solrng.commands.RngAdminCommand;
import com.spacerng.solrng.commands.RngCoreCommand;
import com.spacerng.solrng.commands.SkillTreeCommand;
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
import com.spacerng.solrng.rarity.RollableItem;
import com.spacerng.solrng.scoreboard.ScoreboardManager;
import com.spacerng.solrng.spawn.SpawnManager;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.rarityManager = new RarityManager(getLogger());
        this.skillTreeManager = new SkillTreeManager(getLogger());
        this.playerDataManager = new PlayerDataManager(this);
        this.prestigeManager = new PrestigeManager();
        this.armorManager = new ArmorManager(this);
        this.tagManager = new TagManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.farmingManager = new FarmingManager(this);
        this.spawnManager = new SpawnManager(this);

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
        getCommand("rngadmin").setExecutor(new RngAdminCommand(this));

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
    }

    /**
     * Runs every second; players with an autoroll skill tree node get an
     * automatic roll on their configured interval instead of needing to
     * right-click.
     */
    private void startAutoRollTask() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                PlayerData data = playerDataManager.get(player.getUniqueId());
                int interval = data.getAutoRollIntervalSeconds();
                if (interval <= 0) continue;

                long nowSeconds = System.currentTimeMillis() / 1000L;
                if (nowSeconds % interval == 0) {
                    double luck = prestigeManager.effectiveLuck(data);
                    RollableItem result = rarityManager.roll(luck);
                    rollListener.grantRoll(player, data, result, true);
                }
            }
        }, 20L, 20L);
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

    /**
     * Keeps every online player's Money / Tokens / Credits sidebar up to
     * date — covers changes from converting items, admin commands, or
     * another plugin (Vault economy) moving their Money balance.
     */
    private void startScoreboardRefreshTask() {
        getServer().getScheduler().runTaskTimer(this, () -> scoreboardManager.updateAll(), 20L, 20L);
    }

    /** Recomputes armor Luck bonuses from what's actually worn, every second. */
    private void startArmorRefreshTask() {
        getServer().getScheduler().runTaskTimer(this, () -> armorManager.refreshWornBonuses(), 20L, 20L);
    }

    private void registerPlaceholderExpansion() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        new SolRNGExpansion(this).register();
        getLogger().info("[SolRNG] Registered PlaceholderAPI expansion (%solrng_tag%, %solrng_level%).");
    }
}
