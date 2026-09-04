package fun.wonderful.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import org.lwjgl.glfw.GLFW;

import fun.wonderful.api.commands.Command;
import fun.wonderful.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import fun.wonderful.api.utils.chat.ChatUtils;
import fun.wonderful.api.utils.input.KeyBoardUtils;
import fun.wonderful.client.modules.Module;

import java.lang.reflect.Field;
import java.util.Optional;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class BindCommand extends Command {
    public BindCommand() {
        super("bind");
    }

    
    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        // .bind <module> <key> — быстрая привязка без "add"
        // .bind <module> — показать текущую привязку модуля
        builder.then(arg("module", word())
                .suggests((context, suggestionsBuilder) -> {
                    String remaining = suggestionsBuilder.getRemaining().toLowerCase();
                    ModuleClass.INSTANCE.getObject().stream()
                            .map(Module::getName)
                            .filter(name -> name.toLowerCase().startsWith(remaining))
                            .forEach(suggestionsBuilder::suggest);
                    return suggestionsBuilder.buildFuture();
                })
                .executes(ctx -> {
                    String moduleName = ctx.getArgument("module", String.class);
                    if (isReservedWord(moduleName)) return SINGLE_SUCCESS;
                    Optional<Module> optionalModule = findModuleByName(moduleName);
                    if (optionalModule.isEmpty()) {
                        ChatUtils.sendMessage("Модуль " + moduleName + " не найден");
                        return SINGLE_SUCCESS;
                    }

                    Module module = optionalModule.get();
                    if (module.getKey() == -1) {
                        ChatUtils.sendMessage("У модуля " + module.getName() + " нет привязки");
                    } else {
                        ChatUtils.sendMessage("Модуль " + module.getName()
                                + " привязан к " + KeyBoardUtils.getBindName(module.getKey()));
                    }
                    return SINGLE_SUCCESS;
                })
                .then(arg("key", word())
                        .suggests((context, suggestionsBuilder) -> {
                            String remaining = suggestionsBuilder.getRemaining().toUpperCase();
                            for (Field field : GLFW.class.getDeclaredFields()) {
                                String fieldName = field.getName();
                                if (!fieldName.startsWith("GLFW_KEY_")) {
                                    continue;
                                }

                                String keyName = fieldName.replace("GLFW_KEY_", "");
                                if (keyName.startsWith(remaining)) {
                                    suggestionsBuilder.suggest(keyName);
                                }
                            }
                            for (String mouse : MOUSE_KEYS) {
                                if (mouse.startsWith(remaining)) {
                                    suggestionsBuilder.suggest(mouse);
                                }
                            }

                            if ("NONE".startsWith(remaining)) {
                                suggestionsBuilder.suggest("NONE");
                            }
                            return suggestionsBuilder.buildFuture();
                        })
                        .executes(ctx -> {
                            String moduleName = ctx.getArgument("module", String.class);
                            Optional<Module> optionalModule = findModuleByName(moduleName);
                            if (optionalModule.isEmpty()) {
                                ChatUtils.sendMessage("Модуль " + moduleName + " не найден");
                                return SINGLE_SUCCESS;
                            }

                            Module module = optionalModule.get();
                            String keyName = ctx.getArgument("key", String.class).toUpperCase();
                            int keyCode = getKeyCode(keyName);

                            if (keyCode == -1 && !"NONE".equals(keyName)) {
                                ChatUtils.sendMessage("Клавиша " + keyName + " не найдена");
                                return SINGLE_SUCCESS;
                            }

                            module.setKey(keyCode);
                            if (keyCode == -1) {
                                ChatUtils.sendMessage("Привязка модуля " + module.getName() + " снята");
                            } else {
                                ChatUtils.sendMessage("Модуль " + module.getName()
                                        + " привязан к " + KeyBoardUtils.getBindName(keyCode));
                            }
                            return SINGLE_SUCCESS;
                        })));

        builder.then(literal("remove").then(arg("module", word()).executes(ctx -> {
            String moduleName = ctx.getArgument("module", String.class);
            Optional<Module> optionalModule = findModuleByName(moduleName);
            if (optionalModule.isEmpty()) {
                ChatUtils.sendMessage("Модуль " + moduleName + " не найден");
                return SINGLE_SUCCESS;
            }

            Module module = optionalModule.get();
            module.setKey(-1);
            ChatUtils.sendMessage("Привязка клавиши для модуля " + module.getName() + " удалена");
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("clear").executes(ctx -> {
            ModuleClass.INSTANCE.getObject().forEach(module -> module.setKey(-1));
            ChatUtils.sendMessage("Все привязки клавиш удалены");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("list").executes(ctx -> {
            StringBuilder bindingsList = new StringBuilder("Список привязанных модулей: ");
            boolean hasBinds = ModuleClass.INSTANCE.getObject().stream()
                    .filter(module -> module.getKey() != -1)
                    .peek(module -> bindingsList
                            .append(module.getName())
                            .append(" -> ")
                            .append(KeyBoardUtils.getBindName(module.getKey()))
                            .append(", "))
                    .findAny()
                    .isPresent();

            if (!hasBinds) {
                ChatUtils.sendMessage("Нет привязанных модулей");
            } else {
                bindingsList.setLength(bindingsList.length() - 2);
                ChatUtils.sendMessage(bindingsList.toString());
            }
            return SINGLE_SUCCESS;
        }));
    }

    private static final String[] MOUSE_KEYS = {"LMB", "RMB", "MMB", "MOUSE4", "MOUSE5"};

    private Optional<Module> findModuleByName(String moduleName) {
        return ModuleClass.INSTANCE.getObject().stream()
                .filter(module -> module.getName().equalsIgnoreCase(moduleName))
                .findFirst();
    }

    /** Слова, зарезервированные под под-команды — чтобы .bind list не искал модуль "list" */
    private boolean isReservedWord(String word) {
        return switch (word.toLowerCase()) {
            case "add", "remove", "clear", "list", "none" -> true;
            default -> false;
        };
    }

    private int getKeyCode(String keyName) {
        if ("NONE".equalsIgnoreCase(keyName)) {
            return -1;
        }

        // Мышь: коды клавиш >= MOUSE_BUTTON_OFFSET — так их видит KeyBoardUtils
        switch (keyName) {
            case "LMB", "MOUSE1" -> {
                return KeyBoardUtils.MOUSE_BUTTON_OFFSET; // кнопка 0
            }
            case "RMB", "MOUSE2" -> {
                return KeyBoardUtils.MOUSE_BUTTON_OFFSET + 1;
            }
            case "MMB", "SKM", "MOUSE3" -> {
                return KeyBoardUtils.MOUSE_BUTTON_OFFSET + 2;
            }
            case "MOUSE4" -> {
                return KeyBoardUtils.MOUSE_BUTTON_OFFSET + 3;
            }
            case "MOUSE5" -> {
                return KeyBoardUtils.MOUSE_BUTTON_OFFSET + 4;
            }
            default -> { }
        }

        try {
            return GLFW.class.getField("GLFW_KEY_" + keyName).getInt(null);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return -1;
        }
    }
}