package mc.smpessentials.config;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side config menu using a Chest GUI.
 */
public class ConfigGui {

    // Helper to identify our slots
    // --- Constants ---
    private static final int SLOT_RELOAD = 45;
    private static final int SLOT_RESET = 49;
    private static final int SLOT_SAVE = 53;

    // Row 0: World & Claims
    private static final int SLOT_TOGGLE_LAVA = 0;

    private static final int SLOT_MAX_CLAIMS_MINUS = 2;
    private static final int SLOT_MAX_CLAIMS_DISPLAY = 3;
    private static final int SLOT_MAX_CLAIMS_PLUS = 4;

    private static final int SLOT_VIP_BONUS_MINUS = 6;
    private static final int SLOT_VIP_BONUS_DISPLAY = 7;
    private static final int SLOT_VIP_BONUS_PLUS = 8;

    // Row 1: Chat & Teleport
    private static final int SLOT_WARMUP_MINUS = 11;
    private static final int SLOT_WARMUP_DISPLAY = 12;
    private static final int SLOT_WARMUP_PLUS = 13;

    private static final int SLOT_MSG_INTERVAL_MINUS = 15;
    private static final int SLOT_MSG_INTERVAL_DISPLAY = 16;
    private static final int SLOT_MSG_INTERVAL_PLUS = 17;

    // Row 2: Global Skill Settings
    private static final int SLOT_XP_EXPONENT_MINUS = 22;
    private static final int SLOT_XP_EXPONENT_DISPLAY = 23;
    private static final int SLOT_XP_EXPONENT_PLUS = 24;

    // Row 3: Skill Caps I
    private static final int SLOT_CAP_SPEED_MINUS = 27;
    private static final int SLOT_CAP_SPEED_DISPLAY = 28;
    private static final int SLOT_CAP_SPEED_PLUS = 29;

    private static final int SLOT_CAP_HEALTH_MINUS = 33;
    private static final int SLOT_CAP_HEALTH_DISPLAY = 34;
    private static final int SLOT_CAP_HEALTH_PLUS = 35;

    // Row 4: Skill Caps II
    private static final int SLOT_CAP_DAMAGE_MINUS = 36;
    private static final int SLOT_CAP_DAMAGE_DISPLAY = 37;
    private static final int SLOT_CAP_DAMAGE_PLUS = 38;

    private static final int SLOT_CAP_XP_MINUS = 42;
    private static final int SLOT_CAP_XP_DISPLAY = 43;
    private static final int SLOT_CAP_XP_PLUS = 44;

    public static void open(ServerPlayer player) {
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("QuackedSMP Config");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                // Use 6 rows (54 slots)
                ConfigMenuContainer key = new ConfigMenuContainer(54);
                ChestMenu menu = ChestMenu.sixRows(containerId, inventory, key);
                populate(key);
                return menu;
            }
        };

        player.openMenu(provider);
    }

    private static void populate(ConfigMenuContainer container) {
        // Background / Filler (Gray Stained Glass Pane)
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponents.CUSTOM_NAME, Component.empty());
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, filler);
        }

        // --- Row 0: World & Claims ---
        container.setItem(SLOT_TOGGLE_LAVA,
                createBooleanItem("Allow Lava Wilderness", SmpConfig.ALLOW_LAVA_WILDERNESS));

        container.setItem(SLOT_MAX_CLAIMS_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-5 Claims"));
        container.setItem(SLOT_MAX_CLAIMS_DISPLAY,
                createInfoItem(Items.PAPER, "Max Claims", String.valueOf(SmpConfig.MAX_CLAIMS)));
        container.setItem(SLOT_MAX_CLAIMS_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+5 Claims"));

        container.setItem(SLOT_VIP_BONUS_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-1 Bonus"));
        container.setItem(SLOT_VIP_BONUS_DISPLAY,
                createInfoItem(Items.GOLD_INGOT, "VIP Bonus Claims", String.valueOf(SmpConfig.VIP_BONUS_CLAIMS)));
        container.setItem(SLOT_VIP_BONUS_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+1 Bonus"));

        // --- Row 1: Chat & Teleport ---
        container.setItem(SLOT_WARMUP_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-1s Warmup"));
        container.setItem(SLOT_WARMUP_DISPLAY, createInfoItem(Items.CLOCK, "TP Warmup", SmpConfig.TP_WARMUP + "s"));
        container.setItem(SLOT_WARMUP_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+1s Warmup"));

        container.setItem(SLOT_MSG_INTERVAL_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-60s Interval"));
        container.setItem(SLOT_MSG_INTERVAL_DISPLAY,
                createInfoItem(Items.OAK_SIGN, "Msg Interval", SmpConfig.MESSAGE_INTERVAL + "s"));
        container.setItem(SLOT_MSG_INTERVAL_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+60s Interval"));

        // --- Row 2: Global Skill Settings ---
        container.setItem(SLOT_XP_EXPONENT_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-0.1 Exponent"));

        List<String> expLore = new ArrayList<>();
        expLore.add("\u00a77Controls leveling difficulty curve.");
        expLore.add("\u00a77Formula: XP needed = (Level ^ Exponent) * 100");
        expLore.add("\u00a7eHigher value = Harder to level up.");

        container.setItem(SLOT_XP_EXPONENT_DISPLAY, createInfoItem(Items.EXPERIENCE_BOTTLE, "Global: Leveling Curve",
                String.format("%.2f", SmpConfig.SKILL_XP_EXPONENT), expLore));

        container.setItem(SLOT_XP_EXPONENT_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+0.1 Exponent"));

        // --- Row 3: Skill Caps I ---
        container.setItem(SLOT_CAP_SPEED_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-0.1 Speed"));

        List<String> speedLore = new ArrayList<>();
        speedLore.add("\u00a77Applies to: \u00a7fMining, Excavation, Woodcutting");
        speedLore.add("\u00a77Effect at Level 100:");
        speedLore.add("\u00a7b+" + (int) (SmpConfig.CAP_INDUSTRIAL_SPEED * 100) + "% Mining Speed");

        container.setItem(SLOT_CAP_SPEED_DISPLAY, createInfoItem(Items.GOLDEN_PICKAXE, "Cap: Industrial Speed",
                String.format("%.1f", SmpConfig.CAP_INDUSTRIAL_SPEED), speedLore));

        container.setItem(SLOT_CAP_SPEED_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+0.1 Speed"));

        container.setItem(SLOT_CAP_HEALTH_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-1.0 Health"));

        List<String> healthLore = new ArrayList<>();
        healthLore.add("\u00a77Applies to: \u00a7fFarming, Fishing, Agility");
        healthLore.add("\u00a77Effect at Level 100:");
        healthLore.add("\u00a7c+" + (int) SmpConfig.CAP_NATURE_HEALTH + " Max Hearts");

        container.setItem(SLOT_CAP_HEALTH_DISPLAY, createInfoItem(Items.GOLDEN_APPLE, "Cap: Nature Health",
                String.format("%.1f", SmpConfig.CAP_NATURE_HEALTH), healthLore));

        container.setItem(SLOT_CAP_HEALTH_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+1.0 Health"));

        // --- Row 4: Skill Caps II ---
        container.setItem(SLOT_CAP_DAMAGE_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-0.1 Damage"));

        List<String> damageLore = new ArrayList<>();
        damageLore.add("\u00a77Applies to: \u00a7fMelee, Archery, Defense");
        damageLore.add("\u00a77Effect at Level 100:");
        damageLore.add("\u00a74+" + (int) (SmpConfig.CAP_COMBAT_DAMAGE * 100) + "% Damage Output");

        container.setItem(SLOT_CAP_DAMAGE_DISPLAY, createInfoItem(Items.IRON_SWORD, "Cap: Combat Damage",
                String.format("%.1f", SmpConfig.CAP_COMBAT_DAMAGE), damageLore));

        container.setItem(SLOT_CAP_DAMAGE_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+0.1 Damage"));

        container.setItem(SLOT_CAP_XP_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-0.1 XP Mult"));

        List<String> xpLore = new ArrayList<>();
        xpLore.add("\u00a77Applies to: \u00a7fEnchanting, Alchemy, Trading");
        xpLore.add("\u00a77Effect at Level 100:");
        xpLore.add("\u00a7af+" + (int) (SmpConfig.CAP_KNOWLEDGE_XP * 100) + "% XP Orbs Dropped");

        container.setItem(SLOT_CAP_XP_DISPLAY, createInfoItem(Items.EXPERIENCE_BOTTLE, "Cap: XP Multiplier",
                String.format("%.1f", SmpConfig.CAP_KNOWLEDGE_XP), xpLore));

        container.setItem(SLOT_CAP_XP_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+0.1 XP Mult"));

        // --- Row 5: Controls ---
        // Reload
        ItemStack reload = new ItemStack(Items.YELLOW_TERRACOTTA);
        reload.set(DataComponents.CUSTOM_NAME,
                Component.literal("Discard Changes (Reload)").withStyle(ChatFormatting.RED));
        container.setItem(SLOT_RELOAD, reload);

        // Save
        ItemStack save = new ItemStack(Items.LIME_TERRACOTTA);
        save.set(DataComponents.CUSTOM_NAME, Component.literal("Save to Disk").withStyle(ChatFormatting.GREEN));
        container.setItem(SLOT_SAVE, save);

        // Factory Reset (Slot 49)
        ItemStack reset = new ItemStack(Items.RED_CONCRETE_POWDER);
        reset.set(DataComponents.CUSTOM_NAME, Component.literal("Factory Reset").withStyle(ChatFormatting.RED));
        List<Component> resetLore = new ArrayList<>();
        resetLore.add(Component.literal("Restores all settings to defaults.").withStyle(ChatFormatting.GRAY));
        resetLore.add(Component.literal("Wipes current config file!").withStyle(ChatFormatting.DARK_RED));
        resetLore.add(Component.empty());
        resetLore.add(Component.literal("Shift + Left Click to confirm").withStyle(ChatFormatting.YELLOW));
        reset.set(DataComponents.LORE, new ItemLore(resetLore));
        container.setItem(SLOT_RESET, reset);
    }

    public static void onClick(ServerPlayer player, ConfigMenuContainer container, int slotId,
            net.minecraft.world.inventory.ClickType clickType) {
        boolean refresh = false;
        boolean save = false;
        boolean reload = false;

        // Row 0
        if (slotId == SLOT_TOGGLE_LAVA) {
            SmpConfig.ALLOW_LAVA_WILDERNESS = !SmpConfig.ALLOW_LAVA_WILDERNESS;
            refresh = true;
        } else if (slotId == SLOT_MAX_CLAIMS_MINUS) {
            SmpConfig.MAX_CLAIMS = Math.max(0, SmpConfig.MAX_CLAIMS - 5);
            refresh = true;
        } else if (slotId == SLOT_MAX_CLAIMS_PLUS) {
            SmpConfig.MAX_CLAIMS += 5;
            refresh = true;
        } else if (slotId == SLOT_VIP_BONUS_MINUS) {
            SmpConfig.VIP_BONUS_CLAIMS = Math.max(0, SmpConfig.VIP_BONUS_CLAIMS - 1);
            refresh = true;
        } else if (slotId == SLOT_VIP_BONUS_PLUS) {
            SmpConfig.VIP_BONUS_CLAIMS += 1;
            refresh = true;
        }
        // Row 1
        else if (slotId == SLOT_WARMUP_MINUS) {
            SmpConfig.TP_WARMUP = Math.max(0, SmpConfig.TP_WARMUP - 1);
            refresh = true;
        } else if (slotId == SLOT_WARMUP_PLUS) {
            SmpConfig.TP_WARMUP += 1;
            refresh = true;
        } else if (slotId == SLOT_MSG_INTERVAL_MINUS) {
            SmpConfig.MESSAGE_INTERVAL = Math.max(60, SmpConfig.MESSAGE_INTERVAL - 60);
            refresh = true;
        } else if (slotId == SLOT_MSG_INTERVAL_PLUS) {
            SmpConfig.MESSAGE_INTERVAL += 60;
            refresh = true;
        }
        // Row 2
        else if (slotId == SLOT_XP_EXPONENT_MINUS) {
            SmpConfig.SKILL_XP_EXPONENT = Math.max(1.0, SmpConfig.SKILL_XP_EXPONENT - 0.1);
            refresh = true;
        } else if (slotId == SLOT_XP_EXPONENT_PLUS) {
            SmpConfig.SKILL_XP_EXPONENT += 0.1;
            refresh = true;
        }
        // Row 3
        else if (slotId == SLOT_CAP_SPEED_MINUS) {
            SmpConfig.CAP_INDUSTRIAL_SPEED = Math.max(0.0, SmpConfig.CAP_INDUSTRIAL_SPEED - 0.1);
            refresh = true;
        } else if (slotId == SLOT_CAP_SPEED_PLUS) {
            SmpConfig.CAP_INDUSTRIAL_SPEED += 0.1;
            refresh = true;
        } else if (slotId == SLOT_CAP_HEALTH_MINUS) {
            SmpConfig.CAP_NATURE_HEALTH = Math.max(0.0, SmpConfig.CAP_NATURE_HEALTH - 1.0);
            refresh = true;
        } else if (slotId == SLOT_CAP_HEALTH_PLUS) {
            SmpConfig.CAP_NATURE_HEALTH += 1.0;
            refresh = true;
        }
        // Row 4
        else if (slotId == SLOT_CAP_DAMAGE_MINUS) {
            SmpConfig.CAP_COMBAT_DAMAGE = Math.max(0.0, SmpConfig.CAP_COMBAT_DAMAGE - 0.1);
            refresh = true;
        } else if (slotId == SLOT_CAP_DAMAGE_PLUS) {
            SmpConfig.CAP_COMBAT_DAMAGE += 0.1;
            refresh = true;
        } else if (slotId == SLOT_CAP_XP_MINUS) {
            SmpConfig.CAP_KNOWLEDGE_XP = Math.max(0.0, SmpConfig.CAP_KNOWLEDGE_XP - 0.1);
            refresh = true;
        } else if (slotId == SLOT_CAP_XP_PLUS) {
            SmpConfig.CAP_KNOWLEDGE_XP += 0.1;
            refresh = true;
        }
        // Row 5
        else if (slotId == SLOT_SAVE) {
            save = true;
        } else if (slotId == SLOT_RELOAD) {
            reload = true;
        } else if (slotId == SLOT_RESET) {
            // Check for Shift + Click (QUICK_MOVE in MC internal terminology usually maps
            // to Shift-Click)
            if (clickType == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
                ConfigIO.resetToFactory();
                player.closeContainer();
                player.sendSystemMessage(Component.literal("\u00a7aConfiguration reset to factory defaults!"));
                return;
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cHold SHIFT and click to Factory Reset!"));
            }
        }

        if (refresh) {
            populate(container);
            // Fix sound (Holder vs SoundEvent ambiguity) if needed in future
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
            // net.minecraft.sounds.SoundSource.MASTER, 1f, 1f);
        }

        if (save) {
            ConfigIO.save();
            player.closeContainer();
            player.sendSystemMessage(Component.literal("\u00a7aConfiguration saved!"));
            // player.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP.value(),
            // net.minecraft.sounds.SoundSource.MASTER, 1f, 1f);
        }

        if (reload) {
            SmpConfig.load();
            player.closeContainer();
            player.sendSystemMessage(Component.literal("\u00a7eConfiguration reloaded from disk (changes discarded)."));
        }
    }

    private static ItemStack createBooleanItem(String name, boolean value) {
        ItemStack stack = new ItemStack(value ? Items.LIME_WOOL : Items.RED_WOOL);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(value ? "Enabled" : "Disabled")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
        lore.add(Component.empty());
        lore.add(Component.literal("Click to toggle").withStyle(ChatFormatting.YELLOW));

        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack createInfoItem(net.minecraft.world.item.Item item, String name, String value) {
        return createInfoItem(item, name, value, null);
    }

    private static ItemStack createInfoItem(net.minecraft.world.item.Item item, String name, String value,
            List<String> extraLore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Current: " + value).withStyle(ChatFormatting.WHITE));

        if (extraLore != null) {
            lore.add(Component.empty());
            for (String line : extraLore) {
                lore.add(Component.literal(line));
            }
        }

        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack createActionItem(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
