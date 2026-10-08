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

         if (var5 && var2 - hysteriaSoundAt >= 800L) {
            playCue(var0, SoundEvents.WARDEN_HEARTBEAT, 0.9F, 0.72F);
            hysteriaSoundAt = var2;
         }

         if (var5) {
         if (hysteriaPhantomAt == 0L) {
            hysteriaPhantomAt = var2 + 2500L + ThreadLocalRandom.current().nextInt(5000);
         } else if (var2 >= hysteriaPhantomAt) {
            playPhantomSound(var0);
            hysteriaPhantomAt = var2 + 3000L + ThreadLocalRandom.current().nextInt(9000);
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
         qteWindowWasOpen = qteOpen;
         if (var11 && !adrenalineRushWasActive) {
            adrenalineRushStartedAt = var2;
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
         setCameraPostEffect(var0, "adrenaline_monochrome_5");
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
               int maxX = Math.max(4, w - 60 - (int)(pw * popupScale(w)));
               int maxY = Math.max(4, h - (int)(ph * popupScale(w)) - 20);
               POPUP_IMG[i] = img;
               POPUP_X[i] = rnd.nextInt(4, maxX + 1);
               POPUP_Y[i] = rnd.nextInt(4, maxY + 1);
               POPUP_BORN[i] = now;
               break;
            }
         }

         popupNextAt = now + (shots >= AdrenalineRules.MAX_SHOTS ? 500L : 900L) + ThreadLocalRandom.current().nextInt(500);
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

            for (int var23 = 0; var23 < 28; var23++) {
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
            float rvStrength = Math.min(0.85F, (0.16F + 0.30F * rvPulse + 0.06F * (rvStack - 1)) * rvFadeIn);

            drawVignette(var0, var9, var10, 80 + 36 * (rvStack - 1), rvStrength, 0xB0101A);
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

         renderPopups(var0, var9, var10, var4);
         MobEffectInstance barFx = var3.getEffect(CombatInjuries.ADRENALINE_RUSH_EFFECT);
         if (barFx != null) {
            int bShots = barFx.getAmplifier() + 1;
            int bSeg = AdrenalineRules.segmentTicks(bShots);
            boolean bRed = bShots >= AdrenalineRules.MAX_SHOTS;
            int bH = 150;
            int bW = 12;
            int bX = var9 - 26;
            int bY = (var10 - bH) / 2;
            int cdH = bH * AdrenalineRules.COOLDOWN_TICKS / bSeg;
            int winH = bH * AdrenalineRules.WINDOW_TICKS / bSeg;
            boolean bInWindow = AdrenalineRules.zoneFor(bShots, barFx.getDuration()) == AdrenalineRules.Zone.WINDOW;
            boolean bFlash = (var4 / (bRed ? 70L : 110L)) % 2L == 0L;
            // hard frame: black outer, grey bevel, black inner
            var0.fill(bX - 4, bY - 4, bX + bW + 4, bY + bH + 4, 0xFF000000);
            var0.fill(bX - 3, bY - 3, bX + bW + 3, bY + bH + 3, 0xFF8A8A8A);
            var0.fill(bX - 2, bY - 2, bX + bW + 2, bY + bH + 2, 0xFF000000);
            // cooldown: dark with tick marks
            var0.fill(bX, bY, bX + bW, bY + cdH, 0xFF232323);
            for (int ty = bY + 4; ty < bY + cdH; ty += 8) {
               var0.fill(bX + 2, ty, bX + bW - 2, ty + 1, 0xFF555555);
            }

            // danger: hazard stripes
            int stripeA = bRed ? 0xFFC01212 : 0xFFF2C200;
            int dTop = bY + cdH;
            int dBot = bY + bH - winH;
            for (int sy = dTop; sy < dBot; sy += 6) {
               int stripe = ((sy - dTop) / 6) % 2 == 0 ? stripeA : 0xFF0C0C0C;
               var0.fill(bX, sy, bX + bW, Math.min(dBot, sy + 6), stripe);
            }

            // window: solid and flashing while it is open
            int windowOn = bRed ? 0xFFFF2222 : 0xFF2CFF6B;
            int windowOff = bRed ? 0xFF6E0C0C : 0xFF0F6E2B;
            var0.fill(bX, dBot, bX + bW, bY + bH, bInWindow && bFlash ? windowOn : (bInWindow ? windowOff : (bRed ? 0xFFA31515 : 0xFF1FA64B)));
            var0.fill(bX, dBot, bX + bW, dBot + 2, 0xFFFFFFFF);
            // marker
            float bFrac = Math.max(0.0F, Math.min(1.0F, 1.0F - barFx.getDuration() / (float)bSeg));
            int bMark = bY + (int)(bFrac * (bH - 3));
            var0.fill(bX - 7, bMark - 1, bX + bW + 7, bMark + 4, 0xFF000000);
            var0.fill(bX - 6, bMark, bX + bW + 6, bMark + 3, 0xFFFFFFFF);
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
            float approach = Math.max(0.0F, Math.min(1.0F, (elapsed - AdrenalineRules.COOLDOWN_TICKS) / (float)(seg - AdrenalineRules.COOLDOWN_TICKS)));
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
