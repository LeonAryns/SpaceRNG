package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final SolRNGPlugin plugin;

    public ChatListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String prefix = plugin.getTagManager().getPrefix(event.getPlayer());
        if (prefix == null || prefix.isEmpty()) return;
        // Prepend the equipped tag before the player's name in chat.
        event.setFormat(prefix + event.getFormat());
    }
}
