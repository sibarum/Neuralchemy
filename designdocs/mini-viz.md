# Mini-Visualizer

Status: design draft (pre-implementation)

The reusable visualization widget that appears in every screen. One implementation, four major callers (DSL editor preview, node-graph probe nodes, data column inspector, simulator panels). Designed so that 80% of the visual surface in the app is the same widget under different layer configurations — if the mini-viz is good, the whole app looks good.

## Goals

- **One widget, many roles.** Heatmap, scatter, line plot, vector field, hover-tangent overlays — all the same component with different layers.
- **Layer composition.** Multiple layers stack in z-order; each declares what it draws and how. The simulator's "loss curve with optimal-loss baseline + current-step marker" is *three layers*, not three different widgets.
- **Allocation-flat at 60 fps.** Layers compute and render every frame; an idle frame must do zero allocation.
- **Acts as an input field.** Click-to-emit-point is first class — a probe in the node graph can declare its input source as "wherever the user clicked on this viz".
- **Texture-backed for pixel-density layers.** Every per-pixel layer (scalar field, vector field) renders to a CPU buffer and uploads as a single texture, mirroring the existing `lab.viz.PixelGrid` pattern.

## Anatomy

```
┌─ optional title ─ ⤢ resize ─ ⓘ help ─┐
│                                       │
│   layer 0: scalar field (heatmap)     │
│   layer 1: vector field arrows        │
│   layer 2: line strip overlay         │
│   layer 3: marker dots                │
│   layer 4: hover-tangent (interactive)│
│                                       │
│   axes/grid (always-on bottom layer)  │
│                                       │
└─ status: in (1.42, -0.31)  hover ─────┘
```

A mini-viz is its own rectangular region with:

- A view (world-coord rectangle and aspect preference).
- An ordered list of layers (bottom-to-top).
- An interactivity mode (none / hover / click-emits / drag-emits).
- Optional status strip below the canvas for hover readouts.
- Optional title bar above with toolbar buttons (resize, toggle layers, copy values).

The toolbar is *not* a hover popup — when present, it's always visible. Tiny mini-viz inlines (probe-as-canvas-dot) skip the toolbar entirely.

## View transform

```
View {
  world: { x_min, y_min, x_max, y_max }
  pixel: { x, y, w, h }              // screen rect
  aspect: STRETCH | LOCK_X | LOCK_Y  // how to handle non-matching aspect ratios
}
```

`world → pixel` is a single affine map, recomputed when either rect changes. Layers are given the view and project their own coordinates; the mini-viz never does the projection on the layer's behalf (each layer's render path knows its own data shape best).

## Layer types

Each layer is a small object with a `render(view, frame_buf, draw_calls)` method. Allocation-flat: no temporaries per frame.

### `ScalarField`

```
ScalarField {
  sample: (x, y) -> float
  colormap: Colormap        // gradient + saturation policy
  resolution: int           // pixels per axis (defaults to view's pixel size)
  alpha: float
}
```

Per-pixel evaluation, written to a CPU buffer, uploaded as a texture, drawn as one quad. This is the path the parameter-mixing demo currently uses.

### `VectorField`

```
VectorField {
  sample:   (x, y) -> (dx, dy)
  density:  int        // how many arrows across the view
  scale:    AUTO | float
  style:    LINE | ARROW | STREAMLINE
}
```

Drawn as line / arrow primitives at a sampled lattice; `STREAMLINE` integrates short trails.

### `PointSet`

```
PointSet {
  points:  IntStream / float[]  // (x, y, r, g, b, label_idx)
  size:    float
  symbol:  CIRCLE | SQUARE | CROSS | ...
}
```

For scatter plots — data column distributions, labeled training samples, debug points.

### `LineStrip`

```
LineStrip {
  vertices: float[]   // packed (x, y) pairs
  stroke:   { color, width }
  closed:   boolean
}
```

For loss curves, transformed unit circles, network output traces.

### `Markers`

```
Markers {
  positions: float[]
  kind:      ORIGIN | NULL_CONE | LEVEL_SET | CRITICAL_POINT | CUSTOM
  decoration: ...
}
```

For algebra fingerprints — null cones in split-quat, level sets of `|w·x|²`, etc. Distinct from `PointSet` because Markers carry semantic kind that the legend / tooltip can describe.

### `TangentNormal`

```
TangentNormal {
  curve:    (t) -> (x, y)        // parametric curve (or LineStrip ref)
  trigger:  HOVER_X | HOVER_T    // what mouse position means
  show:     TANGENT | NORMAL | BOTH
  length:   float                // line length in world units
}
```

Interactive overlay. When the user hovers, this layer queries its own cursor mapping and draws a tangent / normal at the corresponding curve point. Used for "look at this neuron's slope at this input value" and similar pedagogical needs.

### `Custom`

```
Custom {
  render: (view, draw) -> void
}
```

Escape hatch for one-off shapes. Used sparingly; if a `Custom` layer appears in two places, it gets promoted to a real layer kind.

## Interactivity modes

```
Interactivity = NONE | HOVER | CLICK_EMITS | DRAG_EMITS
```

- **NONE** — display only; no input. Used for tiny inline viz on collapsed nodes where the canvas needs full control of the cursor.
- **HOVER** — shows a status readout of the cursor's world position and the value(s) returned by the relevant layer's `sample` (if any). No emit, no click consumption.
- **CLICK_EMITS** — single click sets a "current point". The mini-viz exposes this as an output that other systems read (most notably: a node-graph `mini_viz_click` source).
- **DRAG_EMITS** — like `CLICK_EMITS` but updates continuously while the mouse is held. Useful for scrubbing through a parameter range visually.

Two more details:

- **Picking precedence**: when multiple interactive layers overlap, the topmost one wins. A bottom heatmap with a `PointSet` on top: clicking near a point picks the point; clicking empty space picks the heatmap location.
- **Current-point rendering**: the emitted point renders as a non-layer overlay (small target reticle), drawn last so it's never obscured.

## Composition rules

- Layers render in order (index 0 = bottom). Earlier = further back.
- Alpha is per-layer. Compositing is straight-over (premultiplied is overkill at our resolutions).
- Texture-backed layers (`ScalarField`) get one texture each; the texture is re-uploaded only when the layer's data is dirty.
- Vector primitives (`PointSet`, `LineStrip`, `Markers`, `TangentNormal`, axes) are batched together where possible — one `DrawLineStrip` for everything that's lines.
- Axes / grid are *always* a layer at index -1 (below user layers). Removing them is a per-mini-viz toggle, not a layer manipulation.

## Reuse patterns across screens

| Screen | Mini-viz role | Typical layers |
|--------|---------------|----------------|
| DSL editor preview | One viz, fixed input range, watching a single neuron | `ScalarField` (output magnitude) + `Markers` (zeros) + `TangentNormal` |
| Node graph probe | One viz per probe node, embedded in the canvas | Whatever the probe's "view" config says; one `ScalarField` is the common case |
| Data column inspector | One viz per inspected column | `PointSet` (samples) + `LineStrip` (histogram) |
| Simulator | Several at once, in a grid | Network output `ScalarField` + loss `LineStrip` + per-step marker |

The same widget. Different layer configs. The simulator panel is where having layered composition pays off the most: a single "loss with baseline + current-step marker + best-so-far highlight" view is four layers with no special-case code.

## Performance contract

- **Steady-state allocation**: zero per frame. All scratch buffers are members of the layer instance, sized at create time.
- **Re-upload heuristic**: a `ScalarField`'s texture is dirty if any of (a) the sample function changed, (b) the view changed, (c) any param it samples from changed. The mini-viz tracks dirty bits; uploads only on dirty.
- **Vector layers redraw every frame**, but they're a few thousand vertices at most, well below GPU budget at 60fps.
- **Hover doesn't trigger redraw of the underlying field** — the hover overlay is its own (cheap) layer drawn on top.
- **Off-screen mini-vizes pause**: when scrolled out of view, the layer's `render` isn't called and dirty re-uploads are deferred until visible.

## Persistence

A mini-viz instance saves its config, not its rendered contents. The serialized shape:

```json
{
  "view": { "world": [...], "aspect": "LOCK_X" },
  "layers": [
    { "kind": "scalar_field", "sample_ref": "probe.3.field", "colormap": "magenta-cyan", "alpha": 1.0 },
    { "kind": "markers", "kind_hint": "null_cone", "color": "#000" }
  ],
  "interactivity": "HOVER"
}
```

`sample_ref` is a string ID resolved against a registry of "things you can sample from" — DSL outputs, probe nodes, data columns, etc. The mini-viz doesn't hold function references; it asks the registry on every render.

## Open questions

- **Polar / log axes.** Some plots are clearer in log scale. Probably an `axis_transform` field on the view rather than a layer concern. v2.
- **Multi-axis plots** (loss + accuracy on one chart with two y-axes). Support via per-`LineStrip` axis assignment? Probably yes when we hit the use case; not v1.
- **Animation of layer transitions.** When you toggle a layer on/off, fade in/out? Probably not — "everything is deliberate" extends to visual transitions.
- **Headless render.** Saving a probe snapshot from the simulator implies rendering off-screen. Can we share the layer pipeline with a software-only path? Probably yes for everything except `ScalarField` (which currently requires a GL context for the texture upload). For headless, write the CPU buffer directly to PNG.
- **Aspect lock UX.** When the user resizes the panel, do we shrink the world rect to maintain aspect, or letterbox? Probably letterbox by default, with a "fit" mode toggle.
