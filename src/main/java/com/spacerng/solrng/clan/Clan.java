package com.spacerng.solrng.clan;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One clan (V393, Leon). A name, an owner, its members and the XP its
 * members have earned it since they joined. The level is derived from the
 * XP, never stored, the same rule as the battle pass.
 *
 * {@code upgrades} holds the levels bought with clan points (V394), keyed
 * by upgrade id.
 */
public final class Clan {

    private final String id;
    private String name;
    private UUID owner;
    private final Set<UUID> members = new LinkedHashSet<>();
    private long xp;
    private final Map<String, Integer> upgrades = new java.util.HashMap<>();
    private final long created;
    /** V394: the clan chest, 54 slots, null for an empty slot. */
    private final org.bukkit.inventory.ItemStack[] chest = new org.bukkit.inventory.ItemStack[54];

    public Clan(String id, String name, UUID owner, long created) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.created = created;
        members.add(owner);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Set<UUID> members() {
        return members;
    }

    public long xp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0L, xp);
    }

    public void addXp(long amount) {
        if (amount > 0) xp += amount;
    }

    public Map<String, Integer> upgrades() {
        return upgrades;
    }

    public int upgrade(String id) {
        return upgrades.getOrDefault(id, 0);
    }

    public org.bukkit.inventory.ItemStack[] chest() {
        return chest;
    }

    public long created() {
        return created;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }
}
