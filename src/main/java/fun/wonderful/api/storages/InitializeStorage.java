package fun.wonderful.api.storages;


import fun.wonderful.Wonderful;
import fun.wonderful.api.QClient;
import fun.wonderful.api.events.EventInvoker;
import fun.wonderful.api.storages.implement.*;
import fun.wonderful.api.utils.notification.NotificationRenderer;
import fun.wonderful.api.utils.tps.TPSCalc;

public class InitializeStorage implements QClient {

    public void onInitialize() {
        EventInvoker.register(this);
        this.initStorages();
    }

    
    public void initStorages() {
        Wonderful.INSTANCE.moduleStorage = new ModuleStorage();
        Wonderful.INSTANCE.themeStorage = new ThemeStorage();
        Wonderful.INSTANCE.tpsCalc = new TPSCalc();
        EventInvoker.register(Wonderful.INSTANCE.tpsCalc);
        Wonderful.INSTANCE.localizationStorage = new LocalizationStorage();
        Wonderful.INSTANCE.freeLookStorage = new FreeLookStorage();
        Wonderful.INSTANCE.rotationStorage = new RotationStorage();
        Wonderful.INSTANCE.friendStorage = new FriendStorage();
        Wonderful.INSTANCE.macroStorage = new MacroStorage();
        Wonderful.INSTANCE.staffStorage = new StaffStorage();
        Wonderful.INSTANCE.waypointStorage = new WaypointStorage();
        Wonderful.INSTANCE.commandStorage = new CommandStorage();
        Wonderful.INSTANCE.configStorage = new ConfigStorage();

        // Рендер уведомлений (слушает EventRender.Default)
        new NotificationRenderer();

        // HUD: ватермарка, таргет-панель, кейбинды, клавиши, стафф
        new HudStorage();
    }
}
