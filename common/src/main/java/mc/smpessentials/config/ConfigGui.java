package mc.smpessentials.config;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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
