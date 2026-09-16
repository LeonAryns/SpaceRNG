package com.spacerng.solrng.discord;

import org.bukkit.entity.Player;

/**
 * What the rest of the plugin is allowed to ask the Discord bot for.
 *
 * Nothing in this interface mentions a DiscordSRV or a JDA type, which is
 * the point: every call site can hold one of these without the server
 * ever trying to load a class that is only there when DiscordSRV is
 * installed. The implementation is built behind a plugin check, and this
 * is null when there is no DiscordSRV to talk to.
 */
public interface BotHooks {

    /** Puts this player's Discord roles in step with their rank and link. */
    void syncRoles(Player player);

    /** Same, for everybody online. */
    void syncAll();

    void shutdown();
}
