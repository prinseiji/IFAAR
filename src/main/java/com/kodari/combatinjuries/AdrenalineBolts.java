package com.kodari.combatinjuries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The Adrenaline Bolt.
 *
 * The bolt is a plain item in the minecraft:arrows tag, so crossbows and bows accept it and fire an
 * ordinary arrow whose pickup item is the bolt. When that arrow damages a mob, the mob is injected.
 * Part of IFAAR: also bursts a player into red mist when an IFAAR overdose kills them.
 */
public final class AdrenalineBolts {
   public static final String MOD_ID = "combatinjuries";
   /** Mobs with this much max health or less burst almost at once. */
   private static final float WEAK_MAX_HEALTH = 16.0F;
   private static final int WEAK_TICKS = 30;
   private static final int STRONG_TICKS = 120;
   private static final Set<String> IMMUNE = Set.of("warden", "wither", "ender_dragon");
   /** Large-health animals that should still count as weak. */
   private static final Set<String> ALWAYS_WEAK = Set.of("wolf");

   private static final ResourceKey<Item> BOLT_KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "adrenaline_bolt"));
   public static final Item ADRENALINE_BOLT = Registry.register(BuiltInRegistries.ITEM, BOLT_KEY, new Item(new Properties().setId(BOLT_KEY).stacksTo(16)));
   private static final ResourceKey<DamageType> IFAAR_OVERDOSE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("combatinjuries", "adrenaline_overdose"));

   private static final class Rush {
      final Mob mob;
      final ServerLevel level;
      final UUID shooter;
      final boolean strong;
      int ticksLeft;
      final int totalTicks;

      Rush(Mob mob, ServerLevel level, UUID shooter, boolean strong, int ticks) {
         this.mob = mob;
         this.level = level;
         this.shooter = shooter;
         this.strong = strong;
         this.ticksLeft = ticks;
         this.totalTicks = ticks;
      }
   }

   private static final List<Rush> RUSHES = new ArrayList<>();

   /** Called from CombatInjuries.onInitialize(). */
   public static void init() {
      CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities")))
         .register(output -> output.accept(ADRENALINE_BOLT));

      ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
         if (source.getDirectEntity() instanceof AbstractArrow arrow && entity instanceof Mob mob && mob.level() instanceof ServerLevel level) {
            if (((com.kodari.combatinjuries.mixin.AbstractArrowAccessor)(Object)arrow).ifaar$getPickupItem().is(ADRENALINE_BOLT)) {
               inject(mob, level, arrow.getOwner());
               arrow.discard();
               return false;
            }
         }

         return true;
      });

      ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
         if (dead instanceof ServerPlayer player && source.is(IFAAR_OVERDOSE) && player.level() instanceof ServerLevel level) {
            burst(level, player, 1.0F);
         }
      });

      ServerTickEvents.END_SERVER_TICK.register(server -> {
         tickRushes(server);
         if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
               if (holdsBolt(p)) {
                  grant(p, "bolt_get");
               }
            }
         }
      });
   }

   private static boolean holdsBolt(ServerPlayer player) {
      Inventory inv = player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         if (inv.getItem(i).is(ADRENALINE_BOLT)) {
            return true;
         }
      }

      return false;
   }

   private static String idOf(Entity entity) {
      Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      return id == null ? "" : id.getPath();
   }

   private static void inject(Mob mob, ServerLevel level, Entity owner) {
      String id = idOf(mob);
      if (IMMUNE.contains(id)) {
         return;
      }

      for (Rush r : RUSHES) {
         if (r.mob == mob) {
            return;
         }
      }

      boolean strong = mob.getMaxHealth() > WEAK_MAX_HEALTH && !ALWAYS_WEAK.contains(id);
      UUID shooter = owner == null ? null : owner.getUUID();
      mob.setSilent(true);
      // Invisible marker effect: the client reads it (and its remaining time) to flash the mob red. Amplifier 1 = strong mob.
      mob.addEffect(new MobEffectInstance(CombatInjuries.ADRENALINE_RUSH_EFFECT, (strong ? STRONG_TICKS : WEAK_TICKS) + 5, strong ? 1 : 0, false, false, false));
      if (strong) {
         mob.addEffect(new MobEffectInstance(MobEffects.SPEED, STRONG_TICKS, 2));
         mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, STRONG_TICKS, 1));
         if (owner instanceof LivingEntity target) {
            mob.setTarget(target);
         }
      } else {
         mob.setNoAi(true);
      }

      RUSHES.add(new Rush(mob, level, shooter, strong, strong ? STRONG_TICKS : WEAK_TICKS));
   }

   private static void tickRushes(MinecraftServer server) {
      Iterator<Rush> it = RUSHES.iterator();
      while (it.hasNext()) {
         Rush r = it.next();
         if (r.mob.isRemoved() || !r.mob.isAlive()) {
            it.remove();
            continue;
         }

         r.ticksLeft--;
         if (r.ticksLeft <= 0) {
            it.remove();
            burst(r.level, r.mob, 1.0F);
            r.mob.discard();
            if (r.shooter != null) {
               ServerPlayer p = server.getPlayerList().getPlayer(r.shooter);
               if (p != null) {
                  grant(p, r.strong ? "overclocked" : "burst");
                  if (r.strong) {
                     grant(p, "burst");
                  }
               }
            }

            continue;
         }
      }
   }

   /** Gore everywhere, ULTRAKILL style: a hard spray of blood, flying chunks of meat and bone, and a lingering red cloud. */
   private static void burst(ServerLevel level, Entity e, float scale) {
      float w = e.getBbWidth();
      float h = e.getBbHeight();
      float size = Math.max(0.3F, w * h);
      int count = Math.min(400, (int)((60 + 110 * size) * scale));
      double x = e.getX();
      double y = e.getY() + h * 0.5;
      double z = e.getZ();
      // Hard spray of blood in every direction.
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y, z, count, w * 0.3, h * 0.3, w * 0.3, 0.9);
      // Upward geyser.
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y, z, count / 3, w * 0.1, h * 0.8, w * 0.1, 0.5);
      // Chunks of flesh and bone thrown far.
      Item[] gibs = {Items.BEEF, Items.PORKCHOP, Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.BONE, Items.MUTTON};
      int perGib = Math.max(4, count / 14);
      for (Item gib : gibs) {
         level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, gib), x, y, z, perGib, w * 0.25, h * 0.25, w * 0.25, 0.7);
      }

      // Slow dark-red cloud that hangs in the air.
      level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()), x, y, z, count, w * 0.5, h * 0.5, w * 0.5, 0.12);
      level.playSound(null, x, y, z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
      level.playSound(null, x, y, z, SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 1.4F, 0.4F);
      level.playSound(null, x, y, z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.2F, 0.35F);
   }

   private static void grant(ServerPlayer player, String id) {
      try {
         var holder = player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath(MOD_ID, id));
         if (holder != null) {
            player.getAdvancements().award(holder, "done");
         }
      } catch (RuntimeException ignored) {
      }
   }
}
