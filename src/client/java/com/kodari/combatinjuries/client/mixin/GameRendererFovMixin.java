package com.kodari.combatinjuries.client.mixin;

import com.kodari.combatinjuries.client.CombatInjuriesClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the adrenaline "overstimulation" zoom/widening to the camera field of view. Lives in its own non-required mixin config. */
@Mixin({GameRenderer.class})
public abstract class GameRendererFovMixin {
   @Inject(method = {"getFov"}, at = {@At("RETURN")}, cancellable = true, require = 0)
   private void ifaar$adrenalineFov(CallbackInfoReturnable<Float> cir) {
      float bonus = CombatInjuriesClient.fovBonusDegrees();
      if (bonus != 0.0F) {
         cir.setReturnValue(cir.getReturnValue() + bonus);
      }
   }
}
