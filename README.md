# AtlasVFX

Visual effects for Minecraft 1.21.1 (Fabric and NeoForge, via Architectury) drawn entirely from
the game's own sprite atlases: soft light, rings, beams, blades, holes into the End, motes and
short-lived shapes. Its effects ship no textures, so they follow whatever resource pack is on; the
only art it carries is the optional player appearances below.

Extracted from Mo' Mana's effects engine.

## Using it

Depend on the artifact matching each of your modules, the way you depend on Architectury:

```groovy
// common
modApi "dev.eann:atlasvfx-common:0.3.1"
// fabric
modApi "dev.eann:atlasvfx-fabric:0.3.1"
// neoforge
modApi "dev.eann:atlasvfx-neoforge:0.3.1"
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
- `Trail.point(...)` and `Trail.blade(...)` record a trail behind anything moving, through
  `TrailAnchors`; `Trail.thrust(...)` drives a barbed spearhead down a frame.
- `Telegraph` warns of an attack on the ground. Describe the attack once as a `TelegraphArea` with
  a `TelegraphTiming` (world ticks): the server tests damage with `area.contains(...)`, the client
  plays `Telegraph.fixed(area, timing, TelegraphStyles.DANGER)` and it lands when the attack does.
- From the server: `FxEmitter.emit(world, FxCues.NOVA, FxCueArgs.at(style, pos, radius, tier, seed))`
  and `FxEmitter.telegraph(world, area, windupTicks, FxCues.DANGER)` draw on every nearby client.
  Register the style names you send with `FxCueStyles` and any cues of your own with
  `FxCueHandlers`, on the client. The server side touches nothing client-only.
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

`/atlasvfx trail [blade] [glow] [ticks] [#RRGGBB]` puts a recorded trail on you for a while - walk
and look around. `/atlasvfx thrust [glow] [far] [#RRGGBB]` drives a thrust from your eyes.

`/atlasvfx cone [narrow|wide] [far] [heavy] [#RRGGBB]` blasts a cone from your eyes the way you
look.

`/atlasvfx aura [ward|embers|orbit|helix|sigil|sphere|off] [target] [scaled] [#RRGGBB]` puts an
aura on you, or with `target` on whatever you're looking at; `scaled` sizes it to the body rather
than in blocks. Running it again replaces the aura; `off`
fades it out.

`/atlasvfx overlay [glint|glow|pulse|energy|wither|end|off] [target] [#RRGGBB]` draws passes over
your body (or, with `target`, over whatever you're looking at): the glint, a glow from within, a
beating glow, the charged creeper's swirl, the wither's armour, or the End's starfield. Several
words stack.

`/atlasvfx look [endsky|ghost|glow|skin|off] [target] [held] [skinonly] [overdraw] [#RRGGBB]` changes
what your body is drawn as: the End's starfield, see-through, lit from within, or a zombie's skin.
It covers armour, cape, elytra and other mods' layers too, unless you pass `skinonly`; `held`
adds what you hold. `overdraw` draws the look over your body instead of swapping its layers.

**Appearances** let players choose a model to be seen as. They're off by default: set
`"appearances": {"enabled": true}` in `config/atlasvfx.json` (the server's copy decides). Then the
`O` key opens the appearance screen. It also opens by itself on a player's first join, once nothing
else is on screen (`"prompt_on_first_join"`, on by default). Eleven come with AtlasVFX: RPG Minibosses' Forsaken
adventurers (Rogue, Templar, Juggernaut, Fire Mage, White Mage, Dark Mage, Mercenary), and the Raider,
Reverent, Ruffian and Spellblade. More come from
resource packs, as `assets/<namespace>/atlasvfx_appearances/<name>.json`; see `Appearances` for the
format, and AtlasVFX's own for examples. A data pack can lock any of them behind an advancement:
`data/<namespace>/atlasvfx_unlocks/<name>.json` with `{"advancement": "<id>"}`.

`/atlasvfx parity [self|target|arm] [diff <name|last>]` is for checking render compatibility
rather than effects. It lists every render layer one entity is drawn with for one frame: who asked
for it (the model, a feature renderer, the dispatcher's shadow), what kind of layer it is (surface,
translucent surface, effect overlay, unknown), and how many vertices went into it. It saves the
list to `atlasvfx/parity/` in the game folder. `diff` compares it with an earlier capture, for
example with and without a shader pack, or before and after a mod update. `self` needs third
person; `arm` captures the first-person arm with an empty hand.

## Trying it

The `example` mod uses AtlasVFX the way a content mod would and is never published:

```
./gradlew :example-fabric:runClient
```

In a world, `/atlasvfx_example cue <name>` sends any built-in cue from the server, `telegraph
<circle|ring|cone|line> [ticks]` a telegraph, and `sigil` the example's own cue, drawn by a shape
it defines itself.

## Building

```
./gradlew build
./gradlew publishToMavenLocal
```
