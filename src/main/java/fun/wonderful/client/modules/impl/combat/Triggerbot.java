package fun.wonderful.client.modules.impl.combat;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.ListSetting;

/**
 * TriggerBot: бьёт то, что под прицелом (ванильный crosshairTarget).
 * <p>
 * Логика (порт):
 * <ul>
 *   <li>CooldownClicker — двойной гейт: минимум 230 мс между удаарами И
 *       прогресс кулдауна атаки >= 0.92;</li>
 *   <li>предикт крита: заранее сбрасывает спринт, пока цель приближается
 *       к прицелу (луч 3.2), — к моменту удара сервер уже видел сброс;</li>
 *   <li>SprintControl — трекинг серверного спринта и восстановление
 *       клавиши спринта после сброса;</li>
 *   <li>«Только криты» / «Умные криты» (чередование крит/удар на земле),
 *     полный чек движенческих ограничений (слепота, левитация, паутина,
 *     вода, лава, лазание, полёт);</li>
 *   <li>выбор целей: игроки / мобы / животные / стойки брони.</li>
 * </ul>
 */
public class Triggerbot extends Module {

    public static Triggerbot INSTANCE = new Triggerbot();

    // ===== Настройки =====

    public final ListSetting attack = new ListSetting("Атаковать",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Животные", true),
            new BooleanSetting("Стойки", true));

    public final BooleanSetting onlyCritical = new BooleanSetting("Только криты", true);

    public final BooleanSetting smartCritical = new BooleanSetting("Умные криты", false);

    // ===== CooldownClicker =====

    private long lastClickTime = System.currentTimeMillis();

    private boolean cooldownPassed() {
        if (System.currentTimeMillis() - lastClickTime < 230L) return false;
        return mc.player.getAttackCooldownProgress(1.0f) >= 0.92f;
    }

    private void resetClickTime() {
        lastClickTime = System.currentTimeMillis();
    }

    // ===== SprintControl =====

    /**
     * Серверный спринт: ваниль шлёт START/STOP_SPRINTING по факту
     * isSprinting() в конце прошлого тика — снимаем значение в начале тика.
     */
    private boolean serverSprint;

    /** Тиков ещё держать сброс спринта. */
    private int sprintResetTicks;

    /** Сброс был выполнен и клавишу спринта нужно восстановить. */
    private boolean hasReset;

    private void sprintControlTick() {
        serverSprint = mc.player.isSprinting();
        if (sprintResetTicks > 0) {
            hasReset = true;
            mc.player.setSprinting(false);
            sprintResetTicks--;
        } else if (hasReset) {
            mc.options.sprintKey.setPressed(true);
            mc.player.setSprinting(true);
            hasReset = false;
        }
    }

    private void restoreSprint() {
        if (hasReset) {
            mc.options.sprintKey.setPressed(true);
            mc.player.setSprinting(true);
            hasReset = false;
        }
        sprintResetTicks = 0;
    }

    // ===== AttackCritical =====

    /** Заблаговременный сброс спринта: к моменту удара сервер уже не видит спринт. */
    private void preCritical() {
        if (mc.player.isSprinting()) {
            sprintResetTicks = 1;
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
        }
    }

    /** Спринт ли видит сервер прямо сейчас (для валидации крит-удара). */
    private boolean isServerSprinting() {
        return serverSprint
                && !mc.player.isGliding()
                && !mc.player.isTouchingWater();
    }

    // ===== TriggerClicker =====

    private boolean canAttack() {
        if (!cooldownPassed()) return false;

        boolean noRestrict = !hasMovementRestrictions();

        if (smartCritical.isState() && onlyCritical.isState()) {
            // Умные + только криты: чередуем крит и удар на земле;
            // при ограничениях (вода/паутина/слепота…) крит невозможен — бьём как есть
            if (noRestrict) {
                return canCrit() || mc.player.isOnGround();
            }
            return true;
        }

        if (onlyCritical.isState() && noRestrict) {
            return canCrit();
        }
        return true;
    }

    private boolean hasMovementRestrictions() {
        return mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || isInCobweb()
                || mc.player.isSubmergedInWater()
                || mc.player.isInLava()
                || mc.player.isClimbing()
                || mc.player.getAbilities().flying;
    }

    private boolean canCrit() {
        return !mc.player.isOnGround() && mc.player.fallDistance > 0f;
    }

    /** Только оторвались от земли: дистанция падения ещё не накоплена. */
    private boolean hasDistanceFix() {
        return !mc.player.isOnGround() && mc.player.fallDistance < 0.0001f;
    }

    private void onAttackEntity(Entity entity) {
        if (canAttack() || hasDistanceFix()) {
            preCritical();
        }

        if (!canAttack()) return;

        // Бьём только когда сервер уже не видит спринта — удар считается критом
        if (!isServerSprinting()) {
            attack(entity);
        }
    }

    private void attack(Entity entity) {
        resetClickTime();

        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    // ===== TargetFinder =====

    /** Цель под ванильным прицелом (crosshairTarget) с фильтром по типам. */
    private Entity getCrossTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult hitResult) {
            Entity entity = hitResult.getEntity();
            if (entity instanceof LivingEntity le && !le.isRemoved() && le.isAlive()
                    && hasAccessTarget(entity)) {
                return entity;
            }
        }
        return null;
    }

    private boolean hasAccessTarget(Entity entity) {
        if (attack.is("Игроки") && entity instanceof PlayerEntity && entity != mc.player) return true;
        if (attack.is("Мобы") && entity instanceof MobEntity) return true;
        if (attack.is("Животные") && entity instanceof AnimalEntity) return true;
        return attack.is("Стойки") && entity instanceof ArmorStandEntity;
    }

    // ===== Тик =====

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        // Не работаем вдвоём с KillAura — двойной удар в один тик = флаг античита
        if (KillAura.INSTANCE.isEnable()) return;

        sprintControlTick();

        // Предикт: цель приближается к прицелу (луч 3.2) — сбрасываем спринт заранее,
        // чтобы к моменту реального удара сервер уже обработал STOP_SPRINTING
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookVec.x * 3.2, lookVec.y * 3.2, lookVec.z * 3.2);
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.getBoundingBox().raycast(eyePos, endPos).isEmpty()) continue;
            if (!hasAccessTarget(entity)) continue;
            if (canAttack() || hasDistanceFix()) {
                preCritical();
            }
            break;
        }

        Entity target = getCrossTarget();
        if (target != null) {
            onAttackEntity(target);
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            restoreSprint();
        }
        resetClickTime();
        super.onDisable();
    }

    // ===== Утилиты =====

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
}
