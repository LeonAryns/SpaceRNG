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

/**
 * Routes every menu click to the class for that menu, and guards drags and
 * closes. Clicks are cancelled by the handlers themselves; a menu with no
 * route is still cancelled here.
 */
public class GuiListener implements Listener {

    private final ConvertClicks convert;
    private final SkillTreeClicks skillTree;
    private final ProgressionClicks progression;
    private final PlayerMenuClicks playerMenus;
    private final ShopClicks shops;
    private final VaultClicks vaults;
    private final CosmeticClicks cosmetics;

    private final SolRNGPlugin plugin;

    public GuiListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.convert = new ConvertClicks(plugin);
        this.skillTree = new SkillTreeClicks(plugin);
        this.progression = new ProgressionClicks(plugin);
        this.playerMenus = new PlayerMenuClicks(plugin);
        this.shops = new ShopClicks(plugin);
        this.vaults = new VaultClicks(plugin);
        this.cosmetics = new CosmeticClicks(plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof SkillTreeHolder) {
            skillTree.handleSkillTreeClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.RespecHolder) {
            skillTree.handleRespecMenuClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.EnchantBuyHolder) {
            playerMenus.handleEnchantBuyClick(event);
        } else if (topInventory.getHolder() instanceof ConvertHolder) {
            convert.handleConvertClick(event);
        } else if (topInventory.getHolder() instanceof IndexHolder) {
            playerMenus.handleIndexClick(event);
        } else if (topInventory.getHolder() instanceof PrestigeHolder) {
            progression.handlePrestigeClick(event);
        } else if (topInventory.getHolder() instanceof ArmorHolder) {
            progression.handleArmorClick(event);
        } else if (topInventory.getHolder() instanceof OptionsHolder) {
            playerMenus.handleOptionsClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.StarforgeHolder) {
            progression.handleStarforgeClick(event);
        } else if (topInventory.getHolder() instanceof MilestoneHolder) {
            progression.handleMilestoneClick(event);
        } else if (topInventory.getHolder() instanceof CropsHolder) {
            playerMenus.handleCropsClick(event);
        } else if (topInventory.getHolder() instanceof NovaCoreHolder) {
            progression.handleNovaCoreClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.PassHolder) {
            progression.handlePassClick(event);
        } else if (topInventory.getHolder() instanceof BuyHolder) {
            shops.handleBuyClick(event);
        } else if (topInventory.getHolder() instanceof DailyHolder) {
            shops.handleDailyClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.StatsHolder) {
            playerMenus.handleStatsClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.LeaderboardHolder) {
            // Nothing to click - the menu is purely something to read, but
            // an uncancelled click would let a player walk off with the
            // panes.
            event.setCancelled(true);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.ShopHolder) {
            shops.handleShopClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.PotionHolder) {
            shops.handlePotionClick(event);
        } else if (topInventory.getHolder() instanceof HoeHolder) {
            playerMenus.handleHoeClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.PerkRollerHolder) {
            shops.handlePerkRollerClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.PerkIndexHolder) {
            shops.handlePerkIndexClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.FirstsHolder) {
            // A board to read, nothing to click, but an uncancelled click
            // would let somebody walk off with the panes.
            event.setCancelled(true);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.CosmeticsHolder) {
            cosmetics.handle(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.RanksHolder) {
            shops.handleRanksClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.AuraHolder) {
            playerMenus.handleAuraClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.PetsHolder) {
            playerMenus.handlePetsClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.PetUpgradeHolder) {
            playerMenus.handlePetUpgradeClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.PrivateVaultHolder) {
            vaults.handleClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.StashHolder) {
            playerMenus.handleStashClick(event);
        } else if (topInventory.getHolder() instanceof com.spacerng.solrng.gui.MenuHolder) {
            // A menu with no handler yet is still never a chest.
            event.setCancelled(true);
        }
    }

    /** Dragging spreads a stack across slots - same door, same lock. */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof com.spacerng.solrng.gui.PrivateVaultHolder holder) {
            // A vault page is real storage. Only the button bar is off limits,
            // and the selector is off limits entirely.
            for (int slot : event.getRawSlots()) {
                if (slot < top.getSize()
                        && (holder.isSelector() || slot >= com.spacerng.solrng.gui.PrivateVaultGui.PAGE_SLOTS)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (!(top.getHolder() instanceof ConvertHolder)) {
            // Every other menu is click only. A drag across one used to drop
            // the stack into the menu, where it vanished when the menu closed.
            if (top.getHolder() instanceof com.spacerng.solrng.gui.MenuHolder) {
                for (int slot : event.getRawSlots()) {
                    if (slot < top.getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            return;
        }

        boolean touchesTop = false;
        boolean touchesNonInput = false;
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                touchesTop = true;
                if (slot > 26) touchesNonInput = true;
            }
        }
        if (!touchesTop) return;

        if (touchesNonInput || !convert.isDrop(event.getOldCursor())) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage(ChatColor.RED + "Only rolled drops can go in there.");
        }
    }

    /**
     * The input rows are a virtual inventory - anything still sitting in
     * them when the menu closes is destroyed with it. Hand it all back
     * instead; losing a Starforge to a stray Escape isn't acceptable.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(event.getPlayer() instanceof Player player)) return;

        if (top.getHolder() instanceof com.spacerng.solrng.gui.PrivateVaultHolder vault) {
            // The page inventory is virtual: what is in it when the window
            // closes only survives if it is written back onto the player.
            if (!vault.isSelector()) vaults.save(player, top, vault.getPage());
            return;
        }
        if (!(top.getHolder() instanceof ConvertHolder)) return;

        for (int slot : ConvertHolder.INPUT_SLOTS) {
            ItemStack stack = top.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;

            top.setItem(slot, null);
            com.spacerng.solrng.player.Stash.give(plugin, player, stack);
        }
    }
}
