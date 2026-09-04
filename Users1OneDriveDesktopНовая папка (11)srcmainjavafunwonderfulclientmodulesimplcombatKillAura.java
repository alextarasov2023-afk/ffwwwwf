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
    public final ModeSetting correction = new ModeSetting("Коррекция", "Default", "Free", "Default", "Focused");
    public final ModeSetting rotationMode = new ModeSetting("Ротация", "Smooth", "Smooth", "Snap");

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
    private float snapCooldown = 0f;
    private final Random random = new Random();

    public KillAura() {
        super("KillAura", "Атакует цели в радиусе с плавной наводкой", ModuleCategory.COMBAT);
        addSettings(range, fov, critMode, sprintReset, noEatAttack, onlyPlayers, throughWalls, correction, rotationMode);
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
        snapCooldown = 0f;
        RotationStorage.instance.stopRotation();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        currentTick++;
        
        if (snapCooldown > 0) snapCooldown--;
        
        if (currentYaw == 0f && currentPitch == 0f) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
        }

        if (mc.player.isOnGround()) {
            groundTicks++;
            landedTicks++;
        } else {
            groundTicks = 0;
        }

        target = findTarget();
        if (target == null) {
            needSprintReset = false;
            sprintResetDone = false;
            currentTarget = null;
            return;
        }

        currentTarget = target;
        calculateRotationToTarget(target);
        
        String rotMode = rotationMode.getCurrent();
        String corrMode = correction.getCurrent();
        
        if ("Snap".equals(rotMode)) {
            if (snapCooldown <= 0) {
                currentYaw = targetYaw;
                currentPitch = targetPitch;
                snapCooldown = 3f;
            }
        } else {
            smoothAim();
        }
        
        Rotation rotation = new Rotation(currentYaw, currentPitch);
        
        if ("Free".equals(corrMode)) {
            RotationStorage.update(rotation, 35f, 25f, 35f, 25f, 100, 0, false);
        } else if ("Focused".equals(corrMode)) {
            RotationStorage.update(rotation, 35f, 25f, 35f, 25f, 100, 0, false);
            if (mc.options.forwardKey.isPressed()) {
                mc.player.setYaw(targetYaw);
            }
        } else {
            RotationStorage.update(rotation, 35f, 25f, 35f, 25f, 100, 0, true);
        }
        
        if (currentTick - lastAttackTick < 5) return;
        if (noEatAttack.isState() && mc.player.isUsingItem()) return;
        if (!canCritHit(isMace())) return;
        if (!isTargetInCrosshair(target)) return;

        attack(target);
    }

    @EventLink
    public void onMoveInput(EventMoveInput event) {
        if (mc.player == null || !isEnable()) return;
        if (target == null) return;
        if (!sprintReset.isState()) return;

        if (needSprintReset && !sprintResetDone) {
            event.setForward(0f);
            event.setStrafe(0f);
            event.setJump(false);
            sprintResetDone = true;
            needSprintReset = false;
        }
    }

    private boolean isMace() {
        ItemStack mainHand = mc.player.getMainHandStack();
        return mainHand.getItem() instanceof MaceItem;
    }

    private Entity findTarget() {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.isRemoved()) continue;
            if (onlyPlayers.isState() && !(entity instanceof PlayerEntity)) continue;
            if (mc.player.distanceTo(entity) > range.get() + 1.0) continue;
            if (!isEntityInFov(entity)) continue;
            if (!throughWalls.isState() && !mc.player.canSee(entity)) continue;
            entities.add(entity);
        }
        if (entities.isEmpty()) return null;
        entities.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return entities.get(0);
    }

    private boolean isEntityInFov(Entity entity) {
        float yaw = mc.player.getYaw();
        float diff = MathHelper.wrapDegrees(yaw - getYawToEntity(entity));
        return Math.abs(diff) <= fov.get() / 2f;
    }

    private float getYawToEntity(Entity entity) {
        Vec3d diff = entity.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
        return (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;
    }

    private void calculateRotationToTarget(Entity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetPos = target.getBoundingBox().getCenter();
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
    }

    private void smoothAim() {
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;
        float dist = (float) mc.player.distanceTo(target);
        float speed = 0.15f + Math.min(dist * 0.05f, 0.35f);
        float progress = Math.min(1f, speed * 5f);
        float eased = easeOutCubic(progress);
        currentYaw += deltaYaw * eased;
        currentPitch += deltaPitch * eased;
        currentPitch = MathHelper.clamp(currentPitch, -90f, 90f);
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
            if (!falling) return false;
            if (landedTicks < 1) return false;
            return cooldown >= cooldownThreshold;
        }
        if ("Smart Crit".equals(mode)) {
            if (falling) {
                if (landedTicks < 1) return false;
                return cooldown >= cooldownThreshold;
            } else {
                if (mc.options.jumpKey.isPressed()) return false;
                if (landedTicks < 1) return false;
                return cooldown >= 0.96f;
            }
        }
        return cooldown >= cooldownThreshold;
    }

    private boolean isCritFalling() {
        if (mc.player.isOnGround()) return false;
        if (mc.player.isTouchingWater() || mc.player.isSubmergedInWater()) return false;
        if (mc.player.isClimbing()) return false;
        return mc.player.getVelocity().y < -0.08;
    }

    private boolean isInCobweb() {
        Box box = mc.player.getBoundingBox();
        for (int x = (int) Math.floor(box.minX); x <= Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++) {
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.COBWEB) return true;
                }
            }
        }
        return false;
    }

    private void attack(Entity target) {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttackTick = currentTick;
        currentTarget = target;
        landedTicks = 0;
        groundTicks = 0;
        sprintResetDone = false;
        needSprintReset = true;
    }
}
