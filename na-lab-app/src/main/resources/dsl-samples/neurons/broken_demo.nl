// Deliberately broken — open this file to see how the editor surfaces compile errors.
//
// Expected diagnostics:
//   - 'a' cannot be reassigned (declared as param)
//   - duplicate assignment to 'u' (single-assignment rule)
//   - 'v' is never assigned (output left dangling)
//   - 'nope' is unknown
//   - mat2 component access via dot is not allowed (must use .[r, c])

neuron broken_demo {
  in:    x, y
  param: a, b
  out:   u, v

  a = 1.0                 // illegal: param is read-only
  u = a*x + nope*y        // illegal: 'nope' is unknown
  u = a + b               // illegal: duplicate assignment
}
