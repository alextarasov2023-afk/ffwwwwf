package fun.wonderful.mixin;

import fun.wonderful.api.events.implement.EventRotation;
import fun.wonderful.api.storages.implement.FreeLookStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {

    // Сразу после КАЖДОГО setRotation внутри update (их 4: основная, сон, не-living, флип F5).
    // INVOKE + Shift.AFTER — надёжная точка для void-метода (INVOKE_ASSIGN на void не матчится:
    // "Scanned 0 target(s)" — присваиваемого результата нет).
    // Позиция орбиты 3-го лица считается ПОСЛЕ этой точки — уже по свободным углам.
    @Inject(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void wonderful$onCameraRotation(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (focusedEntity == null || focusedEntity != MinecraftClient.getInstance().player) {
            return;
        }

        Camera self = (Camera) (Object) this;
        // Неактивный режим: FreeLookStorage синхронизирует свободные углы с камерой,
        // чтобы активация freecam не дёргала вид. Активный: подставит свободные углы.
        EventRotation event = new EventRotation(self.getYaw(), self.getPitch(), tickDelta);
        event.call();

        if (!FreeLookStorage.isActive()) {
            return; // freecam выключен — камера ведёт себя ванильно
        }

        float yaw = event.getYaw();
        float pitch = event.getPitch();

        // Фронтальный F5: ванилла разворачивает камеру (+180, -pitch) — сохраняем разворот.
        // Применяем всегда (без сравнения с камерой): ванильный флип после нашего
        // применения вернёт «нефлипнутые» углы, сравнение тут ложно срабатывает.
        if (inverseView) {
            yaw += 180f;
            pitch = -pitch;
        }

        ((ICameraAccessor) (Object) this).invokeSetRotation(yaw, pitch);
    }
}
