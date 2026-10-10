package com.kodari.combatinjuries.client;

import com.kodari.combatinjuries.CombatInjuries;
import com.kodari.combatinjuries.client.mixin.GameRendererAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.AudioFormat.Encoding;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.client.renderer.RenderPipelines;
import com.kodari.combatinjuries.AdrenalineRules;

public final class CombatInjuriesClient implements ClientModInitializer {
   private static final String ADRENALINE_WAV_RESOURCE = "assets/combatinjuries/sounds/adrenaline_music/adrenaline.wav";
   private static final long HYSTERIA_FILTER_FADE_IN_MS = 1600L;
   private static final long HYSTERIA_FILTER_FADE_OUT_MS = 5000L;
   private static final long ADRENALINE_FILTER_FADE_OUT_MS = 2200L;
   private static final long ADRENALINE_CRASH_FILTER_FADE_IN_MS = 900L;
   private static final long ADRENALINE_CRASH_FILTER_FADE_OUT_MS = 3500L;
   private static boolean concussionWasActive;
   private static boolean hysteriaWasActive;
   private static boolean hemorrhageWasActive;
   private static boolean tetanusWasActive;
   private static boolean fractureWasActive;
   private static boolean asphyxiaWasActive;
   private static boolean windedWasActive;
   private static boolean adrenalineRushWasActive;
   private static int lastRushShots;
   private static boolean qteWindowWasOpen;
   private static long finalFlashAt;
   private static final long[] POPUP_BORN = new long[3];
   private static final int[] POPUP_X = new int[3];
   private static final int[] POPUP_Y = new int[3];
   private static final int[] POPUP_IMG = new int[3];
   private static long popupNextAt;
   private static final int[][] POPUP_SIZE = {{229, 83}, {221, 83}, {198, 83}, {150, 83}, {272, 83}, {279, 83}};
   private static boolean adrenalineCrashWasActive;
   private static long concussionStartedAt;
   private static long hysteriaStartedAt;
   private static long hysteriaFilterEndedAt;
   private static long concussionSoundAt;
   private static long hysteriaSoundAt;
   private static long adrenalineRushStartedAt;
   private static long adrenalineFilterEndedAt;
   private static long adrenalineCrashStartedAt;
   private static long adrenalineCrashFilterEndedAt;
   private static final float CRASH_TOTAL_TICKS = 300.0F;
   private static final SoundEvent[] PHANTOM_SOUNDS = new SoundEvent[]{
      SoundEvents.ZOMBIE_AMBIENT,
      SoundEvents.SKELETON_AMBIENT,
      SoundEvents.CREEPER_PRIMED,
      SoundEvents.SPIDER_AMBIENT,
      SoundEvents.ENDERMAN_STARE,
      SoundEvents.WITCH_AMBIENT,
      SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR
   };
   private static long hysteriaPhantomAt;
   private static long hysteriaFlashNextAt;
   private static long hysteriaFlashUntil;
   private static boolean postEffectsBroken;
   private static long adrenalineBeatAt;
   private static long lastHeartbeatAt;
   private static long crashBreathAt;
   private static boolean rushWasOnGround = true;
   private static Identifier activePostEffect;
   private static CombatInjuriesClient.AdrenalineMusicSoundInstance adrenalineMusic;

   public void onInitializeClient() {
      prepareMusicFolder();
      ClientTickEvents.END_CLIENT_TICK.register(CombatInjuriesClient::tickClient);
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("combatinjuries", "injury_overlays"), CombatInjuriesClient::renderOverlays);
   }

   private static void tickClient(Minecraft var0) {
      LocalPlayer var1 = var0.player;
      if (var1 == null) {
         stopAdrenalineMusic(var0);
         if (activePostEffect != null) {
            restoreCameraPostEffect(var0);
         }

         concussionWasActive = false;
         hysteriaWasActive = false;
         hemorrhageWasActive = false;
         tetanusWasActive = false;
         fractureWasActive = false;
         asphyxiaWasActive = false;
         windedWasActive = false;
         adrenalineRushWasActive = false;
         lastRushShots = 0;
         qteWindowWasOpen = false;
         adrenalineCrashWasActive = false;
         concussionSoundAt = 0L;
         hysteriaSoundAt = 0L;
         hysteriaPhantomAt = 0L;
         adrenalineBeatAt = 0L;
         lastHeartbeatAt = 0L;
         crashBreathAt = 0L;
         hysteriaStartedAt = 0L;
         hysteriaFilterEndedAt = 0L;
         adrenalineFilterEndedAt = 0L;
         adrenalineCrashStartedAt = 0L;
         adrenalineCrashFilterEndedAt = 0L;
      } else {
         long var2 = System.currentTimeMillis();
         boolean var4 = var1.hasEffect(CombatInjuries.CONCUSSION_EFFECT);
         boolean var5 = var1.hasEffect(CombatInjuries.HYSTERIA_EFFECT);
         boolean var6 = var1.hasEffect(CombatInjuries.HEMORRHAGE_EFFECT);
         boolean var7 = var1.hasEffect(CombatInjuries.TETANUS_EFFECT);
         boolean var8 = var1.hasEffect(CombatInjuries.FRACTURE_EFFECT);
         boolean var9 = var1.hasEffect(CombatInjuries.ASPHYXIA_EFFECT);
         boolean var10 = var1.hasEffect(CombatInjuries.WINDED_EFFECT);
         boolean var11 = var1.hasEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         boolean var12 = var1.hasEffect(CombatInjuries.ADRENALINE_CRASH_EFFECT);
         if (var4 && !concussionWasActive) {
            concussionStartedAt = var2;
            var0.getSoundManager().stop();
            if (var11 && adrenalineRushWasActive && currentTrack != null) {
               adrenalineMusic = new CombatInjuriesClient.AdrenalineMusicSoundInstance(currentTrack);
               var0.getSoundManager().play(adrenalineMusic);
            }
         }

         if (var4 && var2 - concussionSoundAt >= 1600L) {
            MobEffectInstance concEffect = var1.getEffect(CombatInjuries.CONCUSSION_EFFECT);
            boolean heavyConc = concEffect == null || concEffect.getAmplifier() >= 1;
            playCue(var0, CombatInjuries.CONCUSSION_TINNITUS, heavyConc ? 0.55F : 0.2F, heavyConc ? 1.35F : 1.6F);
            concussionSoundAt = var2;
         }

         if (var5 && !hysteriaWasActive) {
            hysteriaStartedAt = var2;
            hysteriaFilterEndedAt = 0L;
         }

         float hystAge = Math.min(1.0F, (float)(var2 - hysteriaStartedAt) / 20000.0F);
         if (var5 && var2 - hysteriaSoundAt >= (long)(800.0F - 320.0F * hystAge)) {
            playCue(var0, SoundEvents.WARDEN_HEARTBEAT, 0.9F, 0.72F);
            hysteriaSoundAt = var2;
         }

         if (var5) {
         if (hysteriaPhantomAt == 0L) {
            hysteriaPhantomAt = var2 + 1500L + ThreadLocalRandom.current().nextInt(3000);
         } else if (var2 >= hysteriaPhantomAt) {
            playPhantomSound(var0);
            hysteriaPhantomAt = var2 + (long)((3000L + ThreadLocalRandom.current().nextInt(9000)) * (1.0F - 0.65F * hystAge));
         }
      } else {
         hysteriaPhantomAt = 0L;
      }

      if (!var5 && hysteriaWasActive) {
            hysteriaFilterEndedAt = var2;
            hysteriaSoundAt = 0L;
         }

         if (var6 && !hemorrhageWasActive) {
            playCue(var0, SoundEvents.PLAYER_HURT, 0.9F, 0.9F);
         }

         if (var7 && !tetanusWasActive) {
            playCue(var0, SoundEvents.SKELETON_HURT, 0.9F, 0.8F);
         }

         if (var8 && !fractureWasActive) {
            playCue(var0, SoundEvents.BONE_BLOCK_BREAK, 0.9F, 0.8F);
         }

         if (var9 && !asphyxiaWasActive) {
            playCue(var0, SoundEvents.PLAYER_HURT_DROWN, 0.95F, 0.8F);
         }

         if (var10 && !windedWasActive) {
            playCue(var0, SoundEvents.PLAYER_BREATH, 0.9F, 0.8F);
         }

         MobEffectInstance qteFx = var1.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         int qteShots = qteFx == null ? 0 : qteFx.getAmplifier() + 1;
         boolean qteOpen = qteFx != null && AdrenalineRules.zoneFor(qteShots, qteFx.getDuration()) == AdrenalineRules.Zone.WINDOW;
         if (qteOpen && !qteWindowWasOpen) {
            playCue(var0, CombatInjuries.ADRENALINE_HEARTBEAT, 1.0F, qteShots >= AdrenalineRules.MAX_SHOTS ? 0.6F : 1.6F);
         }

         int prevRushShots = lastRushShots;
         lastRushShots = qteShots;
         if (qteShots > prevRushShots && prevRushShots > 0) {
            playCue(var0, CombatInjuries.ADRENALINE_VEINS, 1.0F, 1.0F);
         }
         qteWindowWasOpen = qteOpen;
         if (var11 && !adrenalineRushWasActive) {
            adrenalineRushStartedAt = var2;
            veinSeed = ThreadLocalRandom.current().nextLong();
            veinGrowth = 0.0F;
            veinW = 0;
            adrenalineFilterEndedAt = 0L;
            adrenalineCrashFilterEndedAt = 0L;
            playCue(var0, CombatInjuries.ADRENALINE_POWER_UP, 1.0F, 1.0F);
            adrenalineBeatAt = var2 + 700L;
            currentTrack = nextTrack();
            if (currentTrack != null) {
               adrenalineMusic = new CombatInjuriesClient.AdrenalineMusicSoundInstance(currentTrack);
               var0.getSoundManager().play(adrenalineMusic);
            }
         } else if (!var11 && adrenalineRushWasActive) {
            adrenalineFilterEndedAt = var2;
            stopAdrenalineMusic(var0);
            if (prevRushShots >= AdrenalineRules.MAX_SHOTS) {
               finalFlashAt = var2;
               playCue(var0, CombatInjuries.ADRENALINE_POWER_DOWN_GRAND, 1.0F, 1.0F);
            } else {
               playCue(var0, CombatInjuries.ADRENALINE_POWER_DOWN, 1.0F, 1.0F);
            }
         }

         if (var11) {
            if (adrenalineBeatAt > 0L && var2 >= adrenalineBeatAt) {
               playCue(var0, CombatInjuries.ADRENALINE_HEARTBEAT, 0.9F, 1.0F);
               lastHeartbeatAt = var2;
               adrenalineBeatAt = var2 + Math.max(340L, 600L - 80L * Math.max(0, lastRushShots - 1));
            }
         } else {
            adrenalineBeatAt = 0L;
            lastHeartbeatAt = 0L;
         }

         if (var12) {
            if (crashBreathAt == 0L) {
               crashBreathAt = var2 + 1800L;
            } else if (var2 >= crashBreathAt) {
               playCue(var0, SoundEvents.PLAYER_BREATH, 0.8F, 0.6F);
               crashBreathAt = var2 + 2600L;
            }
         } else {
            crashBreathAt = 0L;
         }

         if (var11 && !var1.getAbilities().flying && !var1.isFallFlying() && !var1.isInWater() && !var1.onGround()) {
            net.minecraft.world.phys.Vec3 rushMotion = var1.getDeltaMovement();
            double rushHorizontal = Math.hypot(rushMotion.x, rushMotion.z);
            if (rushHorizontal > 0.05 && rushHorizontal < 0.5) {
               double rushScale = rushWasOnGround ? 1.3 : 1.045;
               var1.setDeltaMovement(rushMotion.x * rushScale, rushMotion.y, rushMotion.z * rushScale);
            }
         }

         rushWasOnGround = var1.onGround();

         if (var12 && !adrenalineCrashWasActive) {
            adrenalineCrashStartedAt = var2;
            adrenalineCrashFilterEndedAt = 0L;
            playCue(var0, SoundEvents.PLAYER_BREATH, 0.8F, 0.65F);
         }

         MobEffectInstance crashEffect = var1.getEffect(CombatInjuries.ADRENALINE_CRASH_EFFECT);
         float crashRemaining = crashEffect == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, crashEffect.getDuration() / CRASH_TOTAL_TICKS));
         updateCameraPostEffect(var0, var5, var11, var12, var2, crashRemaining);
         concussionWasActive = var4;
         hysteriaWasActive = var5;
         hemorrhageWasActive = var6;
         tetanusWasActive = var7;
         fractureWasActive = var8;
         asphyxiaWasActive = var9;
         windedWasActive = var10;
         adrenalineRushWasActive = var11;
         adrenalineCrashWasActive = var12;
         if (!var4) {
            concussionSoundAt = 0L;
         }
      }
   }

   private static void stopAdrenalineMusic(Minecraft var0) {
      if (adrenalineMusic != null) {
         var0.getSoundManager().stop(adrenalineMusic);
         adrenalineMusic = null;
      }
   }

   private static Path musicFolder() {
      return FabricLoader.getInstance().getConfigDir().resolve("ifaar").resolve("adrenaline_music");
   }

   private static void prepareMusicFolder() {
      try {
         Path folder = musicFolder();
         Files.createDirectories(folder);
         Path readme = folder.resolve("README.txt");
         if (!Files.exists(readme)) {
            Files.writeString(
               readme,
               "Drop your own adrenaline rush music here as .wav files (mono or stereo PCM).\n"
                  + "These are ADDED to the built-in tracks. Tracks are shuffled so none repeats until all have played.\n"
                  + "It loops during Adrenaline Rush and stops when the rush ends.\n"
            );
         }
      } catch (IOException var1) {
         System.err.println("[IFAAR] Could not create music folder: " + var1);
      }
   }

   private static final class MusicTrack {
      private final String name;
      private final Path file;
      private final String resource;

      private MusicTrack(String name, Path file, String resource) {
         this.name = name;
         this.file = file;
         this.resource = resource;
      }

      private InputStream open() throws IOException {
         if (this.file != null) {
            return Files.newInputStream(this.file);
         }

         InputStream in = CombatInjuriesClient.class.getClassLoader().getResourceAsStream(this.resource);
         if (in == null) {
            throw new IOException("Missing bundled track " + this.resource);
         }

         return in;
      }
   }

   private static final String MUSIC_BASE = "assets/combatinjuries/sounds/adrenaline_music/";
   private static final java.util.ArrayDeque<MusicTrack> musicBag = new java.util.ArrayDeque<>();
   private static String lastTrackName = "";
   private static MusicTrack currentTrack;

   /** Bundled loops (listed in tracks.txt) plus any .wav the player drops into config/ifaar/adrenaline_music. */
   private static List<MusicTrack> trackPool() {
      List<MusicTrack> pool = new java.util.ArrayList<>();
      try (InputStream in = CombatInjuriesClient.class.getClassLoader().getResourceAsStream(MUSIC_BASE + "tracks.txt")) {
         if (in != null) {
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
               String n = line.trim();
               if (n.toLowerCase(Locale.ROOT).endsWith(".wav")) {
                  pool.add(new MusicTrack(n, null, MUSIC_BASE + n));
               }
            }
         }
      } catch (IOException ignored) {
      }

      Path folder = musicFolder();
      if (Files.isDirectory(folder)) {
         try (java.util.stream.Stream<Path> files = Files.list(folder)) {
            for (Path f : files.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav")).sorted().toList()) {
               pool.add(new MusicTrack("config:" + f.getFileName(), f, null));
            }
         } catch (IOException ignored) {
         }
      }

      return pool;
   }

   /** Shuffle bag: every track plays once before any repeats, and a new round never starts with the track that just played. */
   private static MusicTrack nextTrack() {
      List<MusicTrack> pool = trackPool();
      if (pool.isEmpty()) {
         return null;
      }

      java.util.Set<String> names = new java.util.HashSet<>();
      for (MusicTrack t : pool) {
         names.add(t.name);
      }

      musicBag.removeIf(t -> !names.contains(t.name));
      if (musicBag.isEmpty()) {
         List<MusicTrack> shuffled = new java.util.ArrayList<>(pool);
         int guard = 0;
         do {
            java.util.Collections.shuffle(shuffled);
            guard++;
         } while (shuffled.size() > 1 && shuffled.get(0).name.equals(lastTrackName) && guard < 20);
         musicBag.addAll(shuffled);
      }

      MusicTrack next = musicBag.poll();
      lastTrackName = next.name;
      return next;
   }

   private static void playPhantomSound(Minecraft var0) {
      LocalPlayer player = var0.player;
      if (player != null) {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         double angle = random.nextDouble() * Math.PI * 2.0;
         double distance = 4.0 + random.nextDouble() * 10.0;
         SoundEvent event = PHANTOM_SOUNDS[random.nextInt(PHANTOM_SOUNDS.length)];
         var0.getSoundManager()
            .play(
               new SimpleSoundInstance(
                  event,
                  SoundSource.HOSTILE,
                  0.7F + random.nextFloat() * 0.4F,
                  0.8F + random.nextFloat() * 0.4F,
                  RandomSource.create(),
                  player.getX() + Math.cos(angle) * distance,
                  player.getY(),
                  player.getZ() + Math.sin(angle) * distance
               )
            );
      }
   }

   private static void updateCameraPostEffect(Minecraft var0, boolean var1, boolean var2, boolean var3, long var4, float crashRemaining) {
      if (var1) {
         float var12 = (float)(var4 - hysteriaStartedAt - 320L);
         float var7 = Math.max(0.0F, Math.min(1.0F, var12 / 1600.0F));
         setCameraPostEffect(var0, "hysteria_monochrome_" + Math.round(var7 * 10.0F));
      } else if (var2) {
         int chainLevel = 5;
         MobEffectInstance chainFx = var0.player == null ? null : var0.player.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         if (chainFx != null) {
            int cShots = chainFx.getAmplifier() + 1;
            float cProgress = Math.max(0.0F, Math.min(0.999F, 1.0F - chainFx.getDuration() / (float)AdrenalineRules.segmentTicks(cShots)));
            chainLevel = Math.min(12, (cShots - 1) * 3 + (int)(3.0F * cProgress));
         }

         setCameraPostEffect(var0, "adrenaline_chain_" + chainLevel);
      } else if (var3) {
         float var11 = Math.max(0.0F, Math.min(1.0F, (float)(var4 - adrenalineCrashStartedAt) / 900.0F));
         float crashLevel = Math.min(var11, crashRemaining);
         setCameraPostEffect(var0, "adrenaline_crash_monochrome_" + Math.round(crashLevel * 10.0F));
      } else {
         if (hysteriaFilterEndedAt > 0L) {
            long var6 = var4 - hysteriaFilterEndedAt;
            if (var6 < 5000L) {
               float var14 = 1.0F - (float)var6 / 5000.0F;
               setCameraPostEffect(var0, "hysteria_monochrome_" + Math.round(var14 * 10.0F));
               return;
            }

            hysteriaFilterEndedAt = 0L;
         }

         if (adrenalineCrashFilterEndedAt > 0L) {
            long var9 = var4 - adrenalineCrashFilterEndedAt;
            if (var9 < 3500L) {
               float var13 = 1.0F - (float)var9 / 3500.0F;
               setCameraPostEffect(var0, "adrenaline_crash_monochrome_" + Math.round(var13 * 10.0F));
               return;
            }

            adrenalineCrashFilterEndedAt = 0L;
         }

         if (adrenalineFilterEndedAt > 0L) {
            long var10 = var4 - adrenalineFilterEndedAt;
            if (var10 < 2200L) {
               float var8 = 1.0F - (float)var10 / 2200.0F;
               setCameraPostEffect(var0, "adrenaline_monochrome_" + Math.round(var8 * 5.0F));
               return;
            }

            adrenalineFilterEndedAt = 0L;
         }

         restoreCameraPostEffect(var0);
      }
   }

   private static void setCameraPostEffect(Minecraft var0, String var1) {
      Identifier var2 = Identifier.fromNamespaceAndPath("combatinjuries", var1);
      if (!var2.equals(activePostEffect) && !postEffectsBroken) {
         try {
            ((GameRendererAccessor)var0.gameRenderer).ifaar$setPostEffect(var2);
            activePostEffect = var2;
         } catch (RuntimeException var3) {
            postEffectsBroken = true;
            activePostEffect = null;
            System.err.println("[IFAAR] Screen filter '" + var2 + "' failed to load; filters disabled for this session: " + var3);
         }
      }
   }

   private static void restoreCameraPostEffect(Minecraft var0) {
      if (activePostEffect != null) {
         var0.gameRenderer.checkEntityPostEffect(var0.options.getCameraType().isFirstPerson() ? var0.getCameraEntity() : null);
         activePostEffect = null;
      }
   }

   private static void playCue(Minecraft var0, SoundEvent var1, float var2, float var3) {
      LocalPlayer var4 = var0.player;
      var0.getSoundManager().play(new SimpleSoundInstance(var1, SoundSource.PLAYERS, var2, var3, RandomSource.create(), var4.getX(), var4.getY(), var4.getZ()));
   }

   /**
    * Annoying fake pop-up windows from the third shot on. They stay on the left/center of the screen and are drawn
    * BEFORE the timing bar, and never in the right-hand strip where the bar lives, so they can never hide it.
    */
   /** Pop-ups are drawn smaller on bigger-looking screens: about a fifth of the screen width at most. */
   private static float popupScale(int screenWidth) {
      return Math.max(0.2F, Math.min(0.7F, screenWidth * 0.2F / 279.0F));
   }

   private static final class VeinPath {
      float[] xs;
      float[] ys;
      float startAt;
      int minStack;
      boolean branch;
   }

   private static final java.util.ArrayList<VeinPath> VEINS = new java.util.ArrayList<>();
   private static long veinSeed;
   private static int veinW;
   private static int veinH;
   private static float veinGrowth;

   private static VeinPath traceVein(java.util.Random r, float x, float y, float heading, float length, float startAt, int minStack, boolean branch) {
      int n = Math.max(4, (int)(length / 6.0F));
      VeinPath v = new VeinPath();
      v.xs = new float[n];
      v.ys = new float[n];
      v.startAt = startAt;
      v.minStack = minStack;
      v.branch = branch;
      for (int i = 0; i < n; i++) {
         v.xs[i] = x;
         v.ys[i] = y;
         heading += (float)(r.nextGaussian() * 0.22);
         x += (float)Math.cos(heading) * 6.0F;
         y += (float)Math.sin(heading) * 6.0F;
      }

      return v;
   }

   private static void buildVeins(int w, int h, long seed) {
      VEINS.clear();
      java.util.Random r = new java.util.Random(seed);
      for (int i = 0; i < 17; i++) {
         float x;
         float y;
         if (i < 4) {
            x = (i & 1) == 0 ? -4.0F : w + 4.0F;
            y = (i & 2) == 0 ? -4.0F : h + 4.0F;
         } else {
            int edge = r.nextInt(4);
            if (edge == 0) {
               x = r.nextFloat() * w;
               y = -4.0F;
            } else if (edge == 1) {
               x = r.nextFloat() * w;
               y = h + 4.0F;
            } else if (edge == 2) {
               x = -4.0F;
               y = r.nextFloat() * h;
            } else {
               x = w + 4.0F;
               y = r.nextFloat() * h;
            }
         }

         float heading = (float)Math.atan2(h / 2.0F - y, w / 2.0F - x) + (float)(r.nextGaussian() * 0.25);
         float length = Math.min(w, h) * 0.62F * (0.65F + 0.5F * r.nextFloat());
         int minStack = i < 8 ? 1 : (i < 13 ? 2 : 3);
         VeinPath main = traceVein(r, x, y, heading, length, 0.0F, minStack, false);
         VEINS.add(main);
         for (int b = 0; b < 2; b++) {
            int at = (int)(main.xs.length * (0.3F + 0.3F * r.nextFloat()));
            at = Math.max(1, Math.min(main.xs.length - 1, at));
            float side = r.nextBoolean() ? 1.0F : -1.0F;
            float bh = (float)Math.atan2(main.ys[at] - main.ys[at - 1], main.xs[at] - main.xs[at - 1]) + side * (0.6F + 0.5F * r.nextFloat());
            VEINS.add(traceVein(r, main.xs[at], main.ys[at], bh, length * 0.4F, b == 0 ? 0.2F : 0.45F, minStack, true));
         }
      }
   }

   private static void renderVeins(GuiGraphicsExtractor g, int w, int h, long now) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      MobEffectInstance rush = player == null ? null : player.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
      int shots = rush == null ? 0 : rush.getAmplifier() + 1;
      float target = 0.0F;
      if (rush != null) {
         int seg = Math.max(1, AdrenalineRules.segmentTicks(shots));
         float progress = Math.max(0.0F, Math.min(1.0F, 1.0F - (float)rush.getDuration() / seg));
         target = 0.06F + 0.22F * (shots - 1) + 0.16F * progress;
      }

      veinGrowth += (target - veinGrowth) * (target > veinGrowth ? 0.05F : 0.12F);
      if (veinGrowth < 0.004F) {
         veinGrowth = 0.0F;
         return;
      }

      if (VEINS.isEmpty() || veinW != w || veinH != h) {
         veinW = w;
         veinH = h;
         buildVeins(w, h, veinSeed);
      }

      float pulse = 0.0F;
      if (lastHeartbeatAt > 0L) {
         float since = (float)(now - lastHeartbeatAt);
         pulse = Math.max(0.0F, 1.0F - since / 380.0F);
         pulse *= pulse;
         if (shots >= AdrenalineRules.MAX_SHOTS) {
            float s2 = (float)(now - lastHeartbeatAt - 180L);
            float p2 = Math.max(0.0F, 1.0F - Math.abs(s2) / 260.0F);
            pulse = Math.max(pulse, 0.7F * p2 * p2);
         }
      }

      int alpha = Math.min(255, (int)(0xB0 * Math.min(1.0F, 0.35F + veinGrowth)));
      int red = 0x6E + (int)((0xD0 - 0x6E) * pulse);
      int green = 0x0A + (int)((0x18 - 0x0A) * pulse);
      int blue = 0x12 + (int)((0x28 - 0x12) * pulse);
      int color = alpha << 24 | red << 16 | green << 8 | blue;
      float boost = (shots >= 3 ? 2.0F : (shots == 2 ? 1.0F : 0.5F)) * pulse;
      for (VeinPath v : VEINS) {
         if (shots < v.minStack && veinGrowth > 0.0F && rush != null) {
            continue;
         }

         float vis = v.branch
            ? (veinGrowth - v.startAt) / (0.9F - v.startAt)
            : veinGrowth / 0.9F;
         vis = Math.max(0.0F, Math.min(1.0F, vis));
         if (vis <= 0.0F) {
            continue;
         }

         float pts = (v.xs.length - 1) * vis;
         int last = (int)pts;
         float base = v.branch ? 1.0F : 2.0F;
         for (int i = 0; i < last && i + 1 < v.xs.length; i++) {
            float taper = 1.0F - 0.6F * ((float)i / v.xs.length);
            int t = Math.max(1, Math.round((base + boost) * taper));
            float dx = v.xs[i + 1] - v.xs[i];
            float dy = v.ys[i + 1] - v.ys[i];
            for (int k = 0; k < 3; k++) {
               int px = (int)(v.xs[i] + dx * k / 3.0F);
               int py = (int)(v.ys[i] + dy * k / 3.0F);
               g.fill(px, py, px + t, py + t, color);
            }
         }
      }
   }

   private static void renderPopups(GuiGraphicsExtractor var0, int w, int h, long now) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      MobEffectInstance rush = player == null ? null : player.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
      int shots = rush == null ? 0 : rush.getAmplifier() + 1;
      if (shots < 3) {
         java.util.Arrays.fill(POPUP_BORN, 0L);
         popupNextAt = 0L;
         return;
      }

      long life = 2800L;
      if (popupNextAt == 0L) {
         popupNextAt = now + 400L;
      }

      if (now >= popupNextAt) {
         for (int i = 0; i < POPUP_BORN.length; i++) {
            if (POPUP_BORN[i] == 0L) {
               ThreadLocalRandom rnd = ThreadLocalRandom.current();
               int img = rnd.nextInt(POPUP_SIZE.length);
               int pw = POPUP_SIZE[img][0];
               int ph = POPUP_SIZE[img][1];
               int maxX = Math.max(4, w - 120 - (int)(pw * popupScale(w)));
               int maxY = Math.max(4, h - (int)(ph * popupScale(w)) - 20);
               POPUP_IMG[i] = img;
               POPUP_X[i] = rnd.nextInt(4, maxX + 1);
               POPUP_Y[i] = rnd.nextInt(4, maxY + 1);
               POPUP_BORN[i] = now;
               break;
            }
         }

         popupNextAt = now + (shots >= AdrenalineRules.MAX_SHOTS ? 1300L : 1800L) + ThreadLocalRandom.current().nextInt(900);
      }

      for (int i = 0; i < POPUP_BORN.length; i++) {
         if (POPUP_BORN[i] != 0L) {
            if (now - POPUP_BORN[i] > life) {
               POPUP_BORN[i] = 0L;
            } else {
               int img = POPUP_IMG[i];
               Identifier tex = Identifier.fromNamespaceAndPath("combatinjuries", "textures/gui/popup_" + (img + 1) + ".png");
               int pw = POPUP_SIZE[img][0];
               int ph = POPUP_SIZE[img][1];
               int sw = Math.max(1, (int)(pw * popupScale(w)));
               int sh = Math.max(1, (int)(ph * popupScale(w)));
               var0.blit(RenderPipelines.GUI_TEXTURED, tex, POPUP_X[i], POPUP_Y[i], 0, 0, sw, sh, pw, ph, pw, ph);
            }
         }
      }
   }

   /**
    * A glitched, electric-blue copy of the real syringe bar with a much faster marker. Pressing while it shows is a trap.
    * It sits to the LEFT of the real bar and never covers it.
    */
   private static void renderFakeQte(GuiGraphicsExtractor g, int w, int h, long now, float progress) {
      java.util.Random rnd = new java.util.Random(now / 45L);
      if (rnd.nextInt(9) == 0) {
         return;
      }

      float sc = Math.max(1.0F, h * 0.38F / 64.0F);
      int sw = (int)(24 * sc);
      int sh = (int)(64 * sc);
      int fx = w - sw - 8 - sw - 22;
      int fy = (h - sh) / 2;
      Identifier tex = Identifier.fromNamespaceAndPath("combatinjuries", "textures/gui/syringe_bar.png");
      int strips = 16;
      int stripH = Math.max(1, sh / strips);
      int texStrip = 64 / strips;
      for (int i = 0; i < strips; i++) {
         int off = rnd.nextInt(3) == 0 ? rnd.nextInt(17) - 8 : 0;
         g.blit(RenderPipelines.GUI_TEXTURED, tex, fx + off, fy + i * stripH, 0, i * texStrip, sw, stripH, 24, texStrip, 24, 64);
      }

      g.fill(fx, fy, fx + sw, fy + sh, 0x7A0A3CFF);
      int bx0 = fx + (int)(8 * sc);
      int bx1 = fx + (int)(16 * sc);
      int by0 = fy + (int)(14 * sc);
      int bh = (int)(30 * sc);
      g.fill(bx0, by0, bx1, by0 + bh * 55 / 100, 0xB0001A66);
      g.fill(bx0, by0 + bh * 55 / 100, bx1, by0 + bh * 80 / 100, 0xB000B7FF);
      boolean flash = (now / 50L) % 2L == 0L;
      g.fill(bx0, by0 + bh * 80 / 100, bx1, by0 + bh, flash ? 0xFF8CFFFF : 0xFF1E4DFF);
      int my = by0 + (int)(progress * (bh - 2));
      for (int t = 3; t >= 1; t--) {
         int a = 0x28 * (4 - t);
         g.fill(bx0 - 5, my - t * 5, bx1 + 5, my - t * 5 + 2, a << 24 | 0x66CCFF);
      }

      g.fill(bx0 - 7, my - 1, bx1 + 7, my + 4, 0xFF000000);
      g.fill(bx0 - 6, my, bx1 + 6, my + 3, 0xFF9FD8FF);
      for (int i = 0; i < 4; i++) {
         int ty = fy + rnd.nextInt(Math.max(1, sh));
         g.fill(fx - 6, ty, fx + sw + 6, ty + 1 + rnd.nextInt(2), 0xAA66CCFF);
      }
   }

   private static float fovCurrent;

   /** Extra field of view in degrees while chaining: +3 per shot (12 at the 4th), a thump on every heartbeat, and a push as the window nears. */
   public static float fovBonusDegrees() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      MobEffectInstance rush = player == null ? null : player.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
      float target = 0.0F;
      if (rush != null) {
         int shots = rush.getAmplifier() + 1;
         int seg = AdrenalineRules.segmentTicks(shots);
         float approach = Math.max(0.0F, Math.min(1.0F, (seg - rush.getDuration() - AdrenalineRules.cooldownTicks(shots)) / (float)(seg - AdrenalineRules.cooldownTicks(shots))));
         target = 3.0F * shots + 2.0F * approach * approach;
         if (lastHeartbeatAt > 0L) {
            float since = (float)(System.currentTimeMillis() - lastHeartbeatAt);
            float thump = Math.max(0.0F, 1.0F - since / 320.0F);
            target += 1.5F * thump * thump * shots;
         }
      }

      if (player != null && player.hasEffect(CombatInjuries.HYSTERIA_EFFECT)) {
         long age = System.currentTimeMillis() - hysteriaStartedAt;
         float hf = Math.max(0.0F, Math.min(1.0F, age / 20000.0F));
         double wobble = Math.sin(age / 430.0) * 2.0 + Math.sin(age / 170.0) * 0.8;
         target += (float)wobble * (0.6F + 0.8F * hf);
         if (hysteriaSoundAt > 0L) {
            float since = (float)(System.currentTimeMillis() - hysteriaSoundAt);
            float thump = Math.max(0.0F, 1.0F - since / 260.0F);
            target += 2.5F * thump * thump * (0.5F + 0.5F * hf);
         }
      }

      fovCurrent += (target - fovCurrent) * 0.18F;
      if (Math.abs(fovCurrent) < 0.02F && target == 0.0F) {
         fovCurrent = 0.0F;
      }

      return fovCurrent;
   }

   /** Smooth edge darkening: many thin frames whose strength falls off quadratically toward the centre. */
   private static void drawVignette(GuiGraphicsExtractor g, int w, int h, int depth, float strength, int rgb) {
      int step = 2;
      for (int i = 0; i < depth; i += step) {
         float f = 1.0F - (float)i / (float)depth;
         int a = Math.min(255, (int)(strength * 255.0F * f * f));
         if (a <= 0) {
            break;
         }

         int c = a << 24 | rgb;
         g.fill(i, i, w - i, i + step, c);
         g.fill(i, h - i - step, w - i, h - i, c);
         g.fill(i, i + step, i + step, h - i - step, c);
         g.fill(w - i - step, i + step, w - i, h - i - step, c);
      }
   }

   private static void renderOverlays(GuiGraphicsExtractor var0, DeltaTracker var1) {
      Minecraft var2 = Minecraft.getInstance();
      LocalPlayer var3 = var2.player;
      if (var3 == null) {
         concussionWasActive = false;
         hysteriaWasActive = false;
      } else {
         long var4 = System.currentTimeMillis();
         MobEffectInstance var6 = var3.getEffect(CombatInjuries.CONCUSSION_EFFECT);
         boolean var7 = var6 != null;
         boolean var8 = var3.hasEffect(CombatInjuries.HYSTERIA_EFFECT);
         int var9 = var0.guiWidth();
         int var10 = var0.guiHeight();
         if (var6 != null) {
            long var11 = var4 - concussionStartedAt;
            boolean heavyConc = var6.getAmplifier() >= 1;
            float flashPeak = heavyConc ? 0.9F : 0.4F;
            float var13 = var11 < 90L ? flashPeak : (var11 < 900L ? flashPeak * (float)(900L - var11) / 810.0F : 0.0F);
            float concussionFrac = Math.max(0.0F, Math.min(1.0F, var6.getDuration() / (heavyConc ? 300.0F : 120.0F)));
            float var14 = (heavyConc ? 0.24F + 0.03F * (float)Math.sin(var4 / 420.0) : 0.1F + 0.015F * (float)Math.sin(var4 / 420.0)) * concussionFrac;
            float var15 = Math.max(var14, var13);
            if (var15 > 0.0F) {
               var0.fill(0, 0, var9, var10, Math.min(255, (int)(var15 * 255.0F)) << 24 | 16777215);
            }
         }

         if (var3.hasEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT)) {
            long var18 = var4 - adrenalineRushStartedAt;
            if (var18 < 900L) {
               float var21 = var18 < 100L ? 0.86F : 0.86F * (float)(900L - var18) / 800.0F;
               int var25 = Math.min(255, (int)(var21 * 255.0F)) << 24 | 9175064;
               var0.fill(0, 0, var9, var10, var25);
            }
         }

         if (var8) {
            long var19 = var4 - hysteriaStartedAt;
            if (var19 < 320L) {
               float var22 = var19 < 80L ? 0.92F : 0.92F * (float)(320L - var19) / 240.0F;
               var0.fill(0, 0, var9, var10, Math.min(255, (int)(var22 * 255.0F)) << 24);
            }

            float hf = Math.min(1.0F, var19 / 20000.0F);
            // heartbeat-synced black vignette that closes in the longer the hysteria lasts
            if (hysteriaSoundAt > 0L) {
               float hSince = (float)(var4 - hysteriaSoundAt);
               float hPulse = Math.max(0.0F, 1.0F - hSince / 520.0F);
               drawVignette(var0, var9, var10, (int)(110 + 150 * hf), Math.min(0.9F, 0.30F + 0.30F * hPulse * hPulse + 0.28F * hf), 0x000000);
            }

            // torn static bands
            if (ThreadLocalRandom.current().nextFloat() < 0.10F + 0.25F * hf) {
               int bands = 1 + ThreadLocalRandom.current().nextInt(3);
               for (int b = 0; b < bands; b++) {
                  int by = ThreadLocalRandom.current().nextInt(Math.max(1, var10));
                  int bh = 2 + ThreadLocalRandom.current().nextInt(9);
                  int bc = ThreadLocalRandom.current().nextInt(3) == 0 ? 0x40FFFFFF : (ThreadLocalRandom.current().nextBoolean() ? 0x55000000 : 0x30AA0000);
                  var0.fill(0, by, var9, Math.min(var10, by + bh), bc);
               }
            }

            // sudden white flash
            if (hysteriaFlashNextAt == 0L) {
               hysteriaFlashNextAt = var4 + 2500L + ThreadLocalRandom.current().nextInt(4000);
            } else if (var4 >= hysteriaFlashNextAt) {
               hysteriaFlashUntil = var4 + 70L;
               hysteriaFlashNextAt = var4 + (long)((3000L + ThreadLocalRandom.current().nextInt(6000)) * (1.0F - 0.55F * hf));
            }

            if (var4 < hysteriaFlashUntil) {
               var0.fill(0, 0, var9, var10, 0x55FFFFFF);
            }

            for (int var23 = 0; var23 < 28 + (int)(70 * hf); var23++) {
               int var26 = ThreadLocalRandom.current().nextInt(Math.max(1, var9));
               int var28 = ThreadLocalRandom.current().nextInt(Math.max(1, var10));
               int var16 = ThreadLocalRandom.current().nextInt(2, Math.max(3, Math.min(24, var9 / 5)));
               int var17 = ThreadLocalRandom.current().nextBoolean() ? 587202559 : 570425344;
               var0.fill(var26, var28, Math.min(var9, var26 + var16), Math.min(var10, var28 + 1), var17);
            }
         }

         MobEffectInstance crashFx = var3.getEffect(CombatInjuries.ADRENALINE_CRASH_EFFECT);
         if (crashFx != null) {
            float cvWeight = Math.max(0.0F, Math.min(1.0F, crashFx.getDuration() / 300.0F));
            float cvSlow = (float)(0.5 + 0.5 * Math.sin(var4 / 700.0));
            float cvStrength = (0.22F + 0.14F * cvSlow) * (0.35F + 0.65F * cvWeight);

            drawVignette(var0, var9, var10, 80, cvStrength, 0x000000);
         }

         if (var3.hasEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT) && lastHeartbeatAt > 0L) {
            float rvSince = (float)(var4 - lastHeartbeatAt);
            float rvPulse = Math.max(0.0F, 1.0F - rvSince / 480.0F);
            rvPulse *= rvPulse;
            float rvFadeIn = Math.min(1.0F, (float)(var4 - adrenalineRushStartedAt) / 600.0F);
            int rvStack = Math.max(1, lastRushShots);
            float rvStrength = Math.min(0.85F, (0.16F + 0.30F * rvPulse + 0.09F * (rvStack - 1)) * rvFadeIn);

            drawVignette(var0, var9, var10, 80 + 70 * (rvStack - 1), rvStrength, 0xB0101A);
         }

         if (finalFlashAt > 0L) {
            long ffAge = var4 - finalFlashAt;
            if (ffAge < 1400L) {
               float ffAlpha = ffAge < 150L ? 0.9F : 0.9F * (float)(1400L - ffAge) / 1250.0F;
               var0.fill(0, 0, var9, var10, Math.min(255, (int)(ffAlpha * 255.0F)) << 24 | 0xC00010);
            } else {
               finalFlashAt = 0L;
            }
         }

         renderVeins(var0, var9, var10, var4);
         renderPopups(var0, var9, var10, var4);
         MobEffectInstance barFx = var3.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         if (barFx != null) {
            int bShots = barFx.getAmplifier() + 1;
            int bSeg = AdrenalineRules.segmentTicks(bShots);
            boolean bRed = bShots >= AdrenalineRules.MAX_SHOTS;
            boolean bInWindow = AdrenalineRules.zoneFor(bShots, barFx.getDuration()) == AdrenalineRules.Zone.WINDOW;
            boolean bFlash = (var4 / (bRed ? 70L : 110L)) % 2L == 0L;
            // The bar is a bloody syringe (textures/gui/syringe_bar.png, 24x64). The timeline runs down the barrel,
            // plunger (top) = rush start, needle (bottom) = rush end. Coloured zones are tinted over the barrel.
            float sc = Math.max(1.0F, var10 * 0.38F / 64.0F);
            int sw = (int)(24 * sc);
            int sh = (int)(64 * sc);
            int shake = Math.max(0, bShots - 2);
            int sx = var9 - sw - 8 + (shake == 0 ? 0 : ThreadLocalRandom.current().nextInt(-shake, shake + 1));
            int sy = (var10 - sh) / 2 + (shake == 0 ? 0 : ThreadLocalRandom.current().nextInt(-shake, shake + 1));
            var0.blit(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("combatinjuries", "textures/gui/syringe_bar.png"), sx, sy, 0, 0, sw, sh, 24, 64, 24, 64);
            int barX0 = sx + (int)(8 * sc);
            int barX1 = sx + (int)(16 * sc);
            int barY0 = sy + (int)(14 * sc);
            int barH = (int)(30 * sc);
            int cdH = barH * AdrenalineRules.cooldownTicks(bShots) / bSeg;
            int winH = Math.max(2, barH * AdrenalineRules.windowTicks(bShots) / bSeg);
            int dangerTop = barY0 + cdH;
            int windowTop = barY0 + barH - winH;
            var0.fill(barX0, barY0, barX1, dangerTop, 0xA0000000);
            var0.fill(barX0, dangerTop, barX1, windowTop, bRed ? 0x90FF3A00 : 0x90FFB000);
            int windowColor = bInWindow ? (bFlash ? (bRed ? 0xF0FF2222 : 0xF02CFF6B) : (bRed ? 0xB0801010 : 0xB0108A34)) : (bRed ? 0x80FF2222 : 0x802CFF6B);
            var0.fill(barX0, windowTop, barX1, barY0 + barH, windowColor);
            float bFrac = Math.max(0.0F, Math.min(1.0F, 1.0F - barFx.getDuration() / (float)bSeg));
            int bMark = barY0 + (int)(bFrac * (barH - 2));
            var0.fill(barX0 - 4, bMark - 1, barX1 + 4, bMark + 3, 0xFF000000);
            var0.fill(barX0 - 3, bMark, barX1 + 3, bMark + 2, 0xFFFFFFFF);
         }

         MobEffectInstance fakeFx = var3.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         if (fakeFx != null) {
            int fShots = fakeFx.getAmplifier() + 1;
            float fProg = AdrenalineRules.fakeProgress(fShots, fakeFx.getDuration());
            if (fProg >= 0.0F && AdrenalineRules.zoneFor(fShots, fakeFx.getDuration()) == AdrenalineRules.Zone.DANGER) {
               renderFakeQte(var0, var9, var10, var4, fProg);
            }
         }

         if (var3.hasEffect(CombatInjuries.STUN_EFFECT)) {
            var0.fill(0, 0, var9, var10, 0x40303030);
         }

         if (var3.hasEffect(CombatInjuries.ASPHYXIA_EFFECT)) {
            float var20 = (float)(0.5 + 0.5 * Math.sin(var4 / 260.0));

            for (int var12 = 0; var12 < 4; var12++) {
               int var24 = var12 * 8;
               byte var27 = 8;
               int var29 = (int)((0.1F + var20 * 0.13F) * 255.0F * (4 - var12) / 4.0F);
               int var30 = var29 << 24;
               var0.fill(var24, var24, var9 - var24, var24 + var27, var30);
               var0.fill(var24, var10 - var24 - var27, var9 - var24, var10 - var24, var30);
               var0.fill(var24, var24 + var27, var24 + var27, var10 - var24 - var27, var30);
               var0.fill(var9 - var24 - var27, var24 + var27, var9 - var24, var10 - var24 - var27, var30);
            }
         }
      }
   }

   private static final class AdrenalineMusicSoundInstance extends AbstractTickableSoundInstance implements FabricSoundInstance {
      private final MusicTrack track;

      private AdrenalineMusicSoundInstance(MusicTrack track) {
         super(CombatInjuries.ADRENALINE_MUSIC, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
         this.track = track;
         this.looping = true;
         this.attenuation = Attenuation.NONE;
         this.relative = true;
      }

      public void tick() {
         LocalPlayer var1 = Minecraft.getInstance().player;
         MobEffectInstance rushFx = var1 == null ? null : var1.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         if (rushFx == null) {
            this.stop();
         } else {
            int shots = rushFx.getAmplifier() + 1;
            int seg = AdrenalineRules.segmentTicks(shots);
            float elapsed = (float)(seg - rushFx.getDuration());
            float approach = Math.max(0.0F, Math.min(1.0F, (elapsed - AdrenalineRules.cooldownTicks(shots)) / (float)(seg - AdrenalineRules.cooldownTicks(shots))));
            this.pitch = 1.0F + 0.05F * (shots - 1) + 0.2F * approach * approach;
         }
      }

      public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary var1, Identifier var2, boolean var3) {
         try {
            Object var5 = new CombatInjuriesClient.AdrenalineWavAudioStream(this.track.open());
            return CompletableFuture.completedFuture((AudioStream)var5);
         } catch (IOException var6) {
            return CompletableFuture.completedFuture(new CombatInjuriesClient.SilentAudioStream());
         }
      }
   }

   private static final class AdrenalineWavAudioStream implements AudioStream {
      private final AudioFormat format;
      private final byte[] audioData;
      private int position;

      private AdrenalineWavAudioStream(InputStream var1) throws IOException {
         try {
            try (AudioInputStream var2 = AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(var1))) {
               AudioFormat var3 = var2.getFormat();
               int var4 = var3.getChannels();
               if (var4 != 1 && var4 != 2) {
                  throw new IOException("Adrenaline WAV must be mono or stereo");
               }

               float var5 = var3.getSampleRate();
               this.format = new AudioFormat(Encoding.PCM_SIGNED, var5, 16, var4, var4 * 2, var5, false);

               try (AudioInputStream var6 = AudioSystem.getAudioInputStream(this.format, var2)) {
                  this.audioData = var6.readAllBytes();
               }
            }
         } catch (UnsupportedAudioFileException var13) {
            throw new IOException("Adrenaline music must be a supported WAV file", var13);
         }
      }

      public AudioFormat getFormat() {
         return this.format;
      }

      public ByteBuffer read(int var1) {
         int frame = this.format.getFrameSize();
         int want = var1 - var1 % frame;
         if (want <= 0 || this.audioData.length < frame) {
            return ByteBuffer.allocate(0);
         } else {
            ByteBuffer out = ByteBuffer.allocateDirect(want);

            while (out.hasRemaining()) {
               int n = Math.min(out.remaining(), this.audioData.length - this.position);
               out.put(this.audioData, this.position, n);
               this.position += n;
               if (this.position >= this.audioData.length) {
                  this.position = 0;
               }
            }

            return out.flip();
         }
      }

      public void close() {
      }
   }

   private static final class SilentAudioStream implements AudioStream {
      private static final AudioFormat FORMAT = new AudioFormat(Encoding.PCM_SIGNED, 44100.0F, 16, 1, 2, 44100.0F, false);

      public AudioFormat getFormat() {
         return FORMAT;
      }

      public ByteBuffer read(int var1) {
         return ByteBuffer.allocate(0);
      }

      public void close() {
      }
   }
}
