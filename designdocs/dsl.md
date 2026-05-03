# Neuralchemy DSL

Status: design draft (pre-implementation)
File extension: `.nl`

A small, brace-y, expression-oriented language for defining the building blocks of a network — neurons, activation functions, and loss functions — in a form that's easy for a human to read and easy for a hand-rolled recursive-descent parser to consume. Designed to map cleanly onto a Truffle interpreter later without forcing Truffle on day one.

## Goals

1. **Read like math.** The body of a neuron should look like the formula a researcher would write on a whiteboard.
2. **Trivial to parse.** No lookahead games, no significant whitespace, no operator-precedence ambiguity.
3. **Pure functions.** A neuron is a function of `(inputs, params)`; activation/loss are smaller variants. No side effects, no mutation of params from inside a body.
4. **Allocation-flat at runtime.** Every body compiles to fixed-shape scalar arithmetic over the unpacked components of its typed values. SoA-ready when we move to batched evaluation.
5. **Forward-compatible with Truffle.** Each AST node maps 1:1 to a Truffle node when we cut over. No constructs that resist partial evaluation.

## Non-goals (v1)

- No control flow (no `if`, no `while`, no recursion). Pure expression DAGs.
- No user-defined types. The built-in algebras are the type system; we'll revisit if a real need shows up.
- No imports / modules. Each file declares one top-level artifact (one neuron, one activation, or one loss).
- No mutable locals. Every `=` introduces a single-assignment binding; reuse the same name and you get a parse error.

These keep v1 small. Each one has an obvious extension path if the project demands it.

## File-level structure

Every `.nl` file declares exactly one of:

```
neuron     <name> { ... }
activation <name>(<arg>) = <expr>
loss       <name>(<pred>, <target>) = <expr>
```

The file's filename matches `<name>.nl`. The artifact kind is determined by the first keyword, not the directory — the `dsl/neurons/`, `dsl/activations/`, `dsl/losses/` directory split is a UX convenience for the editor's sidebar, not a semantic one.

## Neurons

```
neuron split_mix {
  in:    x, y                 // declared inputs (per-call)
  param: a, b                 // declared parameters (persist across calls)
  out:   u, v                 // declared outputs

  u = a*x + b*y
  v = b*x + a*y
}
```

Section order is fixed: `in:`, `param:`, `out:`, then body. Each section is optional except `in:` and `out:` (a paramless neuron is a useful constant function; an inputless one is a constant generator).

Bodies are sequences of `<name> = <expr>` assignments. The final state of every name listed in `out:` is what the neuron returns. Every output must be assigned somewhere in the body.

### Typed declarations

A name in `in:` / `param:` / `out:` may carry a type annotation:

```
neuron complex_mix {
  in:    z: complex
  param: w: complex
  out:   y: complex

  y = w * z
}
```

Without a type annotation, names default to `scalar` (a single `float`).

Built-in types — these are the algebras we care about:

| Type            | Components | Multiplication                                   |
|-----------------|-----------:|--------------------------------------------------|
| `scalar`        |          1 | ordinary real multiplication                     |
| `vec2`          |          2 | elementwise (no algebra; the linear baseline)    |
| `complex`       |          2 | `(a+bi)(c+di) = (ac-bd) + (ad+bc)i`              |
| `splitcomplex`  |          2 | `(a+bj)(c+dj) = (ac+bd) + (ad+bc)j`              |
| `quaternion`    |          4 | Hamilton (i² = j² = k² = ijk = -1)               |
| `coquat`        |          4 | split-quaternion (i² = -1, j² = k² = +1, ij = k) |
| `mat2`          |          4 | 2×2 real matrix multiplication                   |

The list is the union of "things we want to compare in the parameter-mixing demo". Adding a new algebra is a runtime-side change in `lab.algebra` plus one line in the type table — no parser change.

## Activations

```
activation tanh_squared(x) = tanh(x) * tanh(x)
```

Activations are scalar→scalar functions, no params, no body block. The right-hand side is a single expression. Use them in neuron bodies as ordinary calls: `u = tanh_squared(a*x + b*y)`.

A multi-arg activation is just a multi-arg one-line function:

```
activation softmax2(x, y) = ...   // returns a tuple
```

If the body needs to be more than one expression, promote it to a neuron with no params.

## Losses

```
loss mse(yhat, y) = (yhat - y)^2
```

Losses are functions returning a `scalar`. By convention `(prediction, target)` argument order. The training loop sums them across a batch — the loss DSL doesn't iterate.

For losses that need vector inputs, declare the type:

```
loss mse_vec(yhat: vec2, y: vec2) = (yhat - y) · (yhat - y)
```

(`·` is the dot product. See operators below.)

## Expressions

### Literals

- Numbers: `1.0`, `-3`, `2.5e-4` — always parsed as `float`.
- Vector / algebra literals: `vec2(1, 0)`, `complex(0, 1)`, `quaternion(1, 0, 0, 0)`. Component count must match the type.
- Boolean: not in v1.

### Operators

Standard precedence (highest to lowest):

```
^                  power (right-associative)
unary -, !
*, /               multiplicative — dispatches on type
+, -               additive
·                  dot product (vec/algebra-typed only)
                   (no comparison, logical, or assignment operators in v1)
```

`*` and `/` dispatch on their operand types: scalar×scalar is real mul; complex×complex is complex mul; etc. Mixed-type cases (scalar×complex) are scalar broadcasts. Mismatched algebras (e.g. `complex × quaternion`) is a type error.

### Built-in scalar functions

`exp`, `log`, `sin`, `cos`, `tan`, `tanh`, `sigmoid`, `relu`, `abs`, `sqrt`, `floor`, `ceil`, `min`, `max`, `clamp(x, lo, hi)`, `lerp(a, b, t)`.

Activation functions defined in the workspace are also callable from any neuron's body — same call syntax (`my_activation(x)`).

### Built-in algebra ops

For every algebra type `T`:
- `T.zero`, `T.one`, `T.identity` — literal constants.
- `conj(z)` — complex/quaternion/coquat conjugate.
- `norm(z)` — multiplicatively-invariant norm of the algebra (Euclidean for `complex` and `quaternion`; Lorentz for `splitcomplex` and `coquat`; det for `mat2`).
- `re(z)`, `im(z)` — for `complex` / `splitcomplex`.

Component access works on any typed value via dot:

```
y.x          // first component of a vec2 / complex / etc.
q.k          // k-component of a quaternion
m.[1, 1]     // element of a mat2 (zero-indexed)
```

## Compile-time rules

These are checked at parse / type-check time, never at runtime:

1. **Single-assignment.** `u = …` may appear at most once per neuron body for each name.
2. **Outputs must be defined.** Every name in `out:` is assigned in the body.
3. **Inputs/params are read-only.** A body cannot reassign a name declared in `in:` or `param:`.
4. **No allocation in bodies.** All intermediate locals are unpacked-scalar at the IR level; the parser allows the abstraction, the IR rejects anything that would require a heap allocation.
5. **No control flow** (until v2).

## Examples

### A linear baseline neuron

```
neuron linear_mix {
  in:    x, y
  param: a, b
  out:   u, v
  u = a*x
  v = b*y
}
```

### A complex neuron, written in scalars

```
neuron complex_mix_scalar {
  in:    x, y
  param: a, b
  out:   u, v
  u = a*x - b*y
  v = a*y + b*x
}
```

### The same neuron, written algebraically

```
neuron complex_mix {
  in:    z: complex
  param: w: complex
  out:   y: complex
  y = w * z
}
```

These two neurons are equivalent at the IR level — the algebraic form unpacks into the same scalar operations.

### A residual block

```
neuron resid {
  in:    x: vec2
  param: a, b: scalar
  out:   y: vec2
  y = vec2(a*x.x + b*x.y, b*x.x + a*x.y) + x
}
```

### Loss

```
loss huber(yhat, y, delta) = ...   // not legal: extra param
loss huber(yhat, y) = ...           // legal: only (pred, target)
```

If you need extra knobs in the loss, expose them as **constants in the surrounding network** (a `Constant` node wired to a port the loss reads from). Loss functions stay 2-arg.

## Future (v2+, in rough order of likelihood)

- `if/else` expressions for piecewise activations.
- User-defined struct types for ad-hoc tuples beyond the built-in algebras.
- A `let` form for sharing intermediate computations across multiple outputs without repeating subexpressions (or just rely on the IR's CSE — TBD).
- Explicit ranges (`for i in 0..n`) — only if matrix-shaped neurons start showing up, which I doubt at the abstraction level we're targeting.
- Truffle migration. The AST / IR shapes here are designed to map 1:1.

## Open questions

- **Loss arity.** Hard-locking to `(pred, target)` is clean; some losses (KL divergence between distributions, contrastive losses) want different shapes. Punting: when we hit one, generalize to "any number of inputs of a declared type".
- **Implicit broadcasting.** `scalar * vec2` is fine; what about `complex * vec2` (where the input is a vec2 but a parameter is a complex)? Likely an error in v1 with a clear "embed via `complex(v.x, v.y)` first" hint.
- **Where do constants live?** In-body literals are clear; named constants shared across files (`pi`, `e`, project-defined `epsilon`) are TBD. Probably a `const` declaration at the top of any file, hoisted into the workspace symbol table.
