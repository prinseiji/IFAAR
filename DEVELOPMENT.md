# IFAAR — "Injured For An Amazing Reason." 
## Development Documentation & Handoff Guide

| | |
|---|---|
| **Mod ID** | `combatinjuries` |
| **Display name** | IFAAR (Injured For An Amazing Reason.) |
| **Version** | 1.0.0 |
| **Platform** | Fabric (Loader ≥ 0.19.3, Fabric API required) |
| **Minecraft** | 26.2 (`~26.2`) |
| **Java** | 25+ |
| **Build tooling** | Fabric Loom 1.18.2, Gradle 9.8.0, Mixin 0.8.7 (via Fabric), official Mojang mappings |
| **Package root** | `com.kodari.combatinjuries` |
| **Environment** | `*` (client + server; client-only code is split into `client/`) |

---

## 0. Read this first

### 0.1 Provenance and status
This document started as an analysis written from the decompiled jar, and was then revised against the real project history (§11.6) and the latest code. Sections 1–9 describe what the code does; §10 lists problems and their status; §11 is design notes and the Adrenaline V2 spec; §14–15 explain how the project is built and the traps already hit.

**Nothing in this project has been run through a compiler by the person who wrote the fixes** (their workspace could not reach Maven). Builds were done by GitHub Actions (§14). The owner has playtested up to the "round 2" build (§11.6). **Round 3 changes (adrenaline audio/visuals, §11.1–11.4) have been confirmed present in the built jar** by decompiling it and diffing against the round-2 jar (static review only — see §10.11 for the gaps found). The build succeeded, but they are still **play-untested**. **Round 4** (hemorrhage, rush/crash tuning, music-vs-concussion, §11.6) is newer still and has not been built or played. Verify with `/injurytest` (§12).

### 0.2 About the source
- **The authoritative source is the GitHub repository** (Gradle project: `src/main/java`, `src/client/java`, `src/main/resources`, `build.gradle`, `settings.gradle`, `gradle.properties`, `.github/workflows/build.yml`).
- The project was **recovered by decompiling a jar** originally produced by the Kodari AI builder, so the Java has no comments and many lambda parameters/locals are named `var0`, `var1`… Fields, methods, classes and Mixin annotations are intact. **Rename the `varN` names as you work.**
- An older standalone "decompiled source" zip (decompile of the round-2 jar) exists but has **no build files** and still contains a decompiler artifact that fails to compile (`this instanceof Player` in `LivingEntityInjuryMixin` must be `(Object)this instanceof Player`; fixed in the repo). Do not start from that zip.

### 0.3 What this mod is, in one paragraph
IFAAR adds a **combat injury system** to survival Minecraft. Specific kinds of damage cause specific injuries (bleeding, tetanus, a broken leg, a concussion, and so on). Each has a distinct trigger, penalty, cure and screen/sound treatment. Dropping to 2 hearts triggers **Hysteria**, a combat-frenzy state. A craftable **Adrenaline Shot** gives a short super-human state (**Adrenaline Rush**) that is paid for with a delayed damage bill and a slowdown (**Adrenaline Crash**). Sleeping through a night cures everything.

---

## 1. Project layout

```
combatinjuries (jar)
├── fabric.mod.json                      entrypoints, dependencies
├── combatinjuries.mixins.json           COMMON mixins
├── combatinjuries.client.mixins.json    CLIENT-ONLY mixins
│
├── com/kodari/combatinjuries/
│   ├── CombatInjuries.java              ★ SERVER/COMMON: all injury logic, items, events, commands
│   ├── mixin/
│   │   ├── BedRuleDaytimeMixin          allow resting in daytime beds
│   │   ├── BrewingStandBlockEntityMixin pufferfish + Swiftness → Adrenaline Shot
│   │   ├── LivingEntityInjuryMixin      block jumping while fractured
│   │   └── MilkFinishUsingItemMixin     milk/honey cure tetanus
│   └── client/
│       ├── CombatInjuriesClient.java    ★ CLIENT: sounds, overlays, post-effect filters, adrenaline music
│       └── mixin/
│           ├── EntityRendererHysteriaMixin  hide enemy nameplates during Hysteria
│           ├── SoundEngineHysteriaMixin     boost enemy sounds (Hysteria) + muffle all sound (Concussion)
│           ├── GameRendererAccessor         invoker to set a post-effect (shader) chain
│           └── EntityBoundSoundInstanceAccessor  read the entity behind a sound
│
├── assets/combatinjuries/
│   ├── sounds.json                      sound event definitions
│   ├── sounds/                          concussion_tinnitus.ogg, adrenaline_music/adrenaline.wav
│   ├── lang/en_us.json                  names + death messages
│   ├── items/ + models/item/            Adrenaline Shot + 10 "rusty" tool models
│   ├── textures/item/, textures/mob_effect/   16×16 icons
│   ├── shaders/post/monochrome.{vsh,fsh}      the one screen-filter shader
│   └── post_effect/                     34 JSON files: pre-baked filter strengths (see §7.1)
│
└── data/
    ├── combatinjuries/damage_type/      hemorrhage, asphyxia, adrenaline_crash
    ├── combatinjuries/villager_trade/cleric/   3 adrenaline-shot trades
    ├── combatinjuries/tags/villager_trade/cleric/level_5.json
    └── minecraft/tags/damage_type/no_knockback.json   the 3 custom damage types don't knock back
```

---

## 2. Architecture: the one idea you need to understand

**The server owns the truth. Status effects are the sync channel. The client only reads effects.**

```
                        SERVER                                         CLIENT
 ┌─────────────────────────────────────────────┐        ┌────────────────────────────────────┐
 │ events (damage, attack, sleep, use item…)   │        │ ClientTickEvents.END_CLIENT_TICK   │
 │        │ set flags/timers                   │        │   player.hasEffect(X)?             │
 │        ▼                                    │        │        │                           │
 │ Map<UUID, InjuryState> STATES               │        │        ▼ edge detection             │
 │        │ tickPlayer() every server tick     │        │  (wasActive vs isActive)           │
 │        ▼                                    │ effect │        │                           │
 │ syncEffect(player, EFFECT, active, ticks) ──┼───────►│        ▼                           │
 │   adds/removes a MobEffectInstance          │ packet │  sounds / overlays / shaders       │
 └─────────────────────────────────────────────┘        └────────────────────────────────────┘
```

Consequences worth internalising:

- **`InjuryState`** (private static class in `CombatInjuries`) holds every flag and timer for one player. It lives in `STATES`, a plain `HashMap<UUID, InjuryState>`.
- **Each injury is also a registered `MobEffect`** (`InjuryStatusEffect` is an empty subclass). The effects carry **no gameplay logic**; they exist so the client can see "player has X" and so the player gets an icon in the HUD. `syncEffect()` is called for all nine every tick with `showParticles=false, showIcon=true`.
- **Gameplay consequences are applied in `tickPlayer()`** (attribute modifiers, damage ticks, item dropping) or in event handlers / mixins.
- **Client FX are edge-triggered**: the client remembers `xWasActive` from last tick and reacts to false→true (start) and true→false (end). This is the pattern that the Adrenaline music bug (§10.1) broke.
- Durations: effects with a countdown (Concussion, Winded, Rush, Crash) are synced with their remaining ticks; open-ended ones (Hemorrhage, Tetanus, Fracture, Asphyxia, Hysteria) are synced with `-1` (infinite) and removed when the flag clears.

### Entry points (`fabric.mod.json`)
| Side | Class | Role |
|---|---|---|
| main | `CombatInjuries` | registers items, effects, sounds, events, commands, loot, tab entries |
| client | `CombatInjuriesClient` | tick loop, HUD overlay element `combatinjuries:injury_overlays`, music folder setup |

---

## 3. Injury reference

All injuries are cleared by a **qualifying sleep** (§3.10) and by `/injurytest clear`.
"HP" below = half-hearts as Minecraft counts health (20 HP = 10 hearts).

### 3.1 Hemorrhage (bleeding)
| | |
|---|---|
| **Trigger A** | A **confirmed Sharpness hit** (rolled in `afterDamage`, not on the swing): the attacker's main-hand weapon has Sharpness and the hit dealt damage. Chance = `max(5%, HEMORRHAGE_BASE_CHANCE (50%) − 3%×(total Protection levels on armor) − 3% if Resistance)`. |
| **Trigger B** | Sustained contact damage from Cactus or Sweet Berry Bush: ≥ 100 ticks of continuous contact (gaps > 25 ticks reset the counter). |
| **Effect** | Custom damage `combatinjuries:hemorrhage`, **1 HP**, every **20 ticks** when standing still, every **10 ticks** when sprinting or moving horizontally (> 0.01 b/tick). |
| **Cure** | A single heal of **≥ 2 HP** (`HEMORRHAGE_CURE_HEAL`: Instant Health, healing potions), the Regeneration effect (incl. golden apples), or a qualifying sleep. **While bleeding, natural regeneration and small heals are blocked** (`BLEEDING_BLOCKS_REGEN`; the health gain is reverted each tick). Earlier builds cured on *any* health gain, so ordinary regen wiped the bleed within seconds. |
| **FX** | Hurt sound cue on start. Death message: "%s bled to death". |

### 3.2 Tetanus
| | |
|---|---|
| **Trigger** | Hit by a living entity whose main hand is a "rusty source" → **25%** chance. Rusty source = item carrying the `combatinjuries_rusty` custom-data flag, **any copper tool**, or an **iron non-armor item held by a Zombie** (`isRustySource`). |
| **Effect** | Every **300 ticks** the player **drops the item in hand**. If both hands are occupied it alternates main/off hand; if both are empty it retries every 20 ticks. |
| **Cure** | **Milk bucket** or **honey bottle** (consumed), via `MilkFinishUsingItemMixin`; qualifying sleep. |
| **FX** | Skeleton-hurt sound cue on start. |

### 3.3 Fracture
| | |
|---|---|
| **Trigger** | Fall damage ≥ 8 HP actually taken; or a Ravager melee attack while the Ravager's `attackTick > 0` — both only when the player's health is > 4 HP. |
| **Effect** | Movement speed **−40%** (`ADD_MULTIPLIED_TOTAL`), and **jumping is cancelled** (`LivingEntityInjuryMixin` at `jumpFromGround` HEAD). |
| **Cure** | Stand still on the ground (not sprinting, not turning the camera) for **200 ticks**; Regeneration; Hysteria (suppresses it); sleep. |
| **FX** | Bone-break sound cue on start. |

### 3.4 Concussion
| | |
|---|---|
| **Trigger** | While health > 4 HP: Falling Anvil, Sonic Boom, any Explosion, Mace smash with `fallDistance ≥ 3`, hit by an Iron Golem or Warden. |
| **Duration** | **300 ticks** (15 s). |
| **Effect** | Player's attacks **whiff 20% of the time** while health > 4 HP (`onAttack` returns `FAIL`). |
| **Cure** | Timer expiry; **any Golden Apple / Enchanted Golden Apple** (cleared the moment the item is *used*, not when finished eating); Hysteria; sleep. |
| **FX** | White flash (90% opacity, fading over ~0.9 s) + a persistent pulsing white haze (~24% opacity, scaled by time remaining). **Tinnitus** sound loops every 1.6 s. **All other game audio is muffled** and recovers over the last 100 ticks (see §8, `SoundEngineHysteriaMixin`). On start the client calls `soundManager.stop()` to cut currently playing sounds. |

### 3.5 Asphyxia
| | |
|---|---|
| **Trigger** | Air supply ≤ 0; or "buried" — `IN_WALL` damage repeatedly (>60 ticks, with gaps ≤ 20 ticks). |
| **Effect** | Custom damage `combatinjuries:asphyxia`, **1 HP / 20 ticks**, *on top of* vanilla drowning damage. |
| **Cure** | Air refills to max after having dropped; burial asphyxia ends when the suffocation contact stops; sleep. |
| **FX** | Drown-hurt cue. Pulsing dark edge-vignette built from 4 nested `fill()` frames. Death: "%s suffocated from asphyxia". |

### 3.6 Winded
| | |
|---|---|
| **Trigger** | Wind Charge damage; hit by a weapon with **Knockback ≥ II**; Ravager roar (`roarTick > 0`). |
| **Duration** | **200 ticks**; **halved** with Resistance or a full netherite set. |
| **Effect** | +0.02 food exhaustion every tick (food drains faster). |
| **FX** | Breath sound cue. |

### 3.7 Hysteria (the 2-heart frenzy)
| | |
|---|---|
| **Trigger** | Health ≤ **4.0 HP** (2 hearts). Automatic; ends when health rises above 4. |
| **Effect (server)** | +50% attack damage, +100% knockback resistance (i.e. immune to knockback), and it **clears** Fracture and Concussion while active. |
| **Effect (client)** | Hostile mobs' **nameplates / below-name scores are hidden** (`EntityRendererHysteriaMixin`); **sounds from aggressive mobs are ×3 louder** (capped at 1.0) (`SoundEngineHysteriaMixin`); **phantom sounds** (zombie, skeleton, creeper hiss, spider, enderman stare, witch, door-bash) play at random positions 4–14 blocks away every 3–12 s. |
| **FX** | Black flash on entry (92% → 0 over 320 ms); heavy **high-contrast darkened monochrome** filter (contrast 2.8, darkness 0.2) fading in over 1.6 s and out over 5 s; **Warden heartbeat** every 800 ms at pitch 0.72; random horizontal static lines drawn each frame (28 per frame). |

### 3.8 Adrenaline Rush & Crash
See §5 — it has its own section.

### 3.9 Other behaviour
- **Natural health regen clears Hemorrhage** because *any* health increase does (see 3.1).
- Regeneration effect clears Hemorrhage and Fracture each tick.

### 3.10 Rest: sleeping cures everything
- `BedRuleDaytimeMixin` wraps `Level.isDarkOutside()` in the bed rule so players can sleep **during the day** too (`isDarkOutside() || isBrightOutside()`).
- A sleep counts as "slept through" only if the player was in bed **≥ 100 ticks** *and* `ALLOW_RESETTING_TIME` fired (the world is skipping the night).
- On a qualifying wake-up (`STOP_SLEEPING`) **every injury is wiped** (all flags, timers, stored adrenaline debt) and the player enters **Deep Recovery**: for 400 ticks, +1 HP per second, each costing `6.0 × HP` food exhaustion, and only while food > 0.

---

## 4. Items, content and acquisition

### 4.1 Adrenaline Shot (`combatinjuries:adrenaline_shot`)
A plain `Item` (no custom Item subclass, no use animation, no stack-size override). Activation is handled by `UseItemCallback` — see §5.

| Source | Details |
|---|---|
| **Brewing** | Brewing stand: **Pufferfish** is accepted as an ingredient on a **Swiftness / Long Swiftness / Strong Swiftness** potion, producing an Adrenaline Shot (`BrewingStandBlockEntityMixin`, three `@WrapOperation`s on `isBrewable` / `doBrew`). |
| **Villager** | Cleric, **level 5**. Cost: **8 emeralds + 1 Swiftness potion** (one data file each for Swiftness, Long, Strong). |
| **Loot** | **1%** extra roll in: stronghold library / crossing / corridor, ancient city, ancient city ice box. |
| **Creative** | "Tools & Utilities" tab. |

### 4.2 Rusty tools (cosmetic + disease vector)
Ten items: `rusty_{iron,copper}_{sword,axe,pickaxe,shovel,hoe}`. They are **not new items** — they are vanilla tools carrying:
- custom data `combatinjuries_rusty: true`
- `ITEM_MODEL` component pointing at `combatinjuries:rusty_<tool>` (so a rusty texture shows)
- a translatable custom name

Created via `rustyCreativeStack()` (creative tab) and applied automatically to the **main-hand iron/copper tool of Zombies, Husks and Drowned when they load** (`markNaturallyHeldIronItems` on `ENTITY_LOAD`). Hits from these cause Tetanus (§3.2).

### 4.3 Commands (operator, permission ≥ Moderator)
```
/injurytest hemorrhage | tetanus | fracture | concussion | asphyxia | winded
/injurytest hysteria            sets your health to ≤ 4 HP
/injurytest adrenaline_rush     600-tick rush
/injurytest adrenaline_crash    300-tick crash
/injurytest rusty_zombie        spawns a Zombie 2 blocks away holding a random rusty tool
/injurytest clear               clears everything and restores full health
```

---

## 5. The Adrenaline system in detail

### 5.1 Lifecycle (current build)
> **Superseded once V2 is built** — see §11.5 for stacking shots, the timing QTE bar, cash-out/stun and overdose.

```
 right-click Adrenaline Shot
        │  (server, UseItemCallback — instant, no animation; plays the `adrenaline_inject` world sound)
        ▼
 consume 1 item; rushTicks = 600 (30 s); crashTicks = 0; storedDamage = 0
        │
        ▼  ─── ADRENALINE RUSH (600 ticks) ───────────────────────────────
        │  • Move speed  +55%   (ADD_MULTIPLIED_TOTAL, id adrenaline_rush_speed)
        │  • Attack dmg  +25%   (ADD_MULTIPLIED_TOTAL, id adrenaline_rush_damage)
        │  • EVERY time the player takes damage (afterDamage, not blocked):
        │        bank  = 0.5 × damageTaken   → storedAdrenalineDamage += bank
        │        heal(bank) immediately       (i.e. heals half of every hit)
        ▼  rushTicks reaches 0
 deal `storedAdrenalineDamage` as damage type combatinjuries:adrenaline_crash (the "bill")
 crashTicks = 300 (15 s)
        ▼  ─── ADRENALINE CRASH (300 ticks) ───────────────────────────────
        │  • Move speed −55% → −19% (tapers over the crash), plus: block-break −50%, attack speed −35%, jump −30% (all tapering), +0.04 food exhaustion/tick, heavy-breath sound every 2.6 s, dark pulsing edge vignette
        ▼
 normal
```
Death message if the bill kills you: "%s could not survive the adrenaline crash".

### 5.2 Constants
| Constant | Value | Where |
|---|---|---|
| Rush duration | 600 ticks | `onUseItem`, `runInjuryTest` |
| Crash duration | 300 ticks | `tickPlayer` (rush end), `runInjuryTest` |
| Rush speed / damage | +0.55 / +0.25 | `tickPlayer` |
| Crash speed | −0.55 tapering to −0.19 | `tickPlayer` |
| Heal-and-bank ratio | 0.5 | `afterDamage` |
| Crash filter fade-in / fade-out | 900 ms / 3500 ms | client |
| Rush filter fade-out | 2200 ms | client |
| Crash filter intensity ceiling | 0.65 (see JSON) | `adrenaline_crash_monochrome_*.json` |

### 5.3 Client presentation (current state)
| Phase | Visual | Audio |
|---|---|---|
| **Inject / rush start** | Red full-screen flash (86% → 0 over 0.9 s) | `adrenaline_inject` (server, world sound, played in `onUseItem`) then `adrenaline_powerup` (client) |
| **During rush** | **Half desaturation** (`adrenaline_monochrome_5`, intensity 0.5) + **pulsing red edge vignette** (HUD overlay, synced to the heartbeat; stays visible during Hysteria) | Looping WAV music (§5.4) + `adrenaline_heartbeat` every 600 ms |
| **Rush end** | Filter fades out over 2.2 s (levels 5→0) | Music stopped; `adrenaline_powerdown` plays |
| **Crash** | Desaturation ramps in over 0.9 s up to 0.65, scaled down as the crash timer drains | A breath cue (`PLAYER_BREATH`, pitch 0.65) on start |
| **Priority** | If Hysteria is also active, **the Hysteria filter wins** and the adrenaline filter is hidden. | |

### 5.4 Adrenaline music pipeline (this is unusual — read carefully)
Minecraft's sound system normally plays `.ogg` files declared in `sounds.json`. This mod instead plays a **`.wav`**, and also lets the player supply their own. How:

1. `sounds.json` declares event `combatinjuries:adrenaline_music` pointing at a tiny **silent placeholder file** `sounds/adrenaline_music_placeholder.ogg` with `"stream": true`. **Minecraft drops any `sounds.json` entry whose file does not exist, leaving the event empty and unplayable** — the original placeholder (`minecraft:block/note_block/harp`) did not exist, which was one of two reasons the music never played (§10.1). The placeholder's audio is never heard.
2. `AdrenalineMusicSoundInstance` extends `AbstractTickableSoundInstance` and implements Fabric's `FabricSoundInstance`, **overriding `getAudioStream(...)`** to hand the engine a custom `AudioStream` built from the WAV.
3. Source selection (`pickCustomMusic()`): any `*.wav` in **`<game dir>/config/ifaar/adrenaline_music/`** is used (random one if several), otherwise the bundled `assets/combatinjuries/sounds/adrenaline_music/adrenaline.wav`. A `README.txt` is auto-created in that folder telling players this.
4. `AdrenalineWavAudioStream` decodes the whole WAV to 16-bit PCM in memory (mono or stereo only) and serves it in chunks. **It loops itself**: `read()` wraps back to the start mid-buffer and never returns an empty buffer, so the engine's `LoopingAudioStream` is no longer used and the `looping` flag is ignored.
5. `SilentAudioStream` is the fallback if no WAV can be found/decoded.
6. Plays on `SoundSource.MUSIC` with `Attenuation.NONE` and `relative=true` (so it's non-positional and obeys the **Music** volume slider).

Bundled file: `adrenaline.wav`, **44.1 kHz, 16-bit, stereo, 2.567 s, ~453 KB**.

---

## 6. Client systems overview (`CombatInjuriesClient`)

- **`tickClient`** (every client tick): reads which effects the local player has, does edge detection, plays one-shot cues, starts/stops music, tracks fade timestamps, and calls `updateCameraPostEffect`.
- **`renderOverlays`** (registered as HUD element `combatinjuries:injury_overlays`, added last = drawn on top): draws concussion haze/flash, rush start flash, hysteria flash + static, asphyxia vignette, using `GuiGraphicsExtractor.fill()`.
- **Timing uses `System.currentTimeMillis()`**, not game ticks — so pulses/fades keep real-time pace even when the game lags or is paused.
- State is reset when the player is `null` (leaving a world).

---

## 7. Screen filters (post-effect shaders)

### 7.1 How it works and why there are 34 JSON files
There is **one shader** (`shaders/post/monochrome.fsh`) with three uniforms:

```glsl
float intensity;  // 0 = original colour … 1 = fully grey
float contrast;   // >1 pushes the grey toward black/white
float darkness;   // subtract from the grey value
// color = mix(original, grey(luminance→contrast→darkness), intensity)
```

Post-effect JSONs bake uniform values in statically, so a different strength needs a different JSON. The author pre-baked **11 steps (0–10) for each of three filters**, and the client picks `<name>_<round(level×10)>`:

| Family | intensity at step 10 | contrast | darkness | Used for |
|---|---|---|---|---|
| `hysteria_monochrome_N` | 1.0 | 2.8 | 0.2 | Hysteria |
| `adrenaline_monochrome_N` | 1.0 (but the client now requests level **5**, i.e. 0.5, during the rush; the fade-out also scales 5→0) | 1.0 | 0.0 | Adrenaline Rush |
| `adrenaline_crash_monochrome_N` | 0.65 | 1.0 | 0.02 | Adrenaline Crash |

(`monochrome.json` is a standalone full-strength version.) Each is a 2-pass chain: `main → scratch` (filter), then `scratch → main` (a no-op pass with intensity 0).

### 7.2 Applying a filter
`GameRendererAccessor` is a Mixin `@Invoker` for `GameRenderer.setPostEffect(Identifier)`. The client calls it only when the target ID **changes**, and restores vanilla behaviour with `gameRenderer.checkEntityPostEffect(...)`. If loading throws, `postEffectsBroken = true` and filters are disabled for the session (logged as `[IFAAR] Screen filter … failed to load`).

**Limitation that shapes the design:** uniforms are static per JSON, so *smoothly pulsing* a filter would mean swapping post-effect chains rapidly, which re-creates the chain each time and is expensive. This is why the red pulsing vignette in §11 is proposed as a **HUD overlay**, not a shader change.

---

## 8. Mixin reference

| Mixin | Target | What it does | Fragility |
|---|---|---|---|
| `BedRuleDaytimeMixin` | `net.minecraft.world.attribute.BedRule$Rule` (string target) | `@WrapOperation` on `test()` → `Level.isDarkOutside()` also true when `isBrightOutside()` | Newer bed-rule class; will break first if Mojang reshuffles |
| `BrewingStandBlockEntityMixin` | `BrewingStandBlockEntity` | 3 wraps: accept pufferfish as an ingredient; accept Swiftness+pufferfish as a valid mix; produce Adrenaline Shot in `doBrew` | Method names `isBrewable`/`doBrew` |
| `LivingEntityInjuryMixin` | `LivingEntity` | `@Inject` HEAD of `jumpFromGround()` → cancel if player is fractured | Low |
| `MilkFinishUsingItemMixin` | `Item` | `@Inject` HEAD of `finishUsingItem` → if milk bucket / honey bottle on server, cure tetanus | **Medium**: only fires for items that don't override `finishUsingItem` without calling super |
| `EntityRendererHysteriaMixin` (client) | `EntityRenderer` | 2 wraps in `extractNameTags` → hide name tag + score of aggressive mobs during Hysteria | Medium (render-state API is new) |
| `SoundEngineHysteriaMixin` (client) | `SoundEngine` | Wraps `getVolume()` in `play` and `calculateVolume()` in `tickInGameSound` → ×3 for aggressive-mob sounds in Hysteria; × `concussionFactor` for everything (muffle + tinnitus crossfade) | Medium |
| `GameRendererAccessor` (client) | `GameRenderer` | invoker for `setPostEffect` | Low |
| `EntityBoundSoundInstanceAccessor` (client) | `EntityBoundSoundInstance` | accessor for the `entity` field | Low |

Both mixin configs use `defaultRequire: 1` — **a mixin that fails to find its target crashes the game at launch** rather than silently skipping. Good for catching breakage, painful on version bumps.

---

## 9. Data & asset reference

- **Damage types** (`data/combatinjuries/damage_type/`): `hemorrhage`, `asphyxia`, `adrenaline_crash` — all `scaling: never`, `exhaustion: 0.1`. **No `bypasses_*` tags are set**, so armor *will* reduce them (see §10.6).
- **`no_knockback` tag** includes all three, so these never push the player.
- **Villager trade tag**: `tags/villager_trade/cleric/level_5.json` lists the three shot trades.
- **Lang** (`en_us.json`): item names, effect names, three death messages. Only English exists.
- **Textures**: `textures/item/adrenaline_shot.png`, nine `textures/mob_effect/*.png`. **Owner-supplied art:** the item is 16×16 and the nine effect icons are **18×18** (the vanilla effect-icon size). To change art, swap files with the same names.
- **Sounds** (`assets/combatinjuries/sounds/`): `concussion_tinnitus.ogg`, `adrenaline_inject.ogg`, `adrenaline_powerup.ogg`, `adrenaline_powerdown.ogg`, `adrenaline_heartbeat.ogg` (these four are **synthesized stand-ins** — replace with real recordings keeping the same filenames), `adrenaline_music_placeholder.ogg` (silent), and `adrenaline_music/adrenaline.wav` (the loop, §5.4). Server registers the four adrenaline events in `CombatInjuries` via `registerSound()`. Everything else still uses **vanilla sounds**: `WARDEN_HEARTBEAT` (hysteria), `PLAYER_HURT`, `SKELETON_HURT`, `BONE_BLOCK_BREAK`, `PLAYER_HURT_DROWN`, `PLAYER_BREATH`, plus the phantom list.

---

## 10. Known issues (priority order)

### 10.0 🔴 OPEN — Daytime sleeping: wakes instantly, doesn't advance the day
`BedRuleDaytimeMixin` makes beds *usable* in daytime, but the player is immediately woken, and the world does not skip time. Something else in 26.2's sleep logic (the per-tick wake-up check in `Player`/`ServerPlayer`, the time-skip / sleep-status code in `ServerLevel`, or the new clock/time-marker system) still treats daytime as "not sleeping time". **Cannot be fixed without reading the vanilla 26.2 source** (decompile `26.2.jar` or use Loom's `genSources`). Steps: find who calls `stopSleepInBed`/`stopSleeping` each tick and what condition triggers it; find where the night is skipped (`ClockManager`, `ClockTimeMarkers.WAKE_UP_FROM_SLEEP`); then add a `@WrapOperation`/`@Inject` for each daytime check, and decide how the daytime skip should work. Related: the sleep-recovery wipe now requires ≥100 ticks in bed plus `ALLOW_RESETTING_TIME` firing, so an instant wake can no longer cure anything (§10.7).

### 10.7 ✅ FIXED (untested) — Hemorrhage was cured just by lying in a bed
The wipe previously fired whenever `ALLOW_RESETTING_TIME` had been seen and the clock was at the wake-up marker. It now needs `sleepTicks >= 100` (counted in `tickPlayer`) **and** `deepSleepQualified`.

### 10.8 ✅ FIXED (untested) — Asphyxia from burial never ended
Burial suffocation does not touch the air meter, so the "air refilled" cure never fired. Burial asphyxia now sets `asphyxiaFromBurial` and ends when the player has been out of suffocating blocks for >20 ticks. Any `IN_WALL` damage now counts (previously only sand/gravel).

### 10.9 ✅ FIXED (untested) — Tetanus rarely/never applied by zombies
Cause: relied on an `ENTITY_LOAD` marker plus a flat 10% roll. Now `isRustySource()` also treats an iron non-armor item held by any `Zombie` (incl. Husk/Drowned) as rusty at hit time; chance is `TETANUS_CHANCE = 0.25`. Rusty tools now also get an `ITEM_MODEL` so they look rusty (§4.2).

### 10.10 ✅ FIXED — "Resource reload failed" crash on first adrenaline use, missing filters/icons
Cause: `build.gradle` generated the 33 post-effect JSONs and all textures at build time into `build/`; a hand-packed jar lacked them. Fix: **all are now real files in `src/main/resources`**; the generator task was removed. Post-effect uniform entries now include `name`. Filter loading failure is caught (`postEffectsBroken`) instead of crashing.


> Found by reading decompiled code. Not yet reproduced in-game. Class/field names refer to `CombatInjuriesClient` / `CombatInjuries`.

### 10.1 ✅ FIXED (verified in jar, play-untested) — Adrenaline music was stopped one tick after it started, and the sound entry was empty
**Was:** the rush-end branch in `tickClient` was `else if (adrenalineRushWasActive)`, which is true on every tick of a running rush, so the music was stopped ~50 ms after it began (and the filter-fade timestamp was overwritten each tick). Separately, the `sounds.json` placeholder did not resolve to a real file (§15).
**Now:** the branch is `else if (!rush && adrenalineRushWasActive)` (a true end-of-rush transition), and `sounds.json` points at the bundled `adrenaline_music_placeholder.ogg`.

### 10.2 ✅ FIXED (verified in jar, play-untested) — Looping: stream could not be marked/reset
**Was:** `AdrenalineWavAudioStream`'s try-with-resources closed the underlying stream, so vanilla's `LoopingAudioStream` could not `reset()` it; the non-looping path also passed an un-markable stream to `AudioSystem`.
**Now:** the source is wrapped in a `BufferedInputStream`, the WAV is decoded fully into memory, and `read()` serves it **circularly** (fills every buffer completely, wrapping at the end). The engine never sees end-of-stream, so there is no gap and no dependency on `LoopingAudioStream`. The seam is gapless only if the WAV itself loops cleanly (owner says it does).

### 10.11 🟡 Round-3 polish gaps (found by diffing the round-3 jar; still open after round 4 — round 4 also added a crash breathing loop every 2.6 s, so gap 2 now also involves that)
1. **Vignette appears late and vanishes abruptly.** It only draws when `lastHeartbeatAt > 0`, i.e. after the *first* heartbeat (~0.7 s after the rush starts), and `lastHeartbeatAt` is reset to 0 the moment the rush ends, so it **snaps off with no fade-out** (the filter does fade over 2.2 s). Fix: draw from rush start using the fade-in timer, and on rush end keep drawing with a fade based on `adrenalineFilterEndedAt`.
2. **Power-down and crash-breath cues stack.** The vanilla `PLAYER_BREATH` cue (§5.3, crash start) fires on the same tick the `adrenaline_powerdown` sound starts. Delay or soften the breath.
3. **Personal cues are positional.** `playCue()` builds a `SimpleSoundInstance` at the player's coordinates, so a sound that lasts over a second (power-up 1.3 s, power-down 1.8 s) is left behind when the player runs at +30% speed and can drift/pan. Prefer a relative (non-positional) instance for "inside your head" cues.
4. **Heartbeat is a single decaying pulse**, not the lub-dub shape suggested in the original design (§11.4) — a stylistic choice, easy to upgrade.
5. The vignette is drawn as 8 nested `fill()` frames (visibly stepped); the gradient-PNG upgrade remains optional.

### 10.3 🟠 Re-injecting during a rush erases the crash bill
`onUseItem` sets `storedAdrenalineDamage = 0` and `adrenalineCrashTicks = 0` every time a shot is used. A player can therefore chain shots to **wipe their accumulated debt and skip the crash entirely**. Probably unintended. **Resolved by design in §11.5** (debt accumulates across a chain, and the timing/danger-zone rules govern when a shot may be used).

### 10.4 🟠 The adrenaline "bill" is reduced by armor
`adrenaline_crash` has no `bypasses_armor` tag (nor `bypasses_effects`/`bypasses_resistance`), so armor, Resistance and enchantments soften the debt payment. If the intent is "you pay back exactly what you borrowed", add the vanilla tags `bypasses_armor`, `bypasses_enchantments`, `bypasses_resistance` for `combatinjuries:adrenaline_crash` in `data/minecraft/tags/damage_type/`. (Same question applies to hemorrhage and asphyxia.)

### 10.5 🟡 State is never reset on death, logout, or world change
`STATES` is an in-memory map keyed by UUID and is never cleared. Consequences: injuries and timers **survive death** (a respawned player can still be fractured/tetanus'd; effects are simply re-added by `syncEffect`), nothing is **persisted across relog/server restart**, and the map slowly grows. Consider: reset on `ServerPlayerEvents.AFTER_RESPAWN`, remove on `ServerPlayConnectionEvents.DISCONNECT`, and decide whether injuries should persist (if so, save to player NBT/attachment).

### 10.6 🟡 Smaller observations
- Concussion golden-apple cure fires at **use start**, so it's cured even if the player cancels eating.
- Hemorrhage chance is rolled on swing, not on a confirmed hit, and `AttackEntityCallback` runs for every swing attempt.
- `isBuriedInSandOrGravel` / `isSandOrGravel` are unused (burial asphyxia now counts any `IN_WALL` suffocation). `TETANUS_CHANCE` **is** used; the decompiler inlines `static final` constants, which is why it showed as a literal `0.25`.
- `pickCustomMusic()` is called twice (`hasAdrenalineWave` and `getAudioStream`); with several custom WAVs, the existence check and the played file can differ. Harmless, but pick once.
- The music instance relies on Fabric's `FabricSoundInstance.getAudioStream` override plus a silent placeholder file (§5.4). It works only as long as Fabric keeps calling `getAudioStream` for streamed sounds — see §11.1 for the pure-OGG alternative.
- Adrenaline filter is hidden while Hysteria is active (by design of the `if/else if` chain); the new vignette (§11.4) should be an overlay so it stays visible in that case.

---

## 11. Pending work / design notes from the latest review round

The owner's review said the build is "almost everything up to what I expected" and listed the following. (The textures item is **resolved** — replaced by the owner.)

### 11.1 ✅ DONE (verified in jar, play-untested) — Fix the adrenaline music + make it loop
Implemented as §10.1 + §10.2 (WAV kept; self-looping stream). Optional long-term alternative: convert the loop to `.ogg`, declare it normally in `sounds.json` with `"stream": true`, and delete the custom `AudioStream` classes — but many OGG encoders add padding at the loop point, and the WAV path is what enables the "drop your own `.wav` into `config/ifaar/adrenaline_music/`" feature.

### 11.2 ✅ DONE (verified in jar, play-untested) — Adrenaline sound effects
| Moment | Sound (event) | Plays | Status |
|---|---|---|---|
| Injection | `adrenaline_inject` (0.45 s) | **Server** world sound in `onUseItem` (`SoundSource.PLAYERS`, heard by nearby players and the user) | ✅ |
| Power-up | `adrenaline_powerup` (1.3 s) | Client, on rush start | ✅ |
| Rush loop | music WAV | Client | ✅ |
| Heartbeat | `adrenaline_heartbeat` (0.55 s), every 600 ms, volume 0.9 | Client, while rushing | ✅ |
| Power-down | `adrenaline_powerdown` (1.8 s) | Client, on rush end (also on `/injurytest clear` / sleep wipe) | ✅ |

All four are **synthesized stand-ins** (mono, 44.1 kHz OGG) registered through `registerSound()` in `CombatInjuries`; replace with real recordings using the same file names. Known polish gaps are in §10.11. Note the event IDs are `adrenaline_powerup` / `adrenaline_powerdown` (no underscore before `up`/`down`).

### 11.2b Master SFX spec — replacing the "make-do" vanilla sounds

> **Status:** only the adrenaline rows `adrenaline_inject`, `adrenaline_power_up` (shipped as `adrenaline_powerup`), `adrenaline_power_down` (shipped as `adrenaline_powerdown`) and `adrenaline_heartbeat` are done. Everything else in this table (other injuries, crash, QTE, overdose, stun, hysteria, cure) is **still using vanilla sounds or has no sound**.

Every cue below currently reuses a vanilla sound (or nothing). This table is a brief for whoever sources/records audio, and a to-do list for the programmer. **Naming rule:** file `assets/combatinjuries/sounds/<id>.ogg` ⇄ sound event `combatinjuries:<id>` ⇄ entry in `sounds.json`.

**Format guidance:** OGG Vorbis, 44.1 kHz. **Mono** for anything played *in the world* (so Minecraft can position/attenuate it); **stereo** for personal/"inside your head" cues. Normalise to roughly −3 dB peak; leave headroom — Minecraft's sliders scale from there. Keep one-shots under ~2 s unless stated.

| ID | Trigger (where in code) | Currently | Proposed character | Length | Mono/Stereo | Plays for |
|---|---|---|---|---|---|---|
| `adrenaline_inject` | shot used (`onUseItem`) | silent | needle pierce + plunger click + short wet thump | 0.5–0.8 s | mono | everyone nearby (server) |
| `adrenaline_power_up` | rush starts (client) | silent | rising whoosh/tonal swell that peaks into the music loop | 1–2 s | stereo | self |
| `adrenaline_music` | rush loop | WAV (broken, §10) | the existing 2.57 s loop | loop | stereo | self |
| `adrenaline_heartbeat` | every beat during rush | vanilla Warden heartbeat | fast, bright, "pounding" double thump; **distinct from hysteria's** | ~0.4 s | stereo | self |
| `adrenaline_power_down` | rush ends | silent | falling/deflating sweep, tape-stop feel | 1–1.5 s | stereo | self |
| `adrenaline_crash` | crash starts | vanilla player breath | heavy exhale + low thud, muffled | ~1.2 s | stereo | self |
| `adrenaline_qte_open` | QTE window opens (V2) | — | short sharp tick/"ready" ping | 0.2 s | stereo | self |
| `adrenaline_qte_hit` | QTE success (V2) | — | satisfying click-thud + upward chime; **pitch up per stack** | 0.4 s | stereo | self |
| `adrenaline_qte_fail` | window missed (V2) | — | dull "empty" click | 0.3 s | stereo | self |
| `adrenaline_overdose` | OD death (V2) | — | heart stutter/flatline sting, abrupt cut | 1.5 s | stereo | self + world |
| `adrenaline_stun` | stun begins (V2) | — | ringing slam + sinking low-pass drone | 1–2 s | stereo | self |
| `hemorrhage_start` | bleeding begins | vanilla player hurt | wet gash/slice + short pained grunt | ~0.7 s | mono | world |
| `hemorrhage_tick` *(new, optional)* | each bleed damage | vanilla hurt | soft drip/squelch, low volume | 0.3 s | mono | world |
| `tetanus_start` | tetanus begins | vanilla skeleton hurt | sick muscle-spasm: dry crunch + shaky breath | ~1 s | stereo | self |
| `tetanus_drop` *(new, optional)* | item dropped by tetanus | vanilla drop | involuntary clatter + grunt | 0.5 s | mono | world |
| `fracture_start` | bone breaks | vanilla bone block break | sharp bone snap + stifled cry | ~0.8 s | mono | world |
| `concussion_hit` *(new)* | concussion begins | vanilla stop-all + tinnitus | heavy impact thump + sudden ring that hands over to the existing tinnitus | ~1 s | stereo | self |
| `concussion_tinnitus` | loop while concussed | **exists** | keep | existing | — | self |
| `asphyxia_start` | air out / buried | vanilla drowning hurt | choking gasp + strained wheeze | ~1 s | mono | world |
| `asphyxia_loop` *(new, optional)* | while asphyxia lasts | — | ragged laboured breathing, ~1.6 s loop | loop | stereo | self |
| `winded_start` | winded | vanilla breath | knocked-out-of-lungs gasp | ~0.8 s | mono | world |
| `hysteria_enter` *(new)* | hysteria starts | black flash only | short dissonant sting / sharp inhale | ~0.8 s | stereo | self |
| `hysteria_heartbeat` | every 800 ms | vanilla Warden heartbeat @0.72 pitch | slow, heavy, thudding — the "dying" heart | ~0.7 s | stereo | self |
| `injury_cured` *(new)* | qualifying sleep wipes injuries | silent | warm relief exhale / soft chime | ~1.5 s | stereo | self |

Kept as vanilla on purpose: the **hysteria phantom sounds** (zombie, skeleton, creeper hiss… — they *must* sound like real mobs).

**Programmer notes**
- Registration is one line per sound; add a helper next to `CONCUSSION_TINNITUS` in `CombatInjuries`:
  ```java
  private static SoundEvent sound(String id) {
      Identifier key = Identifier.fromNamespaceAndPath(MOD_ID, id);
      return Registry.register(BuiltInRegistries.SOUND_EVENT, key, SoundEvent.createVariableRangeEvent(key));
  }
  public static final SoundEvent ADRENALINE_INJECT = sound("adrenaline_inject"); // …etc
  ```
  and one `sounds.json` block per ID: `"adrenaline_inject": { "sounds": [ "combatinjuries:adrenaline_inject" ] }`. Add `"subtitle"` keys + lang entries if you want subtitles for accessibility.
- **Server-side cues** (those heard by *others*: inject, hemorrhage_start, fracture_start, asphyxia_start, winded_start, overdose) should be played from the server with `level.playSound(...)`. **Client-side cues** (everything "inside your head") use the existing `playCue()`. Right now *all* are client-side `playCue()` calls, so other players hear none of them — decide per row if that's intended.
- The `SoundEngineHysteriaMixin` muffles **every** sound except tinnitus while concussed. New cues will be muffled too, which is probably correct; it's only wrong for `concussion_hit`/`adrenaline_overdose` if you want them to punch through — add them to the exception list next to `isTinnitusSound`.
- Replace cues by swapping the `SoundEvents.X` argument for your new `SoundEvent` in the `playCue(...)` calls in `tickClient`; no other logic changes.

### 11.3 ✅ DONE (verified in jar, play-untested) — Half-strength desaturation
Implemented in code, with no JSON regeneration: during the rush the client requests `adrenaline_monochrome_5` (file intensity **0.5**, contrast 1.0, darkness 0.0), and the end-of-rush fade steps `round(fade × 5)` from level 5 down to 0, so the fade is consistent with the new ceiling. (Levels 6–10 of the `adrenaline_monochrome_*` family are now unused. To tune the strength, change the `5` in both places in `CombatInjuriesClient.updateCameraPostEffect`.)

### 11.4 ✅ DONE (verified in jar, play-untested; polish gaps in §10.11) — Pulsing red vignette synced to a heartbeat
- **Heartbeat:** `adrenaline_heartbeat` plays every **600 ms**, first beat 700 ms after rush start, from `adrenalineBeatAt` / `lastHeartbeatAt` in `tickClient`.
- **Vignette:** drawn in `renderOverlays` (a HUD overlay, so it still shows during Hysteria) as **8 nested `fill()` frames**, colour `0xB0101A`. Strength = `(0.16 + 0.30 × pulse) × fadeIn`, where `pulse = (1 − msSinceBeat/480)²` (sharp attack on the beat, decaying) and `fadeIn` ramps over 600 ms. Max alpha ≈ 0.46 at the outer layer, tapering inwards.
- Using the same `lastHeartbeatAt` timestamp for both sound and visual keeps them in sync.
- Upgrade path (optional): a soft radial-gradient PNG instead of stepped frames, a lub-dub pulse shape, and the fixes in §10.11.

### 11.5 Adrenaline V2 — stacking shots, timing QTE, overdose  *(BUILT in round 5, untested in-game; numbers live in `AdrenalineRules.java`. Deviations from this spec: no vanilla item cooldown rendering, the bar shows the cooldown instead; a Totem of Undying saves the player from an overdose but leaves a 8 s stun, ends the rush, wipes the debt and locks shots for 60 s; creative players take the penalty but not the death; no new QTE sounds yet — the heartbeat and inject sounds are reused.)*

This replaces the simple "use shot → rush → crash" flow in §5.1. It also fixes §10.3 (re-injecting no longer wipes the debt — debt now *accumulates* across the chain).

#### 11.5.1 Owner's intent (as stated)
- A further shot can **extend** the rush. Up to **4 shots** in a chain; a 5th shot = **overdose → instant death**.
- **Owner clarification:** at stack 4 the QTE bar still appears, but **in red**. It looks like another opportunity, but *succeeding* it means injecting a 5th shot = overdose. It is a **temptation trap**: the correct play at stack 4 is to do nothing and let the rush run out (cash-out).
- With **1–3** shots you get the existing crash (bill paid back as damage + slowdown), but with **more than one shot the crash value is larger**.
- If you **successfully chain 4 shots**, you **negate all the stored damage**, but are **stunned** for a while.
- A **side bar acts as a quick-time event (QTE)**: it tells you when to inject the next shot. **Miss it → normal crash. Inject too early → overdose.**
- Shots get a **cooldown like an ender pearl / chorus fruit**. The QTE happens **right near the end of the rush**.

#### 11.5.2 State machine

```
            use shot (n=0)                       QTE success (n<4)
   IDLE ───────────────────► RUSH(n=1) ──────────────────────────► RUSH(n+1)  (rush timer extended, debt KEPT)
                               │  │                                    │
        rush runs out,         │  │ injected in DANGER zone            │ n reaches 4 and rush runs out
        no shot pressed        │  ▼                                    ▼
                               │  stack ≥ 3 ─► OVERDOSE (death)     CASH-OUT: debt = 0, STUN
                               │  stack ≤ 2 ─► SHOCK (short stun + crash starts, shot wasted)
                               ▼
                           CRASH(n)  ← bill × crashMult(n), slowdown scaled by n
                               ▼
                             IDLE
        RUSH(4): the bar turns RED; pressing the shot (window or danger zone) ──► OVERDOSE (always lethal)
```

#### 11.5.3 Zones inside one rush segment
Time runs left → right through a segment of length `L` ticks:

```
 |── COOLDOWN ──────────|──── DANGER (early) ────|──── WINDOW ────|
 shot used              item usable again         QTE opens         rush ends
                        (pressing here = bad)     (pressing = good)  (not pressed = crash)
```

| Zone | Pressing the shot… |
|---|---|
| **Cooldown** | does nothing (item greyed, like an ender pearl). Harmless. |
| **Danger** | **Too early** → Shock (stack ≤ 2) or **Overdose** (stack ≥ 3). |
| **Window** | **Success** → +1 shot, timer extended, bill carries over. |
| **Rush ends, nothing pressed** | Crash, scaled by stack — *or* cash-out if the stack is 4. |

> ⚠️ **Design tension to resolve:** if the cooldown lasts until the window, the "too early" case can never happen (the item can't be used). For "too early = overdose" to exist, the **cooldown must end *before* the window opens**, leaving a **danger zone** in between. Proposed starting values below keep the cooldown short enough to leave a real danger zone.

#### 11.5.4 Starting values (all tunable — put them in one shared constants class)

| Constant | Start value | Meaning |
|---|---|---|
| `MAX_SHOTS` | 4 | the 5th shot is always an overdose |
| `SEGMENT_TICKS` | 600 for shot 1; 400 for each extension | length of each rush segment |
| `COOLDOWN_TICKS` | 200 (10 s) | item lockout after each injection |
| `WINDOW_TICKS` | 60 (3 s) | QTE open at the end of the segment |
| Danger zone | from cooldown end up to window start (≈ 140–300 ticks) | pressing = penalty |
| `LATENCY_GRACE_TICKS` | 3 | the window is widened by this at *both* edges on the server |
| `crashMult[n]` | 1.0 / 1.5 / 2.0 (n = 1/2/3) | scales stored bill (and optionally crash slowdown/duration) |
| `STUN_TICKS` (cash-out) | 80–100 (4–5 s) | reward-with-cost for a perfect chain |
| `SHOCK_STUN_TICKS` (early, low stack) | 40 (2 s) | |
| Heal-and-bank ratio | 0.5 (unchanged) | |

Keep the **debt cumulative**: `storedAdrenalineDamage` is no longer reset on each shot; it keeps growing through the chain, which is what makes the cash-out (debt → 0) the jackpot.

#### 11.5.5 Penalty matrix (answer to the owner's question)

| Situation | Stack 1 | Stack 2 | Stack 3 | Stack 4 |
|---|---|---|---|---|
| **Press in WINDOW** | → stack 2 | → stack 3 | → stack 4 | n/a (see next row) |
| **Press during the RED window (or any time after cooldown) at stack 4** | — | — | — | **Overdose (death)** — the red bar is the trap |
| **Press in DANGER (too early)** | Shock (stun 2 s + crash) | Shock | **Overdose (death)** | Overdose |
| **Don't press (rush ends)** | Crash ×1.0 | Crash ×1.5 | Crash ×2.0 | **Cash-out: debt 0, stun 4–5 s** |

Rationale: death should be reserved for *greed* (pressing too early, or a 5th shot), because the player can see and avoid it. A *missed* window is a skill/ping failure, and already punished hard — at stack 3 the ×2.0 bill will often kill by itself. The "failure" at stack 1 is not really a failure: simply not injecting again is the normal one-shot flow.

**Assumptions to confirm** (defaults chosen so the spec is complete): "about four shots" is taken as **exactly 4**; the "stun" payoff is the *only* thing you keep after a perfect chain; "increased crash value" scales the **bill**; slowdown strength/duration scaling is optional.

#### 11.5.6 Side bar (client HUD)
- A **vertical bar on the right edge** (mid-height), drawn as a HUD element alongside `injury_overlays`.
- It maps the current segment's timeline: **grey** = cooldown, **orange** = danger, **green** = window; a **marker drains** as the rush counts down.
- **At stack 4 the whole bar turns red** (the window is red instead of green, with a harsher pulse and a warning tone). Pressing in it = overdose; ignoring it = cash-out.
- **4 pips/ticks** beside it show the current stack; they fill as you chain.
- On the window opening: play `adrenaline_qte_open`, bar pulses. On success: flash green + `adrenaline_qte_hit` (pitch rises with stack). On miss: flash grey + `adrenaline_qte_fail`.
- It should stay visible alongside the Hysteria filter (it's an overlay, not a shader).

#### 11.5.7 Implementation plan (keeps the existing architecture)

**Server (`CombatInjuries`)**
- `InjuryState`: add `int rushShots`, `int rushSegmentTicks`, `int stunTicks`; keep `adrenalineRushTicks` as "ticks left in the current segment"; stop resetting `storedAdrenalineDamage` on use.
- `onUseItem` becomes the **decision point**: compute zone from `adrenalineRushTicks` (and cooldown) and branch per §11.5.5. This is where overdose/shock/extension are decided — **the server decides, never the client.**
- `tickPlayer`: on segment end → if `rushShots == MAX_SHOTS` → cash-out (debt=0, `stunTicks`); else crash with `crashMult[rushShots]`.
- **Stun** (new): while `stunTicks > 0`: speed modifier −1.0 (can't walk), cancel attacks/use (`AttackEntityCallback`/`UseItemCallback` return `FAIL`), jump already cancellable in `LivingEntityInjuryMixin`.
- **Cooldown**: use the vanilla cooldown system so it renders like an ender pearl (`player.getCooldowns()…` or the item's `USE_COOLDOWN` component — **check the 26.2 API**).
- **Overdose**: new damage type `combatinjuries:adrenaline_overdose` (JSON + lang `death.attack.combatinjuries.adrenaline_overdose` = "%1$s overdosed on adrenaline") tagged `bypasses_armor`, `bypasses_effects`, `bypasses_resistance`, `bypasses_enchantments` and **`bypasses_invulnerability`** (so a Totem of Undying can't save you). Deal `Float.MAX_VALUE`-style damage. Exclude creative/spectator players from dying (still show the FX).
- New effect(s) for the HUD icon: `adrenaline_stun` (register + 16×16 texture + `effect.combatinjuries.adrenaline_stun` lang).

**Syncing to the client without a packet:** the client already sees the rush `MobEffectInstance`. Encode the **shot count in the effect's amplifier** (`new MobEffectInstance(RUSH, ticksLeft, shots-1, …)`) and derive the zones from the remaining `duration` and shared constants. Note `syncEffect()` currently compares only duration and never updates the amplifier — it must also re-add the effect when the amplifier changes. If you later need the cooldown, exact window state or stun timer on the HUD, switch to a small Fabric **custom payload** instead.

**Shared code:** put the constants and a pure function `zoneFor(ticksLeft, shots)` in a common class (e.g. `AdrenalineRules`) used by both server decisions and client rendering, so the bar and the rule can never disagree.

**Client (`CombatInjuriesClient`)**
- New HUD element for the bar (+ pips); edge-detect `shots` changes for sounds.
- Extend the rush music/filter logic: **do not stop/restart the music or filter between chained segments** (check that an amplifier change doesn't look like "rush ended then restarted").
- Optional: raise the heartbeat tempo or the vignette intensity with each stack so the player *feels* the danger.

**Commands to add to `/injurytest`:** `adrenaline_shots <n>` (set stack), `adrenaline_stun`, `overdose`, `qte` (jump to window open) — otherwise testing this takes minutes per attempt.

#### 11.5.8 Edge cases the implementer must decide
1. **Logging out mid-chain.** State isn't persisted (§10.5), so a player could **log out to dodge the crash bill or the stun**. With V2 this becomes an exploit worth fixing: persist the state, or apply the pending result on disconnect.
2. **Dying/respawning mid-chain**: reset everything (§10.5).
3. **Sleeping**: the existing "slept through" wipe should reset the chain too (it already zeroes the rush timers).
4. **Hysteria while rushing**: the filter is overridden but the QTE bar must still show.
5. **Multiplayer fairness**: stun in PvP is extremely strong; consider a short post-stun immunity.
6. **Latency**: always judge on the server, with `LATENCY_GRACE_TICKS` at both edges; never trust a client-side "I hit it".
7. **Shots in creative**: `consume()` already skips item loss; decide whether creative players can overdose.
8. **Tuning feel**: a 4-shot chain with these numbers is ~600 + 3×400 = 1800 ticks (90 s) of rush — check that this isn't too long/strong before balancing the bill.

### 11.6 Chronological history
All on **2026-10-07** unless noted. "Owner" = project owner (non-programmer); "Round" numbering is this document's own.

| Round | Change | Why | Verified? |
|---|---|---|---|
| 0 | Mod built with the Kodari AI builder (credits ran out); jar handed over | — | Owner playtested: list of 9 bugs |
| 1 | Jar decompiled, project reconstructed; 9 fixes: static post-effects + textures; tinnitus sound file; concussion fade; crash-filter fade tied to remaining time; burial asphyxia; sleep/hemorrhage; tetanus; hysteria phantom sounds; custom music folder | Owner's bug list | Compile-only |
| 2 | Rusty tool models (tinted vanilla textures); `gradle.properties`; GitHub Actions workflow; build errors fixed in order: (a) `settings.gradle` `FAIL_ON_PROJECT_REPOS` blocked Loom's repository; (b) decompiler wrote `this instanceof Player` in the mixin | First real build | **Built OK; owner playtested: "almost everything up to expectation"** |
| 2 feedback | Icons/textures: owner will supply their own art. Adrenaline music did not load. Wants loop until rush ends; inject/power-up/power-down SFX; rush filter ~half desaturated; red pulsing vignette + heartbeat | Playtest | — |
| 3 | Music root causes fixed (stop-every-tick `else if`; nonexistent placeholder file; unmarkable stream); self-looping stream; 4 new synthesized sounds + registration; inject sound on server; filter level 5; vignette; owner-supplied textures (effect icons 18×18) | Round-2 feedback | **Built OK; owner played it: music works** (gaps: §10.11) |
| 3 feedback | Music was cancelled when a concussion started; hemorrhage unreliable/not sticking; rush not fast enough; jumping killed rush momentum; crash lacked weight | Playtest | — |
| 4 | Music restarts muffled (18% → 100%) through a concussion instead of dying; hemorrhage rolled on confirmed Sharpness hits (50%), cured only by ≥2 HP heals/Regeneration/sleep, natural regen blocked while bleeding; rush speed +55% and client-side airborne momentum boost; crash tapers −55%→−19% with block-break/attack-speed/jump penalties, extra hunger drain, breathing loop and dark vignette; fracture jump-block also checks the synced effect (client has no server state) | Round-3 feedback | **Not built or played yet** |
| — | Adrenaline V2 (§11.5) designed, not implemented | Owner wants it next | — |

### 11.7 Adrenaline music pool — shuffle bag of 7 loops — *BUILT in round 7 (the patch sketch below is the original design; the real code is `trackPool()`/`nextTrack()` in `CombatInjuriesClient`)*

**Current behaviour (from the jar):** `pickCustomMusic()` picks a random `.wav` from `config/ifaar/adrenaline_music/` and, if that folder has any, it **replaces** the bundled track entirely. The bundled track is a single hard-coded resource (`adrenaline.wav`). The pick happens **twice** per rush (`hasAdrenalineWave()` and again inside `getAudioStream`), so the file checked and the file played can differ.

**Goal:** a pool of bundled loops plus any player-supplied loops, with **one pick per rush**, played as a **shuffle bag** (every track once before any repeat), and the chosen track kept for the whole rush.

#### Bundled tracks (cleaned set, `IFAAR_adrenaline_music_pool.zip`)
Put these in `assets/combatinjuries/sounds/adrenaline_music/` and list them in `tracks.txt` (one per line). Delete the old `adrenaline.wav` — `ultrakill_187.wav` is the identical audio.

| File | Length | BPM | Notes |
|---|---|---|---|
| `breakcore_140.wav` | 6.854 s | 140 | 2.9 ms seam crossfade |
| `distorted_165.wav` | 11.636 s | 165 | longest (~2 MB) |
| `jubilation_169.wav` | 5.652 s | ~169 | silent tail trimmed, +6.6 dB |
| `glitched_170.wav` | 5.644 s | 170 | 2.9 ms seam crossfade; check the loop point by ear |
| `ultrakill_187.wav` | 2.567 s | ~187 | the previous bundled loop |
| `uptempo_193.wav` | 4.974 s | 193 | |
| `hardcore_204.wav` | 9.412 s | 204 | |

All seven are 16-bit stereo 44.1 kHz, matched to **≈ −14 dB RMS with peaks ≤ −1 dBFS**; total ≈ 8 MB. Any new loop should be normalised to the same level or the volume will jump between rushes.

#### Behaviour
1. **Pool** = bundled tracks (from `tracks.txt`) **plus** every `*.wav` in `config/ifaar/adrenaline_music/` (player loops are *added*, not an override). Optional: a flag to use custom-only.
2. **Shuffle bag:** shuffle the pool, play through it one track per rush, then reshuffle. The first track of a new round must differ from the last track of the previous round. With 7 tracks a given track can't return for at least 6 rushes (except across a round boundary, where the minimum gap is 1 rush).
3. **Pick once** at rush start; hand the chosen `Track` to the sound instance. The track does **not** change mid-rush (including shot extensions in V2) unless V2 stack-tiering is implemented (below).
4. The bag state is client-side and resets when the game restarts; if the pool changes (files added/removed) rebuild the bag.
5. A pool of 1 simply repeats; a pool of 0 falls back to `SilentAudioStream` (no crash).

#### Optional V2 tie-in: tempo ≈ intensity
Tracks span 140 → 204 BPM, so they can be tiered by shot stack: stack 1 → `breakcore_140`; stacks 2–3 → the 165–193 group; stack 4 (the red trap bar) → `hardcore_204`. Needs a short crossfade between tracks when the stack changes, and `name|minStack` entries in `tracks.txt`. Not required for the shuffle bag.

#### Patch sketch (`CombatInjuriesClient`; untested — adapt names to the real file)
```java
@FunctionalInterface private interface Opener { InputStream open() throws IOException; }
private record Track(String name, Opener opener) {}
private static final String MUSIC_BASE = "assets/combatinjuries/sounds/adrenaline_music/";
private static final Deque<Track> musicBag = new ArrayDeque<>();
private static String lastTrack;

private static List<Track> trackPool() {
    List<Track> pool = new ArrayList<>();
    ClassLoader cl = CombatInjuriesClient.class.getClassLoader();
    try (InputStream in = cl.getResourceAsStream(MUSIC_BASE + "tracks.txt")) {            // bundled
        if (in != null) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String n = line.trim();
                if (n.toLowerCase(Locale.ROOT).endsWith(".wav"))
                    pool.add(new Track(n, () -> cl.getResourceAsStream(MUSIC_BASE + n)));
            }
        }
    } catch (IOException ignored) {}
    Path folder = musicFolder();                                                            // player-supplied
    if (Files.isDirectory(folder)) {
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav")).sorted()
                 .forEach(f -> pool.add(new Track("config:" + f.getFileName(), () -> Files.newInputStream(f))));
        } catch (IOException ignored) {}
    }
    return pool;
}

private static Track nextTrack() {                     // shuffle bag
    if (musicBag.isEmpty()) {
        List<Track> pool = trackPool();
        if (pool.isEmpty()) return null;
        Collections.shuffle(pool);
        if (pool.size() > 1 && pool.get(0).name().equals(lastTrack))   // no repeat across the round boundary
            Collections.swap(pool, 0, pool.size() - 1);
        musicBag.addAll(pool);
    }
    Track t = musicBag.poll();
    lastTrack = t.name();
    return t;
}
```
- At rush start, where the code does `if (hasAdrenalineWave()) { …new AdrenalineMusicSoundInstance()… }`, use `Track t = nextTrack(); if (t != null) { …new AdrenalineMusicSoundInstance(t)… }`; delete `hasAdrenalineWave()` and `pickCustomMusic()`.
- `AdrenalineMusicSoundInstance` takes a `Track`; in `getAudioStream` replace the `pickCustomMusic()` / resource logic with `InputStream in = track.opener().open();` (null → `SilentAudioStream`). The self-looping `AdrenalineWavAudioStream` is unchanged.
- Update the auto-generated config-folder `README.txt` to say custom loops are **added** to the pool and should be level-matched (≈ −14 dB RMS) and seamless.

#### Constraints to respect
- Each track is decoded **fully into memory** (16-bit PCM, ~10 MB/min of stereo 44.1 kHz); only the selected track is decoded, so the longest bundled loop (11.6 s ≈ 2 MB) is fine. Keep loops under about a minute.
- Only mono/stereo 16-bit-convertible WAV is supported. Loops must be **seamless** — the stream wraps with no gap, so a click at the seam repeats audibly.
- Uncompressed WAV adds ≈ 8 MB to the jar for this set.
- The rush heartbeat (600 ms ≈ 100 BPM) will not line up with 140–204 BPM loops; expected, but worth knowing when tuning the vignette/heartbeat feel.

---

## 12. Testing checklist (everything below uses `/injurytest`)

| # | Test | Expected |
|---|---|---|
| 1 | `/injurytest adrenaline_rush` | Red flash; **music plays continuously and loops with no audible gap until the rush ends**; desaturated (not full grey) filter; heartbeat + red pulse (after §11.4) |
| 2 | Wait out the rush (or use the Adrenaline Shot item) | Power-down SFX; filter fades over ~2 s; music stops; crash begins (slow, breath cue) |
| 3 | Use a shot item in survival | Count decreases by 1; inject SFX; *(after §10.3)* re-injecting behaves as designed |
| 4 | During a rush take hits | Health goes up by ~half of each hit; after rush ends you take the banked total as "could not survive the adrenaline crash" if lethal |
| 5 | `/injurytest hysteria` while rushing | Hysteria filter overrides; vignette overlay still shows |
| 6 | Drop a custom `.wav` in `config/ifaar/adrenaline_music/` | It's picked instead of the bundled file |
| 7 | `/injurytest clear` mid-rush | Music stops, power-down SFX plays, no stuck filter |
| 8 | Die during a rush/injury, respawn | *(See §10.5)* currently stays injured — verify and decide |
| 9 | Brew pufferfish onto Swiftness; trade with a level-5 cleric | Adrenaline Shot is produced |
| 10 | Log out and back in mid-rush | Documents current behaviour (state lost) |
| 11 | Check the log for `[IFAAR] Screen filter … failed to load` | Should be absent; if present, post-effect JSON/shader issue |
| R3-a | Start a rush; watch the first second | Power-up swell + first heartbeat; **check whether the vignette is missing until the first beat** (§10.11-1) |
| R3-b | End a rush | Power-down plays; vignette **fades or snaps?** (§10.11-1); breath cue overlap (§10.11-2) |
| R3-c | Run/sprint during power-up/power-down sounds | Listen for the sound drifting to one side (§10.11-3) |
| R3-d | Inject in survival with another player nearby | They hear `adrenaline_inject` |
| R3-e | Let the loop run > 1 minute | No gap, stutter or silence at the loop point |
| 12 | *(V2)* Inject shot 1; wait in cooldown, press | Nothing happens; cooldown sweep visible |
| 13 | *(V2)* Press in the danger zone at stack 1–2 | Shock: short stun + crash, shot spent |
| 14 | *(V2)* Press in the danger zone at stack 3 | Overdose death with the correct death message; Totem doesn't save you |
| 15 | *(V2)* Press in the QTE window, repeat to stack 4 | Rush extends each time, **music/filter don't restart**, debt keeps growing |
| 16 | *(V2)* At stack 4 let the rush run out | Debt cancelled, stun only, no crash damage |
| 17 | *(V2)* At stack 4 the bar is red; press in the red window | Overdose |
| 18 | *(V2)* Miss at stack 2 and 3 | Crash with ×1.5 / ×2.0 bill |
| 19 | *(V2)* Test with 150–250 ms latency | QTE still feels fair at both window edges (grace) |
| 20 | *(V2)* Log out mid-chain / die mid-chain | Behaviour matches the decision made in §11.5.8 |

---

## 13. Notes for the next developer

- **Minecraft 26.2 is new territory.** Official Mojang mappings, no Yarn. Several class names in this codebase are recent renames (`Identifier` for `ResourceLocation`, `GuiGraphicsExtractor`, `EntityTypes`, `Permissions`, `net.minecraft.world.attribute.BedRule`). Don't trust older tutorials; check names against the actual 26.2 sources in your IDE.
- **Mixin breakage on updates** is the main upkeep cost (§8). Because `defaultRequire: 1`, a missing target is a launch crash — which is the right behaviour for catching it.
- **Keep the architecture rule**: state and rules on the server, presentation on the client, connected only via MobEffects. New injuries should follow the same recipe: flag/timer in `InjuryState` → logic in `tickPlayer`/events → `registerEffect` + `syncEffect` → client edge detection → texture + lang.
- **Tunables are scattered** as literals through `CombatInjuries.java` (durations, thresholds, chances). A good first refactor is pulling them into constants or a config file.
- **Playtest-able in one world** thanks to `/injurytest`; there's no need to engineer natural scenarios to see any effect.

---

## 14. Build and deploy (how the jar is actually produced)

The owner has no local build environment. Builds run on **GitHub Actions**:

1. The Gradle project lives at the repository root. `gradle.properties` supplies: `minecraft_version=26.2`, `loader_version=0.19.5`, `fabric_api_version=0.161.0+26.2`, `loom_version=1.18.2`. Plugin id is `net.fabricmc.fabric-loom` (the unobfuscated-era Loom). Java 25.
2. `.github/workflows/build.yml` (Temurin 25, `gradle/actions/setup-gradle` with `gradle-version: current`, `gradle build --no-daemon`) runs on every push and uploads `build/libs/*.jar` (excluding `-sources`) as the artifact **IFAAR-mod-jar**.
3. Download: repo → **Actions** → latest green run → **Artifacts** → unzip → `IFAAR.jar` → `mods/`.
4. Runtime needs: Fabric Loader ≥ 0.19.3, **Fabric API 0.161.0+26.2**, Java 25.
5. No Gradle wrapper is committed. For local builds run `gradle wrapper` once, then `./gradlew build`.

**Gotchas already hit**
- The hidden `.github` folder is **skipped by browser drag-and-drop uploads**; create `.github/workflows/build.yml` via *Add file → Create new file*.
- `settings.gradle` must **not** set `repositoriesMode` to `FAIL_ON_PROJECT_REPOS`; Loom adds its own project repository.
- Decompiled Java can contain `this instanceof X` inside a mixin class; it must be `(Object)this instanceof X`.
- Warnings about Node 20 / `setup-java@v4` in Actions are harmless; bump the action versions when convenient.
- Do not hand-pack a jar. A hand-packed jar is how the generated resources went missing (§10.10).

## 15. Rules of thumb learned on this project
- **A `sounds.json` entry whose `.ogg` file does not exist is silently empty** — the event will never play.
- Any resource referenced by name from Java (post effects, sounds, textures) **must exist as a committed file**. Never generate them in `build.gradle`.
- Edge-detection `if/else if` chains in `tickClient` must test the *transition* (`!now && wasActive`), not just `wasActive`.
- Constants are inlined by the compiler; the decompiler shows literals. Don't conclude a constant is "unused" from a decompile.
- `./gradlew build` success proves it compiles, not that the mixins apply — every mixin has `defaultRequire: 1`, so a wrong target crashes at game launch, which is the intended early-warning.

## Appendix A — Effect colours & registry names

| Effect ID | Category | Colour (decimal in code) |
|---|---|---|
| `hemorrhage` | harmful | 8196128 |
| `tetanus` | harmful | 10903090 |
| `fracture` | harmful | 13223352 |
| `concussion` | harmful | 12959786 |
| `asphyxia` | harmful | 2505809 |
| `winded` | harmful | 7702681 |
| `hysteria` | neutral | 15263976 |
| `adrenaline_rush` | beneficial | 14096693 |
| `adrenaline_crash` | harmful | 3356234 |

## Appendix B — Attribute modifier IDs (all `ADD_MULTIPLIED_TOTAL`, transient, re-applied each tick)

| ID | Attribute | Value | Active when |
|---|---|---|---|
| `fracture_speed` | movement speed | −0.40 | fractured |
| `adrenaline_rush_speed` | movement speed | +0.55 (`RUSH_SPEED_BONUS`) | rushing |
| `adrenaline_crash_speed` | movement speed | −0.55 × (0.35 + 0.65 × remaining/300) (`CRASH_SPEED_PENALTY`), tapers | crashing |
| `adrenaline_rush_damage` | attack damage | +0.25 | rushing |
| `hysteria_damage` | attack damage | +0.50 | hysteria |
| `hysteria_knockback_resistance` | knockback resistance | +1.00 | hysteria |

## Appendix C — Quick tunables map

| Want to change… | Look at |
|---|---|
| Rush / crash length | `onUseItem`, `tickPlayer` (600 / 300) |
| Hysteria health threshold | `tickPlayer` (`<= 4.0F`), plus `afterDamage` and `runInjuryTest` |
| Tetanus chance | `afterDamage` (`0.25`) |
| Hemorrhage chance | `onAttack` (`0.3`, `0.03` per protection level) |
| Rusty tool list | `RUSTY_TOOL_PATHS` + models/items JSON + lang |
| Filter strengths | `post_effect/*.json` (§7.1) |
| Filter fade speeds | `*_FILTER_FADE_*_MS` constants + literals in `updateCameraPostEffect` |
| Loot odds | `LootTableEvents.MODIFY` (`0.01F`) |


### 11.8 Concussion tiers (round 5)
`applyConcussion(state, heavy)` in `CombatInjuries.java`. Heavy = 300 ticks (explosions, Warden, mace smash, sonic boom, anvil). Light = 120 ticks (iron golem). A light hit on an already-concussed player never shortens or downgrades it. The tier is the amplifier of the `concussion` effect (0 light, 1 heavy); `syncEffect` now re-adds an effect when its amplifier changes. The client scales haze, tinnitus volume/pitch and the fade-out length from the amplifier (`CombatInjuriesClient`, `SoundEngineHysteriaMixin`).

### 11.9 Round 6 additions
See CHANGES.md "Round 6". Unverified-API risks to check first if the build or game complains: `ItemCooldowns.addCooldown(ItemStack,int)` (server), `GuiGraphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, texW, texH)` (client pop-ups), the `fabric:components` ingredient in `data/combatinjuries/recipe/adrenaline_shot.json` (a bad recipe only logs an error and is skipped), and `max_uses` in the cleric trade JSONs.

### 11.10 Round 7 additions
See CHANGES.md "Round 7". The timing bar no longer shows shot pips and needs no texture files. Open: the stack-tiered music idea from 11.7 (tempo = intensity) is not built; the pitch-rise toward the window is.

### 11.11 Round 8 additions
See CHANGES.md "Round 8". Things to verify first if something misbehaves: (1) `GameRenderer.getFov` may have a different name/return type in 26.2 - the FOV mixin is non-required so the effect would just be missing (check the log for a mixin warning); (2) advancement awarding uses `server.getAdvancements().get(id)` and `player.getAdvancements().award(holder, "done")`; a wrong API name would fail the BUILD, a bad JSON would just log an error and skip that advancement; (3) the advancements use `minecraft:adventure/root` as parent so no custom tab background is needed.
