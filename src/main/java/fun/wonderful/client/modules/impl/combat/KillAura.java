package fun.wonderful.client.modules.impl.combat;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ItemStack;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventMoveInput;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
import fun.wonderful.api.storages.implement.RotationStorage;
import fun.wonderful.api.storages.implement.FreeLookStorage;
import fun.wonderful.api.utils.input.MovingUtil;
import fun.wonderful.api.storages.implement.FreeLookStorage;
import fun.wonderful.api.utils.rotate.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class KillAura extends Module {

    public static KillAura INSTANCE = new KillAura();

    public final FloatSetting range = new FloatSetting("Дистанция", 3.5f, 1.0f, 6.0f, 0.1f);
    public final FloatSetting fov = new FloatSetting("FOV", 180f, 30f, 360f, 5f);
    public final ModeSetting critMode = new ModeSetting("Крит", "Smart Crit", "Smart Crit", "Only Crit", "Off");
    public final BooleanSetting sprintReset = new BooleanSetting("Сброс спринта", true);
    public final BooleanSetting noEatAttack = new BooleanSetting("Не атаковать когда ешь", true);
    public final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false);
    public final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", false);

    // Rotation modes: Smooth (silent-like) vs Snap (aim then wait)
    public final ModeSetting rotationMode = new ModeSetting("Ротация", "Smooth", "Smooth", "Snap");

    // Target modes: Free (freelook) / Default (normal) / Focused (locks to target when pressing forward)
    public final ModeSetting targetMode = new ModeSetting("Прицел", "Default", "Free", "Default", "Focused");

    private Entity target;
    private Entity currentTarget;
    public Entity getLastTarget() { return currentTarget; }

    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int lastAttackTick = -100;
    private int currentTick = 0;
    private int groundTicks = 0;
    private int landedTicks = 0;

    private float currentYaw = 0f;
    private float currentPitch = 0f;
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private float snapYaw = 0f;
    private float snapPitch = 0f;
    private boolean snapLocked = false;
    private int snapCooldown = 0;

    private final Random random = new Random();

    public KillAura() {
        super("KillAura", "Атакует цели в радиусе с плавной наводкой", ModuleCategory.COMBAT);
        addSettings(range, fov, critMode, sprintReset, noEatAttack, onlyPlayers, throughWalls, rotationMode, targetMode);
    }

    @Override
    public void onDisable() {
        needSprintReset = false;
        sprintResetDone = false;
        groundTicks = 0;
        landedTicks = 0;
        target = null;
        currentTarget = null;
        currentYaw = 0f;
        currentPitch = 0f;
        targetYaw = 0f;
        targetPitch = 0f;
        snapLocked = false;
        snapCooldown = 0;
        RotationStorage.instance.stopRotation();
        if (FreeLookStorage.isActive()) {
            FreeLookStorage.setActive(false);
        }
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        currentTick++;

        if (currentYaw == 0f && currentPitch == 0f) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        if (mc.player.isOnGround()) {
            groundTicks++;
            landedTicks++;
        } else {
            groundTicks = 0;
            landedTicks = 0;
        }

        target = findTarget();
        if (target == null) {
            needSprintReset = false;
            sprintResetDone = false;
            currentTarget = null;
            snapLocked = false;
            snapCooldown = 0;
            if (RotationStorage.instance.isRotating()) {
                RotationStorage.instance.stopRotation();
            }
            if (FreeLookStorage.isActive()) {
                FreeLookStorage.setActive(false);
            }
            return;
        }
        currentTarget = target;

        calculateRotationToTarget(target);

        // Snap mode: wait until hit, then release
        if ("Snap".equals(rotationMode.getCurrent())) {
            if (snapCooldown > 0) {
                snapCooldown--;
                if (snapCooldown == 0) {
                    snapLocked = false;
                }
            }
            if (!snapLocked) {
                snapYaw = targetYaw;
                snapPitch = targetPitch;
                snapLocked = true;
                snapCooldown = 30; // hold for 30 ticks (~1.5s) or until hit
            }
            currentYaw = snapYaw;
            currentPitch = snapPitch;
            RotationStorage.update(new Rotation(snapYaw, snapPitch), 100, 0, 100, 0, 100, 0, isSilent());
        } else {
            // Smooth mode
            smoothAim();
            RotationStorage.update(new Rotation(currentYaw, currentPitch), 35f, 25f, 35f, 25f, 100, 0, isSilent());
        }

        if (currentTick - lastAttackTick < 5) return;
        if (noEatAttack.isState() && mc.player.isUsingItem()) return;

        boolean inCrosshair = isTargetInCrosshair(target);
        if (!throughWalls.isState() && mc.world.raycast(
                new net.minecraft.world.RaycastContext(
                        mc.player.getEyePos(),
                        target.getEyePos(),
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE,
                        mc.player
                )).getType() != net.minecraft.util.hit.BlockHitResult.Type.MISS) {
            return;
        }

        boolean isMace = mc.player.getMainHandStack().getItem() instanceof MaceItem;
        if (!canCritHit(isMace)) return;

        // Focused mode: when pressing forward, move toward target
        if ("Focused".equals(targetMode.getCurrent())) {
            float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
            if (Math.abs(yawDiff) > 90f) return; // don"t attack if facing away
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        lastAttackTick = currentTick;

        // Snap: release after hit
        if ("Snap".equals(rotationMode.getCurrent()) && snapLocked) {
            snapLocked = false;
            snapCooldown = 5;
        }

        // Sprint reset
        if (sprintReset.isState()) {
            needSprintReset = true;
            sprintResetDone = false;
        }
    }

    @EventLink
    public void onMoveInput(EventMoveInput event) {
        if (mc.player == null) return;

        // Sprint reset first (blocks everything else this tick)
        if (sprintReset.isState() && needSprintReset && !sprintResetDone) {
            event.setForward(0);
            event.setStrafe(0);
            needSprintReset = false;
            sprintResetDone = true;
            return;
        }

        if (target == null) return;

        String mode = targetMode.getCurrent();

        // Free: movement corrected to freelook direction
        if ("Free".equals(mode)) {
            MovingUtil.fixMovementFree(event);
            return;
        }

        // Focused: movement corrected toward target rotation
        if ("Focused".equals(mode)) {
            float yaw = RotationStorage.instance != null && RotationStorage.instance.targetRotation() != null
                    ? RotationStorage.instance.targetRotation().getYaw()
                    : mc.player.getYaw();
            MovingUtil.fixMovementFocus(event, yaw);
        }
    }

    private boolean isSilent() {
        // Silent when NOT in Free mode (Free = camera moves freely, server sees hits)
        return !"Free".equals(targetMode.getCurrent());
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private boolean isTargetInCrosshair(Entity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = getLookVector(currentYaw, currentPitch);
        double dist = range.get() + 1.0;
        Vec3d endPos = eyePos.add(lookVec.x * dist, lookVec.y * dist, lookVec.z * dist);
        Box box = target.getBoundingBox().expand(0.1);
        return box.raycast(eyePos, endPos).isPresent();
    }

    private Vec3d getLookVector(float yaw, float pitch) {
        float yr = (float) Math.toRadians(yaw);
        float pr = (float) Math.toRadians(pitch);
        return new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
    }

    private boolean canCritHit(boolean isMace) {
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        boolean inWeb = isInCobweb();
        boolean inWater = mc.player.isTouchingWater() || mc.player.isSubmergedInWater();
        boolean blind = mc.player.hasStatusEffect(StatusEffects.BLINDNESS);
        boolean forcedAttack = inWeb || inWater || blind;
        boolean falling = !inWeb && isCritFalling();
        float cooldownThreshold = 0.848f;
        String mode = critMode.getCurrent();

        if (isMace) return cooldown >= cooldownThreshold;
        if (forcedAttack) return cooldown >= 0.96f;

        if ("Only Crit".equals(mode)) {
            return falling && cooldown >= cooldownThreshold;
        }
        if ("Smart Crit".equals(mode)) {
            if (falling) {
                return cooldown >= cooldownThreshold;
            } else {
                if (mc.options.jumpKey.isPressed()) return false;
                return cooldown >= 0.96f;
            }
        }
        return cooldown >= cooldownThreshold;
    }

    private boolean isInCobweb() {
        BlockPos pos = mc.player.getBlockPos();
        return mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB
                || mc.world.getBlockState(pos.up()).getBlock() == Blocks.COBWEB;
    }

    private boolean isCritFalling() {
        return !mc.player.isOnGround()
                && mc.player.getVelocity().y < -0.05
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && !mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING);
    }

    private void calculateRotationToTarget(Entity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getBoundingBox().getCenter();
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90f;
        targetPitch = (float) -(Math.toDegrees(Math.atan2(dy, dist)));
        targetYaw = MathHelper.wrapDegrees(targetYaw);
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);
    }

    private void smoothAim() {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float maxYawSpeed = 35f;
        float maxPitchSpeed = 25f;
        float yawStep = MathHelper.clamp(yawDiff, -maxYawSpeed, maxYawSpeed);
        float pitchStep = MathHelper.clamp(pitchDiff, -maxPitchSpeed, maxPitchSpeed);
        currentYaw = MathHelper.wrapDegrees(currentYaw + yawStep);
        currentPitch = MathHelper.clamp(currentPitch + pitchStep, -90f, 90f);
    }

    private Entity findTarget() {
        List<Entity> entities = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (e == null) continue;
            if (!e.isAlive()) continue;
            if (e.isSpectator()) continue;
            if (e instanceof LivingEntity living) {
                if (living.getHealth() <= 0) continue;
            }
            if (onlyPlayers.isState() && !(e instanceof PlayerEntity)) continue;
            if (e == mc.player) continue;
            if (e.isInvisible()) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > range.get()) continue;
            if (!isInFov(e)) continue;
            entities.add(e);
        }
        if (entities.isEmpty()) return null;
        entities.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return entities.get(0);
    }

    private boolean isInFov(Entity entity) {
        if (fov.get() >= 360f) return true;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVector();
        Vec3d targetVec = entity.getBoundingBox().getCenter().subtract(eyePos).normalize();
        double dot = lookVec.dotProduct(targetVec);
        double angle = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1, 1)));
        return angle <= fov.get() / 2.0;
    }
}
