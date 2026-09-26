package com.spacerng.solrng.listeners.menu;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.commands.TagCommand;
import com.spacerng.solrng.gui.ArmorGui;
import com.spacerng.solrng.gui.ArmorHolder;
import com.spacerng.solrng.gui.BuyGui;
import com.spacerng.solrng.gui.BuyHolder;
import com.spacerng.solrng.gui.ConvertGui;
import com.spacerng.solrng.gui.Currency;
import com.spacerng.solrng.gui.CropsGui;
import com.spacerng.solrng.gui.DailyGui;
import com.spacerng.solrng.gui.DailyHolder;
import com.spacerng.solrng.gui.HoeGui;
import com.spacerng.solrng.gui.HoeHolder;
import com.spacerng.solrng.gui.CropsHolder;
import com.spacerng.solrng.gui.ConvertHolder;
import com.spacerng.solrng.gui.IndexGui;
import com.spacerng.solrng.gui.IndexHolder;
import com.spacerng.solrng.gui.MilestoneGui;
import com.spacerng.solrng.gui.MilestoneHolder;
import com.spacerng.solrng.gui.NovaCoreGui;
import com.spacerng.solrng.gui.NovaCoreHolder;
import com.spacerng.solrng.gui.OptionsGui;
import com.spacerng.solrng.gui.OptionsHolder;
import com.spacerng.solrng.gui.PrestigeGui;
import com.spacerng.solrng.gui.PrestigeHolder;
import com.spacerng.solrng.gui.SkillTreeGui;
import com.spacerng.solrng.gui.SkillTreeHolder;
import com.spacerng.solrng.player.ArmorManager;
import com.spacerng.solrng.player.ArmorPiece;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.PrestigeManager;
import com.spacerng.solrng.rarity.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Clicks in the progression menus: prestige, Starforge, milestones, Nova Core, armor and the Battle Pass. */
final class ProgressionClicks {

    private final SolRNGPlugin plugin;

    ProgressionClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    void handleStarforgeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof com.spacerng.solrng.gui.StarforgeHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        // The Auto Roll switch only Bedrock players get (V223).
        if (event.getRawSlot() == com.spacerng.solrng.gui.StarforgeGui.AUTO_ROLL_SLOT
                && com.spacerng.solrng.platform.Bedrock.is((Player) event.getWhoClicked())) {
            Player player = (Player) event.getWhoClicked();
            plugin.getRollListener().toggleAutoRoll(player,
                    plugin.getPlayerDataManager().get(player.getUniqueId()));
            player.openInventory(com.spacerng.solrng.gui.StarforgeGui.build(plugin, player));
            return;
        }

        NamespacedKey tierIdKey = com.spacerng.solrng.gui.StarforgeGui.tierIdKey(plugin);
        String tierId = clicked.getItemMeta().getPersistentDataContainer().get(tierIdKey, PersistentDataType.STRING);
        if (tierId == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (plugin.getStarforgeManager().purchase(player, data, tierId)) {
            var tier = plugin.getStarforgeManager().get(tierId);
            player.sendMessage(ChatColor.GREEN + "Forged: " + tier.styledDisplay()
                    + ChatColor.GRAY + " (+"
                    + com.spacerng.solrng.starforge.StarforgeManager.formatPercent(tier.getLuckBonus())
                    + "% base Luck)");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
            player.openInventory(com.spacerng.solrng.gui.StarforgeGui.build(plugin, player)); // refresh
        } else {
            player.sendMessage(ChatColor.RED + "You can't forge that yet.");
        }
    }

    /** Read-only: switch track tab, or page through the current one. */
    void handleMilestoneClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof MilestoneHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        if (rawSlot == MilestoneGui.prevSlot()) {
            player.openInventory(MilestoneGui.build(plugin, player, holder.getTrackId(), holder.getPage() - 1));
            return;
        }
        if (rawSlot == MilestoneGui.nextSlot()) {
            player.openInventory(MilestoneGui.build(plugin, player, holder.getTrackId(), holder.getPage() + 1));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        var pdc = clicked.getItemMeta().getPersistentDataContainer();

        String trackId = pdc.get(MilestoneGui.trackKey(plugin), PersistentDataType.STRING);
        if (trackId != null && !trackId.equals(holder.getTrackId())) {
            player.openInventory(MilestoneGui.build(plugin, player, trackId, 0));
            return;
        }

        // Rewards are collected by hand: clicking a full rung is what pays.
        String tierRef = pdc.get(MilestoneGui.tierKey(plugin), PersistentDataType.STRING);
        if (tierRef == null) return;

        String[] parts = tierRef.split(":");
        if (parts.length != 2) return;
        var track = plugin.getMilestoneManager().get(parts[0]);
        if (track == null) return;

        int index;
        try {
            index = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return;
        }
        if (index < 0 || index >= track.getTiers().size()) return;

        var tier = track.getTiers().get(index);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data.hasClaimedMilestone(track.keyFor(tier))) return; // already taken, stay quiet

        if (!plugin.getMilestoneManager().claim(player, track, tier)) {
            player.sendMessage(ChatColor.RED + "You haven't reached that milestone yet.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        player.openInventory(MilestoneGui.build(plugin, player, holder.getTrackId(), holder.getPage()));
    }

    /** One button: forge the next Nova Core tier. */
    void handleNovaCoreClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof NovaCoreHolder)) return;
        if (event.getRawSlot() != NovaCoreGui.FORGE_SLOT) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        plugin.getNovaCoreManager().attempt(player, data);
        // Reopened either way - the odds, the price and the board all moved.
        player.openInventory(NovaCoreGui.build(plugin, player));
        plugin.getLuckBarManager().update(player);
    }

    void handleArmorClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof ArmorHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        NamespacedKey tierIdKey = ArmorGui.tierIdKey(plugin);
        String tierId = clicked.getItemMeta().getPersistentDataContainer().get(tierIdKey, PersistentDataType.STRING);
        String pieceName = clicked.getItemMeta().getPersistentDataContainer()
                .get(ArmorGui.pieceKey(plugin), PersistentDataType.STRING);
        if (tierId == null || pieceName == null) return;

        ArmorPiece piece;
        try {
            piece = ArmorPiece.valueOf(pieceName);
        } catch (IllegalArgumentException ex) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        ArmorManager armor = plugin.getArmorManager();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        if (armor.purchase(player, data, tierId, piece, rarityKey)) {
            player.sendMessage(ChatColor.GREEN + "Bought: " + armor.get(tierId).pieceDisplay(piece));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            player.openInventory(ArmorGui.build(plugin, player)); // refresh
        } else {
            player.sendMessage(ChatColor.RED + "You can't buy that yet.");
        }
    }

    void handlePrestigeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof PrestigeHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();
        int rawSlot = event.getRawSlot();

        if (holder.isUpgradesPage()) {
            if (rawSlot == PrestigeGui.BACK_SLOT) {
                player.openInventory(PrestigeGui.build(plugin, player));
                return;
            }
            handleUpgradeClick(player, data, prestige, event);
            return;
        }

        if (rawSlot == PrestigeGui.UPGRADES_SLOT) {
            player.openInventory(PrestigeGui.buildUpgrades(plugin, player));
            return;
        }

        if (rawSlot == PrestigeGui.LEVEL_SLOT) {
            // Shift click climbs as far as the rolls reach (V220), rather
            // than one click and one menu redraw per level.
            int gained = 0;
            while (prestige.levelUp(data)) {
                gained++;
                if (!event.isShiftClick()) break;
            }
            if (gained > 0) {
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "LEVEL UP! "
                        + ChatColor.RESET + ChatColor.GRAY + "You're now level "
                        + ChatColor.WHITE + data.getLevel() + ChatColor.GRAY
                        + (gained > 1 ? " (+" + gained + ")." : "."));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            } else {
                player.sendMessage(ChatColor.RED + "You need more rolls to level up.");
            }
        } else if (rawSlot == PrestigeGui.PRESTIGE_SLOT) {
            if (prestige.prestige(data)) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "ASCENDED! "
                        + ChatColor.RESET + ChatColor.GRAY + "Prestige "
                        + ChatColor.WHITE + data.getPrestige() + ChatColor.GRAY + ", and "
                        + ChatColor.LIGHT_PURPLE + "+" + prestige.getPointsPerPrestige()
                        + ChatColor.GRAY + " Prestige Points to spend.");
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                player.sendMessage(ChatColor.RED + "You don't have enough levels to ascend yet.");
                return;
            }
        } else {
            return;
        }

        plugin.getScoreboardManager().update(player);
        plugin.getLuckBarManager().update(player);
        player.openInventory(PrestigeGui.build(plugin, player));
    }

    /** Shift-click buys as many levels as the player's points allow. */
    void handleUpgradeClick(Player player, PlayerData data, PrestigeManager prestige,
                                    InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(PrestigeGui.upgradeKey(plugin), PersistentDataType.STRING);
        if (id == null) return;

        int bought = 0;
        if (event.isShiftClick()) {
            while (prestige.buyUpgrade(data, id)) bought++;
        } else if (prestige.buyUpgrade(data, id)) {
            bought = 1;
        }

        if (bought == 0) {
            player.sendMessage(ChatColor.RED + "You can't upgrade that right now.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        var upgrade = prestige.getUpgrade(id);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "UPGRADED "
                + ChatColor.RESET + ChatColor.YELLOW + upgrade.getDisplay()
                + ChatColor.GRAY + " to level " + ChatColor.WHITE + data.getUpgradeLevel(id)
                + (bought > 1 ? ChatColor.DARK_GRAY + " (+" + bought + ")" : ""));
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);

        plugin.getScoreboardManager().update(player);
        plugin.getLuckBarManager().update(player);
        player.openInventory(PrestigeGui.buildUpgrades(plugin, player));
    }

    void handlePassClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PassHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        com.spacerng.solrng.pass.PassManager pass = plugin.getPassManager();
        int slot = event.getRawSlot();

        if (slot == com.spacerng.solrng.gui.PassGui.prevSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PassGui.build(plugin, player, holder.getPage() - 1));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PassGui.nextSlot()) {
            player.openInventory(com.spacerng.solrng.gui.PassGui.build(plugin, player, holder.getPage() + 1));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PassGui.premiumSlot()) {
            if (data.isPassPremium()) return;
            if (!pass.buyPremium(player, data)) {
                player.sendMessage(ChatColor.RED + "You need "
                        + String.format("%,d", pass.getPremiumCost()) + " Credits for the premium track.");
                return;
            }
            player.openInventory(com.spacerng.solrng.gui.PassGui.build(plugin, player, holder.getPage()));
            return;
        }
        if (slot == com.spacerng.solrng.gui.PassGui.claimAllSlot()) {
            int claimed = pass.claimAll(player, data);
            if (claimed == 0) {
                player.sendMessage(ChatColor.RED + "Nothing to claim right now.");
                return;
            }
            player.openInventory(com.spacerng.solrng.gui.PassGui.build(plugin, player, holder.getPage()));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        ItemMeta meta = clicked.getItemMeta();
        Integer level = meta.getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.PassGui.levelKey(plugin), PersistentDataType.INTEGER);
        String track = meta.getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.PassGui.trackKey(plugin), PersistentDataType.STRING);
        if (level == null || track == null) return;

        if (!pass.claim(player, data, level, track)) {
            if (com.spacerng.solrng.pass.PassManager.PREMIUM.equals(track) && !data.isPassPremium()) {
                player.sendMessage(ChatColor.RED + "That reward is on the premium track.");
            } else if (data.hasClaimedPass(track, level)) {
                player.sendMessage(ChatColor.RED + "You already claimed that one.");
            } else {
                player.sendMessage(ChatColor.RED + "Reach level " + level + " first.");
            }
            return;
        }
        player.openInventory(com.spacerng.solrng.gui.PassGui.build(plugin, player, holder.getPage()));
    }
}
