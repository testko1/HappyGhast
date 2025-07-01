package dev.nweaver.happyghastmod.events;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.entity.Ghastling;
import dev.nweaver.happyghastmod.init.EntityInit;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HappyGhastMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonModEvents {

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(EntityInit.HAPPY_GHAST.get(), HappyGhast.createAttributes().build());
        event.put(EntityInit.GHASTLING.get(), Ghastling.createAttributes().build());
    }
}