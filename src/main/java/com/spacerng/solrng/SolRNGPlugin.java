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
    private com.spacerng.solrng.crate.CrateManager crateManager;
    private com.spacerng.solrng.leaderboard.TopHeadManager topHeadManager;
    private com.spacerng.solrng.pass.PassManager passManager;
    private com.spacerng.solrng.farming.MomentumBar momentumBar;
    private com.spacerng.solrng.consumable.ConsumableManager consumableManager;
    private com.spacerng.solrng.welcome.WelcomeManager welcomeManager;
    private com.spacerng.solrng.perk.PerkManager perkManager;
    private com.spacerng.solrng.decor.FloatingItemManager floatingItemManager;

    /** The namespace every PersistentDataContainer tag is written under. */
    private static final String TAG_NAMESPACE = "solrng";

    /**
     * Set when the SolRNG -> SpaceRNG folder move fails, so onEnable can
     * refuse to start rather than quietly rebuild every player's save
     * from scratch in an empty new folder.
     */
    private boolean folderMoveFailed;

    /**
     * A key in the plugin's tag namespace.
     *
     * Pinned to the old id on purpose. NamespacedKey(plugin, key) derives
     * its namespace from the plugin's name, so renaming the plugin would
     * have silently orphaned the tags on every rolled item, hoe, armor
     * piece and plot already sitting in somebody's inventory. The name on
     * the tin changed; the name in the NBT can't.
     */
    public static org.bukkit.NamespacedKey key(String name) {
        return java.util.Objects.requireNonNull(
                org.bukkit.NamespacedKey.fromString(TAG_NAMESPACE + ":" + name));
    }

    /**
     * Moves plugins/SolRNG to plugins/SpaceRNG the first time the renamed
     * build starts.
     *
     * onLoad, not onEnable: the data folder is created the moment
     * saveDefaultConfig() runs, and a folder that already exists is the
     * signal that there is nothing to migrate.
     */
    @Override
    public void onLoad() {
        java.io.File current = getDataFolder();
        java.io.File legacy = new java.io.File(current.getParentFile(), "SolRNG");
        if (current.exists() || !legacy.isDirectory()) return;
        if (legacy.renameTo(current)) {
            getLogger().info("Moved plugins/SolRNG to plugins/SpaceRNG.");
        } else {
            folderMoveFailed = true;
        }
    }

    @Override
    public void onEnable() {
        if (folderMoveFailed) {
            getLogger().severe("Couldn't move plugins/SolRNG to plugins/SpaceRNG. Rename that "
                    + "folder by hand and restart - refusing to start on an empty data folder "
                    + "and lose everyone's progress.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        // Bring the structural sections on disk up to speed with what
        // the jar ships. Runs before every manager loads its config so
        // the freshly-written sections are what they see.
        ConfigMigrator.run(this);
        sweepBossBars();

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
        this.momentumBar = new com.spacerng.solrng.farming.MomentumBar();
        this.consumableManager = new com.spacerng.solrng.consumable.ConsumableManager(this);
        this.welcomeManager = new com.spacerng.solrng.welcome.WelcomeManager(this);
        this.crateManager = new com.spacerng.solrng.crate.CrateManager(this);
        this.topHeadManager = new com.spacerng.solrng.leaderboard.TopHeadManager(this);
        this.perkManager = new com.spacerng.solrng.perk.PerkManager(getLogger());
        this.floatingItemManager = new com.spacerng.solrng.decor.FloatingItemManager(this);

        reloadAll();

        this.rollListener = new RollListener(this);
        getServer().getPluginManager().registerEvents(rollListener, this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spacerng.solrng.farming.FarmPlotListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.spacerng.solrng.consumable.ConsumableListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.spacerng.solrng.crate.CrateListener(this), this);

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
        getCommand("boosts").setExecutor(new com.spacerng.solrng.commands.BoostsCommand(this));
        getCommand("perks").setExecutor(new com.spacerng.solrng.commands.PerkCommand(this));
        getCommand("potion").setExecutor(new com.spacerng.solrng.commands.PotionCommand(this));
        getCommand("shop").setExecutor(new com.spacerng.solrng.commands.ShopCommand(this));
        getCommand("leaderboards").setExecutor(
                new com.spacerng.solrng.commands.LeaderboardsCommand(this));
        getCommand("stats").setExecutor(new com.spacerng.solrng.commands.StatsCommand(this));
        var limitLuck = new com.spacerng.solrng.commands.LimitLuckCommand(this);
        getCommand("limitluck").setExecutor(limitLuck);
        getCommand("limitluck").setTabCompleter(limitLuck);
        getCommand("rngadmin").setExecutor(adminCommand);
        getCommand("rngadmin").setTabCompleter(adminCommand);

        startAutoRollTask();
        startScoreboardRefreshTask();
        startArmorRefreshTask();
        registerPlaceholderExpansion();
        topHeadManager.start();
        floatingItemManager.start();

        getLogger().info("SpaceRNG enabled.");
    }

    @Override
    public void onDisable() {
        luckBarManager.removeAll();
        questManager.removeAll();
        // Before saving: a crate still spinning pays out now, so the reward
        // is inside the save file rather than lost with the server.
        if (crateManager != null) crateManager.finishAll();
        if (topHeadManager != null) topHeadManager.stop();
        if (floatingItemManager != null) floatingItemManager.stop();
        if (momentumBar != null) momentumBar.removeAll();
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (leaderboardManager != null) {
            leaderboardManager.saveIndex();
        }
        getLogger().info("SpaceRNG disabled, player data saved.");
    }

    /**
     * Clears every boss bar this plugin left on screen last time.
     *
     * onDisable removes them, but it only runs on a clean stop - a crash,
     * a kill, or a hot plugin reload skips it and leaves the bars behind.
     * The player keeps seeing them, the new instance draws its own beside
     * them, and you end up with two of everything. Keyed bars survive in
     * the server's registry, so a fresh start can find the old ones and
     * take them down before drawing anything.
     */
    private void sweepBossBars() {
        java.util.List<org.bukkit.NamespacedKey> stale = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.boss.KeyedBossBar> bars = getServer().getBossBars();
        while (bars.hasNext()) {
            org.bukkit.boss.KeyedBossBar bar = bars.next();
            if (bar.getKey().getNamespace().equals(TAG_NAMESPACE)) {
                bar.removeAll();
                stale.add(bar.getKey());
            }
        }
        for (org.bukkit.NamespacedKey key : stale) {
            getServer().removeBossBar(key);
        }
        if (!stale.isEmpty()) {
            getLogger().info("Cleared " + stale.size() + " boss bar(s) left over from the last run.");
        }
    }

    /**
     * How many of one rarity the convert vault holds.
     *
     * Base from config, raised by the Vault Space nodes. Derived rather
     * than stored, like every other stat, so retuning it retunes it for
     * everybody who already bought the skill.
     */
    public long convertCap(com.spacerng.solrng.player.PlayerData data) {
        long base = getConfig().getLong("conversion.max-per-rarity", 100L);
        return base + Math.round(skillTreeManager.totalOf(data,
                com.spacerng.solrng.player.SkillNode.Effect.CONVERT_CAP));
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
        consumableManager.load(getConfig());
        welcomeManager.load(getConfig());
        crateManager.load(getConfig());
        topHeadManager.load(getConfig());
        perkManager.load(getConfig());
        floatingItemManager.load(getConfig());
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

    public com.spacerng.solrng.crate.CrateManager getCrateManager() {
        return crateManager;
    }

    public com.spacerng.solrng.leaderboard.TopHeadManager getTopHeadManager() {
        return topHeadManager;
    }

    public com.spacerng.solrng.pass.PassManager getPassManager() {
        return passManager;
    }

    public com.spacerng.solrng.farming.MomentumBar getMomentumBar() {
        return momentumBar;
    }

    public com.spacerng.solrng.consumable.ConsumableManager getConsumableManager() {
        return consumableManager;
    }

    public com.spacerng.solrng.welcome.WelcomeManager getWelcomeManager() {
        return welcomeManager;
    }

    public com.spacerng.solrng.perk.PerkManager getPerkManager() {
        return perkManager;
    }

    public com.spacerng.solrng.decor.FloatingItemManager getFloatingItemManager() {
        return floatingItemManager;
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
     * date - covers changes from converting items, admin commands, or
     * another plugin (Vault economy) moving their Money balance.
     */
    private void startScoreboardRefreshTask() {
        getServer().getScheduler().runTaskTimer(this, () -> scoreboardManager.updateAll(), 20L, 20L);
    }

    /**
     * Recomputes the bonuses that depend on what a player currently has
     * equipped - worn armor and the held Starforge. Runs four times a
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
        // The golden crop shimmers on its own clock: often enough to catch
        // the eye across a field, rarely enough to cost nothing.
        getServer().getScheduler().runTaskTimer(this, () -> farmPlotManager.tickGolden(), 10L, 10L);

        // Once, a few seconds in: put back any farm plot whose block went
        // missing while the server was down. Chunks need to be loaded for
        // this to see anything, hence the delay.
        getServer().getScheduler().runTaskLater(this, () -> {
            int healed = farmPlotManager.healAll();
            if (healed > 0) {
                getLogger().info("Restored " + healed + " missing farm plot block(s).");
            }
        }, 100L);

        // The Luck bar has to tick on its own: a global boost's countdown
        // changes it every second even when the player does nothing.
        getServer().getScheduler().runTaskTimer(this, () -> luckBarManager.updateAll(), 20L, 20L);

        // A farming run ends by nothing happening, so something has to
        // notice the silence and take the Momentum bar down.
        getServer().getScheduler().runTaskTimer(this, () -> farmPlotManager.expireMomentum(), 40L, 40L);

        // One sweep covers every milestone track for everyone. A tier
        // landing a second late is invisible, and this can't miss a value
        // change the way per-event hooks can.
        getServer().getScheduler().runTaskTimer(this, () -> milestoneManager.checkAll(), 100L, 100L);

        // The guide's bar has to move as you play, so it ticks faster than
        // the milestone sweep - a quest that says 7/10 while you're at 9 is
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
        new SolRNGExpansion.Legacy(this).register();
        getLogger().info("Registered PlaceholderAPI expansion: %spacerng_tag%, %spacerng_tag_plain%, "
                + "%spacerng_prestige%, %spacerng_prestige_roman%, %spacerng_prestige_badge%, "
                + "%spacerng_level% (and more - see config.yml). The old %solrng_ spelling still works.");
    }
}
