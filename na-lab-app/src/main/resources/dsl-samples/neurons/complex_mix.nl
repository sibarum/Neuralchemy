// The same operation as split_mix, written algebraically over the complex numbers.
// At the IR level this unpacks into the same scalar arithmetic as complex_mix_scalar.

neuron complex_mix {
  in:    z: complex
  param: w: complex
  out:   y: complex

  y = w * z
}
