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
- **Bar is now a syringe.** `textures/gui/syringe_bar.png` (24x64, cropped from the owner's 64x64 art) is drawn at the right edge, sized to about a third of the screen height. The timeline runs down the barrel (plunger = start, needle = end) with translucent zone tints over the art. To swap the art, keep the barrel at x 8-16, y 14-44 of the 24x64 image, or change the four numbers in `renderOverlays`. Pop-ups keep clear of a wider right-hand strip (120 px).

## Round 7b

- **Syringe bar.** The timing bar is now your bloody syringe texture (`textures/gui/syringe_bar.png`, cropped to 24x64), drawn at about a third of the screen height. The plunger at the top is the start of the rush and the needle is the end. The coloured zones (dark cooldown, amber danger, green window; red at stack 4) are tinted over the barrel, and a white marker line moves down it. Texture supplied by the owner from another project - confirm you have the right to ship it.

## Round 8

- **Shorter windows.** The good-timing window shrinks with the stack: 2.5 s (1st shot), 1.75 s (2nd), 1 s (3rd; the last real one) - see `AdrenalineRules.windowTicks()`. The bar uses the same numbers.
- **Fake QTE.** From the 3rd shot on, a blue "FATAL EXCEPTION" window (blue-screen style) flashes beside the bar twice per segment while you are still in the danger zone, with a quickly draining timer. Pressing during it is an overdose. It never covers the real bar. Timing is in `AdrenalineRules.fakeProgress()`.
- **Bar shake** from the 3rd shot on (1 px, then 2 px at stack 4).
- **FOV.** +3 degrees per shot (12 at the 4th), plus a thump on every heartbeat and a small push as the window nears. Implemented as an OPTIONAL mixin (`GameRendererFovMixin`, own config `combatinjuries.fov.mixins.json`, `required: false`) so a wrong method name in 26.2 only disables the effect instead of stopping the game.
- **Chain filter.** 13 pre-baked filters `adrenaline_chain_0..12`, a muted version of the Hysteria look (intensity 0.20 -> 0.65, contrast 1.1 -> 1.85, darkness 0 -> 0.10). The level rises through each segment and with each stack (3 levels per stack).
- **Vignette** creeps further per stack (80 px depth + 70 px per stack) and is stronger.
- **16 advancements** (data/combatinjuries/advancement). They sit in the vanilla Adventure tab. Granted from `CombatInjuries.grant()`. Injury achievements fire the first time each status is seen; "How Did We Get Here?" needs all 7 injuries in one life (reset on death). New test command: `/injurytest fake_qte`.

## Round 8b

- **Fake QTE redone.** It is now a glitched, electric-blue copy of the real syringe bar (sliced/offset syringe, blue wash, flickering, scan tears) sitting to the LEFT of the real bar, with a fast marker with ghost trails sweeping down to a strobing "window". It shows twice per segment from the 3rd shot on, during the danger zone only. Pressing while it shows is an overdose (and the "obvious" achievement).
- **Hysteria crazier**, ramping over its first 20 s: heartbeat-synced black vignette that closes in, torn static bands, more static dashes, random 70 ms white flashes, FOV wobble with a heartbeat thump, a heartbeat that speeds up (800 -> 480 ms) and phantom sounds that come up to 65% more often.
- **Achievements get their own tab** ("IFAAR", root advancement + a generated dark-red background tile `textures/gui/advancements/ifaar.png`) and **15 more**: Needle Work, Is This Even Legal?, The Comedown, Paid In Full, Quit While You're Ahead, Take Two, Too Eager, Can't Touch This, Ring My Bell, Applied Pressure, Sleep It Off, Got Milk?, Walking Disaster, Back To Reality, Dug My Own Grave. 31 in total plus the root.

## Round 9

- **Eased windows, faster marker.** The good-timing window no longer shrinks to 1 s: it is now 2.5 s / 2.5 s / 2.25 s / 2 s (stacks 1-4). Difficulty comes from the marker instead: each segment after a shot is shorter, so the marker sweeps the same bar faster. Segment length by shots: 30 s, 30 s, 20 s, 15 s, 11 s (index = shots; the 1st-shot segment is 30 s, the 2nd-shot segment 20 s, the 3rd-shot segment 15 s, the 4th-shot segment 11 s). Marker speed is about 1x / 1.5x / 2x / 2.7x. The cooldown (grey) also shrinks: 10 s / 7 s / 5 s / 4 s. All in `AdrenalineRules.java`.
- **Fake QTE** windows are now placed as a share of each segment's danger zone (`fakeStart1/2`, `fakeLength1/2`) so they still fit the shorter segments.
- **Bar slightly bigger**: the syringe bar and the fake bar are about 12% larger (0.34 -> 0.38 of the screen height).

## Round 10

- **Procedural veins** around the screen edges during an adrenaline rush (no art needed; drawn from code in `renderVeins`). 17 main veins plus 2 branches each, grown from the edges toward the centre with a random wobble; a new random pattern each rush. They grow with the stack and the segment progress, and pulse harder with every heartbeat (a double beat at stack 4). Veins only appear in layers: the first 8 from stack 1, 5 more from stack 2, the rest from stack 3.
- **Vein sound** (`adrenaline_veins.ogg`, converted from your mp3) plays each time the stack goes up.
- **Popups slower** to make room: a new popup every ~1.8-2.7 s (stack 4: ~1.3-2.2 s), was ~0.9-1.4 s / 0.5-1 s.

## Round 11

- **Tetanus from worn tools, no labels.** Iron and copper swords, axes, pickaxes, shovels and hoes (and copper spears) now turn rusty/weathered on their own once they are 60% worn (`RUST_DAMAGE_FRACTION` in `CombatInjuries.java`, same number as the `threshold` in the item JSONs). Iron shows the tinted rusty texture; copper shows your weathered textures (`textures/item/copper_*_weathered.png`). While a player/mob holds such a tool, its hits can cause tetanus (25%). Copper tools are no longer always rusty, only worn ones. Zombies' own iron tools still count as rusty.
- Done with client item definitions that override vanilla (`assets/minecraft/items/iron_*.json`, `copper_*.json`, using `range_dispatch` on `minecraft:damage`). Removed the labelled "Rusty ..." entries from the creative tab.
- Copper spear: added a weathered model from the 32x32 texture. If vanilla's copper spear uses a special in-hand model, delete `assets/minecraft/items/copper_spear.json` to restore the vanilla look.

## Round 12

- **Sleeping with hemorrhage works.** Bleed damage is paused while you are in bed (it used to knock you out of bed before you could sleep). Sleeping through the night still cures it.
- **Perfect-chain stun** 3 s -> 2 s (`CASHOUT_STUN_TICKS` 60 -> 40).
- **Fracture recovery** 10 s -> 5 s of standing still (`FRACTURE_STILL_TICKS` 200 -> 100 in `CombatInjuries.java`).
- **Crash damage ignores armour and protection enchantments** (added to the `bypasses_armor` / `bypasses_enchantments` damage tags).
- **Logout** clears a player's injury state.
- **Add-on API** (`CombatInjuriesApi.java`): events `RUSH_STARTED`, `RUSH_ENDED(cashedOut)`, `OVERDOSED`, and queries `hasInjury(player, id)`, `rushShots(player)`, `isStunned(player)`.

## Round 13

- **Adrenaline Bolt** (now part of IFAAR, `AdrenalineBolts.java`). Craft 1 arrow + 1 iron nugget + 1 adrenaline shot. It is in the `minecraft:arrows` tag, so crossbows (and bows) load it. It deals no arrow damage and is used up on hit.
  - Weak mobs (16 max HP or less, plus wolves): freeze, flash red fast for ~1.5 s, then burst into blood. No loot or XP.
  - Strong mobs: ~6 s of Speed III + Strength II, hunting the shooter, flashing red faster and faster, then burst. No loot or XP.
  - Immune: Warden, Wither, Ender Dragon (take normal arrow damage). Players are not affected by bolts.
  - A player killed by an IFAAR overdose bursts into red mist (particles + sound).
  - 3 achievements in the IFAAR tab: Loaded Question, Meat Confetti, Overclocked.
  - Tunables at the top of `AdrenalineBolts.java`.

- Build fix: `getPickupItem()` is protected in AbstractArrow, so it is read through a mixin invoker (`mixin/AbstractArrowAccessor.java`, registered in `combatinjuries.mixins.json`).

## Round 14

- **Bolt gore, ULTRAKILL style.** Bursts now throw a hard spray of blood in every direction, an upward geyser, flying chunks of meat and bone (beef, porkchop, rotten flesh, spider eye, bone, mutton) and a dark-red cloud that hangs in the air; louder, deeper sounds.
- **No more damage ticks.** Injected mobs take no damage at all; they just die (burst) when the timer ends.
- **Real red flashing.** Injected mobs now flash bright red on and off (weak mobs fast and steady, strong mobs faster and faster toward the end) instead of staying red. Done on the client by `LivingEntityRendererFlashMixin` reading an invisible rush effect the server puts on the mob.
- **Player flashes red too** during a rush (visible in F5), on the beat of the heartbeat; faster at higher stacks.
- **Chain filter changes only once per stack** (4 levels instead of 13). Swapping the screen filter often is the main suspect for the rainbow flash glitch reported in round 13 testing.
- Build fix: gib particles take an Item, not an ItemStack.

## Round 14b

- **Mob red flash fix.** The client cannot see another entity's potion effects, so the flash had nothing to read. The server now encodes the bolt rush in the mob's air supply (a value vanilla already syncs): weak = -1000 - ticksLeft, strong = -2000 - ticksLeft. `LivingEntityRendererFlashMixin` decodes it. The invisible rush effect on mobs was removed. The first time the hook sees a rushing mob it prints "[IFAAR] red flash hook is active" to the game log (`logs/latest.log`), which tells us the mixin is applying.

## Round 14c

- **Arrow impact is back.** The bolt now deals normal arrow damage and the arrow sticks in the mob like any arrow. Only if that single hit would kill the mob is the damage skipped (the mob must live to burst); the arrow is still stuck into it visually.

## Round 15

- **Brighter red flash.** Besides the vanilla (see-through) hurt overlay, flashing mobs now also get a solid bright-red outline (`state.outlineColor`, visible through walls) while the flash is on.
- **Bolt sounds.** `bolt_beep.ogg` (timer beep) plays on every red flash, pitch rising as the end nears (more so for strong mobs). Bursts use `bolt_burst_weak.ogg` (grit/wet impact) for weak mobs and `bolt_burst_strong.ogg` (heavy punch) for strong mobs, plus a low bone crunch. Player red mist uses the heavy one. Sound events: `BOLT_BEEP`, `BOLT_BURST_WEAK`, `BOLT_BURST_STRONG`.
