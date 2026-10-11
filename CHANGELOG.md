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
