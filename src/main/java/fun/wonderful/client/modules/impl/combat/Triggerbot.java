package fun.wonderful.client.modules.impl.combat;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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

public class Triggerbot extends Module {

    public static Triggerbot INSTANCE = new Triggerbot();

    public final FloatSetting reach = new FloatSetting("Дистанция", 3.0f, 1.0f, 3.4f, 0.05f);
    public final ModeSetting critMode = new ModeSetting("Крит", "Smart Crit", "Smart Crit", "Only Crit", "Off");
    public final BooleanSetting sprintReset = new BooleanSetting("Сброс спринта", true);
    public final BooleanSetting noEatAttack = new BooleanSetting("Не атаковать когда ешь", true);
    public final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false);
    public final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", false);

    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    /** Осталось тиков сброса спринта (2 базовых + случайные 1..3). */
    private int sprintResetTicksLeft = 0;

    private int lastAttackTick = -100;
    private int currentTick = 0;
    private int landedTicks = 0;

    private Entity lastTarget;
    public Entity getLastTarget() { return lastTarget; }


    public Triggerbot() {
        super("Triggerbot", "Автоматически атакует цель при наведении", ModuleCategory.COMBAT);
        addSettings(reach, critMode, sprintReset, noEatAttack, onlyPlayers, throughWalls);
    }

    @Override
    public void onDisable() {
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicksLeft = 0;
        landedTicks = 0;
        lastTarget = null;
        RotationStorage.instance.stopRotation();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        // Не атакуем вдвоём с KillAura — двойная атака в один тик = мгновенный флаг античита
        if (KillAura.INSTANCE.isEnable()) return;
        currentTick++;

        if (mc.player.isOnGround()) {
            landedTicks++;
        }

        if (currentTick - lastAttackTick < 5) return;

        if (noEatAttack.isState() && mc.player.isUsingItem()) {
            ItemStack stack = mc.player.getMainHandStack();
            if (stack.getItem().getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD) ||
                stack.getItem().getComponents().contains(net.minecraft.component.DataComponentTypes.CONSUMABLE)) {
                return;
            }
        }

        Entity target = findTarget();
        if (target == null) {
            needSprintReset = false;
            sprintResetDone = false;
            return;
        }

        boolean isMace = mc.player.getMainHandStack().getItem() instanceof MaceItem;

        if (!canCritHit(target, isMace)) return;

        boolean shouldResetSprint = sprintReset.isState() && !isMace && mc.player.isSprinting();
        if (shouldResetSprint && !needSprintReset && !sprintResetDone) {
            needSprintReset = true;
            sprintResetTicksLeft = 2 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 4);
            return;
        }

        // Держим паузу без движения всю длительность, затем бьём
        if (needSprintReset && !sprintResetDone) return;

        attack(target);
    }

    @EventLink
    public void onMoveInput(EventMoveInput event) {
        if (needSprintReset && !sprintResetDone) {
            event.setForward(0f);
            event.setStrafe(0f);
            event.setJump(false);
            // Каждый тик паузы гасим движение; отсчитав 2+1..3 тика — отпускаем
            if (--sprintResetTicksLeft <= 0) {
                needSprintReset = false;
                sprintResetDone = true;
            }
        }
    }

    private Entity findTarget() {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        double dist = reach.get();
        Vec3d endPos = eyePos.add(lookVec.x * dist, lookVec.y * dist, lookVec.z * dist);

        Entity closest = null;
        double closestDist = dist;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.isRemoved() || ((LivingEntity) entity).getHealth() <= 0) continue;

            if (onlyPlayers.isState() && !(entity instanceof PlayerEntity)) continue;

            Box box = entity.getBoundingBox().expand(0.1);
            if (box.contains(eyePos)) continue;

            if (!throughWalls.isState() && !mc.player.canSee(entity)) continue;

            if (box.raycast(eyePos, endPos).isPresent()) {
                // Reach-лимит: реальная дистанция от глаз до ближайшей точки хитбокса
                // (ваниль 3.0, с запасом) — дальше не бьём, иначе флаг античита
                double dx = Math.max(Math.max(box.minX - eyePos.x, 0.0), eyePos.x - box.maxX);
                double dy = Math.max(Math.max(box.minY - eyePos.y, 0.0), eyePos.y - box.maxY);
                double dz = Math.max(Math.max(box.minZ - eyePos.z, 0.0), eyePos.z - box.maxZ);
                double boxDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (boxDist <= 2.97 && boxDist < closestDist) {
                    closest = entity;
                    closestDist = boxDist;
                }
            }
        }
        return closest;
    }

    private boolean canCritHit(Entity target, boolean isMace) {
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        boolean inWeb = isInCobweb();
        boolean inWater = mc.player.isTouchingWater() || mc.player.isSubmergedInWater();
        boolean blind = mc.player.hasStatusEffect(StatusEffects.BLINDNESS);
        boolean forcedAttack = inWeb || inWater || blind;
        boolean falling = !inWeb && isCritFalling();
        float cooldownThreshold = 0.848f;
        // Булава: бьём только при остатке кулдауна < 7.5% (прогресс >= 0.925)
        float maceThreshold = 0.925f;
        String mode = critMode.getCurrent();

        if (isMace) return cooldown >= maceThreshold;
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
                // На земле — не бьём если зажат пробел (игрок прыгает для крита)
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
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.COBWEB) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void attack(Entity target) {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttackTick = currentTick;
        lastTarget = target;
        landedTicks = 0;
        sprintResetDone = false;
        needSprintReset = false;
    }
}