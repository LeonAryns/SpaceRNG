package com.spacerng.solrng.commands;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.holo.HoloManager;
import com.spacerng.solrng.gui.Lore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /crates (V316): takes you to the crates.
 *
 * Leon asked for the crate area to be reached with a command rather than
 * walked to, with your own key count floating over each crate once you
 * are there (that part is HoloManager.tickCrateKeys). The keys themselves
 * have been digital since V282, so there is nothing to carry.
 *
 * It aims at the crate placements in holograms.yml rather than a warp
 * Leon has to set: the crates already know where they are, and a warp
 * that has to be kept in step with them is a warp that goes stale.
 */
public class CratesCommand implements CommandExecutor {

    private final SolRNGPlugin plugin;

    public CratesCommand(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can go to the crates.");
            return true;
        }

        List<Location> placed = new ArrayList<>();
        for (HoloManager.Spot spot : plugin.getHoloManager().list()) {
            if (spot.kind() == HoloManager.Kind.CRATE && spot.at().getWorld() != null) {
                placed.add(spot.at());
            }
        }
        if (placed.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No crates are placed yet.");
            player.sendMessage(ChatColor.GRAY + "Staff place them with "
                    + ChatColor.YELLOW + "/rngadmin crate place" + ChatColor.GRAY + ".");
            return true;
        }

        // The middle of the crates, then the crate nearest that middle, so
        // a row of crates lands you in front of the row rather than inside
        // whichever one happens to be first in the file.
        double x = 0;
        double y = 0;
        double z = 0;
        for (Location at : placed) {
            x += at.getX();
            y += at.getY();
            z += at.getZ();
        }
        Location centre = new Location(placed.get(0).getWorld(), x / placed.size(),
                y / placed.size(), z / placed.size());
        Location nearest = placed.get(0);
        double best = Double.MAX_VALUE;
        for (Location at : placed) {
            if (at.getWorld() != centre.getWorld()) continue;
            double distance = at.distanceSquared(centre);
            if (distance < best) {
                best = distance;
                nearest = at;
            }
        }

        // Three blocks back from the crate and looking at it, so the first
        // thing on screen is the crate and your own key count over it.
        Location stand = nearest.clone().add(0, 0, 3.0);
        stand.setDirection(nearest.toVector().subtract(stand.toVector()));
        if (!player.teleport(stand)) {
            player.sendMessage(ChatColor.RED + "Could not take you there. Try again.");
            return true;
        }

        player.sendMessage("");
        player.sendMessage(Lore.header("THE CRATES"));
        player.sendMessage(ChatColor.GRAY + "Your keys float over each crate. "
                + ChatColor.YELLOW + "/keys" + ChatColor.GRAY + " lists them all.");
        player.sendMessage("");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        return true;
    }
}
