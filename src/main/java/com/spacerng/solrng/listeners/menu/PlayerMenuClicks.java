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
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Picked " + ChatColor.RESET
                + (secret == null ? id : com.spacerng.solrng.gui.Lore.gradient(secret.display(), true, secret.stops()))
                + ChatColor.GRAY + ", " + ChatColor.GREEN + plugin.getRealmManager().multiplierFor(data) + "x Luck"
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

    /**
     * /keys (V282): click a key to take one out as an item, shift-click for
     * a stack; the switch turns auto storing on or off; the chest puts
     * every key from the inventory in.
     */
    void handleKeysClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof com.spacerng.solrng.gui.KeysHolder holder)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        int slot = event.getRawSlot();
        if (slot == com.spacerng.solrng.gui.KeysGui.TOGGLE_SLOT) {
            data.setAutoStoreKeys(!data.isAutoStoreKeys());
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.7f,
                    data.isAutoStoreKeys() ? 1.5f : 0.8f);
        } else if (slot == com.spacerng.solrng.gui.KeysGui.STORE_SLOT) {
            int moved = 0;
            var inventory = player.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inventory.getItem(i);
                var consumable = plugin.getConsumableManager().from(stack);
                if (consumable == null || !plugin.getCrateManager().isStorableKey(consumable.id())) continue;
                data.addStoredKeys(consumable.id(), stack.getAmount());
                moved += stack.getAmount();
                inventory.setItem(i, null);
            }
            player.sendMessage(moved > 0 ? ChatColor.GREEN + "Stored " + moved + " key" + (moved == 1 ? "" : "s") + "."
                    : ChatColor.GRAY + "No keys in your inventory.");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.2f);
        } else {
            String id = holder.slots().get(slot);
            if (id == null) return;
            var consumable = plugin.getConsumableManager().get(id);
            if (consumable == null) return;
            // V286: shift-click uses them up, opening the crate where you
            // stand; rewards that do not fit go to /stash.
            if (event.isShiftClick()) {
                var crate = plugin.getCrateManager().crateForKey(id);
                if (crate == null || data.storedKeys(id) <= 0) return;
                player.closeInventory();
                // V322: all of them. The only ceiling left is
                // crates.quick-open-max, inside quickOpen itself.
                plugin.getCrateManager().quickOpen(player, player.getLocation().add(0, 1.0, 0), crate,
                        Integer.MAX_VALUE);
                return;
            }
            long took = data.takeStoredKeys(id, 1);
            if (took <= 0) return;
            // Straight to the inventory, past the auto store this would
            // otherwise put it back into.
            com.spacerng.solrng.player.Stash.give(plugin, player,
                    plugin.getConsumableManager().build(consumable, (int) took));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        }
        player.openInventory(com.spacerng.solrng.gui.KeysGui.build(plugin, player));
    }

    /** /boosters: a click on a stored potion drinks one (V264). */
    void handleBoostersClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof com.spacerng.solrng.gui.BoostersHolder holder)) return;
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

        // The forge star: ten Cosmic Dust becomes a pet, or a rarity on one
        // you already have.
        String eggId = com.spacerng.solrng.gui.PetsGui.clickedEgg(event.getCurrentItem());
        if (eggId != null) {
            if (com.spacerng.solrng.pet.PetHatch.isHatching(player)) {
                player.sendMessage(ChatColor.GRAY + "Your egg is still hatching.");
                return;
            }
            var egg = pets.upgrades().egg(eggId);
            if (egg != null && data.getPrestige() < egg.minPrestige()) {
                player.sendMessage(ChatColor.RED + "The " + ChatColor.stripColor(egg.display())
                        + " opens at Prestige " + egg.minPrestige() + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            var made = pets.make(data, egg);
            if (!made.happened()) {
                player.sendMessage(ChatColor.RED + "Not enough Cosmic Dust, or every pet is already maxed.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            String shown = com.spacerng.solrng.gui.Lore.gradient(
                    made.type().display(), true, made.type().stops());
            // V238: the pet is saved now; the hatch decides when the player
            // sees it, and the chat line and the worn pets wait for it.
            int tier = pets.upgrades().eggs().indexOf(egg) + 1;
            Runnable landed = () -> {
                if (made.trashed()) {
                    player.sendMessage(ChatColor.GRAY + "Hatched " + ChatColor.RESET + shown
                            + ChatColor.GRAY + " and threw it away. Autotrash is on for it in the index.");
                } else if (made.isNew()) {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "A new pet hatched: " + ChatColor.RESET
                            + shown + ChatColor.GRAY + ". " + made.type().boostText() + ".");
                } else {
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "Another " + ChatColor.RESET + shown
                            + ChatColor.GRAY + ". You hold " + ChatColor.WHITE + made.pet().copies()
                            + ChatColor.GRAY + " of them.");
                }
                plugin.getPetManager().refresh(player);
                plugin.getScoreboardManager().update(player);
            };
            // V313: an egg hatches on the spot, Leon's call ("pet eggs die
            // gelijk hatch"). The V238 build-up is still in the jar behind
            // pets.eggs.instant, because taking a spectacle out is cheap
            // to undo and expensive to rewrite.
            if (plugin.getConfig().getBoolean("pets.eggs.instant", true)) {
                landed.run();
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                player.openInventory(com.spacerng.solrng.gui.PetsGui.build(plugin, player));
                return;
            }
            player.closeInventory();
            com.spacerng.solrng.pet.PetHatch.start(plugin, player, egg, tier, made, landed);
            return;
        }

        String id = com.spacerng.solrng.gui.PetsGui.clickedPet(event.getCurrentItem());
        if (id == null) return;

        var pet = pets.get(id);
        if (pet == null) return;

        // V324: a shift click in storage throws one spare copy away. The
        // copy that IS the pet never goes, so this cannot lose a pet.
        if (event.isShiftClick() && view == com.spacerng.solrng.gui.PetsHolder.View.STORAGE) {
            if (pets.trashCopy(data, pet.id())) {
                player.sendMessage(ChatColor.GRAY + "Threw away one spare "
                        + com.spacerng.solrng.gui.Lore.gradient(pet.display(), true, pet.stops())
                        + ChatColor.GRAY + ".");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 0.6f, 1.2f);
            } else {
                player.sendMessage(ChatColor.GRAY + "You have no spare copy of that pet.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            }
            player.openInventory(com.spacerng.solrng.gui.PetsGui.storage(plugin, player));
            return;
        }

        // A right click opens the pet for upgrading instead of wearing it.
        // Bedrock cannot right click in a menu (V223), so any click in
        // storage opens it there, and the pet screen has a wear button for
        // them. The worn slots on the main screen still take a pet off.
        if ((event.isRightClick() || (com.spacerng.solrng.platform.Bedrock.is(player)
                        && view == com.spacerng.solrng.gui.PetsHolder.View.STORAGE))
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
        player.openInventory(view == com.spacerng.solrng.gui.PetsHolder.View.STORAGE
                ? com.spacerng.solrng.gui.PetsGui.storage(plugin, player)
                : com.spacerng.solrng.gui.PetsGui.build(plugin, player));
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
