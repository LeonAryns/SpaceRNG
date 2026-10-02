package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.roll.RollItemFactory;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class JoinQuitListener implements Listener {

    private final SolRNGPlugin plugin;

    public JoinQuitListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getWelcomeManager().send(event.getPlayer());
        // Vanilla /setworldspawn only actually relocates brand new players
        // and no-bed death respawns - everyone else just resumes wherever
        // they last logged off. Force every join to the configured spawn
        // (see /rngadmin setspawn) instead, if one's been set.
        if (plugin.getSpawnManager().hasSpawn()) {
            event.getPlayer().teleport(plugin.getSpawnManager().getSpawn());
        }

        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        // V287: an unlinked player is told twice in their first five
        // minutes on, where linking is worth the most to a starter.
        remindLink(event.getPlayer(), 30L * 20L);
        remindLink(event.getPlayer(), 240L * 20L);
        // Offline at the season reset: their inventory goes now.
        com.spacerng.solrng.player.SeasonWipe.onJoin(plugin, event.getPlayer());

        // Level/Prestige is intentionally NOT part of this - it's
        // tab-list-only via %solrng_level%, never the join broadcast.
        event.setJoinMessage(line(event.getPlayer(), "join.message",
                "&8[&a+&8] {name}"));

        // Any title marked auto-grant, which is how Beta reaches everybody
        // who turns up while beta is on. It runs before the name is drawn,
        // so a first time player wears it from their first line of chat.
        plugin.getCosmeticManager().grantAutomatic(data);
        // Their Discord roles catch up with whatever they bought or
        // linked while they were away.
        if (plugin.getDiscordBot() != null) plugin.getDiscordBot().syncRoles(event.getPlayer());
        drawFor(event.getPlayer(), data);
        // One Farmer's Hoe, with true lore: extra copies from before V254 go.
        plugin.getFarmingManager().refreshHoe(event.getPlayer(), data);
        com.spacerng.solrng.commands.TagCommand.autoEquipBest(plugin, event.getPlayer(), data);

        if (!event.getPlayer().hasPlayedBefore()) {
            // Another plugin's starter kit (Essentials' "tools") hands out
            // stone tools nobody here needs; take them back a tick later,
            // once that kit has landed (V256).
            org.bukkit.entity.Player fresh = event.getPlayer();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!fresh.isOnline()) return;
                var inv = fresh.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    var item = inv.getItem(i);
                    if (item == null) continue;
                    switch (item.getType()) {
                        case STONE_SWORD, STONE_PICKAXE, STONE_AXE, STONE_SHOVEL -> inv.setItem(i, null);
                        default -> { }
                    }
                }
            }, 5L);
            plugin.getWelcomeManager().broadcastNewPlayer(event.getPlayer());
            com.spacerng.solrng.player.Stash.give(plugin, event.getPlayer(), RollItemFactory.create(plugin, 1));
            event.getPlayer().sendMessage(ChatColor.GREEN + "Welcome to SpaceRNG! "
                    + ChatColor.GRAY + "Right-click your " + ChatColor.LIGHT_PURPLE + "Roll"
                    + ChatColor.GRAY + " item to get started.");
        }

        // The day's peak decides whether the farming payout runs at all,
        // and a peak concurrent count only ever rises on a join.
        plugin.getLeaderboardManager().notePlayerCount(
                org.bukkit.Bukkit.getOnlinePlayers().size());
        plugin.getQuestManager().check(event.getPlayer());
    }

    /** "Link your Discord for +100% Luck", if still unlinked when it is due. */
    private void remindLink(org.bukkit.entity.Player player, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.getLinkedAccountManager().isLinked(player.getUniqueId())) return;
            long luck = Math.round(plugin.getConfig().getDouble("linked-account.bonus.luck-percent", 1.0) * 100);
            player.sendMessage("");
            player.sendMessage(com.spacerng.solrng.gui.Lore.gradient("✦ DISCORD ✦", true, "#5865F2", "#7289DA", "#5865F2"));
            player.sendMessage(ChatColor.BLUE + com.spacerng.solrng.gui.Lore.BULLET + " " + ChatColor.GRAY
                    + "Link your Discord for " + ChatColor.GREEN + "+" + luck + "% Luck" + ChatColor.GRAY + ", for good.");
            player.sendMessage(ChatColor.BLUE + com.spacerng.solrng.gui.Lore.BULLET + " " + ChatColor.GRAY
                    + "Type " + ChatColor.AQUA + "/link" + ChatColor.GRAY + ", or " + ChatColor.AQUA + "/discord"
                    + ChatColor.GRAY + " to join first.");
            player.sendMessage("");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
        }, delayTicks);
    }

    /**
     * Everything the plugin draws for one player: the tag prefix, the name
     * in tab, their size, the floating tag, the sidebar, the luck bar, a
     * running boss, the farm and the auras around them.
     *
     * A join runs it, and so does an enable with players already online,
     * which is what a hot reload with PlugManX is (V211). Before this the
     * join was the only way in, so after a reload the players who were on
     * had no sidebar, no tag and no name until they relogged.
     */
    public void drawFor(org.bukkit.entity.Player player, PlayerData data) {
        plugin.getPerkManager().applyConfirmDefaults(data);
        // Rebuilds the equipped-tag team prefix (empty if none equipped).
        plugin.getTagManager().refreshPrefix(player, data);
        // The rank name in tab, and the size a /size rank picked, come back on join.
        plugin.getRankManager().refreshName(player);
        if (Math.abs(data.getPlayerSize() - 1.0) > 0.01) {
            com.spacerng.solrng.commands.SizeCommand.apply(plugin, player, data.getPlayerSize());
        }
        if (data.getEquippedTagItemKey() != null && data.getEquippedTagRarity() != null) {
            reattachHologram(player, data);
        }
        plugin.getScoreboardManager().setup(player);
        plugin.getLuckBarManager().show(player);
        // Somebody joining mid-event gets their own copy of the boss and
        // whatever time is left, otherwise the event is invisible to them.
        plugin.getBossManager().onJoin(player);
        plugin.getFarmPlotManager().render(player);
        // Also refreshes the auras: a Bedrock viewer is shown none of the
        // pieces, and gets the Bedrock copy of every leaderboard wall and
        // an ender chest on every crate.
        plugin.getBedrockSupport().applyTo(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.setQuitMessage(line(event.getPlayer(), "join.quit-message",
                "&8[&c-&8] {name}"));
        plugin.getRollListener().cancelRoll(event.getPlayer().getUniqueId());
        plugin.getTagManager().hideHologram(event.getPlayer().getUniqueId());
        plugin.getTagManager().removeNameplate(event.getPlayer().getUniqueId());
        plugin.getAuraManager().hide(event.getPlayer().getUniqueId());
        plugin.getTagManager().forgetPrefix(event.getPlayer().getUniqueId());
        plugin.getFarmPlotManager().forget(event.getPlayer().getUniqueId());
        plugin.getFarmPlotManager().forgetGolden(event.getPlayer().getUniqueId());
        plugin.getWelcomeManager().forget(event.getPlayer().getUniqueId());
        plugin.getLuckBarManager().hide(event.getPlayer().getUniqueId());
        plugin.getBossManager().hideBar(event.getPlayer().getUniqueId());
        plugin.getQuestManager().hide(event.getPlayer().getUniqueId());
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
    }

    // Mounted passengers (the floating tag) are cleared on death, so tear
    // them down then and rebuild once the player respawns.
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getTagManager().hideHologram(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getSpawnManager().hasSpawn()) {
            event.setRespawnLocation(plugin.getSpawnManager().getSpawn());
        }

        PlayerData data = plugin.getPlayerDataManager().get(event.getPlayer().getUniqueId());
        if (data.getEquippedTagItemKey() == null || data.getEquippedTagRarity() == null) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getTagManager().refreshOwnerTag(event.getPlayer()), 1L);
            return;
        }

        // Respawn teleport happens after this event fires, so wait a tick
        // before re-mounting or the displays spawn at the death location.
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> reattachHologram(event.getPlayer(), data), 1L);
    }

    private void reattachHologram(org.bukkit.entity.Player player, PlayerData data) {
        RollableItem rollable = plugin.getRarityManager().findByDisplayName(data.getEquippedTagItemKey());
        if (rollable == null) {
            plugin.getTagManager().refreshOwnerTag(player);
            return;
        }
        // The item's own colors, matching how it's named everywhere else.
        plugin.getTagManager().showHologram(player,
                plugin.getRarityManager().styleTagName(rollable),
                RollFormat.tagOdds(plugin, rollable));
    }

    /**
     * A join or quit line from config, or null for no line at all (V190).
     *
     * {name} is the whole name as it reads everywhere else: the rank
     * badge, the name in its own colours, and the cosmetic title behind
     * it. {plain} is just the account name, for anybody who wants the
     * line to stay quiet.
     */
    private String line(org.bukkit.entity.Player player, String path, String fallback) {
        String raw = plugin.getConfig().getString(path, fallback);
        if (raw == null || raw.isBlank()) return null;
        var data = plugin.getPlayerDataManager().get(player.getUniqueId());
        String name = plugin.getRankManager().badgeOf(player)
                + plugin.getRankManager().coloredName(player)
                + plugin.getCosmeticManager().suffixOf(data);
        return ChatColor.translateAlternateColorCodes('&', raw)
                .replace("{name}", name)
                .replace("{plain}", player.getName());
    }
}
