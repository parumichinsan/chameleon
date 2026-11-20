package com.example.chameleon.event;

import com.example.chameleon.ChameleonMod;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ChameleonHandler {
    private static final int INVENTORY_SLOT_COUNT = 41;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int ACTIVATION_COST_LEVELS = 30;
    private static final int MAINTENANCE_INTERVAL_TICKS = 200;
    private static final int MAINTENANCE_COST_LEVELS = 75;

    private final Map<UUID, ActivationSnapshot> activePlayers = new HashMap<>();
    private final Set<UUID> exhaustedPlayers = new HashSet<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }

        Player player = event.player;
        UUID playerId = player.getUUID();
        boolean mainHand = player.getMainHandItem().is(ChameleonMod.CHAMELEON.get());
        boolean offHand = player.getOffhandItem().is(ChameleonMod.CHAMELEON.get());
        boolean active = mainHand || offHand;
        ActivationSnapshot snapshot = activePlayers.get(playerId);

        if (!active) {
            exhaustedPlayers.remove(playerId);
        } else if (exhaustedPlayers.contains(playerId)) {
            return;
        }

        if (active && snapshot == null) {
            if (!spendExperienceLevels(player, ACTIVATION_COST_LEVELS)) {
                return;
            }
            snapshot = captureInventory(player);
            activePlayers.put(playerId, snapshot);
            clearInventoryExceptChameleon(player, mainHand);
        } else if (active) {
            snapshot.ticksSinceMaintenance++;
            if (snapshot.ticksSinceMaintenance >= MAINTENANCE_INTERVAL_TICKS) {
                if (!spendExperienceLevels(player, MAINTENANCE_COST_LEVELS)) {
                    stashItemsAcquiredWhileActive(player, snapshot);
                    restoreInventory(player, snapshot);
                    activePlayers.remove(playerId);
                    exhaustedPlayers.add(playerId);
                    player.removeEffect(MobEffects.INVISIBILITY);
                    syncInventory(player);
                    int originalSlot = snapshot.selectedHotbarSlot;
                    if (!player.getInventory().getItem(originalSlot).is(ChameleonMod.CHAMELEON.get())) {
                        selectHotbarSlot(player, originalSlot);
                    }
                    return;
                }
                snapshot.ticksSinceMaintenance = 0;
            }
            stashItemsAcquiredWhileActive(player, snapshot);
        } else if (snapshot != null) {
            stashItemsAcquiredWhileActive(player, snapshot);
            restoreInventory(player, snapshot);
            activePlayers.remove(playerId);
            player.removeEffect(MobEffects.INVISIBILITY);
            syncInventory(player);
            int originalSlot = snapshot.selectedHotbarSlot;
            if (!player.getInventory().getItem(originalSlot).is(ChameleonMod.CHAMELEON.get())) {
                selectHotbarSlot(player, originalSlot);
            }
            return;
        } else {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, false, false, false));

        syncInventory(player);
    }

    private static boolean spendExperienceLevels(Player player, int amount) {
        if (player.experienceLevel < amount) {
            return false;
        }
        player.giveExperienceLevels(-amount);
        return true;
    }

    private static ActivationSnapshot captureInventory(Player player) {
        List<ItemStack> savedSlots = new ArrayList<>(INVENTORY_SLOT_COUNT);
        for (int slot = 0; slot < INVENTORY_SLOT_COUNT; slot++) {
            savedSlots.add(player.getInventory().getItem(slot).copy());
        }
        return new ActivationSnapshot(savedSlots, player.getInventory().selected);
    }

    private static void clearInventoryExceptChameleon(Player player, boolean mainHandChameleon) {
        for (int slot = 0; slot < HOTBAR_SLOT_COUNT; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ChameleonMod.CHAMELEON.get())) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (mainHandChameleon && !offhand.is(ChameleonMod.CHAMELEON.get())) {
            player.getInventory().setItem(40, ItemStack.EMPTY);
        }
    }

    private static void stashItemsAcquiredWhileActive(Player player, ActivationSnapshot snapshot) {
        for (int slot = 0; slot < HOTBAR_SLOT_COUNT; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && !stack.is(ChameleonMod.CHAMELEON.get())) {
                snapshot.itemsAcquiredWhileActive.add(stack.copy());
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && !offhand.is(ChameleonMod.CHAMELEON.get())) {
            snapshot.itemsAcquiredWhileActive.add(offhand.copy());
            player.getInventory().setItem(40, ItemStack.EMPTY);
        }
    }

    private static void restoreInventory(Player player, ActivationSnapshot snapshot) {
        for (int slot = 0; slot < HOTBAR_SLOT_COUNT; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            ItemStack saved = snapshot.slots.get(slot);
            if (current.is(ChameleonMod.CHAMELEON.get())
                    && !saved.is(ChameleonMod.CHAMELEON.get())) {
                if (!saved.isEmpty()) {
                    snapshot.itemsAcquiredWhileActive.add(saved.copy());
                }
                continue;
            }
            player.getInventory().setItem(slot, saved.copy());
        }

        ItemStack savedOffhand = snapshot.slots.get(40);
        if (!savedOffhand.is(ChameleonMod.CHAMELEON.get())) {
            player.getInventory().setItem(40, savedOffhand.copy());
        }

        for (ItemStack acquired : snapshot.itemsAcquiredWhileActive) {
            ItemStack remainder = acquired.copy();
            if (!player.getInventory().add(remainder) && !remainder.isEmpty()) {
                player.drop(remainder, false);
            }
        }
    }

    private static void syncInventory(Player player) {
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static void selectHotbarSlot(Player player, int slot) {
        if (player.getInventory().selected == slot) {
            return;
        }
        player.getInventory().selected = slot;
        ((ServerPlayer) player).connection.send(new ClientboundSetCarriedItemPacket(slot));
    }

    @SubscribeEvent
    public void onOffhandItemUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == InteractionHand.OFF_HAND && isMainHandChameleon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onOffhandBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.OFF_HAND && isMainHandChameleon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player
                && isMainHandChameleon(player)
                && !event.getItem().is(ChameleonMod.CHAMELEON.get())) {
            event.setCanceled(true);
        }
    }

    private static boolean isMainHandChameleon(Player player) {
        return player.getMainHandItem().is(ChameleonMod.CHAMELEON.get());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUUID();
        exhaustedPlayers.remove(playerId);
        ActivationSnapshot snapshot = activePlayers.remove(playerId);
        if (snapshot != null) {
            stashItemsAcquiredWhileActive(player, snapshot);
            restoreInventory(player, snapshot);
            player.removeEffect(MobEffects.INVISIBILITY);
            syncInventory(player);
        }
    }

    private static final class ActivationSnapshot {
        private final List<ItemStack> slots;
        private final int selectedHotbarSlot;
        private final List<ItemStack> itemsAcquiredWhileActive = new ArrayList<>();
        private int ticksSinceMaintenance;

        private ActivationSnapshot(List<ItemStack> slots, int selectedHotbarSlot) {
            this.slots = slots;
            this.selectedHotbarSlot = selectedHotbarSlot;
        }
    }
}
