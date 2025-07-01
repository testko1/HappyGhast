package dev.nweaver.happyghastmod.leash;

import dev.nweaver.happyghastmod.mixin.MobAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MultiLeashData {

    private static final Map<UUID, Set<UUID>> LEASH_MAP = new ConcurrentHashMap<>();
    private static final String NBT_KEY = "MultiLeashHolders";

    public static Set<UUID> getHolderUUIDs(Mob mob) {
        return LEASH_MAP.getOrDefault(mob.getUUID(), Collections.emptySet());
    }

    public static List<Entity> getLeashHolders(Mob mob, Level level) {
        if (level == null || level.isClientSide()) {
            return Collections.emptyList();
        }

        Set<UUID> holderUUIDs = getHolderUUIDs(mob);
        if (holderUUIDs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Entity> holders = new ArrayList<>();
        Set<UUID> currentUUIDs = new HashSet<>(holderUUIDs);
        boolean needsUpdate = false;
        for (UUID holderUUID : currentUUIDs) {
            Entity holder = findEntityByUUID(level, holderUUID);
            if (holder != null && !holder.isRemoved()) {
                holders.add(holder);
            } else {
                removeLeashHolder(mob, holderUUID);
                needsUpdate = true;
            }
        }
        if (needsUpdate) {
            updateMainLeashHolder(mob, null);
        }
        return holders;
    }

    public static boolean hasMultiLeashData(Mob mob) {
        return LEASH_MAP.containsKey(mob.getUUID()) && !LEASH_MAP.get(mob.getUUID()).isEmpty();
    }

    public static boolean isLeashedTo(Mob mob, Entity potentialHolder) {
        if (potentialHolder == null) return false;
        return getHolderUUIDs(mob).contains(potentialHolder.getUUID());
    }

    public static void addLeashHolder(Mob mob, Entity holder) {
        if (holder == null || mob == holder || mob.level().isClientSide()) return;
        LEASH_MAP.computeIfAbsent(mob.getUUID(), k -> Collections.synchronizedSet(new HashSet<>())).add(holder.getUUID());
    }

    public static void removeLeashHolder(Mob mob, Entity holder) {
        if (holder == null || mob.level().isClientSide()) return;
        removeLeashHolder(mob, holder.getUUID());
    }

    private static void removeLeashHolder(Mob mob, UUID holderUUID) {
        if (mob.level().isClientSide()) return;
        Set<UUID> holderUUIDs = LEASH_MAP.get(mob.getUUID());
        if (holderUUIDs != null) {
            holderUUIDs.remove(holderUUID);
            if (holderUUIDs.isEmpty()) {
                LEASH_MAP.remove(mob.getUUID());
            }
        }
    }

    public static void clearLeashHolders(Mob mob) {
        if (mob.level().isClientSide()) return;
        LEASH_MAP.remove(mob.getUUID());
    }

    public static void save(Mob mob, CompoundTag compound) {
        Set<UUID> holderUUIDs = getHolderUUIDs(mob);
        if (!holderUUIDs.isEmpty()) {
            ListTag leashList = new ListTag();
            for (UUID holderUUID : holderUUIDs) {
                CompoundTag holderTag = new CompoundTag();
                holderTag.putUUID("UUID", holderUUID);
                leashList.add(holderTag);
            }
            compound.put(NBT_KEY, leashList);
        }
    }

    public static void load(Mob mob, CompoundTag compound, Level level) {
        if (level.isClientSide()) return;
        LEASH_MAP.remove(mob.getUUID());
        if (compound.contains(NBT_KEY, 9)) {
            ListTag leashList = compound.getList(NBT_KEY, 10);
            Set<UUID> loadedUUIDs = Collections.synchronizedSet(new HashSet<>());
            for (int i = 0; i < leashList.size(); i++) {
                CompoundTag holderTag = leashList.getCompound(i);
                if (holderTag.hasUUID("UUID")) {
                    loadedUUIDs.add(holderTag.getUUID("UUID"));
                }
            }
            if (!loadedUUIDs.isEmpty()) {
                LEASH_MAP.put(mob.getUUID(), loadedUUIDs);
            }
        }
    }

    @Nullable
    public static Entity findEntityByUUID(Level level, UUID uuid) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(uuid);
        }
        return null;
    }

    public static void updateMainLeashHolder(Mob mob, @Nullable Entity preferredHolder) {
        if (mob.level().isClientSide()) return;

        Entity currentMainHolder = ((MobAccessor) mob).getLeashHolderAccessor();
        List<Entity> allHolders = getLeashHolders(mob, mob.level());
        Entity newMainHolder = null;

        if (preferredHolder != null && !preferredHolder.isRemoved() && isLeashedTo(mob, preferredHolder)) {
            newMainHolder = preferredHolder;
        } else {
            newMainHolder = allHolders.stream()
                    .filter(h -> h instanceof net.minecraft.world.entity.player.Player)
                    .findFirst().orElse(null);

            if (newMainHolder == null) {
                newMainHolder = allHolders.stream()
                        .filter(h -> h instanceof net.minecraft.world.entity.decoration.LeashFenceKnotEntity)
                        .findFirst().orElse(null);
            }
            if (newMainHolder == null && !allHolders.isEmpty()) {
                newMainHolder = allHolders.get(0);
            }
        }

        if (currentMainHolder != newMainHolder) {
            ((MobAccessor) mob).setLeashHolder(newMainHolder);

            if (mob.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().broadcastAndSend(mob,
                        new ClientboundSetEntityLinkPacket(mob, newMainHolder));
            }
        }
    }
}