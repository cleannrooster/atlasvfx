# 0.4.0

Effects a server can trigger: a server names a cue or sends a telegraph, and every nearby client
with AtlasVFX draws it. Brought in from rpg-minibosses, where it was built to move here.

Added (`dev.eann.atlasvfx.cue`):

- Server side, safe on a dedicated server: `FxEmitter.emit(world, cue, args)` and
  `FxEmitter.telegraph(world, area, windupTicks, style)` send to players within 96 blocks whose
  client has AtlasVFX. `FxCueArgs` carries a point, an end point or direction, a size, an angle, a
  tier, a seed, an entity to follow, and the name of the style to draw in. `FxCues` names the
  built-in cues - pulse, nova, explosion, blast, blast_falling, beam, arc, cone, burst, wisp,
  smoke, rain, slash - and the built-in looks `DANGER`, `WARNING`, `HELPFUL`.
- Client side: `FxCueHandlers.register(cue, handler)` for a mod's own cues, or to draw a built-in
  one its own way; `FxCueStyles.register(name, style, telegraphStyle)` for the looks a server
  names. An unknown style draws in the default look rather than nothing.
- `AtlasVfxCommon`: the library's start on both sides, with a new Fabric `main` entrypoint, so a
  dedicated server declares the payloads.

Recorded trails and thrusts (`dev.eann.atlasvfx.trail`), for paths not known in advance:

- `Trail.point(anchor, width, maxAge, TrailStyles.streak(colour), seed)`: a ribbon behind
  something moving, facing the viewer and narrowing to its tail. `Trail.blade(hilt, tip, ...)`:
  the surface a blade sweeps between two anchors. Both last until an anchor lets go or the trail
  is released, then run off.
- `SampledSweep` samples once a tick and draws a Catmull-Rom curve through the samples, with the
  anchor's position this frame as the head, so it looks the same at any frame rate. A jump
  longer than 6 blocks in a tick starts it again rather than streaking across the world.
  `TrailBuffer` is the fixed ring of samples behind it.
- `Trail.thrust(frame, reach, halfWidth, halfAngle, extendTicks, fadeTicks, TrailStyles.thrust(colour),
  seed)`: Visceral Combat's spearhead - narrow at the root, widest past halfway, barbs raking back -
  extending down the frame's forward direction. `LaneSweep` never draws wider than the lane or
  its cone.
- `TrailAnchors.look(entity, drop, forward, right)` and `TrailAnchors.body(entity)`;
  `TrailWindow.follow()` and `TrailWindow.hold(ticks, fadeTicks)`; `SweptSurface.tick` and
  `finished` hooks for surfaces that record.
- `/atlasvfx trail [blade] [glow] [ticks] [#RRGGBB]` and `/atlasvfx thrust [glow] [far] [#RRGGBB]`.

Auras (`dev.eann.atlasvfx.aura`): light that stays with a body for as long as it is wanted.

- `Aura(style, layers...)`, a stack of `AuraLayer`s sized against the body, so one aura fits a
  player, a wolf and a ravager alike:
  - `GroundSigil`: turning rings at the feet, with the enchanting table's glyphs if you want them;
  - `Shell`: a sphere, dome or column of light, clear where it faces you and bright along its
    outline, with optional bands running up it;
  - `Orbit`: sparks with tails circling the body;
  - `Rise`: motes drifting up off the ground, counted against the mote budget and density;
  - `Helix`: strands of light winding up round the body.

  `aura.on(entity, seed)` is the shape; releasing it fades it out. It follows the body's rules: it
  isn't drawn on what you can't see (invisible, or a spectator when you aren't one), it fades as
  the body dies, and shells, orbits and helixes are left out in first person for the body you're
  looking out of.
- Sizing: an aura's sizes are in blocks, as they look on a player. `Sizing.FIXED`, the default,
  keeps them on any body. `aura.scaledToBody()` (`Sizing.BODY`) scales them by the body's size
  against a player's (whichever of width or height is the larger share, so the scale attribute and
  babies count). `aura.scaled(factor)`, `Sizing.of(factor)` and `sizing.times(factor)` set or
  multiply the factor, and a `Sizing` can be any function of the entity. Shells always fit the body
  they surround.
- `Auras.ward`, `embers`, `orbit`, `helix`, `sigil` and `sphere`: presets in any style.
- `FxAttachments.attach(entity or id, factory, density)` keeps an effect on an entity, not just
  one shape. The effect pauses while the entity is out of view, is made again when it comes back
  or is replaced under the same id (a player respawning), and is forgotten with the world.
  Attaching by id works before the entity reaches the client. `FxAttachment` detaches with a fade
  or cancels at once.
- `FxPainter.viewer()`: where the camera is, for shapes that look different from different sides.
- `/atlasvfx aura [ward|embers|orbit|helix|sigil|sphere|off] [target] [scaled] [#RRGGBB]`.

Character overlays (`dev.eann.atlasvfx.character`): extra passes drawn over a body, posed by
whatever posed it.

- `OverlayPass`: `Glint` (the enchantment glint), `Glow(rgb, strength, pulse)` (the body's own
  skin added on top as light, in the layer spiders' eyes use; it can beat), `Energy(texture, rgb,
  speed)` (the charged creeper's or the wither's scrolling swirl, `Energy.CREEPER` /
  `Energy.WITHER`), and `EndSky` (the End's starfield over the skin). They're all the game's own
  layers, so shader packs and other renderers already handle them.
- `CharacterEffects`: `register(source)` for a mod that keeps its own state of who wears what
  (asked per body as it's drawn), or `set(entityId, effect)` for effects kept by id until cleared
  or the world changes. A `CharacterEffect` holds overlays and, optionally, a substitution.
- Drawn by `OverlayFeatureRenderer`, added to every living entity's renderer through each loader's
  own hook (Fabric API's `LivingEntityFeatureRendererRegistrationCallback`, NeoForge's
  `EntityRenderersEvent.AddLayers`), and over the first-person arm and sleeve. Not drawn on a body
  you can't see, or in a shader pack's shadow pass. Over the body only: armour hides it as it hides
  the skin.
- A body with no overlay costs one check.
- `/atlasvfx overlay [glint|glow|pulse|energy|wither|end|off] [target] [#RRGGBB]`, with several
  words stacking passes.

Substitution (`dev.eann.atlasvfx.character`): changes what a body is drawn as, rather than drawing
over it.

- While an entity wearing a look is drawn, the render layers its parts ask for are swapped. Only
  the layer changes; the geometry is the game's own, posed by whatever posed it, so animation mods
  and modded models keep working. Glint, outlines, eyes and other effect overlays, and layers that
  can't be read, are always drawn as asked.
- `BodyPart` (`SKIN`, `ARMOR`, `CAPE`, `ELYTRA`, `HEAD`, `HELD`, `ATTACHMENT`, `OTHER`, `ARM`), from
  which renderer asked. `BodyParts.register(featureClass, part)` names a mod's own feature
  renderer, and its subclasses follow. Unknown feature renderers are `OTHER` and take the look by
  default. Stuck arrows, stingers and shoulder parrots are `ATTACHMENT` and are left alone.
  `BodyPart.WORN` is everything but held items and attachments; `SKIN_ONLY` is the skin and
  first-person arm.
- `Substitution` decides per part and requested layer, returning a `Swap` (a layer, and optionally
  a tint). `Substitutions.endSky(parts)`, `ghost(alpha, parts)`, `glow(rgb, parts)` and
  `retexture(texture, parts)` are ready-made.
- `CharacterEffect.Mode`: `SUBSTITUTE` (default) swaps layers, `OVERDRAW` draws the look's overdraw
  passes over the body instead. `CharacterEffects.forceMode(mode)` overrides every effect, for a
  host mod's setting. A look not safe under shader packs (the End sky) is overdrawn while a pack is
  in use, and nothing is swapped in a shader pack's shadow pass.
- A glowing body keeps its outline when its layers are swapped. An entity drawn from inside
  another's drawing gets the real provider, not the other's look. A body with no look costs one
  check, and the provider it's drawn into is the game's own object.
- `/atlasvfx parity` now shows what each layer was drawn as (`-> end_portal`), and `diff` reports
  changes to it. The source-tracking mixins run whenever an entity is wrapped, not only while
  capturing.
- `/atlasvfx look [endsky|ghost|glow|skin|off] [target] [held] [skinonly] [overdraw] [#RRGGBB]`.

Appearances (`dev.eann.atlasvfx.appearance`): players choose a model to be seen as. It's off by
default.

- `config/atlasvfx.json` gets `"appearances": {"enabled": false}`. AtlasVFX's first config file,
  written with its defaults the first time it starts. The server's setting is the one that counts,
  and it tells each client as it joins. While it's off, choices are refused and nobody is shown one.
  Choices made earlier are kept for when it's turned back on.
- The appearance screen opens with a key (default `O`, under Controls > AtlasVFX). It's built from
  the game's own list, buttons and background, so it follows the game's look, blur and narration.
  Each appearance row has a small live likeness of you wearing it, and the selected one is shown
  large, following the pointer as the inventory's does. "Your Own Skin" is always first. Done keeps
  the choice; Cancel leaves it.
- `"prompt_on_first_join": true` (on by default, and only while appearances are on) opens the screen
  by itself the first time a player joins a world or server, with a line saying how to come back to
  it. It waits for a quiet moment rather than a set time: in a world, alive, and no screen open for
  2 seconds in a row. A welcome screen, quest book or menu another mod shows on joining therefore
  comes first, and every one of them starts the wait again. Closing the screen in any way, or
  choosing, marks the player as asked, and that's kept with the world, so they're asked once.
  Config files from before this get the new setting written in.
- Each choice is kept with the world (overworld saved data, by player UUID) and shown to everyone:
  all of them as a player joins, and theirs to everyone else. The server keeps only the id; what an
  appearance looks like is the client's.
- Eleven appearances ship with AtlasVFX (`atlasvfx:rogue` and so on):
  - RPG Minibosses' Forsaken adventurers, each with its geometry, texture and `v2` clips: the Rogue,
    Templar, Juggernaut, Fire Mage with White Mage and Dark Mage recolours, and Mercenary;
  - the Raider, Reverent, Ruffian and Spellblade, which follow the body. The Reverent is worn over
    your own skin. Their names and descriptions
  are translatable (`atlasvfx.appearance.<name>`). They show only once appearances are enabled.
- Appearances are data: `assets/<namespace>/atlasvfx_appearances/<name>.json` names a geometry,
  texture, optional animation file, `posing` (`animated` or `follow`), optional `hides`, and
  `clips`: stance, advance, backstep, lateral left and right, a list of swings, use, and swing
  speed, with an optional shared `prefix`. A modpack can add them with a resource pack.
  `Appearances.register(...)` adds them in code.
- An appearance follows the body by default (`"posing": "follow"`), so walking, sneaking, Better
  Combat and PlayerAnimator poses carry over. Its `clips` are kept either way, and
  `Appearance.animated()` gives the same model playing them.
- Appearances can be unlocked by advancement. A data pack locks one with
  `data/<namespace>/atlasvfx_unlocks/<name>.json` holding `{"advancement": "<id>"}`. It lives in
  data rather than with the look, because the server decides and a dedicated server reads no
  resource packs.
  - The server refuses a locked choice, and takes an appearance off a player who no longer has its
    advancement (on join and on `/reload`).
  - Earning the advancement unlocks it at once.
  - The screen still lists locked appearances, greyed with "Locked: <advancement title>". They can
    be previewed, but Done is disabled while one is selected.
  - An advancement that doesn't exist (its mod missing) locks nothing and is reported once.
  - AtlasVFX locks nothing itself. The example mod locks the Juggernaut behind "Diamonds!" to show
    it working.
- `"keep_skin": true` in an appearance file draws the model over your own skin and first-person
  arm, as armour is, for armour-style models that don't cover the whole body. Armour and other worn
  layers are still hidden. `ModelReplacement.hides` now lists exactly what's hidden, with the skin
  and arm in it by default.
- `ClipAnimator`: the clip picker those files describe, also usable from code.
- `CharacterEffects.preview(entity, effect, draw)` shows an effect on an entity for one draw only.
  It's what the screen's likenesses use.

Fixed: a replaced body's armour crashed through ArmorModelAPI ("Duplicate delegates"), because
the game's armour glint was handed the same hidden buffer twice. Feature layers for parts a
replacement hides (armour, capes, elytra, head items, other mods' layers) are now skipped entirely
rather than drawn into nothing, and each hidden layer gets a buffer of its own.

Fixed: a replacement no longer draws whole in first person while an animation mod draws the
player's arms there (PlayerAnimator's first-person mode, as Better Combat's swings use). A
replacement now shows only the bones whose vanilla part is showing, and those arms follow the
vanilla arms. Bones the rig doesn't name go with what they hang from, or with the body.

Model replacement (`dev.eann.atlasvfx.model`): another model drawn in a body's place, without
GeckoLib.

- `GeoModel.parse` reads a Bedrock geometry file (`.geo.json`, as Blockbench, GeckoLib and AzureLib
  save them), and `GeoBaker.bake` turns it into the game's own `ModelPart`s. Box-UV cubes are
  exact, including fractional sizes, which are laid out by their whole pixels as GeckoLib and
  AzureLib do (the game's own cuboid would shift every face after a fractional edge; that's what
  put the Templar's chest plate a pixel off). Cubes with their own rotation get a part of their own. Per-face UV is exact too, mirrored
  and negative sizes included (`FaceCuboid`): each face takes the file's area in the corner order
  the game's own box cuboid uses, and faces follow GeckoLib's naming (a file's east is the part's
  west). A face's `uv_rotation` isn't applied.
- `GeoRig` finds the head, torso, arms, legs and waist by bone name. A waist (`waist`,
  `bipedWaist`, or `armorWaist`) goes with the body, as GeckoLib-style armour rigs mean it to. In
  follow mode a bone turns about its vanilla part's pivot rather than its own, so a waist leans
  from the body and a bone whose pivot isn't quite vanilla's swings in place. The head is any plainly named head
  bone, or failing that the outermost bone with "head" in its name, never armour. A "body" bone that
  holds the limbs isn't taken as the torso.
- `GeoAnimation.parseAll` reads Bedrock animation files: numbers and sums of numbers
  (`"28+720"`), `pre`/`post`, GeckoLib's `easing` names with `easingArgs`, `lerp_mode: catmullrom`,
  and loop, once or hold. Molang that needs the game reads as 0, with a warning naming the file.
  `GeoAnimationState` loops a base clip with a crossfade and plays one-shot actions over it.
  `GeoAnimator` picks clips once a tick.
- `ModelReplacement(geo, texture, animations, posing, animator, hides, followLook)`:
  - `following(geo, texture)` follows the vanilla pose bone by bone, so walking, sneaking and
    animation mods' poses all carry over;
  - `animated(geo, texture, animations, animator)` plays its own clips;
  - either way, the head turns where the body looks, on top of whatever is playing.
  - Put it on with `CharacterEffect.replace(...)` or `withReplacement(...)`.
- The vanilla body isn't cancelled: every renderer and hook still runs, and the seam hides the
  layers of the parts it replaces (skin, first-person arm, and by default armour, cape, elytra,
  head items and modded layers). The replacement is drawn right after, with the same matrices,
  light, hurt tint and transparency, and in first person as the arm. Overlays are drawn over the
  replacement. If the model can't be loaded, the body is drawn as it always was. Files load from
  resource packs on first use and reload with them (`GeoAssets`).
- `/atlasvfx parity` shows hidden layers as `-> (hidden)` and the replacement's own as
  `replacement`.

Parity tooling (`dev.eann.atlasvfx.parity`, internal for now):

- `/atlasvfx parity [self|target|arm] [diff <name|last>]` records, for one frame, every render
  layer an entity (or the first-person arm) asks for. Each layer is attributed to the model, a
  feature renderer, or the dispatcher, classified as surface, translucent surface, effect overlay
  or unknown, and its vertices are counted. The list is shown in chat and saved as JSON under
  `atlasvfx/parity/`, and `diff` compares it with an earlier capture.
- The classifier reads a layer's phases, not its name, so a mod's layer built the vanilla way is
  classified like the vanilla layer it resembles. Glint, outlines, energy swirls and additive
  (eyes) layers are effect overlays.
- AtlasVFX now has mixins (`atlasvfx.mixins.json`, client only): `@WrapMethod` on
  `EntityRenderDispatcher.render` and the first-person arm renderers, `@WrapOperation` around
  each feature renderer and the entity renderer call, and read-only accessors on render phases.
  When no capture is armed they pass the original provider on, the same object, after reading
  one field.

Changed:

- Every AtlasVFX render layer is now built the way the game builds its own, with
  `RenderLayer.of` and its standard phases, instead of setting GL state by hand. The state is the
  same - world light depth-tested and never writing depth, pictures bound without touching the
  atlas's filtering - but mods that read layers rather than run them can now see it. Under Iris a
  shader pack now picks a program and a draw order for AtlasVFX's light from its phases rather
  than treating it as an unknown opaque layer drawn first.
- Screen effects (the HUD painter) no longer write depth and are drawn over whatever the screen
  has drawn so far, rather than with whatever depth state the screen happened to leave.
- The registries mods fill at start-up - `FxCueHandlers` and `FxCueStyles` - are copy-on-write.
  NeoForge constructs mods on parallel threads; a plain map could lose a registration.
- An access widener (`atlasvfx.accesswidener`), declared for Fabric. At run time `RenderLayer.of`
  is already opened by NeoForge's access transformer and Fabric API's transitive access wideners.

Development: an `example` mod (`example-fabric`, `example-neoforge`), never published, uses the
library the way a content mod would. `/atlasvfx_example_client <adventurer>` tries on one of the
appearances AtlasVFX ships, `animated` plays its own clips instead of following the body, `target`
puts it on what you look at, and `off` takes it off. Its dev runs have appearances turned on.
The example's dev clients also run Better Combat 2.3.0, PlayerAnimator 2.0.4 and Cloth
Config 15.0.140 (dev runtime only, never a dependency of the library), so a replacement in follow
mode can be checked against Better Combat's attack poses. `./gradlew :example-fabric:runClient` (or
`:example-neoforge:runClient`), then `/atlasvfx_example cue <name>`, `telegraph <shape> [ticks]`
or `sigil` - the cues go from a real server command through `FxEmitter` to the client.

Moving over from rpg-minibosses' own layer: its `MagicSchool` becomes a style name (register one
`FxCueStyles` entry per school), `helpful` becomes `FxCues.HELPFUL`, and `GUIDE_LINE`,
`GUIDE_RING` and `HOLY_STRIKE` stay its own cues, registered with `FxCueHandlers`. The payload ids
are now `atlasvfx:cue` and `atlasvfx:telegraph`, so server and client move together.

# 0.3.1

- `WorldRenderHook.AFTER_WEATHER`: a second drawing hook, after the world has drawn its clouds and
  weather (Fabric `LAST`, NeoForge `AFTER_WEATHER`). For light in the air that clouds and weather
  must not paint over - a mod's glowing projectiles, say. `AFTER_TRANSLUCENT` is unchanged.

# 0.3.0

Effects can now be aimed: thrown one way, within a cone, rather than in every direction.

Added:

- `FxWorld.jet(recipe, at, axis, halfAngle, reach, count)`: motes thrown along directions within a
  cone, each hard enough to come to rest about `reach` blocks out, with a ragged front. Called once
  it is a gust; called every tick from a shape that lasts, it is a stream.
- `FxShapes.cone(style, at, axis, halfAngle, reach, tier, seed)`: a blast one way - a flare at the
  mouth, streaks of light shooting out along fixed lines inside the cone, a shock ring riding its
  front as wide as the cone is there, and the style's debris and accent thrown along it.
- `FxCone`: `sample(axis, halfAngle, random)`, a direction picked evenly over the cone's cap so a
  wide cone fills to its edge; `launchSpeed(motion, reach)`, the speed that carries a free mote of
  that motion a given distance.
- `FxShapes.arc(style, from, to, tier, seed, ticks)`: an arc that lasts as long as asked, redrawn on
  a new path every tick of it - a longer flicker rather than a slower fade.
- `Mote.drag(motion)`: the share of its speed a free mote keeps each tick, now in one place.
- `/atlasvfx cone [narrow|wide] [far] [heavy] [#RRGGBB]`.

# 0.2.0

Shapes can now last as long as they need to, and whoever starts one can hold on to it.

**Breaking:** `FxShape` no longer has `int life()`, and its hooks take an `FxLife` instead of an
`int age`.

- A shape that lasts a fixed number of ticks implements `FxShape.Timed` and renames `life()` to
  `ticks()`. Everything else about it stays the same.
- `tick(FxWorld, int age, float)` is now `tick(FxWorld, FxLife, float)`, and
  `draw(FxPainter, ClientWorld, int age, float)` is now `draw(FxPainter, ClientWorld, FxLife, float)`.
  Read the age with `life.age()`.
- `FxShape.progress(age, tickDelta, ticks)` is now `life.progress(tickDelta, ticks)`.
- An open-ended shape implements `done(FxLife)` itself, usually ending some ticks after
  `life.released()`.

Added:

- `FxWorld.play` and `FxWorld.add` return an `FxHandle`: `release()` lets a shape fade out,
  `cancel()` removes it at once, `flash()` marks a hit for the shape to show, `alive()` says
  whether it is still playing.
- `FxLife`: a shape's age, when it was released and flashed, and helpers to read them per frame.
- `FxCurve`: numbers along a span - linear, smooth, eased, a hump, evenly spaced points - that
  reverse, scale, multiply, remap and window.
- `FxGradient`: colours along a span, evenly spaced or at chosen stops.
- Trails (`dev.eann.atlasvfx.trail`): a `Trail` is a shape laid on a `SweptSurface` - a function of
  `s` along it and `t` across it - in layered bands, as a slab with thickness, seen through a
  `TrailWindow`.
  - `ArcSweep` is a swing through a known arc in a `SweepFrame` re-read every frame, so it follows
    its swinger; `SweepFrameSource.eyes(entity, drop)` gives Better Combat's attack origin.
  - `TrailLayer` bands take a slice of the width with their own colour, opacity curves and
    `TrailEdging` (clean, serrated, spiked). The crescent, edging and first-person clearance only
    ever take surface away, so a trail on an attack's own arc never claims space it cannot hit.
  - `TrailStyles.slash(colour)` is a wake, body and razor edge derived from one colour, with the
    game's sweep for grain; `Trail.slash(...)` builds one in a call. `FxHandle.flash()` snaps it
    to red for a landed hit.
- Telegraphs (`dev.eann.atlasvfx.telegraph`): warnings on the ground of where an attack lands and
  when.
  - `TelegraphArea` - `Circle`, `Ring`, `Cone`, `Line` - is pure geometry, safe on a server: test
    damage with `contains(point, height)` on the same area the client draws.
  - `TelegraphTiming` pins the landing to a world tick, so a late warning still lands on time.
  - `Telegraph.fixed(...)` or `Telegraph.following(..., anchor, lockTick)`: a faint footprint,
    a front filling it (`GROW`, `SWEEP` or `PULSE`), a heartbeat that quickens, a flash as it
    lands. `FxHandle.release()` lands it early.
  - `TelegraphStyles.DANGER`, `WARNING`, `of(colour)`.
- `FxPainter.arc(...)` and `FxPainter.band(...)`: part of a ring, and a strip lying in a plane.
- `/atlasvfx telegraph [circle|ring|cone|line] [ticks] [grow|sweep|pulse] [follow] [#RRGGBB]`.
- `FxPainter.surface(...)` draws a grid of points with per-point colour and opacity, textured or
  as untextured light; `SurfaceGrid` holds one and is refilled frame after frame.
- `FxColor.toHsl`/`fromHsl`, `FxLight.withMinBlock`, `UvWindow`.
- `/atlasvfx slash [vertical] [glow] [flash] [wide] [#RRGGBB]`: swing a slash from your own eyes.

# 0.1.0

First release: the effects engine extracted from Mo' Mana.
