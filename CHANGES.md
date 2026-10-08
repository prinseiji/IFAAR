# IFAAR fixes (not yet compiled or tested in-game)

Build: see the GitHub Actions workflow; the finished jar is IFAAR.jar (not the -sources one).

## Resources
- All screen filters (`hysteria_monochrome_0..10`, `adrenaline_monochrome_0..10`, `adrenaline_crash_monochrome_0..10`)
  and all textures are now real files. Before, build.gradle generated them at build time, so a hand-packed jar had none.
- Post-effect uniforms now include `name` fields.
- `concussion_tinnitus.ogg` added; sounds.json pointed at a nonexistent file before.
- Removed the generate-textures task from build.gradle.

## Server (CombatInjuries.java)
- Asphyxia from suffocation (any block, not only sand/gravel) now ends once you're out of the blocks.
- Sleep recovery requires an actual completed sleep (>=100 ticks in bed AND the time-reset event).
- Zombie-family mobs holding iron tools, plus copper tools and marked items, can give tetanus; chance 25% (TETANUS_CHANCE).
- `/injurytest asphyxia` now actually drains your air.

## Client
- Concussion: world fades back in and tinnitus fades out over the last 5 seconds; white haze fades with remaining time.
- Adrenaline crash filter fades out across the whole crash, tied to remaining effect time.
- Hysteria: random phantom hostile-mob sounds around the player.
- Custom music: drop .wav files into `<game dir>/config/ifaar/adrenaline_music/` (folder is auto-created).
- Screen filter failures are caught and logged instead of crashing.

## Items
- Rusty iron/copper tools now have their own look (the vanilla texture tinted rust-brown), also on zombies' held tools.
- Added gradle.properties and a GitHub build workflow (.github/workflows/build.yml).

## Still open
- Daytime sleeping waking you instantly / not advancing the day (needs Minecraft 26.2 source to fix properly).

## Round 3 (adrenaline polish; not yet built or played)
- Music: fixed stop-every-tick bug, replaced non-existent placeholder sound file, self-looping WAV stream.
- New sounds (synthesized stand-ins): inject, power-up, power-down, heartbeat.
- Rush filter is half desaturated; pulsing red vignette synced to heartbeat.
- settings.gradle and LivingEntityInjuryMixin build fixes folded back into the project.
- Full handoff documentation: DEVELOPMENT.md

## Round 4 (playtest notes; not yet built or played)
- Music no longer dies on concussion: it restarts muffled and recovers with the tinnitus fade.
- Hemorrhage: rolled on confirmed Sharpness hits (50%); only a >=2 HP heal, Regeneration or sleep cures it; natural regen blocked while bleeding.
- Rush speed +55%, airborne momentum boost so jumps keep speed.
- Crash is heavier: tapering slow, mining/attack-speed/jump penalties, hunger drain, breathing, dark vignette.
- Fracture jump-block also works client-side.

## Round 5

- **Concussion tiers.** Heavy (15 s): explosions, Warden, mace smash, sonic boom, falling anvil. Light (6 s): iron golem hits. Both mute world sounds; light has a softer white haze (about 40% flash, 0.10 ambient), quieter and higher tinnitus, and a shorter fade-out. A light hit never shortens or downgrades a heavy concussion. The tier is carried in the effect amplifier (0 = light, 1 = heavy). Test: `/injurytest concussion` and `/injurytest concussion_light`.
- **Adrenaline V2 minigame** (see DEVELOPMENT.md section 11.5). Up to 4 stacked shots; side bar on the right of the screen (grey cooldown, orange danger, green window, red at stack 4); too early = shock (stack 1-2) or overdose (stack 3+); 5th shot = overdose; a perfect 4-chain cancels the debt and stuns you; crash bill x1.0 / 1.5 / 2.0 by stack. Overdose uses the new damage type `adrenaline_overdose`, which ignores armor and effects. A Totem of Undying saves you but leaves a long stun, ends the rush, wipes the debt and locks shots for 60 s. Creative gets the effects but not the death. New stun effect `adrenaline_stun` (its icon is a grey copy of the crash icon - replace it with your own art). All numbers live in `AdrenalineRules.java`.
- New test commands: `/injurytest adrenaline_shots_2|3|4`, `adrenaline_stun`, `overdose`, `qte`.

## Round 6

- **Pop-ups.** From the 3rd shot on, 6 fake "Windows 95" dialogs (textures/gui/popup_1..6.png) appear for about 3 s each, up to 3 at once. They never appear in the right-hand strip and are drawn before the timing bar, so they can never cover it.
- **Music** rises in pitch toward the window, resets on each hit, and starts a little higher at each stack. **Vignette** gets thicker/stronger and the heartbeat faster with each stack. **Stack 4 ending** flashes the screen red and plays a bigger power-down (`adrenaline_powerdown_grand.ogg`).
- **Stun after a perfect chain** shortened to 3 s.
- **Shots stack to 16** and now use the vanilla item cooldown (greys out like an ender pearl, 10 s).
- **Syringe** item (glass bottle + iron nugget, shapeless). **Adrenaline shot** recipe: syringe + Potion of Swiftness II (strong_swiftness), shapeless, via Fabric's `fabric:components` ingredient. Cleric trades kept, limited to 2 uses each. The syringe texture is a grey placeholder made from the shot art.

## Round 7

- **Music pool.** 7 normalised loops (about -14 dB RMS, seam crossfaded, tails trimmed) in `sounds/adrenaline_music/`, listed in `tracks.txt`: breakcore_140, ultrakill_187, glitched_170, jubilation_169, distorted_165, hardcore_204, uptempo_193. One track is picked per rush as a shuffle bag (all play once before any repeat). `.wav` files in `config/ifaar/adrenaline_music/` are ADDED to the pool. The old single `adrenaline.wav` was removed (identical to ultrakill_187). A concussion mid-rush restarts the SAME track.
- **Pop-ups** drawn at about 20% of the screen width at most (scale 0.2-0.7).
- **Vignette** (rush and crash) is now drawn as many thin 2 px frames with a quadratic falloff, so no visible bands.
- **Timing bar** restyled to a hard-edged look: black/grey bevel frame, tick-marked cooldown, hazard-striped danger zone (red stripes at stack 4), flashing window, white marker with a black outline. The shot-count pips were removed. No textures needed - it is all drawn in code (`renderOverlays`).
- **Adrenaline shot recipe** is now shaped: 7 syringes around a Swiftness II potion (top middle) and a pufferfish (centre) give **7 shots**. The syringe recipe is unchanged.
