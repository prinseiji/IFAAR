package com.kodari.combatinjuries.client.mixin;

import com.kodari.combatinjuries.CombatInjuries;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({SoundEngine.class})
public abstract class SoundEngineHysteriaMixin {
   @WrapOperation(
      method = {"play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"
      )}
   )
   private float amplifyEnemySound(SoundInstance var1, Operation<Float> var2) {
      float var3 = (Float)var2.call(new Object[]{var1});
      float boosted = isEnemySound(var1) ? Math.min(1.0F, var3 * 3.0F) : var3;
      return boosted * concussionFactor(var1);
   }

   @WrapOperation(
      method = {"tickInGameSound()V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F"
      )}
   )
   private float amplifyTickingEnemySound(SoundEngine var1, SoundInstance var2, Operation<Float> var3) {
      float var4 = (Float)var3.call(new Object[]{var1, var2});
      float boosted = isEnemySound(var2) ? Math.min(1.0F, var4 * 3.0F) : var4;
      return boosted * concussionFactor(var2);
   }

   private static final float CONCUSSION_RECOVERY_TICKS = 100.0F;

   /**
    * While concussed the world is muted and the tinnitus rings. During the last
    * CONCUSSION_RECOVERY_TICKS the world fades back in and the tinnitus fades out.
    */
   private static float concussionFactor(SoundInstance var0) {
      LocalPlayer player = Minecraft.getInstance().player;
      boolean tinnitus = isTinnitusSound(var0);
      boolean music = var0.getIdentifier().equals(CombatInjuries.ADRENALINE_MUSIC.location());
      MobEffectInstance effect = player == null ? null : player.getEffect(CombatInjuries.CONCUSSION_EFFECT);
      if (effect == null) {
         return tinnitus ? 0.0F : 1.0F;
      }

      float recoveryTicks = effect.getAmplifier() >= 1 ? CONCUSSION_RECOVERY_TICKS : 60.0F;
      float recovered = 1.0F - Math.max(0.0F, Math.min(1.0F, effect.getDuration() / recoveryTicks));
      recovered = recovered * recovered * (3.0F - 2.0F * recovered);
      if (tinnitus) {
         return 1.0F - recovered;
      }

      return music ? 0.18F + 0.82F * recovered : recovered;
   }

   private static boolean isTinnitusSound(SoundInstance var0) {
      return var0.getIdentifier().equals(CombatInjuries.CONCUSSION_TINNITUS.location());
   }

   private static boolean isEnemySound(SoundInstance var0) {
      LocalPlayer var1 = Minecraft.getInstance().player;
      return var1 != null && var1.hasEffect(CombatInjuries.HYSTERIA_EFFECT) && var0 instanceof EntityBoundSoundInstance var2
         ? ((EntityBoundSoundInstanceAccessor)var2).combatinjuries$getEntity() instanceof Mob var4 && var4.isAggressive()
         : false;
   }
}
