package fun.wonderful.client.modules.impl.combat;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import fun.wonderful.Wonderful;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ListSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;

/**
 * Умный TriggerBot: бьёт то, что под прицелом, с полным знанием механики боя.
 *
 * <p><b>Предметы и кулдаун</b> (порог прогресса, при котором удар ещё выгоден):
 * <ul>
 *   <li>Меч 1.6 atk/s — 87% (быстрое оружие, ранний удар не теряет DPS);</li>
 *   <li>Топор 0.8–1.0 — 93% (медленный, недобитый удар = потерянный урон);</li>
 *   <li>Булава 0.6 — 95% (урон смэша растёт от падения — ждём почти полный
 *       заряд и стараемся бить в падении);</li>
 *   <li>Трезубец 2.2 — 85%; остальное (рука/кирка/лопата) — 90%.</li>
 * </ul>
 *
 * <p><b>Крит возможен только если</b>: не на земле, fallDistance &gt; 0,
 * нет спринта (спринт-удар сбивает крит), нет слепоты/левитации,
 * не в воде/лаве/паутине, не лазаем, не в полёте. Jump Boost — разрешён.
 *
 * <p><b>Сброс спринта</b>:
 * <ul>
 *   <li>Обычный — модуль сам сбрасывает спринт заранее (предикт по лучу)
 *       и восстанавливает его только после приземления;</li>
 *   <li>Packet — как обычный, но в падении бьёт сразу, без остановки;</li>
 *   <li>Легитный — спринт не трогает: спринтуешь — ждёт (остановись сам).</li>
 * </ul>
 *
 * <p>Авто-прыжок готовит крит, когда кулдаун почти заряжен; друзей
 * (FriendStorage) и ботов (AntiBot) не трогает; еду/лук не сбивает.
 */
public class Triggerbot extends Module {

    public static Triggerbot INSTANCE = new Triggerbot();

    // ===== Настройки =====

    public final ListSetting attack = new ListSetting("Атаковать",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Животные", true),
            new BooleanSetting("Стойки", true));

    public final ModeSetting critMode = new ModeSetting("Криты", "Умные криты",
            "Умные криты", "Только криты", "Выкл");

    public final FloatSetting reach = new FloatSetting("Дистанция", 3.0f, 2.0f, 3.4f, 0.05f);

    public final ModeSetting sprintMode = new ModeSetting("Сброс спринта", "Обычный",
            "Обычный", "Packet", "Легитный");

    public final BooleanSetting autoJump = new BooleanSetting("Авто-прыжок", true);

    public Triggerbot() {
        super("Triggerbot", "Бьёт цель под прицелом: умные криты, предметные кулдауны, сброс спринта", ModuleCategory.COMBAT);
        addSettings(attack, critMode, reach, sprintMode, autoJump);
    }

    // ===== Время =====

    private long lastClickTime = System.currentTimeMillis();
    private int currentTick;
    private int lastAttackTick = -100;
    private int lastJumpTick = -100;

    // ===== Сброс спринта =====

    /** Тиков ещё держать сброс спринта. */
    private int sprintResetTicks;

    /** Сброс выполнен — после приземления вернуть клавишу спринта. */
    private boolean hasReset;

    /**
     * Связь с модулем Sprint: пока держим сброс спринта,
     * автоспринт не должен форсить спринт обратно.
     */
    public static boolean isBlockingSprint() {
        Triggerbot t = INSTANCE;
        return t != null && t.isEnable()
                && (t.sprintResetTicks > 0 || t.hasReset);
    }

    private void sprintControlTick() {
        if (sprintResetTicks > 0) {
            hasReset = true;
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
            sprintResetTicks--;
        } else if (hasReset && mc.player.isOnGround()) {
            // Восстанавливаем спринт только на земле: в полёте держим
            // сброшенным — весь спуск сервер видит без спринта (крит)
            mc.options.sprintKey.setPressed(true);
            mc.player.setSprinting(true);
            hasReset = false;
        }
    }

    /** Сброс спринта прямо сейчас (1 тик), удар — следующим тиком. */
    private void preCritical() {
        if (isLegitSprint()) return;
        if (mc.player.isSprinting()) {
            sprintResetTicks = 1;
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
        }
    }

    private void restoreSprint() {
        if (hasReset && mc.player != null) {
            mc.options.sprintKey.setPressed(true);
            mc.player.setSprinting(true);
            hasReset = false;
        }
        sprintResetTicks = 0;
    }

    // ===== Предметные пороги кулдауна =====

    /** Порог прогресса кулдауна для предмета в руке. */
    private float weaponThreshold() {
        Item item = mc.player.getMainHandStack().getItem();
        if (item instanceof MaceItem) return 0.95f;   // булава: смэш требует почти полный заряд
        if (item instanceof AxeItem) return 0.93f;    // топор: медленный, недобивать = терять урон
        if (item instanceof SwordItem) return 0.87f;  // меч: быстрый, ранний удар не режет DPS
        if (item instanceof TridentItem) return 0.85f;
        return 0.90f;                                 // рука/кирка/лопата и прочее
    }

    private float cooldownProgress() {
        return mc.player.getAttackCooldownProgress(0.5f);
    }

    /** Реальная дистанция от глаз до ближайшей точки хитбокса (как считает сервер). */
    private double boxDistance(Entity entity) {
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eye.x, 0.0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0.0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0.0), eye.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ===== Условия крита =====

    /** Ванильные запреты на крит. */
    private boolean hasMovementRestrictions() {
        return mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                || isInCobweb()
                || mc.player.isSubmergedInWater()
                || mc.player.isInLava()
                || mc.player.isClimbing()
                || mc.player.getAbilities().flying;
    }

    /** Крит-фаза: падаем и уже накопили дистанцию. */
    private boolean isFallingCrit() {
        return !mc.player.isOnGround() && mc.player.fallDistance > 0f;
    }

    // ===== Режимы =====

    private boolean isPacketSprint() {
        return sprintMode.is("Packet");
    }

    private boolean isLegitSprint() {
        return sprintMode.is("Легитный");
    }

    // ===== Цели =====

    private Entity getCrossTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (entity instanceof LivingEntity le && le.isAlive() && !le.isRemoved()
                    && hasAccessTarget(entity)) {
                return entity;
            }
        }
        return null;
    }

    private boolean hasAccessTarget(Entity entity) {
        // Ботов (AntiBot) не бьём
        if (entity instanceof LivingEntity le && AntiBot.checkBot(le)) return false;
        if (attack.is("Игроки") && entity instanceof PlayerEntity player && player != mc.player) {
            // Друзей не бьём
            if (Wonderful.INSTANCE.friendStorage != null
                    && Wonderful.INSTANCE.friendStorage.isFriend(player.getGameProfile().getName())) {
                return false;
            }
            return true;
        }
        if (attack.is("Мобы") && entity instanceof MobEntity) return true;
        if (attack.is("Животные") && entity instanceof AnimalEntity) return true;
        if (attack.is("Стойки") && entity instanceof ArmorStandEntity) return true;
        return false;
    }

    // ===== Главный тик =====

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        // Не работаем вдвоём с KillAura и не бьём из меню/чата
        if (KillAura.INSTANCE.isEnable() || mc.currentScreen != null) return;

        currentTick++;
        sprintControlTick();

        // Предикт: цель вот-вот под прицелом (луч чуть дальше удара) —
        // сбрасываем спринт заранее, чтобы удар не ждал ни одного тика
        if (!isLegitSprint()) {
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d lookVec = mc.player.getRotationVec(1.0f);
            double ray = reach.get() + 0.2;
            Vec3d endPos = eyePos.add(lookVec.x * ray, lookVec.y * ray, lookVec.z * ray);
            for (Entity entity : mc.world.getEntities()) {
                if (entity == mc.player || !(entity instanceof LivingEntity le)) continue;
                if (le.getBoundingBox().raycast(eyePos, endPos).isEmpty()) continue;
                if (!hasAccessTarget(entity)) continue;
                if (boxDistance(entity) <= reach.get() + 0.35) {
                    preCritical();
                }
                break;
            }
        }

        Entity target = getCrossTarget();
        if (target != null) {
            onAttackEntity(target);
        }
    }

    private void onAttackEntity(Entity entity) {
        // Дистанция удара
        if (boxDistance(entity) > reach.get()) return;
        // Не сбываем еду/лук/щит
        if (mc.player.isUsingItem()) return;

        boolean restricted = hasMovementRestrictions();
        boolean onGround = mc.player.isOnGround();
        float threshold = weaponThreshold();
        float cd = cooldownProgress();
        boolean cdReady = System.currentTimeMillis() - lastClickTime >= 230L && cd >= threshold;

        // Легитный: спринт не трогаем — пока спринтуешь, модуль ждёт
        if (isLegitSprint() && mc.player.isSprinting()) return;

        // ===== ВОЗДУХ =====
        if (!onGround) {
            if (isFallingCrit() && !restricted) {
                // Падение = крит-фаза: бьём по готовности кулдауна
                if (!cdReady) return;
                if (mc.player.isSprinting() && !isPacketSprint()) {
                    preCritical(); // падение началось со спринтом — сброс, удар следующим тиком
                    return;
                }
                attack(entity); // Packet: не останавливаясь; Обычный: спринт уже сброшен
                return;
            }
            if (mc.player.fallDistance <= 0f) {
                // Только оторвались от земли: крит на следующем тике — готовим спринт
                preCritical();
            }
            return; // взлёт/ограничения — ждём фазы падения
        }

        // ===== ЗЕМЛЯ =====
        boolean wantCrits = !critMode.is("Выкл") && !restricted;

        // Авто-прыжок: кулдаун почти готов — прыгаем, крит уйдёт в падении
        if (wantCrits && autoJump.isState()
                && cd >= threshold
                && !mc.options.sneakKey.isPressed()
                && currentTick - lastJumpTick >= 10
                && currentTick - lastAttackTick >= 4) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
            lastJumpTick = currentTick;
            return;
        }

        if (!cdReady) return;

        // Только криты: на земле не бьём вовсе — ждём прыжка
        if (wantCrits && critMode.is("Только криты")) return;

        // Обычный/Packet на земле: перед ударом сбрасываем спринт
        if (mc.player.isSprinting() && !isLegitSprint()) {
            preCritical();
            return;
        }

        attack(entity);
    }

    private void attack(Entity entity) {
        lastClickTime = System.currentTimeMillis();
        lastAttackTick = currentTick;

        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            restoreSprint();
        }
        lastClickTime = System.currentTimeMillis();
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
