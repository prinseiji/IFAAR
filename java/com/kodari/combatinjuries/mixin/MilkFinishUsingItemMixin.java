package com.kodari.combatinjuries.mixin;

import com.kodari.combatinjuries.CombatInjuries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Item.class})
public abstract class MilkFinishUsingItemMixin {
   @Inject(
      method = {"finishUsingItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"},
      at = {@At("HEAD")}
   )
   private void ifaar$cureTetanusAfterConsumption(ItemStack var1, Level var2, LivingEntity var3, CallbackInfoReturnable<ItemStack> var4) {
      if (!var2.isClientSide() && (var1.getItem() == Items.MILK_BUCKET || var1.getItem() == Items.HONEY_BOTTLE) && var3 instanceof ServerPlayer var5) {
         CombatInjuries.cureTetanus(var5);
      }
   }
}
