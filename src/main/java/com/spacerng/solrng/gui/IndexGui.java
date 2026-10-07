package com.spacerng.solrng.gui;

import com.spacerng.solrng.SolRNGPlugin;
import com.spacerng.solrng.player.PlayerData;
import com.spacerng.solrng.rarity.Rarity;
import com.spacerng.solrng.rarity.RollFormat;
import com.spacerng.solrng.rarity.RollableItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.Statistic;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Collection log: every rollable item, greyed out until the player has
 * actually rolled it at least once.
 *
 * V311: the top row is two ladders and your head. It used to be seven
 * rarity tabs plus a shiny switch, eight buttons answering two questions,
 * and Leon asked for the stepping block /options got in V310 here as
 * well. Index Mode steps through the normal, shiny and secret collections;
 * Index Tier steps through all tiers and then one tier at a time, and
 * carries what the selected tier has collected and what finishing it
 * pays. Both take a right click as one step back.
 *
 * The 36 slots from the third row page through whatever is selected.
 */
public class IndexGui {

    // Row 1 carries the two ladders and your head, row 2 a glass divider,
    // so entries fill rows 3-6.
    private static final int PAGE_SIZE = 36;
    private static final int ENTRY_START_SLOT = 18;
    private static final int DIVIDER_ROW_START = 9;
    // V311: the top row was seven rarity tabs plus a shiny switch, eight
    // buttons for two questions. It is two blocks now, which is the same
    // pattern /options got in V310, and it leaves the row readable.
    // V330: the three collections are three buttons, not one block that
    // has to be stepped through, Leon's call. Normal, Shiny and Secret sit
    // next to each other and say which one you are looking at; the tier
    // ladder keeps stepping, because seven rarities will not fit as
    // buttons and it is a sort, not a collection.
    private static final int[] MODE_SLOTS = {0, 1, 2};
    private static final int MODE_SLOT = 0;
    private static final int RARITY_SLOT = 4;
    private static final int PROGRESS_SLOT = 8;

    /** Which collection the grid is scoring (V311). */
    public enum Mode {
        NORMAL("Normal Index", Material.HEART_OF_THE_SEA),
        SHINY("Shiny Index", Material.NAUTILUS_SHELL),
        SECRET("Secret Index", Material.END_PORTAL_FRAME);

        private final String label;
        private final Material icon;

        Mode(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        public String label() {
            return label;
        }

        /** Just "Normal", "Shiny", "Secret" for the breadcrumb. */
        public String shortLabel() {
            return label.replace(" Index", "");
        }

        public Material icon() {
            return icon;
        }

        public Mode step(boolean back) {
            Mode[] all = values();
            int moved = ordinal() + (back ? -1 : 1);
            if (moved < 0) moved = all.length - 1;
            if (moved >= all.length) moved = 0;
            return all[moved];
        }
    }

    public static int modeSlot() {
        return MODE_SLOT;
    }

    /** The collection a top row button stands for, or null. */
    public static Mode modeAt(int slot) {
        for (int i = 0; i < MODE_SLOTS.length; i++) {
            if (MODE_SLOTS[i] == slot) return Mode.values()[i];
        }
        return null;
    }

    public static int raritySlot() {
        return RARITY_SLOT;
    }

    /**
     * One collection, one button (V330).
     *
     * The button for the collection you are in is glinted and says so;
     * the other two say what they hold and that a click opens them.
     * Secret carries its Prestige floor, because the requirement should
     * be readable here rather than only in the message that turns you
     * away.
     */
    static ItemStack modeBlock(SolRNGPlugin plugin, Player player, PlayerData data, Mode mode) {
        return modeButton(plugin, data, mode, mode);
    }

    static ItemStack modeButton(SolRNGPlugin plugin, PlayerData data, Mode button, Mode showing) {
        int total = plugin.getRarityManager().getItems().size();
        int found = switch (button) {
            case SHINY -> data.getDiscoveredShiny().size();
            case SECRET -> data.getSecretsFound().size();
            case NORMAL -> data.getDiscoveredItems().size();
        };
        int secretTotal = plugin.getRealmManager().secrets().size();
        int minPrestige = plugin.getConfig().getInt("secret-realm.min-prestige", 0);
        boolean allowed = data.getPrestige() >= minPrestige;
        boolean here = button == showing;

        ItemStack item = new ItemStack(button.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(here ? ChatColor.GREEN : ChatColor.AQUA, button.label()));

        List<String> lore = new ArrayList<>();
        switch (button) {
            case NORMAL -> lore.add(Lore.line(ChatColor.GRAY, "Every drop you have rolled."));
            case SHINY -> lore.add(Lore.line(ChatColor.GRAY, "The shiny copy of the same drops."));
            case SECRET -> lore.add(Lore.line(ChatColor.GRAY, "Secrets out of the Secret Realm."));
        }
        lore.add("");
        if (button == Mode.SECRET) {
            lore.add(Lore.stat(ChatColor.AQUA, "Found", found + " / " + Math.max(found, secretTotal)));
            lore.add(allowed
                    ? Lore.stat(ChatColor.GREEN, "Prestige", data.getPrestige() + " of " + minPrestige)
                    : Lore.requirement("Prestige", String.valueOf(data.getPrestige()),
                            String.valueOf(minPrestige), false));
        } else {
            lore.add(Lore.stat(ChatColor.AQUA, button == Mode.SHINY ? "Shinies" : "Found",
                    found + " / " + total));
            lore.add(Lore.bar(total <= 0 ? 0.0 : (double) found / total));
        }
        lore.add("");
        lore.add(here
                ? ChatColor.GREEN + "" + ChatColor.BOLD + "You are here"
                : ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to open");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(here ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * The tier ladder: all tiers, then one tier at a time.
     *
     * It carries what the old per-rarity tab carried, the tier's counts
     * and what finishing it pays, but only for the tier being shown. That
     * is the one a player is looking at, and seven tabs each repeating the
     * block was most of why the top row read as clutter.
     */
    private static ItemStack buildRarityBlock(SolRNGPlugin plugin, PlayerData data, Rarity filter) {
        var rarities = plugin.getRarityManager();
        var prestige = plugin.getPrestigeManager();

        List<String> steps = new ArrayList<>();
        steps.add("All tiers");
        for (Rarity rarity : Rarity.values()) steps.add(rarity.displayName());
        int index = filter == null ? 0 : filter.ordinal() + 1;

        ItemStack item = new ItemStack(filter == null ? Material.BOOKSHELF : tabMaterial(filter));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(ChatColor.AQUA, "Index Tier") + ChatColor.DARK_GRAY + " - "
                + (filter == null
                        ? ChatColor.GREEN.toString() + ChatColor.BOLD + "All tiers"
                        : rarities.style(filter, filter.displayName())));

        List<String> lore = new ArrayList<>();
        int total = filter == null ? rarities.getItems().size() : rarities.countIn(filter);
        int found = filter == null ? data.getDiscoveredItems().size() : rarities.foundIn(data, filter, false);
        int shiny = filter == null ? data.getDiscoveredShiny().size() : rarities.foundIn(data, filter, true);
        boolean done = total > 0 && found >= total;
        boolean shinyDone = total > 0 && shiny >= total;

        lore.add(Lore.section(ChatColor.AQUA, "Collected"));
        lore.add(Lore.requirement("Found", String.valueOf(found), String.valueOf(total), done));
        lore.add(Lore.requirement("Shiny", String.valueOf(shiny), String.valueOf(total), shinyDone));
        lore.add(Lore.bar(total <= 0 ? 0.0 : (double) found / total));
        if (filter != null) {
            double perRarity = rarities.completionFor(filter, prestige.getIndexCompletionPerRarity());
            double perShiny = prestige.getIndexCompletionPerShiny();
            lore.add("");
            lore.add(Lore.section(ChatColor.GREEN, "Completion reward"));
            lore.add((done ? ChatColor.GREEN : ChatColor.DARK_GRAY) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Every one found: "
                    + (done ? ChatColor.GREEN : ChatColor.WHITE) + trim(perRarity) + "x Luck"
                    + (done ? "  " + ChatColor.GREEN + Lore.TICK : ""));
            lore.add((shinyDone ? ChatColor.GREEN : ChatColor.DARK_GRAY) + Lore.BULLET + " "
                    + ChatColor.GRAY + "Every one shiny: "
                    + (shinyDone ? ChatColor.GREEN : ChatColor.WHITE) + trim(perShiny) + "x Luck"
                    + (shinyDone ? "  " + ChatColor.GREEN + Lore.TICK : ""));
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " The shiny reward replaces the other.");
        }
        lore.add("");
        lore.add(Lore.section(ChatColor.YELLOW, "Showing"));
        lore.addAll(Stepper.breadcrumb(steps, index, 4));
        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to step up");
        lore.add(Lore.footnote("Right-click steps back."));
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(done ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /** Steps the tier filter: null is all tiers, then one tier at a time. */
    public static Rarity stepFilter(Rarity filter, boolean back) {
        Rarity[] all = Rarity.values();
        int index = filter == null ? 0 : filter.ordinal() + 1;
        int moved = index + (back ? -1 : 1);
        if (moved < 0) moved = all.length;
        if (moved > all.length) moved = 0;
        return moved == 0 ? null : all[moved - 1];
    }
    private static final int PREV_SLOT = 9;
    private static final int NEXT_SLOT = 17;

    public static int prevSlot() {
        return PREV_SLOT;
    }

    public static int nextSlot() {
        return NEXT_SLOT;
    }

    /**
     * V308: a drop a player holds is in their index, however it reached
     * them. Players had Divines that never showed here; a roll registers
     * its find, but a drop handed over another way (an admin give, a lost
     * save after the roll) did not. Anything rolled in the inventory or
     * in /pv counts the moment /index opens.
     */
    public static void syncHeld(SolRNGPlugin plugin, Player player) {
        var data = plugin.getPlayerDataManager().get(player.getUniqueId());
        if (data == null) return;
        var nameKey = plugin.getRollListener().getRollNameKey();
        java.util.List<ItemStack> held = new java.util.ArrayList<>(java.util.Arrays.asList(player.getInventory().getContents()));
        for (List<ItemStack> page : data.getVaults().values()) held.addAll(page);
        for (ItemStack stack : held) {
            if (stack == null || stack.getItemMeta() == null) continue;
            String name = stack.getItemMeta().getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
            if (name == null || plugin.getRarityManager().findByDisplayName(name) == null) continue;
            if (!data.hasDiscovered(name)) {
                data.markDiscovered(name);
                plugin.getFoundCounts().record(name);
            }
            if (plugin.getRollListener().isShiny(stack) && !data.hasDiscoveredShiny(name)) {
                data.markShinyDiscovered(name);
            }
        }
    }

    public static Inventory build(SolRNGPlugin plugin, Player player, Rarity filter, int page) {
        return build(plugin, player, filter, page, Mode.NORMAL);
    }

    /**
     * The shiny view scores the grid on shiny finds rather than ordinary
     * ones. Two collections live in the same 191 entries and only one of
     * them was ever visible.
     */
    public static Inventory build(SolRNGPlugin plugin, Player player, Rarity filter, int page,
                                  boolean shinyView) {
        return build(plugin, player, filter, page, shinyView ? Mode.SHINY : Mode.NORMAL);
    }

    /**
     * V311: three modes behind one block.
     *
     * Secret hands straight over to {@link SecretIndexGui}, which already
     * is that collection's screen and has its own grid, its own realm card
     * and its own click handling. Folding its 54 slots into this file
     * would have meant a second copy of all of it; carrying the same mode
     * block over there instead costs one slot and reads as one screen.
     */
    public static Inventory build(SolRNGPlugin plugin, Player player, Rarity filter, int page,
                                  Mode mode) {
        if (mode == Mode.SECRET) return SecretIndexGui.build(plugin, player);
        boolean shinyView = mode == Mode.SHINY;
        syncHeld(plugin, player);
        IndexHolder holder = new IndexHolder();
        holder.setFilter(filter);

        List<RollableItem> allItems = plugin.getRarityManager().getItems();
        // V330: sorted by rarity, Common first, Leon's call. The table's
        // own order is the order items were written into config, which
        // reads as no order at all once there are two hundred of them.
        //
        // V355: and INSIDE a rarity, by how rare the drop is rather than
        // alphabetically. Grouping by rarity and then sorting by name
        // still read as no order within a group, which is what Leon was
        // looking at; by odds the whole index is one straight ladder from
        // the commonest drop in the game to the rarest.
        List<RollableItem> shown = new ArrayList<>(filter == null ? allItems
                : allItems.stream().filter(i -> i.getRarity() == filter).toList());
        shown.sort(java.util.Comparator
                .comparingInt((RollableItem i) -> i.getRarity().ordinal())
                .thenComparingLong(RollableItem::getOdds)
                .thenComparing(RollableItem::getDisplayName));

        int totalPages = Math.max(1, (int) Math.ceil(shown.size() / (double) PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        holder.setPage(page);

        Inventory inv = Bukkit.createInventory(holder, 54, MenuStyle.title("Index", "#80DEEA", "#26C6DA"));
        holder.setInventory(inv);
        holder.setMode(mode);

        PlayerData data = plugin.getPlayerDataManager().get(player.getUniqueId());

        // Divider under the tab bar so the filters read as their own
        // section rather than running straight into the entries.
        for (int slot = DIVIDER_ROW_START; slot < DIVIDER_ROW_START + 9; slot++) {
            inv.setItem(slot, divider());
        }

        // The rest of the top row is the divider's own pane, so the two
        // blocks and the head read as a rail rather than a half-empty bar.
        for (int slot = 0; slot < 9; slot++) {
            inv.setItem(slot, divider());
        }
        for (int i = 0; i < MODE_SLOTS.length; i++) {
            inv.setItem(MODE_SLOTS[i], modeButton(plugin, data, Mode.values()[i], mode));
        }
        inv.setItem(RARITY_SLOT, buildRarityBlock(plugin, data, filter));
        inv.setItem(PROGRESS_SLOT, buildProfile(plugin, player, data, filter, shown.size()));
        if (page > 0) {
            inv.setItem(PREV_SLOT, buildPageButton(false, page, totalPages));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, buildPageButton(true, page, totalPages));
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(shown.size(), from + PAGE_SIZE);
        int slot = ENTRY_START_SLOT;
        for (RollableItem item : shown.subList(from, to)) {
            inv.setItem(slot, buildEntry(plugin, data, item, shinyView));
            slot++;
        }


        MenuStyle.apply(inv, MenuStyle.Palette.CYAN);
        // V330: and the divider row goes back to black afterwards. The
        // palette paints every blank pane on the border ring, and the two
        // ends of this row are on it, which is the blue glass Leon means.
        for (int rail = DIVIDER_ROW_START; rail < DIVIDER_ROW_START + 9; rail++) {
            if (rail == PREV_SLOT && page > 0) continue;
            if (rail == NEXT_SLOT && page < totalPages - 1) continue;
            inv.setItem(rail, divider());
        }

        return inv;
    }

    /**
     * A rarity's tab. It carries the completion reward as well as the
     * filter, because "finish this tier for 2x Luck" is the reason to care
     * about a tier at all and it has to be readable from the tier itself.
     */
    /**
     * The icon a tier is recognised by. It used to live inside the tier's
     * own tab; the tier block wears it now so the top row still says at a
     * glance which tier is up.
     */
    private static Material tabMaterial(Rarity rarity) {
        return switch (rarity) {
            // Common moved off white when its label went grey; white is
            // Divine's now, and nothing else in the menu is that bright.
            case COMMON -> Material.LIGHT_GRAY_DYE;
            case UNCOMMON -> Material.LIME_DYE;
            case RARE -> Material.LIGHT_BLUE_DYE;
            case EPIC -> Material.PURPLE_DYE;
            case LEGENDARY -> Material.ORANGE_DYE;
            case MYTHICAL -> Material.RED_DYE;
            case DIVINE -> Material.WHITE_DYE;
            // V319: a dye like every other tier, because the tiers read as
            // one family of icons and the top of a ladder is still a rung
            // of it. Magenta was the one strong colour left unused.
            case ASTRAL -> Material.MAGENTA_DYE;
        };
    }

    /** "2x" rather than "2.00x" when the number is whole. */
    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format("%.2f", value);
    }

    /**
     * The player's own card, top right. It's their head rather than a
     * book because this panel is about them, not about the collection -
     * and it carries the two numbers the sidebar doesn't already show
     * (rolls and playtime) plus the per-rarity breakdown, which is the
     * thing you actually want while staring at a wall of entries.
     */
    private static ItemStack buildProfile(SolRNGPlugin plugin, Player player, PlayerData data,
                                          Rarity filter, int shownCount) {
        int discovered = data.getDiscoveredItems().size();
        int total = plugin.getRarityManager().getItems().size();

        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = info.getItemMeta();
        // Not every meta is a SkullMeta on every server build, so the head
        // degrades to a blank one rather than throwing.
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + player.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "YOUR INDEX");
        lore.add("");
        lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Discovered: " + ChatColor.AQUA + discovered
                + ChatColor.DARK_GRAY + "/" + ChatColor.AQUA + total);
        lore.add(Lore.bar(total <= 0 ? 0.0 : (double) discovered / total));
        lore.add(ChatColor.AQUA + "▎ " + ChatColor.GRAY + "Shinies: " + ChatColor.AQUA
                + data.getDiscoveredShiny().size() + ChatColor.DARK_GRAY + "/" + ChatColor.AQUA + total);
        // V337: the multiplier is the best secret from /secretindex, not
        // the tag, so calling it Tag Luck here is what had Leon asking why
        // his index Luck still applied.
        lore.add(ChatColor.GREEN + "▎ " + ChatColor.GRAY + "Secret Luck: " + ChatColor.GREEN
                + String.format("%.2f", plugin.getRarityManager().tagMultiplierFor(data)) + "x");
        double completion = plugin.getPrestigeManager().indexCompletion(data);
        lore.add((completion > 1.0 ? ChatColor.GREEN : ChatColor.DARK_GRAY) + "▎ "
                + ChatColor.GRAY + "Completion: "
                + (completion > 1.0 ? ChatColor.GREEN : ChatColor.GRAY)
                + trim(completion) + "x");

        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "BY RARITY");
        Map<Rarity, Integer> found = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> tierTotal = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> shinyFound = new EnumMap<>(Rarity.class);
        for (RollableItem item : plugin.getRarityManager().getItems()) {
            tierTotal.merge(item.getRarity(), 1, Integer::sum);
            if (data.hasDiscovered(item.getDisplayName())) {
                found.merge(item.getRarity(), 1, Integer::sum);
            }
            if (data.hasDiscoveredShiny(item.getDisplayName())) {
                shinyFound.merge(item.getRarity(), 1, Integer::sum);
            }
        }
        for (Rarity rarity : Rarity.values()) {
            int have = found.getOrDefault(rarity, 0);
            int all = tierTotal.getOrDefault(rarity, 0);
            if (all == 0) continue;
            int shiny = shinyFound.getOrDefault(rarity, 0);
            lore.add(plugin.getRarityManager().style(rarity, "▎ " + rarity.displayName() + ": ")
                    + (have >= all ? ChatColor.GREEN : ChatColor.GRAY) + have
                    + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + all
                    + (shiny > 0 ? ChatColor.DARK_GRAY + "   " + ChatColor.AQUA + "✦ " + shiny : ""));
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Rolls: " + ChatColor.YELLOW
                + String.format("%,d", data.getTotalRolls()));
        lore.add(ChatColor.YELLOW + "▎ " + ChatColor.GRAY + "Playtime: " + ChatColor.YELLOW
                + playtime(player));

        if (filter != null) {
            lore.add("");
            lore.add(ChatColor.GRAY + "Showing: " + plugin.getRarityManager().style(filter, filter.displayName())
                    + ChatColor.GRAY + " (" + shownCount + ")");
        }
        meta.setLore(lore);
        info.setItemMeta(meta);
        return info;
    }

    /** PLAY_ONE_MINUTE is misnamed - it counts ticks, not minutes. */
    private static String playtime(Player player) {
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long seconds = ticks / 20L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    /** Same black pane the other menus use as filler/section break. */
    private static ItemStack divider() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private static ItemStack buildPageButton(boolean next, int page, int totalPages) {
        ItemStack button = new ItemStack(next ? Material.ARROW : Material.SPECTRAL_ARROW);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + (next ? "Next Page ▶" : "◀ Previous Page"));
        meta.setLore(List.of(ChatColor.GRAY + "Page " + (page + 1) + "/" + totalPages));
        button.setItemMeta(meta);
        return button;
    }

    /** How many players have this drop in their index. */
    private static String foundBy(SolRNGPlugin plugin, RollableItem item) {
        int count = plugin.getFoundCounts().count(item.getDisplayName());
        return Lore.statArrow(ChatColor.AQUA, "Found by",
                count == 1 ? "1 player" : String.format("%,d", count) + " players");
    }

    private static ItemStack buildEntry(SolRNGPlugin plugin, PlayerData data, RollableItem item,
                                        boolean shinyView) {
        boolean shiny = data.hasDiscoveredShiny(item.getDisplayName());
        // In the shiny view an ordinary find does not count as found: the
        // whole point is seeing which of the 144 you still owe a shiny.
        boolean discovered = shinyView ? shiny : data.hasDiscovered(item.getDisplayName());

        ItemStack icon = new ItemStack(discovered ? item.getMaterial() : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();

        List<String> lore = new ArrayList<>();
        if (discovered) {
            meta.setDisplayName(RollFormat.displayName(plugin, item));
            lore.add(Lore.section(ChatColor.AQUA, "The drop"));
            lore.addAll(RollFormat.lore(plugin, item));
            lore.add("");
            lore.add(ChatColor.GREEN + Lore.BULLET + " " + ChatColor.GRAY + "Discovered  "
                    + ChatColor.GREEN + Lore.TICK);
            // The shiny is a second, rarer find of the same drop, so it's a
            // line on the entry rather than an entry of its own.
            lore.add(shiny
                    ? ChatColor.AQUA + Lore.BULLET + " " + ChatColor.GRAY + "Shiny found  "
                            + ChatColor.AQUA + Lore.SPARK
                    : ChatColor.DARK_GRAY + Lore.BULLET + " Shiny not found");
            lore.add(foundBy(plugin, item));
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to equip as your tag");

            meta.getPersistentDataContainer().set(plugin.getRollListener().getRarityKey(),
                    PersistentDataType.STRING, item.getRarity().name());
            meta.getPersistentDataContainer().set(plugin.getRollListener().getRollNameKey(),
                    PersistentDataType.STRING, item.getDisplayName());
        } else {
            meta.setDisplayName(Lore.title(ChatColor.DARK_GRAY, "???"));
            lore.add(Lore.section(ChatColor.AQUA, "What's known"));
            lore.add(Lore.statArrow(ChatColor.AQUA, "Rarity",
                    ChatColor.stripColor(item.getRarity().displayName())));
            // The odds show even before it's found - that's the hook that
            // makes an undiscovered slot worth chasing.
            lore.add(Lore.statArrow(ChatColor.AQUA, "Chance", RollFormat.chance(item.getOdds())));
            lore.add(foundBy(plugin, item));
            lore.add("");
            lore.add(ChatColor.RED + "" + ChatColor.BOLD + "Not yet discovered");
            lore.add(ChatColor.DARK_GRAY + Lore.BULLET + " Shiny not found");
        }
        meta.setLore(lore);
        // A glint on the entry marks the shiny as caught - the same signal
        // the shiny item itself carries.
        meta.setEnchantmentGlintOverride(shiny ? Boolean.TRUE : null);
        icon.setItemMeta(meta);
        return icon;
    }
}
