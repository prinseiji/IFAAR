package com.kodari.combatinjuries.mixin;

import com.kodari.combatinjuries.CombatInjuries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class LivingEntityInjuryMixin {
   @Inject(
      method = {"jumpFromGround()V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void preventJumpWithFracture(CallbackInfo var1) {
      if ((Object)this instanceof Player var2 && (CombatInjuries.isFractured(var2) || var2.hasEffect(CombatInjuries.FRACTURE_EFFECT) || CombatInjuries.isStunned(var2) || var2.hasEffect(CombatInjuries.STUN_EFFECT))) {
         var1.cancel();
      }
   }
}
