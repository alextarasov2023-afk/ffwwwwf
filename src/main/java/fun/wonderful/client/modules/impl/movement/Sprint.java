package fun.wonderful.client.modules.impl.movement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.api.utils.player.MoveUtils;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;

public class Sprint extends Module {

    public static Sprint INSTANCE = new Sprint();
    private final BooleanSetting keepInWater = new BooleanSetting("Сохранять в воде", false);

    public Sprint() {
        super("Sprint", "Автоматический бег", ModuleCategory.MOVEMENT);
        addSettings(keepInWater);
    }

    @Getter @Setter
    private static boolean sprinting;
    @Getter @Setter
    private static long time = 0;
    private static int pauseDepth = 0;
    private static boolean restoreAfterPause = false;
    private ClientPlayerEntity lastPlayer;

    @Override
    public void onEnable() {
        resetPauseState();
        sprinting = true;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetPauseState();
        sprinting = false;
        lastPlayer = null;
        if (mc.options != null) {
            mc.options.sprintKey.setPressed(false);
        }
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
        super.onDisable();
    }

    @EventLink
    public void onEvent(final EventUpdate ignored) {
        if (!isEnable()) return;
        if (mc.player == null) {
            lastPlayer = null;
            resetPauseState();
            if (mc.options != null) {
                mc.options.sprintKey.setPressed(false);
            }
            return;
        }

        if (lastPlayer != mc.player) {
            lastPlayer = mc.player;
            resetPauseState();
            sprinting = true;
        }

        boolean inWater = mc.player.isTouchingWater() || mc.player.isSubmergedInWater();
        // Связь с комбатом: пока KillAura/Triggerbot сбрасывают спринт для
        // крита — не форсим спринт обратно, иначе сброс никогда не сработает
        boolean combatSprintReset = fun.wonderful.client.modules.impl.combat.KillAura.isBlockingSprint()
                || fun.wonderful.client.modules.impl.combat.Triggerbot.isBlockingSprint();

        boolean shouldSprint = !combatSprintReset
                && pauseDepth == 0
                && System.currentTimeMillis() >= time
                && sprinting
                && MoveUtils.isMoving()
                && mc.player.input.movementForward > 0.0F
                && !mc.player.isGliding();

        if (!combatSprintReset && keepInWater.isState() && inWater && mc.player.isSprinting()) {
            shouldSprint = true;
        }

        mc.options.sprintKey.setPressed(shouldSprint);
        mc.player.setSprinting(shouldSprint);
    }

    public boolean shouldKeepSprintInWater() {
        return isEnable() && keepInWater.isState();
    }

    public static void pushPause(long delayMs) {
        restoreAfterPause |= shouldRestoreAfterPause();
        pauseDepth++;
        time = Math.max(time, System.currentTimeMillis() + Math.max(0L, delayMs));
        sprinting = false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.sprintKey.setPressed(false);
        }
        if (client.player != null) {
            client.player.setSprinting(false);
        }
    }

    public static void popPause() {
        if (pauseDepth > 0) {
            pauseDepth--;
        }
        if (pauseDepth > 0) return;

        time = 0;
        sprinting = restoreAfterPause;
        restoreAfterPause = false;
    }

    private static boolean shouldRestoreAfterPause() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && client.player.isSprinting();
    }

    private static void resetPauseState() {
        pauseDepth = 0;
        restoreAfterPause = false;
        time = 0;
    }
}
