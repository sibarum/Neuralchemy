// Vector form of MSE — uses the dot product '·' to collapse a vec2 difference to a scalar.

loss mse_vec(yhat: vec2, y: vec2) = (yhat - y) · (yhat - y)
