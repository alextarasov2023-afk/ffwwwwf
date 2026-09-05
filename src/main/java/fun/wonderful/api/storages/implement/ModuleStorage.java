package fun.wonderful.api.storages.implement;

import lombok.Getter;
import lombok.Setter;
import fun.wonderful.api.QClient;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.client.modules.impl.TestModules;
import fun.wonderful.client.modules.impl.combat.AntiBot;
import fun.wonderful.client.modules.impl.combat.KillAura;
import fun.wonderful.client.modules.impl.combat.PacketCriticals;
import fun.wonderful.client.modules.impl.combat.TpsSync;
import fun.wonderful.client.modules.impl.combat.Triggerbot;
import fun.wonderful.client.modules.impl.combat.Velocity;
import fun.wonderful.client.modules.impl.misc.NoDelay;
import fun.wonderful.client.modules.impl.movement.GrimNoFall;
import fun.wonderful.client.modules.impl.movement.Sprint;
import fun.wonderful.client.modules.impl.player.AutoEat;
import fun.wonderful.client.modules.impl.player.FastPlace;
import fun.wonderful.client.modules.impl.render.FreeLook;
import fun.wonderful.client.modules.impl.render.Hud;

@Getter
@Setter
public class ModuleStorage implements QClient {

    public ModuleStorage() {
        this.initModules();
    }

    private void initModules() {
        var list = ModuleClass.INSTANCE.getObject();

        // ===== Combat =====
        list.add(KillAura.INSTANCE);
        list.add(Triggerbot.INSTANCE);
        list.add(AntiBot.INSTANCE);
        list.add(Velocity.INSTANCE);
        list.add(TpsSync.INSTANCE);
        list.add(PacketCriticals.INSTANCE);
        list.add(new TestModules.AimAssist());

        // ===== Movement =====
        list.add(Sprint.INSTANCE);
        list.add(GrimNoFall.INSTANCE);
        list.add(new TestModules.Speed());
        list.add(new TestModules.Flight());

        // ===== Player =====
        list.add(AutoEat.INSTANCE);
        list.add(FastPlace.INSTANCE);
        list.add(new TestModules.ChestStealer());

        // ===== Misc =====
        list.add(NoDelay.INSTANCE);
        list.add(new TestModules.AutoClicker());
        list.add(new TestModules.AntiStaff());
        list.add(new TestModules.NameProtect());

        // ===== Render =====
        list.add(Hud.INSTANCE);
        list.add(FreeLook.INSTANCE);

        ModuleClass.INSTANCE.initialize();
    }
}
