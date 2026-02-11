package mc.smpessentials.config;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

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
    private static final int SLOT_RELOAD = 49;
    private static final int SLOT_SAVE = 53;

    // Settings Slots
    private static final int SLOT_TOGGLE_LAVA = 10;
    private static final int SLOT_MAX_CLAIMS_MINUS = 19;
    private static final int SLOT_MAX_CLAIMS_DISPLAY = 20;
    private static final int SLOT_MAX_CLAIMS_PLUS = 21;

    private static final int SLOT_WARMUP_MINUS = 28;
    private static final int SLOT_WARMUP_DISPLAY = 29;
    private static final int SLOT_WARMUP_PLUS = 30;

    private static final int SLOT_VIP_BONUS_MINUS = 23;
    private static final int SLOT_VIP_BONUS_DISPLAY = 24;
    private static final int SLOT_VIP_BONUS_PLUS = 25;

    private static final int SLOT_MSG_INTERVAL_MINUS = 32;
    private static final int SLOT_MSG_INTERVAL_DISPLAY = 33;
    private static final int SLOT_MSG_INTERVAL_PLUS = 34;

    // Skill Caps (Row 0 and 4)
    private static final int SLOT_CAP_SPEED_MINUS = 1;
    private static final int SLOT_CAP_SPEED_DISPLAY = 2;
    private static final int SLOT_CAP_SPEED_PLUS = 3;

    private static final int SLOT_CAP_HEALTH_MINUS = 5;
    private static final int SLOT_CAP_HEALTH_DISPLAY = 6;
    private static final int SLOT_CAP_HEALTH_PLUS = 7;

    private static final int SLOT_CAP_DAMAGE_MINUS = 37;
    private static final int SLOT_CAP_DAMAGE_DISPLAY = 38;
    private static final int SLOT_CAP_DAMAGE_PLUS = 39;

    private static final int SLOT_CAP_XP_MINUS = 41;
    private static final int SLOT_CAP_XP_DISPLAY = 42;
    private static final int SLOT_CAP_XP_PLUS = 43;

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

        // --- Settings ---

        // 1. Allow Lava Wilderness (Boolean)
        container.setItem(SLOT_TOGGLE_LAVA,
                createBooleanItem("Allow Lava Wilderness", SmpConfig.ALLOW_LAVA_WILDERNESS));

        // 2. Max Claims (Integer)
        container.setItem(SLOT_MAX_CLAIMS_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-5 Claims"));
        container.setItem(SLOT_MAX_CLAIMS_DISPLAY,
                createInfoItem(Items.PAPER, "Max Claims", String.valueOf(SmpConfig.MAX_CLAIMS)));
        container.setItem(SLOT_MAX_CLAIMS_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+5 Claims"));

        // 3. Teleport Warmup (Integer)
        container.setItem(SLOT_WARMUP_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-1s Warmup"));
        container.setItem(SLOT_WARMUP_DISPLAY, createInfoItem(Items.CLOCK, "TP Warmup", SmpConfig.TP_WARMUP + "s"));
        container.setItem(SLOT_WARMUP_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+1s Warmup"));

        // 4. VIP Bonus Claims (Integer)
        container.setItem(SLOT_VIP_BONUS_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-1 Bonus"));
        container.setItem(SLOT_VIP_BONUS_DISPLAY,
                createInfoItem(Items.GOLD_INGOT, "VIP Bonus Claims", String.valueOf(SmpConfig.VIP_BONUS_CLAIMS)));
        container.setItem(SLOT_VIP_BONUS_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+1 Bonus"));

        // 5. Message Interval (Integer)
        container.setItem(SLOT_MSG_INTERVAL_MINUS, createActionItem(Items.RED_CONCRETE, "\u00a7c-60s Interval"));
        container.setItem(SLOT_MSG_INTERVAL_DISPLAY,
                createInfoItem(Items.OAK_SIGN, "Msg Interval", SmpConfig.MESSAGE_INTERVAL + "s"));
        container.setItem(SLOT_MSG_INTERVAL_PLUS, createActionItem(Items.GREEN_CONCRETE, "\u00a7a+60s Interval"));

        // --- Skill Caps ---

        // Industrial Speed
        container.setItem(SLOT_CAP_SPEED_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-0.1 Speed"));
        container.setItem(SLOT_CAP_SPEED_DISPLAY, createInfoItem(Items.GOLDEN_PICKAXE, "Cap: Mining Speed",
                String.format("%.1f", SmpConfig.CAP_INDUSTRIAL_SPEED)));
        container.setItem(SLOT_CAP_SPEED_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+0.1 Speed"));

        // Nature Health
        container.setItem(SLOT_CAP_HEALTH_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-1.0 Health"));
        container.setItem(SLOT_CAP_HEALTH_DISPLAY, createInfoItem(Items.GOLDEN_APPLE, "Cap: Bonus Health",
                String.format("%.1f", SmpConfig.CAP_NATURE_HEALTH)));
        container.setItem(SLOT_CAP_HEALTH_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+1.0 Health"));

        // Combat Damage
        container.setItem(SLOT_CAP_DAMAGE_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-0.1 Damage"));
        container.setItem(SLOT_CAP_DAMAGE_DISPLAY, createInfoItem(Items.IRON_SWORD, "Cap: Bonus Damage",
                String.format("%.1f", SmpConfig.CAP_COMBAT_DAMAGE)));
        container.setItem(SLOT_CAP_DAMAGE_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+0.1 Damage"));

        // Knowledge XP
        container.setItem(SLOT_CAP_XP_MINUS, createActionItem(Items.RED_STAINED_GLASS, "\u00a7c-0.1 XP Mult"));
        container.setItem(SLOT_CAP_XP_DISPLAY, createInfoItem(Items.EXPERIENCE_BOTTLE, "Cap: XP Multiplier",
                String.format("%.1f", SmpConfig.CAP_KNOWLEDGE_XP)));
        container.setItem(SLOT_CAP_XP_PLUS, createActionItem(Items.GREEN_STAINED_GLASS, "\u00a7a+0.1 XP Mult"));

        // --- Controls ---

        // Reload from Disk
        ItemStack reload = new ItemStack(Items.YELLOW_TERRACOTTA);
        reload.set(DataComponents.CUSTOM_NAME,
                Component.literal("Discard Changes (Reload)").withStyle(ChatFormatting.RED));
        container.setItem(SLOT_RELOAD, reload);

        // Save to Disk
        ItemStack save = new ItemStack(Items.LIME_TERRACOTTA);
        save.set(DataComponents.CUSTOM_NAME, Component.literal("Save to Disk").withStyle(ChatFormatting.GREEN));
        container.setItem(SLOT_SAVE, save);
    }

    public static void onClick(ServerPlayer player, ConfigMenuContainer container, int slotId) {
        boolean refresh = false;
        boolean save = false;
        boolean reload = false;

        if (slotId == SLOT_TOGGLE_LAVA) {
            SmpConfig.ALLOW_LAVA_WILDERNESS = !SmpConfig.ALLOW_LAVA_WILDERNESS;
            refresh = true;
        } else if (slotId == SLOT_MAX_CLAIMS_MINUS) {
            SmpConfig.MAX_CLAIMS = Math.max(0, SmpConfig.MAX_CLAIMS - 5);
            refresh = true;
        } else if (slotId == SLOT_MAX_CLAIMS_PLUS) {
            SmpConfig.MAX_CLAIMS += 5;
            refresh = true;
        } else if (slotId == SLOT_WARMUP_MINUS) {
            SmpConfig.TP_WARMUP = Math.max(0, SmpConfig.TP_WARMUP - 1);
            refresh = true;
        } else if (slotId == SLOT_WARMUP_PLUS) {
            SmpConfig.TP_WARMUP += 1;
            refresh = true;
        } else if (slotId == SLOT_VIP_BONUS_MINUS) {
            SmpConfig.VIP_BONUS_CLAIMS = Math.max(0, SmpConfig.VIP_BONUS_CLAIMS - 1);
            refresh = true;
        } else if (slotId == SLOT_VIP_BONUS_PLUS) {
            SmpConfig.VIP_BONUS_CLAIMS += 1;
            refresh = true;
        } else if (slotId == SLOT_MSG_INTERVAL_MINUS) {
            SmpConfig.MESSAGE_INTERVAL = Math.max(60, SmpConfig.MESSAGE_INTERVAL - 60);
            refresh = true;
        } else if (slotId == SLOT_MSG_INTERVAL_PLUS) {
            SmpConfig.MESSAGE_INTERVAL += 60;
            refresh = true;
        }
        // Skill Caps
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
        } else if (slotId == SLOT_CAP_DAMAGE_MINUS) {
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
        } else if (slotId == SLOT_SAVE) {
            save = true;
        } else if (slotId == SLOT_RELOAD) {
            reload = true;
        }

        if (refresh) {
            populate(container);
            // TODO: Fix sound (Holder vs SoundEvent ambiguity)
            // player.playNotifySound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
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
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Current: " + value).withStyle(ChatFormatting.WHITE));

        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack createActionItem(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
