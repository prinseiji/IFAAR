package com.kodari.combatinjuries.client.mixin;

import com.kodari.combatinjuries.CombatInjuries;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({EntityRenderer.class})
public abstract class EntityRendererHysteriaMixin {
   @WrapOperation(
      method = {"extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z"
      )}
   )
   private boolean hideEnemyNameplate(EntityRenderer<?, ?> var1, Entity var2, double var3, Operation<Boolean> var5) {
      return isHysteriaActive() && isEnemy(var2) ? false : (Boolean)var5.call(new Object[]{var1, var2, var3});
   }

   @WrapOperation(
      method = {"extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/entity/Entity;belowNameDisplay()Lnet/minecraft/network/chat/Component;"
      )}
   )
   private Component hideEnemyScore(Entity var1, Operation<Component> var2) {
      return isHysteriaActive() && isEnemy(var1) ? null : (Component)var2.call(new Object[]{var1});
   }

   private static boolean isHysteriaActive() {
      LocalPlayer var0 = Minecraft.getInstance().player;
      return var0 != null && var0.hasEffect(CombatInjuries.HYSTERIA_EFFECT);
   }

   private static boolean isEnemy(Entity var0) {
      return var0 instanceof Mob var1 && var1.isAggressive();
   }
}
