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
   private static final int[] WINDOW_BY_SHOTS = {50, 50, 45, 40, 40};
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

   /** Length of the rush segment after the Nth shot. It gets shorter, so the marker sweeps the bar faster while the window stays generous. */
   private static final int[] SEGMENT_BY_SHOTS = {600, 600, 400, 300, 220};
   /** Item lock-out after the Nth shot (the grey part of the bar). */
   private static final int[] COOLDOWN_BY_SHOTS = {200, 200, 140, 100, 80};

   public static int cooldownTicks(int shots) {
      return COOLDOWN_BY_SHOTS[Math.max(0, Math.min(COOLDOWN_BY_SHOTS.length - 1, shots))];
   }

   public static int segmentTicks(int shots) {
      return SEGMENT_BY_SHOTS[Math.max(0, Math.min(SEGMENT_BY_SHOTS.length - 1, shots))];
   }

   public static int windowTicks(int shots) {
      return WINDOW_BY_SHOTS[Math.max(0, Math.min(WINDOW_BY_SHOTS.length - 1, shots))];
   }

   public static Zone zoneFor(int shots, int ticksLeft) {
      int elapsed = segmentTicks(shots) - ticksLeft;
      if (elapsed < cooldownTicks(shots)) {
         return Zone.COOLDOWN;
      }
      return ticksLeft <= windowTicks(shots) + LATENCY_GRACE_TICKS ? Zone.WINDOW : Zone.DANGER;
   }

   /** From the 3rd shot on, fake blue "error" windows tempt you to press inside the danger zone. Pressing during one is deadly. */
   private static int dangerLength(int shots) {
      return segmentTicks(shots) - cooldownTicks(shots) - windowTicks(shots) - LATENCY_GRACE_TICKS;
   }

   public static int fakeStart1(int shots) {
      return cooldownTicks(shots) + (int)(dangerLength(shots) * 0.18F);
   }

   public static int fakeLength1(int shots) {
      return Math.min(25, (int)(dangerLength(shots) * 0.25F));
   }

   public static int fakeStart2(int shots) {
      return cooldownTicks(shots) + (int)(dangerLength(shots) * 0.60F);
   }

   public static int fakeLength2(int shots) {
      return Math.min(22, (int)(dangerLength(shots) * 0.22F));
   }

   /** 0..1 progress through the currently active fake window, or -1 when none is showing. */
   public static float fakeProgress(int shots, int ticksLeft) {
      if (shots < LETHAL_EARLY_STACK) {
         return -1.0F;
      }
      int elapsed = segmentTicks(shots) - ticksLeft;
      int s1 = fakeStart1(shots);
      int l1 = fakeLength1(shots);
      if (l1 > 0 && elapsed >= s1 && elapsed < s1 + l1) {
         return (elapsed - s1) / (float)l1;
      }
      int s2 = fakeStart2(shots);
      int l2 = fakeLength2(shots);
      if (l2 > 0 && elapsed >= s2 && elapsed < s2 + l2) {
         return (elapsed - s2) / (float)l2;
      }
      return -1.0F;
   }

   public static boolean fakeActive(int shots, int ticksLeft) {
      return fakeProgress(shots, ticksLeft) >= 0.0F && zoneFor(shots, ticksLeft) == Zone.DANGER;
   }
}
