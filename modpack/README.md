# LAST DAYS — Modpack track

This branch turns the standalone **LAST DAYS** mod into a stable, launcher-installable survival pack for **Minecraft Java 26.3 + Forge 66.0.2**.

## Design rule

LAST DAYS owns the actual game: infected progression, Blood Moons, weapons, projectile simulation, survival systems, city/POI generation, skills, base defence and endgame.

Third-party mods are only used where they already solve a generic problem well: maps, inventory ergonomics, audio atmosphere, HUD and performance. This avoids duplicated zombie/gun systems and keeps the pack debuggable.

## First pack layer

The builder resolves current Forge 26.3 versions from Modrinth for:
- Xaero's Minimap
- Xaero's World Map
- Mouse Tweaks
- Sound Physics Remastered
- AmbientSounds when available
- AppleSkin when available
- Jade when available
- Clumps when available
- Cloth Config when available

The resulting artifact is a **Modrinth .mrpack**, so third-party jars stay hosted by their original authors instead of being copied into this repository. The LAST DAYS jar itself is placed in the pack overrides from our own successful build artifact.

## Why not add 50 mods immediately?

The current 26.3 Forge ecosystem is still young. The pack is deliberately built in layers:
1. stable core + navigation/audio/QoL,
2. runtime smoke test,
3. then more content only where it does not duplicate or destabilize LAST DAYS.

## Install

Import the generated `LAST_DAYS-*.mrpack` into Modrinth App or Prism Launcher.

For manual Forge testing, the standalone LAST DAYS jar is still built by the existing workflow.
