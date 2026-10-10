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

    /** /secretindex (V291): a click on a found secret picks it as your Luck multiplier. */
    void handleSecretIndexClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof com.spacerng.solrng.gui.SecretIndexHolder holder)) return;
        Player stepper = (Player) event.getWhoClicked();
        // V311: the Index Mode block rides along here too, so the secret
        // index steps back to the shiny and normal ones without a command.
        if (event.getRawSlot() == com.spacerng.solrng.gui.SecretIndexGui.modeSlot()) {
            boolean back = event.isRightClick()
                    && !com.spacerng.solrng.platform.Bedrock.is(stepper);
            stepper.openInventory(com.spacerng.solrng.gui.IndexGui.build(plugin, stepper, null, 0,
                    com.spacerng.solrng.gui.IndexGui.Mode.SECRET.step(back)));
            return;
        }
        String id = holder.slots().get(event.getRawSlot());
        if (id == null || !plugin.getRealmManager().secretLuck()) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (!data.getSecretsFound().contains(id)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }
        data.setSelectedSecret(id);
        var secret = plugin.getRealmManager().secrets().get(id);
        // V337: the Luck is the best secret found and applies on its own,
        // so this only says what is worn in the realm.
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Wearing " + ChatColor.RESET
                + (secret == null ? id : com.spacerng.solrng.gui.Lore.gradient(secret.display(), true, secret.stops()))
                + ChatColor.GRAY + " in the realm. Your Luck is your best secret, "
                + ChatColor.GREEN + String.format("%.2f", plugin.getRealmManager().multiplierFor(data)) + "x"
                + ChatColor.GRAY + ".");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        plugin.getScoreboardManager().update(player);
        // V329: inside the realm the secret IS what you wear, so picking
        // another one has to redraw the tag over your head.
        if (plugin.getRealmManager().inside(player)) {
            plugin.getRealmManager().wearSecret(player, data);
        }
        player.openInventory(com.spacerng.solrng.gui.SecretIndexGui.build(plugin, player));
    }

    /** /potions: a click on a stored potion drinks one (V264). */
    void handleBoostersClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof com.spacerng.solrng.gui.BoostersHolder holder)) return;
        // V356: the corner button over to /brewer. Checked before the slot
        // map, which only knows the slots holding a potion.
        if (event.getRawSlot() == com.spacerng.solrng.gui.BoostersGui.BREWER_SLOT) {
            Player viewer = (Player) event.getWhoClicked();
            PlayerData viewerData = plugin.getPlayerDataManager().get(viewer.getUniqueId());
            if (!viewerData.hasUnlocked("potion_unlock")) {
                viewer.sendMessage(ChatColor.RED + "Unlock " + ChatColor.YELLOW + "Potions"
                        + ChatColor.RED + " in " + ChatColor.YELLOW + "/skilltree" + ChatColor.RED + " first.");
                return;
            }
            viewer.openInventory(com.spacerng.solrng.gui.PotionGui.build(plugin, viewer));
            return;
        }
        String id = holder.slots().get(event.getRawSlot());
        if (id == null) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var consumable = plugin.getConsumableManager().get(id);
        if (consumable == null || !data.getStoredBoosters().containsKey(id)) return;
        // V328: a shift click drinks the whole stack. Draughts stack their
        // rolls since V277, so twelve of them is one draught with twelve
        // times the rolls; drinking them one at a time was twelve clicks
        // and twelve chat lines. The first one goes through redeem so the
        // rules and the line are its own, and the rest are added quietly
        // and counted into one summary.
        if (event.isShiftClick() && consumable.isDraught()) {
            long stored = data.getStoredBoosters().getOrDefault(id, 0L);
            if (stored <= 0) return;
            if (!plugin.getConsumableManager().redeem(player, data, consumable)) return;
            data.takeStoredBooster(id);
            long more = 0;
            while (data.getStoredBoosters().getOrDefault(id, 0L) > 0) {
                data.addPotion(consumable.luck(), consumable.speed(), consumable.rolls());
                data.takeStoredBooster(id);
                more++;
            }
            if (more > 0) {
                player.sendMessage(ChatColor.AQUA + "Drank " + ChatColor.WHITE + (more + 1)
                        + ChatColor.AQUA + " of them" + ChatColor.GRAY + ", "
                        + ChatColor.WHITE + String.format("%,d",
                                data.getDraughtRolls(consumable.luck(), consumable.speed()))
                        + ChatColor.GRAY + " rolls left.");
            }
            player.openInventory(com.spacerng.solrng.gui.BoostersGui.build(plugin, player));
            return;
        }
        // Redeem first: a draught refused because another is running
        // stays stored.
        if (plugin.getConsumableManager().redeem(player, data, consumable)) {
            data.takeStoredBooster(id);
        }
        player.openInventory(com.spacerng.solrng.gui.BoostersGui.build(plugin, player));
    }

    void handleOptionsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof OptionsHolder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int rawSlot = event.getRawSlot();

        // V310: the three rarity ladders step forward on a left click and
        // back on a right one. Bedrock cannot send the difference (Geyser
        // reports every menu click as a left click), so there a click
        // always steps forward and the ladder's wrap-around is the only
        // way back. That is why Stepper.next wraps.
        boolean back = event.isRightClick()
                && !com.spacerng.solrng.platform.Bedrock.is(player);

        if (rawSlot == OptionsHolder.SOUND_SLOT) {
            data.setRollSoundEnabled(!data.isRollSoundEnabled());
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.ANIMATION_SLOT) {
            data.setRollAnimationStep(com.spacerng.solrng.gui.Stepper.next(
                    data.getRollAnimationStep(),
                    com.spacerng.solrng.gui.Stepper.steps(PlayerData.ANIMATION_FLOOR), back));
            stepped(player);
        } else if (rawSlot == OptionsHolder.AURA_STEP_SLOT) {
            OptionsGui.setAuraStep(data, com.spacerng.solrng.gui.Stepper.next(
                    OptionsGui.auraStep(data), OptionsGui.auraSteps(), back));
            stepped(player);
        } else if (rawSlot == OptionsHolder.SHOUT_STEP_SLOT) {
            OptionsGui.setShoutStep(data, com.spacerng.solrng.gui.Stepper.next(
                    OptionsGui.shoutStep(data), OptionsGui.auraSteps(), back));
            stepped(player);
        } else if (rawSlot == OptionsHolder.DROP_STEP_SLOT) {
            OptionsGui.setDropStep(data, com.spacerng.solrng.gui.Stepper.next(
                    OptionsGui.dropStep(data), OptionsGui.dropSteps(), back));
            stepped(player);
        } else if (rawSlot == OptionsHolder.WORN_AURA_SLOT) {
            data.setWornAurasVisible(!data.isWornAurasVisible());
            plugin.getAuraManager().refreshVisibility(player);
            player.openInventory(OptionsGui.build(plugin, player));
        } else if (rawSlot == OptionsHolder.OWN_AURA_SLOT) {
            data.cycleOwnAuraView();
            plugin.getAuraManager().refreshVisibility(player);
            player.openInventory(OptionsGui.build(plugin, player));
        }
    }

    /**
     * A step landed: redraw and click once. The pitch does not climb with
     * the step on purpose, because the ladder wraps and a rising pitch
     * would lie about where in it you are.
     */
    private void stepped(Player player) {
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
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

        // V353: the crop you are already growing is the one you can pour
        // Coins into. Clicking it a second time would otherwise do
        // nothing, so it opens that crop's own upgrade board instead.
        if (crop.getId().equals(data.getSelectedCrop())) {
            player.openInventory(com.spacerng.solrng.gui.CropBoostGui.build(plugin, player, crop.getId()));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
            return;
        }

        data.setSelectedCrop(crop.getId());
        farm.render(player);
        player.sendMessage(ChatColor.GREEN + "Your farm is now growing " + ChatColor.YELLOW + crop.getDisplay()
                + ChatColor.GREEN + ".");
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_CROP_PLANT, 0.8f, 1.2f);
        player.openInventory(CropsGui.build(plugin, player));
    }

    /**
     * The per-crop upgrade board. A plain click buys one level, a shift
     * click buys as many as the Coins stretch to, which is the same
     * contract the skill tree makes.
     */
    void handleCropBoostClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.CropBoostHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        if (event.getSlot() == com.spacerng.solrng.gui.CropBoostGui.backSlot()) {
            player.openInventory(CropsGui.build(plugin, player));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) return;
        String statId = clicked.getItemMeta().getPersistentDataContainer()
                .get(com.spacerng.solrng.gui.CropBoostGui.statKey(plugin), PersistentDataType.STRING);
        if (statId == null) return;

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var stat = com.spacerng.solrng.farming.CropBoosts.get(plugin, statId);
        if (stat == null) return;
        String cropId = holder.getCropId();

        int bought = 0;
        int limit = event.isShiftClick() ? stat.maxLevel() : 1;
        while (bought < limit
                && com.spacerng.solrng.farming.CropBoosts.buy(plugin, data, cropId, stat)) {
            bought++;
        }

        if (bought == 0) {
            int level = com.spacerng.solrng.farming.CropBoosts.levelOf(data, cropId, stat.id());
            player.sendMessage(level >= stat.maxLevel()
                    ? ChatColor.GREEN + stat.display() + " is already maxed on this crop."
                    : ChatColor.RED + "You need more Coins for that.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        player.sendMessage(ChatColor.GREEN + stat.display() + ChatColor.GRAY + " is now "
                + ChatColor.YELLOW + com.spacerng.solrng.farming.CropBoosts.levelOf(data, cropId, stat.id())
                + ChatColor.GRAY + " on this crop.");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
        player.openInventory(com.spacerng.solrng.gui.CropBoostGui.build(plugin, player, cropId));
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
            player.sendMessage(ChatColor.RED + "Auras need a linked Discord. Use /link.");
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

    /** The three worn slots on /pets (V366), where a click always takes a pet off. */
    private static boolean isWornSlot(int slot) {
        return slot >= 3 && slot <= 5;
    }

    /** /pets: put a pet in a slot, or take one out. */
    void handlePetsClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PetsHolder holder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var pets = plugin.getPetManager();
        var view = holder.view();

        // V215: the main screen opens storage and the index; both of those
        // come back to it with the arrow top left.
        if (view == com.spacerng.solrng.gui.PetsHolder.View.MAIN) {
            // V366: /pets is the storage and pages through it.
            if (event.getSlot() == com.spacerng.solrng.gui.PetsGui.PREV_SLOT
                    || event.getSlot() == com.spacerng.solrng.gui.PetsGui.NEXT_SLOT) {
                if (event.getCurrentItem() == null || event.getCurrentItem().getType().name().endsWith("PANE")) return;
                int to = holder.page() + (event.getSlot() == com.spacerng.solrng.gui.PetsGui.NEXT_SLOT ? 1 : -1);
                player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player, to));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
                return;
            }
            if (com.spacerng.solrng.gui.PetsGui.isStorageButton(event.getSlot())) {
                player.openInventory(com.spacerng.solrng.gui.PetsGui.storage(plugin, player));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
                return;
            }
            if (com.spacerng.solrng.gui.PetsGui.isIndexButton(event.getSlot())) {
                player.openInventory(com.spacerng.solrng.gui.PetsGui.index(plugin, player));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
                return;
            }
        } else if (com.spacerng.solrng.gui.PetsGui.isBack(event.getSlot())) {
            player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            return;
        }
        // V327: the index is one card per rarity, and the click switches
        // autotrash for every pet in it. With six pets to a rarity, "I
        // never want Commons" is what anybody actually means.
        if (view == com.spacerng.solrng.gui.PetsHolder.View.INDEX) {
            String rarityName = com.spacerng.solrng.gui.PetsGui.clickedRarity(event.getCurrentItem());
            if (rarityName == null) return;
            com.spacerng.solrng.rarity.Rarity rarity;
            try {
                rarity = com.spacerng.solrng.rarity.Rarity.valueOf(rarityName);
            } catch (IllegalArgumentException ex) {
                return;
            }
            boolean on = pets.toggleAutoTrash(data, rarity);
            player.sendMessage(on
                    ? ChatColor.RED + "Autotrash on for every "
                            + plugin.getRarityManager().style(rarity, rarity.displayName())
                            + ChatColor.GRAY + " pet. They are thrown away as they hatch."
                    : ChatColor.GREEN + "Autotrash off for "
                            + plugin.getRarityManager().style(rarity, rarity.displayName())
                            + ChatColor.GRAY + " pets.");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f,
                    on ? 0.8f : 1.5f);
            player.openInventory(com.spacerng.solrng.gui.PetsGui.index(plugin, player));
            return;
        }

        // V359: a click on an egg opens that egg's own screen, where the
        // seven pets in it, their chances and the 1 / 3 / 9 buttons live.
        // It used to buy one on the spot, which gave a player no way to
        // see what was in an egg before paying for it.
        String eggId = com.spacerng.solrng.gui.PetsGui.clickedEgg(event.getCurrentItem());
        if (eggId != null) {
            player.openInventory(com.spacerng.solrng.gui.PetEggGui.build(plugin, player, eggId));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
            return;
        }

        String id = com.spacerng.solrng.gui.PetsGui.clickedPet(event.getCurrentItem());
        if (id == null) return;

        var pet = pets.get(id);
        if (pet == null) return;

        // V324: a shift click in storage throws one spare copy away. The
        // copy that IS the pet never goes, so this cannot lose a pet.
        boolean inStorage = view == com.spacerng.solrng.gui.PetsHolder.View.STORAGE
                || view == com.spacerng.solrng.gui.PetsHolder.View.MAIN;
        if (event.isShiftClick() && inStorage) {
            if (pets.trashCopy(data, pet.id())) {
                player.sendMessage(ChatColor.GRAY + "Threw away one spare "
                        + com.spacerng.solrng.gui.Lore.gradient(pet.display(), true, pet.stops())
                        + ChatColor.GRAY + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.6f, 1.2f);
            } else {
                player.sendMessage(ChatColor.GRAY + "You have no spare copy of that pet.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            }
            player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player, holder.page()));
            return;
        }

        // A right click opens the pet for upgrading instead of wearing it.
        // Bedrock cannot right click in a menu (V223), so any click in
        // storage opens it there, and the pet screen has a wear button for
        // them. The worn slots on the main screen still take a pet off.
        if ((event.isRightClick() || (com.spacerng.solrng.platform.Bedrock.is(player)
                        && inStorage && !isWornSlot(event.getSlot())))
                && data.ownsPet(pet.id())) {
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
        player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player, holder.page()));
    }

    /**
     * One egg's own screen (V359): the 1 / 3 / 9 buttons, the auto open
     * switch and the way back.
     *
     * Auto open keeps buying after the click lands, so a player with it
     * on presses once and the batch runs until the Gems, the storage or
     * the egg itself stops it. The reason it stopped is always said out
     * loud, because a click that quietly does nothing is the worst
     * answer a menu can give.
     */
    void handlePetEggClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder()
                        instanceof com.spacerng.solrng.gui.PetEggHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        var pets = plugin.getPetManager();
        var egg = pets.upgrades().egg(holder.eggId());
        int slot = event.getRawSlot();

        if (slot == com.spacerng.solrng.gui.PetEggGui.BACK_SLOT) {
            player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player));
            return;
        }

        if (slot == com.spacerng.solrng.gui.PetEggGui.ANIMATION_SLOT) {
            boolean on = data.togglePetEggAnimation();
            player.sendMessage(on ? ChatColor.GREEN + "Egg animation is on."
                    : ChatColor.RED + "Egg animation is off. " + ChatColor.GRAY + "Eggs open straight away.");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, on ? 1.5f : 0.8f);
            player.openInventory(com.spacerng.solrng.gui.PetEggGui.build(plugin, player, holder.eggId()));
            return;
        }

        if (slot == com.spacerng.solrng.gui.PetEggGui.AUTO_SLOT) {
            if (!plugin.getLinkedAccountManager().isLinked(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Auto open is for linked accounts. "
                        + ChatColor.GRAY + "Run " + ChatColor.YELLOW + "/link"
                        + ChatColor.GRAY + " first.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            boolean on = data.togglePetAutoOpen();
            player.sendMessage((on ? ChatColor.GREEN + "Auto open is on. "
                    + ChatColor.GRAY + "A click keeps buying until the Gems or the room run out."
                    : ChatColor.RED + "Auto open is off."));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, on ? 1.5f : 0.8f);
            player.openInventory(com.spacerng.solrng.gui.PetEggGui.build(plugin, player, holder.eggId()));
            return;
        }

        // V366: the egg row and its arrows switch egg, and a found pet's
        // card switches auto delete for it.
        String tab = com.spacerng.solrng.gui.PetEggGui.clickedTab(event.getCurrentItem());
        if (tab != null) {
            player.openInventory(com.spacerng.solrng.gui.PetEggGui.build(plugin, player, tab));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
            return;
        }
        String trash = com.spacerng.solrng.gui.PetEggGui.clickedTrash(event.getCurrentItem());
        if (trash != null) {
            var type = pets.get(trash);
            boolean on = data.togglePetAutoTrash(trash);
            if (type != null) {
                player.sendMessage((on ? ChatColor.RED + "Auto delete on for " : ChatColor.GREEN + "Keeping ")
                        + ChatColor.RESET + com.spacerng.solrng.gui.Lore.gradient(type.display(), true, type.stops())
                        + ChatColor.GRAY + (on ? ". New copies are thrown away as they hatch." : " again."));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, on ? 0.8f : 1.5f);
            player.openInventory(com.spacerng.solrng.gui.PetEggGui.build(plugin, player, holder.eggId()));
            return;
        }

        int count = com.spacerng.solrng.gui.PetEggGui.clickedBuy(event.getCurrentItem());
        if (count <= 0 || egg == null) return;
        if (com.spacerng.solrng.pet.PetOpenShow.isRunning(player)) return;

        if (data.getPrestige() < egg.minPrestige()) {
            player.sendMessage(ChatColor.RED + "The " + ChatColor.stripColor(egg.display())
                    + " opens at Prestige " + egg.minPrestige() + ".");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // Auto open turns the button into "keep going": the number on it
        // is the batch, and it repeats while there is anything to spend.
        int wanted = count;
        if (data.isPetAutoOpen() && plugin.getLinkedAccountManager().isLinked(player.getUniqueId())) {
            long affordable = egg.cost() <= 0 ? 0 : data.getShards() / egg.cost();
            int room = pets.storage() - pets.held(data);
            wanted = (int) Math.max(count, Math.min(affordable, Math.max(0, room)));
            wanted = Math.min(wanted, AUTO_OPEN_CAP);
        }

        var batch = pets.makeMany(data, egg, wanted);
        if (batch.opened() == 0) {
            player.sendMessage(switch (batch.stop()) {
                case STORAGE_FULL -> ChatColor.RED + "Your pet storage is full, " + pets.held(data)
                        + " of " + pets.storage() + ". " + ChatColor.GRAY + "Nothing opens until you make room.";
                case OUT_OF_GEMS -> ChatColor.RED + "Not enough Gems. " + ChatColor.GRAY + "One "
                        + ChatColor.stripColor(egg.display()) + " is "
                        + String.format("%,d", egg.cost()) + ".";
                default -> ChatColor.RED + "That egg cannot open right now.";
            });
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // V362: Leon's opening, the heads that shake and turn into the
        // pets. The menu closes so the row is in view; the chat lines land
        // on the reveal frame. Bedrock cannot draw the displays and keeps
        // the old instant answer with the menu reopened.
        boolean show = !com.spacerng.solrng.platform.Bedrock.is(player)
                && data.isPetEggAnimation()
                && plugin.getConfig().getBoolean("pets.eggs.animation", true);
        if (show) {
            player.closeInventory();
            com.spacerng.solrng.pet.PetOpenShow.start(plugin, player, batch.made(),
                    () -> tellOpened(player, data, batch));
            plugin.getPetManager().refresh(player);
            plugin.getScoreboardManager().update(player);
            return;
        }
        tellOpened(player, data, batch);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.7f, batch.best().type().rarity().ordinal() >= 4 ? 1.8f : 1.2f);
        plugin.getPetManager().refresh(player);
        plugin.getScoreboardManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.PetEggGui.build(plugin, player, holder.eggId()));
    }

    /** The chat lines for an opened batch: what came out and why it stopped. */
    private void tellOpened(Player player, PlayerData data, com.spacerng.solrng.pet.PetManager.Batch batch) {
        var pets = plugin.getPetManager();
        var best = batch.best();
        String shown = com.spacerng.solrng.gui.Lore.gradient(
                best.type().display(), true, best.type().stops());
        if (batch.opened() == 1) {
            player.sendMessage((best.isNew() ? ChatColor.GREEN + "A new pet: " : ChatColor.GRAY + "Another ")
                    + ChatColor.RESET + shown + ChatColor.GRAY + ", "
                    + plugin.getRarityManager().style(best.type().rarity(),
                            best.type().rarity().displayName()) + ChatColor.GRAY + ".");
        } else {
            player.sendMessage(ChatColor.GREEN + "Opened " + ChatColor.WHITE + batch.opened()
                    + ChatColor.GREEN + ", " + ChatColor.WHITE + batch.fresh()
                    + ChatColor.GREEN + " new. " + ChatColor.GRAY + "Best: " + ChatColor.RESET + shown
                    + ChatColor.GRAY + ".");
        }
        if (batch.stop() == com.spacerng.solrng.pet.PetManager.Stop.STORAGE_FULL) {
            player.sendMessage(ChatColor.GRAY + "It stopped there: storage is full at "
                    + pets.storage() + ".");
        } else if (batch.stop() == com.spacerng.solrng.pet.PetManager.Stop.OUT_OF_GEMS) {
            player.sendMessage(ChatColor.GRAY + "It stopped there: out of Gems.");
        }
    }

    /**
     * The most eggs one auto click will open.
     *
     * Not a balance number: it is there so a player sitting on billions
     * of Gems cannot ask the server for a hundred thousand rolls inside
     * one click and freeze the tick while it runs.
     */
    private static final int AUTO_OPEN_CAP = 256;

    /**
     * The next stat in the list, wrapping at the end (V359).
     *
     * One direction only, because Geyser cannot tell a left click from a
     * right one in a menu, and a list you can only walk forward through
     * still reaches every entry as long as it comes back round.
     */
    private com.spacerng.solrng.stats.StatSources.Id nextStat(PlayerData data,
                                                              com.spacerng.solrng.pet.PetType type) {
        var values = com.spacerng.solrng.stats.StatSources.Id.values();
        var pet = data.getPet(type.id());
        var current = pet == null ? type.stat() : pet.statOr(type);
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    /**
     * The wear button on the pet screen, which only Bedrock players get
     * (V223): on Java a left click on the pets screen wears a pet, and
     * Bedrock cannot tell that click from the right click that opens it.
     */
    private void wearFromUpgrade(Player player, PlayerData data, com.spacerng.solrng.pet.PetType type) {
        var pets = plugin.getPetManager();
        String shown = com.spacerng.solrng.gui.Lore.gradient(type.display(), true, type.stops());
        switch (pets.toggle(data, type)) {
            case EQUIPPED -> {
                player.sendMessage(ChatColor.GREEN + "Wearing " + ChatColor.RESET + shown
                        + ChatColor.GREEN + ". " + ChatColor.GRAY + type.boostText() + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
            }
            case UNEQUIPPED -> {
                player.sendMessage(ChatColor.GRAY + "Took off " + ChatColor.RESET + shown + ChatColor.GRAY + ".");
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
        pets.refresh(player);
        plugin.getScoreboardManager().update(player);
        player.openInventory(com.spacerng.solrng.gui.PetUpgradeGui.build(plugin, player, type.id()));
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
            player.openInventory(com.spacerng.solrng.gui.PetsGui.storage(plugin, player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
            return;
        }

        String action = com.spacerng.solrng.gui.PetUpgradeGui.clickedAction(event.getCurrentItem());
        if (action == null) return;
        var pets = plugin.getPetManager();
        var type = pets.get(holder.getPetId());
        if (type == null) return;

        if ("wear".equals(action)) {
            wearFromUpgrade(player, data, type);
            return;
        }

        var result = switch (action) {
            case "level" -> pets.upgradeLevel(data, type.id());
            // V359: the stat steps forward and wraps, the one shape that
            // works without a right click, so Bedrock reaches every stat.
            case "stat" -> pets.setStat(data, type.id(), nextStat(data, type));
            case "shiny" -> pets.makeShiny(data, type.id());
            default -> null;
        };
        if (result == null) return;

        String shown = com.spacerng.solrng.gui.Lore.gradient(type.display(), true, type.stops());
        switch (result) {
            case DONE -> {
                var pet = data.getPet(type.id());
                String what = switch (action) {
                    case "level" -> "is now level " + (pet == null ? "?" : pet.level());
                    case "stat" -> "boosts " + com.spacerng.solrng.pet.PetType.statName(
                            pet == null ? type.stat() : pet.statOr(type)) + " now";
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

        if (event.getRawSlot() == HoeGui.farmTreeSlot()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            player.openInventory(SkillTreeGui.build(plugin, player, "farmtree", 0));
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
        var enchant = hoe.get(id);
        if (enchant != null && enchant.comingSoon()) {
            player.sendMessage(ChatColor.GRAY + "That one is not finished yet.");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            return;
        }
        if (!hoe.isUnlocked(data, id)) {
            // V186: take them there rather than telling them where it is.
            // A locked slot that answers with a sentence is a dead end.
            player.sendMessage(ChatColor.GRAY + "That enchant is unlocked in "
                    + ChatColor.YELLOW + "/farmtree" + ChatColor.GRAY + ".");
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            player.openInventory(SkillTreeGui.build(plugin, player, "farmtree", 0));
            return;
        }
        // Right-click switches an owned enchant on or off (V266), the same
        // as the switch in its level screen, which Bedrock players use.
        if (event.isRightClick() && hoe.levelOf(data, id) > 0) {
            boolean on = data.toggleEnchant(id);
            player.sendMessage(enchant.colour() + enchant.display() + (on
                    ? ChatColor.GREEN + " is switched on." : ChatColor.RED + " is switched off."));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, on ? 1.5f : 0.8f);
            plugin.getFarmingManager().refreshHoe(player, data);
            player.openInventory(HoeGui.build(plugin, player));
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

        if (slot == com.spacerng.solrng.gui.EnchantBuyHolder.MESSAGE_SLOT) {
            var muted = plugin.getHoeEnchantManager().get(holder.getEnchantId());
            if (muted == null || plugin.getHoeEnchantManager().levelOf(data, muted.id()) <= 0) return;
            boolean shown = data.toggleEnchantMessage(muted.id());
            player.sendMessage(muted.colour() + muted.display() + (shown
                    ? ChatColor.GREEN + " messages are shown." : ChatColor.RED + " messages are hidden."));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, shown ? 1.5f : 0.8f);
            player.openInventory(com.spacerng.solrng.gui.EnchantBuyGui.build(plugin, player,
                    muted.id(), holder.getTree(), holder.getPage()));
            return;
        }
        if (slot == com.spacerng.solrng.gui.EnchantBuyHolder.TOGGLE_SLOT) {
            var toggled = plugin.getHoeEnchantManager().get(holder.getEnchantId());
            if (toggled == null || plugin.getHoeEnchantManager().levelOf(data, toggled.id()) <= 0) return;
            boolean on = data.toggleEnchant(toggled.id());
            player.sendMessage(toggled.colour() + toggled.display() + (on
                    ? ChatColor.GREEN + " is switched on." : ChatColor.RED + " is switched off."));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f, on ? 1.5f : 0.8f);
            plugin.getFarmingManager().refreshHoe(player, data);
            player.openInventory(com.spacerng.solrng.gui.EnchantBuyGui.build(plugin, player,
                    toggled.id(), holder.getTree(), holder.getPage()));
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

    void handleIndexClick(InventoryClickEvent event) {
        event.setCancelled(true); // collection log - clicking equips a tag or navigates, never moves items
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof IndexHolder holder)) return;

        Player player = (Player) event.getWhoClicked();
        int rawSlot = event.getRawSlot();

        // V311: two ladders on the top row, stepped the same way /options
        // steps. Bedrock has no right click of its own, so it only ever
        // steps forward and relies on the wrap, exactly as in /options.
        boolean back = event.isRightClick()
                && !com.spacerng.solrng.platform.Bedrock.is(player);

        // Page buttons first: they sit on the divider row, clear of the
        // top row, and they have to win regardless of what else is there.
        // V330: one button per collection. A click opens that one; the
        // one you are in does nothing.
        IndexGui.Mode wanted = IndexGui.modeAt(rawSlot);
        if (wanted != null) {
            if (wanted == holder.getMode()) return;
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(), 0, wanted));
            return;
        }
        if (rawSlot == IndexGui.raritySlot()) {
            player.openInventory(IndexGui.build(plugin, player,
                    IndexGui.stepFilter(holder.getFilter(), back), 0, holder.getMode()));
            return;
        }
        if (rawSlot == IndexGui.prevSlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(),
                    Math.max(0, holder.getPage() - 1), holder.getMode()));
            return;
        }
        if (rawSlot == IndexGui.nextSlot()) {
            player.openInventory(IndexGui.build(plugin, player, holder.getFilter(),
                    holder.getPage() + 1, holder.getMode()));
            return;
        }
        if (rawSlot < 18) return; // the rail and the divider carry nothing else

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
