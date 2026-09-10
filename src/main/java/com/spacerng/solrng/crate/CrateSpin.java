package com.spacerng.solrng.crate;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * One crate opening: the reel, the slow down, the landing.
 *
 * The reward was decided before this object existed, and this class only
 * puts on the show. Every way the show can end early goes through
 * finish(): closing the menu, logging out, a frame throwing, the server
 * stopping. finish() always pays, so a key is never spent on nothing.
 */
public class CrateSpin {

    private static final int REEL_START = 9;
    private static final int CENTRE = 13;
    private static final int HOLD_TICKS = 30;

    private final SolRNGPlugin plugin;
    private final CrateManager manager;
    private final Player player;
    private final Crate crate;
    private final CrateReward winner;
    private final Location burstAt;
    private final List<CrateReward> strip = new ArrayList<>();
    private final int[] stepAt;
    private final Inventory inventory;

    private BukkitTask task;
    private int elapsed;
    private int step;
    private int landedAt = -1;
    private boolean granted;
    private boolean finished;

    CrateSpin(SolRNGPlugin plugin, CrateManager manager, Player player, Crate crate,
              CrateReward winner, Location burstAt, int steps, int slowestGap) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.crate = crate;
        this.winner = winner;
        this.burstAt = burstAt;

        // Each step waits a little longer than the one before, on a cubic
        // curve: quick for most of the spin, then a crawl across the last
        // few items, which is the only stretch the eye can actually read.
        stepAt = new int[steps];
        int tick = 0;
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            tick += 1 + (int) Math.round(Math.pow(t, 3) * slowestGap);
            stepAt[i] = tick;
        }

        // The strip is honest filler: every item on it is a real pick from
        // the same table. The winner is simply placed where the reel stops.
        for (int i = 0; i < steps + 9; i++) strip.add(crate.pick());
        strip.set(steps + 4, winner);

        CrateSpinHolder holder = new CrateSpinHolder(this);
        inventory = Bukkit.createInventory(holder, 27, ChatColor.GOLD + "" + ChatColor.BOLD + crate.display());
        holder.setInventory(inventory);
        border(Material.BLACK_STAINED_GLASS_PANE);
        render(0);
    }

    void start() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 0.8f, 1.0f);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        try {
            if (!player.isOnline()) {
                finish(false, false);
                return;
            }
            elapsed++;
            if (landedAt < 0) {
                boolean moved = false;
                while (step < stepAt.length && elapsed >= stepAt[step]) {
                    step++;
                    moved = true;
                }
                if (moved) {
                    render(step);
                    // Pitch climbs as the reel slows, so the tension rises
                    // exactly when the movement stops providing it.
                    float progress = (float) step / stepAt.length;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 0.8f + progress);
                }
                if (step >= stepAt.length) land();
            } else if (elapsed - landedAt >= HOLD_TICKS) {
                finish(true, true);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Crate spin failed for " + player.getName() + ": " + ex.getMessage());
            finish(true, true);
        }
    }

    private void render(int offset) {
        for (int column = 0; column < 9; column++) {
            inventory.setItem(REEL_START + column, manager.icon(crate, strip.get(offset + column)));
        }
    }

    private void border(Material fill) {
        ItemStack pane = pane(fill, " ");
        for (int slot = 0; slot < 27; slot++) {
            if (slot >= REEL_START && slot < REEL_START + 9) continue;
            inventory.setItem(slot, pane);
        }
        inventory.setItem(4, pane(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + "▼"));
        inventory.setItem(22, pane(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + "▲"));
    }

    private void land() {
        landedAt = elapsed;
        boolean jackpot = crate.isJackpot(winner);
        border(Material.LIME_STAINED_GLASS_PANE);

        ItemStack centre = inventory.getItem(CENTRE);
        if (centre != null) {
            ItemMeta meta = centre.getItemMeta();
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            centre.setItemMeta(meta);
            inventory.setItem(CENTRE, centre);
        }

        // Paid on the landing frame, not when the menu closes, so the
        // result and the reward arrive together.
        grant();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
        if (jackpot) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.4f);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.6f);
        }
    }

    private void grant() {
        if (granted) return;
        granted = true;
        manager.grant(player, crate, winner, true);
    }

    /**
     * Ends the opening, always paying.
     *
     * `closeMenu` is false when the menu is already closing, since closing
     * it again from inside its own close event misbehaves. `show` is false
     * for a logout or a shutdown, where there is nobody to show anything to.
     */
    void finish(boolean closeMenu, boolean show) {
        if (finished) return;
        finished = true;
        if (task != null) task.cancel();
        grant();
        manager.spinEnded(player.getUniqueId());

        if (!show || !player.isOnline() || !plugin.isEnabled()) return;
        if (closeMenu && player.getOpenInventory().getTopInventory().getHolder() instanceof CrateSpinHolder holder
                && holder.spin() == this) {
            player.closeInventory();
        }
        CrateFx.burst(plugin, player, burstAt, crate, crate.isJackpot(winner));
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(name);
        pane.setItemMeta(meta);
        return pane;
    }
}
