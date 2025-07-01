package dev.nweaver.happyghastmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.entity.components.GhastMovementComponent;
import dev.nweaver.happyghastmod.network.GhastSpeedSyncPacket;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GhastSpeedCommand {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final float DEFAULT_SPEED = 1.0f;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("ghastspeed")
                        .requires(source -> source.hasPermission(2)) // требуется уровень оператора (2)
                        .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.1f, 10.0f)) // ограничиваем множитель от 0.1 до 10.0
                                .executes(context -> setGhastSpeed(context, FloatArgumentType.getFloat(context, "multiplier"))))
                        .then(Commands.literal("reset")
                                .executes(context -> setGhastSpeed(context, DEFAULT_SPEED)))
                        .executes(context -> {
                            // если аргументы не указаны, выводим текущую скорость
                            float currentMultiplier = GhastMovementComponent.SPEED_MULTIPLIER;
                            // формируем строку, чтобы она была effectively final
                            final String statusText = formatSpeedStatus(currentMultiplier);

                            context.getSource().sendSuccess(() -> Component.literal(statusText), false);
                            return 1;
                        })
        );

        LOGGER.info("GhastSpeedCommand registered");
    }

    // вспомогательный метод для форматирования сообщения
    private static String formatSpeedStatus(float multiplier) {
        StringBuilder status = new StringBuilder();
        status.append(String.format("Current Happy Ghast speed multiplier: %.1f", multiplier));

        if (multiplier == DEFAULT_SPEED) {
            status.append(" (standard speed)");
        } else if (multiplier < DEFAULT_SPEED) {
            status.append(String.format(" (%.1fx slower than standard)", DEFAULT_SPEED / multiplier));
        } else {
            status.append(String.format(" (%.1fx faster than standard)", multiplier / DEFAULT_SPEED));
        }

        return status.toString();
    }

    private static int setGhastSpeed(CommandContext<CommandSourceStack> context, float multiplier) {
        // запоминаем старый множитель
        float oldMultiplier = GhastMovementComponent.SPEED_MULTIPLIER;

        // устанавливаем новый множитель для обратной совместимости
        GhastMovementComponent.SPEED_MULTIPLIER = multiplier;

        // обновляем множитель для всех существующих гастов
        CommandSourceStack source = context.getSource();

        if (source.getServer() != null) {
            // обновляем для гастов во всех измерениях
            for (net.minecraft.server.level.ServerLevel level : source.getServer().getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof HappyGhast happyGhast) {
                        happyGhast.setSpeedMultiplier(multiplier);
                    }
                }
            }
        }

        // формируем сообщение
        String message;
        if (multiplier == DEFAULT_SPEED && oldMultiplier == DEFAULT_SPEED) {
            message = "Happy Ghast speed multiplier remained standard (1.0)";
        } else if (multiplier == DEFAULT_SPEED) {
            message = "Happy Ghast speed multiplier reset to standard (1.0)";
        } else {
            String comparison = "";
            if (oldMultiplier != multiplier) {
                if (multiplier > oldMultiplier) {
                    comparison = String.format(" (%.1fx faster)", multiplier / oldMultiplier);
                } else {
                    comparison = String.format(" (%.1fx slower)", oldMultiplier / multiplier);
                }
            }

            message = String.format("Happy Ghast speed multiplier changed from %.1f to %.1f%s",
                    oldMultiplier, multiplier, comparison);
        }

        // создаем пакет для синхронизации с клиентами
        GhastSpeedSyncPacket packet = new GhastSpeedSyncPacket(multiplier);

        // отправляем пакет всем клиентам
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);

        // отправляем сообщение об успехе
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        LOGGER.info("{} and broadcasted to all clients", message);

        // возвращаем 1 для обозначения успеха
        return 1;
    }
}