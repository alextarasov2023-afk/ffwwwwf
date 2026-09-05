package fun.wonderful.client.modules.impl.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import fun.wonderful.api.events.EventLink;
import fun.wonderful.api.events.implement.EventUpdate;
import fun.wonderful.client.modules.Module;
import fun.wonderful.client.modules.settings.implement.BooleanSetting;

/**
 * AntiBot — определение NPC/ботов на сервере.
 * Проверки: отсутствие в таблисте (самая надёжная — настоящие игроки
 * всегда в списке), пинг 0 у записей таблиста и броня-эвристика Matrix
 * (полный комплект одинаковой незачарованной кожи/железа без урона,
 * пустая левая рука + еда 20).
 */
public class AntiBot extends Module {

    public static AntiBot INSTANCE = new AntiBot();

    public static final List<Entity> isBot = new ArrayList<>();

    private final BooleanSetting tabCheck = new BooleanSetting("Не в таблисте", true);
    private final BooleanSetting pingCheck = new BooleanSetting("Пинг 0", false);
    private final BooleanSetting armorCheck = new BooleanSetting("Броня (Matrix)", true);

    public AntiBot() {
        super("AntiBot", "Определяет ботов на сервере", ModuleCategory.COMBAT);
        addSettings(tabCheck, pingCheck, armorCheck);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isEnable()) return;
        if (mc.world == null) return;

        // Таблиста/пинг имеют смысл только на сервере (в одиночке пинга нет)
        boolean multiplayer = mc.getCurrentServerEntry() != null && mc.getNetworkHandler() != null;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (mc.player == player) continue;

            boolean bot = false;

            if (armorCheck.isState() && isMatrixArmor(player)) {
                bot = true;
            }

            if (!bot && multiplayer && (tabCheck.isState() || pingCheck.isState())) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
                if (tabCheck.isState() && entry == null) {
                    bot = true; // настоящий игрок всегда есть в таблисте
                } else if (pingCheck.isState() && entry != null && entry.getLatency() <= 0) {
                    bot = true; // у живых игроков пинг > 0
                }
            }

            if (bot) {
                if (!isBot.contains(player)) isBot.add(player);
            } else {
                isBot.remove(player);
            }
        }
    }

    /** Эвристика Matrix-ботов: полный сет одинаковой брони без урона и чар. */
    private boolean isMatrixArmor(PlayerEntity player) {
        var armor = player.getInventory().armor;
        return armor.get(0).getItem() != Items.AIR
                && armor.get(1).getItem() != Items.AIR
                && armor.get(2).getItem() != Items.AIR
                && armor.get(3).getItem() != Items.AIR
                && !armor.get(0).isDamaged() && !armor.get(1).isDamaged()
                && !armor.get(2).isDamaged() && !armor.get(3).isDamaged()
                && player.getOffHandStack().getItem() == Items.AIR
                && player.getMainHandStack().getItem() != Items.AIR
                && player.getHungerManager().getFoodLevel() == 20
                && (armor.get(0).getItem() == Items.LEATHER_BOOTS
                || armor.get(1).getItem() == Items.LEATHER_LEGGINGS
                || armor.get(2).getItem() == Items.LEATHER_CHESTPLATE
                || armor.get(3).getItem() == Items.LEATHER_HELMET
                || armor.get(0).getItem() == Items.IRON_BOOTS
                || armor.get(1).getItem() == Items.IRON_LEGGINGS
                || armor.get(2).getItem() == Items.IRON_CHESTPLATE
                || armor.get(3).getItem() == Items.IRON_HELMET);
    }

    public static boolean checkBot(LivingEntity entity) {
        return entity instanceof PlayerEntity && isBot.contains(entity);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isBot.clear();
    }
}
