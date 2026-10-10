package com.kodari.combatinjuries.client.mixin;

import com.kodari.combatinjuries.CombatInjuries;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flashes the body bright red while an adrenaline rush is active: injected mobs (Adrenaline Bolt) flash faster and faster,
 * and players flash on the beat of their heartbeat (visible in third person / F5). Lives in the non-required mixin config.
 */
@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererFlashMixin {
   @Inject(method = {"extractRenderState"}, at = {@At("TAIL")}, require = 0)
   private void ifaar$rushFlash(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
      MobEffectInstance fx = entity.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
      if (fx == null) {
         return;
      }

      if (entity instanceof Player) {
         int shots = fx.getAmplifier() + 1;
         int period = Math.max(7, 12 - 2 * (shots - 1));
         if (entity.level().getGameTime() % period < 3) {
            state.hasRedOverlay = true;
         }

         return;
      }

      boolean strong = fx.getAmplifier() >= 1;
      int duration = fx.getDuration();
      int interval = strong ? Math.max(2, 2 + (int)(10.0F * duration / 120.0F)) : 3;
      state.hasRedOverlay = (duration / interval) % 2 == 0;
   }
}
