// The linear baseline — purely elementwise. Each output depends on only one input.
// Useful as a control when comparing against algebra-aware mixers.

neuron linear_mix {
  in:    x, y
  param: a, b
  out:   u, v

  u = a*x
  v = b*y
}
