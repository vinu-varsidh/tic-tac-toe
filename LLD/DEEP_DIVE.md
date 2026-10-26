# Tic-Tac-Toe — Deep Dive

This document explains **how the game works and why every class is shaped the way it is**: the real problem, end-to-end game traces, and for each class its state, invariants, design decisions and subtle details. It assumes no prior knowledge of the design.

Other docs: [README](../README.md) (API, rules, diagram).

---

## Table of contents

1. [The real problem](#1-the-real-problem)
2. [Vocabulary you need first](#2-vocabulary-you-need-first)
3. [The big picture](#3-the-big-picture)
4. [End-to-end walkthroughs](#4-end-to-end-walkthroughs)
5. [Class-by-class: decisions and invariants](#5-class-by-class-decisions-and-invariants)
6. [Why the win check is O(N) and correct](#6-why-the-win-check-is-on-and-correct)
7. [Cross-cutting decisions](#7-cross-cutting-decisions)
8. [Known flaws and limitations](#8-known-flaws-and-limitations)
9. [How the pieces would change](#9-how-the-pieces-would-change)
10. [Self-test](#10-self-test)

---

## 1. The real problem

Tic-tac-toe looks trivial, but a *well-designed* version has to settle several things most quick implementations leave fuzzy:

| Question | A sloppy answer | This design's answer |
|----------|-----------------|---------------------|
| What if a player picks an occupied cell? | Overwrite it, or switch turns anyway | Reject the move; **nothing changes**; same player tries again |
| Who is responsible for the rules? | Mixed into the `main` loop / UI | One domain class (`Game`), UI-free |
| How do you detect a win on an N×N board? | Rescan the whole board after every move: O(N²) | Check only the lines through the last move: O(N) |
| What if the last move both fills the board *and* wins? | Report a draw | A win takes precedence |
| What if two threads call `playMove` at once? | Corrupted board / two moves in one turn | All state changes are atomic |
| What happens after the game ends? | Moves silently accepted | `GameOverException` |

Everything is organised around one idea: **a move is an all-or-nothing transaction.** It is validated completely *before* anything is changed, and after it succeeds the game is again in a fully consistent state.

---

## 2. Vocabulary you need first

**Invariant** — a fact that must always be true about an object, whatever sequence of calls happens. Example: "the move counter equals the number of filled cells".

**Atomic (all-or-nothing)** — an operation either completes fully or leaves everything exactly as it was. A rejected move must not change the board, the turn or the counter.

**Terminal state** — a state with no way out. `X_WON`, `O_WON` and `DRAW` are terminal; the game accepts no more moves.

**Encapsulation** — hiding internal state behind a small API so outsiders cannot break invariants. `Board` is hidden entirely; only `Game` can touch it.

**Facade** — one class (`Game`) presenting a small, safe API over several internal pieces.

**Separation of concerns** — game logic, rendering and demo code live in different classes so each can change independently.

---

## 3. The big picture

### 3.1 Who owns what

```text
                    Caller (demo / UI / network layer)
                                │  playMove(row, col), getStatus(), getCell(...)
                                ▼
                     ┌─────────────────────┐
                     │        Game         │  public, thread-safe facade
                     │  turn + status      │  knows players and whose turn it is
                     └─────────────────────┘
                         │            │
                         ▼            ▼
                ┌──────────────┐   Player (record: name + mark)
                │    Board     │   GameStatus / Mark (enums)
                │ (package-    │
                │  private)    │   knows cells and lines — nothing about players
                └──────────────┘

         BoardRenderer ──reads──> Game (public getters only)   ← the "view"
```

**Division of knowledge:**

| Class | Knows about | Does NOT know about |
|-------|-------------|---------------------|
| `Board` | cells, bounds, lines, move count | players, turns, game status |
| `Game` | players, turn order, status | how lines are checked, how cells are stored |
| `BoardRenderer` | how to draw a `Game` | how the game decides anything |

### 3.2 Package map

| Package | Contents | Visibility |
|---------|----------|------------|
| `enums` | `Mark`, `GameStatus` | public |
| `model` | `Player` | public |
| `game` | `Game` (public), `Board` (**package-private**) | `Board` hidden |
| `exception` | `InvalidMoveException`, `GameOverException` | public |
| `view` | `BoardRenderer` | public |
| (root) | `TicTacToeDemo`, `TicTacToeVerification` | entry points |

### 3.3 The key design move: `Board` is package-private

`final class Board` has no `public`. Code outside `com.tictactoe.game` cannot even name the type, so the only way to change a board is through `Game.playMove`, which enforces turns and game-over. That makes the *most important invariant* (only legal moves reach the board) **impossible to bypass**, instead of merely documented.

---

## 4. End-to-end walkthroughs

### 4.1 A complete game on 3×3 (the demo)

Coordinates are zero-based: `(row, col)`, `(0,0)` top-left. Alice is X (moves first), Bob is O.

| # | Call | Mover | Board after | Status | Next |
|---|------|-------|-------------|--------|------|
| 1 | `playMove(0,0)` | Alice X | `X . .` / `. . .` / `. . .` | IN_PROGRESS | Bob |
| 2 | `playMove(1,0)` | Bob O | O at (1,0) | IN_PROGRESS | Alice |
| 3 | `playMove(0,1)` | Alice X | X at (0,0),(0,1) | IN_PROGRESS | Bob |
| 4 | `playMove(1,1)` | Bob O | O at (1,0),(1,1) | IN_PROGRESS | Alice |
| 5 | `playMove(0,2)` | Alice X | top row all X | **X_WON** | (unchanged) |

Final render:

```text
 X | X | X
---+---+---
 O | O |
---+---+---
   |   |
```

**What `playMove` does on each call** (this is the whole algorithm):

```text
1. status finished?                  -> throw GameOverException      (nothing changed)
2. mark = currentPlayer.mark()
3. board.placeMark(row, col, mark)   -> validates bounds, then emptiness, THEN writes the cell and moves++
                                        (either throws InvalidMoveException with nothing changed, or succeeds)
4. board.isWinningMove(row,col,mark) -> status = X_WON / O_WON
   else board.isFull()               -> status = DRAW
   else                               -> switch currentPlayer
5. return status
```

### 4.2 Rejected moves leave no trace

After move 1 above (X at `(0,0)`), Bob plays `(0,0)` again:

- Step 3 finds the cell occupied → `InvalidMoveException("Cell is already occupied: (0, 0)")`.
- The cell, the move count (still 1), the status and `currentPlayer` (still Bob) are untouched. Bob simply plays again.

Same for `(-1, 0)` or `(0, 3)` on a 3×3 board (out of bounds) — thrown *before* any write.

### 4.3 Playing after the game ended

After move 5, `playMove(2,2)` hits step 1 → `GameOverException("Game has already ended: X_WON")`. The board is not touched. Note that after the game ends `getCurrentPlayer()` returns the **last mover** (the turn is only switched when the game continues).

### 4.4 A draw (verified sequence)

Nine alternating moves: `(0,0) (0,1) (0,2) (1,2) (1,0) (2,0) (1,1) (2,2) (2,1)` produce

```text
 X | O | X
---+---+---
 X | X | O
---+---+---
 O | X | O
```

No line is complete. After move 9 `board.isFull()` is true and no win was detected → `DRAW`.

### 4.5 A win on the very last move beats a draw

If move 9 completes a line, step 4 checks **win first**, then full. A board that is full *and* contains a winning line is reported as a win, never a draw. (Verification test: `checkWinOnFinalMoveBeatsDraw`.)

### 4.6 Special sizes

| Board | Behaviour |
|-------|-----------|
| 1×1 | The first move fills the only row/column/diagonal → X wins immediately. |
| 3×3 | Classic. |
| 4×4, 5×5, 10×10 | Same code; a win needs **N** in a row (the whole line). |
| 0×0 or negative | `IllegalArgumentException("Board size must be positive")` at construction. |

### 4.6b Which lines are checked after a move at `(r, c)` on a 4×4 board

| Move | Lines checked |
|------|---------------|
| `(0,1)` | row 0, column 1 |
| `(2,2)` | row 2, column 2, **main diagonal** (`r == c`) |
| `(0,3)` | row 0, column 3, **anti-diagonal** (`r + c == N-1 → 3`) |
| `(1,2)` | row 1, column 2 (`1≠2`, `1+2=3` → **also anti-diagonal**) |

---

## 5. Class-by-class: decisions and invariants

---

### 5.1 `Mark` (`enums`)

`X`, `O`, plus `opponent()` returning the other one.

**Invariant.** There are exactly two marks, so a board cell is one of `X`, `O` or empty (`null`).

**Decisions.**
- An enum, not a `char`/`String`: the compiler rejects `'Z'` or `"x"`.
- Empty is represented by `null` in the board rather than a third enum value. *Tradeoff:* null checks, but the type "Mark" then truly means "a player's mark".
- `opponent()` is not currently called by `Game` (which switches players using the `Player` references). It is a small convenience for future code such as an AI.

---

### 5.2 `GameStatus` (`enums`)

`IN_PROGRESS`, `X_WON`, `O_WON`, `DRAW`, with two helpers.

| Member | Meaning |
|--------|---------|
| `isFinished()` | `this != IN_PROGRESS` — the single definition of "terminal". |
| `wonBy(Mark)` | maps a mark to the right winning status. |

**Invariant.** Exactly one status at a time; the three non-`IN_PROGRESS` values are terminal.

**Decision — put behaviour in the enum.** `Game` never writes `status == X_WON || status == O_WON || status == DRAW`; it asks `isFinished()`. If a new terminal state appears (e.g. `ABANDONED`) there is one place to update.

**Decision — status is derived and stored, not recomputed.** The winner/draw is decided once at the moment of the move and stored, rather than re-scanning the board whenever someone asks `getStatus()`.

---

### 5.3 `Player` (`model`)

A record `(name, mark)`.

**Invariants.**
1. Name is neither null nor blank.
2. Mark is never null.
3. The name is **stripped** (`"  A "` becomes `"A"`) — so equality and display are stable.
4. It is immutable (record).

**Decisions.**
- A *compact constructor* validates and normalises in one place; no `Player` object can exist in an invalid state.
- `Game` creates the two players itself (`playerX`, `playerO`) so it can guarantee **one X and one O** — callers cannot build a game with two X players.
- Records give `equals`/`hashCode`/`toString` for free; `Game` compares players by reference when switching turns, which is safe because it holds the only two instances.
- Note: nothing forbids both players having the *same name*; only marks distinguish them.

---

### 5.4 `Board` (`game`, package-private)

**State.** `size` (final), `cells[size][size]` of `Mark` (null = empty), `moves` counter.

**Invariants.**
1. **`size ≥ 1`** (constructor rejects otherwise) and never changes.
2. **Coordinates are always valid before access** — every public method calls `validateCoordinates`.
3. **A cell is written at most once and never cleared.** `placeMark` refuses an occupied cell; there is no `clear`/`undo`.
4. **`moves` equals the number of non-null cells.** `moves++` happens together with the single write, after all checks pass.
5. **`isFull()` ⇔ `moves == size * size`.**
6. **Validation precedes mutation**, so a failed `placeMark` changes nothing.

**Method by method.**

`placeMark(row, col, mark)`

```text
mark == null?            -> IllegalArgumentException       (programmer error)
out of bounds?           -> InvalidMoveException            (player error)
cell already occupied?   -> InvalidMoveException            (player error)
cells[row][col] = mark; moves++                             (the only mutation)
```

`isWinningMove(row, col, mark)` — checks **only** the lines through `(row, col)`:

```java
return rowComplete(row, mark)
    || columnComplete(col, mark)
    || (row == col && mainDiagonalComplete(mark))
    || (row + col == size - 1 && antiDiagonalComplete(mark));
```

It first confirms the cell really contains `mark` (otherwise `false`). Each `...Complete` method is a simple loop of N comparisons.

`isFull()`, `getCell`, `getSize`, `getMoveCount` — straightforward reads; `getCell` validates bounds.

**Decisions.**
- *`Board` knows nothing about players or turns.* It would work unchanged for a different game (e.g. Connect-style variants) or a two-player-less puzzle. Rules about *whose* turn live in `Game`.
- *Two exception types by cause:* `InvalidMoveException` for things a player can do wrong (retryable), `IllegalArgumentException` for programmer errors (`null` mark).
- *`moves` counter instead of scanning for emptiness:* `isFull()` is O(1).
- *Short-circuit loops:* each line check returns `false` at the first mismatch, so typical cost is far below N.
- *Not thread-safe by itself* (documented in its Javadoc). It relies on `Game`'s lock. This is intentional: locking at two levels would add cost with no extra safety, and since `Board` is package-private nobody else can reach it.
- *A subtle consequence of the check order:* because `isWinningMove` is only called after a successful placement, the "cell must contain `mark`" guard is a safety net, not part of the normal path.

---

### 5.5 `Game` (`game`)

The public facade and the only writer of turn/status.

**State.** `board`, `playerX`, `playerO` (final); `currentPlayer`, `status` (mutable, guarded by the object's monitor).

**Invariants.**
1. **X always moves first** (constructor sets `currentPlayer = playerX`).
2. **Turns strictly alternate**, and the turn switches **only after an accepted move that leaves the game in progress**.
3. **No move is accepted once `status.isFinished()`.**
4. **Status always matches the board:** `X_WON`/`O_WON` ⇒ the last mover completed a line; `DRAW` ⇒ board full and no winner; `IN_PROGRESS` ⇒ no complete line and not full.
5. **Rejected moves are invisible:** board, move count, status and current player are unchanged.
6. **All access is serialised** — every public method is `synchronized`, including the getters.

**`playMove` ordering is the heart of the design:**

| Order | Step | Why in this position |
|-------|------|----------------------|
| 1 | game-over check | Before touching anything — terminal means terminal. |
| 2 | `board.placeMark` | Validates **and** mutates; throws before mutating if invalid. |
| 3 | win check | Must precede draw so a winning final move isn't a draw. |
| 4 | draw check | Only if not a win. |
| 5 | switch player | Only if the game continues — so on a finished game `getCurrentPlayer()` stays on the last mover. |

After step 2 succeeds, steps 3–5 cannot throw (the coordinates are already known valid), so the object can't be left half-updated.

**Decisions.**
- *Coarse `synchronized` on the whole object.* One lock = one consistent view; tic-tac-toe has trivially small critical sections, so simplicity beats cleverness. The getters are synchronized too, so a reader never sees a half-applied move.
- *Constructor takes names and a size, not `Player`/`Board` objects.* The `Game` constructs its own parts, which is what guarantees invariant 1 and the two-distinct-marks property.
- *`getWinner()` returns `Optional<Player>`* — absence is a normal state (game in progress or draw), not an error or `null`.
- *Getters instead of exposing the board.* Callers read cells one at a time through `getCell`; they can never obtain a mutable reference.
- *`playMove` returns the new status* so a caller can react without a second call.

---

### 5.6 Exceptions (`exception`)

| Exception | Extends | Thrown when | What the caller should do |
|-----------|---------|-------------|---------------------------|
| `InvalidMoveException` | `RuntimeException` | out-of-bounds or occupied cell (also an out-of-bounds `getCell`) | ask the **same** player to retry |
| `GameOverException` | `IllegalStateException` | move after a terminal status | stop; show the result |

**Decisions.**
- *Two types, not one:* the right reaction is different (retry vs. stop), and a UI can catch each separately.
- `GameOverException` extends `IllegalStateException` because it really is "wrong state for this operation"; generic handlers still catch it.
- Both are unchecked so the happy path isn't cluttered with `throws`.

---

### 5.7 `BoardRenderer` (`view`)

A static utility with a private constructor (`render(Game) → String`).

**Decisions.**
- *Presentation is not in the domain.* `Game` has no `toString()` that draws a board. You can replace this with a Swing/web/JSON view without changing game logic.
- It uses **only public getters** (`getBoardSize`, `getCell`), proving the public API is sufficient for a real client.
- The separator `"---+".repeat(size - 1) + "---"` scales with board size.
- *Subtlety:* each `getCell` call locks separately, so if another thread moves *while* rendering, the picture can mix before/after states (see 8.1). For a single-player demo this is irrelevant.

---

### 5.8 `TicTacToeDemo` and `TicTacToeVerification`

- `TicTacToeDemo` plays the 5-move game of 4.1 and prints the board, status and winner.
- `TicTacToeVerification` is a dependency-free check runner covering: row, column, main-diagonal and anti-diagonal wins; O winning; draw; **win on the final move beats draw**; turn alternation; occupied-cell rejection **without switching turns**; out-of-bounds; move after game over; player and size validation; 1×1; larger boards; and a **concurrency test** — 100 moves submitted from an 8-thread pool on a 10×10 board, checking that accepted moves equal the board's move count (nothing lost or double-counted).

---

## 6. Why the win check is O(N) and correct

### 6.1 The naive approach

After every move, scan all N rows, N columns and 2 diagonals — about 2N lines × N cells = **O(N²)** per move.

### 6.2 The key observation

> A move can only complete a line that **passes through the cell just played**.

At most four such lines exist: the row, the column, and (if the cell lies on them) the two diagonals. Each takes ≤ N comparisons → **O(N) per move**, independent of how full the board is.

### 6.3 Why this is *correct*, not just fast (inductive argument)

Claim: *while the game is `IN_PROGRESS`, no complete line of either mark exists on the board.*

- **Base:** an empty board has none.
- **Step:** suppose none exists before a move. After placing a mark at `(r,c)`, any newly complete line must contain `(r,c)` (otherwise it was already complete before). `isWinningMove` checks every line containing `(r,c)`. If none is complete, the claim still holds; if one is, the game becomes terminal and the claim no longer needs to hold.

Because `Game` stops accepting moves at the first win, the claim is never violated, so checking only the new cell's lines is *sufficient*.

### 6.4 Which lines contain a cell

| Line | Contains `(r,c)` when |
|------|-----------------------|
| row `r` | always |
| column `c` | always |
| main diagonal | `r == c` |
| anti-diagonal | `r + c == N − 1` |

On odd boards the centre cell lies on **both** diagonals; the code uses two independent `if` conditions (joined by `||`) so both get checked.

### 6.5 The O(1) alternative (and why it was not used)

Keep counters: `rows[r] += d`, `cols[c] += d`, `diag`, `anti` where `d = +1` for X and `-1` for O; a player wins when a counter reaches `±N`. That makes each move **O(1)**.

| | Line scan (used) | Counters |
|---|---|---|
| Time per move | O(N) | O(1) |
| Extra memory | none | 2N + 2 ints |
| State to keep in sync | only the grid | grid **and** counters |
| Supports undo / other K-in-a-row rules | easy (just scan) | harder |
| Readability | very direct | needs the ± trick explained |

For realistic board sizes the O(N) scan is negligible, and having a single source of truth (the grid) removes a whole class of "counter drifted from board" bugs. Counters are the right answer for a very large N or a performance-focused interview variant.

---

## 7. Cross-cutting decisions

### 7.1 "Validate, then mutate" gives atomicity without rollback

There is no undo code anywhere. Instead the order of operations guarantees that every possible failure happens **before** the first write. This is cheaper and simpler than "mutate, detect problem, roll back".

### 7.2 Exceptions vs return values

| Situation | Mechanism |
|-----------|-----------|
| Player error (bad cell) | `InvalidMoveException` |
| Wrong phase (game over) | `GameOverException` |
| Programmer error (null mark, size ≤ 0, blank name) | `IllegalArgumentException` |
| Normal absence (no winner yet) | `Optional.empty()` |

### 7.3 Concurrency

| Piece | Mechanism |
|-------|-----------|
| `Game` | every public method `synchronized` on the game |
| `Board` | none of its own — protected because only `Game` can reach it |
| `Player`, enums, exceptions | immutable |
| `BoardRenderer` | stateless |

Why this works: tic-tac-toe is turn-based, so concurrent calls are really "two clients racing for the same turn". The lock makes exactly one of them win the turn; the other either gets `InvalidMoveException` (cell taken) or plays the *next* turn as the other player.

### 7.4 Immutability

`Player` (record), `size`, the player references and enums never change. Mutable state is just: cells, `moves`, `currentPlayer`, `status`.

### 7.5 Why split `Board` from `Game` for a game this small?

Two reasons: (1) each class has a single reason to change (grid mechanics vs. match rules), and (2) it makes the encapsulation trick of 3.3 possible. A single class would also work, but you could no longer say "this class knows nothing about turns".

---

## 8. Known flaws and limitations

### 8.1 Rendering is not a consistent snapshot under concurrency

`BoardRenderer` calls `game.getCell` once per cell; each call is individually synchronized, but another thread may play a move between two calls, so the printed board may never have actually existed. Fix: add a synchronized `Game.snapshot()` returning an immutable copy of the grid.

### 8.2 Draws are detected only when the board is full

A game can become unwinnable earlier (every line contains both marks), but play continues until the 9th move. Early-draw detection would need per-line tracking.

### 8.3 Only full-line wins

A win is **N in a row on an N×N board**. On large boards this is very hard; the usual "K in a row" variants (e.g. 3 on 5×5, Gomoku's 5 on 15×15) are not supported.

### 8.4 Other small points

- No undo/redo or move history (the board never clears a cell).
- `getCell` throws `InvalidMoveException` for out-of-bounds *reads*, which is slightly odd naming for a read.
- Two players may have the same name.
- `Mark.opponent()` is unused.
- Very large `size` allocates `size × size` references up-front.
- No observer/event hook to push updates to a UI; callers poll `getStatus()`.
- Player names are not required to be unique, and there is no notion of a "session" or rematch.

---

## 9. How the pieces would change

| New requirement | What changes | What stays |
|-----------------|--------------|------------|
| K-in-a-row (e.g. 3 on 5×5) | `Board.isWinningMove`: count consecutive marks in each of four directions through the new cell | `Game`, exceptions |
| O(1) win detection | add counters to `Board`, update them in `placeMark` | `Game` API |
| Undo | store moves in a stack; add `Board.clearCell`, restore `currentPlayer`/status | win logic |
| Computer opponent | a `PlayerStrategy` interface asked for `(row, col)`; use `Mark.opponent()` for minimax | `Game` |
| GUI / web UI | new view next to `BoardRenderer`; call `Game` getters | domain untouched |
| Push notifications | observer interface (`onMove`, `onGameEnd`) invoked from `playMove` | rules |
| Consistent render under threads | `Game.snapshot()` | `Board` |
| Persistence / replay | log of `(row, col)`; replay through a fresh `Game` | everything (moves are deterministic) |

---

## 10. Self-test

1. Why does a rejected move not switch the turn? Point to the exact ordering that guarantees it. (4.2, 5.5)
2. Why is `Board` package-private? What invariant does that protect? (3.3)
3. After the game ends, who does `getCurrentPlayer()` return, and why? (4.3, 5.5)
4. On a 5×5 board, after a move at `(2,2)`, which four lines are checked? (6.4)
5. Why does the code check *win* before *draw*? Give a concrete board. (4.5)
6. Prove (informally) that checking only the new cell's lines is enough. (6.3)
7. Describe the counter-based O(1) approach and one reason this project did not use it. (6.5)
8. What two exception types exist and how should a UI react to each? (5.6)
9. Two threads call `playMove(0,0)` simultaneously on a fresh board. What are the possible outcomes? (7.3)
10. How would you change `Board.isWinningMove` to support 3-in-a-row on a 5×5 board? (9)
