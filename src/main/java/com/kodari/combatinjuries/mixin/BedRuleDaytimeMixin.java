package com.kodari.combatinjuries.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
   targets = {"net.minecraft.world.attribute.BedRule$Rule"}
)
public abstract class BedRuleDaytimeMixin {
   @WrapOperation(
      method = {"test"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/Level;isDarkOutside()Z"
      )}
   )
   private boolean allowDaytimeRest(Level var1, Operation<Boolean> var2) {
      return (Boolean)var2.call(new Object[]{var1}) || var1.isBrightOutside();
   }
}
