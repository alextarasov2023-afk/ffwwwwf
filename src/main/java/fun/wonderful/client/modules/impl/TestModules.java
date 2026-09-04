package fun.wonderful.client.modules.impl;

import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.api.storages.implement.FreeLookStorage;
import fun.wonderful.api.storages.implement.RotationStorage;
import fun.wonderful.api.storages.implement.WatermarkStorage;
import fun.wonderful.api.utils.combat.IdealHitUtils;
import fun.wonderful.api.utils.rotate.GCDUtil;
import fun.wonderful.api.utils.rotate.Rotation;
import fun.wonderful.api.utils.rotate.RotationUtils;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BindSetting;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ListSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
import fun.wonderful.client.modules.settings.implement.TextSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public final class TestModules {

    private TestModules() {
    }

    public static class AimAssist extends Module {
        private LivingEntity aimTarget;
        private Vec3d aimPoint;
        private int aimPointTicks;
        private final Random rand = new Random();

        public AimAssist() {
            super("AimAssist", "Легитная плавная наводка: мягко доводит прицел до цели", ModuleCategory.COMBAT);
            addSettings(
                    new FloatSetting("Range", 4.5f, 3f, 6f, 0.1f),
                    new FloatSetting("FOV", 40f, 5f, 90f, 1f),
                    new FloatSetting("Speed", 30f, 5f, 100f, 1f),
                    new BooleanSetting("OnlyHits", true),
                    new BooleanSetting("ThroughWalls", false),
                    new ListSetting("Targets",
                            new BooleanSetting("Players", true),
                            new BooleanSetting("Mobs", true),
                            new BooleanSetting("Animals", false)),
                    new BindSetting("Bind", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN)
            );
        }

        @Override
        public void onDisable() {
            aimTarget = null;
            aimPoint = null;
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (mc.player == null || mc.world == null || !isEnable()) return;
            if (mc.currentScreen != null) return;      // в менюхах/сундуках камеру не трогаем
            if (mc.player.isUsingItem()) return;       // еда/щит — не дёргаем взгляд

            // Не мешаем KillAura: пока та плавно доводит тело — наводка отдыхает
            if (RotationStorage.instance != null && RotationStorage.instance.isRotating()) return;

            // Легит-режим: помогаем только когда игрок сам бьёт
            if (isOn("OnlyHits") && !mc.options.attackKey.isPressed()) return;

            float range = setting("Range").get();
            float fov = setting("FOV").get();
            ListSetting targets = list("Targets");

            LivingEntity best = null;
            float bestAngle = Float.MAX_VALUE;

            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (entity == mc.player || living.isDead() || entity instanceof ArmorStandEntity) continue;
                if (living.getHealth() <= 0f) continue;
                if (mc.player.distanceTo(entity) > range) continue;
                if (targets == null) continue;
                if (entity instanceof PlayerEntity && !targets.is("Players")) continue;
                if (entity instanceof HostileEntity && !targets.is("Mobs")) continue;
                if (entity instanceof AnimalEntity && !targets.is("Animals")) continue;
                if (!isOn("ThroughWalls") && !mc.player.canSee(entity)) continue;

                // Легит-фильтр: цель должна быть в конусе зрения (FOV)
                net.minecraft.util.math.Vec2f rot = RotationUtils.getRotations(
                        entity.getX(), entity.getEyeY(), entity.getZ());
                float dYaw = MathHelper.wrapDegrees(rot.x - mc.player.getYaw());
                float dPitch = rot.y - mc.player.getPitch();
                float angle = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
                if (angle > fov) continue;

                // Из всех кандидатов берём ближайшего к прицелу
                if (angle < bestAngle) {
                    bestAngle = angle;
                    best = living;
                }
            }

            aimTarget = best;
            if (best == null) return;

            aimAt(best);
        }

        private void aimAt(LivingEntity target) {
            // Цель — НЕ фиксированные глаза, а точка в пределах тела цели (чуть в
            // грудь/голову/ноги). Точка выбирается редко (при смене цели), а не каждый
            // тик — иначе камера/руки «дрожат», гоняясь за постоянно новым рандомом.
            if (aimPoint == null || aimTarget != target) {
                aimTarget = target;
                aimPoint = centerOf(target);
                aimPointTicks = 0;
            }

            // Каждые ~2 сек слегка смещаем точку внутри тела — прицел «живой»,
            // но без дрожи между сменами
            if (aimPointTicks++ >= 40) {
                aimPointTicks = 0;
                aimPoint = centerOf(target);
            }
            // Плавно догоняем текущую позицию тела (цель двигается) — без рандома
            aimPoint = aimPoint.lerp(centerOf(target), 0.2f);

            net.minecraft.util.math.Vec2f rot = RotationUtils.getRotations(aimPoint);

            float dYaw = MathHelper.wrapDegrees(rot.x - mc.player.getYaw());
            float dPitch = rot.y - mc.player.getPitch();

            // Скорость доворота: не больше X° за тик (Speed), плавно замедляемся у цели
            float speed = setting("Speed").get();
            float maxStep = Math.max(speed / 20f, 0.05f);

            float stepYaw = MathHelper.clamp(dYaw, -maxStep, maxStep);
            float stepPitch = MathHelper.clamp(dPitch, -maxStep, maxStep);

            mc.player.setYaw(mc.player.getYaw() + stepYaw);
            mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + stepPitch, -90f, 90f));
        }

        /** Стабильная точка тела: центр хитбокса, чуть выше пола — наводка не «прыгает». */
        private Vec3d centerOf(LivingEntity target) {
            Box box = target.getBoundingBox();
            double y = box.minY + Math.max(0.6, target.getHeight() * 0.55);
            return new Vec3d((box.minX + box.maxX) / 2.0, y, (box.minZ + box.maxZ) / 2.0);
        }

        private FloatSetting setting(String name) {
            return (FloatSetting) getSettings().stream()
                    .filter(s -> s.name().equals(name) && s instanceof FloatSetting)
                    .findFirst().orElse(null);
        }

        private boolean isOn(String name) {
            BooleanSetting b = (BooleanSetting) getSettings().stream()
                    .filter(s -> s.name().equals(name) && s instanceof BooleanSetting)
                    .findFirst().orElse(null);
            return b != null && b.isState();
        }

        private ListSetting list(String name) {
            return (ListSetting) getSettings().stream()
                    .filter(s -> s.name().equals(name) && s instanceof ListSetting)
                    .findFirst().orElse(null);
        }
    }

    public static class Velocity extends Module {
        public Velocity() {
            super("Velocity", "Тест отброса урона с настройками процентов", ModuleCategory.COMBAT);
            addSettings(
                    new ModeSetting("Mode", "Grim", "Grim", "Vulcan", "Spartan", "Cancel"),
                    new FloatSetting("Horizontal", 85f, 0f, 100f, 5f),
                    new FloatSetting("Vertical", 100f, 0f, 100f, 5f),
                    new BooleanSetting("OnlyWhileMove", true)
            );
        }
    }

    public static class Speed extends Module {
        public Speed() {
            super("Speed", "Тест ускорения передвижения", ModuleCategory.MOVEMENT);
            addSettings(
                    new ModeSetting("Type", "Strafe", "Vanilla", "Strafe", "Bhop"),
                    new FloatSetting("Speed", 1.6f, 1f, 3f, 0.05f),
                    new BooleanSetting("Boost", false),
                    new BindSetting("KeyBind", -1)
            );
        }
    }

    public static class Flight extends Module {
        public Flight() {
            super("Flight", "Тест полёта с анти-чит ветками", ModuleCategory.MOVEMENT);
            addSettings(
                    new ModeSetting("AntiCheat", "Vanilla", "Vanilla", "Grim"),
                    new FloatSetting("Speed", 1.2f, 0.5f, 5f, 0.1f),
                    new BooleanSetting("DamageFly", false)
            );
        }
    }

    public static class Sprint extends Module {
        // KillAura гасит спринт на время прыжка-удара (сброс спринта);
        // пока лок активен, Sprint не форсирует спринт обратно
        public static boolean clientLock;

        public Sprint() {
            super("Sprint", "Авто-спринт: включается сам (после смерти/включения), шаг недоступен", ModuleCategory.MOVEMENT);
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (mc.player == null || !isEnable()) return;
            if (clientLock) return;                  // спринтом управляет KillAura (сброс на прыжке)
            if (mc.player.isSprinting()) return;     // уже бежим
            if (mc.player.isUsingItem()) return;     // еда/щит в руках — ванилла всё равно собьёт
            if (!mc.options.forwardKey.isPressed()) return;
            // Игрок не может перейти на шаг: спринт принудительно включается
            // каждый тик; выключить можно только сам модуль
            mc.player.setSprinting(true);
        }
    }

    public static class NoFall extends Module {
        public NoFall() {
            super("NoFall", "Тест отмены урона от падения", ModuleCategory.PLAYER);
            addSettings(
                    new ModeSetting("Mode", "Packet", "Packet", "Ground"),
                    new FloatSetting("Distance", 3f, 1f, 10f, 0.5f),
                    new BooleanSetting("Cancel", true)
            );
        }
    }

    public static class ChestStealer extends Module {
        public ChestStealer() {
            super("ChestStealer", "Тест авто-лутания сундука с задержкой", ModuleCategory.PLAYER);
            addSettings(
                    new FloatSetting("Delay", 120f, 0f, 500f, 10f),
                    new BooleanSetting("IgnoreFood", true),
                    new BooleanSetting("IgnoreTrash", false)
            );
        }
    }

    public static class AutoClicker extends Module {
        public AutoClicker() {
            super("AutoClicker", "Тест кликера с джиттером", ModuleCategory.MISC);
            addSettings(
                    new FloatSetting("CPS", 12f, 1f, 20f, 0.5f),
                    new BooleanSetting("Jitter", true),
                    new BooleanSetting("OnlyWeapon", true),
                    new TextSetting("Message", "")
            );
        }
    }

    public static class AntiStaff extends Module {
        public AntiStaff() {
            super("AntiStaff", "Тест детекта персонала на анархии", ModuleCategory.MISC);
            addSettings(
                    new ListSetting("Checks",
                            new BooleanSetting("Vulcan", true),
                            new BooleanSetting("Grim", true),
                            new BooleanSetting("Intave", false)),
                    new BooleanSetting("Notify", true),
                    new TextSetting("Sound", "random.orb")
            );
        }
    }

    public static class NameProtect extends Module {
        public NameProtect() {
            super("NameProtect", "Тест скрытия ника в чате и табе", ModuleCategory.MISC);
            addSettings(
                    new TextSetting("Nick", "Player"),
                    new BooleanSetting("Streamer", false),
                    new BooleanSetting("Skin", true)
            );
        }
    }

    public static class Watermark extends Module {
        public Watermark() {
            super("Худ", "HUD модуль с настройками отображения", ModuleCategory.RENDER);
            addSettings(
                    new BooleanSetting("Watermark", true),
                    new BooleanSetting("FPS", true),
                    new BooleanSetting("Server", true),
                    new BooleanSetting("Coordinates", true),
                    new BooleanSetting("Ping", true),
                    new BooleanSetting("Time", true)
            );
        }

        @Override
        public void onEnable() {
            updateWatermarkStorage();
        }

        @Override
        public void onDisable() {
            // Optionally reset to default when disabled
        }

        private void updateWatermarkStorage() {
            var elementSetting = getSettings().stream()
                    .filter(s -> s.name().equals("Element"))
                    .findFirst();
            if (elementSetting.isPresent() && elementSetting.get() instanceof ModeSetting mode) {
                WatermarkStorage.INSTANCE.setSelectedElement(WatermarkStorage.HudElement.valueOf(mode.getCurrent()));
            }
        }

        private BooleanSetting bool(String name) {
            return (BooleanSetting) getSettings().stream()
                    .filter(s -> s.name().equals(name) && s instanceof BooleanSetting)
                    .findFirst().orElse(null);
        }
        
        public boolean showFps() { BooleanSetting s = bool("FPS"); return s != null && s.isState(); }
        public boolean showServer() { BooleanSetting s = bool("Server"); return s != null && s.isState(); }
        public boolean showCoords() { BooleanSetting s = bool("Coordinates"); return s != null && s.isState(); }
        public boolean showPing() { BooleanSetting s = bool("Ping"); return s != null && s.isState(); }
        public boolean showTime() { BooleanSetting s = bool("Time"); return s != null && s.isState(); }
    }

    public static class Chams extends Module {
        // Статический снимок настроек — читается из миксина рендера EntityRenderDispatcherMixin
        public static boolean enabled;
        public static boolean players = true;
        public static boolean mobs = true;
        public static boolean animals;
        public static int red = 0;
        public static int green = 255;
        public static int blue = 255;
        public static float opacity = 0.55f;
        public static float range = 64f;

        public Chams() {
            super("Chams", "ESP: подсветка тела целей в цвет", ModuleCategory.RENDER);
            addSettings(
                    new ModeSetting("Mode", "Color", "Color", "Rainbow"),
                    new FloatSetting("Red", 0f, 0f, 255f, 1f),
                    new FloatSetting("Green", 255f, 0f, 255f, 1f),
                    new FloatSetting("Blue", 255f, 0f, 255f, 1f),
                    new FloatSetting("Opacity", 0.55f, 0.05f, 1f, 0.05f),
                    new FloatSetting("Range", 64f, 4f, 128f, 1f),
                    new ListSetting("Targets",
                            new BooleanSetting("Players", true),
                            new BooleanSetting("Mobs", true),
                            new BooleanSetting("Animals", false)),
                    new BindSetting("Bind", -1)
            );
        }

        @Override
        public void onEnable() {
            enabled = true;
            sync();
        }

        @Override
        public void onDisable() {
            enabled = false;
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (!isEnable()) return;
            sync();
        }

        private void sync() {
            FloatSetting r = setting("Red");
            FloatSetting g = setting("Green");
            FloatSetting b = setting("Blue");
            FloatSetting o = setting("Opacity");
            FloatSetting rg = setting("Range");
            red = (int) (r != null ? r.get() : 0);
            green = (int) (g != null ? g.get() : 255);
            blue = (int) (b != null ? b.get() : 255);
            opacity = o != null ? o.get() : 0.55f;
            range = rg != null ? rg.get() : 64f;
            ListSetting t = list("Targets");
            players = t != null && t.is("Players");
            mobs = t != null && t.is("Mobs");
            animals = t != null && t.is("Animals");
        }

        /** Является ли сущность целью Chams (по настройкам модуля). */
        public static boolean isTarget(Entity entity) {
            if (!(entity instanceof LivingEntity le)) return false;
            if (entity == mc.player || entity instanceof ArmorStandEntity) return false;
            if (le.isDead() || le.isInvisible()) return false;
            if (mc.player == null || mc.player.squaredDistanceTo(entity) > range * range) return false;

            if (entity instanceof PlayerEntity) return players;
            if (entity instanceof HostileEntity) return mobs;
            if (entity instanceof AnimalEntity) return animals;
            return mobs;
        }

        private FloatSetting setting(String name) {
            return (FloatSetting) getSettings().stream()
                    .filter(s -> s.name().equals(name) && s instanceof FloatSetting)
                    .findFirst().orElse(null);
        }

        private ListSetting list(String name) {
            return (ListSetting) getSettings().stream()
                    .filter(s -> s.name().equals(name) && s instanceof ListSetting)
                    .findFirst().orElse(null);
        }
    }

    public static class TargetHud extends Module {
        public TargetHud() {
            super("Target HUD", "Панель цели: ник, здоровье и дистанция", ModuleCategory.RENDER);
        }
    }

    public static class TargetEsp extends Module {
        public TargetEsp() {
            super("TargetESP", "Метка над игроком, на которого наведён прицел", ModuleCategory.RENDER);
        }
    }
}
