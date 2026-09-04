package fun.wonderful.client.modules.impl.player;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.impl.movement.Sprint;
import fun.wonderful.client.modules.settings.implement.FloatSetting;

public class AutoEat extends Module {

    public static final AutoEat INSTANCE = new AutoEat();

    private final FloatSetting hungerBars = new FloatSetting("Порог", 6.0f, 1.0f, 10.0f, 1.0f);

    private boolean eating;
    private boolean sprintPaused;
    private int originalSlot = -1;

    public AutoEat() {
        super("AutoEat", "Автоматически ест при низком голоде", ModuleCategory.PLAYER);
        addSettings(hungerBars);
    }

    @Override
    public void onDisable() {
        stopEating();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isEnable() || mc.player == null || mc.world == null || mc.interactionManager == null) {
            stopEating();
            return;
        }

        if (mc.currentScreen != null || mc.player.getAbilities().creativeMode || mc.player.isSpectator()) {
            stopEating();
            return;
        }

        if (!eating) {
            if (!shouldStartEating()) return;
            eating = true;
            originalSlot = mc.player.getInventory().selectedSlot;
        }

        tickEating();
    }

    private void tickEating() {
        ClientPlayerEntity player = mc.player;
        if (player == null) { stopEating(); return; }

        if (!sprintPaused) {
            Sprint.pushPause(0L);
            sprintPaused = true;
        }

        mc.options.attackKey.setPressed(false);

        if (!needsFood()) {
            if (!player.isUsingItem()) stopEating();
            return;
        }

        if (!ensureFoodReady()) { stopEating(); return; }

        Hand eatingHand = getEatingHand(player);
        if (eatingHand == null) { stopEating(); return; }

        mc.options.useKey.setPressed(true);

        if (!player.isUsingItem() || player.getActiveHand() != eatingHand) {
            mc.interactionManager.interactItem(player, eatingHand);
        }
    }

    private boolean shouldStartEating() {
        return needsFood() && !mc.player.isUsingItem()
                && (isValidFood(mc.player.getOffHandStack()) || findFoodSlot() != -1);
    }

    private boolean needsFood() {
        return mc.player != null && mc.player.getHungerManager().getFoodLevel() < Math.round(hungerBars.get()) * 2;
    }

    private boolean ensureFoodReady() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        if (isValidFood(player.getOffHandStack()) || isValidFood(player.getMainHandStack())) return true;

        int foodSlot = findFoodSlot();
        if (foodSlot == -1) return false;

        if (foodSlot < 9) {
            selectHotbarSlot(foodSlot);
            return true;
        }

        return false;
    }

    private Hand getEatingHand(ClientPlayerEntity player) {
        if (player == null) return null;
        if (isValidFood(player.getOffHandStack())) return Hand.OFF_HAND;
        if (isValidFood(player.getMainHandStack())) return Hand.MAIN_HAND;
        return null;
    }

    private int findFoodSlot() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return -1;

        int selected = player.getInventory().selectedSlot;
        if (isValidFood(player.getInventory().getStack(selected))) return selected;

        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected) continue;
            if (isValidFood(player.getInventory().getStack(slot))) return slot;
        }
        return -1;
    }

    private boolean isValidFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isOf(Items.GOLDEN_APPLE) || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)
                || stack.isOf(Items.CHORUS_FRUIT)) return false;
        return stack.getUseAction() == UseAction.EAT;
    }

    private void selectHotbarSlot(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) return;
        mc.player.getInventory().selectedSlot = slot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private void stopEating() {
        if (mc.options != null) mc.options.useKey.setPressed(false);
        if (sprintPaused) { Sprint.popPause(); sprintPaused = false; }
        if (originalSlot != -1 && mc.player != null) {
            selectHotbarSlot(originalSlot);
        }
        eating = false;
        originalSlot = -1;
    }
}
