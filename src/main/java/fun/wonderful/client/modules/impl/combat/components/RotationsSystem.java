package fun.wonderful.client.modules.impl.combat.components;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import fun.wonderful.api.QClient;
import fun.wonderful.api.utils.combat.PredictUtils;
import fun.wonderful.client.modules.impl.combat.components.gcd.GCDUtil;

public abstract class RotationsSystem implements QClient {

    public Vec2f rotate = Vec2f.ZERO;

    public abstract void updateRotations(LivingEntity target);

    public static Vec2f correctRotation(float yaw, float pitch) {
        if ((yaw == -90 && pitch == 90) || yaw == -180) return new Vec2f(mc.player.getYaw(), mc.player.getPitch());

        float gcd = GCDUtil.getGCD();
        yaw -= yaw % gcd;
        pitch -= pitch % gcd;

        return new Vec2f(yaw, pitch);
    }

    protected boolean shouldUseElytraPredict(LivingEntity target) {
        return false;
    }

    protected int getElytraPredictTicks() {
        return 0;
    }

    protected Vec3d getPredictedPoint(LivingEntity target, Vec3d point) {
        if (!shouldUseElytraPredict(target)) {
            return point;
        }

        return PredictUtils.bypasselytrahacking(target);
    }

    protected Box getPredictedBox(LivingEntity target) {
        Box box = target.getBoundingBox();
        if (!shouldUseElytraPredict(target)) {
            return box;
        }
        Vec3d currentCenter = box.getCenter();
        Vec3d predictedCenter = getPredictedPoint(target, currentCenter);
        return box.offset(predictedCenter.subtract(currentCenter));
    }
}
