package com.kodari.combatinjuries;

/**
 * Shared numbers for Adrenaline V2 (stacking shots + timing bar).
 * Used by BOTH the server (decisions) and the client (the side bar), so the bar and the rules can never disagree.
 * Every value here is meant to be tuned.
 */
public final class AdrenalineRules {
   private AdrenalineRules() {
   }

   public enum Zone { COOLDOWN, DANGER, WINDOW }

   /** The 5th shot is always an overdose. */
   public static final int MAX_SHOTS = 4;
   /** Length of the first rush segment, in ticks (20 ticks = 1 s). */
   public static final int FIRST_SEGMENT_TICKS = 600;
   /** Length of every extension segment. */
   public static final int EXTENSION_SEGMENT_TICKS = 400;
   /** After an injection the item is locked this long (harmless to press). */
   public static final int COOLDOWN_TICKS = 200;
   /** Default good-timing window (only used by test commands); the real window shrinks per stack, see windowTicks(). */
   public static final int WINDOW_TICKS = 50;
   /** Good-timing window at the very end of a segment, by number of shots so far (index = shots). 20 ticks = 1 s. */
   private static final int[] WINDOW_BY_SHOTS = {50, 50, 35, 20, 20};
   /** The window is widened by this many ticks on its early edge for network lag. */
   public static final int LATENCY_GRACE_TICKS = 3;
   /** Stack at which pressing too early is lethal instead of just a shock. */
   public static final int LETHAL_EARLY_STACK = 3;
   /** Crash bill multiplier by number of shots (index = shots). */
   public static final float[] CRASH_MULT = {1.0F, 1.0F, 1.5F, 2.0F, 2.0F};
   /** Stun after a perfect 4-shot chain (cash-out). */
   public static final int CASHOUT_STUN_TICKS = 60;
   /** Stun when a shot is pressed too early at a low stack. */
   public static final int SHOCK_STUN_TICKS = 40;
   /** Stun when a Totem of Undying saves you from an overdose. */
   public static final int TOTEM_STUN_TICKS = 160;
   /** Nobody can inject again for this long after surviving an overdose. */
   public static final int OVERDOSE_LOCKOUT_TICKS = 1200;

   public static int segmentTicks(int shots) {
      return shots <= 1 ? FIRST_SEGMENT_TICKS : EXTENSION_SEGMENT_TICKS;
   }

   public static int windowTicks(int shots) {
      return WINDOW_BY_SHOTS[Math.max(0, Math.min(WINDOW_BY_SHOTS.length - 1, shots))];
   }

   public static Zone zoneFor(int shots, int ticksLeft) {
      int elapsed = segmentTicks(shots) - ticksLeft;
      if (elapsed < COOLDOWN_TICKS) {
         return Zone.COOLDOWN;
      }
      return ticksLeft <= windowTicks(shots) + LATENCY_GRACE_TICKS ? Zone.WINDOW : Zone.DANGER;
   }

   /** From the 3rd shot on, fake blue "error" windows tempt you to press inside the danger zone. Pressing during one is deadly. */
   public static final int FAKE_START_1 = 235;
   public static final int FAKE_LENGTH_1 = 25;
   public static final int FAKE_START_2 = 300;
   public static final int FAKE_LENGTH_2 = 22;

   /** 0..1 progress through the currently active fake window, or -1 when none is showing. */
   public static float fakeProgress(int shots, int ticksLeft) {
      if (shots < LETHAL_EARLY_STACK) {
         return -1.0F;
      }
      int elapsed = segmentTicks(shots) - ticksLeft;
      if (elapsed >= FAKE_START_1 && elapsed < FAKE_START_1 + FAKE_LENGTH_1) {
         return (elapsed - FAKE_START_1) / (float)FAKE_LENGTH_1;
      }
      if (elapsed >= FAKE_START_2 && elapsed < FAKE_START_2 + FAKE_LENGTH_2) {
         return (elapsed - FAKE_START_2) / (float)FAKE_LENGTH_2;
      }
      return -1.0F;
   }

   public static boolean fakeActive(int shots, int ticksLeft) {
      return fakeProgress(shots, ticksLeft) >= 0.0F && zoneFor(shots, ticksLeft) == Zone.DANGER;
   }
}
