package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.realm.RealmManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /secretindex (V229): every secret the Secret Realm can give, found or not.
 *
 * Laid out like the Pet Index: a rail on top with the realm's own card in
 * the middle and your head top right, the secrets from the third row. A
 * secret not found yet is a stone button with a hint, never its name, so
 * the index keeps something to find. Nothing here is clickable.
 */
public class SecretIndexGui {

    private static final int SIZE = 54;
    private static final int REALM_SLOT = 4;
    private static final int HEAD_SLOT = 8;
    private static final int FIRST = 19;

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        SecretIndexHolder holder = new SecretIndexHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Secret Index");
        holder.setInventory(inv);
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());
        RealmManager realm = plugin.getRealmManager();

        ItemStack rail = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, i < 9 ? rail : filler);

        inv.setItem(REALM_SLOT, realmCard(realm));
        inv.setItem(HEAD_SLOT, head(player, data, realm));

        int slot = FIRST;
        for (RealmManager.Secret secret : realm.secrets().values()) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= SIZE) break;
            inv.setItem(slot++, secretIcon(realm, secret, data.getSecretsFound().contains(secret.id())));
        }
        return inv;
    }

    private static ItemStack realmCard(RealmManager realm) {
        ItemStack item = new ItemStack(Material.END_PORTAL_FRAME);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.gradient("Secret Realm", true, "#B388FF", "#40C4FF"));
        List<String> lore = new ArrayList<>();
        lore.add(Lore.line(ChatColor.GRAY, "Opens on its own at random times"));
        lore.add(Lore.line(ChatColor.GRAY, "for a few minutes. Roll inside it"));
        lore.add(Lore.line(ChatColor.GRAY, "for a chance at a secret."));
        lore.add("");
        lore.add(Lore.stat(ChatColor.AQUA, "A secret", "1 in " + realm.findOneIn() + " rolls inside"));
        lore.add(Lore.stat(ChatColor.GREEN, "Each one found", "+" + trim(realm.luckPerSecret() * 100.0) + "% Luck"));
        lore.add("");
        if (realm.isOpen()) {
            long left = Math.max(0L, realm.openUntil() - System.currentTimeMillis());
            lore.add(Lore.stat(ChatColor.GREEN, "Open", "for " + (left / 60_000L) + "m " + ((left / 1000L) % 60L) + "s"));
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Type /realm to go through");
        } else {
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Closed");
            lore.add(Lore.line(ChatColor.GRAY, "You are told when it opens"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack head(Player player, PlayerData data, RealmManager realm) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.setDisplayName(Lore.title(ChatColor.LIGHT_PURPLE, "Your secrets"));
        int found = 0;
        for (String id : data.getSecretsFound()) if (realm.secrets().containsKey(id)) found++;
        meta.setLore(List.of(
                Lore.stat(ChatColor.AQUA, "Found", found + " / " + realm.secrets().size()),
                Lore.stat(ChatColor.GREEN, "Luck from them", "+" + trim(realm.luckFor(data) * 100.0) + "%"),
                "",
                Lore.footnote("It is in /stats with your other Luck.")));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack secretIcon(RealmManager realm, RealmManager.Secret secret, boolean found) {
        ItemStack item = new ItemStack(found ? secret.icon() : Material.STONE_BUTTON);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (found) {
            meta.setDisplayName(Lore.gradient(secret.display(), true, secret.stops()));
            if (!secret.hint().isBlank()) {
                lore.add(Lore.line(ChatColor.GRAY, secret.hint()));
                lore.add("");
            }
            lore.add(Lore.stat(ChatColor.AQUA, "Share of finds", percent(realm.shareOf(secret))));
            lore.add("");
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "Found");
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        } else {
            meta.setDisplayName(ChatColor.DARK_GRAY + "???");
            lore.add(Lore.stat(ChatColor.AQUA, "Share of finds", percent(realm.shareOf(secret))));
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Not found yet");
            lore.add(Lore.line(ChatColor.GRAY, "Only found in the Secret Realm"));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String percent(double share) {
        double value = share * 100.0;
        return (value >= 1 ? String.format("%.1f", value) : String.format("%.2f", value)) + "%";
    }

    private static String trim(double value) {
        String text = String.format("%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
}
