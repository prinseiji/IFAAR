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
