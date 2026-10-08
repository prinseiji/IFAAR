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
   /** The good-timing window at the very end of a segment. */
   public static final int WINDOW_TICKS = 60;
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

   public static Zone zoneFor(int shots, int ticksLeft) {
      int elapsed = segmentTicks(shots) - ticksLeft;
      if (elapsed < COOLDOWN_TICKS) {
         return Zone.COOLDOWN;
      }
      return ticksLeft <= WINDOW_TICKS + LATENCY_GRACE_TICKS ? Zone.WINDOW : Zone.DANGER;
   }
}
