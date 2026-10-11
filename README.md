# AtlasVFX

Visual effects for Minecraft 1.21.1 (Fabric and NeoForge, via Architectury) drawn entirely from
the game's own sprite atlases: soft light, rings, beams, blades, holes into the End, motes and
short-lived shapes. It ships no textures, so effects follow whatever resource pack is on.

Extracted from Mo' Mana's effects engine.

## Using it

Depend on the artifact matching each of your modules, the way you depend on Architectury:

```groovy
// common
modApi "dev.eann:atlasvfx-common:0.3.0"
// fabric
modApi "dev.eann:atlasvfx-fabric:0.3.0"
// neoforge
modApi "dev.eann:atlasvfx-neoforge:0.3.0"
```

The library starts itself on the client: `FxWorld` is ticked, cleared between worlds and drawn
after translucent terrain. A mod only plays shapes and spawns motes:

- `FxWorld.get().play(shape, density)` starts a shape with the debris it throws as it appears;
  `add` starts it without. Both return an `FxHandle` to `release()` it (let it fade out),
  `cancel()` it (gone now) or `flash()` it (a hit confirmation).
- `FxShapes` has the generic shapes (pulse, nova, explosion, blast, beam, arc, cone) and the
  scattered kinds (burst, wisp, rain, smoke). Implement `FxShape` for your own: `FxShape.Timed` for one
  that lasts a fixed number of ticks, or `done(FxLife)` for one that lasts until released.
- `FxCurve` and `FxGradient` describe numbers and colours along a span - width along a trail,
  alpha over a life, an ember cooling.
- `FxPainter` draws directly: `world(...)` for the world or first-person view, walked once per
  `FxPass`; `screen(DrawContext)` for the HUD.
- `Trail` draws trails on swept surfaces. `Trail.slash(frame, reach, halfAngle, reversed, plane,
  swingTicks, fadeTicks, TrailStyles.slash(colour), seed)` is a weapon swing through its arc; give
  it the same frame, reach and angle as the attack's damage and it draws exactly what can be hit.
- `Telegraph` warns of an attack on the ground. Describe the attack once as a `TelegraphArea` with
  a `TelegraphTiming` (world ticks): the server tests damage with `area.contains(...)`, the client
  plays `Telegraph.fixed(area, timing, TelegraphStyles.DANGER)` and it lands when the attack does.
- `FxDensity` is your mod's share of each effect, multiplied by the game's particle setting.
  `FxBudget` caps motes across every mod.
- `FxWorld.jet(recipe, at, axis, halfAngle, reach, count)` throws motes one way, within a cone, to
  about `reach` blocks; call it every tick from a shape that lasts for a stream. `FxCone` has the
  geometry: an even direction within a cone, and the speed that carries a mote a given distance.

## Looking at effects

`/atlasvfx slash [vertical] [glow] [flash] [wide] [#RRGGBB]` swings a slash from your own eyes in
any world, with nothing else installed. Third person shows all of it.

`/atlasvfx telegraph [circle|ring|cone|line] [ticks] [grow|sweep|pulse] [follow] [#RRGGBB]` lays a
telegraph at your feet, facing where you face.

`/atlasvfx cone [narrow|wide] [far] [heavy] [#RRGGBB]` blasts a cone from your eyes the way you
look.

## Building

```
./gradlew build
./gradlew publishToMavenLocal
```
