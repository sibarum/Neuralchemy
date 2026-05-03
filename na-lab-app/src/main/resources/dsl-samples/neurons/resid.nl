// A residual block: applies a 2x2 mix to a vec2 and adds the input back.
// Demonstrates: typed inputs, vec2 constructors, component access via .x / .y.

neuron resid {
  in:    x: vec2
  param: a, b: scalar
  out:   y: vec2

  y = vec2(a*x.x + b*x.y, b*x.x + a*x.y) + x
}
