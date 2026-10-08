package com.kodari.combatinjuries.client.mixin;

import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({EntityBoundSoundInstance.class})
public interface EntityBoundSoundInstanceAccessor {
   @Accessor("entity")
   Entity combatinjuries$getEntity();
}
