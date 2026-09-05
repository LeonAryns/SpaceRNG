package com.spacerng.solrng;

import com.spacerng.solrng.commands.ArmorCommand;
import com.spacerng.solrng.commands.ConvertCommand;
import com.spacerng.solrng.commands.IndexCommand;
import com.spacerng.solrng.commands.OptionsCommand;
import com.spacerng.solrng.commands.PassCommand;
import com.spacerng.solrng.commands.PrestigeCommand;
import com.spacerng.solrng.commands.BuyCommand;
import com.spacerng.solrng.commands.CropsCommand;
import com.spacerng.solrng.commands.FarmTreeCommand;
import com.spacerng.solrng.commands.DailyCommand;
import com.spacerng.solrng.commands.TopCommand;
import com.spacerng.solrng.commands.GuideCommand;
import com.spacerng.solrng.commands.NovaCoreCommand;
import com.spacerng.solrng.commands.MilestonesCommand;
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
    private com.spacerng.solrng.farming.FarmPlotManager farmPlotManager;
    private com.spacerng.solrng.milestone.MilestoneManager milestoneManager;
    private com.spacerng.solrng.boost.BoostManager boostManager;
    private com.spacerng.solrng.boost.LuckBarManager luckBarManager;
    private com.spacerng.solrng.cookie.NovaCoreManager novaCoreManager;
    private com.spacerng.solrng.farming.HoeEnchantManager hoeEnchantManager;
    private com.spacerng.solrng.quest.QuestManager questManager;
    private com.spacerng.solrng.announce.AnnouncerManager announcerManager;
    private com.spacerng.solrng.daily.DailyManager dailyManager;
    private com.spacerng.solrng.leaderboard.LeaderboardManager leaderboardManager;
    private com.spacerng.solrng.pass.PassManager passManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.rarityManager = new RarityManager(getLogger());
        this.skillTreeManager = new SkillTreeManager(getLogger());
        this.leaderboardManager = new com.spacerng.solrng.leaderboard.LeaderboardManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.prestigeManager = new PrestigeManager(this);
        this.armorManager = new ArmorManager(this);
        this.tagManager = new TagManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.farmingManager = new FarmingManager(this);
        this.spawnManager = new SpawnManager(this);
        this.starforgeManager = new StarforgeManager(this);
        this.farmPlotManager = new com.spacerng.solrng.farming.FarmPlotManager(this);
        this.milestoneManager = new com.spacerng.solrng.milestone.MilestoneManager(this);
        this.boostManager = new com.spacerng.solrng.boost.BoostManager(this);
        this.luckBarManager = new com.spacerng.solrng.boost.LuckBarManager(this);
        this.novaCoreManager = new com.spacerng.solrng.cookie.NovaCoreManager(this);
        this.hoeEnchantManager = new com.spacerng.solrng.farming.HoeEnchantManager(this);
        this.questManager = new com.spacerng.solrng.quest.QuestManager(this);
        this.announcerManager = new com.spacerng.solrng.announce.AnnouncerManager(this);
        this.dailyManager = new com.spacerng.solrng.daily.DailyManager(this);
        this.passManager = new com.spacerng.solrng.pass.PassManager(this);

        reloadAll();

        this.rollListener = new RollListener(this);
        getServer().getPluginManager().registerEvents(rollListener, this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spacerng.solrng.farming.FarmPlotListener(this), this);

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
        getCommand("milestones").setExecutor(new MilestonesCommand(this));
        getCommand("crops").setExecutor(new CropsCommand(this));
        getCommand("novacore").setExecutor(new NovaCoreCommand(this));
        getCommand("farmtree").setExecutor(new FarmTreeCommand(this));
        getCommand("guide").setExecutor(new GuideCommand(this));
        getCommand("daily").setExecutor(new DailyCommand(this));
        TopCommand topCommand = new TopCommand(this);
        getCommand("top").setExecutor(topCommand);
        getCommand("top").setTabCompleter(topCommand);
        getCommand("buy").setExecutor(new BuyCommand(this));
        getCommand("pass").setExecutor(new PassCommand(this));
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
        luckBarManager.removeAll();
        questManager.removeAll();
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (leaderboardManager != null) {
            leaderboardManager.saveIndex();
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
        farmPlotManager.load(getConfig());
        milestoneManager.load(getConfig());
        boostManager.load(getConfig());
        novaCoreManager.load(getConfig());
        hoeEnchantManager.load(getConfig());
        questManager.load(getConfig());
        announcerManager.load(getConfig());
        dailyManager.load(getConfig());
        leaderboardManager.load(getConfig());
        passManager.load(getConfig());
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
                // isBusy, not isRolling: a big drop's reveal holds the
                // next auto-roll until the finale has played out.
                if (rollListener.isBusy(player.getUniqueId())) continue;

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

    public com.spacerng.solrng.farming.FarmPlotManager getFarmPlotManager() {
        return farmPlotManager;
    }

    public com.spacerng.solrng.boost.BoostManager getBoostManager() {
        return boostManager;
    }

    public com.spacerng.solrng.boost.LuckBarManager getLuckBarManager() {
        return luckBarManager;
    }

    public com.spacerng.solrng.announce.AnnouncerManager getAnnouncerManager() {
        return announcerManager;
    }

    public com.spacerng.solrng.leaderboard.LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public com.spacerng.solrng.pass.PassManager getPassManager() {
        return passManager;
    }

    public com.spacerng.solrng.daily.DailyManager getDailyManager() {
        return dailyManager;
    }

    public com.spacerng.solrng.quest.QuestManager getQuestManager() {
        return questManager;
    }

    public com.spacerng.solrng.farming.HoeEnchantManager getHoeEnchantManager() {
        return hoeEnchantManager;
    }

    public com.spacerng.solrng.cookie.NovaCoreManager getNovaCoreManager() {
        return novaCoreManager;
    }

    public com.spacerng.solrng.milestone.MilestoneManager getMilestoneManager() {
        return milestoneManager;
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
            for (Player player : getServer().getOnlinePlayers()) {
                PlayerData data = playerDataManager.get(player.getUniqueId());
                data.setSkillSpeedBonus(skillTreeManager.skillSpeed(data));
            }
        }, 5L, 5L);

        // Repaints the shared farm so plots appear as players walk into
        // range, and picks up anyone who logged in near one.
        getServer().getScheduler().runTaskTimer(this, () -> farmPlotManager.renderAll(), 40L, 40L);

        // Once, a few seconds in: put back any farm plot whose block went
        // missing while the server was down. Chunks need to be loaded for
        // this to see anything, hence the delay.
        getServer().getScheduler().runTaskLater(this, () -> {
            int healed = farmPlotManager.healAll();
            if (healed > 0) {
                getLogger().info("[SolRNG] Restored " + healed + " missing farm plot block(s).");
            }
        }, 100L);

        // The Luck bar has to tick on its own: a global boost's countdown
        // changes it every second even when the player does nothing.
        getServer().getScheduler().runTaskTimer(this, () -> luckBarManager.updateAll(), 20L, 20L);

        // One sweep covers every milestone track for everyone. A tier
        // landing a second late is invisible, and this can't miss a value
        // change the way per-event hooks can.
        getServer().getScheduler().runTaskTimer(this, () -> milestoneManager.checkAll(), 100L, 100L);

        // The guide's bar has to move as you play, so it ticks faster than
        // the milestone sweep — a quest that says 7/10 while you're at 9 is
        // worse than no counter at all.
        getServer().getScheduler().runTaskTimer(this, () -> questManager.checkAll(), 40L, 40L);

        // Rotating tips. Started here rather than inside the manager so a
        // /rngadmin reload can change the message list without leaving a
        // second timer running behind it.
        getServer().getScheduler().runTaskTimer(this, () -> announcerManager.broadcastNext(),
                announcerManager.getIntervalTicks(), announcerManager.getIntervalTicks());

        // A gentle nudge for anyone still on the guide, well apart from the
        // tips so the two never land together.
        getServer().getScheduler().runTaskTimer(this, () -> questManager.nudgeAll(), 2400L, 2400L);

        // Checks whether a farming period is due to roll over. On a timer
        // rather than scheduled for the hour, so it still fires if the
        // server was down when the hour passed.
        getServer().getScheduler().runTaskTimer(this, () -> leaderboardManager.tick(), 600L, 600L);
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
