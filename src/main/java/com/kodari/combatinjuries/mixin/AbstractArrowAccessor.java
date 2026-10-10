package com.kodari.combatinjuries.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets AdrenalineBolts read which item an arrow was fired from (getPickupItem is protected). */
@Mixin({AbstractArrow.class})
public interface AbstractArrowAccessor {
   @Invoker("getPickupItem")
   ItemStack ifaar$getPickupItem();
}
