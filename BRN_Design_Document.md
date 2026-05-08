# Binary Routing Network
## Architecture & Training Design Document
*v0.1 — Draft*

---

## 0. Project architecture

na-binary-utils: For all common utilities involving bitwise operations, to keep the code very clean and easy to understand.

na-neuralnetworks: For all common interfaces and general-purpose neural network code that might be reusable for other network implementations.

na-brnn: For all BRN-specific code

na-nn-visualizer: A realtime visualizer for the BRN training/inference code. Will be designed specifically for BRN initially, but designed to be easily extended later on.

---

## 1. Overview

The Binary Routing Network (BRN) is a fully discrete neural network architecture in which all expressivity resides in learned binary routing matrices rather than in weighted connections. The core primitive is a two-input XOR gate with independently learnable input routing. There are no floating-point parameters anywhere in the architecture — not in the weights, not in the activations, and not in the training algorithm.

The motivation is that float-precision networks carry substantial redundancy in their parameter representations. Quantization studies consistently show that most of the precision in a float weight is unused. BRN eliminates this redundancy by design: every bit is either routing signal or gate output, with no fractional values anywhere in the system. The expected result is high expressivity-per-bit relative to float networks of equivalent parameter count.

---

## 2. Core Primitive

### 2.1 The Gate

Each computational unit consists of:

- An input routing step: a sparse binary matrix that selects two source bits from the incoming bit vector
- A fixed XOR gate applied to the two selected bits
- The single output bit passed to the next layer

The gate operation is fixed — it is never learned. XOR is chosen because it is a balanced boolean function in which each input has equal influence over the output. XNOR is equivalent up to polarity convention; the routing matrix absorbs any polarity differences, so the choice between them is arbitrary. Fix one globally.

Fan-in is fixed at 2. Higher fan-in is equivalent to stacking additional layers with fan-in 2, so there is no benefit to increasing it — use depth instead.

### 2.2 The Routing Matrix

Each layer boundary has one binary routing matrix R of shape `[N_out x N_in]`, where each row selects exactly 2 source indices (sparse, exactly 2 non-zero entries per row). This is the only learned parameter in the network.

Routing on one side of the gate only is sufficient. The next layer's routing matrix handles any further remixing of the outputs. A single routing matrix per layer boundary keeps the architecture maximally atomic.

### 2.3 Layer Definition

A single layer is fully defined as:

```
output[i] = XOR(input[R[i,0]], input[R[i,1]])   for i in 0..N_out
```

The full network is a sequence of such layers. The forward pass is exclusively sparse binary lookups and XOR operations — no multiply-accumulate anywhere.

---

## 3. Training Algorithm

### 3.1 Error Signal

The error at the output layer is an XOR mask: the bitwise difference between the network's output and the target. This is the most natural error representation for a binary system — it directly encodes which bits are wrong without requiring a continuous loss function.

```
error = output XOR target
```

### 3.2 Backpropagation

Error propagates backward layer by layer. The error mask from layer N gets mapped in reverse through the routing table.
Each bit gets flipped according to whether the N-1 bit is opposite the N bit.
The resulting error mask becomes the error signal presented to layer N-1.

This isn't credit assignment, it's simply error translation.

### 3.3 Update Rule

For each layer, given an incoming error mask:

1. Stochastically select a fraction `p` of the flagged bits
2. For each selected bit, select some other flagged bit as a partner to trade with. Don't swap the same bit twice in the same iteration.
3. Apply the rewire

The flip rate `p` is the primary hyperparameter, analogous to learning rate. It controls how many routing entries are updated per step. Unlike learning rate in gradient descent, `p` has a natural interpretation: it is the fraction of wrong bits that get a new route each update.

### 3.4 Convergence

Convergence may not be steady, especially in the early cycles. The primary goal of early training is to
build an average correlation map for future layers to refine.
It should improve intermittently as layers are added.
However, occasionally the network may detect an anomalous positive result; it may be worthwhile to checkpoint these,
using them as a fallback if the error drifts too far (while also re-seeding the RNG or incrementing the dataset)

There are a variety of techniques that can help push convergence towards desirable numbers using simple
algorithms: these should be investigated and documented.

---

## 4. Layer Update Frequency

Earlier layers should update less frequently than later layers. The reasoning is architectural rather than empirical: early layers serve as routing substrate for all subsequent layers and need to converge to maximally general representations of the full corpus. Frequent updates destabilize this substrate.

A practical schedule: each layer tier receives updates at half the frequency of the tier above it. This is a soft prior, not a hard constraint — the stochastic dynamics handle the rest.

Critically, early layers are never frozen. They continue to receive occasional updates throughout training so they can adapt to the aggregate signal of the full corpus rather than being locked to whatever an early data subset looked like. The goal is annealing into a stable prior, not freezing.

| Layer Tier   | Relative Update Rate | Role                     | Freeze? |
|--------------|----------------------|--------------------------|---------|
| 1 (earliest) | 1x                   | Universal routing prior  | Never   |
| 2            | 2x                   | Mid-level abstraction    | Never   |
| 3            | 4x                   | Task-specific features   | Never   |
| N (latest)   | 2^(N-1)x             | Output representation    | Never   |

---

## 5. Progressive Expansion

### 5.1 Expansion Schedule

The network begins shallow (2 layers) and expands by adding fixed-size layer blocks at the output end on a predetermined schedule. The schedule is derived entirely from two quantities fixed before training begins:

- Total corpus size (tokens)
- Target final parameter count (bits in routing matrices)

From these, the number of expansion cycles and corpus allocation per cycle follow directly. No loss monitoring, no saturation detection, no adaptive triggers. The schedule is a design decision, not a runtime measurement.

### 5.2 Pass-Through Initialization

New layers are initialized as identity mappings: each output bit routes to exactly one input bit with no mixing. This is the binary analog of residual zero-initialization. The loss is continuous across expansion events — new layers begin as no-ops and gradually diverge from identity as training proceeds.

```
R_init[i, 0] = i   # route bit i to itself
R_init[i, 1] = i   # second input also maps to i (XOR(x,x) = 0, handle separately)
```

Note: XOR of a bit with itself is always 0, so the initialization requires a small adjustment — route the second input to a neighbor or use XNOR for the identity case. The exact convention is an implementation detail; the key property is loss continuity.

### 5.3 Corpus Distribution

Each expansion cycle receives an even, weighted sample of the full corpus — not a sequential slice. This ensures every layer tier, regardless of when it was added, sees the full data distribution. Early layers must not be trained on a biased subset, as they serve as the routing prior for all subsequent computation.

Standard corpus preprocessing requirements apply: deduplication, domain balancing, shuffling. These are table stakes for any LLM training regime and are not specific to BRN.

### 5.4 Why Fixed Expansion, Not Adaptive

The early layers need to represent the mean of the full corpus distribution — a smoothed, maximally general prior. This cannot be assessed by loss plateau or saturation metrics, because those layers should continue training long after they stop improving on any local measure. The correct signal for when to expand is not performance — it is the corpus budget. You expand when the current tier has seen its allocated share of the data, regardless of what the loss is doing.

---

## 6. Equivariance

A fully binary stochastic network is highly nonlinear by nature. Without structural constraints, it would need to independently learn every transformation of every input pattern, wasting capacity. Equivariance must be introduced architecturally.

The standard approach in float networks — convolutional weight tying — is incompatible with the local error propagation scheme. Shared weights create non-local credit assignment: a routing change at one position affects all positions that share the same weights, and the XOR error mask cannot apportion this correctly without global information.

The resolution is to enforce equivariance through connectivity structure rather than parameter sharing. The routing matrix's sparsity pattern is constrained to be equivariant — the same connectivity pattern is enforced across positions — but every connection has its own independent routing entry. Each bit is still updated locally and independently. Equivariance is a structural fact about the wiring topology, not a constraint on parameter values.

The cost is parameter count relative to full weight tying. In a binary network this is acceptable: each parameter costs one bit, so the overhead of independent-but-structured routing is far cheaper than it would be in a float network.

An alternative worth investigating: whether approximate equivariance emerges naturally from stochastic updates over a well-shuffled corpus, without explicit structural enforcement. If early layers converge to position-agnostic routing through the law of large numbers, explicit constraints may be unnecessary. This is an empirical question for the first implementation.

---

## 7. Relationship to Prior Work

| Approach                      | Similarity to BRN                  | Key Difference                                                              |
|-------------------------------|------------------------------------|-----------------------------------------------------------------------------|
| Binary Neural Networks (BNNs) | Binary weights and activations     | BNNs use STE with real-valued latent weights; BRN has no real values anywhere |
| BitNet b1.58                  | Native binary/ternary training     | BitNet uses gradient descent on latent weights; BRN uses routing rewire     |
| Target Propagation            | Layer-local error signals          | TP requires a backward model per layer; BRN passes raw XOR mask             |
| Progressive Training (PaLM)   | Staged depth expansion             | PaLM freezes early layers; BRN keeps them warm throughout                   |
| Sparse Random Wiring          | Learned connectivity               | Random wiring is fixed at init; BRN actively learns routing                 |

---

## 8. Open Questions for Initial Implementation

The following questions should be treated as empirical rather than theoretical — build first, measure second.

- What flip rate `p` produces stable convergence on a toy task? Is it sensitive to network depth?
- Does pass-through initialization using `XOR(x, neighbor)` preserve loss continuity adequately, or does a different identity approximation work better?
- Does equivariance need to be enforced structurally, or does it emerge from corpus statistics over sufficient updates?
- What is the relationship between layer width (bits per layer) and depth for a given task complexity?
- How does the update frequency ratio between layer tiers affect convergence speed vs. final performance?

---

## 9. Minimum Viable Implementation

The simplest test of whether the training scheme converges at all:

- 2 layers, fixed width (e.g. 64 bits per layer)
- Binary classification task on a small dataset
- Random routing initialization
- XOR error signal at output
- Stochastic rewire with fixed flip rate `p`
- No expansion, no frequency scheduling — just the core update loop

Success criterion: does the error rate drop meaningfully below random over training? If yes, the core mechanism works and the more complex features (expansion, frequency scheduling, equivariance constraints) can be layered in incrementally.

The forward pass and update rule are both simple enough to implement in a few dozen lines of code in any language with bitwise operations. The routing matrix is just an integer array of shape `[N x 2]` — no tensors, no autograd, no GPU required for the initial test.

---

*End of Document*

## ADDENDUM:
Proposal:
2 Bit Parameter XOR routing with learned NOT gates

```
Input      In   In   In   In
           |    |    |    |
-------    |    |    |    |
Layer 0   (#)  (#)  (#)  (#)  
        \/   \/   \/   \/   \
 0-1    XOR  XOR  XOR  XOR   Static XOR gates with static upstream mappings
          \    \    \    \
 0-2      [!]  [!]  [!]  [!]   Dynamic NOT gates
           |    |    |    |
 0-3     >-?-<>-?-<>-?-<>-?-<  Dynamic Routing
-------    |    |    |    |
Layer 1   (#)  (#)  (#)  (#)  
         /   \/   \/   \/   \
 1-1     ====================
 1-2     ====And so on...====
 1-3     ====================
-------    |    |    |    |
Output    Out  Out  Out  Out
```

Backprop:
1. Take the actual output, compare it with expected. Flag mismatched bits.
2. Move up to previous layer's Dynamic routing table.
3. If there are 3 or more mismatched bits, use RNG and "flip rate" hyperparameter
to determine if 3-way shuffle should occur. If not, skip 3a.
3a. Using M mismatched bits, random number R [0,1) and flip rate hyperparameter F:
If M >3 and (M*F) > R, do a 3-way flip:
Given R[i] is the route for bit i, choose 3 flagged bits a, b, c: choose new routes A,B,C.
A = R[b]
B = R[c]
C = R[a]
R[a] = A
R[b] = B
R[c] = C
And unflag the 3 bits
4. Translate remaining flagged bits to their new positions according to the routing table
5. For each remaining flagged bit, given random number R and flip rate F:
if R <= F, flip the parameter on the NOT gate and unflag the bit at that position.
6. At each XOR gate at each flagged index, there are two upstream choices.
But these are fixed routes. The XOR gate at index i should be connected to the output
of the previous layer at the same index i (plus on other bit). This means either
the error flag could be traced back to the same index, or it could be traced to
the other index i+1 (or index 0 for the last bit). So generate a random number R:
if R > 0.5 (50% chance) bitshift right 1 bit, loop the last bit back to 0.







