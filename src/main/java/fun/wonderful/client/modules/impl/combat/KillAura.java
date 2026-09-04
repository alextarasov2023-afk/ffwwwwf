package fun.wonderful.client.modules.impl.combat;

import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
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
import fun.wonderful.Wonderful;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventMoveInput;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.impl.render.FreeLook;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
import fun.wonderful.api.storages.implement.RotationStorage;
import fun.wonderful.api.storages.implement.FreeLookStorage;
import fun.wonderful.api.utils.input.MovingUtil;
import fun.wonderful.api.utils.rotate.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {

    public static KillAura INSTANCE = new KillAura();

    public final FloatSetting range = new FloatSetting("Дистанция", 3.5f, 1.0f, 6.0f, 0.1f);
    public final FloatSetting fov = new FloatSetting("FOV", 180f, 30f, 360f, 5f);
    public final ModeSetting critMode = new ModeSetting("Крит", "Smart Crit", "Smart Crit", "Only Crit", "Off");
    public final BooleanSetting sprintReset = new BooleanSetting("Сброс спринта", true);
    public final BooleanSetting noEatAttack = new BooleanSetting("Не атаковать когда ешь", true);
    public final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false);
    public final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", false);
    public final BooleanSetting ignoreBots = new BooleanSetting("Игнорировать ботов", true);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Не бить друзей", true);
    public final BooleanSetting crosshairOnly = new BooleanSetting("Только в прицел", false);
    public final FloatSetting attackDelay = new FloatSetting("Задержка удара", 5f, 0f, 10f, 1f);

    // Приоритет выбора цели: ближе / слабее / под прицелом
    public final ModeSetting priority = new ModeSetting("Приоритет", "Дистанция", "Дистанция", "Здоровье", "Угол");

    // Rotation modes: Smooth (silent-like) vs Snap (aim then wait)
    public final ModeSetting rotationMode = new ModeSetting("Ротация", "Smooth", "Smooth", "Snap");
    public final FloatSetting rotationSpeed = new FloatSetting("Скорость ротации", 35f, 5f, 90f, 1f);

    // Target modes: Free (freelook) / Default (normal) / Focused (locks to target when pressing forward)
    public final ModeSetting targetMode = new ModeSetting("Прицел", "Default", "Free", "Default", "Focused");

    private Entity target;
    private Entity currentTarget;
    public Entity getLastTarget() { return currentTarget; }

    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int lastAttackTick = -100;
    private int currentTick = 0;

    private boolean rotationsInitialized = false;
    private float currentYaw = 0f;
    private float currentPitch = 0f;
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private float snapYaw = 0f;
    private float snapPitch = 0f;
    private boolean snapLocked = false;
    private int snapCooldown = 0;

    public KillAura() {
        super("KillAura", "Атакует цели в радиусе с плавной наводкой", ModuleCategory.COMBAT);
        addSettings(range, fov, critMode, sprintReset, noEatAttack, onlyPlayers, throughWalls,
                ignoreBots, ignoreFriends, crosshairOnly, attackDelay, priority, rotationMode,
                rotationSpeed, targetMode);
    }

    @Override
    public void onDisable() {
        needSprintReset = false;
        sprintResetDone = false;
        target = null;
        currentTarget = null;
        rotationsInitialized = false;
        snapLocked = false;
        snapCooldown = 0;
        stopRotations();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        currentTick++;

        // Инициализация ротации один раз от текущего взгляда (0/0 — валидные углы,
        // поэтому сравнение с нулём заменили на явный флаг)
        if (!rotationsInitialized) {
            currentYaw = mc.player.getYaw();
            currentPitch = mc.player.getPitch();
            rotationsInitialized = true;
        }

        target = findTarget();
        if (target == null) {
            needSprintReset = false;
            sprintResetDone = false;
            currentTarget = null;
            snapLocked = false;
            snapCooldown = 0;
            if (RotationStorage.instance != null && RotationStorage.instance.isRotating()) {
                stopRotations();
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
            RotationStorage.update(new Rotation(snapYaw, snapPitch),
                    100f, 100f, 100f, 100f, 100, 0, isSilent());
        } else {
            // Smooth mode
            smoothAim();
            float speed = rotationSpeed.get();
            float pitchSpeed = speed * 0.72f;
            RotationStorage.update(new Rotation(currentYaw, currentPitch),
                    speed, pitchSpeed, speed, pitchSpeed, 100, 0, isSilent());
        }

        if (currentTick - lastAttackTick < attackDelayTicks()) return;
        if (noEatAttack.isState() && isEating()) return;

        if (!canSeeTarget()) return;

        boolean isMace = mc.player.getMainHandStack().getItem() instanceof MaceItem;
        if (!canCritHit(isMace)) return;

        // Легит-режим: бьём только когда прицел реально наведён на хитбокс
        if (crosshairOnly.isState() && !isTargetInCrosshair(target)) return;

        // Focused mode: when pressing forward, move toward target
        if ("Focused".equals(targetMode.getCurrent())) {
            float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
            if (Math.abs(yawDiff) > 90f) return; // don't attack if facing away
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

    /** Задержка между ударами в тиках с учётом TPS сервера (TpsSync). */
    private int attackDelayTicks() {
        float delay = attackDelay.get();
        if (TpsSync.INSTANCE.isEnable()) {
            delay *= 20f / TpsSync.INSTANCE.getCurrentTPS();
        }
        return Math.round(delay);
    }

    /** Едим/пьём что-то в руках — атаковать не стоит (проверка как в Triggerbot). */
    private boolean isEating() {
        if (!mc.player.isUsingItem()) return false;
        for (Hand hand : Hand.values()) {
            ItemStack stack = mc.player.getStackInHand(hand);
            if (stack.getItem().getComponents().contains(DataComponentTypes.FOOD)
                    || stack.getItem().getComponents().contains(DataComponentTypes.CONSUMABLE)) {
                return true;
            }
        }
        return false;
    }

    /** Стена между нами и целью (проверяем точку прицеливания и центр хитбокса). */
    private boolean canSeeTarget() {
        if (throughWalls.isState()) return true;
        if (raycastMisses(targetPosFor(target))) return true;
        return raycastMisses(target.getBoundingBox().getCenter());
    }

    private boolean raycastMisses(Vec3d point) {
        return mc.world.raycast(
                new net.minecraft.world.RaycastContext(
                        mc.player.getEyePos(),
                        point,
                        net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                        net.minecraft.world.RaycastContext.FluidHandling.NONE,
                        mc.player
                )).getType() == net.minecraft.util.hit.BlockHitResult.Type.MISS;
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

    /** Multipoint-lite: точка прицеливания на хитбоксе, требующая минимального доворота. */
    private Vec3d targetPosFor(Entity target) {
        Box box = target.getBoundingBox().expand(-0.05);
        Vec3d eye = mc.player.getEyePos();
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double h = box.maxY - box.minY;

        List<Vec3d> points = new ArrayList<>();
        for (double f : new double[]{0.3, 0.55, 0.8}) {
            points.add(new Vec3d(cx, box.minY + h * f, cz));
        }
        points.add(box.getCenter());

        Vec3d best = box.getCenter();
        double bestCost = Double.MAX_VALUE;
        for (Vec3d p : points) {
            double dx = p.x - eye.x;
            double dy = p.y - eye.y;
            double dz = p.z - eye.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            float pYaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90f;
            float pPitch = (float) -(Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
            double dYaw = Math.abs(MathHelper.wrapDegrees(pYaw - currentYaw));
            double dPitch = Math.abs(pPitch - currentPitch);

            // Доворот важнее дистанции: целимся туда, куда рука дотянется быстрее
            double cost = (dYaw * 0.7 + dPitch) * 10.0 + dist;
            if (cost < bestCost) {
                bestCost = cost;
                best = p;
            }
        }
        return best;
    }

    private void calculateRotationToTarget(Entity target) {
        Vec3d aimPoint = targetPosFor(target);
        Vec3d eyePos = mc.player.getEyePos();
        double dx = aimPoint.x - eyePos.x;
        double dy = aimPoint.y - eyePos.y;
        double dz = aimPoint.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90f;
        targetPitch = (float) -(Math.toDegrees(Math.atan2(dy, dist)));
        targetYaw = MathHelper.wrapDegrees(targetYaw);
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);
    }

    /** Smooth: пропорциональное замедление у цели (плавный «довод» руки). */
    private void smoothAim() {
        float speed = rotationSpeed.get();
        float pitchSpeed = speed * 0.72f;
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float yawStep = MathHelper.clamp(yawDiff * 0.45f, -speed, speed);
        float pitchStep = MathHelper.clamp(pitchDiff * 0.45f, -pitchSpeed, pitchSpeed);
        currentYaw = MathHelper.wrapDegrees(currentYaw + yawStep);
        currentPitch = MathHelper.clamp(currentPitch + pitchStep, -90f, 90f);
    }

    /** Полный набор фильтров цели: жива, не бот/друг, в радиусе и FOV. */
    private boolean isValidTarget(Entity e) {
        if (e == null || e == mc.player) return false;
        if (!e.isAlive() || e.isSpectator()) return false;
        if (e instanceof LivingEntity living) {
            if (living.getHealth() <= 0) return false;
            if (ignoreBots.isState() && AntiBot.checkBot(living)) return false;
            if (ignoreFriends.isState() && isFriend(living)) return false;
        }
        if (onlyPlayers.isState() && !(e instanceof PlayerEntity)) return false;
        if (e.isInvisible()) return false;
        if (mc.player.distanceTo(e) > range.get()) return false;
        return isInFov(e);
    }

    private boolean isFriend(LivingEntity entity) {
        if (Wonderful.INSTANCE == null || Wonderful.INSTANCE.friendStorage == null) return false;
        if (!(entity instanceof PlayerEntity)) return false;
        return Wonderful.INSTANCE.friendStorage.isFriend(entity.getName().getString());
    }

    private Entity findTarget() {
        // Липкая цель: пока текущая цель валидна — не переключаемся (анти-мерцание)
        if (currentTarget != null && isValidTarget(currentTarget)) {
            return currentTarget;
        }

        List<Entity> entities = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (isValidTarget(e)) {
                entities.add(e);
            }
        }
        if (entities.isEmpty()) return null;

        String mode = priority.getCurrent();
        Comparator<Entity> comparator;
        switch (mode) {
            case "Здоровье" -> comparator = Comparator.comparingDouble(e ->
                    e instanceof LivingEntity living ? living.getHealth() : mc.player.distanceTo(e));
            case "Угол" -> comparator = Comparator.comparingDouble(this::getAngleTo);
            default -> comparator = Comparator.comparingDouble(e -> mc.player.distanceTo(e));
        }
        entities.sort(comparator);
        return entities.get(0);
    }

    /** Угол между взглядом и направлением на цель (для приоритета «Угол»). */
    private double getAngleTo(Entity entity) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVector();
        Vec3d targetVec = entity.getBoundingBox().getCenter().subtract(eyePos).normalize();
        double dot = lookVec.dotProduct(targetVec);
        return Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1, 1)));
    }

    private boolean isInFov(Entity entity) {
        if (fov.get() >= 360f) return true;
        return getAngleTo(entity) <= fov.get() / 2.0;
    }

    /** Останавливает ротацию, не трогая FreeLook, если тот включён отдельным модулем. */
    private void stopRotations() {
        if (RotationStorage.instance != null) {
            RotationStorage.instance.stopRotation();
        }
        if (FreeLookStorage.isActive() && !FreeLook.INSTANCE.isEnable()) {
            FreeLookStorage.setActive(false);
        }
    }
}
