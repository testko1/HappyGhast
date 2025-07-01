package dev.nweaver.happyghastmod.api;

import java.util.Optional;
import java.util.UUID;

public interface IQuadLeashTarget {
    Optional<UUID> getQuadLeashingGhastUUID();
    void setQuadLeashingGhastUUID(Optional<UUID> ghastUUID);
}