package dev.nweaver.happyghastmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.nweaver.happyghastmod.custom.CustomHarnessManager;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import dev.nweaver.happyghastmod.network.OpenHarnessCreatorPacket;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// команда для открытия gui создателя сбруи
public class HarnessCreatorCommand {
    private static final Logger LOGGER = LogManager.getLogger();
    // флаг для отслеживания, когда игрок использует команду
    private static boolean isCommandBeingExecuted = false;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("ghast")
                        .then(Commands.literal("createharness")
                                .requires(source -> {
                                    // требуется уровень разрешений 2 (операторы)
                                    if (!source.hasPermission(2)) {
                                        return false;
                                    }

                                    // проверка, является ли сервер однопользовательским
                                    if (source.getServer() != null && !CustomHarnessManager.isSinglePlayerServer(source.getServer())) {
                                        // отправляем сообщение только если команда активно выполняется игроком
                                        // это предотвратит отправку сообщений при смене измерений
                                        if (isCommandBeingExecuted && source.getEntity() instanceof ServerPlayer) {
                                            source.sendFailure(Component.literal(
                                                    "Custom harness creation is currently only available in single-player mode"
                                            ));
                                        }
                                        return false;
                                    }

                                    return true;
                                })
                                .executes(HarnessCreatorCommand::execute)
                        )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // устанавливаем флаг, что команда активно выполняется
        isCommandBeingExecuted = true;

        try {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("This command can only be used by players"));
                return 0;
            }

            // дополнительная проверка на одиночный режим для безопасности
            if (!CustomHarnessManager.isSinglePlayerServer(source.getServer())) {
                source.sendFailure(Component.literal(
                        "Custom harness creation is currently only available in single-player mode"
                ));
                return 0;
            }

            // создаем пакет с информацией об одиночном режиме
            boolean isSinglePlayer = source.getServer().isSingleplayer();
            OpenHarnessCreatorPacket packet = new OpenHarnessCreatorPacket(isSinglePlayer);

            // отправляем пакет для открытия gui создателя сбруи
            NetworkHandler.sendToPlayer(packet, player);

            LOGGER.debug("Sent open harness creator packet to player {}", player.getName().getString());

            return 1;
        } finally {
            // сбрасываем флаг после выполнения команды
            isCommandBeingExecuted = false;
        }
    }
}