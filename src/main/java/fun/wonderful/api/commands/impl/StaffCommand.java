package fun.wonderful.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

import fun.wonderful.Wonderful;
import fun.wonderful.api.commands.Command;
import fun.wonderful.api.utils.chat.ChatUtils;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class StaffCommand extends Command {

    public StaffCommand() {
        super("staff");
    }

    
    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .then(literal("add")
                        .then(arg("player", word())
                                .suggests((context, builder1) -> {
                                    for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                                        String name = entry.getProfile().getName();
                                        if (name.toLowerCase().startsWith(builder1.getRemaining().toLowerCase())) {
                                            builder1.suggest(name);
                                        }
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String player = context.getArgument("player", String.class);
                                    if (!Wonderful.INSTANCE.staffStorage.isStaff(player)) {
                                        Wonderful.INSTANCE.staffStorage.add(player);
                                        ChatUtils.sendMessage("Игрок " + player + " добавлен в список стаффов!");
                                    } else {
                                        ChatUtils.sendMessage("Игрок " + player + " уже в списке стаффов!");
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("remove")
                        .then(arg("player", word())
                                .suggests((context, builder1) -> {
                                    Wonderful.INSTANCE.staffStorage.getStaffs().stream()
                                            .sorted(String::compareTo)
                                            .filter(name -> name.startsWith(builder1.getRemaining()))
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String player = context.getArgument("player", String.class);
                                    if (Wonderful.INSTANCE.staffStorage.isStaff(player)) {
                                        Wonderful.INSTANCE.staffStorage.remove(player);
                                        ChatUtils.sendMessage("Игрок " + player + " удалён из списка стаффов!");
                                    } else {
                                        ChatUtils.sendMessage("Игрок " + player + " не найден в списке стаффов!");
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            StringBuilder builder1 = new StringBuilder();
                            if (Wonderful.INSTANCE.staffStorage.getStaffs().isEmpty()) {
                                ChatUtils.sendMessage("Список стаффов пуст!");
                            } else {
                                for (int i = 0; i < Wonderful.INSTANCE.staffStorage.getStaffs().size(); i++) {
                                    builder1.append(Wonderful.INSTANCE.staffStorage.getStaffs().get(i));
                                    if (i < Wonderful.INSTANCE.staffStorage.getStaffs().size() - 1) {
                                        builder1.append(", ");
                                    }
                                }
                                builder1.append(".");
                                ChatUtils.sendMessage("Стаффы: " + builder1);
                            }
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (!Wonderful.INSTANCE.staffStorage.isEmpty()) {
                                Wonderful.INSTANCE.staffStorage.clear();
                                ChatUtils.sendMessage("Список стаффов очищен!");
                            } else {
                                ChatUtils.sendMessage("Список стаффов пуст!");
                            }
                            return SINGLE_SUCCESS;
                        })
                );
    }
}