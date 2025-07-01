package dev.nweaver.happyghastmod.command; // правильный пакет

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.entity.components.GhastPlatformComponent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class GhastRideCommand {

    // сообщения об ошибках
    private static final SimpleCommandExceptionType ERROR_NOT_A_HAPPY_GHAST = new SimpleCommandExceptionType(Component.literal("The specified entity is not a Happy Ghast"));
    private static final SimpleCommandExceptionType ERROR_GHAST_NOT_FOUND = new SimpleCommandExceptionType(Component.literal("Happy Ghast with this UUID not found"));
    private static final SimpleCommandExceptionType ERROR_CANNOT_SPAWN_ENTITY = new SimpleCommandExceptionType(Component.literal("Failed to create entity of the specified type"));
    private static final SimpleCommandExceptionType ERROR_CANNOT_RIDE = new SimpleCommandExceptionType(Component.literal("Failed to make the entity ride the ghast"));
    private static final SimpleCommandExceptionType ERROR_GHAST_FULL = new SimpleCommandExceptionType(Component.literal("The ghast has no free seats"));
    private static final SimpleCommandExceptionType ERROR_GHAST_PLATFORM = new SimpleCommandExceptionType(Component.literal("The ghast's platform is active, riding is not possible"));
    private static final SimpleCommandExceptionType ERROR_GHAST_NOT_SADDLED = new SimpleCommandExceptionType(Component.literal("The ghast is not saddled"));


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("ghastride")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("ghastUuid", UuidArgument.uuid())
                        .then(Commands.argument("entityType", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                                .executes(GhastRideCommand::executeSpawnAndRide)
                        )
                )
        );
    }

    private static int executeSpawnAndRide(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID ghastUuid = UuidArgument.getUuid(context, "ghastUuid");

        // исправленное получение entitytype
        // получаем холдер из аргумента
        Holder.Reference<EntityType<?>> entityTypeHolder = ResourceArgument.getResource(context, "entityType", Registries.ENTITY_TYPE);
        EntityType<?> entityTypeToSpawn = entityTypeHolder.value(); // получаем сам entitytype из холдера

        ServerLevel level = source.getLevel();

        // ищем гаста
        Entity ghastEntity = level.getEntity(ghastUuid);
        if (ghastEntity == null) {
            throw ERROR_GHAST_NOT_FOUND.create();
        }
        if (!(ghastEntity instanceof HappyGhast happyGhast)) {
            throw ERROR_NOT_A_HAPPY_GHAST.create();
        }

        // проверки возможности посадки
        // используем throw вместо sendfailure
        if (!happyGhast.isSaddled()) {
            throw ERROR_GHAST_NOT_SADDLED.create();
        }
        if (happyGhast.getPassengers().size() >= happyGhast.getMaxPassengers()) {
            throw ERROR_GHAST_FULL.create();
        }
        GhastPlatformComponent platformComponent = happyGhast.getPlatformComponent(); // получаем компонент один раз
        if (platformComponent != null && platformComponent.isActive()) {
            throw ERROR_GHAST_PLATFORM.create();
        }

        // создаем и спавним новую сущность
        Entity newEntity = entityTypeToSpawn.create(level);
        if (newEntity == null) {
            // полезная проверка, хотя resourceargument гарантирует валидный тип
            throw ERROR_CANNOT_SPAWN_ENTITY.create();
        }

        // позиционирование и спавн
        Vec3 spawnPos = happyGhast.position().add(0, happyGhast.getBbHeight() + 0.5, 0);
        newEntity.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, happyGhast.getYRot(), 0);
        if (newEntity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), net.minecraft.world.entity.MobSpawnType.COMMAND, null, null);
        }
        if (!level.addFreshEntity(newEntity)) {
            // sendfailure здесь уместен из-за непредвиденной ошибки спавна
            source.sendFailure(Component.literal("Failed to add entity " + newEntity.getName().getString() + " to the world"));
            return 0;
        }

        // пытаемся посадить сущность
        boolean success = newEntity.startRiding(happyGhast, true);

        if (success) {
            source.sendSuccess(() -> Component.literal("Entity " + newEntity.getName().getString() + " created and is now riding " + happyGhast.getName().getString()), true);
            return 1;
        } else {
            newEntity.discard();
            // используем throw вместо sendfailure
            throw ERROR_CANNOT_RIDE.create();
        }
    }
}