package fun.wonderful.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fun.wonderful.Wonderful;
import fun.wonderful.api.utils.chat.ChatUtils;

/**
 * Перехват ввода в чате: если сообщение начинается с префикса команд (по умолчанию «.»),
 * оно не отправляется на сервер, а передаётся в {@code CommandStorage.getDispatcher()}.
 * Это позволяет вводить команды клиента прямо в чат, а не через макросы.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    /**
     * Inject в начало {@code ChatScreen.sendMessage(String, boolean)}.
     * Если текст начинается с префикса — выполняем как клиентскую команду
     * и отменяем отправку на сервер.
     */
    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void wonderful$interceptCommand(String chatText, boolean addToHistory, CallbackInfo ci) {
        String prefix = Wonderful.INSTANCE.commandStorage.getPrefix();
        if (prefix == null || prefix.isEmpty()) return;
        if (!chatText.startsWith(prefix)) return;

        // Отменяем отправку сообщения на сервер
        ci.cancel();

        // Обрезаем префикс и передаём в диспетчер
        String raw = chatText.substring(prefix.length());
        try {
            Wonderful.INSTANCE.commandStorage.getDispatcher().execute(
                    raw,
                    Wonderful.INSTANCE.commandStorage.getSource()
            );
        } catch (CommandSyntaxException e) {
            ChatUtils.sendMessage(Formatting.RED + "Ошибка: " + e.getMessage());
        }
    }
}
