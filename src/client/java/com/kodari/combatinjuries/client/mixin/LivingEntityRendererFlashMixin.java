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
 *
 * Mobs: the server encodes the rush in the mob's air supply (see AdrenalineBolts.markForClient):
 * weak = -1000 - ticksLeft, strong = -2000 - ticksLeft (values drift by a few ticks on the client, so ranges are used).
 */
@Mixin({LivingEntityRenderer.class})
public abstract class LivingEntityRendererFlashMixin {
   private static boolean ifaar$logged;

   @Inject(method = {"extractRenderState"}, at = {@At("TAIL")}, require = 0)
   private void ifaar$rushFlash(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
      if (entity instanceof Player) {
         MobEffectInstance fx = entity.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         if (fx != null) {
            int shots = fx.getAmplifier() + 1;
            int period = Math.max(7, 12 - 2 * (shots - 1));
            if (entity.level().getGameTime() % period < 3) {
               state.hasRedOverlay = true;
            }
         }

         return;
      }

      int air = entity.getAirSupply();
      boolean strong = air <= -1900 && air > -2300;
      boolean weak = air <= -950 && air > -1100;
      if (!strong && !weak) {
         return;
      }

      if (!ifaar$logged) {
         ifaar$logged = true;
         System.out.println("[IFAAR] red flash hook is active (air supply " + air + ")");
      }

      int ticksLeft = strong ? -2000 - air + 4 : -1000 - air + 4;
      int interval = strong ? Math.max(2, 2 + (int)(10.0F * ticksLeft / 120.0F)) : 3;
      state.hasRedOverlay = (Math.max(0, ticksLeft) / interval) % 2 == 0;
   }
}
