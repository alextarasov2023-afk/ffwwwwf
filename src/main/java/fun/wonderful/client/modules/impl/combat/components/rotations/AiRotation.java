package fun.wonderful.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

import fun.wonderful.api.QClient;
import fun.wonderful.api.storages.implement.RotationStorage;
import fun.wonderful.api.utils.rotate.Rotation;

/**
 * AI — humanized tracking rotation.
 * Predicts target position, moderate turn speed, slight jitter.
 * Fixed preset — no user settings needed.
 */
public class AiRotation implements QClient {

    public static final AiRotation INSTANCE = new AiRotation();
    private final Random random = new Random();

    private Vec3d aimPoint;
    private int refreshTicks;
    private int lastTargetId = -1;

    /**
     * Update rotation toward target. Call every tick.
     */
    public void tick(LivingEntity target) {
        if (mc.player == null || target == null) {
            reset();
            return;
        }

        // Refresh aim point periodically or when target changes
        if (aimPoint == null || target.getId() != lastTargetId || refreshTicks <= 0) {
            pickAimPoint(target);
            lastTargetId = target.getId();
            refreshTicks = 25 + random.nextInt(25);
        }
        refreshTicks--;

        // Smoothly drift aim point toward actual center (lag tracking)
        Box box = target.getBoundingBox();
        Vec3d actualCenter = box.getCenter();
        aimPoint = aimPoint.lerp(actualCenter, 0.12f + random.nextFloat() * 0.06f);

        // Predict: lead target slightly based on velocity
        Vec3d vel = target.getVelocity();
        Vec3d predicted = aimPoint.add(vel.x * 0.15, vel.y * 0.1, vel.z * 0.15);

        // Compute rotation from eye to predicted point
        Vec3d eye = mc.player.getEyePos();
        Vec3d delta = predicted.subtract(eye);
        double hDist = Math.hypot(delta.x, delta.z);

        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        float pitch = (float) Math.toDegrees(Math.atan2(-delta.y, hDist));
        pitch = MathHelper.clamp(pitch, -90f, 90f);

        Rotation rot = new Rotation(yaw, pitch);
        // Moderate speed — not instant like Smooth, not as heavy as Rotation1
        RotationStorage.update(rot, 150f, 130f, 260f, 5, 1);
    }

    /**
     * Pick a new drift point on the target's body.
     */
    private void pickAimPoint(LivingEntity target) {
        Box box = target.getBoundingBox();
        double widthX = (box.maxX - box.minX) * 0.3;
        double widthZ = (box.maxZ - box.minZ) * 0.3;
        aimPoint = new Vec3d(
                box.getCenter().x + (random.nextFloat() - 0.5) * widthX,
                box.minY + target.getHeight() * (0.35f + random.nextFloat() * 0.4f),
                box.getCenter().z + (random.nextFloat() - 0.5) * widthZ
        );
    }

    /**
     * Reset internal state. Call when target is lost or module disabled.
     */
    public void reset() {
        aimPoint = null;
        refreshTicks = 0;
        lastTargetId = -1;
    }
}
