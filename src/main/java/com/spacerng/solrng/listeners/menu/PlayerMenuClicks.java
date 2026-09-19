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

/** Clicks in the menus about the player: options, stats, the index, crops and the hoe. */
final class PlayerMenuClicks {

    private final SolRNGPlugin plugin;

    PlayerMenuClicks(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    void handleOptionsClick(InventoryClickEvent event) {
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
        } else if (rawSlot == OptionsHolder.WORN_AURA_SLOT) {
            data.setWornAurasVisible(!data.isWornAurasVisible());
            plugin.getAuraManager().refreshVisibility(player);
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.OWN_AURA_SLOT) {
            data.cycleOwnAuraView();
            plugin.getAuraManager().refreshVisibility(player);
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.AURA_EPIC_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.EPIC);
        } else if (rawSlot == OptionsHolder.AURA_LEGENDARY_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.LEGENDARY);
        } else if (rawSlot == OptionsHolder.AURA_MYTHICAL_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.MYTHICAL);
        } else if (rawSlot == OptionsHolder.AURA_DIVINE_SLOT) {
            toggleAura(player, data, com.spacerng.solrng.rarity.Rarity.DIVINE);
        } else if (rawSlot == OptionsHolder.SHOUT_EPIC_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.EPIC);
        } else if (rawSlot == OptionsHolder.SHOUT_LEGENDARY_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.LEGENDARY);
        } else if (rawSlot == OptionsHolder.SHOUT_MYTHICAL_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.MYTHICAL);
        } else if (rawSlot == OptionsHolder.SHOUT_DIVINE_SLOT) {
            toggleShout(player, data, com.spacerng.solrng.rarity.Rarity.DIVINE);
        } else if (rawSlot >= OptionsHolder.DROP_COMMON_SLOT
                && rawSlot <= OptionsHolder.DROP_DIVINE_SLOT) {
            var rarity = com.spacerng.solrng.rarity.Rarity.values()
                    [rawSlot - OptionsHolder.DROP_COMMON_SLOT];
            data.setDropMessageEnabled(rarity, !data.isDropMessageEnabled(rarity));
            player.openInventory(OptionsGui.build(plugin, player));
        }
    }

    void toggleShout(Player player, PlayerData data,
                             com.spacerng.solrng.rarity.Rarity rarity) {
        data.setBroadcastEnabled(rarity, !data.isBroadcastEnabled(rarity));
        player.openInventory(OptionsGui.build(plugin, player));
    }

    /** Read-only: open one stat's breakdown, or come back from it. */
    void handleStatsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.StatsHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;

        if (holder.getOpen() != null) {
            if (clicked.getType() == org.bukkit.Material.ARROW) {
                player.openInventory(com.spacerng.solrng.gui.StatsGui.overview(
                        plugin, holder.getTarget(), holder.getTargetName()));
            }
            return;
        }

        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.StatsGui.statKey(plugin), PersistentDataType.STRING);
        if (id == null) return;
        try {
            player.openInventory(com.spacerng.solrng.gui.StatsGui.breakdown(plugin,
                    holder.getTarget(), holder.getTargetName(),
                    com.spacerng.solrng.stats.StatSources.Id.valueOf(id)));
        } catch (IllegalArgumentException ignored) {
            // A stat that no longer exists - the menu is stale, not broken.
        }
    }

    /** Picking a crop repaints the shared farm for this player only. */
    void handleCropsClick(InventoryClickEvent event) {
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

    /** /aura: pick a look, or go back to following the tag. */
    void handleAuraClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.AuraHolder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        String choice = com.spacerng.solrng.gui.AuraGui.clickedChoice(event.getCurrentItem());
        if (choice == null) return;

        if (plugin.getRankManager().rankOf(data) == null
                && plugin.getConfig().getBoolean("auras.require-linked", true)) {
            player.sendMessage(ChatColor.RED + "Auras need a linked Discord. Use /discord link.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        if (!choice.isEmpty() && !plugin.getAuraManager().owns(data, choice)) {
            player.sendMessage(ChatColor.RED + "You have not found that one yet.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        // Clicking the one already worn puts it back to following the tag.
        String wanted = choice.equalsIgnoreCase(data.getAuraChoice()) ? "" : choice;
        data.setAuraChoice(wanted);
        plugin.getAuraManager().hide(player.getUniqueId());
        plugin.getAuraManager().applyTag(player);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.4f);
        player.openInventory(com.spacerng.solrng.gui.AuraGui.build(plugin, player));
    }

    /** /pets: put a pet in a slot, or take one out. */
    void handlePetsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PetsHolder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var pets = plugin.getPetManager();

        // The forge star: ten Cosmic Dust becomes a pet, or a rarity on one
        // you already have.
        if (com.spacerng.solrng.gui.PetsGui.clickedForge(event.getCurrentItem())) {
            var made = pets.make(data);
            if (!made.happened()) {
                player.sendMessage(ChatColor.RED + "Not enough Cosmic Dust, or every pet is already maxed.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            String shown = com.spacerng.solrng.gui.Lore.gradient(
                    made.type().display(), true, made.type().stops());
            if (made.isNew()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "A new pet took shape: " + ChatColor.RESET
                        + shown + ChatColor.GRAY + ".");
            } else {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "The dust went into " + ChatColor.RESET + shown
                        + ChatColor.GRAY + ", now rarity " + made.pet().rarity() + ".");
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.4f);
            plugin.getPetManager().refresh(player);
            plugin.getScoreboardManager().update(player);
            player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player));
            return;
        }

        String id = com.spacerng.solrng.gui.PetsGui.clickedPet(event.getCurrentItem());
        if (id == null) return;

        var pet = pets.get(id);
        if (pet == null) return;

        // A right click opens the pet for upgrading instead of wearing it.
        if (event.isRightClick() && data.ownsPet(pet.id())) {
            player.openInventory(com.spacerng.solrng.gui.PetUpgradeGui.build(plugin, player, pet.id()));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
            return;
        }

        switch (pets.toggle(data, pet)) {
            case EQUIPPED -> {
                player.sendMessage(ChatColor.GREEN + "Wearing " + ChatColor.RESET
                        + com.spacerng.solrng.gui.Lore.gradient(pet.display(), true, pet.stops())
                        + ChatColor.GREEN + ". " + ChatColor.GRAY + pet.boostText() + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
            }
            case UNEQUIPPED -> {
                player.sendMessage(ChatColor.GRAY + "Took off " + ChatColor.RESET
                        + com.spacerng.solrng.gui.Lore.gradient(pet.display(), true, pet.stops())
                        + ChatColor.GRAY + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.4f);
            }
            case FULL -> {
                player.sendMessage(ChatColor.RED + "Your " + pets.slots(data)
                        + " slot(s) are full. Take one off first.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            case LOCKED -> {
                player.sendMessage(ChatColor.RED + "You have not found that pet yet.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
        }
        // The pieces are part of the aura, so the aura is what rebuilds.
        plugin.getPetManager().refresh(player);
        plugin.getScoreboardManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player));
    }

    /**
     * The pet upgrade menu: rarity, tier and shiny.
     *
     * A failed tier attempt is the one outcome here that costs something
     * and gives nothing back, so it gets its own line and its own sound.
     * Being told plainly that it failed is the difference between a
     * gamble and a bug report.
     */
    void handlePetUpgradeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PetUpgradeHolder holder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        if (com.spacerng.solrng.gui.PetUpgradeGui.isBack(event.getSlot())) {
            player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            return;
        }

        String action = com.spacerng.solrng.gui.PetUpgradeGui.clickedAction(event.getCurrentItem());
        if (action == null) return;
        var pets = plugin.getPetManager();
        var type = pets.get(holder.getPetId());
        if (type == null) return;

        var result = switch (action) {
            case "rarity" -> pets.upgradeRarity(data, type.id());
            case "tier" -> pets.upgradeTier(data, type.id());
            case "shiny" -> pets.makeShiny(data, type.id());
            default -> null;
        };
        if (result == null) return;

        String shown = com.spacerng.solrng.gui.Lore.gradient(type.display(), true, type.stops());
        switch (result) {
            case DONE -> {
                var pet = data.getPet(type.id());
                String what = switch (action) {
                    case "rarity" -> "is now rarity " + (pet == null ? "?" : pet.rarity());
                    case "tier" -> "is now tier " + (pet == null ? "?" : pet.tier());
                    default -> "is shiny";
                };
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.RESET + shown
                        + ChatColor.GREEN + " " + what + ".");
                player.playSound(player.getLocation(),
                        "shiny".equals(action)
                                ? org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME
                                : org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.5f);
            }
            case FAILED -> {
                player.sendMessage(ChatColor.RED + "The tier did not take. " + ChatColor.GRAY
                        + "The Farm Dust is spent.");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.8f);
            }
            case TOO_POOR -> {
                player.sendMessage(ChatColor.RED + "You do not have the dust for that.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
            case MAXED -> {
                player.sendMessage(ChatColor.GRAY + "That is already as far as it goes.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.6f, 1.2f);
            }
            case NO_SHINY -> {
                player.sendMessage(ChatColor.RED + "Find a shiny " + type.rarity().displayName()
                        + ChatColor.RED + " before you can make this one shiny.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
            case LOCKED -> {
                player.sendMessage(ChatColor.RED + "You do not own that pet.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        }
        // A worn pet that just changed has to be redrawn, and /stats reads
        // the new number straight away.
        plugin.getPetManager().refresh(player);
        plugin.getScoreboardManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.PetUpgradeGui.build(plugin, player, type.id()));
    }

    void handleHoeClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof HoeHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // The tool card at the top is the tier button.
        if (event.getRawSlot() == 4) {
            var farming = plugin.getFarmingManager();
            var next = farming.nextTier(data);
            if (next == null) {
                player.sendMessage(ChatColor.GRAY + "Your hoe is at the top of the ladder.");
            } else if (farming.purchaseTier(player, data)) {
                player.sendMessage(ChatColor.GREEN + "Hoe upgraded to "
                        + ChatColor.YELLOW + farming.tierOf(data).display() + ChatColor.GREEN + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
            } else {
                StringBuilder price = new StringBuilder();
                for (var cost : next.costs().entrySet()) {
                    if (price.length() > 0) price.append(", ");
                    price.append(cost.getValue()).append(" ").append(cost.getKey().displayName());
                }
                player.sendMessage(ChatColor.RED + "The next tier costs " + price + ".");
            }
            player.openInventory(com.spacerng.solrng.gui.HoeGui.build(plugin, player));
            return;
        }

        if (event.getRawSlot() == HoeGui.hidePlayersSlot()) {
            data.setFarmHidePlayers(!data.isFarmHidePlayers());
            player.openInventory(HoeGui.build(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f,
                    data.isFarmHidePlayers() ? 1.5f : 0.8f);
            return;
        }
        if (event.getRawSlot() == HoeGui.farmSoundSlot()) {
            data.setFarmSoundEnabled(!data.isFarmSoundEnabled());
            player.openInventory(HoeGui.build(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f,
                    data.isFarmSoundEnabled() ? 1.5f : 0.8f);
            return;
        }
        if (event.getRawSlot() == HoeGui.enchantSoundSlot()) {
            data.setEnchantSoundEnabled(!data.isEnchantSoundEnabled());
            player.openInventory(HoeGui.build(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f,
                    data.isEnchantSoundEnabled() ? 1.5f : 0.8f);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        String id = clicked.getItemMeta().getPersistentDataContainer()
                .get(HoeGui.enchantKey(plugin), PersistentDataType.STRING);
        if (id == null) return;

        var hoe = plugin.getHoeEnchantManager();
        if (!hoe.isUnlocked(data, id)) {
            player.sendMessage(ChatColor.RED + "Unlock that enchant in /farmtree first.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        // Levels are bought in their own screen now (V161): +1, +10, +100 or max.
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.3f);
        player.openInventory(com.spacerng.solrng.gui.EnchantBuyGui.build(plugin, player, id, null, 0));
    }

    /**
     * The enchant level screen, opened from the hoe menu or /farmtree.
     * Each button buys what it quoted; back returns to where it came from.
     */
    void handleEnchantBuyClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getView().getTopInventory().getHolder()
                instanceof com.spacerng.solrng.gui.EnchantBuyHolder holder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int slot = event.getRawSlot();

        if (slot == com.spacerng.solrng.gui.EnchantBuyHolder.BACK_SLOT) {
            player.openInventory(holder.getTree() == null
                    ? HoeGui.build(plugin, player)
                    : com.spacerng.solrng.gui.SkillTreeGui.build(plugin, player, holder.getTree(), holder.getPage()));
            return;
        }

        int wanted;
        if (slot == com.spacerng.solrng.gui.EnchantBuyHolder.ONE_SLOT) wanted = 1;
        else if (slot == com.spacerng.solrng.gui.EnchantBuyHolder.TEN_SLOT) wanted = 10;
        else if (slot == com.spacerng.solrng.gui.EnchantBuyHolder.HUNDRED_SLOT) wanted = 100;
        else if (slot == com.spacerng.solrng.gui.EnchantBuyHolder.MAX_SLOT) wanted = 10_000;
        else return;

        var hoe = plugin.getHoeEnchantManager();
        var enchant = hoe.get(holder.getEnchantId());
        if (enchant == null) return;
        // +10 and +100 are all or nothing, so the button never buys fewer
        // levels than it said at the price it quoted. Max buys what it can.
        if (wanted > 1 && wanted < 10_000) {
            long[] quote = hoe.priceOfNext(data, enchant, wanted);
            if (quote[0] == 0 || data.getTokens() < quote[1]) {
                player.sendMessage(ChatColor.RED + "Not enough Coins for that.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
        }
        int bought = hoe.buyMany(data, enchant.id(), wanted);
        if (bought == 0) {
            player.sendMessage(ChatColor.RED + (hoe.levelOf(data, enchant.id()) >= hoe.maxLevelFor(data, enchant)
                    ? "That enchant is at its cap." : "Not enough Coins for that."));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Upgraded " + enchant.colour() + enchant.display()
                + ChatColor.GREEN + " to level " + ChatColor.WHITE + String.format("%,d", hoe.levelOf(data, enchant.id()))
                + ChatColor.GREEN + " (+" + bought + ")");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
        // The hoe's lore is a snapshot, so it has to be rewritten whenever
        // what it describes changes.
        plugin.getFarmingManager().refreshHoe(player, data);
        plugin.getScoreboardManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.EnchantBuyGui.build(plugin, player,
                enchant.id(), holder.getTree(), holder.getPage()));
    }

    void toggleAura(Player player, PlayerData data, com.spacerng.solrng.rarity.Rarity rarity) {
        data.setAuraEnabled(rarity, !data.isAuraEnabled(rarity));
        player.openInventory(OptionsGui.build(plugin, player));
    }

    void handleIndexClick(InventoryClickEvent event) {
        event.setCancelled(true); // collection log - clicking equips a tag or navigates, never moves items
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof IndexHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        // Page buttons first: they sit on the divider row, clear of the
        // tab bar, and they have to win regardless of what else is there.
        if (rawSlot == IndexGui.shinySlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(), 0,
                    !holder.isShinyView()));
            return;
        }
        if (rawSlot == IndexGui.prevSlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(),
                    Math.max(0, holder.getPage() - 1), holder.isShinyView()));
            return;
        }
        if (rawSlot == IndexGui.nextSlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(),
                    holder.getPage() + 1, holder.isShinyView()));
            return;
        }
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

    /** Slots 0 to 6 are the rarity tabs. 7 is a spacer, 8 is your head. */
    void handleIndexTopBar(IndexHolder holder, Player player, int rawSlot) {
        Rarity[] rarities = Rarity.values();
        if (rawSlot >= rarities.length) return;

        Rarity clicked = rarities[rawSlot];
        Rarity newFilter = holder.getFilter() == clicked ? null : clicked;
        player.openInventory(IndexGui.build(plugin, player, newFilter, 0, holder.isShinyView()));
    }

    /** /stash: click a stack to take it, or the hopper to take as much as fits. */
    void handleStashClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof com.spacerng.solrng.gui.StashHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        java.util.List<ItemStack> stash = data.getStash();
        int slot = event.getRawSlot();
        boolean full = false;

        if (slot == com.spacerng.solrng.gui.StashGui.TAKE_ALL_SLOT) {
            java.util.List<ItemStack> kept = new java.util.ArrayList<>();
            for (ItemStack stack : stash) {
                if (full) {
                    kept.add(stack);
                    continue;
                }
                java.util.Map<Integer, ItemStack> left = player.getInventory().addItem(stack.clone());
                if (!left.isEmpty()) {
                    kept.addAll(left.values());
                    full = true;
                }
            }
            stash.clear();
            stash.addAll(kept);
        } else if (slot >= 0 && slot < com.spacerng.solrng.gui.StashGui.ITEM_SLOTS && slot < stash.size()) {
            java.util.Map<Integer, ItemStack> left = player.getInventory().addItem(stash.get(slot).clone());
            if (left.isEmpty()) {
                stash.remove(slot);
            } else {
                stash.set(slot, left.values().iterator().next());
                full = true;
            }
        } else {
            return;
        }

        if (full) {
            player.sendMessage(ChatColor.RED + "Your inventory is full. The rest stays in your stash.");
        } else {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        }
        player.openInventory(com.spacerng.solrng.gui.StashGui.build(plugin, player));
    }
}
