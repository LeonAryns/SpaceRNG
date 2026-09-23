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
import com.spacerng.solrng.commands.admin.RngAdminCommand;
import com.spacerng.solrng.commands.RngCoreCommand;
import com.spacerng.solrng.commands.SkillTreeCommand;
import com.spacerng.solrng.commands.StarforgeCommand;
import com.spacerng.solrng.commands.TagCommand;
import com.spacerng.solrng.farming.FarmingListener;
import com.spacerng.solrng.farming.FarmingManager;
import com.spacerng.solrng.listeners.ChatListener;
import com.spacerng.solrng.listeners.menu.GuiListener;
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
    private com.spacerng.solrng.boost.CrowdBoostManager crowdBoostManager;
    private com.spacerng.solrng.boost.LuckBarManager luckBarManager;
    private com.spacerng.solrng.nova.NovaCoreManager novaCoreManager;
    private com.spacerng.solrng.farming.HoeEnchantManager hoeEnchantManager;
    private com.spacerng.solrng.quest.QuestManager questManager;
    private com.spacerng.solrng.announce.AnnouncerManager announcerManager;
    private com.spacerng.solrng.daily.DailyManager dailyManager;
    private com.spacerng.solrng.leaderboard.LeaderboardManager leaderboardManager;
    private com.spacerng.solrng.firsts.FirstTenManager firstTenManager;
    private com.spacerng.solrng.aura.AuraManager auraManager;
    private com.spacerng.solrng.tab.TabListManager tabListManager;
    private com.spacerng.solrng.crate.CrateManager crateManager;
    private com.spacerng.solrng.discord.DiscordWebhook discordWebhook;
    private com.spacerng.solrng.holo.HoloManager holoManager;
    private com.spacerng.solrng.leaderboard.TopHeadManager topHeadManager;
    private com.spacerng.solrng.pass.PassManager passManager;
    private com.spacerng.solrng.farming.MomentumBar momentumBar;
    private com.spacerng.solrng.consumable.ConsumableManager consumableManager;
    private com.spacerng.solrng.welcome.WelcomeManager welcomeManager;
    private com.spacerng.solrng.perk.PerkManager perkManager;
    private com.spacerng.solrng.decor.FloatingItemManager floatingItemManager;
    private com.spacerng.solrng.discord.LinkedAccountManager linkedAccountManager;

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
        this.firstTenManager = new com.spacerng.solrng.firsts.FirstTenManager(this);
        this.auraManager = new com.spacerng.solrng.aura.AuraManager(this);
        this.tabListManager = new com.spacerng.solrng.tab.TabListManager(this);
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
        this.crowdBoostManager = new com.spacerng.solrng.boost.CrowdBoostManager(this);
        this.luckBarManager = new com.spacerng.solrng.boost.LuckBarManager(this);
        this.novaCoreManager = new com.spacerng.solrng.nova.NovaCoreManager(this);
        this.hoeEnchantManager = new com.spacerng.solrng.farming.HoeEnchantManager(this);
        this.questManager = new com.spacerng.solrng.quest.QuestManager(this);
        this.announcerManager = new com.spacerng.solrng.announce.AnnouncerManager(this);
        this.dailyManager = new com.spacerng.solrng.daily.DailyManager(this);
        this.passManager = new com.spacerng.solrng.pass.PassManager(this);
        this.momentumBar = new com.spacerng.solrng.farming.MomentumBar();
        this.consumableManager = new com.spacerng.solrng.consumable.ConsumableManager(this);
        this.welcomeManager = new com.spacerng.solrng.welcome.WelcomeManager(this);
        this.crateManager = new com.spacerng.solrng.crate.CrateManager(this);
        this.discordWebhook = new com.spacerng.solrng.discord.DiscordWebhook(this);
        this.holoManager = new com.spacerng.solrng.holo.HoloManager(this);
        this.topHeadManager = new com.spacerng.solrng.leaderboard.TopHeadManager(this);
        this.perkManager = new com.spacerng.solrng.perk.PerkManager(getLogger());
        this.floatingItemManager = new com.spacerng.solrng.decor.FloatingItemManager(this);
        this.linkedAccountManager = new com.spacerng.solrng.discord.LinkedAccountManager(this);
        this.rankManager = new com.spacerng.solrng.rank.RankManager(this);
        this.bossManager = new com.spacerng.solrng.boss.BossManager(this);
        this.petManager = new com.spacerng.solrng.pet.PetManager(this);
        this.dustManager = new com.spacerng.solrng.pet.DustManager(this);
        // Only built when DiscordSRV is actually installed. The class
        // mentions its types, so touching it without the plugin present
        // would be a NoClassDefFoundError on startup.
        if (getServer().getPluginManager().getPlugin("DiscordSRV") != null) {
            try {
                com.spacerng.solrng.discord.DiscordBot bot = new com.spacerng.solrng.discord.DiscordBot(this);
                bot.start();
                this.discordBot = bot;
            } catch (Throwable t) {
                getLogger().warning("Discord bot could not start: " + t);
            }
        }

        reloadAll();

        this.rollListener = new RollListener(this);
        getServer().getPluginManager().registerEvents(rollListener, this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new FarmingListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spacerng.solrng.farming.FarmPlotListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spacerng.solrng.listeners.WorldLoadListener(this), this);
        getServer().getPluginManager().registerEvents(new com.spacerng.solrng.listeners.HungerListener(), this);
        getServer().getPluginManager().registerEvents(new com.spacerng.solrng.listeners.FarmlandListener(), this);
        getServer().getPluginManager().registerEvents(new com.spacerng.solrng.listeners.ChatTagsListener(this), this);
        foundCounts = new com.spacerng.solrng.player.FoundCounts(this);
        foundCounts.load();
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
        getCommand("linked").setExecutor(new com.spacerng.solrng.commands.LinkedCommand(this));
        getCommand("potion").setExecutor(new com.spacerng.solrng.commands.PotionCommand(this));
        getCommand("shop").setExecutor(new com.spacerng.solrng.commands.ShopCommand(this));
        getCommand("ranks").setExecutor(new com.spacerng.solrng.commands.RanksCommand(this));
        getCommand("aura").setExecutor(new com.spacerng.solrng.commands.AuraCommand(this));
        com.spacerng.solrng.commands.PrivateVaultCommand vaultCommand =
                new com.spacerng.solrng.commands.PrivateVaultCommand(this);
        getCommand("pv").setExecutor(vaultCommand);
        getCommand("pv").setTabCompleter(vaultCommand);
        getCommand("keyall").setExecutor(new com.spacerng.solrng.commands.KeyAllCommand(this));
        getCommand("fly").setExecutor(new com.spacerng.solrng.commands.FlyCommand(this));
        getCommand("nick").setExecutor(new com.spacerng.solrng.commands.NickCommand(this));
        getCommand("size").setExecutor(new com.spacerng.solrng.commands.SizeCommand(this));
        getCommand("boss").setExecutor(new com.spacerng.solrng.commands.BossCommand(this));
        getCommand("pets").setExecutor(new com.spacerng.solrng.commands.PetsCommand(this));
        getCommand("stash").setExecutor(new com.spacerng.solrng.commands.StashCommand(this));
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
        holoManager.start();
        auraManager.start();
        tabListManager.start();
        floatingItemManager.start();
        linkedAccountManager.start();
        crowdBoostManager.start();
        rankManager.start();
        bossManager.start();

        getLogger().info("SpaceRNG enabled.");
    }

    @Override
    public void onDisable() {
        luckBarManager.removeAll();
        questManager.removeAll();
        // Before saving: a crate still spinning pays out now, so the reward
        // is inside the save file rather than lost with the server.
        if (crateManager != null) crateManager.finishAll();
        if (foundCounts != null) foundCounts.save();
        if (topHeadManager != null) topHeadManager.stop();
        if (holoManager != null) holoManager.stop();
        if (auraManager != null) auraManager.stop();
        if (tabListManager != null) tabListManager.stop();
        if (floatingItemManager != null) floatingItemManager.stop();
        if (crowdBoostManager != null) crowdBoostManager.stop();
        if (linkedAccountManager != null) linkedAccountManager.stop();
        if (rankManager != null) rankManager.stop();
        if (bossManager != null) bossManager.stop();
        if (discordBot != null) discordBot.shutdown();
        if (momentumBar != null) momentumBar.removeAll();
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        if (firstTenManager != null) firstTenManager.save();
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

    private void holdTime() {
        long lock = getConfig().getLong("world-time.lock", 6000L);
        if (lock < 0) return;
        for (org.bukkit.World world : getServer().getWorlds()) {
            if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL) continue;
            stopDaylightCycle(world);
            if (Math.abs(world.getTime() - lock) > 20) world.setTime(lock);
        }
    }

    @SuppressWarnings("unchecked")
    private void stopDaylightCycle(org.bukkit.World world) {
        for (String name : new String[]{"doDaylightCycle", "advance_time", "minecraft:advance_time"}) {
            try {
                org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(name);
                if (rule == null || rule.getType() != Boolean.class) continue;
                org.bukkit.GameRule<Boolean> daylight = (org.bukkit.GameRule<Boolean>) rule;
                if (!Boolean.FALSE.equals(world.getGameRuleValue(daylight))) world.setGameRule(daylight, false);
                return;
            } catch (RuntimeException ignored) {
                // not this name on this version, try the next
            }
        }
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
        crowdBoostManager.load(getConfig());
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
        com.spacerng.solrng.gui.Lore.setTheme(
                com.spacerng.solrng.gui.Lore.Theme.parse(getConfig().getString("menu-style", "classic")));
        discordWebhook.load(getConfig());
        holoManager.load(getConfig());
        topHeadManager.load(getConfig());
        perkManager.load(getConfig());
        floatingItemManager.load(getConfig());
        linkedAccountManager.load(getConfig());
        rankManager.load(getConfig());
        bossManager.load(getConfig());
        petManager.load(getConfig());
        dustManager.load(getConfig());
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

    public com.spacerng.solrng.aura.AuraManager getAuraManager() {
        return auraManager;
    }

    public com.spacerng.solrng.firsts.FirstTenManager getFirstTenManager() {
        return firstTenManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public com.spacerng.solrng.farming.FarmPlotManager getFarmPlotManager() {
        return farmPlotManager;
    }

    public com.spacerng.solrng.boost.CrowdBoostManager getCrowdBoostManager() {
        return crowdBoostManager;
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

    public com.spacerng.solrng.discord.DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    private com.spacerng.solrng.player.FoundCounts foundCounts;
    private com.spacerng.solrng.rank.RankManager rankManager;
    private com.spacerng.solrng.boss.BossManager bossManager;
    private com.spacerng.solrng.pet.PetManager petManager;
    private com.spacerng.solrng.pet.DustManager dustManager;
    private com.spacerng.solrng.discord.BotHooks discordBot;

    /** The Discord bot, or null when DiscordSRV is not installed. */
    public com.spacerng.solrng.discord.BotHooks getDiscordBot() {
        return discordBot;
    }

    /** Pets: what a player owns, what they wear and what it pays. */
    public com.spacerng.solrng.pet.DustManager getDustManager() {
        return dustManager;
    }

    public com.spacerng.solrng.pet.PetManager getPetManager() {
        return petManager;
    }

    /** The boss event: what is up, who has hurt it, what it pays. */
    public com.spacerng.solrng.boss.BossManager getBossManager() {
        return bossManager;
    }

    /** Ranks, their multipliers and what they unlock. */
    public com.spacerng.solrng.rank.RankManager getRankManager() {
        return rankManager;
    }

    /** How many players have found each drop, for /index. */
    public com.spacerng.solrng.player.FoundCounts getFoundCounts() {
        return foundCounts;
    }

    public com.spacerng.solrng.holo.HoloManager getHoloManager() {
        return holoManager;
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

    public com.spacerng.solrng.discord.LinkedAccountManager getLinkedAccountManager() {
        return linkedAccountManager;
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

    public com.spacerng.solrng.nova.NovaCoreManager getNovaCoreManager() {
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

        // The sun stays put (V171): world-time.lock is the time of day to
        // hold, -1 lets it run. The daylight gamerule was renamed in
        // 1.21.11, so it is looked up by every name it has had, and the
        // time is pinned every second whether or not one was found.
        getServer().getScheduler().runTaskTimer(this, this::holdTime, 40L, 20L);

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

        // Hide Other Farmers, checked once a second.
        var farmVisibility = new com.spacerng.solrng.farming.FarmVisibility(this);
        getServer().getScheduler().runTaskTimer(this, farmVisibility::tick, 20L, 20L);

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

        // Autosave. Player data was only written on quit and on a clean
        // shutdown, so a crash lost everything online players had done since
        // they joined. Every five minutes caps that at five minutes.
        getServer().getScheduler().runTaskTimer(this, () -> {
            playerDataManager.saveAll();
            if (firstTenManager != null) firstTenManager.save();
        }, 6000L, 6000L);
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
