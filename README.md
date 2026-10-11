# AtlasVFX

Visual effects for Minecraft 1.21.1 (Fabric and NeoForge, via Architectury) drawn entirely from
the game's own sprite atlases: soft light, rings, beams, blades, holes into the End, motes and
short-lived shapes. It ships no textures, so effects follow whatever resource pack is on.

Extracted from Mo' Mana's effects engine.

## Using it

Depend on the artifact matching each of your modules, the way you depend on Architectury:

```groovy
// common
modApi "dev.eann:atlasvfx-common:0.1.0"
// fabric
modApi "dev.eann:atlasvfx-fabric:0.1.0"
// neoforge
modApi "dev.eann:atlasvfx-neoforge:0.1.0"
```

The library starts itself on the client: `FxWorld` is ticked, cleared between worlds and drawn
after translucent terrain. A mod only plays shapes and spawns motes:

- `FxWorld.get().play(shape, density)` starts a shape with the debris it throws as it appears;
  `add` starts it without.
- `FxShapes` has the generic shapes (pulse, nova, explosion, blast, beam, arc) and the scattered
  kinds (burst, wisp, rain, smoke). Implement `FxShape` for your own.
- `FxPainter` draws directly: `world(...)` for the world or first-person view, walked once per
  `FxPass`; `screen(DrawContext)` for the HUD.
- `FxDensity` is your mod's share of each effect, multiplied by the game's particle setting.
  `FxBudget` caps motes across every mod.

## Building

```
./gradlew build
./gradlew publishToMavenLocal
```
