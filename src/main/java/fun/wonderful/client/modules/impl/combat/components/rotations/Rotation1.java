package fun.wonderful.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import fun.wonderful.api.QClient;
import fun.wonderful.api.storages.implement.FreeLookStorage;

/**
 * Rotation1 — humanized rotation engine (basierte auf HumanRotationEngine).
 * Bezier кривизна, overshoot, гармоничный тремор, GCD fix, dense multipoint (27 точек).
 * Включает FreeLook — камера свободна, ротация применяется к атаке.
 */
public class Rotation1 implements QClient {

    public static final Rotation1 INSTANCE = new Rotation1();
    private final Random random = new Random();

    // ================== СОСТОЯНИЯ РОТАЦИИ ==================
    private float currentYaw = 0.0F;
    private float currentPitch = 0.0F;
    private float startYaw = 0.0F;
    private float startPitch = 0.0F;
    private float targetYaw = 0.0F;
    private float targetPitch = 0.0F;

    private float progress = 1.0F;
    private float curveOffsetX = 0.0F;
    private float curveOffsetY = 0.0F;

    private boolean isOvershooting = false;
    private float overshootYaw = 0.0F;
    private float overshootPitch = 0.0F;

    // Гармонический джиттер
    private double tremorTime = 0.0;
    private int lastTargetId = -1;

    private boolean active = false;

    // Настройки
    public float tremorIntensity = 0.15F;

    private Rotation1() {
    }

    /**
     * Основной метод обновления ротации. Вызывать каждый тик.
     */
    public void tick(LivingEntity target) {
        if (mc.player == null) {
            stop();
            return;
        }

        // FreeLook должен быть активен — камера свободна, ротация идёт в атаку
        if (!FreeLookStorage.isActive()) {
            FreeLookStorage.setActive(true);
        }

        // Синхронизируем свободные углы с текущим взглядом при старте
        if (!active) {
            active = true;
            FreeLookStorage.setFreeYaw(mc.player.getYaw());
            FreeLookStorage.setFreePitch(mc.player.getPitch());
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        float[] result = update(target);
        // Применяем к игроку (взгляд цели)
        mc.player.setYaw(result[0]);
        mc.player.setPitch(result[1]);
    }

    private float[] update(Entity target) {
        if (target == null) {
            reset();
            return new float[]{mc.player.getYaw(), mc.player.getPitch()};
        }

        // Инициализация при старте
        if (progress >= 1.0F && (currentYaw == 0.0F && currentPitch == 0.0F)) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        Vec3d eyePos = mc.player.getEyePos();

        // 1. DENSE MULTIPOINT: лучшая точка на хитбоксе (27 точек)
        Vec3d bestPoint = getBestHitboxPoint(target, eyePos, currentYaw, currentPitch);

        // 2. Идеальные углы
        double deltaX = bestPoint.x - eyePos.x;
        double deltaY = bestPoint.y - eyePos.y;
        double deltaZ = bestPoint.z - eyePos.z;
        double horizontalDist = Math.hypot(deltaX, deltaZ);

        float calculatedYaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0F;
        float calculatedPitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDist));

        // 3. Паттерны & инициализация новой траектории
        boolean targetChanged = target.getId() != lastTargetId;
        float totalDeltaYaw = Math.abs(wrapAngle(calculatedYaw - currentYaw));
        float totalDeltaPitch = Math.abs(calculatedPitch - currentPitch);

        if (progress >= 1.0F || targetChanged) {
            lastTargetId = target.getId();
            startYaw = currentYaw;
            startPitch = currentPitch;
            progress = 0.0F;

            // Естественная кривизна руки (Bezier Pattern)
            curveOffsetX = (random.nextFloat() - 0.5F) * Math.min(totalDeltaYaw * 0.4F, 8.0F);
            curveOffsetY = (random.nextFloat() - 0.5F) * Math.min(totalDeltaPitch * 0.3F, 4.0F);

            // Овершут (промах мимо цели при быстрых фликах с коррекцией)
            if (totalDeltaYaw > 25.0F && random.nextFloat() < 0.65F) {
                isOvershooting = true;
                overshootYaw = (random.nextBoolean() ? 1 : -1) * (totalDeltaYaw * (0.05F + random.nextFloat() * 0.08F));
                overshootPitch = (random.nextFloat() - 0.5F) * 3.0F;
            } else {
                isOvershooting = false;
                overshootYaw = 0.0F;
                overshootPitch = 0.0F;
            }
        }

        // Текущая промежуточная цель с учётом овершута
        targetYaw = calculatedYaw + (isOvershooting ? overshootYaw : 0.0F);
        targetPitch = MathHelper.clamp(calculatedPitch + (isOvershooting ? overshootPitch : 0.0F), -90.0F, 90.0F);

        // 4. Кинематика и динамическая скорость (Закон Фиттса + нелинейное ускорение)
        float baseSpeed = 0.08F + (float) Math.sin(progress * Math.PI) * 0.12F;
        float distanceWeight = Math.min((totalDeltaYaw + totalDeltaPitch) / 60.0F, 1.5F);
        float step = MathHelper.clamp((baseSpeed * distanceWeight) + 0.04F, 0.035F, 0.35F);

        progress = Math.min(progress + step, 1.0F);

        // Кубический Безье (Ease-Out-Cubic)
        float t = progress;
        float ease = (float) (1.0 - Math.pow(1.0 - t, 3.0));

        float ctrlYaw = startYaw + wrapAngle(targetYaw - startYaw) * 0.5F + curveOffsetX;
        float ctrlPitch = startPitch + (targetPitch - startPitch) * 0.5F + curveOffsetY;

        // Quadratic Bezier Interpolation
        float bezierYaw = (1 - ease) * (1 - ease) * startYaw + 2 * (1 - ease) * ease * ctrlYaw + ease * ease * targetYaw;
        float bezierPitch = (1 - ease) * (1 - ease) * startPitch + 2 * (1 - ease) * ease * ctrlPitch + ease * ease * targetPitch;

        // 5. Органический джиттер (гармонический тремор руки)
        float[] tremor = calculateTremor(tremorIntensity);
        bezierYaw += tremor[0];
        bezierPitch += tremor[1];

        // 6. GCD Fix (квантование по сетке чувствительности мыши)
        currentYaw = applyGCD(currentYaw, bezierYaw);
        currentPitch = MathHelper.clamp(applyGCD(currentPitch, bezierPitch), -90.0F, 90.0F);

        // Снятие овершута для фазы микро-коррекции
        if (progress >= 0.75F && isOvershooting) {
            isOvershooting = false;
        }

        return new float[]{currentYaw, currentPitch};
    }

    public void stop() {
        active = false;
    }

    public void reset() {
        if (mc.player != null) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }
        progress = 1.0F;
        isOvershooting = false;
        lastTargetId = -1;
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    // ================== МУЛЬТИПОИНТ МАТРИЦА (27 ТОЧЕК) ==================
    private static Vec3d getBestHitboxPoint(Entity target, Vec3d eyePos, float cYaw, float cPitch) {
        // Уменьшаем хитбокс на 8% внутрь, чтобы не целиться в экстремальные края коллизии
        Box box = target.getBoundingBox().expand(-0.04);
        List<Vec3d> points = new ArrayList<>(27);

        double stepX = (box.maxX - box.minX) / 2.0;
        double stepY = (box.maxY - box.minY) / 2.0;
        double stepZ = (box.maxZ - box.minZ) / 2.0;

        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    points.add(new Vec3d(
                            box.minX + stepX * x,
                            box.minY + stepY * y,
                            box.minZ + stepZ * z
                    ));
                }
            }
        }

        // Ищем точку, которая требует минимального отклонения от текущей траектории прицела
        return points.stream()
                .min(Comparator.comparingDouble(p -> {
                    double dX = p.x - eyePos.x;
                    double dY = p.y - eyePos.y;
                    double dZ = p.z - eyePos.z;
                    double dist = Math.hypot(dX, dZ);

                    float y = (float) Math.toDegrees(Math.atan2(dZ, dX)) - 90.0F;
                    float pt = (float) -Math.toDegrees(Math.atan2(dY, dist));

                    return Math.hypot(Math.abs(wrapAngle(y - cYaw)), Math.abs(pt - cPitch));
                }))
                .orElse(target.getEyePos());
    }

    // ================== СОВРЕМЕННЫЙ ТРЕМОР (НОЙЗ-ДЖИТТЕР) ==================
    private float[] calculateTremor(float intensity) {
        // Псевдо-шум Перлина на суперпозиции гармоник разной частоты
        tremorTime += 0.038;
        double waveX = Math.sin(tremorTime * 1.8) * 0.45 + Math.sin(tremorTime * 4.2) * 0.35 + Math.sin(tremorTime * 8.9) * 0.20;
        double waveY = Math.cos(tremorTime * 1.4) * 0.45 + Math.cos(tremorTime * 3.8) * 0.35 + Math.cos(tremorTime * 7.7) * 0.20;

        return new float[]{
                (float) (waveX * intensity),
                (float) (waveY * intensity)
        };
    }

    // ================== GCD FIX & КОРРЕКЦИЯ СЕНСЫ ==================
    private static float applyGCD(float current, float target) {
        float gcd = getGCD();
        float delta = target - current;
        // Округляем смещение угла до целого числа тиков сенсора
        delta = Math.round(delta / gcd) * gcd;
        return current + delta;
    }

    private static float getGCD() {
        // Формула чувствительности Minecraft (1.21.4)
        float sensitivity = (float) (mc.options.getMouseSensitivity().getValue() * 0.6F + 0.2F);
        return sensitivity * sensitivity * sensitivity * 1.2F;
    }

    private static float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }
}
