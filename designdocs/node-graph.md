# Node Graph

Status: design draft (pre-implementation)

The node graph is the data structure behind the Network & Node Editor. A graph defines a *function* that maps inputs to outputs — concretely, "given these data columns, evaluate this network and produce these outputs". Training and inference are both forward-passes over the same graph; the difference is whether the optimizer touches the `Learnable` nodes afterwards.

## One uniform model

Everything on the canvas is a node. Everything connecting nodes is a wire. There is no special-cased "parameter binding": a node's parameters are *just typed input ports*, fed by wires from other nodes — usually `Slider` or `Learnable` nodes for tunable / trainable params, but it could be anything that produces the right type.

This is the entire data model:

```
Network {
  id:       UUID
  nodes:    List<Node>
  edges:    List<Edge>
  viewport: { x, y, zoom }    // for the editor; not part of evaluation
}

Node {
  id:       UUID
  kind:     String            // "neuron.split_mix", "builtin.slider", ...
  position: { x, y }          // canvas position; not part of evaluation
  state:    Map<String, Value> // node-private state (slider's current value, learnable's weights)
  collapse: AUTO | EXPANDED | COLLAPSED  // editor display hint, not eval state
}

Port {
  name:     String
  type:     Type              // scalar, vec2, complex, ..., or a stream-of-T
  index:    int
}

Edge {
  from: { node_id, port_name }
  to:   { node_id, port_name }
}
```

Nodes don't carry their input/output port lists in the saved data — those are looked up from the `kind` registry at load time. This keeps the persisted format small and guarantees the ports always match the current version of the node kind (a v2 `split_mix` neuron with a new output port "just appears" in old saves).

## Built-in node kinds

The built-in catalog. Anything user-defined (DSL neurons, saved networks) appears here too at runtime, but these are the ones the app ships with.

### Value sources (zero inputs, one output)

| Kind                   | State                       | Output           | Notes |
|------------------------|-----------------------------|------------------|-------|
| `builtin.constant`     | `value: T`                  | `value: T`       | Literal of any type. |
| `builtin.slider`       | `min, max, value: scalar`   | `value: scalar`  | UI slider; user drags during sim. |
| `builtin.range_slider` | `min, max, lo, hi: scalar`  | `lo, hi: scalar` | Two-handle, two outputs. |
| `builtin.learnable`    | `value: T, init, optimizer` | `value: T`       | Updated by training. State is the trained value. |
| `builtin.data_column`  | `dataset_id, column_name`   | `value: T`       | Streams one row's value per evaluation tick. |
| `builtin.input`        | `name: String, type: T`     | `value: T`       | Network's external input. Bound from outside the graph. |

### Sinks (one input, zero outputs)

| Kind                 | State                | Notes |
|----------------------|----------------------|-------|
| `builtin.output`     | `name: String`       | Network's external output. |
| `builtin.probe.field` | `viz_config: ...`   | Captures values into a mini-viz layer. |
| `builtin.probe.metric` | `name, agg: ...`   | Records a scalar metric per tick (loss, accuracy). |

### Combinators (n inputs, m outputs)

| Kind                 | Inputs                  | Outputs           | Notes |
|----------------------|-------------------------|-------------------|-------|
| `builtin.split`      | `(a, b): vec2`          | `a, b: scalar`    | Type-aware unpack. |
| `builtin.join`       | `a, b: scalar`          | `(a, b): vec2`    | Type-aware pack. |
| `builtin.add`        | `a, b: T`               | `a + b: T`        | Algebra-dispatched. |
| `builtin.mul`        | `a, b: T`               | `a · b: T`        | Algebra-dispatched. |
| `builtin.activation` | `x: scalar`             | `f(x): scalar`    | Wraps a DSL activation; specific kind = `builtin.activation.tanh` etc. |
| `builtin.loss`       | `pred, target`          | `loss: scalar`    | Wraps a DSL loss. |

### User-defined

| Kind                  | Notes |
|-----------------------|-------|
| `dsl.neuron.<name>`   | One per `.nl` file in `dsl/neurons/`. Inputs/outputs/params come from the DSL declaration. |
| `network.<id>`        | A saved network used as a sub-network. Inputs are the parent net's `builtin.input` nodes; outputs are its `builtin.output` nodes. Recursive composition with cycle detection at load time. |

## Evaluation

A network is a directed acyclic graph (cycle detection at edit time, not at run time — see Constraints). Forward pass:

1. Topological sort.
2. For each node in order:
   1. Read input port values from upstream edges.
   2. Apply the node's eval function `(inputs, state) → outputs`.
   3. Cache outputs for downstream reads.

That's the whole forward pass. There's no special handling for "training nodes" vs "inference nodes" — `Learnable.eval` just returns `state.value`. Training is what happens *after* the forward pass.

### Backward pass (for training)

Reverse topological order. For each node:

1. Look up the gradient of its outputs (from downstream nodes' input gradients, summed).
2. Compute gradients of its inputs and state via the node's `backward(in_values, in_grads, out_grads, state)` function.
3. If the node is a `Learnable`, the optimizer applies `state += -lr * grad_state`.

Every built-in node has a hand-written `backward`. DSL neurons get a backward derived from the body's expression DAG by symbolic differentiation at parse time (no runtime autodiff overhead).

### Streaming vs batched

A "tick" of the network corresponds to one row from each `data_column` source. Training iterates over rows. Eventually we'll batch this — `data_column` becomes a stream of `vec[batch_size]` and every operator gets a vectorized path — but v1 is per-row.

## Constraints (checked at edit time)

- **Type-checked wires.** An edge from a `complex` output to a `scalar` input is a type error; the editor refuses the connection, with the inspector showing why.
- **No cycles** in the data DAG. Cycle detection runs after every edge addition; the editor prevents the bad edge.
- **One incoming edge per port.** Multiple wires into the same input port doesn't make sense for our semantics; if you want a sum, use a `builtin.add` node.
- **Outputs may have many fan-outs.** A `slider` value can drive ten neurons. No fan-out limit.
- **Required ports must be wired** before the network is "valid" (runnable). A network with unwired required inputs is allowed in the editor — it just shows a "not runnable: 2 unconnected inputs" indicator and refuses to be passed to the simulator.

Optional ports (rare, used for things like a default-zero bias input) are an explicit `optional: true` flag in the kind's port spec.

## Display: collapse rules

Three collapse states drive how a node renders on the canvas. None of them affect evaluation.

### Single-port leaf collapse

A node with **only one input** *or* **only one output** can render *inside* the node it connects to, on the relevant edge. The user clicks the parent node to expand the leaf back out for editing.

```
  before:                  after (slider collapsed into neuron):

  ┌─────────┐              ┌──────────────────┐
  │ slider  │              │  split_mix       │
  │  ●──────┼─────●──┐     │  in:  x, y       │
  │         │        │     │  param: a [▁▂▄▆█]│  ← inline slider
  │ a [▁█]  │        │     │           b [...] │
  └─────────┘        ●─────┤  out: u, v        │
                           └──────────────────┘
```

This is how the canvas stays readable when you have a complex network full of `Slider` and `Constant` nodes — they just become inline UI on the neurons they parameterize. Selecting the neuron turns them back into proper canvas nodes (with their own positions remembered).

### Pass-through dot collapse

A node with **exactly one input and one output** of the same type renders as a small dot on the edge between its source and destination. Click the dot to expand. Loses focus → re-collapses.

```
  before:                  after (activation as dot):

  [neuron]──●──┐           [neuron]──●─┐
               │                       ├──●──[next]
  [activation]─┤                            │
               │                          tanh
  [next]───────●
```

Nodes with the same shape but different in-out types still expand by default — the type change is information worth showing (a `vec2 → scalar` "magnitude" node carries semantic weight a tanh doesn't).

### Manual override

A user can pin any node `EXPANDED` or `COLLAPSED` via the inspector. `AUTO` is the default and applies the rules above. Manual state is persisted in the network file.

## Sub-network references

Saving a network as a node makes it reusable: it appears in the palette as `network.<id>`. Behavior:

- The sub-network's `builtin.input` and `builtin.output` nodes become the new node's input and output ports. The port type is each input's declared type.
- `Slider` / `Learnable` / `data_column` nodes inside the sub-network are *NOT exposed* by default — the sub-network is sealed. To expose a parameter, the user manually replaces an internal `Slider` with a `builtin.input` of the same type and re-saves.
- Cycles across the network/sub-network boundary are still forbidden. Detection happens at sub-network instantiation.
- Sub-networks are referenced **by id**, not embedded. Modifying the sub-network later updates everywhere it's used.

## Persistence

Networks save to `networks/<name>.json`. Hand-rolled JSON, same conventions as `project-format.md`. One file per network. Sub-network references serialize as `{ "kind": "network.<id>" }` — the resolver chases the id at load time.

## Open questions

- **Optimizer per Learnable, or one optimizer for the whole network?** Per-Learnable is more flexible (Adam for the embeddings, SGD for the readout) but most users will set one optimizer for the whole network. Defaulting to one-per-network with per-Learnable override.
- **Probe lifecycle.** A `probe.field` node holds a snapshot of values for visualization. When does that snapshot reset — every tick? Every epoch? On manual demand? Probably "follows the simulator's frame" — every UI repaint = one probe sample.
- **What types are streamable?** Right now I'm assuming all types stream identically (one row per tick). When we batch, do we keep the per-row API and let the runtime fuse, or expose `vec[batch]` as a first-class type? Likely the former; saves the user from thinking about batching.
- **Node groups.** When the canvas gets dense, users will want to box-select and "group these into a logical block". Probably just a visual annotation (named rectangle around nodes), not a structural change to the graph. Easy to add later.
