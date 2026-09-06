package fun.wonderful.api.storages.implement;

import java.util.HashMap;
import java.util.Map;

public class LocalizationStorage {

    public enum Language {
        RUSSIAN("Русский"),
        ENGLISH("English"),
        UKRAINIAN("Українська");

        private final String displayName;

        Language(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Language next() {
            Language[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private final Map<String, String> en = new HashMap<>();
    private final Map<String, String> uk = new HashMap<>();
    private Language language = Language.RUSSIAN;

    public LocalizationStorage() {
        add("Combat", "Combat", "Бій");
        add("Movement", "Movement", "Рух");
        add("Render", "Render", "Візуал");
        add("Misc", "Misc", "Різне");
        add("Player", "Player", "Гравець");
        add("Язык", "Language", "Мова");
        add("Худ", "HUD", "Худ");
        add("Shader Sky", "Shader Sky", "Шейдер-небо");
        add("Яркость", "Brightness", "Яскравість");
        add("Ватермарка", "Watermark", "Ватермарка");
        add("Кейбинды", "Keybinds", "Хоткеї");
        add("Клавиши", "Keystrokes", "Клавіші");
        add("Пинг", "Ping", "Пінг");
        add("Русский", "Russian", "Російська");
        add("Английский", "English", "Англійська");
        add("Украинский", "Ukrainian", "Українська");
        add("Sprint", "Sprint", "Спринт");
        add("AttackAura", "Attack Aura", "Атака Аура");
        add("Автоматически атакует ближайшую цель", "Automatically attacks the nearest target", "Автоматично атакує найближчу ціль");
        add("Дистанция", "Range", "Дистанція");
        add("Скорость поворота", "Rotation Speed", "Швидкість повороту");
        add("Коррекция движения", "Motion Correction", "Корекція руху");
        add("Обычная", "Regular", "Звичайна");
        add("Свободная", "Free", "Вільна");
        add("Ротация", "Rotations", "Ротація");
        add("ИИ", "AI", "ШІ");
        add("Крит", "Crit", "Кріт");
        add("Only Crit", "Only Crit", "Тільки кріт");
        add("Smart Crit", "Smart Crit", "Розумний кріт");
        add("AutoTotem", "Auto Totem", "Авто тотем");
        add("Interface", "Interface", "Інтерфейс");
        add("InventoryWalk", "Inventory Walk", "Хода з інвентарем");
        add("NoVignette", "No Vignette", "Без віньєтки");
        add("ElytraBoost", "Elytra Boost", "Елітра буст");
        add("ElytraTarget", "Elytra Target", "Таргет елітри");
        add("FullBright", "Full Bright", "Повна яскравість");
        add("ElytraSwap", "Elytra Swap", "Свап елітр");
        add("PlayerFakeLags", "Fake Lag", "Фейк лаг");
        add("ClientSounds", "Client Sounds", "Звуки клієнта");
        add("Cosmetics", "Cosmetics", "Косметика");
        add("ServerHelper", "Server Helper", "Сервер Хелпер");
        add("KTLeave", "KT Leave", "КТ лів");
        add("NoClip", "No Clip", "Без кліпу");
        add("Particles", "Particles", "Частинки");
        add("ElytraMotion", "Elytra Motion", "Рух елітри");
        add("HitBubbles", "Hit Bubbles", "Бульбашки удару");
        add("RPSpoofer", "RP Spoofer", "RP спуфер");
        add("Projectile", "Projectile", "Снаряд");
        add("AutoExplosion", "Auto Explosion", "Авто вибух");
        add("PacketCriticals", "Packet Criticals", "Пакетні кріти");
        add("EntityESP", "Entity ESP", "ESP сутностей");
        add("ItemESP", "Item ESP", "ESP предметів");
        add("Tracer", "Tracer", "Трейсер");
        add("Velocity", "Velocity", "Велосіті");
        add("AutoEat", "Auto Eat", "Авто їжа");
        add("FastPlace", "Fast Place", "Быстре розміщення");
        add("Sprint", "Sprint", "Спринт");
        add("NoPush", "No Push", "Без штовхання");
        add("TPLoot", "TP Loot", "ТП лут");
        add("CutDetector", "Cut Detector", "Детектор кату");
        add("AirStuck", "Air Stuck", "Зависання в повітрі");
        add("Sonar", "Sonar", "Сонар");
        add("NoWeb", "No Web", "Без павутини");
        add("BlockESP", "Block ESP", "ESP блоків");
        add("ChestESP", "Chest ESP", "ESP сундуків");
        add("BlockOutline", "Block Outline", "Контур блоків");
        add("NoRender", "No Render", "Без рендеру");
        add("Zoom", "Zoom", "Зум");
        add("Freelook", "Freelook", "Вільний огляд");
        add("CrystalESP", "Crystal ESP", "ESP кристалів");
        add("Cubes", "Cubes", "Куби");
        add("Removals", "Removals", "Видалення");
        add("ViewModel", "View Model", "Модель рук");
        add("JumpCircle", "Jump Circle", "Коло стрибка");
        add("CustomWorld", "Custom World", "Світ");
        add("InterpolateF5", "Interpolate F5", "Плавний F5");
        add("BlockOverlay", "Block Overlay", "Оверлей блоку");
        add("ShaderHands", "Shader Hands", "Шейдер рук");
        add("Режим", "Mode", "Режим");
        add("Мод", "Mode", "Режим");
        add("Стиль", "Style", "Стиль");
        add("Скорость", "Speed", "Швидкість");
        add("Скорость анимации", "Animation Speed", "Швидкість анімації");
        add("Скорость вращения", "Rotation Speed", "Швидкість обертання");
        add("Скорость волн", "Wave Speed", "Швидкість хвиль");
        add("Скорость нитей", "Thread Speed", "Швидкість ниток");
        add("Дистанция", "Distance", "Дистанція");
        add("Размер", "Size", "Розмір");
        add("Прозрачность", "Opacity", "Прозорість");
        add("Свечение", "Glow", "Світіння");
        add("Сила свечения", "Glow Strength", "Сила світіння");
        add("Сила анимации", "Animation Strength", "Сила анімації");
        add("Плавность", "Smoothness", "Плавність");
        add("Анимация", "Animation", "Анімація");
        add("Анимация крыльев", "Wing Animation", "Анімація крил");
        add("Плавная анимация", "Smooth Animation", "Плавна анімація");
        add("Тип частиц", "Particle Type", "Тип частинок");
        add("Количество", "Count", "Кількість");
        add("Приоритет", "Priority", "Пріоритет");
        add("Ротация", "Rotation", "Ротація");
        add("Обход", "Bypass", "Обхід");
        add("Сервер", "Server", "Сервер");
        add("После лута", "After Loot", "Після луту");
        add("Элементы", "Elements", "Елементи");
        add("Горячие клавиши", "Key Binds", "Гарячі клавіші");
        add("Зелья", "Potions", "Зілля");
        add("Таргет худ", "Target HUD", "Таргет HUD");
        add("Уведомления", "Notifications", "Сповіщення");
        add("Стафф", "Staff", "Стаф");
        add("Сессия", "Session", "Сесія");
        add("КейСтроки", "Key Strokes", "Кейстроки");
        add("Информация", "Information", "Інформація");
        add("Обычный", "Default", "Звичайний");
        add("Красивый", "Fancy", "Гарний");
        add("Шейдер", "Shader", "Шейдер");
        add("Нитки", "Threads", "Нитки");
        add("Разлет", "Scatter", "Розліт");
        add("Падение", "Fall", "Падіння");
        add("Возвращаться", "Return", "Повертатися");
        add("Тепаться на спавн", "Teleport to Spawn", "Телепортуватись на спавн");
        add("Картинка 1", "Image 1", "Картинка 1");
        add("Картинка 2", "Image 2", "Картинка 2");
        add("Призраки", "Ghosts", "Привиди");
        add("Райдер", "Rider", "Райдер");
        add("Души", "Souls", "Души");
        add("Кристаллы", "Crystals", "Кристали");
        add("Коллизия", "Collision", "Колізія");
        add("Тест", "Test", "Тест");
        add("Правая рука X", "Right Hand X", "Права рука X");
        add("Правая рука Y", "Right Hand Y", "Права рука Y");
        add("Правая рука Z", "Right Hand Z", "Права рука Z");
        add("Левая рука X", "Left Hand X", "Ліва рука X");
        add("Левая рука Y", "Left Hand Y", "Ліва рука Y");
        add("Левая рука Z", "Left Hand Z", "Ліва рука Z");
        add("Авто-взлёт", "Auto Takeoff", "Авто зліт");
        add("Обходить Grim", "Bypass Grim", "Обходити Grim");
        add("Крылья", "Wings", "Крила");
        add("Крылья 2", "Wings 2", "Крила 2");
        add("Китайская шляпа", "China Hat", "Китайський капелюх");
    }

    private void add(String key, String english, String ukrainian) {
        en.put(key, english);
        uk.put(key, ukrainian);
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language == null ? Language.RUSSIAN : language;
    }

    public void cycleLanguage() {
        language = language.next();
    }

    public String translate(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        return switch (language) {
            case RUSSIAN -> key;
            case ENGLISH -> en.getOrDefault(key, fallbackEnglish(key));
            case UKRAINIAN -> uk.getOrDefault(key, fallbackUkrainian(key));
        };
    }

    private String fallbackEnglish(String key) {
        if (key.chars().allMatch(ch -> ch < 128)) {
            return humanizeAscii(key);
        }
        return key;
    }

    private String fallbackUkrainian(String key) {
        if (uk.containsKey(key)) {
            return uk.get(key);
        }
        if (key.chars().allMatch(ch -> ch < 128)) {
            return humanizeAscii(key);
        }
        return key;
    }

    private String humanizeAscii(String key) {
        if (key.indexOf(' ') >= 0) {
            return key;
        }

        String humanized = key.replaceAll("([a-z])([A-Z])", "$1 $2");
        humanized = humanized.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        return humanized.trim();
    }
}
