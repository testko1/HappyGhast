package dev.nweaver.happyghastmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ListHappyGhastsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("happyghast")
                        .then(Commands.literal("list")
                                .executes(context -> listGhasts(context.getSource(), 30))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> listGhasts(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))
                                )
                        )
        );
    }

    private static int listGhasts(CommandSourceStack source, int radius) {
        Entity entity = source.getEntity();
        if (!(entity instanceof Player)) {
            source.sendFailure(Component.literal("This command must be executed by a player"));
            return 0;
        }

        Player player = (Player) entity;
        ServerLevel level = source.getLevel();
        Vec3 pos = player.position();

        // создаем область поиска вокруг игрока
        AABB searchArea = new AABB(
                pos.x - radius, pos.y - radius, pos.z - radius,
                pos.x + radius, pos.y + radius, pos.z + radius
        );

        // находим всех счастливых гастов в этой области
        List<HappyGhast> ghasts = level.getEntitiesOfClass(HappyGhast.class, searchArea);

        if (ghasts.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.happyghast.list.no_ghasts", radius), false);
            return 0;
        }

        // сортируем по расстоянию до игрока
        ghasts.sort((g1, g2) -> {
            double dist1 = g1.distanceToSqr(player);
            double dist2 = g2.distanceToSqr(player);
            return Double.compare(dist1, dist2);
        });

        // формируем и отправляем сообщение
        source.sendSuccess(() -> Component.translatable("commands.happyghast.list.header", ghasts.size()), false);

        for (HappyGhast ghast : ghasts) {
            double distance = Math.sqrt(ghast.distanceToSqr(player));
            String saddleStatus = ghast.isSaddled()
                    ? "§a(" + Component.translatable("commands.happyghast.list.with_saddle").getString() + ")"
                    : "§c(" + Component.translatable("commands.happyghast.list.without_saddle").getString() + ")";
            String posString = String.format("§7[%.1f, %.1f, %.1f]", ghast.getX(), ghast.getY(), ghast.getZ());
            String distString = String.format("§7(%.1f blocks)", distance);

            // uuid с возможностью копирования при клике
            String uuidString = ghast.getUUID().toString();
            Component uuidComponent = Component.literal("§b" + uuidString)
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, uuidString))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("commands.happyghast.list.click_to_copy")))
                    );

            // создаем кнопку для команды добавления моба
            Component addMobButton = Component.literal(" §a[+]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ghastride " + uuidString + " zombie"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("commands.happyghast.list.add_mob")))
                    );

            // отправляем полное сообщение
            source.sendSuccess(() -> Component.literal("")
                    .append(posString)
                    .append(" ")
                    .append(distString)
                    .append(" ")
                    .append(saddleStatus)
                    .append(" UUID: ")
                    .append(uuidComponent)
                    .append(addMobButton), false);
        }

        return ghasts.size();
    }
}