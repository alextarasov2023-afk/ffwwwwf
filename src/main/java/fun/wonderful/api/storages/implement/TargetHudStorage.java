package fun.wonderful.api.storages.implement;

import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.impl.combat.Triggerbot;
import fun.wonderful.client.modules.impl.combat.KillAura;
import fun.wonderful.client.modules.impl.TestModules;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class TargetHudStorage implements QClient {

    public static TargetHudStorage instance;
    public LivingEntity target;
    private long lastSeenNanos;
    private long lastAttackNanos;
    private static final long HOLD_NANOS = 300_000_000L;
    private TestModules.TargetHud targetHudModule;

    public TargetHudStorage() {
        instance = this;
        EventInvoker.register(this);
        targetHudModule = new TestModules.TargetHud();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (targetHudModule == null || !targetHudModule.isEnable()) return;

        // Check KillAura target first
        Entity kaTarget = KillAura.INSTANCE.getLastTarget();
        if (kaTarget instanceof LivingEntity living && living.isAlive()) {
            target = living;
            lastSeenNanos = System.nanoTime();
            return;
        }

        // Then Triggerbot target
        Entity tbTarget = Triggerbot.INSTANCE.getLastTarget();
        if (tbTarget instanceof LivingEntity living && living.isAlive()) {
            target = living;
            lastSeenNanos = System.nanoTime();
            return;
        }

        if (target != null && System.nanoTime() - lastAttackNanos > HOLD_NANOS) {
            target = null;
        }
    }

    public void render(MatrixStack matrices, float partialTick) {
        // TODO: implement render when RenderUtils API is known
    }
}
