package com.kodari.combatinjuries;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.AllowResettingTime;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.StartSleeping;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.StopSleeping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import com.kodari.combatinjuries.AdrenalineRules.Zone;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents.Modify;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.EntityHitResult;

public final class CombatInjuries implements ModInitializer {
   public static final String MOD_ID = "combatinjuries";
   private static final String RUSTY_MARKER = "combatinjuries_rusty";
   private static final double TETANUS_CHANCE = 0.25;
   private static final double HEMORRHAGE_BASE_CHANCE = 0.50;
   private static final float HEMORRHAGE_CURE_HEAL = 2.0F;
   private static final boolean BLEEDING_BLOCKS_REGEN = true;
   private static final double RUSH_SPEED_BONUS = 0.55;
   private static final double CRASH_SPEED_PENALTY = 0.55;
   private static final java.util.Set<String> RUSTY_TOOL_PATHS = java.util.Set.of(
      "iron_sword", "iron_axe", "iron_pickaxe", "iron_shovel", "iron_hoe",
      "copper_sword", "copper_axe", "copper_pickaxe", "copper_shovel", "copper_hoe"
   );
   private static final int SLEEP_TICKS_REQUIRED = 100;
   private static final int FRACTURE_STILL_TICKS = 100;
   private static final ResourceKey<Item> ADRENALINE_KEY = ResourceKey.create(
      Registries.ITEM, Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_shot")
   );
   public static final Item ADRENALINE_SHOT = (Item)Registry.register(BuiltInRegistries.ITEM, ADRENALINE_KEY, new Item(new Properties().setId(ADRENALINE_KEY).stacksTo(16)));
   private static final ResourceKey<Item> SYRINGE_KEY = ResourceKey.create(
      Registries.ITEM, Identifier.fromNamespaceAndPath("combatinjuries", "syringe")
   );
   public static final Item SYRINGE = (Item)Registry.register(BuiltInRegistries.ITEM, SYRINGE_KEY, new Item(new Properties().setId(SYRINGE_KEY)));
   public static final Holder<MobEffect> HEMORRHAGE_EFFECT = registerEffect("hemorrhage", MobEffectCategory.HARMFUL, 8196128);
   public static final Holder<MobEffect> TETANUS_EFFECT = registerEffect("tetanus", MobEffectCategory.HARMFUL, 10903090);
   public static final Holder<MobEffect> FRACTURE_EFFECT = registerEffect("fracture", MobEffectCategory.HARMFUL, 13223352);
   public static final Holder<MobEffect> CONCUSSION_EFFECT = registerEffect("concussion", MobEffectCategory.HARMFUL, 12959786);
   public static final Holder<MobEffect> ASPHYXIA_EFFECT = registerEffect("asphyxia", MobEffectCategory.HARMFUL, 2505809);
   public static final Holder<MobEffect> WINDED_EFFECT = registerEffect("winded", MobEffectCategory.HARMFUL, 7702681);
   public static final Holder<MobEffect> HYSTERIA_EFFECT = registerEffect("hysteria", MobEffectCategory.NEUTRAL, 15263976);
   public static final Holder<MobEffect> ADRENALINE_RUSH_EFFECT = registerEffect("adrenaline_rush", MobEffectCategory.BENEFICIAL, 14096693);
   public static final Holder<MobEffect> ADRENALINE_CRASH_EFFECT = registerEffect("adrenaline_crash", MobEffectCategory.HARMFUL, 3356234);
   public static final Holder<MobEffect> STUN_EFFECT = registerEffect("adrenaline_stun", MobEffectCategory.HARMFUL, 8421504);
   private static final Identifier FRACTURE_SPEED_ID = Identifier.fromNamespaceAndPath("combatinjuries", "fracture_speed");
   private static final Identifier RUSH_SPEED_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_rush_speed");
   private static final Identifier CRASH_SPEED_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_crash_speed");
   private static final Identifier CRASH_FATIGUE_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_crash_fatigue");
   private static final Identifier CRASH_ATTACK_SPEED_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_crash_attack_speed");
   private static final Identifier CRASH_JUMP_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_crash_jump");
   private static final Identifier RUSH_DAMAGE_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_rush_damage");
   private static final Identifier HYSTERIA_DAMAGE_ID = Identifier.fromNamespaceAndPath("combatinjuries", "hysteria_damage");
   private static final Identifier HYSTERIA_KNOCKBACK_ID = Identifier.fromNamespaceAndPath("combatinjuries", "hysteria_knockback_resistance");
   private static final ResourceKey<DamageType> HEMORRHAGE_DAMAGE_TYPE = damageTypeKey("hemorrhage");
   private static final ResourceKey<DamageType> ASPHYXIA_DAMAGE_TYPE = damageTypeKey("asphyxia");
   private static final ResourceKey<DamageType> ADRENALINE_CRASH_DAMAGE_TYPE = damageTypeKey("adrenaline_crash");
   private static final ResourceKey<DamageType> ADRENALINE_OVERDOSE_DAMAGE_TYPE = damageTypeKey("adrenaline_overdose");
   private static final Identifier STUN_SPEED_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_stun_speed");
   private static final Identifier ADRENALINE_MUSIC_ID = Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_music");
   private static final Identifier CONCUSSION_TINNITUS_ID = Identifier.fromNamespaceAndPath("combatinjuries", "concussion_tinnitus");
   public static final SoundEvent ADRENALINE_MUSIC = (SoundEvent)Registry.register(
      BuiltInRegistries.SOUND_EVENT, ADRENALINE_MUSIC_ID, SoundEvent.createVariableRangeEvent(ADRENALINE_MUSIC_ID)
   );
   public static final SoundEvent CONCUSSION_TINNITUS = (SoundEvent)Registry.register(
      BuiltInRegistries.SOUND_EVENT, CONCUSSION_TINNITUS_ID, SoundEvent.createVariableRangeEvent(CONCUSSION_TINNITUS_ID)
   );
   public static final SoundEvent ADRENALINE_INJECT = registerSound("adrenaline_inject");
   public static final SoundEvent ADRENALINE_POWER_UP = registerSound("adrenaline_powerup");
   public static final SoundEvent ADRENALINE_POWER_DOWN = registerSound("adrenaline_powerdown");
   public static final SoundEvent ADRENALINE_POWER_DOWN_GRAND = registerSound("adrenaline_powerdown_grand");
   public static final SoundEvent ADRENALINE_VEINS = registerSound("adrenaline_veins");
   public static final SoundEvent BOLT_BEEP = registerSound("bolt_beep");
   public static final SoundEvent BOLT_BURST_WEAK = registerSound("bolt_burst_weak");
   public static final SoundEvent BOLT_BURST_STRONG = registerSound("bolt_burst_strong");
   public static final SoundEvent ADRENALINE_HEARTBEAT = registerSound("adrenaline_heartbeat");
   private static final Map<UUID, CombatInjuries.InjuryState> STATES = new HashMap<>();

   public void onInitialize() {
      AdrenalineBolts.init();
      CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")))
         .register((ModifyOutput)var0 -> {
            var0.accept(ADRENALINE_SHOT);
            var0.accept(SYRINGE);
         });
      LootTableEvents.MODIFY
         .register(
            (Modify)(var0, var1, var2, var3) -> {
               if (var0.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)
                  || var0.equals(BuiltInLootTables.STRONGHOLD_CROSSING)
                  || var0.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR)
                  || var0.equals(BuiltInLootTables.ANCIENT_CITY)
                  || var0.equals(BuiltInLootTables.ANCIENT_CITY_ICE_BOX)) {
                  var1.withPool(LootPool.lootPool().add(LootItem.lootTableItem(ADRENALINE_SHOT)).when(LootItemRandomChanceCondition.randomChance(0.01F)));
               }
            }
         );
      ServerEntityEvents.ENTITY_LOAD.register(CombatInjuries::markNaturallyHeldIronItems);
      AttackEntityCallback.EVENT.register(CombatInjuries::onAttack);
      UseItemCallback.EVENT.register(CombatInjuries::onUseItem);
      ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatInjuries::afterDamage);
      ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
         if (dead instanceof ServerPlayer deadPlayer) {
            state(deadPlayer).seenMask = 0;
         } else if (source != null && source.getEntity() instanceof ServerPlayer killer) {
            CombatInjuries.InjuryState ks = state(killer);
            if (ks.adrenalineRushTicks > 0 && ks.rushShots >= 3) {
               grant(killer, "no_touching");
            }
         }
      });
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(var0, var1, var2) -> var0.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                                      "injurytest"
                                                   )
                                                   .requires(var0x -> var0x.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)))
                                                .then(
                                                   Commands.literal("hemorrhage")
                                                      .executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "hemorrhage"))
                                                ))
                                             .then(
                                                Commands.literal("tetanus").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "tetanus"))
                                             ))
                                          .then(
                                             Commands.literal("fracture").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "fracture"))
                                          ))
                                       .then(
                                          Commands.literal("concussion").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "concussion"))
                                       ))
                                    .then(Commands.literal("concussion_light").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "concussion_light")))
                                    .then(Commands.literal("adrenaline_shots_2").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "adrenaline_shots_2")))
                                    .then(Commands.literal("adrenaline_shots_3").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "adrenaline_shots_3")))
                                    .then(Commands.literal("adrenaline_shots_4").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "adrenaline_shots_4")))
                                    .then(Commands.literal("adrenaline_stun").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "adrenaline_stun")))
                                    .then(Commands.literal("overdose").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "overdose")))
                                    .then(Commands.literal("qte").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "qte")))
                                    .then(Commands.literal("fake_qte").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "fake_qte")))
                                    .then(Commands.literal("asphyxia").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "asphyxia"))))
                                 .then(Commands.literal("winded").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "winded"))))
                              .then(Commands.literal("hysteria").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "hysteria"))))
                           .then(Commands.literal("adrenaline_rush").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "adrenaline_rush"))))
                        .then(Commands.literal("adrenaline_crash").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "adrenaline_crash"))))
                     .then(Commands.literal("rusty_zombie").executes(var0x -> spawnRustyZombie((CommandSourceStack)var0x.getSource()))))
                  .then(Commands.literal("clear").executes(var0x -> runInjuryTest((CommandSourceStack)var0x.getSource(), "clear")))
            )
         );
      ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
      EntitySleepEvents.START_SLEEPING.register((StartSleeping)(var0, var1) -> {
         if (var0 instanceof ServerPlayer var2) {
            CombatInjuries.InjuryState startState = state(var2);
            startState.deepSleepQualified = false;
            startState.sleepTicks = 0;
         }
      });
      EntitySleepEvents.ALLOW_RESETTING_TIME.register((AllowResettingTime)var0 -> {
         if (var0 instanceof ServerPlayer var1) {
            state(var1).deepSleepQualified = true;
         }

         return true;
      });
      EntitySleepEvents.STOP_SLEEPING.register((StopSleeping)(var0, var1) -> {
         if (var0 instanceof ServerPlayer var2) {
            CombatInjuries.InjuryState var3 = state(var2);
            boolean sleptThrough = var3.deepSleepQualified && var3.sleepTicks >= SLEEP_TICKS_REQUIRED;
            var3.deepSleepQualified = false;
            var3.sleepTicks = 0;
            if (!sleptThrough) {
               return;
            }

            if (var3.hemorrhage) {
               grant(var2, "sleep_cure");
            }

            var3.hemorrhage = false;
            var3.tetanus = false;
            var3.fracture = false;
            var3.asphyxia = false;
            var3.asphyxiaSawLowAir = false;
            var3.asphyxiaFromBurial = false;
            var3.asphyxiaDamageTicks = 0;
            var3.buriedSuffocationTicks = 0;
            var3.lastBuriedSuffocationTick = -1;
            var3.concussionTicks = 0;
            var3.winded = false;
            var3.windedTicks = 0;
            var3.hysteria = false;
            var3.adrenalineRushTicks = 0;
            var3.adrenalineCrashTicks = 0;
            var3.storedAdrenalineDamage = 0.0F;
            var3.rushShots = 0;
            var3.stunTicks = 0;
            var3.shotLockoutTicks = 0;
            var3.bleedTicks = 0;
            var3.tetanusCooldown = 0;
            var3.tetanusDropOffHand = false;
            var3.berryDamageTicks = 0;
            var3.lastBerryDamageTick = -1;
            var3.stillTicks = 0;
            var3.deepRecoveryTicks = 400;
            var3.deepRecoveryHealTicks = 0;
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)var0 -> {
         for (ServerPlayer var2 : var0.getPlayerList().getPlayers()) {
            tickPlayer(var2);
         }
      });
   }

   private static void markNaturallyHeldIronItems(Entity var0, ServerLevel var1) {
      if (var0.getType() == EntityTypes.ZOMBIE || var0.getType() == EntityTypes.HUSK || var0.getType() == EntityTypes.DROWNED) {
         ItemStack var2 = ((LivingEntity)var0).getMainHandItem();
         Identifier var3 = BuiltInRegistries.ITEM.getKey(var2.getItem());
         if (var3 != null && RUSTY_TOOL_PATHS.contains(var3.getPath())) {
            CompoundTag var4 = new CompoundTag();
            CustomData var5 = (CustomData)var2.get(DataComponents.CUSTOM_DATA);
            if (var5 != null) {
               var4 = var5.copyTag();
            }

            var4.putBoolean("combatinjuries_rusty", true);
            var2.set(DataComponents.CUSTOM_DATA, CustomData.of(var4));
            var2.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("combatinjuries", "rusty_" + var3.getPath()));
         }
      }
   }

   private static boolean isIronArmor(String var0) {
      return var0.equals("iron_helmet") || var0.equals("iron_chestplate") || var0.equals("iron_leggings") || var0.equals("iron_boots");
   }

   private static InteractionResult onAttack(Player var0, Level var1, InteractionHand var2, Entity var3, EntityHitResult var4) {
      if (!(var0 instanceof ServerPlayer var5)) {
         return InteractionResult.PASS;
      } else if (state(var5).stunTicks > 0) {
         return InteractionResult.FAIL;
      } else if (var5.getHealth() > 4.0F && state(var5).concussionTicks > 0 && Math.random() < 0.2) {
         return InteractionResult.FAIL;
      } else {
         return InteractionResult.PASS;
      }
   }

   private static int runInjuryTest(CommandSourceStack var0, String var1) throws CommandSyntaxException {
      ServerPlayer var2 = var0.getPlayerOrException();
      CombatInjuries.InjuryState var3 = state(var2);
      switch (var1) {
         case "hemorrhage":
            var3.hemorrhage = true;
            break;
         case "tetanus":
            var3.tetanus = true;
            break;
         case "fracture":
            var3.fracture = true;
            break;
         case "concussion":
            applyConcussion(var3, true);
            break;
         case "adrenaline_shots_2":
         case "adrenaline_shots_3":
         case "adrenaline_shots_4":
            var3.rushShots = Integer.parseInt(var1.substring(var1.length() - 1));
            var3.adrenalineRushTicks = AdrenalineRules.segmentTicks(var3.rushShots);
            var3.adrenalineCrashTicks = 0;
            break;
         case "adrenaline_stun":
            var3.stunTicks = AdrenalineRules.CASHOUT_STUN_TICKS;
            break;
         case "overdose":
            overdose(var2, var3, Math.max(1, var3.rushShots), false);
            break;
         case "fake_qte":
            var3.rushShots = Math.max(3, var3.rushShots);
            var3.adrenalineRushTicks = AdrenalineRules.segmentTicks(var3.rushShots) - (AdrenalineRules.fakeStart1(var3.rushShots) + 3);
            var3.adrenalineCrashTicks = 0;
            break;
         case "qte":
            if (var3.adrenalineRushTicks > 0) {
               var3.adrenalineRushTicks = AdrenalineRules.windowTicks(Math.max(1, var3.rushShots)) + AdrenalineRules.LATENCY_GRACE_TICKS + 20;
            }
            break;
         case "concussion_light":
            applyConcussion(var3, false);
            break;
         case "asphyxia":
            var3.asphyxia = true;
            var3.asphyxiaSawLowAir = true;
            var2.setAirSupply(0);
            break;
         case "winded":
            var3.windedTicks = 200;
            break;
         case "hysteria":
            var2.setHealth(Math.min(var2.getHealth(), 4.0F));
            break;
         case "adrenaline_rush":
            var3.adrenalineRushTicks = 600;
            var3.rushShots = 1;
            var3.adrenalineCrashTicks = 0;
            var3.storedAdrenalineDamage = 0.0F;
            var3.rushShots = 0;
            var3.stunTicks = 0;
            var3.shotLockoutTicks = 0;
            break;
         case "adrenaline_crash":
            var3.adrenalineRushTicks = 0;
            var3.adrenalineCrashTicks = 300;
            break;
         case "clear":
            var3.hemorrhage = false;
            var3.tetanus = false;
            var3.fracture = false;
            var3.asphyxia = false;
            var3.asphyxiaSawLowAir = false;
            var3.asphyxiaFromBurial = false;
            var3.asphyxiaDamageTicks = 0;
            var3.buriedSuffocationTicks = 0;
            var3.lastBuriedSuffocationTick = -1;
            var3.winded = false;
            var3.windedTicks = 0;
            var3.hysteria = false;
            var3.concussionTicks = 0;
            var3.adrenalineRushTicks = 0;
            var3.adrenalineCrashTicks = 0;
            var3.storedAdrenalineDamage = 0.0F;
            var3.rushShots = 0;
            var3.stunTicks = 0;
            var3.shotLockoutTicks = 0;
            var3.deepRecoveryTicks = 0;
            var3.tetanusCooldown = 0;
            var3.bleedTicks = 0;
            var3.stillTicks = 0;
            var2.setHealth(var2.getMaxHealth());
      }

      var0.sendSuccess(() -> Component.literal("Injury test: " + var1), false);
      return 1;
   }

   private static int spawnRustyZombie(CommandSourceStack var0) throws CommandSyntaxException {
      ServerPlayer var1 = var0.getPlayerOrException();
      ServerLevel var2 = var1.level();
      Zombie var3 = (Zombie)EntityTypes.ZOMBIE.create(var2, EntitySpawnReason.COMMAND);
      if (var3 == null) {
         return 0;
      } else {
         Item[] var4 = new Item[]{
            Items.IRON_SWORD,
            Items.IRON_AXE,
            Items.IRON_PICKAXE,
            Items.IRON_SHOVEL,
            Items.IRON_HOE,
            Items.COPPER_SWORD,
            Items.COPPER_AXE,
            Items.COPPER_PICKAXE,
            Items.COPPER_SHOVEL,
            Items.COPPER_HOE
         };
         Item var5 = var4[ThreadLocalRandom.current().nextInt(var4.length)];
         String var6 = BuiltInRegistries.ITEM.getKey(var5).getPath();
         var3.setPos(var1.getX() + 2.0, var1.getY(), var1.getZ());
         var3.setItemInHand(InteractionHand.MAIN_HAND, rustyCreativeStack(var5, "item.combatinjuries.rusty_" + var6));
         if (!var2.addFreshEntity(var3)) {
            return 0;
         } else {
            var0.sendSuccess(() -> Component.literal("Spawned a rusty " + var6.replace('_', ' ') + " zombie."), false);
            return 1;
         }
      }
   }

   private static InteractionResult onUseItem(Player var0, Level var1, InteractionHand var2) {
      if (!(var0 instanceof ServerPlayer var3)) {
         return InteractionResult.PASS;
      } else {
         ItemStack var4 = var3.getItemInHand(var2);
         CombatInjuries.InjuryState var5 = state(var3);
         if (var4.getItem() == Items.GOLDEN_APPLE || var4.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            var5.concussionTicks = 0;
         }

         if (var4.getItem() != ADRENALINE_SHOT) {
            return InteractionResult.PASS;
         } else if (var5.stunTicks > 0 || var5.shotLockoutTicks > 0 || var3.getCooldowns().isOnCooldown(var4)) {
            return InteractionResult.FAIL;
         } else {
            int shots = var5.adrenalineRushTicks > 0 ? Math.max(1, var5.rushShots) : 0;
            if (shots == 0) {
               ItemStack cdStack = var4.copy();
               var4.consume(1, var3);
               var3.getCooldowns().addCooldown(cdStack, AdrenalineRules.cooldownTicks(1));
               var1.playSound((Player)null, var3.getX(), var3.getY(), var3.getZ(), ADRENALINE_INJECT, SoundSource.PLAYERS, 1.0F, 1.0F);
               var5.rushShots = 1;
               var5.adrenalineRushTicks = AdrenalineRules.FIRST_SEGMENT_TICKS;
               var5.adrenalineCrashTicks = 0;
               var5.storedAdrenalineDamage = 0.0F;
               grant(var3, "first_dose");
               CombatInjuriesApi.RUSH_STARTED.invoker().on(var3);
               return InteractionResult.SUCCESS;
            }

            Zone zone = AdrenalineRules.zoneFor(shots, var5.adrenalineRushTicks);
            if (zone == Zone.COOLDOWN) {
               return InteractionResult.FAIL;
            }

            ItemStack cdStack2 = var4.copy();
            var4.consume(1, var3);
            var3.getCooldowns().addCooldown(cdStack2, AdrenalineRules.cooldownTicks(Math.min(AdrenalineRules.MAX_SHOTS, shots + 1)));
            var1.playSound((Player)null, var3.getX(), var3.getY(), var3.getZ(), ADRENALINE_INJECT, SoundSource.PLAYERS, 1.0F, 1.0F + 0.12F * shots);
            boolean fakePress = zone == Zone.DANGER && AdrenalineRules.fakeActive(shots, var5.adrenalineRushTicks);
            if (shots >= AdrenalineRules.MAX_SHOTS) {
               overdose(var3, var5, shots, fakePress);
            } else if (zone == Zone.WINDOW) {
               var5.rushShots = shots + 1;
               var5.adrenalineRushTicks = AdrenalineRules.segmentTicks(var5.rushShots);
               grant(var3, var5.rushShots == 2 ? "chain_2" : (var5.rushShots == 3 ? "chain_3" : "chain_4"));
            } else if (shots >= AdrenalineRules.LETHAL_EARLY_STACK) {
               overdose(var3, var5, shots, fakePress);
            } else {
               grant(var3, "jumpy");
               var5.stunTicks = AdrenalineRules.SHOCK_STUN_TICKS;
               endRush(var3, var5, false);
            }

            return InteractionResult.SUCCESS;
         }
      }
   }

   /** Rush is over: either pay the (multiplied) bill and crash, or - after a perfect 4-shot chain - walk away stunned with no debt. */
   private static void endRush(ServerPlayer var0, CombatInjuries.InjuryState var1, boolean cashOut) {
      int shots = Math.max(1, Math.min(AdrenalineRules.MAX_SHOTS, var1.rushShots));
      var1.adrenalineRushTicks = 0;
      var1.rushShots = 0;
      CombatInjuriesApi.RUSH_ENDED.invoker().on(var0, cashOut);
      if (cashOut) {
         grant(var0, "quit_ahead");
         var1.storedAdrenalineDamage = 0.0F;
         var1.stunTicks = AdrenalineRules.CASHOUT_STUN_TICKS;
         return;
      }

      var1.adrenalineCrashTicks = 300;
      var1.crashFromShots = shots;
      if (var1.storedAdrenalineDamage > 0.0F) {
         ServerLevel var13 = var0.level();
         float bill = var1.storedAdrenalineDamage * AdrenalineRules.CRASH_MULT[shots];
         var1.storedAdrenalineDamage = 0.0F;
         var0.hurtServer(var13, injuryDamageSource(var13, ADRENALINE_CRASH_DAMAGE_TYPE), bill);
      }
   }

   /** Lethal damage that ignores armor, effects and enchantments. A Totem of Undying still saves you, at a heavy price. Creative players keep the penalty but not the death. */
   private static void overdose(ServerPlayer var0, CombatInjuries.InjuryState var1, int shotsBefore, boolean fake) {
      grant(var0, "overdose");
      CombatInjuriesApi.OVERDOSED.invoker().on(var0);
      if (shotsBefore < AdrenalineRules.MAX_SHOTS) {
         grant(var0, "overdose_early");
      }

      if (fake) {
         grant(var0, "fake_qte");
      }

      var1.adrenalineRushTicks = 0;
      var1.adrenalineCrashTicks = 0;
      var1.rushShots = 0;
      var1.storedAdrenalineDamage = 0.0F;
      if (!var0.isCreative() && !var0.isSpectator()) {
         ServerLevel var2 = var0.level();
         var0.hurtServer(var2, injuryDamageSource(var2, ADRENALINE_OVERDOSE_DAMAGE_TYPE), 1.0E6F);
      }

      if (var0.isAlive()) {
         if (!var0.isCreative() && !var0.isSpectator()) {
            grant(var0, "take_two");
         }

         var1.stunTicks = AdrenalineRules.TOTEM_STUN_TICKS;
         var1.shotLockoutTicks = AdrenalineRules.OVERDOSE_LOCKOUT_TICKS;
      }
   }

   private static void afterDamage(LivingEntity var0, DamageSource var1, float var2, float var3, boolean var4) {
      if (var0 instanceof ServerPlayer var5 && !var4) {
         CombatInjuries.InjuryState var6 = state(var5);
         boolean var7 = var5.getHealth() <= 4.0F;
         boolean var8 = var1.is(DamageTypes.CACTUS) || var1.is(DamageTypes.SWEET_BERRY_BUSH);
         if (var8) {
            int var9 = var6.lastBerryDamageTick < 0 ? 0 : var6.ticks - var6.lastBerryDamageTick;
            if (var9 > 25) {
               var6.berryDamageTicks = 0;
            } else {
               var6.berryDamageTicks += var9;
            }

            var6.lastBerryDamageTick = var6.ticks;
            if (var6.berryDamageTicks >= 100) {
               var6.hemorrhage = true;
            }
         }

         if (!var7 && var1.is(DamageTypes.FALL) && var3 >= 8.0F) {
            var6.fracture = true;
            var6.stillTicks = 0;
         }

         Entity var14 = var1.getEntity();
         boolean var10 = var1.is(DamageTypes.MACE_SMASH) && var14 instanceof LivingEntity var11 && var11.fallDistance >= 3.0;
         if (!var7
            && (
               var1.is(DamageTypes.FALLING_ANVIL)
                  || var1.is(DamageTypes.SONIC_BOOM)
                  || var1.is(DamageTypes.EXPLOSION)
                  || var1.is(DamageTypes.PLAYER_EXPLOSION)
                  || var10
            )) {
            applyConcussion(var6, true);
         }

         if (var1.is(DamageTypes.IN_WALL)) {
            if (var6.lastBuriedSuffocationTick >= 0 && var6.ticks - var6.lastBuriedSuffocationTick <= 20) {
               var6.buriedSuffocationTicks = var6.buriedSuffocationTicks + Math.max(1, var6.ticks - var6.lastBuriedSuffocationTick);
            } else {
               var6.buriedSuffocationTicks = 0;
            }

            var6.lastBuriedSuffocationTick = var6.ticks;
            if (var6.buriedSuffocationTicks > 60) {
               var6.asphyxia = true;
               var6.asphyxiaFromBurial = true;
            }
         } else if (var6.lastBuriedSuffocationTick >= 0 && var6.ticks - var6.lastBuriedSuffocationTick > 20) {
            var6.buriedSuffocationTicks = 0;
            var6.lastBuriedSuffocationTick = -1;
         }

         if (var1.is(DamageTypes.WIND_CHARGE)) {
            applyWinded(var5, var6);
         }

         if (var14 instanceof LivingEntity bleeder && var3 > 0.0F) {
            Reference sharp = var5.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
            if (EnchantmentHelper.getItemEnchantmentLevel(sharp, bleeder.getMainHandItem()) > 0) {
               Reference prot = var5.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
               int protLevels = 0;

               for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                  protLevels += EnchantmentHelper.getItemEnchantmentLevel(prot, var5.getItemBySlot(slot));
               }

               double bleedChance = Math.max(0.05, HEMORRHAGE_BASE_CHANCE - protLevels * 0.03 - (var5.hasEffect(MobEffects.RESISTANCE) ? 0.03 : 0.0));
               if (Math.random() < bleedChance) {
                  var6.hemorrhage = true;
               }
            }
         }

         if (var14 instanceof LivingEntity var15 && isRustySource(var15) && Math.random() < TETANUS_CHANCE) {
            var6.tetanus = true;
         }

         if (var14 instanceof LivingEntity var16) {
            Reference var12 = var5.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK);
            if (EnchantmentHelper.getItemEnchantmentLevel(var12, var16.getMainHandItem()) >= 2) {
               applyWinded(var5, var6);
            }

            if (var16 instanceof Ravager var13) {
               if (var13.getRoarTick() > 0) {
                  applyWinded(var5, var6);
               } else if (!var7 && var13.getAttackTick() > 0) {
                  var6.fracture = true;
                  var6.stillTicks = 0;
               }
            } else if (!var7 && var16.getType() == EntityTypes.WARDEN) {
               applyConcussion(var6, true);
            } else if (!var7 && var16.getType() == EntityTypes.IRON_GOLEM) {
               applyConcussion(var6, false);
            }
         }

         if (var6.adrenalineRushTicks > 0 && var3 > 0.0F) {
            float var17 = var3 * 0.5F;
            var6.storedAdrenalineDamage += var17;
            var5.heal(var17);
         }
      }
   }

   private static void tickPlayer(ServerPlayer var0) {
      CombatInjuries.InjuryState var1 = state(var0);
      var1.ticks++;
      checkInjuryAchievements(var0, var1);
      if (var1.lastBerryDamageTick >= 0 && var1.ticks - var1.lastBerryDamageTick > 25) {
         var1.berryDamageTicks = 0;
         var1.lastBerryDamageTick = -1;
      }

      if (var1.deepRecoveryTicks > 0) {
         var1.deepRecoveryTicks--;
         var1.deepRecoveryHealTicks++;
         if (var1.deepRecoveryHealTicks >= 20) {
            var1.deepRecoveryHealTicks = 0;
            float var2 = Math.min(1.0F, var0.getMaxHealth() - var0.getHealth());
            if (var2 > 0.0F && var0.getFoodData().getFoodLevel() > 0) {
               var0.heal(var2);
               var0.causeFoodExhaustion(6.0F * var2);
            }
         }
      } else {
         var1.deepRecoveryHealTicks = 0;
      }

      if (var1.lastBuriedSuffocationTick >= 0 && var1.ticks - var1.lastBuriedSuffocationTick > 20) {
         var1.buriedSuffocationTicks = 0;
         var1.lastBuriedSuffocationTick = -1;
      }

      if (var1.asphyxia && var1.asphyxiaFromBurial && var1.lastBuriedSuffocationTick < 0) {
         var1.asphyxia = false;
         var1.asphyxiaFromBurial = false;
         var1.asphyxiaSawLowAir = false;
         var1.asphyxiaDamageTicks = 0;
         var1.buriedSuffocationTicks = 0;
      }

      if (var0.isSleeping()) {
         var1.sleepTicks++;
      } else {
         var1.sleepTicks = 0;
      }

      float var10 = var0.getHealth();
      float healthGained = var10 - var1.previousHealth;
      if (healthGained >= HEMORRHAGE_CURE_HEAL) {
         if (var1.hemorrhage) {
            grant(var0, "stop_bleeding");
         }

         var1.hemorrhage = false;
      } else if (healthGained > 0.0F && var1.hemorrhage && BLEEDING_BLOCKS_REGEN && !var0.hasEffect(MobEffects.REGENERATION)) {
         var0.setHealth(var1.previousHealth);
         var10 = var1.previousHealth;
      }

      var1.previousHealth = var10;
      if (var0.getAirSupply() <= 0) {
         var1.asphyxia = true;
         var1.asphyxiaSawLowAir = true;
      }

      if (var1.asphyxia && var0.getAirSupply() < var0.getMaxAirSupply()) {
         var1.asphyxiaSawLowAir = true;
      }

      if (var1.asphyxia && var1.asphyxiaSawLowAir && var0.getAirSupply() >= var0.getMaxAirSupply()) {
         var1.asphyxia = false;
         var1.asphyxiaFromBurial = false;
         var1.asphyxiaSawLowAir = false;
         var1.asphyxiaDamageTicks = 0;
      }

      if (var0.hasEffect(MobEffects.REGENERATION)) {
         var1.hemorrhage = false;
         var1.fracture = false;
      }

      if (var1.windedTicks > 0) {
         var1.windedTicks--;
      }

      var1.winded = var1.windedTicks > 0;
      if (var10 <= 4.0F) {
         var1.hysteria = true;
      } else {
         var1.hysteria = false;
      }

      if (var1.hysteria) {
         var1.fracture = false;
         var1.concussionTicks = 0;
      }

      AttributeInstance var3 = var0.getAttribute(Attributes.MOVEMENT_SPEED);
      if (var3 != null) {
         var3.removeModifier(FRACTURE_SPEED_ID);
         var3.removeModifier(RUSH_SPEED_ID);
         var3.removeModifier(CRASH_SPEED_ID);
         var3.removeModifier(STUN_SPEED_ID);
         if (var1.stunTicks > 0) {
            var3.addTransientModifier(new AttributeModifier(STUN_SPEED_ID, -1.0, Operation.ADD_MULTIPLIED_TOTAL));
         }

         if (var1.fracture) {
            var3.addTransientModifier(new AttributeModifier(FRACTURE_SPEED_ID, -0.4, Operation.ADD_MULTIPLIED_TOTAL));
         }

         if (var1.adrenalineRushTicks > 0) {
            var3.addTransientModifier(new AttributeModifier(RUSH_SPEED_ID, RUSH_SPEED_BONUS, Operation.ADD_MULTIPLIED_TOTAL));
         } else if (var1.adrenalineCrashTicks > 0) {
            var3.addTransientModifier(new AttributeModifier(CRASH_SPEED_ID, -CRASH_SPEED_PENALTY * (0.35 + 0.65 * var1.adrenalineCrashTicks / 300.0), Operation.ADD_MULTIPLIED_TOTAL));
         }
      }

      AttributeInstance var4 = var0.getAttribute(Attributes.ATTACK_DAMAGE);
      if (var4 != null) {
         var4.removeModifier(RUSH_DAMAGE_ID);
         var4.removeModifier(HYSTERIA_DAMAGE_ID);
         if (var1.adrenalineRushTicks > 0) {
            var4.addTransientModifier(new AttributeModifier(RUSH_DAMAGE_ID, 0.25, Operation.ADD_MULTIPLIED_TOTAL));
         }

         if (var1.hysteria) {
            var4.addTransientModifier(new AttributeModifier(HYSTERIA_DAMAGE_ID, 0.5, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }

      AttributeInstance var5 = var0.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
      if (var5 != null) {
         var5.removeModifier(HYSTERIA_KNOCKBACK_ID);
         if (var1.hysteria) {
            var5.addTransientModifier(new AttributeModifier(HYSTERIA_KNOCKBACK_ID, 1.0, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }

      float crashWeight = var1.adrenalineCrashTicks > 0 ? 0.35F + 0.65F * var1.adrenalineCrashTicks / 300.0F : 0.0F;
      AttributeInstance crashBreak = var0.getAttribute(Attributes.BLOCK_BREAK_SPEED);
      if (crashBreak != null) {
         crashBreak.removeModifier(CRASH_FATIGUE_ID);
         if (crashWeight > 0.0F) {
            crashBreak.addTransientModifier(new AttributeModifier(CRASH_FATIGUE_ID, -0.5 * crashWeight, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }

      AttributeInstance crashSwing = var0.getAttribute(Attributes.ATTACK_SPEED);
      if (crashSwing != null) {
         crashSwing.removeModifier(CRASH_ATTACK_SPEED_ID);
         if (crashWeight > 0.0F) {
            crashSwing.addTransientModifier(new AttributeModifier(CRASH_ATTACK_SPEED_ID, -0.35 * crashWeight, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }

      AttributeInstance crashJump = var0.getAttribute(Attributes.JUMP_STRENGTH);
      if (crashJump != null) {
         crashJump.removeModifier(CRASH_JUMP_ID);
         if (crashWeight > 0.0F) {
            crashJump.addTransientModifier(new AttributeModifier(CRASH_JUMP_ID, -0.3 * crashWeight, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }

      if (var1.hemorrhage && !var0.isSleeping()) {
         var1.bleedTicks++;
         int var6 = !var0.isSprinting() && !(var0.getDeltaMovement().horizontalDistance() > 0.01) ? 20 : 10;
         if (var1.bleedTicks >= var6) {
            ServerLevel var7 = var0.level();
            if (var0.hurtServer(var7, injuryDamageSource(var7, HEMORRHAGE_DAMAGE_TYPE), 1.0F)) {
               var1.bleedTicks = 0;
            }
         }
      }

      if (var1.tetanus && var1.tetanusCooldown > 0) {
         var1.tetanusCooldown--;
      }

      if (var1.tetanus && var1.tetanusCooldown == 0) {
         ItemStack var11 = var0.getItemInHand(InteractionHand.MAIN_HAND);
         ItemStack var15 = var0.getItemInHand(InteractionHand.OFF_HAND);
         InteractionHand var8 = var11.isEmpty()
            ? InteractionHand.OFF_HAND
            : (var15.isEmpty() ? InteractionHand.MAIN_HAND : (var1.tetanusDropOffHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
         ItemStack var9 = var0.getItemInHand(var8);
         if (!var9.isEmpty() && var0.drop(var9.copy(), true) != null) {
            var0.setItemInHand(var8, ItemStack.EMPTY);
            var1.tetanusDropOffHand = var8 == InteractionHand.MAIN_HAND;
            var1.tetanusCooldown = 300;
         } else {
            var1.tetanusCooldown = 20;
         }
      }

      if (var1.concussionTicks > 0) {
         var1.concussionTicks--;
      }

      if (var1.winded) {
         var0.causeFoodExhaustion(0.02F);
      }

      if (var1.asphyxia) {
         var1.asphyxiaDamageTicks++;
         if (var1.asphyxiaDamageTicks >= 20) {
            ServerLevel var12 = var0.level();
            if (var0.hurtServer(var12, injuryDamageSource(var12, ASPHYXIA_DAMAGE_TYPE), 1.0F)) {
               var1.asphyxiaDamageTicks = 0;
            }
         }
      }

      if (var1.stunTicks > 0) {
         var1.stunTicks--;
      }

      if (var1.shotLockoutTicks > 0) {
         var1.shotLockoutTicks--;
      }

      if (var1.adrenalineRushTicks > 0) {
         var1.adrenalineRushTicks--;
         if (var1.adrenalineRushTicks == 0) {
            endRush(var0, var1, var1.rushShots >= AdrenalineRules.MAX_SHOTS);
         }
      } else if (var1.adrenalineCrashTicks > 0) {
         var1.adrenalineCrashTicks--;
         var0.causeFoodExhaustion(0.04F);
         if (var1.adrenalineCrashTicks == 0 && var0.isAlive()) {
            grant(var0, "comedown");
            if (var1.crashFromShots >= 3) {
               grant(var0, "paid_in_full");
            }
         }
      }

      float var14 = var0.getYRot();
      float var16 = var0.getXRot();
      boolean var17 = var1.hasRotationSnapshot && (var14 != var1.lastStillYaw || var16 != var1.lastStillPitch);
      if (var1.fracture && var0.onGround() && !var0.isSprinting() && var0.getDeltaMovement().horizontalDistance() < 0.001 && !var17) {
         var1.stillTicks++;
         if (var1.stillTicks >= FRACTURE_STILL_TICKS) {
            var1.fracture = false;
            var1.stillTicks = 0;
         }
      } else {
         var1.stillTicks = 0;
      }

      var1.lastStillYaw = var14;
      var1.lastStillPitch = var16;
      var1.hasRotationSnapshot = true;
      if (var0.hasEffect(MobEffects.RESISTANCE)) {
         var1.hasResistance = true;
      } else {
         var1.hasResistance = false;
      }

      syncEffect(var0, HEMORRHAGE_EFFECT, var1.hemorrhage, -1);
      syncEffect(var0, TETANUS_EFFECT, var1.tetanus, -1);
      syncEffect(var0, FRACTURE_EFFECT, var1.fracture, -1);
      syncEffect(var0, CONCUSSION_EFFECT, var1.concussionTicks > 0, var1.concussionTicks, var1.concussionHeavy ? 1 : 0);
      syncEffect(var0, ASPHYXIA_EFFECT, var1.asphyxia, -1);
      syncEffect(var0, WINDED_EFFECT, var1.winded, var1.windedTicks);
      syncEffect(var0, HYSTERIA_EFFECT, var1.hysteria, -1);
      syncEffect(var0, ADRENALINE_RUSH_EFFECT, var1.adrenalineRushTicks > 0, var1.adrenalineRushTicks, Math.max(0, var1.rushShots - 1));
      syncEffect(var0, STUN_EFFECT, var1.stunTicks > 0, var1.stunTicks);
      syncEffect(var0, ADRENALINE_CRASH_EFFECT, var1.adrenalineCrashTicks > 0, var1.adrenalineCrashTicks);
   }

   private static SoundEvent registerSound(String name) {
      Identifier id = Identifier.fromNamespaceAndPath("combatinjuries", name);
      return (SoundEvent)Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
   }

   private static Holder<MobEffect> registerEffect(String var0, MobEffectCategory var1, int var2) {
      ResourceKey var3 = ResourceKey.create(Registries.MOB_EFFECT, Identifier.fromNamespaceAndPath("combatinjuries", var0));
      return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, var3, new CombatInjuries.InjuryStatusEffect(var1, var2));
   }

   private static ResourceKey<DamageType> damageTypeKey(String var0) {
      return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("combatinjuries", var0));
   }

   private static DamageSource injuryDamageSource(ServerLevel var0, ResourceKey<DamageType> var1) {
      return new DamageSource(var0.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(var1));
   }

   private static void syncEffect(ServerPlayer var0, Holder<MobEffect> var1, boolean var2, int var3) {
      syncEffect(var0, var1, var2, var3, 0);
   }

   /** Light concussion lasts 6 s, heavy 15 s. A light hit never shortens or downgrades a heavy one. */
   public static final int CONCUSSION_LIGHT_TICKS = 120;
   public static final int CONCUSSION_HEAVY_TICKS = 300;

   /** Gives a mod advancement (data/combatinjuries/advancement/<id>.json). Safe to call repeatedly: vanilla ignores an advancement already earned. */
   private static void grant(ServerPlayer player, String id) {
      try {
         var holder = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("combatinjuries", id));
         if (holder != null) {
            player.getAdvancements().award(holder, "done");
         }
      } catch (RuntimeException ignored) {
      }
   }

   /** Called every tick: awards the injury achievements the first time each status is seen, and "all injuries" once all seven were seen in one life. */
   private static void checkInjuryAchievements(ServerPlayer var0, CombatInjuries.InjuryState var1) {
      int seen = var1.seenMask;
      if (var1.hemorrhage) {
         seen |= 1;
      }
      if (var1.tetanus) {
         seen |= 2;
      }
      if (var1.concussionTicks > 0) {
         seen |= 4;
      }
      if (var1.fracture) {
         seen |= 8;
      }
      if (var1.winded) {
         seen |= 16;
      }
      if (var1.asphyxia) {
         seen |= 32;
      }
      if (var1.hysteria) {
         seen |= 64;
      }

      if (var1.concussionTicks > 0 && var1.concussionHeavy) {
         grant(var0, "heavy_concussion");
      }

      if (var1.asphyxia && var1.asphyxiaFromBurial) {
         grant(var0, "dug_grave");
      }

      int activeCount = (var1.hemorrhage ? 1 : 0) + (var1.tetanus ? 1 : 0) + (var1.concussionTicks > 0 ? 1 : 0) + (var1.fracture ? 1 : 0)
         + (var1.winded ? 1 : 0) + (var1.asphyxia ? 1 : 0) + (var1.hysteria ? 1 : 0);
      if (activeCount >= 3) {
         grant(var0, "walking_disaster");
      }

      if (var1.hysteriaPrev && !var1.hysteria) {
         grant(var0, "back_to_reality");
      }

      var1.hysteriaPrev = var1.hysteria;
      if (seen != var1.seenMask) {
         int added = seen & ~var1.seenMask;
         var1.seenMask = seen;
         String[] ids = {"hemorrhage", "tetanus", "concussion", "fracture", "winded", "asphyxia", "hysteria"};
         for (int i = 0; i < ids.length; i++) {
            if ((added & (1 << i)) != 0) {
               grant(var0, ids[i]);
            }
         }

         if (seen == 127) {
            grant(var0, "all_injuries");
         }
      }

      if (++var1.shotCheckTicks >= 40) {
         var1.shotCheckTicks = 0;
         if (var0.getInventory().contains(stack -> stack.is(ADRENALINE_SHOT))) {
            grant(var0, "obtain_shot");
         }

         if (var0.getInventory().contains(stack -> stack.is(SYRINGE))) {
            grant(var0, "needle_work");
         }

         if (var0.getInventory().contains(stack -> stack.is(ADRENALINE_SHOT) && stack.getCount() >= 16)) {
            grant(var0, "bulk_order");
         }
      }
   }

   private static void applyConcussion(CombatInjuries.InjuryState var0, boolean heavy) {
      if (heavy) {
         var0.concussionTicks = CONCUSSION_HEAVY_TICKS;
         var0.concussionHeavy = true;
      } else if (var0.concussionTicks <= 0) {
         var0.concussionTicks = CONCUSSION_LIGHT_TICKS;
         var0.concussionHeavy = false;
      } else if (!var0.concussionHeavy) {
         var0.concussionTicks = Math.max(var0.concussionTicks, CONCUSSION_LIGHT_TICKS);
      }
   }

   private static void syncEffect(ServerPlayer var0, Holder<MobEffect> var1, boolean var2, int var3, int amp) {
      MobEffectInstance var4 = var0.getEffect(var1);
      if (!var2 || var4 != null && var4.getAmplifier() == amp && (var3 == -1 || var3 <= var4.getDuration() + 1) && (var3 != -1 || var4.isInfiniteDuration())) {
         if (!var2) {
            var0.removeEffect(var1);
         }
      } else {
         var0.addEffect(new MobEffectInstance(var1, var3, amp, false, false, true));
      }
   }

   private static boolean hasRustyMarker(ItemStack var0) {
      CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      return var1 != null && var1.copyTag().getBoolean("combatinjuries_rusty").orElse(false);
   }

   private static boolean isBuriedInSandOrGravel(ServerPlayer var0) {
      BlockPos var1 = var0.blockPosition();
      return isSandOrGravel(var0.level().getBlockState(var1)) || isSandOrGravel(var0.level().getBlockState(var1.above()));
   }

   private static boolean isSandOrGravel(BlockState var0) {
      return var0.is(Blocks.SAND) || var0.is(Blocks.GRAVEL);
   }

   private static void applyWinded(ServerPlayer var0, CombatInjuries.InjuryState var1) {
      short var2 = 200;
      if (var0.hasEffect(MobEffects.RESISTANCE)
         || var0.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.NETHERITE_HELMET
            && var0.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.NETHERITE_CHESTPLATE
            && var0.getItemBySlot(EquipmentSlot.LEGS).getItem() == Items.NETHERITE_LEGGINGS
            && var0.getItemBySlot(EquipmentSlot.FEET).getItem() == Items.NETHERITE_BOOTS) {
         var2 /= 2;
      }

      var1.windedTicks = Math.max(var1.windedTicks, var2);
   }

   private static boolean isRustySource(LivingEntity attacker) {
      ItemStack held = attacker.getMainHandItem();
      if (hasRustyMarker(held) || isWornMetalTool(held)) {
         return true;
      }

      if (attacker instanceof Zombie) {
         Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
         return id != null && id.getPath().startsWith("iron_") && !isIronArmor(id.getPath());
      }

      return false;
   }

   private static final float RUST_DAMAGE_FRACTION = 0.6F;

   private static boolean isWornMetalTool(ItemStack stack) {
      if (!stack.isDamageableItem()) {
         return false;
      }

      Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      if (id == null) {
         return false;
      }

      String path = id.getPath();
      boolean metal = path.startsWith("iron_") || path.startsWith("copper_");
      boolean tool = path.endsWith("_sword") || path.endsWith("_axe") || path.endsWith("_pickaxe") || path.endsWith("_shovel") || path.endsWith("_hoe") || path.endsWith("_spear");
      return metal && tool && stack.getDamageValue() >= stack.getMaxDamage() * RUST_DAMAGE_FRACTION;
   }

   private static boolean isCopperTool(ItemStack var0) {
      Item var1 = var0.getItem();
      return var1 == Items.COPPER_SWORD || var1 == Items.COPPER_AXE || var1 == Items.COPPER_PICKAXE || var1 == Items.COPPER_SHOVEL || var1 == Items.COPPER_HOE;
   }

   private static ItemStack rustyCreativeStack(Item var0, String var1) {
      ItemStack var2 = new ItemStack(var0);
      CompoundTag var3 = new CompoundTag();
      var3.putBoolean("combatinjuries_rusty", true);
      var2.set(DataComponents.CUSTOM_DATA, CustomData.of(var3));
      Identifier toolId = BuiltInRegistries.ITEM.getKey(var0);
      if (toolId != null && RUSTY_TOOL_PATHS.contains(toolId.getPath())) {
         var2.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("combatinjuries", "rusty_" + toolId.getPath()));
      }

      var2.set(DataComponents.CUSTOM_NAME, Component.translatable(var1));
      return var2;
   }

   private static CombatInjuries.InjuryState state(ServerPlayer var0) {
      return STATES.computeIfAbsent(var0.getUUID(), var1 -> new CombatInjuries.InjuryState(var0.getHealth()));
   }

   public static void cureTetanus(ServerPlayer var0) {
      CombatInjuries.InjuryState var1 = state(var0);
      if (var1.tetanus) {
         grant(var0, "got_milk");
      }

      var1.tetanus = false;
      var1.tetanusCooldown = 0;
      var1.tetanusDropOffHand = false;
   }

   public static boolean hasInjury(Player player, String id) {
      CombatInjuries.InjuryState st = STATES.get(player.getUUID());
      if (st == null) {
         return false;
      }

      return switch (id) {
         case "hemorrhage" -> st.hemorrhage;
         case "tetanus" -> st.tetanus;
         case "concussion" -> st.concussionTicks > 0;
         case "fracture" -> st.fracture;
         case "winded" -> st.winded;
         case "asphyxia" -> st.asphyxia;
         case "hysteria" -> st.hysteria;
         default -> false;
      };
   }

   public static int rushShotsOf(Player player) {
      CombatInjuries.InjuryState st = STATES.get(player.getUUID());
      return st != null && st.adrenalineRushTicks > 0 ? st.rushShots : 0;
   }

   public static boolean isStunned(Player var0) {
      CombatInjuries.InjuryState var1 = STATES.get(var0.getUUID());
      return var1 != null && var1.stunTicks > 0;
   }

   public static boolean isFractured(Player var0) {
      CombatInjuries.InjuryState var1 = STATES.get(var0.getUUID());
      return var1 != null && var1.fracture;
   }

   private static final class InjuryState {
      private boolean hemorrhage;
      private boolean tetanus;
      private boolean fracture;
      private boolean asphyxia;
      private boolean asphyxiaSawLowAir;
      private boolean asphyxiaFromBurial;
      private int sleepTicks;
      private boolean winded;
      private boolean hysteria;
      private boolean hasResistance;
      private int concussionTicks;
      private boolean concussionHeavy;
      private int rushShots;
      private int seenMask;
      private int crashFromShots;
      private boolean hysteriaPrev;
      private int shotCheckTicks;
      private int stunTicks;
      private int shotLockoutTicks;
      private int asphyxiaDamageTicks;
      private int buriedSuffocationTicks;
      private int lastBuriedSuffocationTick = -1;
      private int windedTicks;
      private int adrenalineRushTicks;
      private int adrenalineCrashTicks;
      private float storedAdrenalineDamage;
      private int tetanusCooldown;
      private boolean tetanusDropOffHand;
      private int bleedTicks;
      private int ticks;
      private int berryDamageTicks;
      private int lastBerryDamageTick = -1;
      private int stillTicks;
      private int deepRecoveryTicks;
      private int deepRecoveryHealTicks;
      private boolean deepSleepQualified;
      private float lastStillYaw;
      private float lastStillPitch;
      private boolean hasRotationSnapshot;
      private float previousHealth;

      private InjuryState(float var1) {
         this.previousHealth = var1;
      }
   }

   private static final class InjuryStatusEffect extends MobEffect {
      private InjuryStatusEffect(MobEffectCategory var1, int var2) {
         super(var1, var2);
      }
   }
}
