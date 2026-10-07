package com.kodari.combatinjuries.mixin;

import com.kodari.combatinjuries.CombatInjuries;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BrewingStandBlockEntity.class})
public abstract class BrewingStandBlockEntityMixin {
   @WrapOperation(
      method = {"isBrewable"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;isIngredient(Lnet/minecraft/world/item/ItemStack;)Z"
      )}
   )
   private static boolean allowPufferfish(PotionBrewing var0, ItemStack var1, Operation<Boolean> var2) {
      return var1.getItem() == Items.PUFFERFISH || (Boolean)var2.call(new Object[]{var0, var1});
   }

   @WrapOperation(
      method = {"isBrewable"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;hasMix(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
      )}
   )
   private static boolean allowSwiftnessOutput(PotionBrewing var0, ItemStack var1, ItemStack var2, Operation<Boolean> var3) {
      return isSwiftness(var1) && var2.getItem() == Items.PUFFERFISH || (Boolean)var3.call(new Object[]{var0, var1, var2});
   }

   @WrapOperation(
      method = {"doBrew"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/item/alchemy/PotionBrewing;mix(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"
      )}
   )
   private static ItemStack brewAdrenalineShot(PotionBrewing var0, ItemStack var1, ItemStack var2, Operation<ItemStack> var3) {
      return var1.getItem() == Items.PUFFERFISH && isSwiftness(var2)
         ? new ItemStack(CombatInjuries.ADRENALINE_SHOT)
         : (ItemStack)var3.call(new Object[]{var0, var1, var2});
   }

   private static boolean isSwiftness(ItemStack var0) {
      if (var0.getItem() != Items.POTION) {
         return false;
      } else {
         PotionContents var1 = (PotionContents)var0.get(DataComponents.POTION_CONTENTS);
         return var1 != null && (var1.is(Potions.SWIFTNESS) || var1.is(Potions.LONG_SWIFTNESS) || var1.is(Potions.STRONG_SWIFTNESS));
      }
   }
}
