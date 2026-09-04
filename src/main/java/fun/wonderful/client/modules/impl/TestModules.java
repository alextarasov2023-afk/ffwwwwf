package fun.wonderful.client.modules.impl;

import fun.wonderful.Wonderful;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventPacket;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.api.storages.implement.RotationStorage;
import fun.wonderful.api.storages.implement.WatermarkStorage;
import fun.wonderful.api.utils.chat.ChatUtils;
import fun.wonderful.api.utils.client.ClientSoundPlayer;
import fun.wonderful.api.utils.math.MathUtils;
import fun.wonderful.api.utils.notification.NotificationManager;
import fun.wonderful.api.utils.player.MoveUtils;
import fun.wonderful.api.utils.rotate.RotationUtils;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BindSetting;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;
import fun.wonderful.client.modules.settings.implement.FloatSetting;
import fun.wonderful.client.modules.settings.implement.ListSetting;
import fun.wonderful.client.modules.settings.implement.ModeSetting;
import fun.wonderful.client.modules.settings.implement.TextSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.HashSet;
import java.util.Set;

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

    /**
     * Speed — ускорение передвижения.
     * Vanilla: постоянная скорость каждый тик.
     * Strafe: на земле держим скорость, в воздухе не теряем (фрикшен-компенсация).
     * Bhop: прыжок + стрейф (классический bhop).
     */
    public static class Speed extends Module {

        private final ModeSetting type = new ModeSetting("Тип", "Strafe", "Vanilla", "Strafe", "Bhop");
        private final FloatSetting speed = new FloatSetting("Скорость", 1.6f, 1f, 3f, 0.05f);
        private final BooleanSetting boost = new BooleanSetting("Буст спринта", false);

        public Speed() {
            super("Speed", "Ускорение передвижения: Vanilla / Strafe / Bhop", ModuleCategory.MOVEMENT);
            addSettings(type, speed, boost);
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (!isEnable() || mc.player == null) return;
            if (!MoveUtils.isMoving()) return;

            double base = speed.get();
            if (boost.isState() && mc.player.isSprinting()) {
                base *= 1.15;
            }

            switch (type.getCurrent()) {
                case "Vanilla" -> MoveUtils.setMotion(base);
                case "Bhop" -> {
                    if (mc.player.isOnGround()) {
                        // Ванильный прыжок без разгона спринта — чистая вертикаль 0.42
                        mc.player.setVelocity(mc.player.getVelocity().x, 0.42, mc.player.getVelocity().z);
                    }
                    MoveUtils.strafe(base);
                }
                default -> {
                    // Strafe: в воздухе не разгоняемся, а сохраняем набранную
                    if (mc.player.isOnGround()) {
                        MoveUtils.strafe(base);
                    } else {
                        MoveUtils.strafe(Math.min(MoveUtils.getSpeed() * 1.005, base));
                    }
                }
            }
        }
    }

    /**
     * Flight — полёт.
     * Vanilla: прямое управление скоростью (быстро, но палится на большинстве серверов).
     * Grim: плавный разгон с лимитами скорости + анти-кик «провалом» раз в 4 секунды.
     * DamageFly: полученный урон подбрасывает и поддерживает высоту.
     */
    public static class Flight extends Module {

        private final ModeSetting antiCheat = new ModeSetting("Античит", "Vanilla", "Vanilla", "Grim");
        private final FloatSetting speed = new FloatSetting("Скорость", 1.2f, 0.1f, 5.0f, 0.05f);
        private final BooleanSetting damageFly = new BooleanSetting("ДамаджФлай", false);

        private int antiKickTicks;

        public Flight() {
            super("Flight", "Полёт: Vanilla — прямой, Grim — плавный с анти-киком", ModuleCategory.MOVEMENT);
            addSettings(antiCheat, speed, damageFly);
        }

        @Override
        public void onDisable() {
            antiKickTicks = 0;
            if (mc.player != null) {
                mc.player.setVelocity(Vec3d.ZERO);
            }
            super.onDisable();
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (mc.player == null) return;

            double spd = speed.get();
            float yaw = (float) Math.toRadians(mc.player.getYaw());

            double forward = 0;
            double strafe = 0;
            if (mc.options.forwardKey.isPressed()) forward++;
            if (mc.options.backKey.isPressed()) forward--;
            if (mc.options.leftKey.isPressed()) strafe++;
            if (mc.options.rightKey.isPressed()) strafe--;

            double motionX = 0;
            double motionY = 0;
            double motionZ = 0;

            if (forward != 0 || strafe != 0) {
                double angle = Math.atan2(forward, strafe) - Math.PI / 2;
                motionX = -Math.sin(yaw + angle) * spd;
                motionZ = Math.cos(yaw + angle) * spd;
            }

            boolean verticalInput = false;
            if (mc.options.jumpKey.isPressed()) {
                motionY = spd;
                verticalInput = true;
            } else if (mc.options.sneakKey.isPressed()) {
                motionY = -spd;
                verticalInput = true;
            }

            // ДамаджФлай: во время урона урон «толкает» вверх и держит высоту
            if (damageFly.isState() && mc.player.hurtTime > 0 && motionY <= 0) {
                motionY = Math.min(spd, 0.42);
                verticalInput = true;
            }

            if (antiCheat.is("Grim")) {
                // Плавный разгон/торможение + лимиты, чтобы скорость была «человеческой»
                motionX = MathHelper.clamp(motionX, -0.45, 0.45);
                motionZ = MathHelper.clamp(motionZ, -0.45, 0.45);
                motionY = MathHelper.clamp(motionY, -0.35, 0.35);

                Vec3d cur = mc.player.getVelocity();
                motionX = cur.x + (motionX - cur.x) * 0.25;
                motionY = cur.y + (motionY - cur.y) * 0.25;
                motionZ = cur.z + (motionZ - cur.z) * 0.25;

                // Ховер: без вертикального ввода зависаем, раз в 4 сек слегка «проваливаемся»
                if (!verticalInput) {
                    antiKickTicks++;
                    if (antiKickTicks >= 80) {
                        motionY = -0.032;
                        antiKickTicks = 0;
                    } else {
                        motionY = 0;
                    }
                } else {
                    antiKickTicks = 0;
                }
            }

            mc.player.setVelocity(new Vec3d(motionX, motionY, motionZ));
            mc.player.fallDistance = 0f;
        }
    }

    /**
     * ChestStealer — автоматический вынос сундуков, шалкеров и диспенсеров.
     * Работает через QUICK_MOVE (shift-клик) с задержкой между слотами.
     */
    public static class ChestStealer extends Module {

        private final FloatSetting delay = new FloatSetting("Задержка", 120f, 0f, 500f, 10f);
        private final BooleanSetting ignoreFood = new BooleanSetting("Игнорировать еду", false);
        private final BooleanSetting ignoreTrash = new BooleanSetting("Игнорировать мусор", false);
        private final BooleanSetting autoClose = new BooleanSetting("Закрывать", true);

        private long lastClickAt;

        public ChestStealer() {
            super("ChestStealer", "Автоматически выносит содержимое сундуков и шалкеров", ModuleCategory.PLAYER);
            addSettings(delay, ignoreFood, ignoreTrash, autoClose);
        }

        @Override
        public void onDisable() {
            lastClickAt = 0L;
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (!isEnable() || mc.player == null || mc.interactionManager == null) return;
            if (!(mc.currentScreen instanceof GenericContainerScreen)) return;

            ScreenHandler handler = mc.player.currentScreenHandler;
            if (handler == null) return;

            long now = System.currentTimeMillis();
            if (now - lastClickAt < (long) delay.get()) return;

            // Слоты контейнера = все слоты минус 36 слотов инвентаря игрока (27 + 9 хотбар)
            int containerSlots = handler.slots.size() - 36;
            boolean nothingToSteal = true;

            for (int i = 0; i < containerSlots; i++) {
                Slot slot = handler.slots.get(i);
                if (!slot.hasStack()) continue;
                ItemStack stack = slot.getStack();

                if (ignoreFood.isState() && isFood(stack)) continue;
                if (ignoreTrash.isState() && isTrash(stack)) continue;

                nothingToSteal = false;
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                lastClickAt = now;
                return; // один слот за интервал — как человек
            }

            if (autoClose.isState() && nothingToSteal) {
                mc.setScreen(null);
                lastClickAt = now;
            }
        }

        private boolean isFood(ItemStack stack) {
            return stack.getItem().getComponents().contains(DataComponentTypes.FOOD)
                    || stack.getItem().getComponents().contains(DataComponentTypes.CONSUMABLE);
        }

        /** Мусор — всё, что не оружие / инструмент / броня. */
        private boolean isTrash(ItemStack stack) {
            return !(stack.getItem() instanceof SwordItem
                    || stack.getItem() instanceof AxeItem
                    || stack.getItem() instanceof PickaxeItem
                    || stack.getItem() instanceof ShovelItem
                    || stack.getItem() instanceof HoeItem
                    || stack.getItem() instanceof ArmorItem
                    || stack.getItem() instanceof MaceItem);
        }
    }

    /**
     * AutoClicker — кликер при зажатой ЛКМ с человеческим джиттером интервала.
     */
    public static class AutoClicker extends Module {

        private final FloatSetting cps = new FloatSetting("CPS", 12f, 1f, 20f, 0.5f);
        private final BooleanSetting jitter = new BooleanSetting("Джиттер", true);
        private final BooleanSetting onlyWeapon = new BooleanSetting("Только оружие", true);

        private long nextClickAt;

        public AutoClicker() {
            super("AutoClicker", "Кликер с джиттером при зажатой ЛКМ", ModuleCategory.MISC);
            addSettings(cps, jitter, onlyWeapon);
        }

        @Override
        public void onDisable() {
            nextClickAt = 0L;
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (!isEnable() || mc.player == null || mc.currentScreen != null) return;
            if (!mc.options.attackKey.isPressed() || mc.player.isUsingItem()) return;
            if (onlyWeapon.isState() && !isWeapon(mc.player.getMainHandStack())) return;

            long now = System.currentTimeMillis();
            if (now < nextClickAt) return;

            click();

            double interval = 1000.0 / Math.max(1f, cps.get());
            if (jitter.isState()) {
                interval += MathUtils.randomBest(-interval * 0.15, interval * 0.15);
            }
            nextClickAt = now + Math.max(15L, (long) interval);
        }

        private void click() {
            if (mc.interactionManager == null) return;
            if (mc.crosshairTarget instanceof EntityHitResult hit) {
                mc.interactionManager.attackEntity(mc.player, hit.getEntity());
            }
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        private boolean isWeapon(ItemStack stack) {
            return stack.getItem() instanceof SwordItem
                    || stack.getItem() instanceof AxeItem
                    || stack.getItem() instanceof MaceItem;
        }
    }

    /**
     * AntiStaff — оповещение о персонале (список .staff):
     * чат + уведомление + звук при входе стаффа, проверка уже онлайн при подключении.
     */
    public static class AntiStaff extends Module {

        private final BooleanSetting notify = new BooleanSetting("Уведомление", true);
        private final BooleanSetting sound = new BooleanSetting("Звук", true);
        private final BooleanSetting onWorldJoin = new BooleanSetting("При подключении", true);

        private final Set<String> knownPlayers = new HashSet<>();
        private int refreshTicks;

        public AntiStaff() {
            super("AntiStaff", "Оповещает о входе персонала (список .staff)", ModuleCategory.MISC);
            addSettings(notify, sound, onWorldJoin);
        }

        @Override
        public void onDisable() {
            knownPlayers.clear();
            refreshTicks = 0;
        }

        @EventLink
        public void onUpdate(EventUpdate event) {
            if (!isEnable() || mc.player == null || mc.getNetworkHandler() == null) {
                knownPlayers.clear();
                return;
            }

            // Список игроков обновляем раз в 10 тиков (~2 раза в секунду)
            if (++refreshTicks % 10 != 0) return;

            Set<String> current = new HashSet<>();
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                current.add(entry.getProfile().getName());
            }

            if (knownPlayers.isEmpty()) {
                // Первый скан (вход в мир / включение модуля): стафф уже онлайн?
                if (onWorldJoin.isState() && !current.isEmpty()) {
                    for (String name : current) {
                        if (isStaff(name)) {
                            alert(name);
                        }
                    }
                }
            } else {
                for (String name : current) {
                    if (!knownPlayers.contains(name) && isStaff(name)) {
                        alert(name);
                    }
                }
            }

            knownPlayers.clear();
            knownPlayers.addAll(current);
        }

        private boolean isStaff(String name) {
            if (Wonderful.INSTANCE == null || Wonderful.INSTANCE.staffStorage == null) return false;
            for (String staff : Wonderful.INSTANCE.staffStorage.getStaffs()) {
                if (staff.equalsIgnoreCase(name)) return true;
            }
            return false;
        }

        private void alert(String name) {
            if (notify.isState()) {
                ChatUtils.sendMessage("⚠ Персонал в сети: " + name);
                NotificationManager.pushCustom("Персонал: " + name, "staff");
            }
            if (sound.isState()) {
                ClientSoundPlayer.playSound("Второй.wav", 0.8, 1.0f);
            }
        }
    }

    /**
     * NameProtect — скрывает ваш ник во входящих сообщениях чата.
     * Сообщение с ником отменяется и добавляется замаскированная копия
     * (структура и цвета оригинала сохраняются). «Стример» — маскирует ник
     * в любом регистре.
     */
    public static class NameProtect extends Module {

        private final TextSetting nick = new TextSetting("Замена", "Вы");
        private final BooleanSetting streamer = new BooleanSetting("Стример", false);
        private final BooleanSetting skin = new BooleanSetting("Скин", false);

        public NameProtect() {
            super("NameProtect", "Скрывает ваш ник в чате (стрим-режим)", ModuleCategory.MISC);
            addSettings(nick, streamer, skin);
        }

        @EventLink
        public void onPacket(EventPacket event) {
            if (!isEnable() || mc.player == null) return;
            if (event.getType() != EventPacket.Type.RECEIVE) return;
            if (!(event.getPacket() instanceof GameMessageS2CPacket message)) return;

            String myNick = mc.player.getName().getString();
            Text content = message.getContent();
            if (!content.getString().contains(myNick)) return;

            // Отменяем ванильный показ и добавляем замаскированную копию.
            // Пакеты приходят на Netty-потоке — добавление в чат уводим на основной
            event.setCancelled(true);
            String replacement = nick.get().isEmpty() ? "Вы" : nick.get();
            MutableText masked = mask(content, myNick, replacement, streamer.isState());
            mc.execute(() -> mc.inGameHud.getChatHud().addMessage(masked));
        }

        /** Рекурсивная маска: узел -> замаскированная своя строка + дети (стили сохраняются). */
        private MutableText mask(Text node, String nickToHide, String replacement, boolean anyCase) {
            MutableText out = Text.literal(replace(ownString(node), nickToHide, replacement, anyCase))
                    .setStyle(node.getStyle());
            for (Text child : node.getSiblings()) {
                out.append(mask(child, nickToHide, replacement, anyCase));
            }
            return out;
        }

        /** Собственная строка узла = полная строка минус конкатенация дочерних строк. */
        private String ownString(Text node) {
            String full = node.getString();
            StringBuilder siblings = new StringBuilder();
            for (Text child : node.getSiblings()) {
                siblings.append(child.getString());
            }
            String suffix = siblings.toString();
            if (!suffix.isEmpty() && full.endsWith(suffix)) {
                return full.substring(0, full.length() - suffix.length());
            }
            return suffix.isEmpty() ? full : "";
        }

        private String replace(String source, String nickToHide, String replacement, boolean anyCase) {
            if (source.isEmpty()) return source;
            if (anyCase) {
                return source.replaceAll("(?i)" + java.util.regex.Pattern.quote(nickToHide),
                        java.util.regex.Matcher.quoteReplacement(replacement));
            }
            return source.replace(nickToHide, replacement);
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
