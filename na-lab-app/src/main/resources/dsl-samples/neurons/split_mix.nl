// The lead example from dsl.md — a 2-input, 2-output neuron with two parameters.
// Mixes its inputs the same way a 2x2 matrix would, just written componentwise.

neuron split_mix {
  in:    x, y                 // declared inputs (per-call)
  param: a, b                 // declared parameters (persist across calls)
  out:   u, v                 // declared outputs

  u = a*x + b*y
  v = b*x + a*y
}
