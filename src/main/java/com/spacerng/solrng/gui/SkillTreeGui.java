package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.player.SkillNode;
import com.spacerng.solrng.player.SkillTreeManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A 6x9 skill tree, drawn entirely from config. Every node declares its
 * own page, (column, row) and icon, so adding a skill — or a whole extra
 * page, or a whole second tree like the farming one — is a config change
 * rather than a code change.
 *
 * Slots in the tree's shape that no node has claimed render as "???"
 * placeholders. That's deliberate: the outline of everything still to come
 * is visible from the first time a player opens the menu, which makes the
 * tree feel like a map rather than a list that grows.
 */
public class SkillTreeGui {

    private static final int STATS_SLOT = 53;
    private static final int PREV_SLOT = 0;
    private static final int NEXT_SLOT = 8;

    public static NamespacedKey nodeIdKey(SolRNGPlugin plugin) {
        return new NamespacedKey(plugin, "solrng_node_id");
    }

    public static int prevSlot() {
        return PREV_SLOT;
    }

    public static int nextSlot() {
        return NEXT_SLOT;
    }

    public static Inventory build(SolRNGPlugin plugin, Player player) {
        return build(plugin, player, "skilltree", 0);
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, String tree, int page) {
        boolean farming = "farmtree".equals(tree);
        SkillTreeManager manager = plugin.getSkillTreeManager();
        int pageCount = manager.pageCount(tree);
        page = Math.max(0, Math.min(page, pageCount - 1));

        SkillTreeHolder holder = new SkillTreeHolder();
        holder.setTree(tree);
        holder.setPage(page);

        String pageName = manager.pageName(tree, page);
        String title = (farming
                ? ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Farming Skills"
                : ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Skill Tree")
                + ChatColor.GRAY + " — " + (pageName.isEmpty() ? "Page " + (page + 1) : pageName);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        ItemStack filler = glassFiller();
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, filler);
        }

        // The silhouette is the tree's reserved slots plus wherever this
        // page's own nodes sit. Every page is therefore the same shape, and
        // an empty page reads as visible room to grow rather than as a
        // different menu.
        List<SkillNode> nodes = manager.getNodes(tree, page);
        Set<Integer> shape = new HashSet<>(manager.reservedSlots(tree));
        Set<Integer> placed = new HashSet<>();
        for (SkillNode node : nodes) {
            if (node.getSlot() < 0 || node.getSlot() >= 54) continue;
            shape.add(node.getSlot());
            boolean reqMet = manager.requirementMet(data, node);
            inv.setItem(node.getSlot(), reqMet
                    ? buildNodeIcon(plugin, player, data, node)
                    : lockedNode(plugin, data, node));
            placed.add(node.getSlot());
        }

        for (int slot : shape) {
            if (!placed.contains(slot)) {
                inv.setItem(slot, placeholderNode());
            }
        }

        inv.setItem(STATS_SLOT, buildWalletPanel(plugin, player, data, farming));
        if (page > 0) {
            inv.setItem(PREV_SLOT, pageButton(Material.SPECTRAL_ARROW, "◀ Previous",
                    manager.pageName(tree, page - 1), page, pageCount));
        }
        if (page < pageCount - 1) {
            inv.setItem(NEXT_SLOT, pageButton(Material.ARROW, "Next ▶",
                    manager.pageName(tree, page + 1), page + 2, pageCount));
        }
        return inv;
    }

    private static ItemStack pageButton(Material material, String label, String name, int shownPage, int total) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + label);
        List<String> lore = new ArrayList<>();
        if (!name.isEmpty()) {
            lore.add(Lore.state(name));
            lore.add("");
        }
        lore.add(Lore.stat(ChatColor.AQUA, "Page", shownPage + " / " + total));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildNodeIcon(SolRNGPlugin plugin, Player player, PlayerData data, SkillNode node) {
        boolean leveled = node.isLeveled();
        int level = leveled ? data.getNodeLevel(node.getId()) : 0;
        boolean maxed = leveled && level >= node.getMaxLevel();
        boolean started = leveled ? level > 0 : data.hasUnlocked(node.getId());
        boolean complete = leveled ? maxed : started;
        ChatColor accent = complete ? ChatColor.GREEN : started ? ChatColor.YELLOW : ChatColor.AQUA;

        Currency wallet = node.usesTokens() ? Currency.TOKENS : Currency.COINS;
        long price = Math.round(plugin.getSkillTreeManager().priceFor(data, node));
        long balance = node.usesTokens() ? data.getTokens() : balanceOf(player);
        boolean affordable = plugin.getSkillTreeManager().canAfford(player, data, node);

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state(leveled ? "upgrade" : "unlock"));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Effect"));
        lore.addAll(describeEffect(node, level));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Information"));
        if (leveled) {
            lore.add(Lore.stat(ChatColor.AQUA, "Level", level + " / " + node.getMaxLevel()));
            lore.add(Lore.bar(level / (double) node.getMaxLevel()));
        }
        if (!complete) {
            lore.add((affordable ? ChatColor.YELLOW : ChatColor.RED) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Cost: " + wallet.price(price, affordable));
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " " + ChatColor.DARK_GRAY + "You have "
                    + wallet.amount(balance));
        }
        lore.add("");
        if (complete) {
            lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + (leveled ? "MAXED" : "UNLOCKED"));
        } else if (affordable) {
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + (leveled ? "CLICK TO UPGRADE" : "CLICK TO UNLOCK"));
        } else {
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "NOT ENOUGH " + wallet.label().toUpperCase());
        }

        Material material = Material.matchMaterial(node.getIcon());
        if (material == null) material = Material.RECOVERY_COMPASS;

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        // A leveled skill wears its level as a badge, the way the farming
        // enchants do; a one-time unlock gets the framed title instead. The
        // two shapes are how you tell at a glance which kind of thing a
        // slot is without reading anything.
        meta.setDisplayName(leveled
                ? ChatColor.DARK_GRAY + "[" + accent + level + Lore.STAR + ChatColor.DARK_GRAY + "] "
                        + accent + ChatColor.BOLD + node.getDisplay()
                : Lore.title(accent, node.getDisplay()));
        // Glint instead of a colour-coded dye, so the skill keeps its own
        // icon while still reading as "done" at a glance.
        meta.setEnchantmentGlintOverride(complete ? Boolean.TRUE : null);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(nodeIdKey(plugin), PersistentDataType.STRING, node.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * A real node whose prerequisite isn't met yet. It names what's in the
     * way rather than hiding the slot outright — knowing a skill exists and
     * what stands between you and it is most of what makes a tree readable.
     */
    private static ItemStack lockedNode(SolRNGPlugin plugin, PlayerData data, SkillNode node) {
        SkillNode parent = plugin.getSkillTreeManager().get(node.getRequires());
        ItemStack icon = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "???"));
        meta.setLore(List.of(
                Lore.state("locked"),
                "",
                Lore.line(ChatColor.RED, "Buy the skill before it first."),
                "",
                ChatColor.RED + "" + ChatColor.BOLD + "LOCKED",
                ChatColor.RED + Lore.BULLET + " " + ChatColor.GRAY + "Needs " + ChatColor.YELLOW
                        + (parent == null ? "an earlier skill" : parent.getDisplay())));
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * Undefined, unclickable reserved slot — no PersistentData tag, so
     * clicking it is a no-op in GuiListener.
     */
    private static ItemStack placeholderNode() {
        ItemStack icon = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "???"));
        meta.setLore(List.of(
                Lore.state("empty"),
                "",
                ChatColor.DARK_GRAY + Lore.BULLET + " Reserved for a future skill."));
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack glassFiller() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildWalletPanel(SolRNGPlugin plugin, Player player, PlayerData data, boolean farming) {
        Currency wallet = farming ? Currency.TOKENS : Currency.COINS;
        long balance = farming ? data.getTokens() : balanceOf(player);

        ItemStack stats = new ItemStack(farming ? Material.WHEAT : Material.GOLD_INGOT);
        ItemMeta meta = stats.getItemMeta();
        meta.setDisplayName(Lore.title(wallet.colour(), player.getName()));

        List<String> lore = new ArrayList<>();
        lore.add(Lore.state("wallet"));
        lore.add("");
        lore.add(wallet.colour() + Lore.BULLET + " " + wallet.amount(balance));
        lore.add("");
        lore.add(Lore.section(ChatColor.AQUA, "Your stats"));
        lore.add(Lore.stat(ChatColor.GREEN, "Luck", "+"
                + String.format("%.2f", plugin.getPrestigeManager().effectiveLuck(data) * 100.0) + "%"));
        lore.add(Lore.stat(ChatColor.YELLOW, "Speed",
                String.valueOf(Math.round(data.getEffectiveRollSpeedMultiplier() * 100))));
        lore.add(Lore.stat(ChatColor.AQUA, "Skills owned", String.valueOf(ownedCount(plugin, data))));
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " "
                + (farming ? "Farm skills are bought with Tokens."
                           : "Skills are bought with Coins."));
        meta.setLore(lore);
        stats.setItemMeta(meta);
        return stats;
    }

    /** Levels bought across every tree — one number for "how far in am I". */
    private static int ownedCount(SolRNGPlugin plugin, PlayerData data) {
        int total = 0;
        for (SkillNode node : plugin.getSkillTreeManager().getNodes().values()) {
            total += plugin.getSkillTreeManager().levelOf(data, node);
        }
        return total;
    }

    private static long balanceOf(Player player) {
        var registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) return 0L;
        return Math.round(registration.getProvider().getBalance(player));
    }

    /**
     * The human-readable "what does this do". Leveled nodes show both the
     * per-level value and the running total, so a half-bought skill still
     * answers "what am I getting right now" without arithmetic.
     */
    private static List<String> describeEffect(SkillNode node, int level) {
        boolean leveled = node.isLeveled();
        String target = node.getTarget() == null ? "" : prettify(node.getTarget());
        double value = node.getValue();

        return switch (node.getEffect()) {
            case LUCK -> scaled(ChatColor.GREEN, "+" + pct(value) + "% Luck", pct(value * level) + "%", leveled);
            case ROLL_SPEED -> scaled(ChatColor.YELLOW, "+" + pct(value) + " Speed", "+" + pct(value * level), leveled);
            case BONUS_ROLL_CHANCE -> scaled(ChatColor.LIGHT_PURPLE,
                    "+" + pct(value) + "% chance of a second free roll", pct(value * level) + "%", leveled);
            case INSTANT_ROLL -> scaled(ChatColor.AQUA,
                    "+" + pct(value) + "% chance a roll resolves instantly", pct(value * level) + "%", leveled);
            case SHINY_CHANCE -> scaled(ChatColor.AQUA,
                    "+" + pct(value) + "% Shiny chance", "+" + pct(value * level) + "%", leveled);
            case LUCK_PER_DISCOVERY -> scaled(ChatColor.GREEN,
                    "+" + trim(value * 100) + "% Luck per Index entry found",
                    "+" + trim(value * level * 100) + "% each", leveled);
            case LUCK_PER_PRESTIGE -> scaled(ChatColor.GREEN,
                    "+" + pct(value) + "% Luck per Prestige held",
                    "+" + pct(value * level) + "% each", leveled);
            case STARFORGE_POWER -> scaled(ChatColor.LIGHT_PURPLE,
                    "+" + pct(value) + "% to your Starforge's Luck",
                    "+" + pct(value * level) + "%", leveled);
            case ARMOR_POWER -> scaled(ChatColor.AQUA,
                    "+" + pct(value) + "% to the Luck from worn armor",
                    "+" + pct(value * level) + "%", leveled);

            case MONEY_MULTIPLIER -> scaled(ChatColor.GOLD,
                    "+" + pct(value) + "% Coins per roll", "+" + pct(value * level) + "%", leveled);
            case MONEY_PER_LEVEL -> List.of(
                    ChatColor.GOLD + "▎ +" + pct(value) + "% Coins per /prestige level",
                    ChatColor.DARK_GRAY + "▎ Grows every time you level up.");
            case TOKEN_GAIN -> scaled(ChatColor.YELLOW,
                    "+" + pct(value) + "% Tokens from farming", "+" + pct(value * level) + "%", leveled);
            case GEM_MULTIPLIER -> scaled(ChatColor.AQUA,
                    "+" + pct(value) + "% Gems from farming", "+" + pct(value * level) + "%", leveled);
            case DUPLICATE_BONUS -> scaled(ChatColor.GOLD,
                    "+" + pct(value) + "% Coins on a drop you already own",
                    "+" + pct(value * level) + "%", leveled);
            case CONVERT_BONUS -> scaled(ChatColor.AQUA,
                    "+" + pct(value) + "% chance a converted drop banks twice",
                    pct(value * level) + "%", leveled);
            case DAILY_BONUS -> scaled(ChatColor.GREEN,
                    "+" + pct(value) + "% from every /daily reward", "+" + pct(value * level) + "%", leveled);
            case MILESTONE_BONUS -> scaled(ChatColor.GREEN,
                    "+" + pct(value) + "% from every /milestones reward", "+" + pct(value * level) + "%", leveled);
            case PASS_XP -> scaled(ChatColor.GOLD,
                    "+" + pct(value) + "% Battle Pass XP", "+" + pct(value * level) + "%", leveled);

            case SUPERCHARGE -> List.of(
                    ChatColor.LIGHT_PURPLE + "▎ Every " + ChatColor.WHITE + String.format("%,d", node.getInterval())
                            + ChatColor.LIGHT_PURPLE + " rolls, one roll at "
                            + ChatColor.WHITE + String.format("%,.0f", value) + "x" + ChatColor.LIGHT_PURPLE + " Luck",
                    ChatColor.DARK_GRAY + "▎ Announced before it fires.");
            case PITY -> List.of(
                    ChatColor.LIGHT_PURPLE + "▎ " + String.format("%,d", node.getInterval())
                            + " rolls without a " + target + " or better",
                    ChatColor.LIGHT_PURPLE + "▎ forces the next roll to be one",
                    ChatColor.DARK_GRAY + "▎ A lucky roll is never downgraded.");
            case NOVA_DISCOUNT -> scaled(ChatColor.YELLOW,
                    "-" + pct(value) + "% Nova Core climb cost", "-" + pct(value * level) + "%", leveled);
            case NOVA_SAFETY -> scaled(ChatColor.AQUA,
                    "+" + pct(value) + "% chance a failed Nova climb holds",
                    pct(value * level) + "%", leveled);

            case AUTO_ROLL -> gate("Rolls automatically at your own Speed");
            case UNLOCK_CONVERT -> gate("Unlocks /convert — turn drops into stored ones");
            case UNLOCK_AUTO_CONVERT -> gate("Unlocks the auto-convert switches in /convert");
            case UNLOCK_FARMING -> gate("Unlocks the farm and the Farmer's Hoe");
            case UNLOCK_ARMOR -> gate("Unlocks the /armor shop");
            case UNLOCK_POTION -> gate("Unlocks the Potion system (coming soon)");
            case UNLOCK_SHINY -> gate("Unlocks Shiny drops — 1 in 100 rolls");
            case UNLOCK_INDEX_LUCK -> gate("Lets you equip a tag for its Index Luck");
            case UNLOCK_ARTIFACT -> gate("Unlocks the Artifact shop (coming soon)");
            case UNLOCK_PRIVATE_VAULT -> gate("Unlocks your Private Vault — /pv");
            case UNLOCK_PASS -> gate("Unlocks the Battle Pass — /pass");

            case UNLOCK_CROP -> gate("Unlocks " + target + " on the farm");
            case UNLOCK_SHARDS -> gate("Farm crops start paying Gems");
            case UNLOCK_ENCHANT -> List.of(ChatColor.LIGHT_PURPLE + "▎ Unlocks the " + target + " hoe enchant");
            case ENCHANT_POWER -> scaled(ChatColor.LIGHT_PURPLE,
                    "+1 " + target + " level per rank", "+" + level, leveled);
            case TOKEN_MULTIPLIER -> scaled(ChatColor.YELLOW,
                    "+" + trim(value) + "x farm Tokens", "+" + trim(value * level) + "x", leveled);
            case FARM_SPEED -> scaled(ChatColor.GREEN,
                    pct(value) + "% faster regrow", pct(value * level) + "%", leveled);
        };
    }

    /** "+5% Luck per level  (+20% now)" — one shape for every leveled stat. */
    private static List<String> scaled(ChatColor colour, String per, String now, boolean leveled) {
        if (!leveled) {
            return List.of(colour + "▎ " + per);
        }
        return List.of(colour + "▎ " + per + " per level",
                ChatColor.DARK_GRAY + "▎ " + now + " right now");
    }

    private static List<String> gate(String text) {
        return List.of(ChatColor.AQUA + "▎ " + text);
    }

    /** "token_greed" -> "Token Greed", for lore that names a target. */
    private static String prettify(String raw) {
        StringBuilder out = new StringBuilder();
        for (String word : raw.toLowerCase().split("[_\\s]+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static int pct(double fraction) {
        return (int) Math.round(fraction * 100);
    }

    /** Keeps the decimals only when there are any — "0.4", not "0.40". */
    private static String trim(double value) {
        if (Math.abs(value - Math.round(value)) < 0.001) return String.valueOf(Math.round(value));
        return String.format("%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
