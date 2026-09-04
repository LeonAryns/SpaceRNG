package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.commands.TagCommand;
import com.spacerng.solrng.gui.ArmorGui;
import com.spacerng.solrng.gui.ArmorHolder;
import com.spacerng.solrng.gui.ConvertGui;
import com.spacerng.solrng.gui.CropsGui;
import com.spacerng.solrng.gui.CropsHolder;
import com.spacerng.solrng.gui.ConvertHolder;
import com.spacerng.solrng.gui.IndexGui;
import com.spacerng.solrng.gui.IndexHolder;
import com.spacerng.solrng.gui.MilestoneGui;
import com.spacerng.solrng.gui.MilestoneHolder;
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

public class GuiListener implements Listener {

    private final SolRNGPlugin plugin;

    public GuiListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof SkillTreeHolder) {
            handleSkillTreeClick(event);
        } else if (topInventory.getHolder() instanceof ConvertHolder) {
            handleConvertClick(event);
        } else if (topInventory.getHolder() instanceof IndexHolder) {
            handleIndexClick(event);
        } else if (topInventory.getHolder() instanceof PrestigeHolder) {
            handlePrestigeClick(event);
        } else if (topInventory.getHolder() instanceof ArmorHolder) {
            handleArmorClick(event);
        } else if (topInventory.getHolder() instanceof OptionsHolder) {
            handleOptionsClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.StarforgeHolder) {
            handleStarforgeClick(event);
        } else if (topInventory.getHolder() instanceof MilestoneHolder) {
            handleMilestoneClick(event);
        } else if (topInventory.getHolder() instanceof CropsHolder) {
            handleCropsClick(event);
        }
    }

    private void handleStarforgeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof com.spacerng.solrng.gui.StarforgeHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

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

    private void handleOptionsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof OptionsHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int rawSlot = event.getRawSlot();

        if (rawSlot == OptionsHolder.SOUND_SLOT) {
            data.setRollSoundEnabled(!data.isRollSoundEnabled());
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.ANIMATION_SLOT) {
            data.setRollAnimationEnabled(!data.isRollAnimationEnabled());
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.AURA_SLOT) {
            data.setRevealAuraEnabled(!data.isRevealAuraEnabled());
            player.openInventory(OptionsGui.build(plugin, player));
        }
    }

    /** The milestone screens are read-only: pick a track, page, or go back. */
    private void handleMilestoneClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof MilestoneHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        if (holder.getTrackId() != null) {
            if (rawSlot == MilestoneGui.backSlot()) {
                player.openInventory(MilestoneGui.buildRoot(plugin, player));
                return;
            }
            if (rawSlot == MilestoneGui.prevSlot()) {
                player.openInventory(MilestoneGui.buildTrack(plugin, player, holder.getTrackId(), holder.getPage() - 1));
                return;
            }
            if (rawSlot == MilestoneGui.nextSlot()) {
                player.openInventory(MilestoneGui.buildTrack(plugin, player, holder.getTrackId(), holder.getPage() + 1));
                return;
            }
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        String trackId = clicked.getItemMeta().getPersistentDataContainer()
                .get(MilestoneGui.trackKey(plugin), PersistentDataType.STRING);
        if (trackId != null) {
            player.openInventory(MilestoneGui.buildTrack(plugin, player, trackId, 0));
        }
    }

    /** Picking a crop repaints the shared farm for this player only. */
    private void handleCropsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof CropsHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        String cropId = clicked.getItemMeta().getPersistentDataContainer()
                .get(CropsGui.cropKey(plugin), PersistentDataType.STRING);
        if (cropId == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var farm = plugin.getFarmPlotManager();
        var crop = farm.getCrop(cropId);
        if (crop == null) return;

        if (!farm.isUnlocked(data, crop)) {
            player.sendMessage(ChatColor.RED + "You haven't unlocked " + crop.getDisplay() + " yet.");
            return;
        }

        data.setSelectedCrop(crop.getId());
        farm.render(player);
        player.sendMessage(ChatColor.GREEN + "Your farm is now growing " + ChatColor.YELLOW + crop.getDisplay()
                + ChatColor.GREEN + ".");
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_CROP_PLANT, 0.8f, 1.2f);
        player.openInventory(CropsGui.build(plugin, player));
    }

    private void handleArmorClick(InventoryClickEvent event) {
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

    private void handlePrestigeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof PrestigeHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        PrestigeManager prestige = plugin.getPrestigeManager();
        int rawSlot = event.getRawSlot();

        if (rawSlot == PrestigeHolder.LEVEL_SLOT) {
            if (prestige.levelUp(data)) {
                plugin.getTagManager().refreshPrefix(player, data);
                player.sendMessage(ChatColor.GREEN + "Level up! You're now level " + data.getLevel() + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
                player.openInventory(PrestigeGui.build(plugin, player));
            } else {
                player.sendMessage(ChatColor.RED + "Keep rolling to level up.");
            }
        } else if (rawSlot == PrestigeHolder.PRESTIGE_SLOT) {
            if (prestige.prestige(data)) {
                plugin.getTagManager().refreshPrefix(player, data);
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Prestiged! " + ChatColor.RESET
                        + ChatColor.GRAY + "You're now [P" + data.getPrestige() + "] and back to level 1.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                player.openInventory(PrestigeGui.build(plugin, player));
            } else {
                player.sendMessage(ChatColor.RED + "Not enough levels to prestige yet.");
            }
        }
    }

    private void handleIndexClick(InventoryClickEvent event) {
        event.setCancelled(true); // collection log — clicking equips a tag or navigates, never moves items
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof IndexHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        if (rawSlot < 9) {
            handleIndexTopBar(holder, player, rawSlot);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        ItemMeta meta = clicked.getItemMeta();
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();
        NamespacedKey nameKey = plugin.getRollListener().getRollNameKey();
        String rarityName = meta.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        String rollName = meta.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        if (rarityName == null || rollName == null) return; // undiscovered entry

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        TagCommand.equip(plugin, player, data, rollName, rarityName);
    }

    private void handleIndexTopBar(IndexHolder holder, Player player, int rawSlot) {
        Rarity[] rarities = Rarity.values();
        if (rawSlot < rarities.length) {
            Rarity clicked = rarities[rawSlot];
            Rarity newFilter = holder.getFilter() == clicked ? null : clicked;
            player.openInventory(IndexGui.build(plugin, player, newFilter, 0));
        } else if (rawSlot == 6) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(), holder.getPage() - 1));
        } else if (rawSlot == 8) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(), holder.getPage() + 1));
        }
        // slot 7 is the Index Progress readout — no-op
    }

    private void handleSkillTreeClick(InventoryClickEvent event) {
        event.setCancelled(true); // whole GUI is view/click only, no item movement
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof SkillTreeHolder)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        NamespacedKey nodeIdKey = SkillTreeGui.nodeIdKey(plugin);
        ItemMeta meta = clicked.getItemMeta();
        String nodeId = meta.getPersistentDataContainer().get(nodeIdKey, PersistentDataType.STRING);
        if (nodeId == null) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        com.spacerng.solrng.player.SkillNode node = plugin.getSkillTreeManager().get(nodeId);
        boolean success = plugin.getSkillTreeManager().purchase(player, data, nodeId);
        if (success) {
            if (node != null && node.getMaxLevel() > 1) {
                player.sendMessage(ChatColor.GREEN + node.getDisplay() + " is now level "
                        + data.getNodeLevel(nodeId) + "/" + node.getMaxLevel() + "!");
            } else {
                player.sendMessage(ChatColor.GREEN + "Unlocked: " + (node != null ? node.getDisplay() : nodeId));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);

            if (nodeId.equals("farming_unlock")) {
                player.getInventory().addItem(plugin.getFarmingManager().createBoundHoe());
                player.sendMessage(ChatColor.GREEN + "You received a Farmer's Hoe — bound to you!");
            }

            player.openInventory(SkillTreeGui.build(plugin, player)); // refresh
        } else {
            player.sendMessage(ChatColor.RED + "You can't unlock that yet.");
        }
    }

    /**
     * Only rolled drops may enter the input rows. Anything else — the
     * Starforge, the Farmer's Hoe, armor, a stack of dirt — is bounced at
     * the door rather than being silently accepted and then having to be
     * handled by the converter.
     *
     * Every route an item can take into a container has to be covered
     * separately: placing from the cursor, shift-clicking from the player's
     * inventory, the 1-9 hotbar swap, the offhand (F) swap, and dragging.
     * Blocking only the obvious click leaves the other four wide open.
     */
    private boolean isDrop(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getItemMeta() == null) return false;
        if (plugin.getStarforgeManager().isStarforge(stack)) return false;

        var pdc = stack.getItemMeta().getPersistentDataContainer();
        // Both keys: only an item this plugin rolled carries a roll name
        // AND a rarity, which is what makes it convertible.
        return pdc.has(plugin.getRollListener().getRollNameKey(), PersistentDataType.STRING)
                && pdc.has(plugin.getRollListener().getRarityKey(), PersistentDataType.STRING);
    }

    private void rejectNonDrop(InventoryClickEvent event) {
        event.setCancelled(true);
        event.getWhoClicked().sendMessage(ChatColor.RED + "Only rolled drops can go in there.");
    }

    private void handleConvertClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();

        boolean clickedTopInventory = rawSlot >= 0 && rawSlot < top.getSize();
        boolean isInputSlot = clickedTopInventory && rawSlot <= 26;

        if (!clickedTopInventory) {
            // The player's own inventory. Shift-clicking is the one action
            // down here that pushes an item into the GUI.
            if (event.isShiftClick() && !isDrop(event.getCurrentItem())) {
                rejectNonDrop(event);
            }
            return;
        }

        if (isInputSlot) {
            switch (event.getClick()) {
                case NUMBER_KEY -> {
                    ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
                    // Empty hotbar slot = taking an item OUT, which is fine.
                    if (hotbar != null && hotbar.getType() != Material.AIR && !isDrop(hotbar)) {
                        rejectNonDrop(event);
                    }
                }
                case SWAP_OFFHAND -> {
                    ItemStack offhand = player.getInventory().getItemInOffHand();
                    if (offhand.getType() != Material.AIR && !isDrop(offhand)) {
                        rejectNonDrop(event);
                    }
                }
                default -> {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && cursor.getType() != Material.AIR && !isDrop(cursor)) {
                        rejectNonDrop(event);
                    }
                }
            }
            return; // otherwise let them place/remove drops freely
        }

        // Any other top-inventory slot (glass, confirm, toggles) is a button, not storage.
        event.setCancelled(true);

        if (rawSlot == ConvertHolder.CONFIRM_SLOT) {
            convertInputSlots(player, top);
            return;
        }

        if (rawSlot >= ConvertHolder.AUTO_TOGGLE_ROW_START && rawSlot <= ConvertHolder.AUTO_TOGGLE_ROW_END) {
            handleAutoToggleClick(player, rawSlot);
        }
    }

    /** Dragging spreads a stack across slots — same door, same lock. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ConvertHolder)) return;

        boolean touchesTop = false;
        boolean touchesNonInput = false;
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                touchesTop = true;
                if (slot > 26) touchesNonInput = true;
            }
        }
        if (!touchesTop) return;

        if (touchesNonInput || !isDrop(event.getOldCursor())) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED + "Only rolled drops can go in there.");
        }
    }

    /**
     * The input rows are a virtual inventory — anything still sitting in
     * them when the menu closes is destroyed with it. Hand it all back
     * instead; losing a Starforge to a stray Escape isn't acceptable.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof ConvertHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        for (int slot : ConvertHolder.INPUT_SLOTS) {
            ItemStack stack = top.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;

            top.setItem(slot, null);
            for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    /**
     * Converting banks each item as a stored drop of its own rarity
     * rather than paying Credits — a Common in the bank buys exactly what
     * a Common in the inventory buys, so /armor and /starforge stay the
     * sinks for rolled loot and Credits stay reserved for the paid store.
     */
    private void convertInputSlots(Player player, Inventory top) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        NamespacedKey rarityKey = plugin.getRollListener().getRarityKey();

        java.util.Map<Rarity, Long> banked = new java.util.EnumMap<>(Rarity.class);
        int itemsConverted = 0;

        for (int slot : ConvertHolder.INPUT_SLOTS) {
            ItemStack stack = top.getItem(slot);
            if (!isDrop(stack)) continue; // left alone, and handed back on close
            ItemMeta meta = stack.getItemMeta();

            String rarityName = meta.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
            if (rarityName == null) continue;

            try {
                Rarity rarity = Rarity.valueOf(rarityName);
                long amount = stack.getAmount();
                banked.merge(rarity, amount, Long::sum);
                itemsConverted += amount;
                data.addBankedDrops(rarity, amount);
                data.addConverted(rarity, amount);
                top.setItem(slot, null);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (itemsConverted == 0) {
            player.sendMessage(ChatColor.RED + "Place some rolled items in the top rows first.");
            return;
        }

        StringBuilder summary = new StringBuilder();
        for (java.util.Map.Entry<Rarity, Long> entry : banked.entrySet()) {
            if (summary.length() > 0) summary.append(ChatColor.GRAY).append(", ");
            summary.append(plugin.getRarityManager().style(entry.getKey(),
                    entry.getValue() + " " + entry.getKey().displayName()));
        }

        plugin.getScoreboardManager().update(player);
        player.openInventory(ConvertGui.build(plugin, player)); // refresh the Stored Drops panel
        player.sendMessage(ChatColor.GREEN + "Stored " + itemsConverted + " drop(s): " + summary);
    }

    private void handleAutoToggleClick(Player player, int rawSlot) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.hasUnlocked("auto_convert")) {
            player.sendMessage(ChatColor.RED + "Unlock 'Auto-Convert' in /skilltree first.");
            return;
        }

        int index = rawSlot - ConvertHolder.AUTO_TOGGLE_ROW_START;
        Rarity[] values = Rarity.values();
        if (index < 0 || index >= values.length) return;

        Rarity rarity = values[index];
        data.toggleAutoConvert(rarity);
        player.sendMessage(ChatColor.YELLOW + "Auto-convert for " + rarity.name() + " is now "
                + (data.isAutoConverting(rarity) ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
        player.openInventory(ConvertGui.build(plugin, player)); // refresh
    }
}
