package com.spacerng.solrng.listeners;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.roll.MysteryHead;
import com.spacerng.solrng.roll.RollAura;
import com.spacerng.solrng.roll.ShinyPreRoll;
import com.spacerng.solrng.roll.RollShowcase;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RollListener implements Listener {

    private final SolRNGPlugin plugin;
    private final NamespacedKey rarityKey;
    private final NamespacedKey rollNameKey;
    private final NamespacedKey shinyKey;
    private final Map<UUID, BukkitTask> rollingTasks = new HashMap<>();
    // ticks remaining in the current roll, kept up to date so the scoreboard
    // can show a live countdown without duplicating the timing logic.
    private final Map<UUID, Long> remainingTicks = new HashMap<>();
    // Guards against a single physical right-click firing PlayerInteractEvent
    // twice - Bukkit/Paper fires a second RIGHT_CLICK_AIR event right after
    // RIGHT_CLICK_BLOCK for the same hand when the clicked block doesn't
    // consume the interaction (most blocks). EquipmentSlot filtering alone
    // doesn't catch this since both events are for the main hand.
    private final Map<UUID, Long> lastInteractMillis = new HashMap<>();
    // The Epic+ build-up currently running for a player, so it can be
    // revealed when the roll lands and torn down if the roll is abandoned.
    private final Map<UUID, RollAura> activeAuras = new HashMap<>();
    // While an Epic+ finale is playing out, no new roll may start. Without
    // this an auto-roller immediately begins the next roll on top of their
    // own payoff, and the build-up for the next one fights the shockwaves
    // of the last. Stored as a wall-clock deadline in millis.
    private final Map<UUID, Long> revealLockUntil = new HashMap<>();
    // The item floating in front of a rolling player. Kept after the roll
    // lands until the next roll replaces it or the player leaves.
    private final Map<UUID, RollShowcase> showcases = new HashMap<>();
    private final Random random = new Random();

    public RollListener(SolRNGPlugin plugin) {
        this.plugin = plugin;
        this.rarityKey = SolRNGPlugin.key( "solrng_rarity");
        this.rollNameKey = SolRNGPlugin.key( "solrng_roll_name");
        this.shinyKey = SolRNGPlugin.key( "solrng_shiny");
    }

    public NamespacedKey getRarityKey() {
        return rarityKey;
    }

    public NamespacedKey getRollNameKey() {
        return rollNameKey;
    }

    public NamespacedKey getShinyKey() {
        return shinyKey;
    }

    public boolean isShiny(ItemStack item) {
        return item != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(shinyKey, PersistentDataType.BYTE);
    }

    /**
     * Whether this roll comes out shiny. Gated on the skill node, so the
     * chance simply doesn't exist until it's bought - a player who hasn't
     * unlocked it never rolls one and never sees a near-miss.
     */
    public boolean rollShiny(PlayerData data) {
        String node = plugin.getConfig().getString("shiny.node", "shiny_unlock");
        if (!node.isEmpty() && !data.hasUnlocked(node)) return false;
        return random.nextDouble() < shinyChance(data);
    }

    /**
     * The base 1-in-100, scaled by the Shiny Chance skills. They add to a
     * multiplier rather than to the chance itself, so "+10% per level"
     * means a tenth more shinies per level whatever the base is set to -
     * retuning shiny.chance doesn't silently retune the skills too.
     */
    private final Map<java.util.UUID, Long> inventoryWarned = new java.util.HashMap<>();

    /**
     * Says the drop was lost, at most once a minute.
     *
     * Auto Roll would otherwise print this every few seconds forever,
     * which teaches the player to ignore the one line they most need to
     * read.
     */
    private void warnInventoryFull(Player player) {
        long now = System.currentTimeMillis();
        Long last = inventoryWarned.get(player.getUniqueId());
        if (last != null && now - last < 60_000L) return;
        inventoryWarned.put(player.getUniqueId(), now);

        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Your inventory is full.");
        player.sendMessage(ChatColor.GRAY + "Drops are being lost. Free a slot, or turn on "
                + ChatColor.YELLOW + "auto-convert" + ChatColor.GRAY + " in "
                + ChatColor.YELLOW + "/convert" + ChatColor.GRAY + " to bank them instead.");
        player.sendMessage("");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    /**
     * A flat chance that a roll comes out one tier rarer than it landed.
     *
     * Deliberately independent of Luck: it is the moment a brand new
     * player gets something they had no business getting, which is what
     * makes them stay. Capped below the top rarity, so the thing everyone
     * is actually chasing still has to be earned.
     */
    private RollableItem luckyStrike(RollableItem result) {
        double chance = plugin.getConfig().getDouble("roll-item.lucky-strike.chance", 0.004);
        if (chance <= 0 || random.nextDouble() >= chance) return result;

        Rarity ceiling;
        try {
            ceiling = Rarity.valueOf(plugin.getConfig()
                    .getString("roll-item.lucky-strike.max-rarity", "MYTHICAL").toUpperCase());
        } catch (IllegalArgumentException ex) {
            ceiling = Rarity.MYTHICAL;
        }

        int next = result.getRarity().ordinal() + 1;
        if (next > ceiling.ordinal() || next >= Rarity.values().length) return result;

        java.util.List<RollableItem> pool = new java.util.ArrayList<>();
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            if (item.getRarity().ordinal() == next) pool.add(item);
        }
        if (pool.isEmpty()) return result;
        return pool.get(random.nextInt(pool.size()));
    }

    private final Map<java.util.UUID, Long> vaultWarned = new java.util.HashMap<>();

    /** Says the vault is full, at most once a minute. */
    private void warnVaultFull(Player player, Rarity rarity) {
        long now = System.currentTimeMillis();
        Long last = vaultWarned.get(player.getUniqueId());
        if (last != null && now - last < 60_000L) return;
        vaultWarned.put(player.getUniqueId(), now);

        player.sendMessage(ChatColor.RED + "Your "
                + plugin.getRarityManager().style(rarity, rarity.displayName())
                + ChatColor.RED + " vault is full. "
                + ChatColor.GRAY + "Spend some, or buy Vault Space in /skilltree.");
    }

    public double shinyChance(PlayerData data) {
        return com.spacerng.solrng.stats.StatSources.shiny(plugin, data).total();
    }

    public boolean isRolling(UUID uuid) {
        return rollingTasks.containsKey(uuid);
    }

    /** True while a big drop's finale is still playing. */
    public boolean isRevealLocked(UUID uuid) {
        Long until = revealLockUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            revealLockUntil.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Mid-roll or mid-reveal - either way, don't start another roll. This
     * is what both the manual click and the auto-roll loop check.
     */
    public boolean isBusy(UUID uuid) {
        return isRolling(uuid) || isRevealLocked(uuid);
    }

    /**
     * Seconds left in a player's in-progress roll, or 0 if they aren't
     * currently rolling. Used by the scoreboard's live status line.
     */
    public int getRemainingSeconds(UUID uuid) {
        Long ticks = remainingTicks.get(uuid);
        if (ticks == null || ticks <= 0) return 0;
        return (int) Math.ceil(ticks / 20.0);
    }

    /**
     * Cancels a player's in-progress roll task without granting anything -
     * used when they log out mid-roll so the task doesn't keep running
     * against an offline player.
     */
    public void cancelRoll(UUID uuid) {
        BukkitTask task = rollingTasks.remove(uuid);
        remainingTicks.remove(uuid);
        lastInteractMillis.remove(uuid);
        RollAura aura = activeAuras.remove(uuid);
        if (aura != null) {
            aura.cancel();
        }
        revealLockUntil.remove(uuid);
        RollShowcase showcase = showcases.remove(uuid);
        if (showcase != null) showcase.cancel();
        chimedOnLanding.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!rightClick && !leftClick) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // ignore the duplicate off-hand firing

        // Identified by its PersistentData tag, not its name - every
        // Starforge tier is a different display name but the same item.
        if (!plugin.getStarforgeManager().isStarforge(event.getItem())) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        // Same physical click can still fire twice for the main hand alone
        // (RIGHT_CLICK_BLOCK immediately followed by RIGHT_CLICK_AIR) - if
        // we just handled a click from this player within the last tick,
        // this is that duplicate, not a real second click.
        long now = System.currentTimeMillis();
        Long last = lastInteractMillis.get(player.getUniqueId());
        if (last != null && now - last < 100L) return;
        lastInteractMillis.put(player.getUniqueId(), now);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // Shift plus right-click is the ability. Right-click alone still
        // rolls, so the muscle memory of every existing player is intact.
        if (rightClick && player.isSneaking()) {
            long wait = plugin.getStarforgeManager().activateAbility(player, data);
            if (wait < 0) {
                player.sendMessage(ChatColor.GRAY + "This Starforge has no ability. "
                        + "The later tiers do.");
            } else if (wait > 0) {
                player.sendMessage(ChatColor.RED + "Not ready for another "
                        + (wait >= 60 ? (wait / 60) + "m " + (wait % 60) + "s" : wait + "s") + ".");
            }
            return;
        }

        if (leftClick) {
            toggleAutoRoll(player, data);
            return;
        }

        if (isBusy(player.getUniqueId())) {
            return; // mid-roll, or the last big drop is still revealing
        }

        startRoll(player);
    }

    /**
     * The Starforge can't be dropped - it's the one item a player can't
     * afford to lose by fumbling the drop key.
     */
    @EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (!plugin.getStarforgeManager().isStarforge(event.getItemDrop().getItemStack())) return;

        event.setCancelled(true);
        sendActionBar(event.getPlayer(),
                ChatColor.RED + "Your Starforge can't be dropped.");
    }

    /**
     * Left-clicking the Starforge flips Auto Roll, but only once the Auto
     * Roll skill is unlocked - otherwise it just points them at the tree.
     */
    private void toggleAutoRoll(Player player, PlayerData data) {
        if (!data.hasUnlocked("auto_roll_root")) {
            sendActionBar(player, ChatColor.RED + "Unlock \"Auto Roll\" in /skilltree first!");
            return;
        }

        boolean enabled = !data.isAutoRollEnabled();
        data.setAutoRollEnabled(enabled);
        sendActionBar(player, enabled
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "Auto Roll ON"
                : ChatColor.RED + "" + ChatColor.BOLD + "Auto Roll OFF");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, enabled ? 1.5f : 0.8f);
    }

    /**
     * Kicks off the roll animation: no cooldown, but the result isn't
     * decided/granted until the timer finishes. A player's roll-speed
     * multiplier (from the Rolling Speed skill tree branch, or future
     * armor upgrades) shortens the wait.
     */
    public void startRoll(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // Supercharge fires on the roll NUMBER, so it has to be decided
        // against the roll that is about to happen rather than the one
        // that just did - grantRoll is what increments the counter.
        long rollNumber = data.getTotalRolls() + 1;
        double supercharge = plugin.getSkillTreeManager().superchargeFor(data, rollNumber);
        // A banked charge is spent here rather than at the end, so it can't
        // be lost to a disconnect mid-roll without having done anything.
        double charge = data.consumeRollCharge();
        // A 10x charge or a supercharge multiplies the CHANCE, which is
        // (1 + Luck), not the Luck number. Until V158 it multiplied Luck
        // itself, so at 0% Luck a 10x Roll did nothing at all.
        double luck = (1.0 + plugin.getPrestigeManager().effectiveLuck(data)) * supercharge * charge - 1.0;
        if (supercharge > 1.0) {
            announceSupercharge(player, supercharge);
        }
        if (charge > 1.0) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "\u26a1 CHARGED ROLL \u26a1"
                    + ChatColor.RESET + ChatColor.GRAY + "  this one rolls at "
                    + ChatColor.LIGHT_PURPLE + com.spacerng.solrng.consumable.ConsumableManager.trim(charge)
                    + "x" + ChatColor.GRAY + " Luck."
                    + (data.getRollCharges() > 0
                            ? ChatColor.DARK_GRAY + "  (" + data.getRollCharges() + " left)" : ""));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);
        }

        // The result is decided up front rather than when the timer ends,
        // so the animation can land on it: the teaser flashes candidates,
        // then the final frames ARE the drop you're about to be handed.
        // Rolling at the end instead meant the reel visibly stopped on one
        // item and gave you a different one.
        // Lucky Streak: every so many rolls, this one can't land below its rarity.
        int floor = plugin.getSkillTreeManager().luckyStreakFloor(data, rollNumber);
        // /rngadmin nextroll, taken before the draw so the result stays a
        // single assignment and the whole roll after this point is the
        // real one: the reel, the showcase, the acts, the comet, the
        // counter and the finale.
        Rarity demanded = forcedRarity.remove(player.getUniqueId());
        RollableItem asked = demanded == null ? null : randomItemOf(demanded);
        final RollableItem result = asked != null ? asked : luckyStrike(floor > 0
                ? plugin.getRarityManager().rollAtLeast(luck, Rarity.values()[floor])
                : plugin.getRarityManager().roll(luck));
        if (floor > 0) {
            Rarity least = Rarity.values()[floor];
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✦ LUCKY STREAK ✦" + ChatColor.RESET
                    + ChatColor.GRAY + "  this roll is at least "
                    + plugin.getRarityManager().style(least, least.displayName()) + ChatColor.GRAY + ".");
        }
        boolean shiny = forcedShiny.remove(player.getUniqueId()) || rollShiny(data);

        // An Epic+ roll is stretched to at least the length of its own
        // build-up, so the effect always gets to play out in full - a
        // 10-second Mythical reveal on a 2-second roll would just be a
        // flash. It also means a longer-than-usual roll is itself the
        // first hint that something good is coming.
        //
        // Instant Roll deliberately can't skip a big drop: the reveal IS
        // the reward there, and cutting it would be a downgrade dressed up
        // as an upgrade.
        // Assigned once: the timer lambda below captures it, so it has to
        // stay effectively final.
        long baseTicks = effectiveRollTicks(data);
        // The cutscene decides the length of a big roll, and it is one act
        // per rarity band: five seconds of Epic, then either it ends there
        // or it breaks through into five of Legendary, and so on. A Divine
        // is four acts and twenty seconds, and fifteen for somebody with
        // the Epic aura switched off. The ladder therefore has to be built
        // BEFORE the timer, since its length is what the timer runs for.
        final com.spacerng.solrng.roll.RollStages stages = RollAura.isBigDrop(result.getRarity())
                ? com.spacerng.solrng.roll.RollStages.of(plugin, data, result.getRarity(), result.getOdds())
                : null;
        final long actTicks = RollAura.actTicks(plugin);
        final long cutscene = RollAura.durationTicks(stages, actTicks);
        // No cutscene means no ladder to walk: the drop's own rarity is
        // switched off in this player's /options, so the roll is an
        // ordinary one and nobody is watching an effect anyway.
        final long rollTicks = cutscene > 0L ? cutscene : (rollsInstantly(data) ? 1L : baseTicks);

        // A shiny gets its own beat before the roll, and Instant Roll can't
        // skip it for the same reason it can't skip a big drop. The aura's
        // build-up is timed to the roll that follows, so it only starts
        // once the pre-roll is over.
        final long preTicks = shiny ? ShinyPreRoll.TICKS : 0L;
        final long totalTicks = preTicks + rollTicks;
        final ShinyPreRoll preRoll = shiny ? new ShinyPreRoll(plugin, player) : null;
        // Auto Roll reports on the action bar instead of chat. The reel,
        // the item and the title still play; roll animation in /options is
        // the one switch for those.
        final boolean auto = data.isAutoRollEnabled();
        final RollShowcase[] showcase = {null};
        final RollAura[] aura = {null};
        final boolean[] auraStarted = {false};
        // True once a comet is flying with its counter on. The reel then
        // hands the screen over: the drop stays a question mark and the
        // odds climb in its place until the comet lands.
        final boolean[] cinematic = {false};
        if (preTicks == 0L) {
            auraStarted[0] = true;
            aura[0] = RollAura.start(plugin, player, result.getRarity(), result.getOdds(), stages, actTicks);
            if (aura[0] != null) {
                activeAuras.put(player.getUniqueId(), aura[0]);
                cinematic[0] = aura[0].ownsScreen();
            }
        }

        long[] elapsed = {0L};
        int[] lastStep = {-1};
        remainingTicks.put(player.getUniqueId(), totalTicks);

        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            elapsed[0] += 2L;

            if (elapsed[0] <= preTicks) {
                remainingTicks.put(player.getUniqueId(), totalTicks - elapsed[0]);
                preRoll.frame((double) elapsed[0] / preTicks, data.isRollAnimationEnabled());
                return;
            }

            if (!auraStarted[0]) {
                auraStarted[0] = true;
                aura[0] = RollAura.start(plugin, player, result.getRarity(), result.getOdds(), stages, actTicks);
                if (aura[0] != null) {
                    activeAuras.put(player.getUniqueId(), aura[0]);
                    cinematic[0] = aura[0].ownsScreen();
                }
            }

            long rollElapsed = elapsed[0] - preTicks;
            if (rollElapsed >= rollTicks) {
                taskHolder[0].cancel();
                rollingTasks.remove(player.getUniqueId());
                remainingTicks.remove(player.getUniqueId());
                finishRoll(player, data, result, shiny, auto, cinematic[0]);
                return;
            }

            remainingTicks.put(player.getUniqueId(), totalTicks - elapsed[0]);

            // Case-opening reel: 20 frames that land on the real result.
            // Candidates come fast and then slow down, and the click follows
            // the frames rather than ticking at a flat rate, so the sound
            // decelerates with the picture and climbs in pitch toward the
            // landing.
            int step = reelStep((double) rollElapsed / rollTicks);
            if (step != lastStep[0]) {
                lastStep[0] = step;
                // A big roll goes quiet and lets the aura's score carry the audio.
                if (data.isRollSoundEnabled() && aura[0] == null) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                            (float) plugin.getConfig().getDouble("roll-item.roll-sound-volume", 0.12),
                            (float) (0.9 + 0.7 * step / 19.0));
                }
                // The chime belongs to the moment the drop lands, not to the
                // end of the hold after it. A big drop's aura brings its own.
                if (step >= 19 && !RollAura.isBigDrop(result.getRarity())) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                    chimedOnLanding.add(player.getUniqueId());
                }
                // NOT gated on Rolling Animation. That switch belongs to the
                // reel; this is the cutscene, and it is already gated on the
                // rarity's own aura switch, which is what the player used to
                // ask for it. Gating it twice is how the whole thing can be
                // invisible to somebody with the wrong toggle off.
                if (cinematic[0]) {
                    // A comet is falling, so the reel steps aside. The drop
                    // used to appear at 78% of the roll, which on a fifteen
                    // second Divine put the answer on the screen while the
                    // comet was still in the sky and left the impact with
                    // nothing to reveal. A question mark hangs there for the
                    // whole build-up instead, and the counter under the
                    // comet carries the tension.
                    //
                    // Fed on EVERY reel step, exactly like the branch
                    // below. Leon's own argument, and it is the right one:
                    // "die playerhead moet niet zo moeilijk zijn want dat
                    // is toch gwn hetzelfde als alle andere items bij de
                    // rolling animation". It was set once at creation
                    // before, which is the one way this path differed from
                    // the one that works, and a display whose item never
                    // arrived is an invisible display rather than an empty
                    // one. Handing it the same stack twenty times costs a
                    // clone of a cached item.
                    if (showcase[0] == null) {
                        showcase[0] = RollShowcase.start(plugin, player);
                        RollShowcase previous = showcases.put(player.getUniqueId(), showcase[0]);
                        if (previous != null) previous.cancel();
                    }
                    // Small in the first band and bigger in every one it
                    // survives, so the question mark itself says how far
                    // the roll has climbed.
                    showcase[0].grow(1f + 0.15f * (aura[0] == null ? 0 : aura[0].currentAct()));
                    showcase[0].show(MysteryHead.item(plugin), false);
                } else if (data.isRollAnimationEnabled()) {
                    boolean landed = step >= 19;
                    RollableItem shown = landed ? result : teaser(data, result, step);
                    // A candidate stays up until the next one replaces it, so
                    // the slow frames at the end hang instead of blinking out;
                    // the landing holds until the roll finishes.
                    showRollTitle(player, shown, shiny,
                            landed ? (rollTicks - rollElapsed) * 50L + 400L : 1500L);
                    if (shown != null) {
                        if (showcase[0] == null) {
                            showcase[0] = RollShowcase.start(plugin, player);
                            RollShowcase previous = showcases.put(player.getUniqueId(), showcase[0]);
                            if (previous != null) previous.cancel();
                        }
                        showcase[0].show(buildTaggedItem(shown, shiny), landed);
                    }
                }
            }
        }, 0L, 2L);

        rollingTasks.put(player.getUniqueId(), taskHolder[0]);
    }

    // One-shot "the next roll is shiny" flags for /rngadmin shiny, so the
    // pre-roll can be judged without waiting for a 1 in 2,500.
    private final java.util.Set<UUID> forcedShiny = new java.util.HashSet<>();

    // Rolls whose landing frame played the chime, so finishRoll doesn't play it twice.
    private final java.util.Set<UUID> chimedOnLanding = new java.util.HashSet<>();

    public void forceShinyNext(UUID uuid) {
        forcedShiny.add(uuid);
    }

    /**
     * One-shot "the next roll lands on this rarity", for /rngadmin
     * nextroll.
     *
     * The reveal cost five jars partly because there was no way to run it.
     * /rngadmin aura plays the aura and the comet but not the reel, and
     * /rngadmin roll grants a drop and plays the burst on the spot, so
     * neither of them ever built the showcase and neither was the real
     * thing. Waiting for a one in five thousand Epic to test a change is
     * not a test loop. This makes the next real right-click land where you
     * want it, through startRoll and everything after it.
     */
    private final Map<UUID, Rarity> forcedRarity = new HashMap<>();

    public void forceRarityNext(UUID uuid, Rarity rarity) {
        forcedRarity.put(uuid, rarity);
    }

    // Where the real result lands, as a fraction of the roll. The rest of
    // the roll holds it on screen.
    private static final double REEL_LANDS_AT = 0.78;
    // How hard the candidates slow down. A square curve left the last
    // candidate up for almost a quarter of the roll.
    private static final double REEL_EASE = 1.5;

    /**
     * Which of the 20 reel frames a roll is on. Frames 0 to 18 are
     * candidates on an ease-out curve across the first 78% of the roll, so
     * they arrive quickly and then slow down, the last one up for about a
     * tenth of the roll. Frame 19 is the real result and stays for the last
     * 22%, twice as long as the frame before it.
     */
    private static int reelStep(double t) {
        if (t >= REEL_LANDS_AT) return 19;
        double u = t / REEL_LANDS_AT;
        double eased = 1.0 - Math.pow(1.0 - u, REEL_EASE);
        return Math.min(18, (int) (eased * 19.0));
    }

    /**
     * The candidate for one reel frame. For a Rare or better, the last
     * three frames before the landing climb toward the real rarity, three
     * tiers below, then two, then one, so a good roll visibly builds instead
     * of cutting in from nowhere. The climb never shows anything rarer than
     * what is actually coming.
     */
    private RollableItem teaser(PlayerData data, RollableItem result, int step) {
        Rarity landing = result.getRarity();
        int below = 19 - step;
        if (landing.ordinal() >= Rarity.RARE.ordinal() && below <= 3) {
            RollableItem climb = randomItemOf(Rarity.values()[Math.max(0, landing.ordinal() - below)]);
            if (climb != null) return climb;
        }
        return randomPreview(data);
    }

    private RollableItem randomItemOf(Rarity rarity) {
        java.util.List<RollableItem> pool = new java.util.ArrayList<>();
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            if (item.getRarity() == rarity) pool.add(item);
        }
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    /**
     * One reel frame: the item's name as the title, and its rarity in its
     * own colour with the odds as the subtitle, so the tier reads at a
     * glance even when the item name doesn't give it away.
     */
    private void showRollTitle(Player player, RollableItem item, boolean shiny, long stayMillis) {
        if (item == null) return;

        Component name = LegacyComponentSerializer.legacySection()
                .deserialize(RollFormat.displayName(plugin, item, shiny));
        Component subtitle = LegacyComponentSerializer.legacySection()
                .deserialize(plugin.getRarityManager().style(item.getRarity(), item.getRarity().displayName())
                        + ChatColor.DARK_GRAY + "  ·  " + ChatColor.GRAY + RollFormat.chance(item.getOdds()));

        player.showTitle(Title.title(name, subtitle,
                Title.Times.times(Duration.ZERO, Duration.ofMillis(stayMillis), Duration.ZERO)));
    }

    /** Instant Roll: the whole animation collapses to a single tick. */
    private boolean rollsInstantly(PlayerData data) {
        double chance = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.INSTANT_ROLL)
                + plugin.getPerkManager().totalOf(data, com.spacerng.solrng.perk.PerkStat.INSTANT_ROLL_PERCENT);
        return chance > 0.0 && random.nextDouble() < chance;
    }

    /**
     * Supercharge announces itself before the roll rather than after. The
     * whole appeal of "every 100th roll is a 10x" is the anticipation, and
     * telling the player afterwards throws that away.
     */
    private void announceSupercharge(Player player, double multiplier) {
        String label = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "\u26a1 SUPERCHARGED ROLL \u26a1";
        player.sendMessage(label + ChatColor.RESET + ChatColor.GRAY + "  this one rolls at "
                + ChatColor.LIGHT_PURPLE + String.format("%,.0f", multiplier) + "x"
                + ChatColor.GRAY + " Luck.");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
    }

    /**
     * How long one roll takes for this player right now, in ticks -
     * base duration scaled by their current Speed (skill tree + worn
     * armor). Auto Roll fires on this same cadence, so upgrading Speed
     * speeds up manual and automatic rolls identically.
     */
    public long effectiveRollTicks(PlayerData data) {
        double baseSeconds = plugin.getConfig().getDouble("roll-item.roll-duration-seconds", 5.0);
        // The same Speed /stats shows. PlayerData's own sum left out perks,
        // permanent Speed and Autopilot, so those showed up in /stats but
        // never made a roll any faster.
        double multiplier = com.spacerng.solrng.stats.StatSources.speed(plugin, data).total();
        return Math.max(1L, Math.round((baseSeconds / multiplier) * 20.0));
    }

    /**
     * Flashes a candidate item drawn from the same luck-weighted odds as
     * the real roll - pulling a uniform-random item here made the teaser
     * flash absurd combinations (a 1-in-10M item right before landing on
     * something 1-in-17), which didn't feel believable.
     */
    private RollableItem randomPreview(PlayerData data) {
        if (plugin.getRarityManager().getItems().isEmpty()) return null;
        return plugin.getRarityManager().roll(plugin.getPrestigeManager().effectiveLuck(data));
    }

    private void finishRoll(Player player, PlayerData data, RollableItem result, boolean shiny,
                            boolean auto, boolean cinematic) {
        clearActionBar(player);
        // The landed item stays in front of the player through the payoff.
        RollShowcase showcase = showcases.get(player.getUniqueId());
        if (showcase != null) {
            // A cinematic roll held a question mark for the whole build-up,
            // so this is where it becomes the drop: on the same frame the
            // comet lands, which is the moment the counter stopped on the
            // real odds.
            if (cinematic) {
                showcase.show(buildTaggedItem(result, shiny), true);
            }
            showcase.finish(RollAura.finaleTicks(result.getRarity()) + 30L);
        }
        // The level-up chime would land on the same tick as a big drop's
        // detonation and just clutter it - the aura brings its own. A roll
        // that reached its landing frame already chimed there.
        if (!RollAura.isBigDrop(result.getRarity()) && !chimedOnLanding.remove(player.getUniqueId())) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }

        RollAura aura = activeAuras.remove(player.getUniqueId());
        // Single assignment: the bonus-roll lambda below captures this, so
        // it has to stay effectively final.
        final long finaleTicks = aura == null ? 0L : RollAura.finaleTicks(result.getRarity());
        if (aura != null) {
            aura.reveal();
            // Hold off every other roll until the payoff has finished.
            revealLockUntil.put(player.getUniqueId(), System.currentTimeMillis() + finaleTicks * 50L);
        }

        // Hold the landed item on screen so the reel ends on exactly what
        // the player is handed, shiny markers included. For a big drop the
        // title waits a moment: dropping it over the detonation on the same
        // tick hides the burst the player just sat through the build-up for.
        if (data.isRollAnimationEnabled()) {
            long titleDelay = RollAura.titleDelayTicks(result.getRarity());
            if (titleDelay <= 0) {
                showRollTitle(player, result, shiny, 1500L);
            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) showRollTitle(player, result, shiny, 1500L);
                }, titleDelay);
            }
        }
        // The shiny decided when the roll started is the one the item gets.
        // Letting grantRoll roll it again meant the pre-roll could play and
        // still hand over a plain drop, or the other way round.
        grantRoll(player, data, result, false, shiny, auto);

        // A live boss takes the rarity's damage from the same landing, so
        // rolling counts towards the event exactly like farming does.
        plugin.getBossManager().onRoll(player, result.getRarity());

        // Cosmic Dust falls off the same landing, once the skill tree has
        // unlocked it. It rides here rather than in grantRoll so an admin
        // roll never pays it.
        plugin.getDustManager().onRoll(player, data);

        // A boss can also decide to turn up off the back of a roll.
        plugin.getBossManager().maybeSpawnNaturally(
                com.spacerng.solrng.boss.BossManager.Trigger.ROLL);

        // Server First 10 hangs off the real roll path only, so an admin
        // roll can never take a spot. The event starts the moment the reel
        // lands (V158); waiting for the title as well left seconds of
        // nothing between the roll and its First.
        // Including on a forced roll. V201 held the Server First back on
        // the grounds that a spot cannot be given back; Leon asked for it
        // anyway ("voor de nextroll moet er ook een first komen"), and the
        // spots are his to spend.
        plugin.getFirstTenManager().onRoll(player, result, shiny, finaleTicks);

        // Double Roll skill tree branch: a chance to immediately chain into
        // another free roll, no click required.
        double bonusChance = data.getBonusRollChance()
                + plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.BONUS_ROLL_CHANCE)
                + plugin.getPerkManager().totalOf(data, com.spacerng.solrng.perk.PerkStat.BONUS_ROLL_PERCENT);
        if (bonusChance > 0.0 && random.nextDouble() < bonusChance) {
            if (!auto) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Bonus Roll! " + ChatColor.RESET
                        + ChatColor.GRAY + "Rolling again...");
            }
            // Waits out the finale rather than being swallowed by the lock.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !isRolling(player.getUniqueId())) {
                    startRoll(player);
                }
            }, finaleTicks + 1L);
        }
    }

    /** What a roll paid, for the end of an action bar line; empty when Vault paid nothing. */
    private static String moneyLine(double moneyEarned) {
        return moneyEarned > 0
                ? com.spacerng.solrng.gui.Currency.MONEY.numberColour()
                        + "  +" + RollFormat.abbreviate(Math.round(moneyEarned))
                        + com.spacerng.solrng.gui.Currency.MONEY.colour() + " Money"
                : "";
    }

    private void sendActionBar(Player player, String text) {
        // Sent as a real component rather than as legacy text.
        // TextComponent carries the section codes through untouched, and
        // the client's own legacy reader does not understand the hex form
        // a gradient is written in: it read each of the six hex digits as
        // its own old colour code, which is why a gradient name came out
        // of the action bar in the wrong colours while the same name was
        // right everywhere else.
        player.sendActionBar(net.kyori.adventure.text.serializer.legacy
                .LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private void clearActionBar(Player player) {
        sendActionBar(player, "");
    }

    /**
     * Gives the player their rolled item (or converts it straight to points
     * if they've toggled auto-convert for that rarity), then broadcasts it
     * if it meets the configured rarity threshold. Either way, a preview
     * item is built so chat messages can show a hoverable tooltip of it.
     */
    public void grantRoll(Player player, PlayerData data, RollableItem result, boolean silent) {
        grantRoll(player, data, result, silent, rollShiny(data));
    }

    public void grantRoll(Player player, PlayerData data, RollableItem result, boolean silent, boolean shiny) {
        grantRoll(player, data, result, silent, shiny, false);
    }

    /**
     * {@code auto} is an Auto Roll landing: it reports on the action bar
     * instead of chat, "[Auto Roll] Shroomlight". A first find still gets
     * its chat line. Server broadcasts for big drops go out as normal.
     */
    public void grantRoll(Player player, PlayerData data, RollableItem result, boolean silent, boolean shiny,
                          boolean auto) {
        data.addRoll();
        Rarity rarity = result.getRarity();
        ItemStack previewItem = buildTaggedItem(result, shiny);

        // Read before the discovery is registered, so a first find pays the
        // normal rate and only genuine repeats get the Duplicate bonus.
        boolean duplicate = data.hasDiscovered(result.getDisplayName());
        double moneyEarned = depositRollMoney(player, data, result, duplicate);
        maybeKeyRoll(player, data, silent);

        // A shiny is only ever auto-converted by its OWN switch. The normal
        // per-rarity toggles are set for the common version of a drop, and
        // letting one of those swallow a 1-in-100 find would be the single
        // most annoying thing the plugin could do.
        boolean converting = shiny ? data.isAutoConvertShiny() : data.isAutoConverting(rarity);

        if (converting) {
            if (shiny) {
                data.addBankedShiny(rarity, 1L);
            } else {
                long banked = data.addBankedDrops(rarity, 1L, plugin.convertCap(data));
                if (banked == 0) {
                    warnVaultFull(player, rarity);
                } else {
                    data.addConverted(rarity, banked);
                }
            }
            if (!silent && !auto) {
                // Auto-convert is a bulk mode: the full name and the odds
                // on every single roll is noise you asked for none of.
                player.sendMessage(ChatColor.AQUA + "\u26a1 " + ChatColor.GRAY + "You rolled "
                        + plugin.getRarityManager().style(rarity, rarity.displayName())
                        + ChatColor.DARK_GRAY + " (auto converted)");
            }
        } else {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(previewItem.clone());
            if (!overflow.isEmpty()) {
                // Nothing goes on the floor. A pile of loose drops under an
                // auto-rolling player is an entity leak and a free-for-all
                // for whoever walks past. Losing them is the honest
                // outcome, as long as we say so clearly.
                warnInventoryFull(player);
            }
            if (!silent && !auto && data.isDropMessageEnabled(result.getRarity())) {
                sendHoverable(player, previewItem, RollFormat.personalRollLine(plugin, result, shiny));
            }
        }

        if (!silent && auto) {
            sendActionBar(player, RollFormat.autoRollLine(plugin, result, shiny) + moneyLine(moneyEarned));
        } else if (!silent) {
            // Money green, like it is everywhere else. Gold here made the
            // one currency with its own colour the only one not using it.
            String moneyText = moneyEarned > 0
                    ? com.spacerng.solrng.gui.Currency.MONEY.numberColour()
                            + "  +" + RollFormat.abbreviate(Math.round(moneyEarned))
                            + com.spacerng.solrng.gui.Currency.MONEY.colour() + " Money"
                    : "";
            sendActionBar(player, RollFormat.displayName(plugin, result, shiny)
                    + ChatColor.GRAY + "  " + RollFormat.compactOdds(result.getOdds()) + moneyText);
        }

        plugin.getPassManager().awardRoll(player, data, rarity);
        if (data.tickPotion()) {
            player.sendMessage(ChatColor.DARK_GRAY + "Your draught has run out.");
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 0.7f);
        }

        // A new find is still news on Auto Roll, so it reaches chat; only
        // the action bar stays with the Auto Roll line.
        maybeRegisterDiscovery(player, data, result, silent, shiny, auto);
        maybeBroadcast(player, result, previewItem, shiny);
    }

    /**
     * Every roll also pays real Money (Vault) on top of the item itself -
     * money = odds x configured multiplier, so rarer items pay out more.
     * Returns 0 if Vault/an economy plugin isn't installed.
     */
    /** Key Roll: a small chance per roll to turn up a crate key, into the inventory or the stash. */
    private void maybeKeyRoll(Player player, PlayerData data, boolean silent) {
        double chance = plugin.getSkillTreeManager().totalOf(data, SkillNode.Effect.KEY_ROLL);
        if (chance <= 0.0 || random.nextDouble() >= chance) return;
        var consumables = plugin.getConsumableManager();
        var key = consumables.get(plugin.getConfig().getString("key-roll-key", "crate_key"));
        if (key == null) return;
        consumables.give(player, key, 1);
        if (!silent) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Key Roll! " + ChatColor.RESET
                    + ChatColor.GRAY + "You found a " + consumables.styledName(key) + ChatColor.GRAY + ".");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.4f);
        }
    }

    private double depositRollMoney(Player player, PlayerData data, RollableItem result, boolean duplicate) {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) return 0.0;

        // Base rate, Nova Core, prestige upgrades and Money skills all
        // live in StatSources so /stats can show the same figure this pays.
        double multiplier = com.spacerng.solrng.stats.StatSources.money(plugin, data).total();
        double dupe = duplicate
                ? plugin.getSkillTreeManager().multiplierOf(data, SkillNode.Effect.DUPLICATE_BONUS)
                        + plugin.getPerkManager().totalOf(data, com.spacerng.solrng.perk.PerkStat.DUPLICATE_PERCENT)
                : 1.0;

        // Explorer: a drop new to the index pays more.
        double explorer = duplicate ? 1.0
                : plugin.getSkillTreeManager().multiplierOf(data, SkillNode.Effect.EXPLORER);
        double money = result.getOdds() * multiplier * dupe * explorer;
        registration.getProvider().depositPlayer(player, money);
        return money;
    }

    /**
     * Sends a chat line where hovering over it shows the real item tooltip
     * (name, lore - Rarity/Chance) via Minecraft's built-in hover-item
     * component. No resource pack needed; this is the same mechanism as
     * shift-clicking an item into chat.
     */
    private void sendHoverable(Player player, ItemStack item, String legacyText) {
        Component message = LegacyComponentSerializer.legacySection().deserialize(legacyText)
                .hoverEvent(item.asHoverEvent());
        player.sendMessage(message);
    }

    /**
     * The first time a player rolls a given item, it's added to their
     * /index and grants a small permanent luck bonus - collecting every
     * item is itself a form of progression.
     */
    private void maybeRegisterDiscovery(Player player, PlayerData data, RollableItem result, boolean silent,
                                        boolean shiny, boolean auto) {
        boolean newBase = !data.hasDiscovered(result.getDisplayName());
        boolean newShiny = shiny && !data.hasDiscoveredShiny(result.getDisplayName());
        if (!newBase && !newShiny) return;

        if (newBase) {
            data.markDiscovered(result.getDisplayName());
            plugin.getFoundCounts().record(result.getDisplayName());
        }
        if (newShiny) data.markShinyDiscovered(result.getDisplayName());
        if (silent) return;

        String notice = (newShiny
                ? ChatColor.AQUA + "" + ChatColor.BOLD + "New shiny  "
                : ChatColor.GREEN + "" + ChatColor.BOLD + "New  ")
                + ChatColor.RESET + RollFormat.displayName(plugin, result, shiny)
                + ChatColor.GRAY + " added to your index "
                + ChatColor.DARK_AQUA + "(" + String.format("%.2f", result.getLuckMultiplier()) + "x Luck)";
        player.sendMessage(notice);
        if (!auto) sendActionBar(player, notice);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, newShiny ? 1.8f : 1.3f);
    }

    public ItemStack buildTaggedItem(RollableItem result) {
        return buildTaggedItem(result, false);
    }

    /**
     * The physical, PDC-tagged drop item. A shiny carries an extra tag, an
     * enchant glint and its own name styling, so it's obvious in an
     * inventory without having to read the tooltip.
     */
    public ItemStack buildTaggedItem(RollableItem result, boolean shiny) {
        ItemStack item = new ItemStack(result.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(RollFormat.displayName(plugin, result, shiny));
        meta.setLore(RollFormat.lore(plugin, result, shiny));
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, result.getRarity().name());
        meta.getPersistentDataContainer().set(rollNameKey, PersistentDataType.STRING, result.getDisplayName());
        if (shiny) {
            meta.getPersistentDataContainer().set(shinyKey, PersistentDataType.BYTE, (byte) 1);
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void maybeBroadcast(Player player, RollableItem result, ItemStack previewItem, boolean shiny) {
        // Discord has its own list of rarities, so it's asked before the in-game threshold.
        plugin.getDiscordWebhook().drop(player.getName(), result, shiny);

        String minRarityName = plugin.getConfig().getString("broadcast.min-rarity-to-broadcast", "EPIC");
        Rarity minRarity;
        try {
            minRarity = Rarity.valueOf(minRarityName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            minRarity = Rarity.EPIC;
        }

        // A shiny is a 1-in-100 on top of whatever the drop already was, so
        // it's worth announcing even at a rarity that normally isn't.
        boolean shinyWorthy = shiny && plugin.getConfig().getBoolean("shiny.broadcast", true);
        if (!shinyWorthy && result.getRarity().ordinal() < minRarity.ordinal()) return;

        Component banner = LegacyComponentSerializer.legacySection()
                .deserialize(RollFormat.broadcastBanner(plugin, player.getName(), result, shiny))
                .hoverEvent(previewItem.asHoverEvent());

        // Announced one player at a time rather than server-wide, because
        // muting a rarity is a per-player setting. The person who rolled it
        // always sees their own - the mute is for other people's noise.
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)
                    && !plugin.getPlayerDataManager().get(online.getUniqueId())
                            .isBroadcastEnabled(result.getRarity())) {
                continue;
            }
            online.sendMessage(banner);
        }
        Bukkit.getConsoleSender().sendMessage(banner);
    }
}
