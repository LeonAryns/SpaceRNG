package com.spacerng.solrng.commands;

import com.spacerng.solrng.gui.MenuStyle;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * /trash (V262): an empty chest whose contents are gone when it closes.
 * Dropping items is switched off server wide, so this is the way to get
 * rid of something. The bound Farmer's Hoe cannot be put in, the same as
 * any other chest.
 */
public class TrashCommand implements CommandExecutor {

    /** Marks the trash inventory; nothing reads its contents, so they vanish on close. */
    public static final class TrashHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players have anything to throw away.");
            return true;
        }
        TrashHolder holder = new TrashHolder();
        Inventory inv = Bukkit.createInventory(holder, 36, MenuStyle.title("Trash", "#FF8A80", "#D50000"));
        holder.inventory = inv;
        player.openInventory(inv);
        player.sendMessage(ChatColor.GRAY + "Anything left in the trash is deleted when you close it.");
        return true;
    }
}
