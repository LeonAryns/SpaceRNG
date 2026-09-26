package com.spacerng.solrng.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /help (V224): every feature on one screen, explained in its tooltip.
 *
 * /guide walks a new player through the first steps in order; this is
 * the reference they come back to afterwards. Four rows, one per part of
 * the game (rolling, progression, farming, rewards), each framed by a
 * coloured pane with its name, an overview at the top and the handy
 * commands at the bottom.
 *
 * Every item has the same shape: what it is in a line or two, a "How it
 * works" section, and where to open it. Nothing is clickable. On Bedrock
 * reading a tooltip is a tap and a tap is a click, so a help item that
 * opened a menu would throw a phone player out of the help every time
 * they tried to read it.
 */
public final class HelpGui {

    private static final int SIZE = 54;

    private HelpGui() {
    }

    public static Inventory build() {
        HelpHolder holder = new HelpHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                ChatColor.BLUE + "" + ChatColor.BOLD + "Help" + ChatColor.GRAY + " - How it all works");
        holder.setInventory(inv);

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        inv.setItem(4, entry(Material.BOOK, ChatColor.AQUA, "How SpaceRNG works",
                List.of("Roll for drops, spend them to get", "luckier, and roll again for rarer ones."),
                List.of("Roll with your Starforge",
                        "Spend Money in /skilltree",
                        "Farm your plot for Coins",
                        "Chase Divine drops and shinies"),
                "New here", "/guide"));

        row(inv, 1, Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.AQUA, "Rolling");
        inv.setItem(10, entry(Material.NETHER_STAR, ChatColor.AQUA, "Rolling",
                List.of("Right-click your Starforge to roll.", "Every roll hands you a drop."),
                List.of("7 rarities, Common up to Divine",
                        "Each drop pays Money on its odds",
                        "Epic and up get a big reveal",
                        "Sneak + right-click: the ability"),
                "Use", "your Starforge"));
        inv.setItem(11, entry(Material.RABBIT_FOOT, ChatColor.GREEN, "Luck and Speed",
                List.of("Luck makes rare drops likelier.", "Speed makes every roll quicker."),
                List.of("Skills, armor and prestige",
                        "Nova Core, potions, pets, perks",
                        "Your tag and your rank",
                        "See where yours comes from"),
                "Open", "/stats"));
        inv.setItem(12, entry(Material.CLOCK, ChatColor.YELLOW, "Auto Roll",
                List.of("Rolls for you while you play."),
                List.of("Unlock the root skill in /skilltree",
                        "Left-click your Starforge to switch",
                        "Rolls at your own roll speed",
                        "On Bedrock: the switch is in /starforge"),
                "Unlock", "/skilltree"));
        inv.setItem(13, entry(Material.NAME_TAG, ChatColor.LIGHT_PURPLE, "Index and Tags",
                List.of("Every drop you find is logged."),
                List.of("Wear a drop as your tag",
                        "A tag adds a Luck multiplier",
                        "It shows next to your name",
                        "Epic tags and up wear an aura"),
                "Open", "/index"));
        inv.setItem(14, entry(Material.AMETHYST_SHARD, ChatColor.AQUA, "Shinies",
                List.of("A rare glowing version of a drop."),
                List.of("Unlock them in /skilltree",
                        "Base chance 1 in 2,500",
                        "Shiny Boost skills raise it",
                        "Every shiny is announced"),
                "Unlock", "/skilltree"));
        inv.setItem(15, entry(Material.BEACON, ChatColor.GOLD, "Server Firsts",
                List.of("Be one of the first to find", "a Legendary, Mythical or Divine."),
                List.of("A few spots per rarity",
                        "Your name stays on the wall",
                        "The whole server sees it happen"),
                "Open", "/firsts"));
        inv.setItem(16, entry(Material.COMPARATOR, ChatColor.GRAY, "Options",
                List.of("Make the game look and sound", "the way you like it."),
                List.of("Roll sound and animation",
                        "Auras and big drop effects",
                        "Other players' drop shouts"),
                "Open", "/options"));

        row(inv, 2, Material.PURPLE_STAINED_GLASS_PANE, ChatColor.LIGHT_PURPLE, "Progression");
        inv.setItem(19, entry(Material.EXPERIENCE_BOTTLE, ChatColor.LIGHT_PURPLE, "Skill Tree",
                List.of("Your main upgrade path."),
                List.of("5 pages, over 100 skills",
                        "Mostly paid with Money",
                        "Luck, Speed, Money and more",
                        "Unlocks Auto Roll and shinies"),
                "Open", "/skilltree"));
        inv.setItem(20, entry(Material.END_CRYSTAL, ChatColor.LIGHT_PURPLE, "Prestige",
                List.of("Level up by rolling, then ascend."),
                List.of("Levels come from your rolls",
                        "Ascending gives Prestige Points",
                        "Spend them on lasting upgrades"),
                "Open", "/prestige"));
        inv.setItem(21, entry(Material.BLAZE_ROD, ChatColor.GOLD, "Starforge",
                List.of("The item you roll with."),
                List.of("Upgrade it with your drops",
                        "Higher tiers give more Luck",
                        "Later tiers have an ability"),
                "Open", "/starforge"));
        inv.setItem(22, entry(Material.HEART_OF_THE_SEA, ChatColor.LIGHT_PURPLE, "Nova Core",
                List.of("A tier ladder for extra Luck."),
                List.of("Each forge uses one Nova Core",
                        "Tier 1 always works",
                        "Higher tiers can fail"),
                "Open", "/novacore"));
        inv.setItem(23, entry(Material.DIAMOND_CHESTPLATE, ChatColor.AQUA, "Armor",
                List.of("Wear armor for a Luck bonus."),
                List.of("Bought with your banked drops",
                        "Every piece counts on its own",
                        "Six tiers to climb"),
                "Open", "/armor"));
        inv.setItem(24, entry(Material.BREWING_STAND, ChatColor.LIGHT_PURPLE, "Potions",
                List.of("Brew timed boosts from drops."),
                List.of("Paid with banked drops",
                        "Drinking again extends it",
                        "See what runs in /boosts"),
                "Open", "/potion"));
        inv.setItem(25, entry(Material.HOPPER, ChatColor.GRAY, "Convert",
                List.of("Bank your drops so your", "inventory stays free."),
                List.of("Banked drops pay for armor,",
                        "potions and Starforge upgrades",
                        "Auto convert per rarity"),
                "Open", "/convert"));

        row(inv, 3, Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN, "Farming");
        inv.setItem(28, entry(Material.WHEAT, ChatColor.GREEN, "Farming",
                List.of("Harvest crops on the shared farm."),
                List.of("You see your own crops",
                        "Crops pay Coins and Gems",
                        "Pick your crop with /crops"),
                "Open", "/crops"));
        inv.setItem(29, entry(Material.DIAMOND_HOE, ChatColor.GREEN, "Your Hoe",
                List.of("Right-click your hoe to upgrade it."),
                List.of("Enchants fire while you harvest",
                        "Level them with Coins",
                        "New enchants come from /farmtree"),
                "Use", "right-click your hoe"));
        inv.setItem(30, entry(Material.OAK_SAPLING, ChatColor.GREEN, "Farm Tree",
                List.of("The skill tree for farming."),
                List.of("Unlocks hoe enchants",
                        "Farm upgrades and special rewards",
                        "Paid with what you farm"),
                "Open", "/farmtree"));
        inv.setItem(31, entry(Material.GOLD_INGOT, ChatColor.YELLOW, "Farming Payout",
                List.of("The best farmers get paid every day."),
                List.of("Top 3 of the day get Credits",
                        "150, 100 and 50",
                        "Only on a day the server filled up"),
                "Open", "/top farming"));
        inv.setItem(32, entry(Material.WITHER_SKELETON_SKULL, ChatColor.RED, "Bosses",
                List.of("A boss turns up every couple of hours."),
                List.of("Everyone fights their own copy",
                        "Harvest crops to hurt it",
                        "Your rolls hit it too",
                        "Beat it in time for a Boss Box"),
                "Open", "/boss"));
        inv.setItem(33, entry(Material.LEAD, ChatColor.AQUA, "Pets",
                List.of("Pets give a boost while worn."),
                List.of("Cosmic Dust falls while rolling",
                        "Farm Dust falls while farming",
                        "Unlock both in the trees",
                        "Make and upgrade pets with dust"),
                "Open", "/pets"));
        inv.setItem(34, entry(Material.MAP, ChatColor.YELLOW, "Milestones",
                List.of("Rewards for your long-run progress."),
                List.of("Tracks for rolls, crops and more",
                        "Rolls, potions, Nova Cores,",
                        "Perk Tickets and Credits"),
                "Open", "/milestones"));

        row(inv, 4, Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW, "Rewards");
        inv.setItem(37, entry(Material.SUNFLOWER, ChatColor.YELLOW, "Daily Streak",
                List.of("Come back every day for a reward."),
                List.of("The reward grows with your streak",
                        "Miss a day and it starts over"),
                "Open", "/daily"));
        inv.setItem(38, entry(Material.FILLED_MAP, ChatColor.GOLD, "Battle Pass",
                List.of("Levels up as you play."),
                List.of("A free and a premium track",
                        "Premium is bought with Credits",
                        "and pays its price back"),
                "Open", "/pass"));
        inv.setItem(39, entry(Material.TRIPWIRE_HOOK, ChatColor.GOLD, "Crates and Keys",
                List.of("Open crates at spawn with keys."),
                List.of("Right-click a crate with a key",
                        "Left-click to see what's inside",
                        "Keys come from milestones,",
                        "the pass and Key Finder"),
                "Find them", "at spawn"));
        inv.setItem(40, entry(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE, "Perks",
                List.of("Roll a perk with Perk Tickets."),
                List.of("A perk boosts your stats",
                        "Five levels, rarer ones are stronger",
                        "Tickets are bought with Credits"),
                "Open", "/perks"));
        inv.setItem(41, entry(Material.AMETHYST_CLUSTER, ChatColor.LIGHT_PURPLE, "Credits and Ranks",
                List.of("Credits buy ranks and boosts."),
                List.of("Ranks: Comet, Nova, Supernova",
                        "A Global Luck boost for everyone",
                        "Premium Pass and Perk Tickets",
                        "Earned in game or at the store"),
                "Open", "/buy"));
        inv.setItem(42, entry(Material.ENDER_PEARL, ChatColor.BLUE, "Discord Link",
                List.of("Link your account for free perks."),
                List.of("Gives you the Linked rank",
                        "Lets your tag wear an aura",
                        "Type /discord link to start"),
                "Status", "/linked"));
        inv.setItem(43, entry(Material.ARMOR_STAND, ChatColor.AQUA, "Cosmetics",
                List.of("Titles, auras and name colours."),
                List.of("Pick your aura in /aura",
                        "Titles show after your name",
                        "Higher ranks wear bigger auras"),
                "Open", "/cosmetics"));

        inv.setItem(49, entry(Material.WRITABLE_BOOK, ChatColor.WHITE, "Handy commands",
                List.of(),
                List.of("/guide  your first steps",
                        "/stash  items that did not fit",
                        "/pv  your private vault",
                        "/boosts  what is running on you",
                        "/limitluck  roll with less Luck",
                        "/shop  every shop in one place",
                        "/leaderboards  every standing"),
                null, null));
        return inv;
    }

    /** One feature: what it is, how it works, where to open it. */
    private static ItemStack entry(Material material, ChatColor colour, String name,
                                   List<String> pitch, List<String> how, String openLabel, String openValue) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Lore.title(colour, name));
        List<String> lore = new ArrayList<>();
        for (String line : pitch) lore.add(ChatColor.GRAY + line);
        if (!pitch.isEmpty()) lore.add("");
        lore.add(Lore.section(colour, pitch.isEmpty() ? "Commands" : "How it works"));
        for (String line : how) lore.add(Lore.line(colour, line));
        if (openLabel != null) {
            lore.add("");
            lore.add(Lore.stat(colour, openLabel, openValue));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** The coloured frame at both ends of a row, carrying the row's name. */
    private static void row(Inventory inv, int row, Material material, ChatColor colour, String name) {
        ItemStack label = pane(material, colour + "" + ChatColor.BOLD + name);
        inv.setItem(row * 9, label);
        inv.setItem(row * 9 + 8, label);
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
