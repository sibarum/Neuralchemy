// A single-line activation: tanh(x)^2 expressed as tanh(x) * tanh(x).
// Activations are scalar -> scalar by default; multi-arg variants are also legal.

activation tanh_squared(x) = tanh(x) * tanh(x)
