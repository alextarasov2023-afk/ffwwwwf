package fun.wonderful.api.storages.implement;

import lombok.Getter;
import lombok.Setter;
import fun.wonderful.api.QClient;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.client.modules.impl.combat.AntiBot;
import fun.wonderful.client.modules.impl.combat.PacketCriticals;
import fun.wonderful.client.modules.impl.combat.TpsSync;
import fun.wonderful.client.modules.impl.combat.Velocity;
import fun.wonderful.client.modules.impl.combat.Triggerbot;
import fun.wonderful.client.modules.impl.combat.KillAura;
import fun.wonderful.client.modules.impl.movement.Sprint;
import fun.wonderful.client.modules.impl.movement.Sprint;
import fun.wonderful.client.modules.impl.movement.NoSlow;
import fun.wonderful.client.modules.impl.movement.GrimNoFall;
import fun.wonderful.client.modules.impl.misc.NoDelay;
import fun.wonderful.client.modules.impl.player.AutoEat;
import fun.wonderful.client.modules.impl.player.FastPlace;
import fun.wonderful.client.modules.impl.render.Nametags;
import fun.wonderful.client.modules.impl.render.FreeLook;
import fun.wonderful.client.modules.impl.render.SwingAnimations;
import fun.wonderful.client.modules.impl.TestModules;

@Getter
@Setter
public class ModuleStorage implements QClient {

    public ModuleStorage() {
        this.initModules();
        this.initWatermark();
    }

    private void initModules() {
        var list = ModuleClass.INSTANCE.getObject();        list.add(Triggerbot.INSTANCE);
        list.add(KillAura.INSTANCE);
        list.add(AntiBot.INSTANCE);
        list.add(TpsSync.INSTANCE);
        list.add(PacketCriticals.INSTANCE);

        list.add(new TestModules.AimAssist());
        list.add(Velocity.INSTANCE);
        list.add(new TestModules.Speed());
        list.add(Sprint.INSTANCE);
        list.add(new TestModules.Flight());
        list.add(GrimNoFall.INSTANCE);
        list.add(new TestModules.ChestStealer());
        list.add(new TestModules.AutoClicker());
        list.add(new TestModules.AntiStaff());
        list.add(new TestModules.NameProtect());
        list.add(NoSlow.INSTANCE);
        list.add(NoDelay.INSTANCE);
        list.add(AutoEat.INSTANCE);
        list.add(FastPlace.INSTANCE);

        list.add(Nametags.INSTANCE);
        list.add(FreeLook.INSTANCE);
        list.add(SwingAnimations.INSTANCE);
        list.add(new TestModules.TargetEsp());
        list.add(new TestModules.Watermark());

        ModuleClass.INSTANCE.initialize();
    }

    private void initWatermark() {
        new WatermarkStorage();
        new TargetHudStorage();
        new TargetEspStorage();
    }
}
