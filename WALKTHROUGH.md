# LUDO-T — Complete Code Walkthrough

Every class, every method, and the reason each one exists.

This is the document to read if you have to **explain or defend the code**. It follows the program in
the order things actually happen, quotes the real source, and after every chunk answers two
questions: *what does this do?* and *why is it written this way and not some other way?*

**Companion documents**

| File | What it is for |
|---|---|
| `README.md` | how to build and run, and where to look first |
| `REPORT.md` | the assignment's required report: structures, justification, SOLID, efficiency |
| `WALKTHROUGH.md` | **this file** — the line-by-line explanation |

---

## Table of contents

- [0. How to use this document](#0-how-to-use-this-document)
- [1. The ten-minute mental model](#1-the-ten-minute-mental-model)
- [2. Reading the board off Figure 1](#2-reading-the-board-off-figure-1)
- [3. Package `ludot.board`](#3-package-ludotboard)
- [4. Package `ludot.effects`](#4-package-ludoteffects)
- [5. Package `ludot.random`](#5-package-ludotrandom)
- [6. Package `ludot.movement`](#6-package-ludotmovement)
- [7. Package `ludot.mystery`](#7-package-ludotmystery)
- [8. Package `ludot.player`](#8-package-ludotplayer)
- [9. Package `ludot.game`](#9-package-ludotgame)
- [10. Package `ludot.ui`](#10-package-ludotui)
- [11. Wiring it all together](#11-wiring-it-all-together)
- [12. Five worked traces from real games](#12-five-worked-traces-from-real-games)
- [13. The test suite](#13-the-test-suite)
- [14. Viva preparation](#14-viva-preparation)

---

## 0. How to use this document

Read **section 1** and **section 2** first — they are short, and nothing else makes sense without
them. After that the sections follow the packages from the inside out: the board and the pieces are
plain data, movement is the rule engine, the players are the decisions, and the game package is the
loop that drives everything.

Throughout, a box like this one flags the interesting part:

> **Why this way?** The answer to "couldn't you have just…", which is the question you will actually
> be asked.

Code is quoted **exactly** as it appears in `src/` — this was checked mechanically, line by line,
against the source. Where a method is long it is broken into chunks with the explanation between them,
and a lone `...` marks a passage skipped as uninteresting (a field assignment, a plain getter).

### The 37 types at a glance

```
src/
  Main.java                       the entry point: reads the optional seed, starts the game
  ludot/
    LudoTSimulation.java          the composition root: builds every object once

    board/                        WHERE things are - plain geometry and occupancy
      PieceColour.java            the 4 colours; each knows its own X and approach cell
      Direction.java              clockwise / counter-clockwise, and what each implies
      BoardGeometry.java          the board's fixed numbers (52, 5, 4, Alpha/Beta/Gamma)
      Square.java                 an immutable "where is this?" value; 80 shared instances
      Board.java                  the occupancy index and every block question
      Piece.java                  identity, position, direction, captures, approach passes

    effects/                      WHAT a mystery teleport leaves behind on a piece
      PieceEffects.java           the four-round timers of Rules T-12 / T-13, and T-13's escape count
      SpeedModifier.java          NORMAL / DOUBLED / HALVED, each with its own arithmetic

    random/                       CHANCE, behind one interface
      RandomSource.java           the abstraction everything random depends on
      SeededRandomSource.java     the real one, backed by java.util.Random
      Dice.java                   1..6
      Coin.java                   heads/tails -> a Direction (Rule T-1)

    movement/                     THE RULE ENGINE
      PathResolver.java           walks the board one cell at a time; all geometry rules
      MoveGenerator.java          "what is legal?" -> MoveOptions
      MoveExecutor.java           "make it happen" -> changes the board
      PlannedMove.java            record: one fully-checked, not-yet-applied move
      PieceMovement.java          record: where one piece inside that move would end up
      BlockedAttempt.java         record: a move Rule T-3 refused, plus its optional fall-back
      MoveOptions.java            record: the playable moves and the refused ones
      MoveKind.java               ENTER_BOARD / ADVANCE / BLOCK_ADVANCE / PARTIAL_ADVANCE

    mystery/                      THE TWIST
      MysteryCell.java            the wandering cell's life cycle (Rule T-10)
      MysteryEffectResolver.java  what a teleport does (Rules T-11..T-15)
      TeleportDestination.java    the 6 destinations, each knowing where it is

    player/                       THE DECISIONS
      Player.java                 abstract base: the invariants plus shared helpers
      RedPlayer.java              aggressive
      GreenPlayer.java            blocker
      YellowPlayer.java           racer
      BluePlayer.java             cyclic mystery-chaser
      PlayerFactory.java          colour -> behaviour, in one place

    game/                         THE LOOP
      LudoGame.java               rounds, turn order, end-of-round report, placings, gridlock
      TurnEngine.java             one turn, including every extra roll
      FirstPlayerSelector.java    the opening roll-off
      GameRules.java              the numeric rules and the documented interpretations

    ui/
      GameListener.java           interface: every event a game can raise (Observer)
      GameLog.java                the listener that prints them, one method per message
test/                             224 JUnit 5 tests in 21 classes, mirroring the packages above
  MainTest.java
  ludot/Fixtures.java             shared set-up: place / placeOn / piece / fixedRandom
  ludot/board/  ludot/effects/  ludot/movement/  ludot/mystery/
  ludot/player/ ludot/game/     ludot/ui/        ludot/random/
pom.xml                           Maven build: JUnit 5, Mockito, JaCoCo coverage
```

---

## 1. The ten-minute mental model

### 1.1 One turn, end to end

This is the single most important picture in the program. Follow one dice roll through it:

```
LudoGame.play()
  └─ for each round 1..N
       └─ LudoGame.playRound()
            └─ for each colour in turn order
                 └─ TurnEngine.playTurn(player)          <-- one player's whole turn
                      │
                      ├─ dice.roll()                     "red player rolled 4."
                      ├─ releaseBriefedPieces...(4)      Rule T-13: each briefed piece sees the roll
                      │
                      └─ TurnEngine.playSingleRoll()
                           │
                           │   ┌──────────── PHASE 1: WHAT IS LEGAL? ────────────┐
                           ├──>│ MoveGenerator.optionsFor(RED, 4)                │
                           │   │   ├─ addEnterBoardMove   (only if the roll is 6)│
                           │   │   ├─ addSinglePieceMoves  ─┐                    │
                           │   │   └─ addBlockMoves        ─┤ each asks           │
                           │   │                            └> PathResolver.walk │
                           │   └─ returns MoveOptions {playable, blocked}         │
                           │   └──────────────────────────────────────────────────┘
                           │
                           │   ┌──────────── PHASE 2: WHICH IS BEST? ────────────┐
                           ├──>│ player.chooseMove(options, 4)                    │
                           │   │   └─ RedPlayer.selectMove(...) -> Optional<PlannedMove>
                           │   └──────────────────────────────────────────────────┘
                           │
                           │   ┌──────────── PHASE 3: MAKE IT HAPPEN ────────────┐
                           └──>│ MoveExecutor.execute(chosenMove)                 │
                               │   ├─ board.relocate(...)      the piece moves     │
                               │   ├─ applyCaptures(...)       Rules 6, T-8, T-9   │
                               │   └─ mystery teleport?        Rules T-10, T-11    │
                               │   └─ returns "did it capture?"  -> Rule T-2 bonus │
                               └──────────────────────────────────────────────────┘
```

### 1.2 The one design rule that explains everything

> **The three phases are three different classes, and they are strictly ordered.**
>
> `MoveGenerator` decides **what is legal** and changes nothing.
> The `Player` decides **which legal move to play** and changes nothing.
> `MoveExecutor` is the **only** class allowed to change the board.

Almost every "why is it like that?" question in this codebase has the same answer: *because of that
rule*. Some consequences worth naming out loud:

1. **A player cannot cheat.** `selectMove` receives a `List<PlannedMove>` and must return one of its
   elements. It has no access to the board's mutators, so a behaviour bug can never become a rules
   bug.
2. **A strategy can compare freely.** Because a `PlannedMove` is inert data, red can ask "does this
   one capture?" and green can ask "does this one form a block?" and then *throw the move away*. If
   moves were applied as they were considered, every rejected option would need undoing.
3. **A rule is implemented once.** Rule T-3 (blocks) lives inside `PathResolver.blockerAt`, which
   judges every step of every walk; the only other place that asks about an opponent block is
   `MoveGenerator.opponentBlockerOn`, for the one square a piece reaches without walking — its `X`.
   Nothing outside `ludot.movement` knows what a block is allowed to do.
4. **The three phases are independently testable.** `MoveGeneratorTest` calls `MoveGenerator`
   directly and inspects the returned `MoveOptions` without ever running a game; the player tests
   hand a list of moves to `chooseMove`; `MoveExecutorTest` applies one move and looks at the board.

Every phase also *reports* what it did, but none of them prints anything. They raise events on a
`GameListener` — "a piece moved", "a piece was captured" — and the one implementation that turns
events into text, `GameLog`, is plugged in by `LudoTSimulation` (section 10).

### 1.3 Who is allowed to change what

Mutable state in this program is deliberately tiny:

| State | Who may change it | How |
|---|---|---|
| a piece's position | `Board` only — the compiler enforces it | `Board.relocate(piece, square)`; `Piece.setSquare` is package-private |
| a piece's direction, captures, approach passes | `MoveExecutor`, `MysteryEffectResolver` | after a move / a teleport |
| a piece's Alpha / Beta timers and its T-13 escape count | `PieceEffects` | `applyAlphaAura`, `beginBriefing`, `observeRoll`, `onRoundCompleted` |
| the mystery cell's position | `MysteryCell` only | `onRoundCompleted()` |
| blue's cycle | `BluePlayer` only | `onMoveExecuted(move)` notes the first piece moved; `onRoundCompleted()` advances |

Everything else — `Square`, `PathResolver.Walk` and the four records `PlannedMove`, `PieceMovement`,
`MoveOptions`, `BlockedAttempt` — is **immutable**. That is why the program has no "who moved my
piece?" class of bug.

---

## 2. Reading the board off Figure 1

The specification never prints a numbered board. It gives a picture and one sentence, and everything
else has to be derived. Getting this wrong would break every rule at once, so it is worth doing
slowly.

### 2.1 The sentence that fixes everything

> "When considering the square ID, each white square and the coloured square has to be numbered. All
> squares on the white path, including the starting squares. **The numbering starts with the Yellow
> starting square and continues clockwise on the white path. The numbering starts with zero (0) and
> ends at 51.**" — Legend

So: cell `0` is the yellow `X`, and the numbers increase clockwise up to `51`.

### 2.2 The board as a 15 × 15 grid

Figure 1 is the standard Ludo layout. Written as a grid (row, column), with `.` for a white path
cell, `#` for a base, and the centre marked `HOME`:

```
        col 0  1  2  3  4  5   6  7  8   9 10 11 12 13 14
 row  0  #  #  #  #  #  #   .  ○  .   #  #  #  #  #  #      ○ = yellow approach (cell 50)
 row  1  #  #  #  #  #  #   .  y  X   #  #  #  #  #  #      X = yellow start     (cell 0)
 row  2  #  # GREEN #  #    .  y  .   #  # YELLOW #  #      y = yellow home straight
 row  3  #  #  BASE  #  #   .  y  .   #  #  BASE  #  #
 row  4  #  #  #  #  #  #   .  y  .   #  #  #  #  #  #
 row  5  #  #  #  #  #  #   .  y  .   #  #  #  #  #  #
        ─────────────────────────────────────────────────
 row  6  .  X  .  .  .  .   \        /   .  .  .  .  .  .   green X = cell 39
 row  7  ○  g  g  g  g  g    \ HOME /    b  b  b  b  b  ○   green ○ = cell 37, blue ○ = cell 11
 row  8  .  .  .  .  .  .   /        \   .  .  .  .  X  .   blue X = cell 13
        ─────────────────────────────────────────────────
 row  9  #  #  #  #  #  #   .  r  .   #  #  #  #  #  #      r = red home straight
 row 10  #  #  #  #  #  #   .  r  .   #  #  #  #  #  #
 row 11  #  #  RED   #  #   .  r  .   #  #  BLUE  #  #
 row 12  #  #  BASE  #  #   .  r  .   #  #  BASE  #  #
 row 13  #  #  #  #  #  #   X  r  .   #  #  #  #  #  #      red X = cell 26
 row 14  #  #  #  #  #  #   .  ○  .   #  #  #  #  #  #      red ○ = cell 24
```

Counting the white cells: each of the four arms contributes 6 + 1 + 6 = **13** cells, and 4 × 13 =
**52**. That matches "There are 52 standard … cells" exactly, which is the first confirmation that
the reading of the figure is right.

### 2.3 Walking the ring to get the numbers

Start at the yellow `X` = (row 1, col 8) = cell **0**. Yellow's approach circle is at (row 0, col 7),
one step *back* along the column, so yellow must set off in the other direction — down column 8 —
otherwise it would arrive home after two steps. Walking clockwise from there:

| Cells | Grid squares | Landmark |
|---|---|---|
| 0–4 | (1,8) … (5,8) | **cell 0 = yellow X** |
| 5–10 | (6,9) … (6,14) | |
| 11 | (7,14) | **blue approach** |
| 12–17 | (8,14) … (8,9) | **cell 13 = blue X** |
| 18–23 | (9,8) … (14,8) | |
| 24 | (14,7) | **red approach** |
| 25–30 | (14,6) … (9,6) | **cell 26 = red X** |
| 31–36 | (8,5) … (8,0) | |
| 37 | (7,0) | **green approach** |
| 38–43 | (6,0) … (6,5) | **cell 39 = green X** |
| 44–49 | (5,6) … (0,6) | |
| 50 | (0,7) | **yellow approach** |
| 51 | (0,8) | back to cell 0 |

### 2.4 The four numbers that fall out

| Colour | Start `X` | Approach | Start → approach |
|--------|-----------|----------|------------------|
| Yellow | 0 | 50 | 50 cells |
| Blue | 13 | 11 | 50 cells |
| Red | 26 | 24 | 50 cells |
| Green | 39 | 37 | 50 cells |

Two patterns make this trustworthy:

- the four starts are exactly **13 apart** (0, 13, 26, 39) — one per arm;
- every approach cell is exactly **50 cells in front of** its own start (equivalently, 2 cells
  behind it).

That second fact is the one the code stores, because it is the one movement needs:

```
clockwise journey = 50 (X to approach) + 5 (home straight) + 1 (into Home) = 56 cells
```

### 2.5 Alpha, Beta and Gamma

> "Alpha, Beta, and Gamma is the 9th, 27th, and 46th cell respectively from the yellow approach cell.
> The cell identified in the yellow approach cell is considered zero (0)." — Rule T-11

Yellow's approach cell is 50, and counting it as zero:

```
Alpha = (50 +  9) mod 52 =  59 mod 52 =  7
Beta  = (50 + 27) mod 52 =  77 mod 52 = 25
Gamma = (50 + 46) mod 52 =  96 mod 52 = 44
```

### 2.6 Turn order

> "the dice is passed to the player to the left. For example, if R rolled the dice, the next player to
> roll would be G." — Section 1.1

Red is 26 and green is 39, so "the player to the left" is **+13 in the numbering**. Applying that
repeatedly gives the cycle:

```
yellow(0) -> blue(13) -> red(26) -> green(39) -> yellow(0) -> ...
```

> **Why this matters.** The specification's only concrete example is `R -> G`. Any turn order that
> gets that wrong is wrong. Because the code derives the order from the same start-cell numbers it
> uses for movement, the two can never disagree — and `GeometryTest` asserts `R -> G` directly.

### 2.7 Everything derived, in one place

All of the above is stored as **four numbers** — one start cell per colour. Every other landmark is
computed from them, so there is no table of magic constants to keep in sync:

```
approach(colour) = (start(colour) + 50) mod 52
Alpha / Beta / Gamma = (approach(YELLOW) + 9 / 27 / 46) mod 52
turn order = increasing start cell
```

---

## 3. Package `ludot.board`

This package answers exactly one question: **where is everything?** — the fixed geometry, the
squares, the sixteen pieces and the index of who stands where. It contains no rules about *movement*
— those live in `ludot.movement`. Keeping the two apart is why the geometry can be checked
independently of the rules.

`Piece` lives here too, next to `Board`, for one concrete reason: the two share a single fact (where
a piece stands), and putting them in the same package lets `Board` be the *only* class allowed to
change it — see `relocate` in section 3.5.

### 3.1 `PieceColour` — four colours that know their own landmarks

```java
public enum PieceColour {

    YELLOW("yellow", 'Y', 0),
    BLUE("blue", 'B', 13),
    RED("red", 'R', 26),
    GREEN("green", 'G', 39);
```

Four constants, each given three things: the lower-case name the status messages use, the letter that
names its pieces (`R1`…`R4`), and **its start cell** — the four numbers derived in section 2.

> **Why is the declaration order yellow, blue, red, green and not R, G, Y, B?**
> Because the declaration order *is* the turn order. Section 2.6 showed that "the player to the left"
> means +13 in the cell numbering, so listing the colours by increasing start cell makes
> `nextInTurnOrder()` a one-line `ordinal() + 1`. Any other order would need a separate lookup table
> that could drift out of step with the geometry.

```java
    /** Cells walked from the starting square until the piece stands on its own approach cell. */
    private static final int CELLS_FROM_START_TO_APPROACH = 50;

    private final String displayName;
    private final char initial;
    private final int startCell;

    PieceColour(String displayName, char initial, int startCell) {
        this.displayName = displayName;
        this.initial = initial;
        this.startCell = startCell;
    }
```

A named constant instead of a bare `50`. The name states the fact it encodes, so nobody has to
rediscover section 2 to understand the arithmetic below. The constructor is the standard "enum with
fields" pattern; the fields are `final`, so a colour is immutable.

```java
    /** The "X" square this colour enters from its base (Rule 2). */
    public int startCell() {
        return startCell;
    }

    /** The "Approach" circle of this colour; the doorway to its home straight (Rule 9). */
    public int approachCell() {
        return BoardGeometry.wrapRing(startCell + CELLS_FROM_START_TO_APPROACH);
    }
```

`approachCell()` is **computed, not stored**. For yellow: `(0 + 50) mod 52 = 50`. For blue:
`(13 + 50) mod 52 = 63 mod 52 = 11`. For red: `76 mod 52 = 24`. For green: `89 mod 52 = 37`.

> **Why compute it instead of storing a second number per colour?**
> Two numbers per colour can contradict each other; one number cannot. Storing `startCell` and
> `approachCell` separately would let a typo produce a board where green's approach is nowhere near
> green's home straight, and nothing would catch it. Deriving the second from the first makes that
> class of bug unrepresentable.

```java
    /** The next colour to roll, i.e. the player "to the left". */
    public PieceColour nextInTurnOrder() {
        return values()[(ordinal() + 1) % values().length];
    }
}
```

`ordinal()` is the position in the declaration list (yellow = 0 … green = 3). Adding one and wrapping
with `% 4` walks the cycle: green (3) → `(3+1) % 4 = 0` → yellow. `values().length` rather than a
literal `4` so the expression stays correct on its own terms.

### 3.2 `Direction` — and the rule hidden inside it

```java
public enum Direction {

    CLOCKWISE("clockwise", +1, 1),
    COUNTER_CLOCKWISE("counter-clockwise", -1, 2);
```

Each direction carries three things: the wording the messages need, the **step** it takes along the
ring (`+1` or `-1`), and — the interesting one — **how many times a piece must reach its approach
cell before it may turn into its home straight**.

That third number is Rule T-1's second half:

> "When moving counterclockwise, a piece can only move into the home straight **if it passes the
> approach cell for the second time**." — Rule T-1

A clockwise piece needs **1** visit; a counter-clockwise piece needs **2**.

> **Why put that number on the enum instead of writing `if (direction == COUNTER_CLOCKWISE)` in the
> movement code?**
> Because it would not be one `if` — it would be one in `walk`, one in `destinationIgnoringBlocks`,
> and one in `distanceToHome`, and they would have to agree forever. Storing it as data means the
> movement code contains a single comparison, `approachPasses >= direction.requiredApproachPasses()`,
> which reads as the rule itself and cannot fall out of step with a second copy.

```java
    /** The standard-path cell reached by taking one single step from {@code cell}. */
    public int nextRingCell(int cell) {
        return BoardGeometry.wrapRing(cell + ringStep);
    }
```

The whole of "which way round does this piece go?" collapses into this one method. Clockwise from 51:
`wrapRing(52) = 0`. Counter-clockwise from 0: `wrapRing(-1) = 51`. Both wrap-arounds are handled by
`wrapRing`, so no caller ever writes `% 52` again.

### 3.3 `BoardGeometry` — the board's fixed numbers, once each

```java
public final class BoardGeometry {

    /** Number of cells on the shared standard path (Section 1.1: "52 standard ... cells"). */
    public static final int RING_SIZE = 52;

    /** Cells in one colour's home straight, named {@code [colour]homepath0} .. {@code 4}. */
    public static final int HOME_STRAIGHT_LENGTH = 5;

    /** Steps needed to walk from the approach cell all the way into "Home". */
    public static final int STEPS_FROM_APPROACH_TO_HOME = HOME_STRAIGHT_LENGTH + 1;

    /** Pieces every player owns (Section 1.1: "four pieces named 1 to 4"). */
    public static final int PIECES_PER_PLAYER = 4;
```

Every constant names the sentence of the specification that produced it. `STEPS_FROM_APPROACH_TO_HOME`
is `5 + 1` — five home-straight cells plus the final step into Home — and it is *derived* from
`HOME_STRAIGHT_LENGTH` rather than written as `6`, so the two cannot disagree.

```java
    private static final int ALPHA_OFFSET_FROM_YELLOW_APPROACH = 9;
    private static final int BETA_OFFSET_FROM_YELLOW_APPROACH = 27;
    private static final int GAMMA_OFFSET_FROM_YELLOW_APPROACH = 46;

    /** Cell 7 - the "aura" cell of Rule T-12. */
    public static final int ALPHA_CELL = offsetFromYellowApproach(ALPHA_OFFSET_FROM_YELLOW_APPROACH);

    /** Cell 25 - the "briefing" cell of Rule T-13. */
    public static final int BETA_CELL = offsetFromYellowApproach(BETA_OFFSET_FROM_YELLOW_APPROACH);

    /** Cell 44 - the "clarification" cell of Rule T-14. */
    public static final int GAMMA_CELL = offsetFromYellowApproach(GAMMA_OFFSET_FROM_YELLOW_APPROACH);
```

The offsets 9, 27 and 46 are quoted straight from Rule T-11; the resulting cells 7, 25 and 44 are
**computed**, not typed in.

> **Why not just write `ALPHA_CELL = 7`?**
> Because `7` is an answer without a question. Written this way, a marker can read the code beside
> Rule T-11 and see the rule being applied. And if the board numbering were ever re-based — say
> someone decided cell 0 should be red's `X` — Alpha, Beta and Gamma would move with it automatically.

```java
    private BoardGeometry() {
        // Utility class: never instantiated.
    }

    /** Maps any integer onto a valid standard-path cell index, wrapping around 0..51. */
    public static int wrapRing(int cell) {
        return ((cell % RING_SIZE) + RING_SIZE) % RING_SIZE;
    }
```

The private constructor stops anyone writing `new BoardGeometry()`, which would be meaningless — the
class is a bag of constants.

`wrapRing` is doubled up for a reason. In Java, `-1 % 52` is `-1`, **not** `51` — the sign of the
remainder follows the dividend. A single `%` would therefore produce negative cell indices the moment
a counter-clockwise piece stepped past cell 0, and `Square.ring(-1)` would throw. Adding `RING_SIZE`
and taking the remainder again forces the result into `0..51` for any input:

```
wrapRing(-1)  =  ((-1 % 52) + 52) % 52  =  ((-1) + 52) % 52  =  51 % 52  =  51   ✓
wrapRing(52)  =  ((52 % 52) + 52) % 52  =  (0 + 52) % 52      =  0               ✓
wrapRing(-53) =  ((-53 % 52) + 52) % 52 =  ((-1) + 52) % 52    =  51             ✓
```

```java
    private static int offsetFromYellowApproach(int offset) {
        return wrapRing(PieceColour.YELLOW.approachCell() + offset);
    }
}
```

> **Is there a circular-initialisation problem here?** It looks like one:
> `BoardGeometry.ALPHA_CELL` calls `PieceColour.YELLOW.approachCell()`, which calls
> `BoardGeometry.wrapRing(...)` — back into a class that is still initialising. It is safe, and worth
> knowing why. `RING_SIZE` is a `static final int` with a constant initialiser, so the Java compiler
> **inlines** it at every use site; `wrapRing` therefore reads no static field at run time, and
> calling a static *method* of a partly-initialised class is legal. Nothing in `PieceColour` touches a
> non-constant static of `BoardGeometry`, so the cycle never closes.

### 3.4 `Square` — an immutable "where is this?"

This is the class that keeps the rest of the program honest, so it earns a long explanation.

#### The problem it solves

A LUDO-T board is **not** one flat list of cells. There are four different kinds of place:

| Kind | How many | Shared? |
|---|---|---|
| standard path cell | 52 | shared by everybody |
| home-straight cell | 5 per colour | one colour only |
| base | 1 per colour | one colour only |
| home | 1 per colour | one colour only |

The tempting shortcut is one integer with hidden ranges — `0..51` = ring, `52..56` = home straight,
`57` = home, `-1` = base. That is where off-by-one bugs live: nothing stops you comparing a yellow
`54` with a green `54`, or asking for the "next cell" after `57`.

#### The declaration

```java
public final class Square {

    /** The four kinds of place a piece can occupy. */
    public enum Kind {
        /** One of the 52 shared standard cells. */
        RING,
        /** One of the five colour-specific cells between the approach cell and home. */
        HOME_STRAIGHT,
        /** The player's base, where pieces wait to roll a six. */
        BASE,
        /** The player's home; a piece that reaches it leaves the game (Section 1.1). */
        HOME
    }

    private final Kind kind;
    /** Owning colour for BASE / HOME_STRAIGHT / HOME; {@code null} for the shared ring. */
    private final PieceColour owner;
    /** 0..51 on the ring, 0..4 in a home straight, and 0 for BASE / HOME. */
    private final int index;
```

Three fields, all `final`: *what kind of place*, *whose*, and *which one*. `owner` is `null` for ring
cells because the ring genuinely has no owner — that is a fact about the board, not a missing value.

`final class` + all-`final` fields = **immutable**. A `Square` can be stored in a field, put in a map
key, returned from a method and logged with no possibility that some other part of the program
mutates it behind your back.

#### The 80 shared instances (Flyweight)

```java
    /*
     * A LUDO-T board contains exactly 80 distinct squares: the 52 shared standard cells, plus a base,
     * a five-cell home straight and a home for each of the four colours (52 + 4 x 7). Since a Square is immutable,
     * every one of them can be created once, up front, and shared by everybody who refers to it -
     * the Flyweight pattern. Walking a path then costs no object allocation at all, and identical
     * squares are also identical objects, which makes comparing them as cheap as it can be.
     */
    private static final Square[] RING_SQUARES = new Square[BoardGeometry.RING_SIZE];
    private static final Map<PieceColour, Square[]> HOME_STRAIGHT_SQUARES =
            new EnumMap<>(PieceColour.class);
    private static final Map<PieceColour, Square> BASE_SQUARES = new EnumMap<>(PieceColour.class);
    private static final Map<PieceColour, Square> HOME_SQUARES = new EnumMap<>(PieceColour.class);

    static {
        for (int cell = 0; cell < BoardGeometry.RING_SIZE; cell++) {
            RING_SQUARES[cell] = new Square(Kind.RING, null, cell);
        }
        for (PieceColour colour : PieceColour.values()) {
            Square[] homeStraight = new Square[BoardGeometry.HOME_STRAIGHT_LENGTH];
            for (int cell = 0; cell < BoardGeometry.HOME_STRAIGHT_LENGTH; cell++) {
                homeStraight[cell] = new Square(Kind.HOME_STRAIGHT, colour, cell);
            }
            HOME_STRAIGHT_SQUARES.put(colour, homeStraight);
            BASE_SQUARES.put(colour, new Square(Kind.BASE, colour, 0));
            HOME_SQUARES.put(colour, new Square(Kind.HOME, colour, 0));
        }
    }
```

The `static { … }` block runs once, the first time the class is touched, and builds all 80 squares:
52 ring cells, plus for each of the four colours a home straight of 5, a base and a home —
52 + 4 × (5 + 1 + 1) = 52 + 28 = **80**.

`EnumMap` rather than `HashMap` because the keys are enum constants: an `EnumMap` is internally just
an array indexed by `ordinal()`, so lookups are array accesses with no hashing at all.

> **What does the Flyweight actually buy here?**
> `PathResolver.walk` takes up to 12 steps, and each step asks for the next square. Without sharing,
> a single game (~214 rounds, thousands of candidate moves) would allocate hundreds of thousands of
> throwaway `Square` objects. With sharing it allocates **none**. As a bonus, `Square.ring(7)` returns
> *the same object* every time, so `equals` hits its `this == other` fast path immediately.
>
> This is only safe *because* `Square` is immutable. If a `Square` had a setter, sharing one instance
> between two pieces would be a disaster. Immutability is what makes the optimisation legal.

#### Construction

```java
    private Square(Kind kind, PieceColour owner, int index) {
        this.kind = kind;
        this.owner = owner;
        this.index = index;
    }

    /** One of the 52 shared standard cells, numbered as in the Legend of the specification. */
    public static Square ring(int cell) {
        if (cell < 0 || cell >= BoardGeometry.RING_SIZE) {
            throw new IllegalArgumentException("Standard-path cell out of range: " + cell);
        }
        return RING_SQUARES[cell];
    }

    /** One of a colour's five home-straight cells, {@code [colour]homepath0} .. {@code 4}. */
    public static Square homeStraight(PieceColour owner, int cell) {
        if (cell < 0 || cell >= BoardGeometry.HOME_STRAIGHT_LENGTH) {
            throw new IllegalArgumentException("Home-straight cell out of range: " + cell);
        }
        return HOME_STRAIGHT_SQUARES.get(owner)[cell];
    }

    public static Square base(PieceColour owner) {
        return BASE_SQUARES.get(owner);
    }

    public static Square home(PieceColour owner) {
        return HOME_SQUARES.get(owner);
    }
```

The constructor is **private**; the only way in is through the four named factories. That is what
guarantees nobody can create a 73rd square, and it makes the call sites read like English:
`Square.ring(37)`, `Square.homeStraight(GREEN, 2)`, `Square.base(RED)`.

The range checks turn a programming mistake into an immediate, named exception instead of an
`ArrayIndexOutOfBoundsException` five frames away. In a correct run they never fire — they are there
so that if the movement code ever computed a nonsense cell, the failure would point straight at it.

#### Queries

```java
    /** Cell index inside this square's kind: 0..51 on the ring, 0..4 in a home straight. */
    public int index() {
        return index;
    }

    public boolean isRing() {
        return kind == Kind.RING;
    }

    public boolean isHomeStraight() {
        return kind == Kind.HOME_STRAIGHT;
    }

    public boolean isBase() {
        return kind == Kind.BASE;
    }

    public boolean isHome() {
        return kind == Kind.HOME;
    }
```

One predicate per kind. Callers write `if (current.isHomeStraight())` rather than
`if (current.kind() == Square.Kind.HOME_STRAIGHT)`, which is why there is no public `kind()` getter —
nothing needs it, so it is not there.

```java
    /** True when this is the given colour's approach circle, the doorway to its home straight. */
    public boolean isApproachCellOf(PieceColour colour) {
        return isRing() && index == colour.approachCell();
    }
```

The single most-used question in the movement engine, and note the `isRing() &&` guard: without it,
`greenhomepath2` (index 2) would be compared against green's approach cell (37) — harmless here, but
`yellowhomepath0` (index 0) would compare equal to *blue's* start-adjacent numbering in a differently
laid-out board. Checking the kind first means the index is only ever interpreted in the right
namespace. **This is exactly the bug the `Square` class exists to prevent.**

#### Printing

```java
    /**
     * The square identifier used by every status message.
     *
     * <p>Per the Legend: standard cells are printed as their number 0..51, and home-straight cells
     * are printed as {@code [colour]homepath[cell number]} starting at zero.
     */
    public String label() {
        return switch (kind) {
            case RING -> Integer.toString(index);
            case HOME_STRAIGHT -> owner.displayName() + "homepath" + index;
            case BASE -> "Base";
            case HOME -> "Home";
        };
    }
```

The required output format lives *on the value itself*, so `GameLog` never has to reconstruct a label
from a number and a colour. This produces `"37"`, `"greenhomepath2"`, `"Base"`, `"Home"` — exactly the
Legend's `[colour]homepath[cell number]`.

This is a **switch expression** (Java 14+): it returns a value, and because `Kind` is an enum with all
four cases covered it needs no `default`. If a fifth kind were ever added the compiler would reject
this method until it was handled — a `default` branch would silently return the wrong thing instead.

#### Value equality

```java
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Square that)) {
            return false;
        }
        return kind == that.kind && owner == that.owner && index == that.index;
    }
```

`this == other` first, which thanks to the Flyweight is the case that almost always fires.
`instanceof Square that` is a **pattern match** (Java 16+): it tests the type and declares the
cast variable in one step, and correctly returns `false` for `null`. Then all three fields are
compared — `kind` and `owner` with `==` because enums are singletons.

```java
    /**
     * Required because {@code Board} uses squares as hash-map keys: two squares that are
     * {@code equals} must return the same hash. It is never called by this program's own code - the
     * JDK's {@code HashMap} calls it.
     */
    @Override
    public int hashCode() {
        return Objects.hash(kind, owner, index);
    }

    @Override
    public String toString() {
        return label();
    }
}
```

`hashCode` is not decoration: `Board` keys a `LinkedHashMap` by `Square`, and the map contract says
equal keys must hash equally. Override `equals` without `hashCode` and the board would silently lose
pieces — you would put a piece on `Square.ring(7)` and fail to find it there. `toString` delegating to
`label()` means a `Square` prints usefully in a debugger and in assertion failures.

### 3.5 `Board` — the occupancy index

```java
public final class Board {

    /** Two or more pieces of the same player on one cell form a "block" (Rule T-3). */
    public static final int MINIMUM_BLOCK_SIZE = 2;

    private static final Comparator<Piece> BY_PIECE_NUMBER =
            Comparator.comparingInt(Piece::number);

    private final Map<PieceColour, List<Piece>> piecesByColour = new EnumMap<>(PieceColour.class);
    private final Map<Square, List<Piece>> occupants = new LinkedHashMap<>();
```

Two maps, and they hold the same sixteen pieces viewed two different ways:

- `piecesByColour` — "give me red's four pieces" (used to iterate a player's pieces);
- `occupants` — "give me whatever is standing on cell 37" (used by every block and capture check).

`MINIMUM_BLOCK_SIZE = 2` is the definition of a block, named once. `BY_PIECE_NUMBER` is a shared
comparator so that groups always come back in `R1, R2, R3, R4` order — see `groupOn` below for why
that matters.

> **Why an index at all — why not scan all 16 pieces?**
> Because occupancy is the hottest question in the program: every step of every candidate move asks
> it. A scan is O(16) per step; the map is O(1). More importantly the map has **no special cases** —
> the same structure holds ring cells, home straights, bases and homes, because a `Square` is a
> `Square`. A scan-based version would need four different code paths.

#### Construction

```java
    /** Builds a board with four pieces per colour, all of them sitting in their base. */
    public Board() {
        for (PieceColour colour : PieceColour.values()) {
            List<Piece> pieces = new ArrayList<>();
            for (int number = 1; number <= BoardGeometry.PIECES_PER_PLAYER; number++) {
                Piece piece = new Piece(colour, number);
                pieces.add(piece);
                occupantsAt(piece.square()).add(piece);
            }
            piecesByColour.put(colour, Collections.unmodifiableList(pieces));
        }
    }
```

Sixteen pieces, numbered from **1** (so the names read `R1`…`R4`, not `R0`…`R3`). Each new `Piece`
starts in its own base — see `Piece`'s constructor — and is immediately registered in `occupants`, so
the index is complete from the very first line of the game.

`Collections.unmodifiableList` is the important detail: `piecesOf(RED)` hands out a **read-only** view.
A player behaviour can iterate red's pieces but cannot add or remove one.

#### The single mutation point

```java
    /** Moves one piece and keeps the occupancy index in step with it. */
    public void relocate(Piece piece, Square destination) {
        occupantsAt(piece.square()).remove(piece);
        piece.setSquare(destination);
        occupantsAt(destination).add(piece);
    }
```

Three lines, and the most important method on the class. Remove from the old cell's list, update the
piece, add to the new cell's list.

> **Why does this matter so much?**
> The same fact — "R1 is on cell 30" — is stored in *two* places: in `R1.square` and in the
> `occupants` entry for cell 30. Two representations of one fact can only stay consistent if exactly
> one method changes both. `relocate` is that method. Every move, every capture, every teleport and
> every briefing recall goes through it, which is why the board can never end up with a piece that is
> on cell 30 according to itself and on cell 12 according to the index.
>
> This is also why `Piece.setSquare` is **package-private** (no `public`): it is the second half of
> `relocate`, not a public API. `Piece` and `Board` are both in `ludot.board`, so `relocate` can call
> it, while `MoveExecutor`, `MysteryEffectResolver` and every player — all in other packages —
> cannot. The single-mutation-point rule is therefore enforced by the compiler, not by a comment.

#### Grouping — the shape the block rules need

```java
    public Map<PieceColour, List<Piece>> groupsOn(Square square) {
        Map<PieceColour, List<Piece>> groups = new LinkedHashMap<>();
        for (Piece piece : occupantsAt(square)) {
            groups.computeIfAbsent(piece.colour(), colour -> new ArrayList<>()).add(piece);
        }
        groups.values().forEach(group -> group.sort(BY_PIECE_NUMBER));
        return groups;
    }
```

Takes the flat list of occupants and buckets it by colour. `computeIfAbsent` creates the bucket the
first time a colour appears, then appends.

The reason this shape is exactly right is that **every** block rule is a statement about the size of
a same-colour group on one cell:

| Group size | What the rules say |
|---|---|
| 0 | nothing there |
| 1 | a lone piece: jumped over (Rule 5) or captured (Rule 6) |
| 2 or more | a **block**: cannot be jumped over (Rule T-3), captured only by an equal blockade (Rule T-8) |

So `groupsOn(square)` reduces "what am I allowed to do here?" to "how big is each opponent's group?".

```java
    public List<Piece> groupOn(Square square, PieceColour colour) {
        List<Piece> group = new ArrayList<>();
        for (Piece piece : occupantsAt(square)) {
            if (piece.colour() == colour) {
                group.add(piece);
            }
        }
        group.sort(BY_PIECE_NUMBER);
        return group;
    }
```

The one-colour version. The `sort` at the end is not cosmetic:

> **Why sort by piece number?**
> `occupants` lists pieces in *arrival* order, which depends on the history of the game. Two things
> read `groupOn` and care about order: `MoveGenerator.directionSettingPieceOf` takes `block.get(0)` as
> the piece that sets a block's direction, and `GameLog.movesBlock` prints the names. Without the sort
> a block would print as `(G4, G2, G1)` and — worse — the *same board position* reached by two
> different histories could produce two different block directions. Sorting makes the board's
> behaviour a function of its position alone, which is what makes seeded games reproducible.

#### The block questions

```java
    /** True when {@code colour} has a block (two or more pieces) on {@code square}. */
    public boolean hasBlockOn(Square square, PieceColour colour) {
        return groupOn(square, colour).size() >= MINIMUM_BLOCK_SIZE;
    }

    /** True when this piece is currently part of one of its own player's blocks. */
    public boolean isPartOfBlock(Piece piece) {
        return piece.isInPlay() && hasBlockOn(piece.square(), piece.colour());
    }
```

`isPartOfBlock` is what Rule T-5 hangs off — a piece leaving a block reverts to its coin-toss
direction — and what green consults before breaking one up. The `isInPlay()` guard stops it answering
"yes" for four pieces sitting together in a base, which is not a block in any meaningful sense.

Note what is *not* on `Board`: a question like "does an opponent hold a block on this square?". That
is a movement rule (Rule T-3), so it lives in `ludot.movement` — `PathResolver.blockerAt` for cells a
piece walks through, and `MoveGenerator.opponentBlockerOn` for the `X` a piece steps out onto. The
board only answers questions about occupancy.

```java
    public List<Square> blockSquaresOf(PieceColour colour) {
        List<Square> squares = new ArrayList<>();
        for (Piece piece : piecesOf(colour)) {
            if (piece.isInPlay() && !squares.contains(piece.square())
                    && hasBlockOn(piece.square(), colour)) {
                squares.add(piece.square());
            }
        }
        return squares;
    }
```

Finds every cell where this colour has a block, by walking its four pieces and keeping the squares
that hold a block. The `!squares.contains(...)` guard **de-duplicates**: a block of three would
otherwise be reported three times, once per member. `contains` works because `Square` has value
equality (and, thanks to the Flyweight, identity too).

Because the outer loop is over `piecesOf(colour)`, which is always in `R1..R4` order, the returned
list is deterministic.

#### Counting and end-of-game

```java
    /** True when no piece stands on the given standard-path cell (needed by Rule T-10). */
    public boolean isRingCellEmpty(int cell) {
        return occupantsAt(Square.ring(cell)).isEmpty();
    }

    /** True when at least one piece of any colour is on the standard path (Rule T-10 trigger). */
    public boolean hasAnyPieceOnRing() {
        for (Piece piece : allPieces()) {
            if (piece.isOnRing()) {
                return true;
            }
        }
        return false;
    }
```

Both exist purely for Rule T-10: the mystery cell may only spawn "on a cell that, at the time of
spawning, has no pieces on it", and only "after two rounds have passed **from pieces in the standard
path**".

```java
    public List<Piece> piecesInBase(PieceColour colour) {
        return groupOn(Square.base(colour), colour);
    }

    public List<Piece> piecesAtHome(PieceColour colour) {
        return groupOn(Square.home(colour), colour);
    }
```

Note how these reuse `groupOn` rather than adding new logic. A base *is* just a square, so "the
pieces in red's base" is "the red pieces standing on `Square.base(RED)`". That is the payoff of
modelling all four kinds of place with one type: the base and home bookkeeping came for free.

```java
    /** Pieces of this colour that are out on the board, i.e. neither in the base nor home. */
    public List<Piece> piecesInPlay(PieceColour colour) {
        List<Piece> inPlay = new ArrayList<>();
        for (Piece piece : piecesOf(colour)) {
            if (piece.isInPlay()) {
                inPlay.add(piece);
            }
        }
        return inPlay;
    }

    /** Rule 11: a player wins once all four of its pieces have reached home. */
    public boolean hasAllPiecesHome(PieceColour colour) {
        return piecesAtHome(colour).size() == BoardGeometry.PIECES_PER_PLAYER;
    }

    private List<Piece> occupantsAt(Square square) {
        return occupants.computeIfAbsent(square, key -> new ArrayList<>());
    }
}
```

`piecesInPlay` is the list `MoveGenerator` iterates: exactly the pieces that can be asked to move.
It is also what the required message *"[Color X] player now has [N]/4 on pieces on the board"* counts.

`occupantsAt` is the only private helper, and `computeIfAbsent` is what keeps every other method
short: no caller ever has to check whether a cell has a list yet. The map therefore grows lazily and
tops out at 80 entries.

### 3.6 `Piece` — sixteen small records of state

A `Piece` answers *what does one piece know about itself*. Crucially, it **does not know how to
move** — that is `ludot.movement`'s job.

> **Why separate "a piece's state" from "how a piece moves"?**
> Moving depends on the whole board (are there blocks in the way?), so a `move()` method on `Piece`
> would need a reference back to the `Board`, and every piece would be entangled with every other.
> Keeping `Piece` as a small record of its own facts is what lets `PathResolver` be a single readable
> class instead of logic smeared across sixteen objects.

```java
package ludot.board;

import ludot.effects.PieceEffects;
```

The one import is the effects holder from section 4; `Square`, `Direction` and `PieceColour` need no
import because they are in the same package.

```java
public final class Piece {

    private final PieceColour colour;
    private final int number;
    private final String name;

    private Square square;
    private Direction direction;
    private Direction initialDirection;
    private int captureCount;
    private int approachPasses;
    private final PieceEffects effects = new PieceEffects();
```

Three `final` identity fields and five mutable state fields. The identity never changes; `R1` is `R1`
for the whole game even after being captured and reset.

The five mutable fields are worth taking one at a time, because **four of them exist only because of
the LUDO-T twists** — a traditional Ludo piece would need just `square`.

```java
    public Piece(PieceColour colour, int number) {
        this.colour = colour;
        this.number = number;
        this.name = "" + colour.initial() + number;
        this.square = Square.base(colour);
    }
```

`"" + colour.initial() + number` builds `"R1"`: the leading `""` forces String concatenation, because
`'R' + 1` on a `char` and an `int` would otherwise produce the number `83`. The piece starts in its own
base, which is Rule 3 — *"At the beginning of the game, no piece belonging to any player will be on
the standard cells"*.

```java
    /**
     * Overwrites the piece's own record of where it stands.
     *
     * <p>Package-private on purpose: only {@link Board#relocate(Piece, Square)} may call it, so the
     * board's occupancy index and the piece can never disagree. The compiler enforces that.
     */
    void setSquare(Square square) {
        this.square = square;
    }
```

The only setter for `square`, and it has **no access modifier**, which in Java means "visible inside
this package only". `Board.relocate` is in the same package and can call it; nothing in
`ludot.movement`, `ludot.mystery` or `ludot.player` can. Every move, capture and teleport therefore
has to go through `relocate`, which updates the occupancy index in the same breath (section 3.5).

```java
    /*
     * One predicate per kind of place, so callers never have to reach through to the Square. The
     * four mirror Square.Kind exactly, and isInPlay() below is the one the movement code really
     * wants: "is this piece somewhere it can be asked to move from?".
     */

    public boolean isInBase() {
        return square.isBase();
    }

    public boolean isOnRing() {
        return square.isRing();
    }

    public boolean isInHomeStraight() {
        return square.isHomeStraight();
    }

    public boolean isAtHome() {
        return square.isHome();
    }

    /** True when the piece stands somewhere it can be asked to move from. */
    public boolean isInPlay() {
        return square.isRing() || square.isHomeStraight();
    }
```

`isInPlay()` is the one that earns its keep. A piece in its base cannot move (it needs a six to come
out, which is a different kind of move) and a piece at home has left the game — *"When a piece reaches
home, the piece is removed from the game"*. So "in play" means exactly "on the ring or in the home
straight", and that single predicate is the filter `MoveGenerator` uses.

#### Direction — and why there are two of them

```java
    /** The direction this piece is travelling in right now (Rules T-1 and T-14). */
    public Direction direction() {
        return direction;
    }

    /** The direction decided by the coin toss at "X"; restored by Rule T-5. */
    public Direction initialDirection() {
        return initialDirection;
    }

    /** Called once, when the piece steps out of the base onto "X" and the coin is tossed. */
    public void assignStartingDirection(Direction tossedDirection) {
        this.direction = tossedDirection;
        this.initialDirection = tossedDirection;
    }

    /** Rule T-14: Gamma turns a clockwise piece around. */
    public void setDirection(Direction direction) {
        this.direction = direction;
    }
```

This is the subtlest piece of state in the program, and it exists because two rules disagree about
what "the piece's direction" means:

> "The direction to move is determined by a **coin toss** after the piece has been moved to X from the
> base." — Rule T-1
>
> "If any group of pieces are moved as a part of a block, when the block is broken by moving an
> individual piece from the block, its direction will be **the original direction of the piece when it
> was placed in X**." — Rule T-5

So a piece needs to remember *both* the direction it is travelling now (which Rule T-14's Gamma can
flip) **and** the direction it was originally given (which Rule T-5 restores). Hence two fields.

`assignStartingDirection` sets both at once and is called exactly once per trip out of the base;
`setDirection` changes only the current one and is called by Gamma and by `MoveExecutor`.

`direction` is `null` while a piece sits in its base — it has not been tossed for yet. That is a real
"there is no answer" case, and several methods guard against it (`PathResolver.walk` returns
`IMPOSSIBLE`, `distanceToHome` returns `UNREACHABLE`).

#### Captures — Rule T-7's gate

```java
    /** Rule T-7: how many opponent pieces this piece has captured so far. */
    public int captureCount() {
        return captureCount;
    }

    public void recordCapture() {
        captureCount++;
    }

    /** Rule T-7: only a piece that has captured at least once may turn into its home straight. */
    public boolean hasEarnedHomeStraightEntry() {
        return captureCount > 0;
    }
```

> "Rule 9 is modified such that a piece can enter the home straight **if and only if it has at least
> captured one opponent piece** during its movement through the board." — Rule T-7

`hasEarnedHomeStraightEntry()` names the rule rather than the mechanism, so `PathResolver` reads
`entryEarned` instead of `captureCount > 0`. The count itself is kept (not just a boolean) because
Rule T-8 talks about incrementing "the number of captures for each piece", and yellow's and green's
strategies ask which pieces still need one. For a block, `PathResolver.walk` asks the question of
*every* member: the block turns into its home straight only if they all answer yes (section 6.2).

#### Approach passes — Rule T-1's counter

```java
    /** Rule T-1: how many times this piece has arrived at its own approach cell. */
    public int approachPasses() {
        return approachPasses;
    }

    public void setApproachPasses(int approachPasses) {
        this.approachPasses = approachPasses;
    }

    public void recordApproachPass() {
        approachPasses++;
    }
```

Two mutators, for two different callers:

- `setApproachPasses` is used by `MoveExecutor` after a move, because a single roll can pass the
  approach cell more than once (a doubled six travels 12 cells) and `PathResolver` has already
  counted them all. It writes the total, rather than incrementing repeatedly.
- `recordApproachPass` is used by `MysteryEffectResolver` when a teleport lands a piece *on* its
  approach cell — see section 7 for why that counts.

#### Rule T-9

```java
    /**
     * Rule T-9: "If any piece is captured and returned to base, all information in that piece will
     * be reset." The caller is responsible for putting the piece back into its base.
     */
    public void resetAfterCapture() {
        direction = null;
        initialDirection = null;
        captureCount = 0;
        approachPasses = 0;
        effects.clear();
    }
```

"All information" taken literally: both directions, the capture count that Rule T-7 needs, the
approach-pass counter, and the Alpha/Beta timers. What it deliberately does **not** do is move the
piece — that is `Board.relocate`'s job, and mixing the two would break the single-mutation-point rule.
The javadoc says so explicitly so the pairing is never forgotten.

The consequence in play is real: a green piece that had captured twice and was three cells from home
goes back to needing a fresh capture, a fresh coin toss, and 56 more cells. That is what makes red's
aggressive strategy effective.

---

## 4. Package `ludot.effects`

Two small types for the lasting consequences of a mystery teleport: Alpha's aura (Rule T-12) and
Beta's briefing (Rule T-13). They are what a `Piece` carries in its `effects` field. Keeping them in a
package of their own keeps `Piece` to plain position-and-history state, and puts all of the "four
rounds" arithmetic in one class.

### 4.1 `SpeedModifier` — Rule T-12's arithmetic

```java
public enum SpeedModifier {

    NORMAL {
        @Override
        public int apply(int rollValue) {
            return rollValue;
        }
    },

    DOUBLED {
        @Override
        public int apply(int rollValue) {
            return rollValue * 2;
        }
    },

    HALVED {
        @Override
        public int apply(int rollValue) {
            return rollValue / 2;
        }
    };

    /** Converts the face value of the dice into the number of cells the piece actually moves. */
    public abstract int apply(int rollValue);
}
```

Rule T-12 in three lines of arithmetic:

> "If the piece gets **energised**, when the piece moves after a roll within the next four rounds, the
> movement will be **double** the value of the roll. If the piece gets **sick** … the movement will be
> **half** the value of the roll." — Rule T-12

Each constant carries its own implementation of `apply`. This is the **Strategy pattern expressed as
an enum**: `apply` is `abstract` on the enum and overridden per constant, so there is no `switch`
anywhere in the program deciding what "doubled" means.

> **Why not `switch (modifier) { case DOUBLED -> roll * 2; ... }` somewhere?**
> Because that `switch` would sit in whichever class happened to need it, far away from the constant
> it describes, and a fourth modifier would mean hunting down every such `switch`. Here, adding
> `TRIPLED` is adding one constant with one method — the Open/Closed Principle at the smallest
> possible scale.

**`/` is integer division, and that is deliberate.** Half of a 5 is 2, and half of a 1 is **0** — a
sick piece that rolls a 1 simply cannot move. `MoveGenerator` handles that explicitly with a
`if (steps <= 0) continue;`. The alternative readings (round up, or use floating point) would either
invent a rule the specification does not state, or produce fractional cells, which the board has no
concept of.

### 4.2 `PieceEffects` — the two four-round timers, and the way out of a briefing

```java
public final class PieceEffects {

    /** Rules T-12 and T-13 both last "the next four rounds". */
    public static final int EFFECT_DURATION_IN_ROUNDS = 4;

    /** Rule T-13: the value whose repetition sends a briefed piece back to its base. */
    public static final int BRIEFING_ESCAPE_ROLL = 3;

    /**
     * Rule T-13: a piece at a Beta briefing is sent to its base if "the player rolls value three
     * consecutively".
     *
     * <p><b>Interpretation.</b> The rule names the <em>value</em> three but not how many times in a
     * row it must appear; "consecutively" needs at least two rolls to mean anything, so two
     * successive threes are used. Only rolls made while the piece is at the briefing count.
     */
    public static final int CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING = 2;
```

Three numbers, each named after the sentence it comes from. The last one is **an honest admission**:
Rule T-13 does not say how many threes "consecutively" means, so the interpretation is written next to
the constant (and listed in `REPORT.md` §6). It lives here, beside the briefing it belongs to, rather
than in `GameRules`, so everything about Rule T-13 can be read in one class.

```java
    private SpeedModifier speedModifier = SpeedModifier.NORMAL;
    private int speedRoundsRemaining;
    private int briefingRoundsRemaining;
    private boolean speedAppliedThisRound;
    private boolean briefingBegunThisRound;
    private int consecutiveEscapeRolls;
```

Two effects, two countdowns. `speedModifier` remembers *which* aura; `speedRoundsRemaining` and
`briefingRoundsRemaining` remember *how much longer*. The two booleans remember that an effect has
only just started (see `onRoundCompleted` below), and `consecutiveEscapeRolls` is the briefing's
run of threes. Java initialises `int` fields to `0` and `boolean` fields to `false`, so a fresh piece
starts with no effects.

> **Why countdowns and not "the round it expires"?**
> The obvious alternative is to store `speedExpiresAtRound = currentRound + 4` and compare against the
> current round at every read. That works, but it means every reader needs the current round number,
> so `Piece`, `MoveGenerator` and `PathResolver` would all have to be handed a clock. It also invites
> a subtle bug class: an off-by-one in the comparison makes the effect last three or five rounds and
> nothing obviously breaks.
>
> A countdown needs no clock at all. Time passes in exactly one place — `onRoundCompleted()` — which
> `LudoGame` calls once per piece per round. There is nothing to compare and nothing to get wrong.

```java
    /** Rule T-12: the piece was energised or made sick by the Alpha aura. */
    public void applyAlphaAura(SpeedModifier modifier) {
        this.speedModifier = modifier;
        this.speedRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
        this.speedAppliedThisRound = true;
    }

    /** Rule T-13: the piece is sent to a briefing at Beta and cannot move for four rounds. */
    public void beginBriefing() {
        this.briefingRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
        this.briefingBegunThisRound = true;
        this.consecutiveEscapeRolls = 0;
    }
```

Both *overwrite* rather than accumulate: landing on Alpha twice in three rounds gives four fresh rounds
of the new aura, it does not stack to eight. The specification says "within the next four rounds" of
the teleport, so re-teleporting restarts the clock.

Each also raises its "applied this round" flag, and `beginBriefing` starts the run of threes from
zero, so a three rolled *before* the piece reached Beta can never count towards its escape.

```java
    /** Rule T-13: every roll of the owning player is shown to the piece while it is briefed. */
    public void observeRoll(int rollValue) {
        if (!isAttendingBriefing()) {
            return;
        }
        consecutiveEscapeRolls = rollValue == BRIEFING_ESCAPE_ROLL ? consecutiveEscapeRolls + 1 : 0;
    }

    /** True once the player has rolled enough consecutive threes to send this piece to base. */
    public boolean mustLeaveBriefingForBase() {
        return isAttendingBriefing()
                && consecutiveEscapeRolls >= CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING;
    }

    /** True while Rule T-13 forbids this piece from moving. */
    public boolean isAttendingBriefing() {
        return briefingRoundsRemaining > 0;
    }
```

Rule T-13's escape clause, owned by the piece it frees:

> "during the next four rounds, the piece will be **teleported to the base** if the player rolls value
> three consecutively." — Rule T-13

`TurnEngine` shows **every** roll of the player to each of its pieces (section 9.2). A piece that is
not briefed ignores it — the guard clause returns at once — so rolls made before or after a briefing
never count. While briefed, one line implements "consecutively": a 3 extends the run, **anything else
resets it to zero**. `mustLeaveBriefingForBase` then answers the only question the turn engine asks.

> **Why is the count on the piece and not on the player?**
> The rolls are the player's, but the four-round window is the piece's: *"during the next four rounds,
> the piece will be teleported"*. A count kept on the player would mix together threes from before
> the briefing and threes from another piece's briefing. Keeping it per piece means each briefing
> counts exactly the rolls made during that briefing, and two pieces briefed at different times each
> get their own window.

```java
    /** Turns a dice face value into the distance this particular piece travels. */
    public int adjustRoll(int rollValue) {
        return activeSpeedModifier().apply(rollValue);
    }

    /** The aura currently in force, which is NORMAL again once its four rounds have run out. */
    private SpeedModifier activeSpeedModifier() {
        return speedRoundsRemaining > 0 ? speedModifier : SpeedModifier.NORMAL;
    }
```

`adjustRoll` is the whole public surface of Rule T-12: hand it a dice value, get back the number of
cells *this* piece travels. `MoveGenerator` calls it once per piece per roll and never has to know
whether an aura is active.

The `speedRoundsRemaining > 0` check in `activeSpeedModifier` makes the countdown authoritative even
if `speedModifier` still holds a stale value — belt and braces, and it means the class is correct
regardless of the order in which its fields happen to be reset.

```java
    /**
     * Advances both countdowns by one round. Called once per round for every piece.
     *
     * <p>The round in which an effect was applied is skipped, so it is never cut short by one.
     */
    public void onRoundCompleted() {
        if (speedAppliedThisRound) {
            speedAppliedThisRound = false;
        } else if (speedRoundsRemaining > 0) {
            speedRoundsRemaining--;
            if (speedRoundsRemaining == 0) {
                speedModifier = SpeedModifier.NORMAL;
            }
        }
        if (briefingBegunThisRound) {
            briefingBegunThisRound = false;
        } else if (briefingRoundsRemaining > 0) {
            briefingRoundsRemaining--;
        }
        if (!isAttendingBriefing()) {
            consecutiveEscapeRolls = 0;
        }
    }
```

`onRoundCompleted` is the *only* place time moves, and the `if … else if` shape is the interesting
part.

An effect always starts **part-way through a round** — the piece is teleported during its owner's
turn. If the end of that same round counted as one of the four, the piece would really get the rest of
one round plus three: the aura would be cut short by one. So the end of the round in which an effect
was applied only lowers the flag; the countdown starts at the end of the *next* round. A briefing that
begins in round 49 therefore keeps the piece frozen through rounds 50, 51, 52 and 53 and lets it move
again in round 54 — the rest of its round plus the **next four full rounds**. (Trace 4 in section 12
shows a piece still frozen in round 52 for exactly this reason.)

The `> 0` guards stop the counters going negative, and resetting `speedModifier` to `NORMAL` when the
count hits zero keeps the state tidy rather than leaving a spent `HALVED` lying around. The last
`if` clears the run of threes once the briefing is over, so it can never leak into a later one.

```java
    /** Rule T-9: a captured piece loses every piece of information it carried. */
    public void clear() {
        speedModifier = SpeedModifier.NORMAL;
        speedRoundsRemaining = 0;
        briefingRoundsRemaining = 0;
        speedAppliedThisRound = false;
        briefingBegunThisRound = false;
        consecutiveEscapeRolls = 0;
    }
}
```

`clear()` implements Rule T-9's "all information in that piece will be reset" for the effects half;
`Piece.resetAfterCapture()` does the rest and calls this. Every field is reset, including the two
flags and the run of threes.

---

## 5. Package `ludot.random`

Four small classes whose only purpose is that **nothing else in the program calls `Math.random()`**.

### 5.1 `RandomSource` — the abstraction

```java
public interface RandomSource {

    /** A value in {@code [0, boundExclusive)}. */
    int nextInt(int boundExclusive);

    /** A fair true/false, used for the coin toss and for the Alpha aura outcome. */
    boolean nextBoolean();

    /** Picks one element of a non-empty list uniformly at random. */
    default <T> T pick(List<T> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick from an empty list");
        }
        return candidates.get(nextInt(candidates.size()));
    }
}
```

Two abstract methods, and one `default` method built on top of them. Everything random in LUDO-T is
one of these three shapes:

| Randomness in the rules | Method used |
|---|---|
| the dice (Rule 1) | `nextInt(6) + 1` |
| the coin toss (Rule T-1) | `nextBoolean()` |
| where the mystery cell spawns (Rule T-10) | `pick(emptyCells)` |
| which of the six teleports (Rule T-11) | `pick(destinations)` |
| energised or sick (Rule T-12) | `nextBoolean()` |

> **Why an interface for something this small?**
> This is the **Dependency Inversion Principle**, and it pays for itself twice.
>
> First, **reproducibility**: because the concrete source is injected once in `LudoTSimulation`,
> `java -cp out Main 42` replays the identical game every time. Debugging a rule that misfires in
> round 137 would be nearly impossible otherwise.
>
> Second, **testability**: `MysteryEffectResolverTest` verifies Rule T-14 by passing
> `Fixtures.fixedRandom(2, true)` — a Mockito mock of `RandomSource` whose `nextInt` always returns
> `2`, i.e. always "Gamma" — so the test can force the exact situation it wants to check. That test
> runs the real `MysteryEffectResolver`, not a copy of it. Neither of those is possible if the rule
> classes call `Math.random()` directly.

`pick` is a `default` method rather than a duplicated helper: it is derived from `nextInt`, so every
implementation gets it for free and none can get it wrong. The explicit empty-list check turns a
would-be `nextInt(0)` exception into a message that names the actual mistake.

### 5.2 `SeededRandomSource` — the real one

```java
public final class SeededRandomSource implements RandomSource {

    private final Random random;

    /** Unpredictable run, seeded by the JVM. */
    public SeededRandomSource() {
        this.random = new Random();
    }

    /** Reproducible run: the same seed always replays the same game. */
    public SeededRandomSource(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int nextInt(int boundExclusive) {
        return random.nextInt(boundExclusive);
    }

    @Override
    public boolean nextBoolean() {
        return random.nextBoolean();
    }
}
```

A thin adapter over `java.util.Random`. Two constructors: no-argument for a fresh game each run,
seeded for a replay. `Random` is the only JDK randomness class the program touches, and it touches it
here and nowhere else.

### 5.3 `Dice`

```java
public final class Dice {

    /** "Value = 1, 2, 3, 4, 5, and 6" (Legend). */
    public static final int FACES = 6;

    /** The face that lets a piece leave the base and grants an extra roll (Rules 2 and 4). */
    public static final int SIX = 6;

    private final RandomSource randomSource;

    public Dice(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    /** Rolls the dice, returning a face value from 1 to 6. */
    public int roll() {
        return randomSource.nextInt(FACES) + 1;
    }
}
```

`nextInt(6)` gives `0..5`; the `+ 1` makes it `1..6`.

> **Why are `FACES` and `SIX` both `6`?**
> Because they mean different things. `FACES` is *how many sides the dice has* — change it and you
> have a different dice. `SIX` is *the special value the rules single out* — Rule 2 (leave the base)
> and Rule 4 (roll again). `MoveGenerator` reads `Dice.SIX`, and `TurnEngine` reads `Dice.SIX`;
> neither cares how many faces there are. Collapsing them into one constant would tie two unrelated
> ideas together, and `if (rollValue != Dice.FACES)` in the enter-the-board check would read as
> nonsense.

### 5.4 `Coin` — Rule T-1's toss

```java
public final class Coin {

    /** The two faces of the coin, each mapped to the direction it awards. */
    public enum Face {
        HEADS("heads", Direction.CLOCKWISE),
        TAILS("tails", Direction.COUNTER_CLOCKWISE);

        private final String displayName;
        private final Direction awardedDirection;
        ...
        public Direction awardedDirection() {
            return awardedDirection;
        }
    }

    private final RandomSource randomSource;

    public Coin(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public Face toss() {
        return randomSource.nextBoolean() ? Face.HEADS : Face.TAILS;
    }
}
```

Rule T-1 transcribed directly:

> "if **heads** was received, the piece would move in a clockwise direction as in the traditional game,
> and if a **tail** was received, the piece moved in the counterclockwise direction." — Rule T-1

The mapping heads→clockwise lives **on the `Face` constant**, so `MoveExecutor` writes
`piece.assignStartingDirection(face.awardedDirection())` and never has to remember which way round it
goes.

> **Why keep a `Face` enum at all — why not have `toss()` return a `Direction` directly?**
> Because the transcript is more convincing with the coin in it: *"The coin toss for green piece G1 is
> heads, so it will move in a clockwise direction."* Returning a bare `Direction` would throw away the
> `"heads"`/`"tails"` wording, and Rule T-1 is stated in terms of the coin. Keeping both means the
> output can show the cause as well as the effect.

---

## 6. Package `ludot.movement`

**This is the rule engine.** Nine types: four inert data records, one path walker, one legality
checker, one applier, and one enum. If you only have time to understand one package, understand this
one.

### 6.1 The four data types

These carry information between the three phases of a turn. All four are Java **records**, so all
four are immutable, and none of them uses `null` to say "nothing here": an absent fall-back move is an
empty `Optional`.

#### `MoveKind`

```java
public enum MoveKind {

    /** Rule 2: a six moves one piece from the base onto its "X" square. */
    ENTER_BOARD,

    /** Rule 1: one piece walks the number of cells shown on the dice. */
    ADVANCE,

    /** Rule T-4: a whole block moves together, each piece by {@code roll / blockSize} cells. */
    BLOCK_ADVANCE,

    /**
     * Rule T-3 / Section 3: the piece could not travel the full distance because of an opponent
     * block, so it stopped on "the cell before the block". Only offered when the player has no
     * other piece able to move.
     */
    PARTIAL_ADVANCE
}
```

Four kinds, because the rules genuinely produce four different things. They matter because the
executor and the log behave differently for each: `ENTER_BOARD` triggers a coin toss, `BLOCK_ADVANCE`
prints a different message and credits captures differently (Rule T-8), and `PARTIAL_ADVANCE` is the
Section 3 fall-back.

#### `PieceMovement` — where one piece goes

```java
/**
 * Where one single piece would end up if a {@link PlannedMove} were carried out.
 *
 * <p>A normal move contains exactly one of these; a block move (Rule T-4) contains one per piece in
 * the block. Modelling it this way means the executor and the log never need to care which kind of
 * move they are dealing with - they just apply every movement in the list.
 *
 * @param direction the direction travelled; {@code null} only for a piece stepping out of its base,
 *                  whose direction is decided by the coin toss after it arrives (Rule T-1).
 * @param approachPassesAtDestination Rule T-1 bookkeeping: the piece's approach-cell counter once
 *                                    it arrives.
 */
public record PieceMovement(Piece piece, Square from, Square to, Direction direction,
        int stepsTaken, int approachPassesAtDestination) {
}
```

A **record** (Java 16+): one line declares six final fields, a constructor, the accessors
`piece()`, `from()`, `to()` … and value-based `equals`, `hashCode` and `toString`. There is nothing
else to write, and nothing that could mutate it after it is built.

The six components describe one piece's journey. `from` and `to` feed the required message *"moves
piece R1 **from location 26 to 30**"*; `stepsTaken` feeds *"**by 4 units**"*; `direction` feeds *"in
**clockwise** direction"*.

`approachPassesAtDestination` is the Rule T-1 bookkeeping: `PathResolver` counted how many times this
walk touched the piece's approach cell, and this component carries the **new total** so `MoveExecutor`
can write it back with a single `setApproachPasses`.

The `direction` may be `null`, and the javadoc says exactly when: a piece stepping out of its base has
no direction until the coin is tossed *after* it lands on `X`. That is a genuine "not decided yet",
not a missing result, so it is documented on the component rather than wrapped in an `Optional`.

> **Why is this a separate type from `PlannedMove`?**
> Because of Rule T-4. A normal move relocates one piece; a block move relocates two, three or four.
> If `PlannedMove` held `from`/`to` directly it would need special-casing everywhere. Instead a
> `PlannedMove` holds a **list** of `PieceMovement`, of length one in the ordinary case, and both the
> executor and the log just loop over it. The four-piece case and the one-piece case are the same code
> path — which is why there is no "block" branch in `MoveExecutor.advance`'s loop body.

#### `PlannedMove` — one fully-checked, not-yet-applied move

```java
public record PlannedMove(MoveKind kind, List<PieceMovement> movements, List<Piece> capturedPieces) {

    public PlannedMove {
        movements = List.copyOf(movements);
        capturedPieces = List.copyOf(capturedPieces);
    }
```

Also a record, with three components: what kind of move it is, which pieces go where, and which
opponents it would capture. The block `public PlannedMove { … }` is a **compact constructor**: it runs
before the fields are assigned, and reassigning the parameters replaces what gets stored.

`List.copyOf` makes **unmodifiable defensive copies**. A record only makes its *fields* final, not the
lists they point to; without the copies, a caller could keep a reference to the list it passed in and
mutate the move after it had been validated — precisely the kind of hole that would let a player
behaviour change a move after the rules approved it. The record's own accessors `movements()` and
`capturedPieces()` then hand out those unmodifiable copies.

```java
    /** The piece the message log talks about; for a block move, the first piece of the block. */
    public Piece primaryPiece() {
        return movements.get(0).piece();
    }

    public Square from() {
        return movements.get(0).from();
    }

    public Square destination() {
        return movements.get(0).to();
    }

    public Direction direction() {
        return movements.get(0).direction();
    }

    /** Cells actually travelled, which is fewer than the roll for a partial or block move. */
    public int stepsTaken() {
        return movements.get(0).stepsTaken();
    }
```

Five conveniences that all read `movements.get(0)`. For a single-piece move that *is* the move; for a
block move all members share the same `from`, `to`, `direction` and `stepsTaken` (they travel as one
body), so element 0 is representative. Because `groupOn` sorts by piece number, element 0 is
deterministically the lowest-numbered piece of the block.

```java
    public boolean capturesAnything() {
        return !capturedPieces.isEmpty();
    }

    public boolean isEnteringBoard() {
        return kind == MoveKind.ENTER_BOARD;
    }

    public boolean isBlockMove() {
        return kind == MoveKind.BLOCK_ADVANCE;
    }

    /** How many pieces travel together; the divisor of Rule T-4. */
    public int groupSize() {
        return movements.size();
    }

    /** The pieces moved by this move, in board order. */
    public List<Piece> movedPieces() {
        return movements.stream().map(PieceMovement::piece).toList();
    }
```

**These questions are the vocabulary the player strategies speak.** Read them next to the
specification's player descriptions and the mapping is exact:

| Strategy sentence | Method it uses |
|---|---|
| red: "if any opponent piece can be captured" | `capturesAnything()` |
| red/green/yellow: "moved to X whenever a six is thrown" | `isEnteringBoard()` |
| green: "attempts to move forward using the block move" | `isBlockMove()` |
| blue: "prioritizes landing on the mystery cell" | `destination()`, asked of `mysteryCell.isOn(...)` |
| red: "prioritises capturing the opponent piece closest to its home" | `capturedPieces()` |

Blue's question is answered by the mystery cell itself — `mysteryCell.isOn(move.destination())` —
rather than by a method on the move. `MysteryCell.isOn` already checks `isRing()` before comparing the
index, so a move ending on `bluehomepath3` can never be mistaken for one ending on cell 3.

#### `BlockedAttempt` — a refused move and its fall-back

```java
/**
 * A move that Rule T-3 refused: an opponent block stands on or before the destination.
 *
 * <p>The specification requires the simulation to report exactly this situation, and to react in one
 * of two ways when the player has nothing else to move: either shuffle the piece forward to "the
 * cell before the block", or ignore the throw altogether. Both possibilities are described here, so
 * the turn engine only has to ask {@link #partialMove()}.
 *
 * @param intendedDestination where the piece would have landed had the block not been there (the
 *                            "L2" of the message).
 * @param blockingPiece       one of the pieces forming the offending block; named in the message.
 * @param partialMove         the shortened move up to the cell before the block, or empty when the
 *                            block leaves no room to advance at all.
 */
public record BlockedAttempt(Piece piece, Square from, Square intendedDestination,
        Piece blockingPiece, Optional<PlannedMove> partialMove) {
}
```

This record exists to serve two specific required messages:

```
[Color X] piece [Name] is blocked from moving from L1 to L2 by [Color X/Y] piece [Name].
[Color X] does not have other pieces ... Moved the piece to square L3 which is the cell before the block.
```

Mapping the components onto them: `piece` and `from` give the first two blanks, `intendedDestination`
is **L2** (where it *would* have gone), `blockingPiece` names the culprit, and `partialMove` is the
shortened move ending on **L3**.

`partialMove` is an `Optional<PlannedMove>`, and that is the whole reason both possibilities are
packaged together: `TurnEngine` asks `attempt.partialMove().isPresent()` to decide between the two
Section 3 messages. If the block sits *immediately* in front of the piece there is no cell before it
to move to, the `Optional` is empty, and the throw is ignored instead. The type makes the "no room at
all" case impossible to forget — there is no `null` to dereference by accident.

A blocked attempt is also produced for a piece that cannot even **leave its base** because an opponent
block is sitting on its `X` (section 6.3). There `from` is the base, `intendedDestination` is `X`, and
`partialMove` is always empty — there is no "cell before" a starting square.

#### `MoveOptions` — the result of phase 1

```java
public record MoveOptions(List<PlannedMove> playableMoves, List<BlockedAttempt> blockedAttempts) {

    public MoveOptions {
        playableMoves = List.copyOf(playableMoves);
        blockedAttempts = List.copyOf(blockedAttempts);
    }

    public boolean hasBlockedAttempt() {
        return !blockedAttempts.isEmpty();
    }
}
```

Two lists rather than one, because the specification treats them completely differently. The player
chooses freely from `playableMoves`; `blockedAttempts` is consulted **only** when `playableMoves` is
empty, which is exactly the condition in the required message *"does not have other pieces in the
board to move instead of the blocked piece"*. The compact constructor makes the same defensive copies
as `PlannedMove`'s.

> **Why not return one list with a "legal" flag on each move?**
> Because then every strategy would have to remember to filter out the illegal ones, and forgetting
> would be a rules violation. Two separate lists make the illegal ones unreachable from
> `selectMove`, which only ever receives `playableMoves`.

### 6.2 `PathResolver` — the heart of the program

Five of the eleven traditional rules and three of the fifteen twists live in this one class. It is
the class to know cold.

#### What it is responsible for

| Rule | What it means here |
|---|---|
| 1, 8, T-1 | a step goes to the next or previous ring cell |
| 9, T-1, T-7 | when a piece may turn into its home straight |
| 10 | the home straight needs an exact roll |
| 5, T-3 | lone pieces can be jumped, blocks cannot |
| 6, T-8 | what may be landed on, and what gets captured |

#### The constants

```java
public final class PathResolver {

    /** Distance value meaning "this piece cannot reach home from where it currently stands". */
    public static final int UNREACHABLE = Integer.MAX_VALUE;

    /**
     * The longest journey any piece can face: two laps of the ring - a counter-clockwise piece must
     * see its approach cell twice (Rule T-1) - plus the home straight, with a cell to spare.
     */
    private static final int MAXIMUM_JOURNEY_LENGTH =
            2 * BoardGeometry.RING_SIZE + BoardGeometry.STEPS_FROM_APPROACH_TO_HOME + 1;
```

`UNREACHABLE` is `Integer.MAX_VALUE` so that "cannot reach home" naturally sorts *last* wherever
distances are compared — a piece in its base never wins a "closest to home" contest.

`MAXIMUM_JOURNEY_LENGTH` is `2 × 52 + 6 + 1 = 111`. It bounds the loop in `distanceToHome`, which
walks until it reaches home. Without a bound, a piece that can never reach home would loop forever;
with it, the method returns `UNREACHABLE` and the program keeps going. The value is *derived* from the
board constants, with the reasoning in the comment: worst case is a counter-clockwise piece that has
just missed its first approach pass, so it needs almost two full laps plus the home straight.

#### The result type

```java
    /** How a walk ended. */
    public enum Outcome {
        /** The piece travelled the full requested distance. */
        COMPLETED,
        /** An opponent block stood in the way (Rule T-3). */
        BLOCKED,
        /** Rule 10: the distance would carry the piece beyond home, so it cannot be played. */
        IMPOSSIBLE
    }
```

Three genuinely different endings, and the caller must handle all three differently:

- `COMPLETED` → a playable move;
- `BLOCKED` → a `BlockedAttempt`, possibly with a shortened fall-back;
- `IMPOSSIBLE` → nothing at all; this piece cannot use this roll.

> **Why an enum instead of returning an empty result for failure?**
> Because there are *two* different failures and they need opposite treatment. `BLOCKED` must be
> reported to the user and may still produce a partial move; `IMPOSSIBLE` must be silently skipped.
> A single "no result" would collapse that distinction, and `MoveGenerator` would have to re-derive it.

```java
    /** The result of walking a piece a given number of cells. */
    public static final class Walk {

        private final Outcome outcome;
        private final Square destination;
        private final int stepsTaken;
        private final int approachArrivals;
        private final Piece blockingPiece;

        private Walk(Outcome outcome, Square destination, int stepsTaken, int approachArrivals,
                Piece blockingPiece) {
            ...
        }
```

An immutable result object with a `private` constructor — only `PathResolver` can create one, so a
`Walk` always reflects an actual walk. (It stays a small class rather than a record precisely because
a record's constructor would have to be public.) Two of its fields have no value for some outcomes,
and the accessors say so in their type:

```java
        /**
         * Where the piece ends up. For {@link Outcome#BLOCKED} this is the furthest cell it could
         * still reach - "the cell before the block" - and it is empty when the block sits
         * immediately in front of the piece. It is also empty for {@link Outcome#IMPOSSIBLE}.
         */
        public Optional<Square> destination() {
            return Optional.ofNullable(destination);
        }
        ...
        /** One of the pieces forming the block that stopped the walk; empty unless BLOCKED. */
        public Optional<Piece> blockingPiece() {
            return Optional.ofNullable(blockingPiece);
        }
```

The fields are plain references, but nothing outside `Walk` ever sees them raw: the accessors wrap
them with `Optional.ofNullable`. An empty `destination()` is the "no room at all" case, and it is what
ends up as an empty `BlockedAttempt.partialMove()`.

```java
        private static Walk impossible() {
            return new Walk(Outcome.IMPOSSIBLE, null, 0, 0, null);
        }
```

One named factory for the result that three different places return. Every "this roll cannot be
played by this piece" in the class reads `return Walk.impossible();`.

#### `walk` — the main method, line by line

There are two `walk` methods, one for a single piece and one for a group:

```java
    /**
     * Walks a single {@code piece} {@code steps} cells in {@code direction}, honouring every block
     * rule.
     */
    public Walk walk(Piece piece, Direction direction, int steps) {
        return walk(List.of(piece), piece, direction, steps);
    }
```

A single piece is simply a group of one, so the one-piece version delegates. Every ordinary move, the
Rule T-6 forced moves and the "cell before the block" fall-back use this form.

```java
    public Walk walk(List<Piece> group, Piece leader, Direction direction, int steps) {
        if (!leader.isInPlay() || direction == null || steps <= 0) {
            return Walk.impossible();
        }
```

The group form is the real one. `group` is every piece travelling together (one piece, or a whole
Rule T-4 block); `leader` is the piece whose square the walk starts from — for a block, the piece
that sets its direction.

Three guards, each a real case: a piece in its base or at home cannot walk; a piece with no coin toss
yet has no direction; and a sick piece's halved roll can be zero (Rule T-12). All three mean "this
roll cannot be played", which is `IMPOSSIBLE`.

```java
        PieceColour colour = leader.colour();
        boolean entryEarned = group.stream().allMatch(Piece::hasEarnedHomeStraightEntry);
        int approachPasses = group.stream().mapToInt(Piece::approachPasses).min().orElse(0);
        Square current = leader.square();
        int approachArrivals = 0;
        Square furthestReached = null;
        int stepsToFurthestReached = 0;
```

The two group-wide facts come first, and they are **Rule T-7 applied to a block**:

- `entryEarned` uses `allMatch` — the block may turn into its home straight only if **every** piece
  in it has captured. One piece without a capture keeps the whole block on the standard path; the
  alternative would carry a piece into its home straight against Rule T-7, or tear the block in half
  mid-move.
- `approachPasses` takes the **minimum** across the group, for the same reason applied to Rule T-1:
  the block is only as far along as its least-travelled member.

For a group of one, both reduce to the piece's own values. Then four locals — and note that **none of
them touch the piece**. The walk is a simulation: it computes what *would* happen. `furthestReached`
starts as `null` precisely so that "blocked immediately, could not move at all" is distinguishable
from "blocked after three steps"; it is a private local, and it only ever leaves the method wrapped in
the `Walk`'s `Optional`.

```java
        for (int step = 1; step <= steps; step++) {
            Optional<Square> nextStep = nextSquare(current, colour, direction,
                    approachPasses + approachArrivals, entryEarned);
            if (nextStep.isEmpty()) {
                return Walk.impossible();
            }
            Square next = nextStep.get();
```

The loop takes one step at a time. The fourth argument is the subtle one:
`approachPasses + approachArrivals` is the group's **stored** pass count plus the passes accumulated
*so far in this very walk*. That addition is what makes a single long roll work correctly: a doubled
six travelling 12 cells can reach the approach cell and then continue, and the second half of the
walk must know that the first half already ticked the counter.

An empty `nextStep` means `nextSquare` refused — the step would go past Home, which is Rule 10. Note
it returns `stepsTaken = 0`: an inexact roll is not a shortened move, it is **no move**.

```java
            boolean isFinalStep = step == steps;
            Optional<Piece> blocker = blockerAt(next, colour, group.size(), isFinalStep);
            if (blocker.isPresent()) {
                return new Walk(Outcome.BLOCKED, furthestReached, stepsToFurthestReached,
                        approachArrivals, blocker.get());
            }
```

`isFinalStep` is essential, because **passing through** a square and **landing on** it obey different
rules. Passing through an opponent block is always forbidden (Rule T-3); landing on one is forbidden
*unless* the arriving group is a blockade of equal size (Rule T-8). One boolean carries that
distinction into `blockerAt`, and `group.size()` carries the size of the arriving group.

When blocked, the walk returns `furthestReached` — the last square it actually stood on. That is
literally *"the cell before the block"* from the required message.

```java
            if (next.isApproachCellOf(colour)) {
                approachArrivals++;
            }
            current = next;
            furthestReached = next;
            stepsToFurthestReached = step;
        }

        return new Walk(Outcome.COMPLETED, current, steps, approachArrivals, null);
    }
```

The step is committed: count an approach arrival if this square is the piece's own approach cell, then
advance the three trackers. Falling out of the loop means all `steps` were taken, so the walk
`COMPLETED`.

> **Worked example — Rule T-3's own worked example.** Green's G1 on cell 0 moving clockwise, red's R1
> and R2 forming a block on cell 4, green rolls 6.
>
> | step | `next` | blocked? | state after |
> |---|---|---|---|
> | 1 | cell 1 | no | `furthestReached = 1` |
> | 2 | cell 2 | no | `furthestReached = 2` |
> | 3 | cell 3 | no | `furthestReached = 3` |
> | 4 | cell 4 | **yes** — red group of 2, not the final step | returns `BLOCKED`, destination **3** |
>
> The specification says *"G1 can move up until cell 3"*.
> `MoveGeneratorTest.theWorkedExampleOfRuleT3OffersAMoveUpToTheCellBeforeTheBlock` asserts exactly
> this.

#### `nextSquare` — one step, and three rules in four lines

```java
    private Optional<Square> nextSquare(Square current, PieceColour colour, Direction direction,
            int approachPasses, boolean entryEarned) {
        if (current.isHomeStraight()) {
            int nextCell = current.index() + 1;
            return Optional.of(nextCell < BoardGeometry.HOME_STRAIGHT_LENGTH
                    ? Square.homeStraight(colour, nextCell)
                    : Square.home(colour));
        }
```

Inside the home straight there is no direction and no wrapping — it is a dead-end corridor of five
cells. From `homepath4` (index 4), `nextCell` is 5, which is not `< 5`, so the next square is **Home**.

This is also where **Rule 10** is enforced, though it takes a moment to see. From Home there is
nowhere to go, and the next branch is what says so:

```java
        if (!current.isRing()) {
            return Optional.empty();
        }
```

A square that is neither a home straight nor a ring cell is a base or a home. Returning an empty
`Optional` makes the whole walk `IMPOSSIBLE`. So a piece on `homepath3` asked to move 3 goes
`homepath4` → `Home` → *empty*, and the roll is refused — *"the player must roll the exact number to
reach home"*. A roll of exactly 2 lands on Home and completes.

```java
        if (current.isApproachCellOf(colour)
                && entryEarned && approachPasses >= direction.requiredApproachPasses()) {
            return Optional.of(Square.homeStraight(colour, 0));
        }
        return Optional.of(Square.ring(direction.nextRingCell(current.index())));
    }
```

The turn into the home straight, and it is **three rules in one condition**:

- `current.isApproachCellOf(colour)` — **Rule 9**: the approach cell is the only doorway.
- `entryEarned` — **Rule T-7**: the piece (for a block, every piece) must have captured at least once.
- `approachPasses >= direction.requiredApproachPasses()` — **Rule T-1**: once clockwise, twice
  counter-clockwise.

If any of the three fails, control falls through to the last line and the piece simply **continues
along the ring**, past its own doorway, for another lap. That is the correct behaviour and it is worth
saying out loud: a piece with no captures does not get stuck, it goes round again.

> **Why does `distanceToHome` pass `entryEarned = true`?**
> Because it measures *progress*, not *legality*. If it respected Rule T-7, every piece that had not
> yet captured would be `UNREACHABLE`, and yellow's "move the piece closest to its home" would have no
> way to compare its pieces at all. The javadoc states this explicitly. Legality is `walk`'s job;
> `distanceToHome` is a ruler.

#### `blockerAt` — Rules 5, T-3 and T-8

```java
    private Optional<Piece> blockerAt(Square square, PieceColour mover, int groupSize,
            boolean isFinalStep) {
        if (!square.isRing()) {
            return Optional.empty();
        }
```

Only ring cells can be contested. A home straight belongs to one colour, so no opponent can ever be
in it — no check needed.

```java
        for (Map.Entry<PieceColour, List<Piece>> group : board.groupsOn(square).entrySet()) {
            if (group.getKey() == mover) {
                continue;
            }
```

Skip the mover's own pieces. **Rule T-3 gives blocks power only over opponents**, so your own block is
something you can walk over and join.

```java
            int opponentGroupSize = group.getValue().size();
            if (opponentGroupSize < Board.MINIMUM_BLOCK_SIZE) {
                continue;
            }
```

A group of one is not a block. **Rule 5** says it can be jumped over, **Rule 6** says it can be
captured — either way it does not stop anybody. So it is skipped, and the capture (if this is the
landing square) is worked out separately by `capturesOnLanding`.

```java
            boolean blockadeCapturesBlockade = isFinalStep && opponentGroupSize == groupSize;
            if (!blockadeCapturesBlockade) {
                return Optional.of(group.getValue().get(0));
            }
        }
        return Optional.empty();
    }
```

The one line that implements **Rule T-8**:

> "A blockade of the same size can capture a blockade." — Rule T-8

The permission needs both halves. `isFinalStep` because you may *land on* an equal blockade but never
*pass through* one — passing through is Rule T-3 with no exception. And `opponentGroupSize ==
groupSize` because the rule says "the same size": a pair may not walk onto a trio, and a trio may not
walk onto a pair. If the permission does not apply, the group's first piece is returned as the
blocker — the one named in the status message, and deterministic because `groupsOn` sorts by number.

The full truth table:

| Arriving group | Opponent group on the square | Passing through | Landing on |
|---|---|---|---|
| 1 | 1 | jump over (Rule 5) | **capture** (Rule 6) |
| 1 | 2 | blocked | blocked |
| 2 | 1 | jump over | **capture** |
| 2 | 2 | blocked | **capture the blockade** (Rule T-8) |
| 2 | 3 | blocked | blocked |
| 3 | 2 | blocked | blocked |

#### `capturesOnLanding`

```java
    public List<Piece> capturesOnLanding(Square square, PieceColour mover) {
        List<Piece> captured = new ArrayList<>();
        if (!square.isRing()) {
            return captured;
        }
        for (Map.Entry<PieceColour, List<Piece>> group : board.groupsOn(square).entrySet()) {
            if (group.getKey() != mover) {
                captured.addAll(group.getValue());
            }
        }
        return captured;
    }
```

Called only *after* `blockerAt` has approved the landing, so by the time this runs the square is known
to be legal to land on. Every opponent piece there is therefore captured — a lone piece by Rule 6, or
a whole equal-sized blockade by Rule T-8.

It collects across **all** opponent colours, which matters in one specific situation: teleports do not
capture (see section 7), so a red piece and a green piece can end up sharing a cell. A yellow piece
landing there sends both home.

#### `destinationIgnoringBlocks`

```java
    public Square destinationIgnoringBlocks(Piece piece, Direction direction, int steps) {
        Square current = piece.square();
        int approachArrivals = 0;
        for (int step = 1; step <= steps; step++) {
            Optional<Square> nextStep = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, piece.hasEarnedHomeStraightEntry());
            if (nextStep.isEmpty()) {
                return current;
            }
            ...
        }
        return current;
    }
```

The same walk with the block checks removed. Its **only** purpose is to fill in the `L2` of *"is
blocked from moving from L1 to **L2**"* — the user needs to be told where the piece was trying to go,
which by definition is a square the block prevented it from reaching. It cannot be answered by `walk`,
because `walk` stops at the block.

#### `distanceToHome`

```java
    public int distanceToHome(Piece piece) {
        return piece.direction() == null ? UNREACHABLE : distanceToHome(piece, piece.direction());
    }

    /** The same measurement, but assuming the piece travelled in the given direction. */
    public int distanceToHome(Piece piece, Direction direction) {
        if (!piece.isInPlay() || direction == null) {
            return UNREACHABLE;
        }
        Square current = piece.square();
        int approachArrivals = 0;
        for (int steps = 1; steps <= MAXIMUM_JOURNEY_LENGTH; steps++) {
            Optional<Square> nextStep = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, true);
            if (nextStep.isEmpty()) {
                return UNREACHABLE;
            }
            Square next = nextStep.get();
            if (next.isHome()) {
                return steps;
            }
            ...
        }
        return UNREACHABLE;
    }
```

Walks until it reaches Home and returns the number of steps that took. Three rules are phrased in
terms of this one measurement, which is why it is worth having:

- yellow "moves the piece **closest to its home**";
- red captures "the opponent piece **closest to its home**";
- Rule T-4 moves a mixed block "in the direction of the **longest distance from home**".

The two-argument overload exists so the distance can be asked for a direction other than the one the
piece is currently facing; the one-argument form is the everyday case and guards the base piece,
whose direction is still `null`, by answering `UNREACHABLE`.

Two checkable results, both asserted in `PathResolverTest`:

```
clockwise from X:          50 (to approach) + 5 (home straight) + 1 (into Home) = 56
counter-clockwise from X:   2 (first approach pass) + 52 (a full lap) + 6        = 60
```

> **Why re-walk the path every time instead of caching it?**
> It costs at most 111 iterations of a three-line loop, and it is called a handful of times per turn.
> A cache would have to be invalidated on every capture, every teleport, every direction change and
> every approach pass — four separate opportunities to get it wrong, in exchange for saving time that
> is not measurable against the cost of printing a line of output.

### 6.3 `MoveGenerator` — "what is legal?"

```java
    /** Every legal move - and every block-refused attempt - for this colour and this dice value. */
    public MoveOptions optionsFor(PieceColour colour, int rollValue) {
        List<PlannedMove> playable = new ArrayList<>();
        List<BlockedAttempt> blocked = new ArrayList<>();

        addEnterBoardMove(colour, rollValue, playable, blocked);
        addSinglePieceMoves(colour, rollValue, playable, blocked);
        addBlockMoves(colour, rollValue, playable);

        return new MoveOptions(playable, blocked);
    }
```

The whole method is three calls, because there are exactly **three shapes of move** in LUDO-T:

1. a six lifting a piece out of the base (Rules 2 and 3);
2. one piece walking the dice value (Rule 1);
3. a whole block walking together (Rule T-4).

> **Why is this "collect into a list I was handed" style used instead of returning three lists and
> concatenating?**
> Because the three helpers contribute to the same two output lists at different rates —
> `addEnterBoardMove` produces zero or one entry (a move, or a blocked attempt when `X` is occupied by
> an opponent block), `addSinglePieceMoves` can produce both kinds, and `addBlockMoves` only ever adds
> playable moves. Passing the accumulators in keeps each helper to a single job and avoids three
> intermediate collections.

#### Rule T-5's exception

```java
    private Direction travelDirectionOf(Piece piece) {
        return board.isPartOfBlock(piece) ? piece.initialDirection() : piece.direction();
    }
```

Two lines that implement Rule T-5:

> "If any group of pieces are moved as a part of a block, when the block is broken by moving an
> individual piece from the block, its direction will be **the original direction of the piece when it
> was placed in X**." — Rule T-5

A piece standing in a block might have been *carried* in the block's direction, which may not be its
own. So the moment it strikes out alone it reverts to its coin-toss direction. Note this is consulted
**at generation time**, not after — the direction changes where the piece can go, so it must be settled
before the path is walked.

#### Rule T-6's back door

```java
    /**
     * Builds a move for a fixed distance, outside the normal "one roll, one move" flow.
     *
     * <p>Rule&nbsp;T-6 needs this: a player that rolls a third consecutive six while holding a
     * blockade must break it up by moving its pieces "in their original direction by six units
     * cumulatively", which is a distance the dice never produced directly.
     *
     * @return the planned move, or empty when the piece cannot travel that far.
     */
    public Optional<PlannedMove> forcedMove(Piece piece, Direction direction, int steps) {
        PathResolver.Walk walk = pathResolver.walk(piece, direction, steps);
        if (!walk.isCompleted()) {
            return Optional.empty();
        }
        return Optional.of(singlePieceMove(MoveKind.ADVANCE, piece, direction, walk));
    }
```

The one method that steps outside the "one roll, one move" flow. Rule T-6 forces a player to break up
a blockade by moving pieces "by six units cumulatively" — a distance that no dice value produced, so
`TurnEngine` needs a way to ask for an arbitrary number of steps (its share of the six, see section
9.2). Returning an empty `Optional` when the walk did not complete lets the caller report that a piece
was stuck rather than crashing — and the return type means the caller *cannot* forget to check.

#### Rules 2 and 3 — leaving the base

```java
    private void addEnterBoardMove(PieceColour colour, int rollValue, List<PlannedMove> playable,
            List<BlockedAttempt> blocked) {
        if (rollValue != Dice.SIX) {
            return;
        }
        List<Piece> waitingInBase = board.piecesInBase(colour);
        if (waitingInBase.isEmpty()) {
            return;
        }
```

Rule 2 — *"To move a piece from the base to the starting square 'X', the player must obtain a six"* —
and there has to be a piece waiting. Together these two guards are also Rule 3: with no six, no piece
ever reaches the standard cells.

```java
        // The pieces waiting in the base are interchangeable, so the lowest numbered one is used.
        Piece piece = waitingInBase.get(0);
        Square startSquare = Square.ring(colour.startCell());
        Optional<Piece> blocker = opponentBlockerOn(startSquare, colour);
        if (blocker.isPresent()) {
            // An opponent block is sitting on "X", so there is nowhere to step out to (Rule T-3).
            blocked.add(new BlockedAttempt(piece, piece.square(), startSquare, blocker.get(),
                    Optional.empty()));
            return;
        }
```

An edge case worth knowing about, because it is the sort of thing a marker probes. If an opponent has
parked a block on your `X`, there is nowhere to step out to: Rule T-3 says you cannot land on an
opponent block, and `X` is a landing. That is a *blocked* move in exactly the sense of Section 3, so it
is recorded as a `BlockedAttempt` — from the base to `X`, naming the blocking piece, with an empty
partial move because there is no "cell before" a starting square. If the player has nothing else to
play, the turn engine then prints the required pair of messages, for example *"red piece R1 is
blocked from moving from Base to 26 by green piece G1."* followed by *"… Ignoring the throw and moving
on to the next player."* A *lone* opponent piece on `X` is different — it gets captured, which the
next lines handle.

```java
        PieceMovement movement = new PieceMovement(piece, piece.square(), startSquare, null, 0, 0);
        List<Piece> captured = pathResolver.capturesOnLanding(startSquare, colour);
        playable.add(new PlannedMove(MoveKind.ENTER_BOARD, List.of(movement), captured));
    }
```

Only **one** enter-board move is generated, even with four pieces in the base, because the four are
genuinely indistinguishable: they all have no direction, no captures and no history. Generating four
identical options would just make every strategy's list longer for no benefit. `waitingInBase` is
sorted by number (via `groupOn`), so `get(0)` deterministically picks the lowest.

The `null` and two `0`s in the `PieceMovement` are meaningful, not filler:

- `direction` is `null` because **the coin has not been tossed yet** — Rule T-1 says the toss happens
  *"after the piece has been moved to X"*. `MoveExecutor.enterBoard` tosses it.
- `stepsTaken = 0` because stepping out of the base is not a walk along the path.
- `approachPassesAtDestination = 0` because a fresh piece has passed nothing.

#### Rule 1 — one piece walking

```java
    private void addSinglePieceMoves(PieceColour colour, int rollValue, List<PlannedMove> playable,
            List<BlockedAttempt> blocked) {
        for (Piece piece : board.piecesInPlay(colour)) {
            if (piece.effects().isAttendingBriefing()) {
                // Rule T-13: a piece at a Beta briefing cannot move for four rounds.
                continue;
            }
            int steps = piece.effects().adjustRoll(rollValue);
            if (steps <= 0) {
                // Rule T-12: a sick piece halves its roll, and half of a 1 is no move at all.
                continue;
            }
```

Every piece on the board gets considered, with two twist-driven filters first: Rule T-13 pieces are
frozen, and Rule T-12's halving can reduce a roll of 1 to zero cells.

Note that `adjustRoll` is **per piece**, not per player. Two red pieces can be moving at different
speeds in the same roll — one energised, one sick — and the generator handles that without any special
case, because the speed lives on the piece.

```java
            Direction direction = travelDirectionOf(piece);
            PathResolver.Walk walk = pathResolver.walk(piece, direction, steps);
            switch (walk.outcome()) {
                case COMPLETED -> playable.add(
                        singlePieceMove(MoveKind.ADVANCE, piece, direction, walk));
                case BLOCKED -> blocked.add(blockedAttempt(piece, direction, steps, walk));
                case IMPOSSIBLE -> {
                    // Rule 10: the roll is not the exact number needed to finish the home straight.
                }
            }
        }
    }
```

The single-piece form of `walk` is used, because this piece walks alone — a group of one, which is
what tells `blockerAt` it may not capture a blockade.

The `switch` handles all three outcomes, and the empty `IMPOSSIBLE` branch is deliberate: it is
written out with a comment so a reader can see the case was *considered and intentionally does
nothing*, rather than wondering whether it was forgotten. Because `Outcome` is an enum and all three
constants appear, adding a fourth outcome would be a compile error here.

#### Rule T-4 — a block walking together

```java
    private void addBlockMoves(PieceColour colour, int rollValue, List<PlannedMove> playable) {
        for (Square blockSquare : board.blockSquaresOf(colour)) {
            List<Piece> block = board.groupOn(blockSquare, colour);
            if (containsRestrictedPiece(block)) {
                continue;
            }
            int steps = rollValue / block.size();
            if (steps <= 0) {
                continue;
            }
```

`rollValue / block.size()` is Rule T-4 verbatim:

> "all pieces in the block are moved by the number of positions equal to **the die roll value divided
> by the number of pieces** participating in the block." — Rule T-4

Integer division, so a block of two moving on a 5 travels 2 cells, and a block of four needs a roll of
at least 4 to move at all (`3 / 4 = 0`). `containsRestrictedPiece` stops a block moving if any member
is frozen at a briefing — the body cannot travel while one of its parts cannot.

Note this uses the **raw** `rollValue`, not a speed-adjusted one. Rule T-4 says "the die roll value",
and the Alpha aura is a property of an individual piece; a block of two pieces with different auras
has no defined combined speed. This interpretation is recorded in `REPORT.md` §6.

```java
            Piece leader = directionSettingPieceOf(block);
            Direction direction = leader.direction();
            PathResolver.Walk walk = pathResolver.walk(block, leader, direction, steps);
            if (!walk.isCompleted()) {
                continue;
            }
```

The walk is resolved **once**, for the whole block, starting from the piece that sets the direction.
Passing the `block` itself does two jobs inside `walk`: its size lets Rule T-8 apply, and its members
decide Rule T-7 together — the block turns into its home straight only if every piece in it has
captured. If the block cannot complete its walk it simply does not get a move — there is no "partial
block move" in the specification.

> **Why resolve once rather than walking each piece separately?**
> Because a block is one body. Walked individually, the members could diverge: one might have captured
> and be allowed into its home straight while another has not, and the "block" would tear in half
> mid-move. Resolving once — with the strictest member deciding the home-straight turn — and applying
> the destination to everyone is what keeps a block a block. This is documented as an interpretation
> in `REPORT.md` §6.

```java
            Square destination = walk.destination().orElseThrow();
            List<PieceMovement> movements = new ArrayList<>();
            for (Piece piece : block) {
                movements.add(new PieceMovement(piece, blockSquare, destination, direction, steps,
                        piece.approachPasses() + walk.approachArrivals()));
            }
            List<Piece> captured = pathResolver.capturesOnLanding(destination, colour);
            playable.add(new PlannedMove(MoveKind.BLOCK_ADVANCE, movements, captured));
        }
    }
```

`walk.destination().orElseThrow()` is safe here: a completed walk always has a destination, and the
`isCompleted()` check just above guarantees this one completed. Should that ever stop being true, the
failure would be an immediate, named exception rather than a `null` travelling on into the move.

One `PieceMovement` per member, all sharing the same destination, direction and step count — but each
getting **its own** approach-pass total (`piece.approachPasses() + walk.approachArrivals()`), because
they may have arrived at this cell having seen the approach cell a different number of times.

#### Rule T-4's direction choice

```java
    private Piece directionSettingPieceOf(List<Piece> block) {
        Piece firstPiece = block.get(0);
        boolean directionsAgree = block.stream()
                .allMatch(piece -> piece.direction() == firstPiece.direction());
        if (directionsAgree) {
            return firstPiece;
        }
```

The common case first: if every member faces the same way there is nothing to decide, so the first
piece (lowest-numbered, thanks to `groupOn`'s sort) speaks for the block.

```java
        Piece leader = firstPiece;
        int longestDistance = pathResolver.distanceToHome(firstPiece);
        for (Piece piece : block) {
            int distance = pathResolver.distanceToHome(piece);
            if (distance > longestDistance) {
                longestDistance = distance;
                leader = piece;
            }
        }
        return leader;
    }
```

Rule T-4's first sentence:

> "If a block is created by two pieces moving in the opposite direction, the block shall move in the
> direction of the **longest distance from home**." — Rule T-4

A linear scan for the maximum. Note it is a **strict** `>`, so on a tie the earliest piece wins and the
result stays deterministic.

> **A concrete case, as in `MoveGeneratorTest`.** Two yellow pieces share cell 20, one clockwise, one
> counter-clockwise. Yellow's approach is cell 50, so the clockwise piece has 30 cells to the approach
> plus 6 = **36** to go. The counter-clockwise one must reach cell 50 twice: 22 cells back to 50, then
> a full 52-cell lap, then 6 = **80**. So 80 > 36, the counter-clockwise piece leads, and the block
> moves *backwards*. On a roll of 6 a block of two moves `6 / 2 = 3` cells, landing on cell **17**.

#### Who is blocking `X`?

```java
    /** One piece of an opponent block standing on {@code square}, if there is such a block. */
    private Optional<Piece> opponentBlockerOn(Square square, PieceColour mover) {
        return board.groupsOn(square).entrySet().stream()
                .filter(group -> group.getKey() != mover)
                .filter(group -> group.getValue().size() >= Board.MINIMUM_BLOCK_SIZE)
                .map(group -> group.getValue().get(0))
                .findFirst();
    }
```

"Does an **opponent** hold a block here — and if so, who should the message name?" The first filter
skips the mover's own pieces, because Rule T-3 only forbids *opponents* from using a block's cell; the
second keeps only real blocks; the `map` picks the block's first piece (lowest-numbered, because
`groupsOn` sorts), which becomes the "[Color Y] piece [Name]" of the blocked message. It answers with
an `Optional` because there usually is no such block.

It is private to `MoveGenerator` because it has exactly one use — the base-entry check above. Every
other "is there a block in the way?" question is asked step by step inside `PathResolver.blockerAt`.

```java
    private boolean containsRestrictedPiece(List<Piece> block) {
        return block.stream().anyMatch(piece -> piece.effects().isAttendingBriefing());
    }
```

The Rule T-13 check used by `addBlockMoves`: one briefed member freezes the whole block.

#### The two builders

```java
    private PlannedMove singlePieceMove(MoveKind kind, Piece piece, Direction direction,
            PathResolver.Walk walk) {
        Square destination = walk.destination().orElseThrow();
        PieceMovement movement = new PieceMovement(piece, piece.square(), destination, direction,
                walk.stepsTaken(), piece.approachPasses() + walk.approachArrivals());
        List<Piece> captured = pathResolver.capturesOnLanding(destination, piece.colour());
        return new PlannedMove(kind, List.of(movement), captured);
    }
```

Shared by all three single-piece cases — a normal advance, a Rule T-6 forced move, and a partial
advance — which is why `kind` is a parameter. It is also the one place where captures are attached to
a single-piece move, so the three cases cannot drift apart.

```java
    private BlockedAttempt blockedAttempt(Piece piece, Direction direction, int steps,
            PathResolver.Walk walk) {
        Square intendedDestination = pathResolver.destinationIgnoringBlocks(piece, direction, steps);
        Optional<PlannedMove> partialMove = walk.destination()
                .map(reached -> singlePieceMove(MoveKind.PARTIAL_ADVANCE, piece, direction, walk));
        return new BlockedAttempt(piece, piece.square(), intendedDestination,
                walk.blockingPiece().orElseThrow(), partialMove);
    }
```

Two extra pieces of information are gathered for the report: `intendedDestination` (the `L2` of the
message, computed by the block-blind walk) and the shortened `partialMove`.

`walk.destination().map(...)` is where "there was no room to move at all" stays an empty `Optional`:
if the walk reached some cell before the block, `map` turns it into a `PARTIAL_ADVANCE` move; if it
reached none, there is nothing to map and the attempt carries `Optional.empty()`, which the turn engine
later turns into the "ignore the throw" branch. `blockingPiece().orElseThrow()` cannot fail, because
this method is only called for a `BLOCKED` walk, which always names its blocker.

### 6.4 `MoveExecutor` — "make it happen"

The **only** class that changes the board as the result of a move.

```java
    private final Board board;
    private final Coin coin;
    private final MysteryCell mysteryCell;
    private final MysteryEffectResolver mysteryEffectResolver;
    private final GameListener log;
```

Note the type of `log`: a `GameListener`, the interface from section 10, not the `GameLog` class.
`MoveExecutor` reports *what happened* — "R1 moved", "B1 was captured" — and has no idea whether that
becomes a line on the console or a verified call on a test's mock.

```java
    public boolean execute(PlannedMove move) {
        List<Piece> movedPieces = move.movedPieces();
        Square destination = move.destination();

        if (move.isEnteringBoard()) {
            enterBoard(move);
        } else {
            advance(move);
        }

        boolean captured = applyCaptures(move, destination);

        // Rule T-11: the teleport happens after the arrival is complete, so a piece can capture an
        // opponent on the mystery cell and only then be whisked away.
        if (mysteryCell.isOn(destination)) {
            for (Piece piece : movedPieces) {
                if (piece.square().equals(destination)) {
                    mysteryEffectResolver.resolveLandingOnMysteryCell(piece);
                }
            }
        }

        reportPiecesThatReachedHome(movedPieces);
        return captured;
    }
```

`movedPieces` and `destination` are captured into locals **before** anything moves, because the
mystery teleport is about to relocate pieces and `move.destination()` would then no longer describe
where they arrived.

The ordering is a rules decision, not an accident:

1. **relocate** — the piece is physically there;
2. **capture** — Rules 6, T-8, T-9 send opponents home;
3. **teleport** — Rule T-11 may then whisk the arriving piece away.

Capturing before teleporting means a piece can take an opponent off the mystery cell and *then* be
teleported. Reversing the order would silently spare the opponent.

The `piece.square().equals(destination)` guard inside the loop handles the block case: after the first
piece teleports away it is no longer on the destination, but the others still are and each gets its own
independent roll of the six destinations.

The return value is the whole of **Rule T-2**: `true` means "you captured, take another roll".

```java
    /** Rules 2 and T-1: the piece steps onto "X" and its travel direction is tossed for. */
    private void enterBoard(PlannedMove move) {
        Piece piece = move.primaryPiece();
        board.relocate(piece, move.destination());
        log.movesToStartingPoint(piece);
        log.playerPieceCounts(board, piece.colour());

        Coin.Face face = coin.toss();
        piece.assignStartingDirection(face.awardedDirection());
        log.coinTossed(piece, face);
    }
```

The order here is dictated by Rule T-1: *"The direction to move is determined by a coin toss **after
the piece has been moved to X**"*. So the piece is relocated, the two required messages are printed,
and only then is the coin tossed. `assignStartingDirection` sets both `direction` and
`initialDirection`, arming Rule T-5 for the rest of the piece's life.

```java
    private void advance(PlannedMove move) {
        for (PieceMovement movement : move.movements()) {
            Piece piece = movement.piece();
            if (!move.isBlockMove()) {
                // Rule T-5: a piece that steps out of a block on its own travels in the direction it
                // was given at "X", so that direction becomes its current one again here. A block
                // move deliberately does not touch it: the pieces are carried in the block's
                // direction but each keeps its own, which is what Rule T-4 compares next time.
                piece.setDirection(movement.direction());
            }
            piece.setApproachPasses(movement.approachPassesAtDestination());
            board.relocate(piece, movement.to());
        }

        if (move.isBlockMove()) {
            log.movesBlock(move);
        } else {
            log.movesPiece(move);
        }
    }
```

The loop is the same for one piece and for four — that is the payoff of the `PieceMovement` list.

The `if (!move.isBlockMove())` around `setDirection` is a genuinely subtle rules point, and it is the
kind of thing worth being able to explain:

> **Why must a block move NOT write the direction back onto its pieces?**
> Rule T-4 asks whether "a block is created by two pieces moving in the opposite direction". If a
> block move overwrote every member's direction with the block's direction, then after one block move
> all members would "agree", and Rule T-4's mixed-direction clause could never fire again for that
> block. Worse, a member that later broke away would carry the block's direction rather than its own.
>
> Leaving each piece's own direction alone keeps Rule T-4 meaningful on every subsequent roll, and
> keeps Rule T-5 honest: the piece's own direction is still there when it strikes out alone. A single
> piece moving *does* write its direction back, because that is precisely the T-5 restoration.

```java
    private boolean applyCaptures(PlannedMove move, Square destination) {
        if (!move.capturesAnything()) {
            return false;
        }

        Piece capturer = move.primaryPiece();
        for (Piece captured : new ArrayList<>(move.capturedPieces())) {
            board.relocate(captured, Square.base(captured.colour()));
            captured.resetAfterCapture();
            log.capture(capturer, captured, destination.label());
        }
```

Each victim is sent home and wiped. The two lines together are Rules 6 and T-9: *"the opposing player's
piece is returned to the base"* and *"all information in that piece will be reset"*. `relocate` first,
then `resetAfterCapture` — the reset does not move anything, by design.

`new ArrayList<>(...)` makes a copy to iterate. `capturedPieces` is already immutable, so this is
defensive rather than necessary, but it documents the intent: the loop body mutates board state, and
iterating a snapshot makes that unambiguous.

```java
        if (move.isBlockMove()) {
            // Rule T-8: "The number of captures for each piece participating in the capturing
            // blockade will be incremented by one (1)."
            for (Piece piece : move.movedPieces()) {
                piece.recordCapture();
            }
        } else {
            // Rule 6: the single arriving piece is credited with every piece it removed.
            for (int index = 0; index < move.capturedPieces().size(); index++) {
                capturer.recordCapture();
            }
        }

        log.playerPieceCounts(board, capturer.colour());
        return true;
    }
```

Two different crediting rules, because the specification states them differently.

Rule T-8 is explicit: *each* piece in the capturing blockade gets **+1**. So a pair capturing a pair
gives each of the two attackers one capture — not two.

Rule 6 has no such sentence, so a lone capturer is credited with everything it removed. That normally
means +1, and only differs in the rare case where a teleport had left two opponents of different
colours on one cell.

Either way each attacker ends up with `captureCount > 0`, which is what **Rule T-7** needs to open the
home straight. This is the mechanical link between "capturing" and "winning".

The single `log.playerPieceCounts(board, capturer.colour())` at the end follows the Section 3.1
template for a capture exactly:

```
[Color X] piece [Name] lands on square L1, captures [Color Y] piece [Name], and returns it to the base.
[Color X] player now has [Number]/4 on pieces on the board and [Number]/4 pieces on the base.
```

Both lines are about *Color X*, the capturing player, so only the capturer's counts are printed — once,
after every victim has been reported. The victims' new counts appear in the end-of-round report.

```java
    private void reportPiecesThatReachedHome(List<Piece> movedPieces) {
        for (Piece piece : movedPieces) {
            if (piece.isAtHome()) {
                log.pieceReachedHome(piece, board.piecesAtHome(piece.colour()).size());
            }
        }
    }
```

Purely reporting. Note it asks the **board** how many pieces are home rather than keeping a counter —
one source of truth, consistent with the rest of the design.

---

## 7. Package `ludot.mystery`

Three classes for Rules T-10 to T-15 — the twist that makes LUDO-T not-quite-Ludo.

### 7.1 `TeleportDestination` — the six places

```java
public enum TeleportDestination {

    /** Cell 7. Rule T-12: the aura either energises or sickens the piece. */
    ALPHA("Alpha") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(BoardGeometry.ALPHA_CELL);
        }
    },
    ...
    /** The "X" starting square of the piece's own colour. */
    START("X") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(colour.startCell());
        }
    },

    /** The approach circle of the piece's own colour - the doorway to its home straight. */
    APPROACH("Approach") {
        @Override
        public Square squareFor(PieceColour colour) {
            return Square.ring(colour.approachCell());
        }
    };
    ...
    /** Where this destination actually is for a piece of the given colour. */
    public abstract Square squareFor(PieceColour colour);
}
```

Rule T-11's list of six, in the order the rule gives them:

> 1. Alpha  2. Beta  3. Gamma  4. Base  5. X of the piece colour  6. Approach of the piece colour

The clever part is that the six split into two kinds and the enum hides the difference. Alpha, Beta and
Gamma are **absolute** — fixed cells 7, 25 and 44, the same for everybody, so they ignore the `colour`
parameter. Base, X and Approach are **relative** to the piece being teleported.

By giving every constant the same `squareFor(colour)` method, the caller does not care which kind it
got:

```java
Square target = destination.squareFor(piece.colour());
```

> **Why abstract-method-per-constant rather than a `switch`?**
> The same argument as `SpeedModifier`: the knowledge of where "Approach" is belongs next to the
> constant named `APPROACH`, not in a `switch` in another class. It also means the random pick is
> trivially `randomSource.pick(List.of(values()))` — all six are uniformly usable, exactly as Rule
> T-11 requires.

The `displayName` strings — `"Alpha"`, `"Base"`, `"X"`, `"Approach"` — are the exact words the required
messages need: *"[Color X] piece [name] teleported to **Approach**."*

### 7.2 `MysteryCell` — Rule T-10's life cycle

```java
public final class MysteryCell {

    /** Rule T-10: two full rounds with pieces on the standard path before the first spawn. */
    public static final int ROUNDS_BEFORE_FIRST_SPAWN = 2;

    /** Rule T-10: "it will remain in the same cell for four rounds". */
    public static final int LIFETIME_IN_ROUNDS = 4;

    private static final int NO_CELL = -1;

    private final Board board;
    private final RandomSource randomSource;

    private boolean piecesWereOnPathAtRoundStart;
    private int fullRoundsWithPiecesOnPath;
    private int currentCell = NO_CELL;
    private int previousCell = NO_CELL;
    private int roundsRemaining;
```

Rule T-10 has four separate conditions, and this class is the only place that knows any of them:

> "The mystery cell should appear on the board **after two rounds have passed from pieces in the
> standard path**. The mystery cell can occur randomly at any cell location in the standard path (52
> cells) **on a cell that, at the time of spawning, has no pieces on it**. Once the mystery cell has
> appeared, it will **remain in the same cell for four rounds** and reappear at another random
> location. Mystery cells **cannot appear in the same place consecutively**." — Rule T-10

The fields map onto those conditions: `piecesWereOnPathAtRoundStart` and `fullRoundsWithPiecesOnPath`
count towards the first, the board is consulted for the second, `roundsRemaining` counts down the
third, and `previousCell` enforces the fourth.

> **Why `int` with a `NO_CELL` sentinel for `currentCell` and `previousCell`?**
> Both genuinely have a "there is no such cell" state, and cell **0** is a perfectly valid cell, so the
> "nothing" value must be something no real cell can be. `-1` is outside `0..51`, it is given a name,
> and it never escapes the class: callers ask `isActive()`, and `onRoundCompleted()` reports a spawn as
> an `OptionalInt`. A primitive `int` also means the comparison `cell != previousCell` below is a plain
> number comparison — no boxing, and no `null` to guard against first.

```java
    /** True once the mystery cell is somewhere on the board. */
    public boolean isActive() {
        return currentCell != NO_CELL;
    }

    /** The standard-path cell it currently occupies. Only meaningful while {@link #isActive()}. */
    public int cell() {
        if (!isActive()) {
            throw new IllegalStateException("The mystery cell has not spawned yet");
        }
        return currentCell;
    }
```

`isActive()` names the sentinel check so nobody else ever writes `== -1`. `cell()` refuses to answer
while the cell is not on the board: returning `-1` would let a caller print "the mystery cell is at -1"
or compare a piece's cell against it, so asking at the wrong moment is turned into an immediate,
named exception instead. `GameLog.mysteryCellStatus` asks `isActive()` first, so in a real game it
never fires.

```java
    /** True when the given square is the mystery cell right now. */
    public boolean isOn(Square square) {
        return isActive() && square.isRing() && square.index() == currentCell;
    }
```

The question `MoveExecutor` asks after every move, and the one blue's strategy asks of every option.
Three conditions, in cheap-first order, and again the `isRing()` guard so a home-straight index is
never compared against a ring cell number.

```java
    public OptionalInt onRoundCompleted() {
        boolean piecesAreOnPath = board.hasAnyPieceOnRing();
        if (piecesWereOnPathAtRoundStart && piecesAreOnPath) {
            fullRoundsWithPiecesOnPath++;
        }
        piecesWereOnPathAtRoundStart = piecesAreOnPath;
```

The first condition, counted in **full** rounds. `onRoundCompleted` runs at the end of every round, so
the value it saves in `piecesWereOnPathAtRoundStart` is exactly "were there pieces on the path when
the *next* round started?". A round counts only when it both **started and ended** with a piece on
the path.

> **Why not simply count every round that ends with a piece on the path?**
> Because the round in which the first piece steps out of its base is only partly spent with a piece
> there — at its start the path was empty. Counting it would make the cell appear after one and a bit
> rounds, not "after two rounds have passed". With the full-round count, a piece that enters in
> round 1 makes rounds 2 and 3 the two that pass, and the cell spawns at the end of round 3. Seeded
> games show exactly that: in seeds 5, 6, 8, 9 and 10 the first piece leaves its base in round 1 and
> the first *"A mystery cell has spawned…"* line follows round 3.

```java
        if (isActive()) {
            roundsRemaining--;
            if (roundsRemaining > 0) {
                return OptionalInt.empty();
            }
            previousCell = currentCell;
            currentCell = NO_CELL;
            return spawn();
        }

        return fullRoundsWithPiecesOnPath >= ROUNDS_BEFORE_FIRST_SPAWN ? spawn() : OptionalInt.empty();
    }
```

The rest of the life cycle, readable top to bottom, called once per round by `LudoGame`.

- If the cell is already active, tick it down. Still alive → nothing to report. Expired → remember
  where it was (so Rule T-10's "not consecutively" can be enforced) and immediately spawn elsewhere,
  because the rule says it "will … reappear at another random location", not "will disappear".
- If it is not active, spawn as soon as two full rounds have passed.

Returning the newly spawned cell as an `OptionalInt` is how `LudoGame` knows whether to print *"A
mystery cell has spawned in location L1…"*: present means "it just spawned here", empty means "nothing
new this round". `OptionalInt` rather than `Optional<Integer>` because a cell is a primitive `int` —
no boxing.

> **Why return a value instead of logging from here?**
> `MysteryCell` would then need a `GameListener`, and a class whose job is "track where the mystery
> cell is" would acquire a second job, "announce itself". Returning the fact and letting the caller
> narrate keeps them separable — and it is why `MysteryCellTest` can assert the spawn timing with no
> listener at all.

```java
    private OptionalInt spawn() {
        List<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < BoardGeometry.RING_SIZE; cell++) {
            if (cell != previousCell && board.isRingCellEmpty(cell)) {
                candidates.add(cell);
            }
        }
        if (candidates.isEmpty()) {
            return OptionalInt.empty();
        }
        currentCell = randomSource.pick(candidates);
        roundsRemaining = LIFETIME_IN_ROUNDS;
        return OptionalInt.of(currentCell);
    }
```

Build the list of legal cells, then pick one. Both of Rule T-10's placement conditions are in the
`if`: not the previous cell, and not occupied. Before the first spawn `previousCell` is `NO_CELL`
(`-1`), which no loop value can equal, so the "not consecutively" test needs no special case.

> **Why build a candidate list instead of picking at random and retrying?**
> A retry loop can spin — in the extreme it never terminates — and its running time depends on luck.
> Filtering first is 52 cheap checks with a guaranteed answer, and it makes the impossible case
> ("every cell is occupied or forbidden") explicit rather than a hang. Sixteen pieces can never fill
> 52 cells, so in practice the list is never empty, but the code says what it would do.
>
> It also makes the *distribution* obviously uniform over legal cells, which is what "can occur
> randomly at any cell location" asks for.

### 7.3 `MysteryEffectResolver` — Rules T-11 to T-15

```java
public final class MysteryEffectResolver {

    private static final List<TeleportDestination> DESTINATIONS =
            List.of(TeleportDestination.values());
```

The six destinations as an immutable list, built once. `RandomSource.pick` needs a `List`, and
`values()` returns a fresh array on every call, so hoisting it into a constant avoids re-copying it on
every teleport.

```java
    public MysteryEffectResolver(Board board, RandomSource randomSource, GameListener log) {
```

Like every rule class, it is handed a `GameListener` rather than the `GameLog`, so
`MysteryEffectResolverTest` can verify that, say, `gammaSendsPieceToBeta(piece)` was raised without
reading any printed text.

```java
    /** Rule T-11: pick one of the six destinations at random and send the piece there. */
    public void resolveLandingOnMysteryCell(Piece piece) {
        TeleportDestination destination = randomSource.pick(DESTINATIONS);
        log.landsOnMysteryCell(piece, destination);
        teleport(piece, destination);
        applyDestinationEffect(piece, destination);
    }
```

Four lines, and **the only public method on the class**. That is deliberate, and it is how Rule T-15 is
enforced:

> "Effects of Alpha, Beta, and Gamma will only apply **if the piece is teleported via a mystery cell**.
> No effects will occur if a piece lands on such a cell without teleport." — Rule T-15

Because `applyDestinationEffect` is private and reachable only from here, a piece that simply *walks*
onto cell 7 cannot possibly trigger the Alpha aura. **The rule is enforced by the shape of the class
rather than by a flag that somebody has to remember to check.** That is the strongest kind of
guarantee available.

```java
    /** Moves the piece to a teleport destination without any of the walking rules applying. */
    private void teleport(Piece piece, TeleportDestination destination) {
        Square target = destination.squareFor(piece.colour());
        board.relocate(piece, target);
        log.teleported(piece, destination);

        if (destination == TeleportDestination.BASE) {
            // A piece in the base carries no direction and no history, exactly as after a capture
            // (Rule T-9); it will be tossed a fresh coin when it next steps out onto "X".
            piece.resetAfterCapture();
        } else if (target.isApproachCellOf(piece.colour())) {
            // Arriving on the approach cell counts as a visit for Rule T-1, however the piece got
            // there, so a teleport to "Approach" is not silently wasted.
            piece.recordApproachPass();
        }
    }
```

A teleport is **not** a move: it does not walk, so no block can stop it and — importantly — nothing is
captured. Two interpretations are recorded here:

1. **Teleporting to Base resets the piece.** A piece in a base has no direction (it has not been tossed
   for), so leaving a stale direction on it would be an inconsistent state. Rule T-9 already describes
   exactly this reset for a captured piece, so the same treatment applies.
2. **Teleporting to Approach counts as an approach visit.** Without this line, destination 6 of Rule
   T-11 would often be *worthless*: a clockwise piece dumped on its approach cell with a pass count of
   zero would fail Rule T-1's check and walk straight past its own doorway. Counting the arrival makes
   the reward a reward.

Both are listed in `REPORT.md` §6 as documented interpretations.

```java
    private void applyDestinationEffect(Piece piece, TeleportDestination destination) {
        switch (destination) {
            case ALPHA -> applyAlphaAura(piece);
            case BETA -> applyBetaBriefing(piece);
            case GAMMA -> applyGammaClarification(piece);
            case BASE, START, APPROACH -> {
                // Rule T-11 destinations 4, 5 and 6 relocate the piece but leave no lasting effect.
            }
        }
    }
```

Three of the six have consequences; three are pure relocation. The empty branch names all three
explicitly rather than using `default`, so a seventh destination would be a compile error here rather
than silently falling into "no effect".

```java
    /** Rule T-12: "the piece may get energised by the aura or get sick due to the aura." */
    private void applyAlphaAura(Piece piece) {
        SpeedModifier modifier =
                randomSource.nextBoolean() ? SpeedModifier.DOUBLED : SpeedModifier.HALVED;
        piece.effects().applyAlphaAura(modifier);
        log.alphaAura(piece, modifier);
    }
```

Rule T-12 says "may get energised … or get sick" without stating odds, so a fair coin is used. The
arithmetic itself is already on the `SpeedModifier` constants.

```java
    /** Rule T-13: the piece has to attend a briefing and cannot move for the next four rounds. */
    private void applyBetaBriefing(Piece piece) {
        piece.effects().beginBriefing();
        log.betaBriefing(piece);
    }
```

Note what is **not** here: the escape clause. Rule T-13's second half — *"the piece will be teleported
to the base if the player rolls value three consecutively"* — is about rolls that have not happened
yet. `beginBriefing` only opens the window (and zeroes the run of threes); the piece then counts the
player's rolls itself in `PieceEffects.observeRoll` (section 4.2), and `TurnEngine` sends it home when
`mustLeaveBriefingForBase()` says so (section 9.2).

```java
    private void applyGammaClarification(Piece piece) {
        if (piece.direction() == Direction.CLOCKWISE) {
            piece.setDirection(Direction.COUNTER_CLOCKWISE);
            log.gammaTurnedPieceAround(piece);
            return;
        }
        log.gammaSendsPieceToBeta(piece);
        teleport(piece, TeleportDestination.BETA);
        applyBetaBriefing(piece);
    }
```

Rule T-14, both halves:

> "if it is moving in a clockwise direction, it will change its direction to counterclockwise. If it
> were moving in the counterclockwise direction, it would be teleported to Beta." — Rule T-14

The second branch **re-enters** `teleport` and then applies the Beta effect — so a counter-clockwise
piece that lands on the mystery cell and draws Gamma ends up at cell 25, frozen for four rounds. The
transcript shows the whole chain, which is why both messages are printed:

```
green player lands on a mystery cell and is teleported to Gamma.
green piece G3 teleported to Gamma.
The green piece G3 is moving in a counterclockwise direction. Teleporting to Beta from Gamma.
green piece G3 teleported to Beta.
green piece G3 attends briefing and cannot move for four rounds.
```

The recursion is bounded: `BETA` is not `GAMMA`, so the second `teleport` cannot trigger a third.

---

## 8. Package `ludot.player`

Six classes: one abstract base, four behaviours, one factory. This is where Section 2.1 of the
specification lives.

### 8.1 `Player` — the base class

#### The Template Method

```java
public abstract class Player {

    private final PieceColour colour;
    protected final Board board;
    protected final PathResolver pathResolver;
```

`colour` is `private` (nobody needs to change it), while `board` and `pathResolver` are `protected`
because the subclasses' helpers need them. There is no other state: three of the four behaviours are
completely stateless, and the one that is not (blue) keeps its own memory.

```java
    public final Optional<PlannedMove> chooseMove(MoveOptions options, int rollValue) {
        List<PlannedMove> playable = options.playableMoves();
        if (playable.isEmpty()) {
            return Optional.empty();
        }
        PlannedMove chosen = selectMove(playable, rollValue)
                .filter(playable::contains)
                .orElse(playable.get(0));
        return Optional.of(chosen);
    }

    /**
     * The behaviour of this colour: choose one of the legal moves, which is never an empty list.
     * Returning empty means "no preference", and the first legal move is played instead.
     */
    protected abstract Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue);
```

This pair is the **Template Method pattern**, and the `final` on `chooseMove` is the whole point.

`chooseMove` fixes three invariants that must hold for every behaviour:

1. **Never ask a behaviour to choose from nothing.** If `playable` is empty it returns
   `Optional.empty()` at once, so no `selectMove` implementation has to handle an empty list — and
   none can crash on `get(0)`. The empty `Optional` is how `TurnEngine` learns that the roll cannot
   be played normally.
2. **Never play an illegal move.** `.filter(playable::contains)` discards any answer that is not one
   of the legal moves it was given, so even a buggy behaviour cannot smuggle in a move the rules did
   not approve.
3. **Never waste a roll.** If a behaviour returns empty — "no preference", or a filter chain that
   eliminated everything — `.orElse(playable.get(0))` plays a legal move anyway.

Because `chooseMove` is `final`, a subclass **cannot** bypass those invariants. Both methods return
`Optional`, so "I have no move" is part of the type rather than a `null` that a caller could forget to
check.

> **Why Template Method rather than a `MoveStrategy` interface held by a concrete `Player`?**
> Both are defensible; this one was chosen because the four behaviours also need shared *vocabulary* —
> `capturingMoves`, `formsNewBlock`, `closestToHome` and the rest. With a separate strategy interface
> those helpers would have to live in a utility class and be passed the board and the path resolver on
> every call. As a base class they are simply `protected` methods, and each behaviour reads like the
> paragraph it implements. The polymorphism is the same either way.

```java
    /** Hook for behaviours that keep state between turns; blue uses it to follow its cycle. */
    public void onMoveExecuted(PlannedMove move) {
        // Most behaviours are stateless and have nothing to remember.
    }

    /** Hook called once at the end of every round; blue uses it to advance its cycle. */
    public void onRoundCompleted() {
        // Most behaviours are stateless and have nothing to remember.
    }
```

Two **no-op hooks**, not abstract. Three of the four behaviours are stateless, so making these
abstract would force six empty overrides. `TurnEngine` calls `onMoveExecuted` after every move a
player makes, and `LudoGame` calls `onRoundCompleted` once per player at the end of every round; only
`BluePlayer` overrides them, because blue's cycle is defined in terms of *rounds* (section 8.5).

#### The shared vocabulary

```java
    /** Moves that send at least one opponent piece back to its base (Rules 6 and T-8). */
    protected final List<PlannedMove> capturingMoves(List<PlannedMove> options) {
        return options.stream().filter(PlannedMove::capturesAnything).toList();
    }

    /** Rule T-7: captures made by a piece that has not yet earned entry to its home straight. */
    protected final List<PlannedMove> capturesNeededForHomeStraight(List<PlannedMove> options) {
        return capturingMoves(options).stream()
                .filter(move -> !move.primaryPiece().hasEarnedHomeStraightEntry())
                .toList();
    }

    /** The move that lifts a piece out of the base onto "X", if a six made one available. */
    protected final Optional<PlannedMove> enterBoardMove(List<PlannedMove> options) {
        return options.stream().filter(PlannedMove::isEnteringBoard).findFirst();
    }

    /** Moves in which a whole block travels together (Rule T-4). */
    protected final List<PlannedMove> blockMoves(List<PlannedMove> options) {
        return options.stream().filter(PlannedMove::isBlockMove).toList();
    }
```

Small filters that let the behaviours read as prose: `RedPlayer` says `capturingMoves(options)`, not
`options.stream().filter(...)`. All are `final` so no behaviour can redefine what "a capturing move"
means.

`capturesNeededForHomeStraight` is the phrase that yellow **and** green share: both "will not look to
capture opponent pieces more than what is required to enter the home straight". Under Rule T-7 a piece
needs exactly one capture to enter its home straight, so the only captures either of them wants are
captures made by a piece that has **not** captured yet. Writing that once, here, means the two
behaviours cannot drift apart on what "required" means.

`enterBoardMove` returns an `Optional` single move rather than a list because the generator only ever
produces one (the base pieces are interchangeable), and it may produce none.

```java
    /**
     * True when a single piece arrives on a cell already holding one of this player's pieces, so a
     * block (Rule T-3) exists afterwards that did not exist before. Moving an existing block along
     * does not count as forming one.
     */
    protected final boolean formsNewBlock(PlannedMove move) {
        if (move.isBlockMove()) {
            return false;
        }
        Square destination = move.destination();
        if (!destination.isRing()) {
            return false;
        }
        return board.groupOn(destination, colour).stream()
                .anyMatch(piece -> !move.movedPieces().contains(piece));
    }

    /** True when the moved piece or pieces stand in a block once the move is over. */
    protected final boolean endsInBlock(PlannedMove move) {
        return move.isBlockMove() || formsNewBlock(move);
    }
```

Two related questions, deliberately kept apart because the specification asks both.

`formsNewBlock` is green's question — *"does this move **create** a block?"*. Its three parts:

- a block move is **not** forming a block: the block already existed and is only travelling. (Treating
  it as "forming" one would let green's top priority be satisfied by simply shuffling an existing
  block, which would wrongly outrank emptying the base on a six.)
- a home straight cannot hold a block that matters — it is private to one colour, and no opponent can
  ever be obstructed there — so `!destination.isRing()` returns `false`;
- otherwise, look at my own pieces already standing on the destination and ask whether any of them is
  **not** part of this move. If one is staying put while another arrives, that is a new block.

That last check is the subtle one. Without `!move.movedPieces().contains(piece)`, a piece would see
itself at the destination and wrongly report a block.

`endsInBlock` is red's question — *"will my pieces be standing in a block after this move?"*. Red
"will always avoid creating blocks", and a block move ends in a block just as surely as forming one
does, so for red both count.

```java
    /** True when this move takes a single piece out of an existing block, breaking it up. */
    protected final boolean movesPieceOutOfBlock(PlannedMove move) {
        return !move.isBlockMove() && !move.isEnteringBoard()
                && board.isPartOfBlock(move.primaryPiece());
    }
```

The inverse question, for green. A block move keeps the block together, and an enter-board move starts
from the base, so neither breaks anything; only a lone piece walking away from a block does.

```java
    protected final Optional<PlannedMove> closestToHome(List<PlannedMove> options) {
        return options.stream()
                .min(Comparator.comparingInt(move -> pathResolver.distanceToHome(move.primaryPiece())));
    }
```

Yellow's *"moves the piece closest to its home"*, and the tie-break every other behaviour falls back
on. Three details:

- it returns an **empty `Optional`** for an empty list, which is what makes the "try this, else try
  that" chains in the behaviours read cleanly — each step tests `isPresent()` and falls through;
- `Stream.min` keeps the **first** of several equal minimums, so on a tie the earlier option wins,
  and the option order is itself deterministic because it comes from `piecesInPlay` in `R1..R4` order;
- a move whose piece is `UNREACHABLE` (for instance an enter-board move, whose piece is still in its
  base) is still a candidate — `Integer.MAX_VALUE` just sorts it last — so a list of only such moves
  still yields a move.

### 8.2 `RedPlayer` — aggressive

> "The red player is a **very aggressive** player who prioritises capturing opponent pieces rather than
> winning the game." — Section 2.1.1

```java
    @Override
    protected Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue) {
        List<PlannedMove> captures = capturingMoves(options);
        if (!captures.isEmpty()) {
            return mostDamagingCapture(captures);
        }
```

**Step 1 — capture above all else.** Rule: *"if any opponent piece can be captured by moving the
specified number of cells in the dice, the red player would prioritise capturing the opponent piece"*.
Nothing outranks this, not even a six that could bring a new piece out.

```java
        // "Red will always keep one piece in the standard path and will not take another piece to
        // the path from the base unless it cannot capture any piece by moving six cells."
        // Reaching this point means no capture is possible with this roll, so the six is used to
        // bring a piece out.
        Optional<PlannedMove> enterBoard = enterBoardMove(options);
        if (enterBoard.isPresent()) {
            return enterBoard;
        }
```

**Step 2 — the base, but only as a fall-back.** This is where the control flow *is* the rule. The
specification says red brings a piece out only *"unless it cannot capture any piece by moving six
cells"* — and "cannot capture" is precisely the condition of having fallen through step 1. So the
ordering of the two `if`s encodes the rule with no extra test.

```java
        // "Red will always avoid creating blocks unless it is unavoidable."
        List<PlannedMove> withoutBlocks = options.stream()
                .filter(move -> !endsInBlock(move))
                .toList();
        return closestToHome(withoutBlocks.isEmpty() ? options : withoutBlocks);
    }
```

**Step 3 — avoid blocks "unless it is unavoidable".** Filter out every move that would leave red's
pieces standing in a block — forming a new one *or* moving an existing one along, which is why this
uses `endsInBlock` rather than `formsNewBlock`. If that empties the list then blocking genuinely *is*
unavoidable, so fall back to the unfiltered list. The `isEmpty() ? options : filtered` idiom is how
"unless unavoidable" is expressed throughout this codebase.

```java
    private Optional<PlannedMove> mostDamagingCapture(List<PlannedMove> captures) {
        return captures.stream().min(Comparator.comparingInt(this::shortestVictimDistanceToHome));
    }

    private int shortestVictimDistanceToHome(PlannedMove move) {
        return move.capturedPieces().stream()
                .mapToInt(pathResolver::distanceToHome)
                .min()
                .orElse(PathResolver.UNREACHABLE);
    }
```

> "If more than one piece can be captured by moving different red pieces, red prioritises capturing
> **the opponent piece closest to its home**." — Section 2.1.1

"Its home" means the *victim's* home, so red targets the opponent that has made the most progress —
the capture that destroys the most work. `distanceToHome` measures that, and the smallest distance is
the most advanced victim.

`shortestVictimDistanceToHome` takes the minimum across a move's victims, because one move can capture
more than one piece (a blockade capture under Rule T-8, or two different colours sharing a cell after
teleports). A move is judged by the best victim it can reach. As with `closestToHome`, `Stream.min`
keeps the first of equal candidates.

### 8.3 `GreenPlayer` — the blocker

> "The green player prioritises **winning by blocking**. It will not look to capture opponent pieces
> more than what is required to enter the home straight." — Section 2.1.2

Green has the longest preference ladder — six levels — because the specification gives it four
interacting sentences.

```java
    @Override
    protected Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue) {
        Optional<PlannedMove> newBlock = closestToHome(options.stream()
                .filter(this::formsNewBlock)
                .filter(move -> !movesPieceOutOfBlock(move))
                .toList());
        if (newBlock.isPresent()) {
            return newBlock;
        }
```

**Level 1 — form a new block.** This outranks even emptying the base, and that ordering comes straight
from the rule's own wording:

> "any pieces in the base will be moved to X whenever a six is thrown, if there are any pieces in the
> base **unless moving six cells enables green to create a block**." — Section 2.1.2

The `unless` clause makes block *creation* the higher priority, so it is tested first. Two filters
make "create" mean exactly that:

- `formsNewBlock` excludes block moves — moving an existing block along creates nothing new;
- `!movesPieceOutOfBlock` excludes a piece that would leave one block to form another, which merely
  trades a block for a block (and would break one, which green does only as a last resort).

```java
        Optional<PlannedMove> enterBoard = enterBoardMove(options);
        if (enterBoard.isPresent()) {
            return enterBoard;
        }
```

**Level 2 — keep the base empty.** The main clause of the same sentence, reached only when level 1
found nothing: on a six, a piece comes out of the base.

```java
        Optional<PlannedMove> blockMove = closestToHome(blockMoves(options));
        if (blockMove.isPresent()) {
            return blockMove;
        }
```

**Level 3 — move a whole block forward.** *"Green always attempts to move forward using the block move
explained in Rule T-4."* Moving the block keeps it intact while still making progress, which is
exactly green's strategy.

> **Why must level 2 come before level 3?** Because the base sentence says "**whenever** a six is
> thrown". If moving an existing block were tested first, a six would be spent shuffling the block
> two or three cells along while pieces sat in the base — the opposite of "always likes to keep an
> empty base". `GreenPlayerTest.movingAnExistingBlockIsNotMistakenForFormingANewOne` pins this down.

```java
        Optional<PlannedMove> neededCapture = closestToHome(capturesNeededForHomeStraight(options)
                .stream()
                .filter(move -> !movesPieceOutOfBlock(move))
                .toList());
        if (neededCapture.isPresent()) {
            return neededCapture;
        }
```

**Level 4 — the one capture green wants.** *"It will not look to capture opponent pieces more than
what is required to enter the home straight."* Read the other way round, green **does** look for the
capture that *is* required: a piece that has not captured yet can never enter its home straight
(Rule T-7), so without it green could never win. The shared `capturesNeededForHomeStraight` helper
keeps only captures by such pieces — a piece that already has its capture ignores them — and the
extra filter refuses to break a block to make one.

```java
        List<PlannedMove> keepingBlocksIntact = options.stream()
                .filter(move -> !movesPieceOutOfBlock(move))
                .toList();
        if (!keepingBlocksIntact.isEmpty()) {
            return closestToHome(keepingBlocksIntact);
        }

        // Every remaining option breaks a block, which the specification permits only when "the
        // value of the roll cannot be performed by green using the pieces in front of the block".
        return closestToHome(options);
    }
```

**Level 5 — move something that is not in a block.** *"Green always prioritises moving its other
pieces home before breaking a block."*

**Level 6 — break a block, but only now.** *"Green will only break a block … if and only if the value
of the roll cannot be performed by green using the pieces in front of the block."* Reaching this line
means every single option breaks a block, i.e. the roll cannot be played any other way — which is the
rule's condition, established by exhaustion rather than by a separate test.

> **Does this actually behave like a blocker?** Measurably yes. Over 40 seeded games (seeds 1–40)
> green moved blocks **3055** times; red, yellow and blue managed 45, 54 and 33 respectively. And
> because level 4 lets it take the captures it needs, green also made 607 captures, second only to
> red's 633. The ladder produces the intended personality.

### 8.4 `YellowPlayer` — the racer

> "The yellow player **always prioritises winning**. It will not look to capture opponent pieces more
> than what is required to enter the home straight." — Section 2.1.3

```java
    @Override
    protected Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue) {
        // "Yellow always like to keep an empty base. Therefore, anytime a six is thrown, if there
        // are any pieces in the base, they will be moved to X."
        Optional<PlannedMove> enterBoard = enterBoardMove(options);
        if (enterBoard.isPresent()) {
            return enterBoard;
        }
```

**Step 1 — the base, unconditionally.** Note the contrast with green: yellow's rule has no *unless*
clause, so the enter-board move is first with no test in front of it. The two behaviours differ by
exactly the ordering of their `if` statements, which is what the specification differs by.

```java
        // "Yellow will prioritise the pieces that need captures first to see whether any opponent
        // piece is within range. If such a piece is within range then the capture will take place."
        List<PlannedMove> capturesThatUnlockHome = capturesNeededForHomeStraight(options);
        if (!capturesThatUnlockHome.isEmpty()) {
            return closestToHome(capturesThatUnlockHome);
        }
```

**Step 2 — capture, but only where Rule T-7 requires it.** This is the interesting half of yellow.

> "Yellow will prioritise **the pieces that need captures first** to see whether any opponent piece is
> within range." — Section 2.1.3

Combined with *"will not look to capture … more than what is required to enter the home straight"*,
the filter is: only consider captures made by pieces that have **not yet** captured. A yellow piece
that already has its Rule T-7 ticket ignores captures entirely and just runs. The filter is the shared
`capturesNeededForHomeStraight` from section 8.1 — the same one green uses, because the two colours'
specifications use the same sentence.

```java
        // "In case no captures could be done, Yellow moves the piece closest to its home by the
        // number specified in the roll."
        return closestToHome(options);
    }
```

**Step 3 — pure progress.**

### 8.5 `BluePlayer` — the cyclic mystery-chaser

> "The blue player is a **random player that prioritises mystery cells**." — Section 2.1.4

Blue is the only behaviour that remembers anything between turns, and the specification gives it three
sentences:

> - "The blue player always moves in a cyclic manner. That is, **if B1 is moved in the current round,
>   B2 is considered in the next** and so on."
> - "**If the piece to be moved is movable**, the blue player prioritizes landing on the mystery cell if
>   it is moving counterclockwise."
> - "**If the piece to be moved is movable**, the blue player prioritizes avoiding landing on the
>   mystery cell if it is moving clockwise."

Two readings follow directly from the wording. The cycle is measured in **rounds** ("in the current
round … in the next"), not in moves. And the two mystery-cell sentences are about **"the piece to be
moved"** — the piece the cycle has reached — not about any blue piece anywhere on the board.

```java
public final class BluePlayer extends Player {

    private static final int NO_PIECE = 0;

    private final MysteryCell mysteryCell;

    /** Number (1..4) of the piece blue considers first this round. */
    private int scheduledPieceNumber = 1;

    /** Number of the first piece blue moved this round, or {@link #NO_PIECE}. */
    private int firstPieceMovedThisRound = NO_PIECE;
```

Two pieces of state. `scheduledPieceNumber` is the piece blue considers first, and it is **fixed for a
whole round** — even when a six gives blue several rolls in one turn, every roll starts from the same
piece. `firstPieceMovedThisRound` records which piece blue actually moved first, because that is what
decides the next round. `NO_PIECE = 0` is a safe sentinel: piece numbers run from 1 to 4.

`mysteryCell` is injected — blue is the only behaviour that needs to know where it is, which is why
`PlayerFactory` passes it to `BluePlayer` alone.

```java
    @Override
    protected Optional<PlannedMove> selectMove(List<PlannedMove> options, int rollValue) {
        for (int offset = 0; offset < BoardGeometry.PIECES_PER_PLAYER; offset++) {
            List<PlannedMove> movesOfPiece = movesOfPiece(options, pieceNumberAt(offset));
            Optional<PlannedMove> choice = preferredMoveOf(movesOfPiece);
            if (choice.isPresent()) {
                return choice;
            }
        }
        // Every movable piece is a clockwise piece that could only land on the mystery cell.
        return firstMoveInCycle(options);
    }
```

Walk the cycle starting from the scheduled piece; for each piece, ask what blue would like to do with
it; take the first piece that has an answer. A piece with no legal move gives no answer and the cycle
simply moves on — *"if the piece to be moved is movable"* is a real precondition. Only if **every**
piece declined does the last line give up the mystery-cell dodge (see below).

```java
    @Override
    public void onMoveExecuted(PlannedMove move) {
        if (firstPieceMovedThisRound == NO_PIECE) {
            firstPieceMovedThisRound = move.primaryPiece().number();
        }
    }

    /** "if B1 is moved in the current round, B2 is considered in the next and so on." */
    @Override
    public void onRoundCompleted() {
        if (firstPieceMovedThisRound != NO_PIECE) {
            scheduledPieceNumber = firstPieceMovedThisRound % BoardGeometry.PIECES_PER_PLAYER + 1;
            firstPieceMovedThisRound = NO_PIECE;
        }
    }
```

The two hooks from section 8.1, and together they are the cycle.

- `onMoveExecuted` runs after every blue move, but only the **first** move of the round is remembered
  — "if B1 is moved in the current round" is about the piece blue moved, and later bonus rolls in the
  same round do not change it.
- `onRoundCompleted` runs once at the end of the round (`LudoGame` calls it for every player). It sets
  the next round's first choice to the piece *after* the one moved: `movedNumber % 4 + 1` maps 1→2,
  2→3, 3→4 and **4→1**, so the cycle wraps. If blue moved nothing at all this round — every roll was
  unusable — the schedule stays where it is, because no piece "was moved in the current round".

> **Why advance from the piece that moved rather than just adding one to the schedule?**
> Because the scheduled piece is not always movable. If B1 is scheduled but still in its base and the
> roll is not a six, blue moves B2 instead — and then the specification's own sentence ("if B1 is
> moved …, B2 is considered in the next") says the piece after the one *moved*, B3, is the one to
> consider in the next round. Advancing from what was actually moved keeps the cycle honest.
> `BluePlayerTest.anImmovablePieceIsSkippedAndTheCycleContinuesAfterTheOneMoved` checks exactly this.

```java
    /** The piece blue will consider first in the current round (exposed for the tests). */
    int scheduledPieceNumber() {
        return scheduledPieceNumber;
    }
```

Package-private, for `BluePlayerTest` (which is in the same package) to observe the cycle directly.

```java
    private Optional<PlannedMove> preferredMoveOf(List<PlannedMove> movesOfPiece) {
        if (movesOfPiece.isEmpty()) {
            return Optional.empty();
        }
        Direction direction = movesOfPiece.get(0).primaryPiece().direction();
        if (direction == Direction.COUNTER_CLOCKWISE) {
            return Optional.of(movesOfPiece.stream()
                    .filter(this::landsOnMysteryCell)
                    .findFirst()
                    .orElse(movesOfPiece.get(0)));
        }
        if (direction == Direction.CLOCKWISE) {
            return movesOfPiece.stream().filter(move -> !landsOnMysteryCell(move)).findFirst();
        }
        // A piece still in its base has no direction until its coin is tossed on "X".
        return Optional.of(movesOfPiece.get(0));
    }
```

The two mystery-cell sentences, applied to **one** piece — the piece the cycle has reached:

- **no moves** → empty: the piece is not movable, so the cycle moves on;
- **counter-clockwise** → the piece *prioritises landing on* the mystery cell, so among its own moves
  (a piece can have two: on its own and as part of a block) the one that lands there wins, and
  otherwise its first move is played. It always gives an answer — the craving is a preference, not a
  condition;
- **clockwise** → the piece *prioritises avoiding* the mystery cell, so only its moves that do **not**
  land there are acceptable. If every one of its moves lands there, the result is empty and the cycle
  skips to the next piece;
- **no direction** → a piece in its base (an enter-board move); it has no direction to have a
  preference about, so its move is taken.

The direction comparisons use `==` against enum constants, which is also null-safe: a base piece's
`null` direction simply matches neither.

```java
    private Optional<PlannedMove> firstMoveInCycle(List<PlannedMove> options) {
        for (int offset = 0; offset < BoardGeometry.PIECES_PER_PLAYER; offset++) {
            List<PlannedMove> movesOfPiece = movesOfPiece(options, pieceNumberAt(offset));
            if (!movesOfPiece.isEmpty()) {
                return Optional.of(movesOfPiece.get(0));
            }
        }
        return Optional.empty();
    }
```

The fall-back for the one case `selectMove` cannot settle: every movable piece is clockwise and every
move it has would land on the mystery cell. Blue then gives up the dodge rather than waste the roll,
and plays the first move in cycle order — the aversion is a preference, never a reason to forfeit a
turn.

```java
    private int pieceNumberAt(int offsetInCycle) {
        return (scheduledPieceNumber - 1 + offsetInCycle) % BoardGeometry.PIECES_PER_PLAYER + 1;
    }

    private List<PlannedMove> movesOfPiece(List<PlannedMove> options, int pieceNumber) {
        return options.stream()
                .filter(move -> move.movedPieces().stream()
                        .anyMatch(piece -> piece.number() == pieceNumber))
                .toList();
    }

    private boolean landsOnMysteryCell(PlannedMove move) {
        return mysteryCell.isOn(move.destination());
    }
}
```

`pieceNumberAt` converts between 1-based piece numbers and 0-based offsets: subtract 1, add the
offset, wrap on 4, add 1 back. With `scheduledPieceNumber = 3` it visits 3, 4, 1, 2.

`movesOfPiece` uses `movedPieces()` rather than just `primaryPiece()`, so a **block move** counts as a
move of every piece in the block. Blue's cycle is satisfied by B2 moving as part of a block, not only
by B2 moving alone.

`landsOnMysteryCell` delegates to `MysteryCell.isOn`, which already checks that the cell is active and
that the destination is a ring cell — so for the first few rounds, before any mystery cell exists,
blue behaves as a plain cyclic player.

### 8.6 `PlayerFactory`

```java
    public Player create(PieceColour colour) {
        return switch (colour) {
            case RED -> new RedPlayer(board, pathResolver);
            case GREEN -> new GreenPlayer(board, pathResolver);
            case YELLOW -> new YellowPlayer(board, pathResolver);
            case BLUE -> new BluePlayer(board, pathResolver, mysteryCell);
        };
    }

    /** All four players, in the fixed board order yellow, blue, red, green. */
    public List<Player> createAll() {
        List<Player> players = new ArrayList<>();
        for (PieceColour colour : PieceColour.values()) {
            players.add(create(colour));
        }
        return players;
    }
```

The **whole** "which colour behaves how" mapping, in one switch. `TurnEngine` and `LudoGame` only ever
see `Player`, so neither knows that red hunts captures — that is the Liskov Substitution Principle
doing real work.

Note that only `BluePlayer` receives `mysteryCell`: dependencies are given to the classes that
actually need them, rather than handing everything to everyone.

The switch has no `default`, so adding a fifth colour to `PieceColour` would be a **compile error here
until a behaviour is provided** — the compiler enforcing completeness instead of a runtime surprise.

---

## 9. Package `ludot.game`

Four classes: the numeric rules, the opening roll-off, one turn, and the whole game.

### 9.1 `GameRules` — the numbers and the honest admissions

```java
public final class GameRules {

    /**
     * Rule 4: "if a six is rolled for the third consecutive time, the roll is ignored, and the dice
     * passes to the next player."
     */
    public static final int MAX_CONSECUTIVE_SIXES = 3;
```

Rule 4's limit, quoted.

```java
    /**
     * Rule T-6: a blockade is broken by moving its pieces "in their original direction by six units
     * cumulatively". Cumulatively means the six units are shared out between the pieces that move.
     */
    public static final int BLOCKADE_BREAK_UNITS = 6;

    /**
     * How the {@link #BLOCKADE_BREAK_UNITS} are shared out, indexed by the number of pieces leaving
     * the blockade minus one: one piece takes all 6, two take 4 and 2, three take 3, 2 and 1.
     *
     * <p><b>Interpretation.</b> An equal split would defeat the rule: two pieces moving 3 each from
     * the same cell in the same direction land on the same cell again and simply re-form the
     * blockade one step further on. Every share here is different, so the pieces always separate,
     * and each list still adds up to six.
     */
    public static final List<List<Integer>> BLOCKADE_BREAK_SHARES =
            List.of(List.of(6), List.of(4, 2), List.of(3, 2, 1));
```

**This is the honest admission**, written next to the constant it explains so an examiner who reads
the rule differently can change the interpretation in one line; it is cross-referenced from
`REPORT.md` §6.

"Six units cumulatively" says the six are a *total* for the pieces that move, not six each. The
obvious way to share six — equally — fails the rule's own purpose. Take a blockade of three on cell 26
with every piece moving clockwise: two pieces leave, and an equal split moves each 3 cells, so both
land on cell 29 together and the "broken" blockade is standing one step further on. The shares here
are all different, so the leaving pieces always end on different cells: `[4, 2]` puts them on 30 and
28 (seed 1 in section 12 shows exactly this), `[3, 2, 1]` spreads three of them over three cells.
Every list still adds up to `BLOCKADE_BREAK_UNITS`.

The list is indexed by "pieces leaving minus one" because a blockade of *n* pieces always keeps one
("removing all pieces, baring one"), so 1, 2 or 3 pieces leave a blockade of 2, 3 or 4.

The other interpretation that used to live here, Rule T-13's "two threes", now sits with the rest of
the briefing rules in `PieceEffects` (section 4.2), so each admission is next to the code it governs.

```java
    /**
     * A safety net rather than a rule. Rule&nbsp;T-7 only lets a piece enter its home straight after
     * it has captured an opponent, so an unlucky run of dice can keep a simulation going for a very
     * long time. The limit guarantees the program always terminates and says so when it stops.
     */
    public static final int MAX_ROUNDS = 2000;

    /**
     * Gridlock detection. Rule T-3 lets blocks stop every opponent, so blocks of different colours
     * on neighbouring cells can leave no legal move for anybody, and even a Rule T-6 break-up cannot
     * get past them. When no piece has changed square for this many consecutive rounds the board is
     * treated as gridlocked and the game ends with the places decided so far. Fifty rounds is far
     * longer than any temporary hold-up (a Beta briefing lasts at most five rounds).
     */
    public static final int GRIDLOCK_ROUNDS = 50;

    /**
     * Another safety net. Rules 4 and T-2 both grant extra rolls, and although a chain of captures
     * is naturally limited by the twelve opponent pieces on the board, a hard cap makes it
     * impossible for one turn to run away.
     */
    public static final int MAX_ROLLS_PER_TURN = 24;

    /** Places 1st to 3rd decide the game; the remaining player is last by elimination. */
    public static final int PLACES_TO_DECIDE = 3;
```

The two safety nets are labelled as such, so nobody mistakes them for rules.

`GRIDLOCK_ROUNDS` is the third admission, and the one that matters in practice: the block rules can
genuinely deadlock (section 14), and the rule book has no provision for it. Rather than invent a rule
that lets a piece through, the program recognises the situation — no piece has changed square for
50 rounds in a row — and ends the game honestly. The threshold is deliberately generous: the longest
legitimate pause in the rules is a Beta briefing (the rest of its round plus four), so fifty still
rounds cannot be mistaken for a temporary hold-up. Measured over 200 seeded games (seeds 1–200), six
games end this way (seeds 79, 121, 126, 162, 179 and 181) and **none** reaches `MAX_ROUNDS`, which
stays as the last safety net.

`PLACES_TO_DECIDE = 3` is Rule 11 counted carefully: once three players have brought all their pieces
home, the fourth can only be fourth, so the game has nothing left to decide.

### 9.2 `TurnEngine` — one turn, and every extra roll

A turn is *not* one roll. Rule 4 grants extra rolls for sixes, Rule T-2 grants one for every capture,
and Rule T-6 turns the third six into a forced blockade break-up.

```java
    private final Board board;
    private final Dice dice;
    private final MoveGenerator moveGenerator;
    private final MoveExecutor moveExecutor;
    private final PathResolver pathResolver;
    private final GameListener log;
```

Six collaborators, all handed in by the constructor. As everywhere in the rule engine, `log` is a
`GameListener`; `TurnEngineTest` passes a Mockito mock for it, and a mocked `Dice` whose `roll()` is
scripted with `when(dice.roll()).thenReturn(6, 6, 6)`, so a whole turn can be replayed roll by roll.

```java
    public void playTurn(Player player) {
        int consecutiveSixes = 0;

        for (int rollNumber = 1; rollNumber <= GameRules.MAX_ROLLS_PER_TURN; rollNumber++) {
            int value = dice.roll();
            log.diceRolled(player.colour(), value);

            releaseBriefedPiecesOnConsecutiveThrees(player, value);
```

The loop is bounded by `MAX_ROLLS_PER_TURN` rather than being a `while (true)`, so no turn can run
away. `consecutiveSixes` is a **local**, which is exactly right: Rule 4's streak is per turn, and it
resets naturally when the method returns.

Every roll is shown to the Rule T-13 check **before** anything else happens with it, so a second three
in a row frees a briefed piece before the move for that three is chosen.

```java
            if (value == Dice.SIX) {
                consecutiveSixes++;
                if (consecutiveSixes == GameRules.MAX_CONSECUTIVE_SIXES) {
                    handleThirdConsecutiveSix(player);
                    return;
                }
            } else {
                consecutiveSixes = 0;
            }
```

Rule 4's counter. The third six **ends the turn immediately** (`return`) without playing a move —
*"the roll is ignored, and the dice passes to the next player"*. The `else` resets the streak on any
other value, which is what "consecutive" means.

Note the order: the third-six check happens **before** the move is played, so the ignored roll really
is ignored.

```java
            boolean captured = playSingleRoll(player, value);
            if (captured) {
                // Rule T-2: "allowing the capturing player another roll as a bonus for capturing".
                log.captureEarnsAnotherRoll(player.colour());
            }
            boolean earnedAnotherRoll = value == Dice.SIX || captured;
            if (!earnedAnotherRoll || board.hasAllPiecesHome(player.colour())) {
                // A player whose last piece has just reached home has nothing left to roll for.
                return;
            }
        }
    }
```

The turn continues on two conditions, one from each rule: a six (Rule 4) **or** a capture (Rule T-2).
Anything else ends the turn.

The second half of the `if` is the end of a player's game. If this roll carried the player's **last**
piece home, the player has finished (Rule 11) and has nothing left to move — so even a six or a
capture does not earn another roll. Without that check the program would go on printing rolls for a
player who has already won.

> **Why is a capture-bonus roll able to reset the six streak?**
> Because it is a genuine roll of the dice. If a player rolls 6, then captures with the bonus roll and
> rolls a 2, the streak is broken — the next 6 starts counting from one again. That follows from
> reading "consecutive" as "consecutive rolls", which is the only reading available.

```java
    private boolean playSingleRoll(Player player, int value) {
        MoveOptions options = moveGenerator.optionsFor(player.colour(), value);
        Optional<PlannedMove> chosen = player.chooseMove(options, value);
        if (chosen.isPresent()) {
            return applyMove(player, chosen.get());
        }
        return handleRollThatCannotBePlayed(player, options);
    }
```

**The three phases, in five lines.** Generate, choose, execute. This is the method to point at when
asked how the program is structured. `chooseMove` answers with an `Optional`: present means "play
this", empty means "there was nothing playable", and the second case falls through to the Section 3
messages for an unplayable roll.

```java
    private boolean handleRollThatCannotBePlayed(Player player, MoveOptions options) {
        if (!options.hasBlockedAttempt()) {
            log.rollCannotBeUsed(player.colour());
            return false;
        }

        // A blocked piece that can at least shuffle up to the block is preferred to one that cannot.
        BlockedAttempt attempt = options.blockedAttempts().stream()
                .filter(blocked -> blocked.partialMove().isPresent())
                .findFirst()
                .orElse(options.blockedAttempts().get(0));
        log.pieceIsBlocked(attempt);
        if (attempt.partialMove().isPresent()) {
            PlannedMove partialMove = attempt.partialMove().get();
            log.blockedButMovedUpToTheBlock(player.colour(), partialMove);
            return applyMove(player, partialMove);
        }
        log.blockedWithNothingElseToMove(player.colour());
        return false;
    }
```

Reached only when the player had **no playable move**, which is exactly the condition in the required
message *"does not have other pieces in the board to move instead of the blocked piece"*. Three
outcomes:

1. **Nothing was even blocked** — every piece is in the base and no six was rolled, or Rule 10 refused
   every roll. The throw is simply lost (Rule 7's *"the roll is ignored"*).
2. **Blocked, with room to shuffle up** — print the block message, then *"Moved the piece to square L3
   which is the cell before the block."*, and play the shortened move.
3. **Blocked with no room** — the block is immediately adjacent, or it is sitting on the `X` a piece
   wanted to step out onto, so *"Ignoring the throw and moving on to the next player."*

When more than one piece was blocked, the stream picks the first attempt that **has** a partial move,
falling back to the first attempt only if none has one. The reasoning is the specification's own: the
fall-back exists so that a blocked player still uses the roll where it can, so a piece that can at
least advance to the cell before its block is preferred to one that can do nothing — for example a
base piece blocked on `X`. `TurnEngineTest.aFullMoveIsAlwaysPreferredToStoppingBeforeABlock` checks
the other half of the ordering: a partial move is only ever a fall-back when nothing can be played in
full.

```java
    private boolean applyMove(Player player, PlannedMove move) {
        boolean captured = moveExecutor.execute(move);
        player.onMoveExecuted(move);
        return captured;
    }
```

Three lines, but they are the reason blue's cycle works: **every** path that plays a move goes through
here, so `onMoveExecuted` is never forgotten — not for a normal move, not for a partial move, not for a
Rule T-6 forced move.

```java
    /**
     * Rule T-13: "during the next four rounds, the piece will be teleported to the base if the
     * player rolls value three consecutively." Each briefed piece keeps its own count of the rolls
     * made since its briefing began.
     */
    private void releaseBriefedPiecesOnConsecutiveThrees(Player player, int value) {
        for (Piece piece : board.piecesOf(player.colour())) {
            piece.effects().observeRoll(value);
            if (piece.effects().mustLeaveBriefingForBase()) {
                log.briefingEndedByConsecutiveThrees(piece);
                board.relocate(piece, Square.base(piece.colour()));
                piece.resetAfterCapture();
            }
        }
    }
```

Rule T-13's escape clause:

> "during the next four rounds, the piece will be **teleported to the base** if the player rolls value
> three consecutively." — Rule T-13

The turn engine does not keep a streak of its own. It shows the roll to each of the player's four
pieces, and each piece's `PieceEffects` decides for itself (section 4.2): a piece that is not briefed
ignores the roll; a briefed piece extends or resets its own run of threes; and a piece whose run has
reached two must go. The rolls are the player's — *"if the player rolls"* — but only rolls made during
that piece's briefing count, so a three rolled the round before the piece reached Beta can never help
it escape.

Sending the piece home reuses the two standard steps: `board.relocate` (the single mutation point) and
`resetAfterCapture`, because a piece in a base must not keep a direction, captures or a timer — it is
the same "back to the start" state as after a capture. Clearing the effects also ends the briefing and
its run of threes.

```java
    private void handleThirdConsecutiveSix(Player player) {
        List<Square> blockades = board.blockSquaresOf(player.colour());
        if (blockades.isEmpty()) {
            log.thirdSixIgnored(player.colour());
            return;
        }

        for (Square blockade : blockades) {
            List<Piece> pieces = board.groupOn(blockade, player.colour());
            if (pieces.size() < Board.MINIMUM_BLOCK_SIZE) {
                // An earlier break-up in this same turn has already dissolved this blockade.
                continue;
            }
            log.blockadeMustBeBroken(player.colour(), blockade.label(), pieces.size());
            breakUpBlockade(player, pieces);
        }
    }
```

Rule 4 and Rule T-6 meeting. With no blockade the third six is just ignored; with one, Rule T-6 forces
it apart.

The `pieces.size() < MINIMUM_BLOCK_SIZE` re-check inside the loop is there because `blockades` is a
**snapshot** taken before any piece moved, while the loop body moves pieces. Each group is therefore
re-read from the board just before it is broken up — a piece pushed out of the first blockade may, for
instance, have landed on the second one's cell and enlarged it — and a square that no longer holds a
block by then is skipped rather than "broken" a second time.

```java
    private void breakUpBlockade(Player player, List<Piece> blockade) {
        List<Piece> leaving = piecesLeavingTheBlockade(blockade);
        List<Integer> shares = GameRules.BLOCKADE_BREAK_SHARES.get(leaving.size() - 1);

        for (int index = 0; index < leaving.size(); index++) {
            Piece piece = leaving.get(index);
            int units = shares.get(index);
            Optional<PlannedMove> move =
                    moveGenerator.forcedMove(piece, piece.initialDirection(), units);
            if (move.isEmpty()) {
                log.blockadePieceCannotBeMoved(piece, units);
                continue;
            }
            applyMove(player, move.get());
        }
    }

    /**
     * "removing all pieces, baring one" - the piece with the shortest journey left is the one kept
     * in place, because it is the one that gains least from being pushed on.
     */
    private List<Piece> piecesLeavingTheBlockade(List<Piece> blockade) {
        List<Piece> ordered = new ArrayList<>(blockade);
        ordered.sort(Comparator.comparingInt(pathResolver::distanceToHome));
        return ordered.subList(1, ordered.size());
    }
```

> "the blockade has to be broken by the player by removing all pieces, **baring one** by moving them in
> their **original direction** by **six units cumulatively**." — Rule T-6

Three decisions, all visible:

- **`piece.initialDirection()`** — "their original direction" is the coin-toss direction, which is
  exactly what `initialDirection` stores. Two pieces of one blockade can therefore leave in opposite
  directions.
- **`BLOCKADE_BREAK_SHARES.get(leaving.size() - 1)`** — "cumulatively" read as *shared out*, with
  every share different (section 9.1). A blockade of 2 moves one piece 6 cells; a blockade of 3 moves
  two pieces 4 and 2 cells; a blockade of 4 moves three pieces 3, 2 and 1 cells. The `index` loop
  pairs the *n*-th leaving piece with the *n*-th share.
- **which piece stays** — the specification does not say, so the piece **closest to home** is kept
  (sort ascending by distance, drop the first with `subList(1, …)`), on the reasoning that it gains
  least from being pushed on. `List.sort` is stable, so among pieces at the same distance the
  lowest-numbered stays.

A piece that cannot travel its share — blocked, or it would overshoot home — gets an empty `Optional`
from `forcedMove`; it is reported and left where it is, rather than crashing. Each forced move goes
through `applyMove`, so captures, teleports and blue's cycle work exactly as for a normal move.

### 9.3 `FirstPlayerSelector` — the opening roll-off

```java
    public PieceColour determineFirstPlayer() {
        List<PieceColour> contenders = new ArrayList<>(List.of(PieceColour.values()));
        while (contenders.size() > 1) {
            List<PieceColour> highestRollers = rollOffBetween(contenders);
            if (highestRollers.size() == 1) {
                return highestRollers.get(0);
            }
            log.openingRollTie();
            contenders = highestRollers;
        }
        return contenders.get(0);
    }
```

> "Each player rolls the dice to identify who will be the first to roll. The player who rolls the
> highest will be the first to roll." — Section 1.1

The specification is silent on ties, so the tied players roll again — and only they do, which is why
`contenders` narrows each round. The loop terminates because `highestRollers` is always a strict subset
unless everyone tied, and a tie among *n* players eventually breaks with probability 1.

```java
    public List<PieceColour> roundOrderStartingWith(PieceColour first) {
        List<PieceColour> order = new ArrayList<>();
        PieceColour colour = first;
        for (int index = 0; index < PieceColour.values().length; index++) {
            order.add(colour);
            colour = colour.nextInTurnOrder();
        }
        return order;
    }
```

Rotates the fixed cycle to start at the winner. All the knowledge of *"the player to the left"* is in
`nextInTurnOrder()`, derived in section 2.6 — so this method contains no order of its own.

```java
    private List<PieceColour> rollOffBetween(List<PieceColour> contenders) {
        Map<PieceColour, Integer> rolls = new LinkedHashMap<>();
        int highest = 0;
        for (PieceColour colour : contenders) {
            int value = dice.roll();
            log.openingRoll(colour, value);
            rolls.put(colour, value);
            highest = Math.max(highest, value);
        }

        List<PieceColour> highestRollers = new ArrayList<>();
        for (Map.Entry<PieceColour, Integer> roll : rolls.entrySet()) {
            if (roll.getValue() == highest) {
                highestRollers.add(roll.getKey());
            }
        }
        return highestRollers;
    }
```

Two passes: roll and log everyone (Section 3 requires all four lines), tracking the maximum; then
collect everyone who matched it. `LinkedHashMap` preserves insertion order so the reported order is
stable.

`roll.getValue() == highest` compares an `Integer` with an `int`, so the `Integer` is unboxed and the
comparison is numeric — not reference identity. (Had both sides been `Integer`, values above 127 would
have compared wrongly; dice values are 1–6 so it would have worked by accident, but unboxing makes it
correct by construction.)

### 9.4 `LudoGame` — the whole game

```java
    public void play() {
        introducePlayers();
        List<PieceColour> turnOrder = decideTurnOrder();

        List<Square> previousPosition = boardPosition();
        int roundsWithoutMovement = 0;
        for (int round = 1; round <= GameRules.MAX_ROUNDS; round++) {
            log.roundHeader(round);
            playRound(turnOrder);
            reportEndOfRound();

            if (finishingOrder.size() >= GameRules.PLACES_TO_DECIDE) {
                log.announceFinalStandings(placings(turnOrder));
                return;
            }

            List<Square> position = boardPosition();
            roundsWithoutMovement = position.equals(previousPosition) ? roundsWithoutMovement + 1 : 0;
            previousPosition = position;
            if (roundsWithoutMovement >= GameRules.GRIDLOCK_ROUNDS) {
                log.gameGridlocked(round, GameRules.GRIDLOCK_ROUNDS, board);
                log.announceFinalStandings(finishingOrder);
                return;
            }
        }

        log.gameStoppedAtRoundLimit(GameRules.MAX_ROUNDS, board);
        log.announceFinalStandings(finishingOrder);
    }
```

The shortest interesting method in the program, and that is the point: `LudoGame` knows the *shape* of
a game and delegates everything else. Every line it "prints" is an event on its `GameListener`
(`log`), so `LudoGameTest` can play a whole seeded game against a Mockito mock and verify the events.

The bounded `for` rather than `while (nobody has won)` guarantees termination. There are three exits,
and they report differently:

- **the normal end** — three players are home, so all four places are known, and
  `placings(turnOrder)` lists them;
- **gridlock** — no piece has changed square for `GRIDLOCK_ROUNDS` consecutive rounds;
  `gameGridlocked` says so and lists every unfinished player, and the standings show only the places
  actually earned (`finishingOrder`, which may be empty);
- **the safety limit** — `gameStoppedAtRoundLimit` does the same after `MAX_ROUNDS`. Since gridlock
  detection was added, no seeded game in 1–200 gets this far; it remains purely as a last guarantee.

The gridlock check is four lines. After each round's report — and only if the game did not just end
normally — the current position is compared with the previous round's. `List.equals` compares the two
lists element by element, and because `Square` is a value object (and a Flyweight) "the same position"
means exactly "every piece on the same square as before". Any change at all — a move, a capture, a
teleport, a piece sent home from a briefing — resets the counter to zero; fifty identical rounds in a
row end the game. Seed 79 shows it: the board freezes after round 187 and the game ends after round
237 with *"No piece has moved for 50 rounds: the blocks on the board leave no legal move, so the game
ends after round 237."*

```java
    /** Where every piece stands, in a fixed order, so two rounds can be compared. */
    private List<Square> boardPosition() {
        return board.allPieces().stream().map(Piece::square).toList();
    }
```

A snapshot of the board as sixteen squares. `allPieces()` always lists the pieces in the same order
(yellow, blue, red, green; 1 to 4 within each colour), so two snapshots line up piece for piece, and
`toList()` makes the snapshot unmodifiable.

```java
    private List<PieceColour> placings(List<PieceColour> turnOrder) {
        List<PieceColour> placings = new ArrayList<>(finishingOrder);
        for (PieceColour colour : turnOrder) {
            if (!placings.contains(colour)) {
                placings.add(colour);
            }
        }
        return placings;
    }
```

> "The first player to bring all its pieces home wins the game. The game may continue to find second,
> third, and fourth places." — Rule 11

The first three places come straight from `finishingOrder`. The fourth needs no more play: once three
players have brought every piece home, the one still on the board can only be fourth. The loop adds
whichever colour is missing — there is exactly one — so the final standings always read `1st place`
to `4th place`. `LudoGameTest.aGameIntroducesAllFourPlayersAndDecidesAllFourPlaces` captures the
list with an `ArgumentCaptor` and checks it holds all four colours, with the finishing order first.

```java
    /** Colours in the order they brought all four pieces home; exposed for the tests. */
    List<PieceColour> finishingOrder() {
        return List.copyOf(finishingOrder);
    }
```

Package-private and an unmodifiable copy: the test can read the placings table, but nothing can change
it from outside.

```java
    private void introducePlayers() {
        for (PieceColour colour : PieceColour.values()) {
            Player player = players.get(colour);
            log.introducePlayer(colour, board.piecesOf(colour), player.behaviourSummary());
        }
        log.announceBoardLayout();
    }
```

Section 3's *"Before Game Begins"* line for each player, then the board layout. Note there is no
`blankLine()` call here: how the transcript is spaced is the listener's business, so `GameLog` ends
its own layout block with a blank line.

```java
    private void playRound(List<PieceColour> turnOrder) {
        for (PieceColour colour : turnOrder) {
            if (finishingOrder.contains(colour)) {
                // All four pieces are already home, so this player has nothing left to move.
                continue;
            }
            log.turnStarted(colour);
            turnEngine.playTurn(players.get(colour));
            recordIfFinished(colour);
            if (finishingOrder.size() >= GameRules.PLACES_TO_DECIDE) {
                return;
            }
        }
    }
```

> "A single round is where each player rolls the dice once." — Section 1.1

Finished players are skipped — all their pieces are home, so there is nothing to move. The early
`return` stops mid-round as soon as three places are decided, rather than making the remaining players
roll pointlessly.

`log.turnStarted(colour)` is an **event**, not formatting: "this colour's turn begins". `GameLog`
chooses to show it as a blank line between turns; a different listener could ignore it or draw a
separator.

```java
    private void recordIfFinished(PieceColour colour) {
        if (finishingOrder.contains(colour) || !board.hasAllPiecesHome(colour)) {
            return;
        }
        finishingOrder.add(colour);
        if (finishingOrder.size() == 1) {
            log.announceWinner(colour);
        }
    }
```

`finishingOrder` is the placings table. The `size() == 1` test is what makes *"[Color X] player
wins!!!"* print for the winner only; second, third and fourth are reported in the final standings.

This is checked **after every turn**, not at the end of the round, so the winner is announced at the
moment it happens.

```java
    private void reportEndOfRound() {
        log.roundEnded();
        for (PieceColour colour : PieceColour.values()) {
            log.playerPieceCounts(board, colour);
            log.pieceLocations(board, colour);
        }

        OptionalInt spawnedCell = mysteryCell.onRoundCompleted();
        spawnedCell.ifPresent(log::mysteryCellSpawned);
        log.mysteryCellStatus(mysteryCell);

        for (Piece piece : board.allPieces()) {
            piece.effects().onRoundCompleted();
        }
        players.values().forEach(Player::onRoundCompleted);
    }
```

Section 3's *"After each round, status of each player has to be shown"*, plus the three clocks.

`spawnedCell.ifPresent(log::mysteryCellSpawned)` is the `OptionalInt` from section 7.2 at work: the
spawn message is raised only when a cell actually spawned this round, with no `null` check in sight.

> **Why are the clocks advanced here and nowhere else?**
> Because "a round" has to mean exactly one thing across the whole program. Rule T-10's four-round
> lifetime, Rule T-12's four-round aura, Rule T-13's four-round briefing and blue's "in the next
> round" must all tick on the same boundary. Doing it in one method, once, is what guarantees that. If
> the mystery cell were advanced in `TurnEngine` and the auras here, "four rounds" would silently mean
> two different things.

The last line calls the `onRoundCompleted` hook of every player (section 8.1). Only blue does
anything with it — it advances its cycle to the piece after the one it moved first this round.

The mystery cell is advanced **before** its status line is printed, so *"will be at that location for
the next N values"* shows the freshly-decremented count — and a cell that has just spawned correctly
reports 4.

---

## 10. Package `ludot.ui`

Two types: an interface that names every event a game can raise, and the class that turns those
events into the transcript.

### 10.1 `GameListener` — what can happen in a game

```java
/**
 * Everything that can happen during a game that somebody might want to hear about.
 *
 * <p>The rule classes report <em>what happened</em> through this interface and never decide how it is
 * shown. {@link GameLog} turns each event into the status message required by Section&nbsp;3, while a
 * unit test can plug in a mock and simply verify that the right event was raised. This is the
 * Observer pattern, and it is also what keeps the rules independent of the console (Dependency
 * Inversion): nothing outside {@code ludot.ui} knows that output goes to a {@code PrintStream}.
 */
public interface GameListener {
```

The interface lists one method per event, grouped the way a game unfolds:

| Group | Events |
|---|---|
| before the game begins | `introducePlayer`, `announceBoardLayout`, `openingRoll`, `openingRollTie`, `firstPlayerChosen`, `roundOrder` |
| rounds and turns | `roundHeader`, `turnStarted`, `diceRolled`, `rollCannotBeUsed`, `thirdSixIgnored` |
| moving | `movesToStartingPoint`, `coinTossed`, `movesPiece`, `movesBlock`, `pieceReachedHome` |
| blocks | `pieceIsBlocked`, `blockedWithNothingElseToMove`, `blockedButMovedUpToTheBlock`, `blockadeMustBeBroken`, `blockadePieceCannotBeMoved` |
| captures | `capture`, `captureEarnsAnotherRoll` |
| mystery cell | `mysteryCellSpawned`, `mysteryCellStatus`, `landsOnMysteryCell`, `teleported`, `alphaAura`, `betaBriefing`, `briefingEndedByConsecutiveThrees`, `gammaTurnedPieceAround`, `gammaSendsPieceToBeta` |
| status and results | `playerPieceCounts`, `pieceLocations`, `roundEnded`, `announceWinner`, `announceFinalStandings`, `gameStoppedAtRoundLimit`, `gameGridlocked` |

A few signatures are worth reading, because they show that the events carry **domain objects**, not
text:

```java
    void movesPiece(PlannedMove move);
    ...
    void pieceIsBlocked(BlockedAttempt attempt);
    ...
    /** Raised once every player has had its turn, just before the end-of-round report. */
    void roundEnded();

    void announceWinner(PieceColour colour);

    void announceFinalStandings(List<PieceColour> placings);

    void gameStoppedAtRoundLimit(int roundLimit, Board board);

    /** No piece has changed square for {@code stillRounds} rounds, so the game cannot go on. */
    void gameGridlocked(int round, int stillRounds, Board board);
}
```

`turnStarted(colour)` and `roundEnded()` are pure structure: "a turn is beginning", "the turns of this
round are over". The rule classes no longer decide where blank lines go — they report the shape of
the game, and the listener lays it out.

> **Why an interface, when there is only one implementation?**
> Because the *rule classes* are what benefit. `TurnEngine`, `LudoGame`, `FirstPlayerSelector`,
> `MoveExecutor` and `MysteryEffectResolver` all take a `GameListener` in their constructor and never
> mention `GameLog`. That is the **Dependency Inversion Principle**: high-level rules depend on an
> abstraction, and the low-level detail (printing to a stream) depends on the same abstraction. It
> pays off immediately in the tests — `TurnEngineTest` and `LudoGameTest` pass `mock(GameListener.class)`
> and assert with `verify(listener).blockadeMustBeBroken(PieceColour.YELLOW, "10", 3)` instead of
> searching printed text — and it is the **Observer pattern**: the rules publish events without
> knowing who is listening.

### 10.2 `GameLog` — every line the program prints

One class, one method per required message. No other class in the program calls `System.out` (apart
from `Main` reporting a bad seed on `System.err`), and `LudoTSimulation` is the only class that ever
constructs a `GameLog`.

```java
public final class GameLog implements GameListener {

    private static final String SEPARATOR = "============================";
    private static final String[] PLACES = {"1st", "2nd", "3rd", "4th"};

    private final PrintStream out;

    public GameLog(PrintStream out) {
        this.out = out;
    }
```

The `PrintStream` is **injected**, not hard-coded to `System.out`. That is what lets `GameLogTest`
pass a stream backed by a `ByteArrayOutputStream` and compare every message word for word, and it is
why redirecting a whole game to a file needs no change to any rule class.

Every event method carries `@Override`, so the compiler checks that `GameLog` and `GameListener`
always agree: rename an event in one and the other stops compiling.

The class javadoc records one more decision:

```java
 * <p>Colours are always printed in lower case, even at the start of a line, because the Legend of
 * the specification defines {@code Color X} as "red, yellow, blue, or green".
```

So the transcript reads *"red player rolled 4."*, not *"Red player rolled 4."* — the Legend's own
list of values for `[Color X]` is lower case, and the output follows it literally.

A representative method:

```java
    /**
     * "[Color X] moves piece X from location L1 to L2 by [value] units in
     * [clockwise/counter-clockwise] direction."
     */
    @Override
    public void movesPiece(PlannedMove move) {
        Piece piece = move.primaryPiece();
        out.printf("%s moves piece %s from location %s to %s by %d units in %s direction.%n",
                piece.colour().displayName(), piece.name(), move.from().label(),
                move.destination().label(), move.stepsTaken(), move.direction().displayName());
    }
```

Three things worth noticing about the style used throughout:

1. **The javadoc quotes the specification's template**, so a marker can check the format without
   running anything.
2. **The method takes a domain object, not strings.** Callers say `log.movesPiece(move)`, not
   `log.print("red moves piece R1 from...")`. The log decides the wording; the rest of the program
   just says what happened.
3. **`%n`, not `\n`.** `%n` emits the platform line separator, so the transcript is correct on Windows
   as well as on macOS and Linux.

Note where the pieces of the message come from: `move.from().label()` and `move.destination().label()`
delegate straight to `Square.label()`, so the Legend's `[colour]homepath[n]` format is produced by the
square itself. `GameLog` never assembles a location out of a number and a colour.

```java
    /** The end-of-round listing of one player's pieces. */
    @Override
    public void pieceLocations(Board board, PieceColour colour) {
        out.println(SEPARATOR);
        out.printf("Location of pieces %s%n", colour.displayName());
        out.println(SEPARATOR);
        for (Piece piece : board.piecesOf(colour)) {
            out.printf("Piece %s -> %s.%n", piece.name(), piece.square().label());
        }
    }
```

Section 3's status block. Because it iterates `board.piecesOf(colour)` — the immutable `R1..R4` list —
the four lines always appear in the same order, and `Square.label()` prints `Base`, `Home` or a cell
id as appropriate with no branching here.

```java
    @Override
    public void alphaAura(Piece piece, SpeedModifier modifier) {
        String effect = modifier == SpeedModifier.DOUBLED
                ? "feels energized, and movement speed doubles"
                : "feels sick, and movement speed halves";
        out.printf("%s piece %s %s.%n", piece.colour().displayName(), piece.name(), effect);
    }
```

Two required messages sharing one method, because they are the two halves of one Rule T-12 outcome.
The wording — including the American *"energized"* — is copied exactly from the specification.

```java
    /** Each turn is separated from the previous one by a blank line. */
    @Override
    public void turnStarted(PieceColour colour) {
        blankLine();
    }
    ...
    /** The end-of-round report is set apart from the last turn by a blank line. */
    @Override
    public void roundEnded() {
        blankLine();
    }
    ...
    private void blankLine() {
        out.println();
    }
```

The layout events. `blankLine` is **private**: spacing is `GameLog`'s own concern, and no rule class
can reach in and print an empty line. The rules say "a turn started" or "the round ended"; this class
decides that those look like a blank line.

```java
    @Override
    public void announceFinalStandings(List<PieceColour> placings) {
        blankLine();
        out.println(SEPARATOR);
        out.println("Final standings");
        out.println(SEPARATOR);
        for (int index = 0; index < placings.size(); index++) {
            out.printf("%s place: %s%n", PLACES[index], placings.get(index).displayName());
        }
    }

    /** The safety net was hit; the players still on the board are listed with their progress. */
    @Override
    public void gameStoppedAtRoundLimit(int roundLimit, Board board) {
        blankLine();
        out.printf("The simulation reached its safety limit of %d rounds and was stopped.%n",
                roundLimit);
        listUnfinishedPlayers(board);
    }

    /** The board is gridlocked by blocks, so no further move is possible. */
    @Override
    public void gameGridlocked(int round, int stillRounds, Board board) {
        blankLine();
        out.printf("No piece has moved for %d rounds: the blocks on the board leave no legal move, "
                + "so the game ends after round %d.%n", stillRounds, round);
        listUnfinishedPlayers(board);
    }

    private void listUnfinishedPlayers(Board board) {
        for (PieceColour colour : PieceColour.values()) {
            if (!board.hasAllPiecesHome(colour)) {
                out.printf("Unfinished: %s with %d/%d pieces home%n", colour.displayName(),
                        board.piecesAtHome(colour).size(), BoardGeometry.PIECES_PER_PLAYER);
            }
        }
    }
```

The three endings. A finished game prints exactly `1st place: …` to `4th place: …` — the fourth place
decided by elimination in `LudoGame.placings`. A gridlocked game, or one stopped by the safety net,
says *why* it ended, lists every player that has not brought all its pieces home with its progress,
and then prints whatever places were genuinely earned. The "Unfinished" list is the same for both, so
it is written once, in the private `listUnfinishedPlayers`. Seed 79 ends like this:

```
No piece has moved for 50 rounds: the blocks on the board leave no legal move, so the game ends after round 237.
Unfinished: yellow with 0/4 pieces home
Unfinished: blue with 0/4 pieces home
Unfinished: red with 0/4 pieces home
Unfinished: green with 0/4 pieces home
```

#### Messages beyond Section 3

A handful of methods print lines the specification does not require. Each was added because it makes
the transcript self-explanatory, and they are clearly distinguishable from required output:

| Method | Why it is there |
|---|---|
| `announceBoardLayout` | prints the derived cell numbers, so the geometry can be checked against Figure 1 at a glance |
| `coinTossed` | Rule T-1's toss is a cause; without it a counter-clockwise piece looks like a bug |
| `movesBlock` | Rule T-4 moves several pieces at once; the required single-piece format cannot express that |
| `captureEarnsAnotherRoll` | makes Rule T-2's bonus roll visible rather than mysterious |
| `thirdSixIgnored` | shows Rule 4 being applied rather than a roll silently vanishing |
| `rollCannotBeUsed` | says why a roll produced no move when nothing was blocked (e.g. all pieces in the base) |
| `blockadeMustBeBroken` / `blockadePieceCannotBeMoved` | Rule T-6 is otherwise invisible |
| `pieceReachedHome` | progress towards Rule 11 |
| `announceFinalStandings` | Rule 11's "second, third, and fourth places" |
| `gameStoppedAtRoundLimit` | honesty: says plainly when the safety limit was hit, and who had not finished |
| `gameGridlocked` | the rules have no stalemate provision, so the program says why a deadlocked game ended, and who had not finished |
| `introducePlayer`'s behaviour line | names each colour's strategy so the transcript explains itself |

---

## 11. Wiring it all together

### 11.1 `LudoTSimulation` — the composition root

```java
    public LudoTSimulation(RandomSource randomSource, PrintStream out) {
        GameLog log = new GameLog(out);

        Board board = new Board();
        PathResolver pathResolver = new PathResolver(board);
        MysteryCell mysteryCell = new MysteryCell(board, randomSource);

        Dice dice = new Dice(randomSource);
        Coin coin = new Coin(randomSource);

        MysteryEffectResolver mysteryEffectResolver =
                new MysteryEffectResolver(board, randomSource, log);
        MoveGenerator moveGenerator = new MoveGenerator(board, pathResolver);
        MoveExecutor moveExecutor =
                new MoveExecutor(board, coin, mysteryCell, mysteryEffectResolver, log);

        TurnEngine turnEngine =
                new TurnEngine(board, dice, moveGenerator, moveExecutor, pathResolver, log);
        List<Player> players = new PlayerFactory(board, pathResolver, mysteryCell).createAll();

        this.game = new LudoGame(board, players, turnEngine,
                new FirstPlayerSelector(dice, log), mysteryCell, log);
    }
```

**Every `new` in the program that matters happens here.** That is what makes constructor injection
possible everywhere else: no class reaches out for a collaborator, each is handed exactly what it asked
for, and nothing has a hidden dependency on a global.

This is also the **only** place the concrete `GameLog` appears. The same `log` object is passed to
`MysteryEffectResolver`, `MoveExecutor`, `TurnEngine`, `FirstPlayerSelector` and `LudoGame`, but every
one of those constructors asks for a `GameListener` — so swapping the console transcript for anything
else is a one-line change here and nowhere else.

The order is bottom-up — things with no dependencies first, then the things that need them:

```
GameLog          <- out
Board            <- (nothing)
PathResolver     <- board
MysteryCell      <- board, randomSource
Dice, Coin       <- randomSource
MysteryEffects   <- board, randomSource, log
MoveGenerator    <- board, pathResolver
MoveExecutor     <- board, coin, mysteryCell, mysteryEffects, log
TurnEngine       <- board, dice, moveGenerator, moveExecutor, pathResolver, log
players          <- board, pathResolver, mysteryCell
LudoGame         <- board, players, turnEngine, firstPlayerSelector, mysteryCell, log
```

Notice that **one** `RandomSource` instance is shared by the dice, the coin, the mystery cell and the
teleport resolver. That is deliberate: a single stream of random numbers means a seed reproduces the
entire game, including which teleport was drawn in round 137.

```java
    /** Convenience constructor: a reproducible game printed to standard output. */
    public LudoTSimulation(long seed) {
        this(new SeededRandomSource(seed), System.out);
    }

    public void run() {
        game.play();
    }
```

The two parameters of the main constructor are exactly the two things a test wants to control — chance
and output — which is not a coincidence.

### 11.2 `Main`

```java
    public static void main(String[] args) {
        SeededRandomSource randomSource;
        try {
            randomSource = args.length > 0
                    ? new SeededRandomSource(parseSeed(args[0]))
                    : new SeededRandomSource();
        } catch (IllegalArgumentException invalidSeed) {
            System.err.println(invalidSeed.getMessage());
            System.err.println("Usage: java -cp out Main [seed]");
            return;
        }
        new LudoTSimulation(randomSource, System.out).run();
    }
```

An optional argument is a seed; no argument means an unseeded `SeededRandomSource()` — a fresh,
unpredictable game every run. `Main` is in the default package so the command line is simply
`java -cp out Main`, and its private constructor stops anyone instantiating it.

The `try` exists for one situation: a seed that is not a number. Instead of a stack trace from deep
inside `Long.parseLong`, the user sees what went wrong and how to call the program, on standard error
so it never mixes with a game transcript:

```
The seed must be a whole number, but was: "abc"
Usage: java -cp out Main [seed]
```

```java
    static long parseSeed(String argument) {
        try {
            return Long.parseLong(argument.trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "The seed must be a whole number, but was: \"" + argument + "\"", notANumber);
        }
    }
```

The parsing is a method of its own so that it can be tested without starting a game. It is
package-private (no `public`) because only `Main` and `MainTest` — which is in the same default
package — need it. `trim()` forgives stray spaces around the number (`" 42 "` is seed 42). The
original `NumberFormatException` is kept as the *cause*, so nothing is lost for debugging, while the
message names the bad input exactly as it was typed. `MainTest` checks both halves: a whole number is
accepted, and `"abc"` produces this message rather than a stack trace.

---

## 12. Five worked traces from real games

All output below is genuine, copied from real runs of the current program — the seed (and the round)
is named for each excerpt, so every one of them can be reproduced with `java -cp out Main <seed>`.
Following these end to end is the fastest way to see the whole program working.

### Trace 1 — leaving the base: Rules 2, T-1 and 4

Seed 10, round 8:

```
green player rolled 6.
green player moves piece G1 to the starting point.
green player now has 1/4 on pieces on the board and 3/4 pieces on the base.
The coin toss for green piece G1 is tails, so it will move in a counter-clockwise direction.
green player rolled 1.
green moves piece G1 from location 39 to 38 by 1 units in counter-clockwise direction.
```

What happened, line by line:

1. `LudoGame.playRound` raises `turnStarted(GREEN)` (the blank line before the excerpt), then
   `TurnEngine.playTurn` calls `dice.roll()` → 6, and `log.diceRolled` prints it.
2. `releaseBriefedPiecesOnConsecutiveThrees` shows the 6 to each green piece; none is briefed, so
   nothing happens. `consecutiveSixes` becomes 1 — below the limit of 3, so play continues.
3. `MoveGenerator.optionsFor(GREEN, 6)`:
   - `addEnterBoardMove` fires because the roll **is** 6 (Rule 2), green has pieces in its base, and
     `opponentBlockerOn(cell 39)` finds no opponent block. It produces one `ENTER_BOARD` move whose
     `PieceMovement` has `direction = null`.
   - `addSinglePieceMoves` and `addBlockMoves` add nothing — green has nothing on the board yet.
4. `GreenPlayer.selectMove`: level 1 finds no move that forms a new block (`formsNewBlock` is false —
   no green piece is standing on cell 39), so level 2's `enterBoardMove` returns it, wrapped in an
   `Optional`; `chooseMove` confirms it is one of the legal moves.
5. `MoveExecutor.execute` sees `isEnteringBoard()` and calls `enterBoard`:
   - `board.relocate(G1, Square.ring(39))` — **green's start cell is 39**, exactly as derived in
     section 2;
   - the two required messages print;
   - `coin.toss()` returns `TAILS`, so `assignStartingDirection(COUNTER_CLOCKWISE)` sets **both**
     `direction` and `initialDirection` (arming Rule T-5 for the rest of G1's life).
6. Back in `playTurn`: the roll was a six and green has not finished, so the loop continues — **Rule
   4's second roll**.
7. The second roll is 1. G1 walks counter-clockwise: `Direction.nextRingCell(39)` = `wrapRing(38)` =
   **38**. Had G1 been at cell 0, the same call would have given `wrapRing(-1) = 51` — which is what
   the doubled modulo in `wrapRing` is for.

### Trace 2 — a capture and Rule T-2's bonus roll

Seed 72, round 76:

```
red player rolled 5.
red moves piece R1 from location 38 to 43 by 5 units in clockwise direction.
red piece R1 lands on square 43, captures blue piece B1, and returns it to the base.
red player now has 1/4 on pieces on the board and 1/4 pieces on the base.
red captured an opponent piece and receives another roll (Rule T-2).
red player rolled 4.
red moves piece R1 from location 43 to 47 by 4 units in clockwise direction.
```

1. During generation, `PathResolver.walk` reached cell 43 on its final step. `blockerAt` found a blue
   group of size **1** there — below `MINIMUM_BLOCK_SIZE`, so not a blocker (Rule 5 says a lone piece
   can be jumped; Rule 6 says it can be captured).
2. `capturesOnLanding(Square.ring(43), RED)` returned `[B1]`, so the `PlannedMove` carries it.
3. `RedPlayer.selectMove` step 1: `capturingMoves` is non-empty, so red captures. (R1 is red's only
   piece on the board here — the other three are one in the base and two at home — so it is also the
   only move; but step 1 would have chosen it over a base entry too. That is red's defining trait.)
4. `MoveExecutor.execute` first moves the piece (`advance` prints the move line), then
   `applyCaptures`:
   - `board.relocate(B1, Square.base(BLUE))` — Rule 6, back to base;
   - `B1.resetAfterCapture()` — **Rule T-9**, wiping B1's direction, captures, approach passes and any
     Alpha/Beta timers;
   - `R1.recordCapture()` — R1's count goes up by one, so `hasEarnedHomeStraightEntry()` is `true`
     and **Rule T-7's gate is open** for R1;
   - `log.playerPieceCounts(board, RED)` — the Section 3.1 template's second line, for the
     *capturing* player only. Blue's new count appears in the end-of-round report.
5. `execute` returns `true`, so `TurnEngine` prints the Rule T-2 line and rolls again, even though
   the 5 was not a six.
6. The bonus roll is 4, a plain move, and since it is neither a six nor a capture the turn ends there.

### Trace 3 — Rule T-3, and the "cell before the block" fall-back

Seed 17, round 25:

```
red player rolled 2.
red piece R1 is blocked from moving from 50 to 0 by yellow piece Y3.
red does not have other pieces in the board to move instead of the blocked piece. Moved the piece to square 51 which is the cell before the block.
red moves piece R1 from location 50 to 51 by 1 units in clockwise direction.
```

At the start of the round yellow holds a block on cell 0 (Y3 and Y4). Red has two pieces out: R1 on
cell 50 moving clockwise, and R2 on cell 2 moving counter-clockwise — both heading **into** that block.

1. `walk(R1, CLOCKWISE, 2)` (a group of one):
   - **step 1** → cell 51. `blockerAt(51, RED, 1, isFinalStep=false)` finds nothing.
     `furthestReached = 51`, `stepsToFurthestReached = 1`.
   - **step 2** → cell 0. `blockerAt(0, RED, 1, isFinalStep=true)` finds a yellow group of size 2.
     The Rule T-8 exception needs `opponentGroupSize == groupSize`, i.e. `2 == 1` — false. So Y3, the
     lowest-numbered piece of the block, is returned as the blocker.
   - Returns `BLOCKED` with `destination()` = cell 51.
2. `blockedAttempt` builds the report: `destinationIgnoringBlocks` walks the same two steps with no
   block checks and returns cell **0** — the `L2` of the message. `walk.destination().map(...)` turns
   cell 51 into a `PARTIAL_ADVANCE` move, so `partialMove` is present.
3. R2 fares the same way: counter-clockwise from 2, its first step to cell 1 is free and its second
   would land on the yellow block, so it too becomes a `BlockedAttempt` with a partial move (to 1).
4. `optionsFor` therefore returns **no playable moves** and two blocked attempts.
5. `chooseMove` returns an empty `Optional` (empty playable list), so `handleRollThatCannotBePlayed`
   runs: `hasBlockedAttempt()` is true; the stream picks the first attempt **with** a partial move —
   both have one, so R1's, which comes first because `piecesInPlay` lists R1 before R2;
   `partialMove().isPresent()` → the shortened move is played.
6. Three messages result, which are exactly the Section 3 sequence for this case.

> Had yellow's block been on cell **51** instead — immediately in front of R1 — then step 1 would have
> been blocked, `furthestReached` would still be `null`, `partialMove` would be empty, and (if no other
> piece could at least shuffle forward) the output would end with *"Ignoring the throw and moving on to
> the next player."* instead.

The same pair of messages also covers a piece that cannot leave its **base**. Seed 100, round 25 —
every red piece is in its base and green's G1 and G3 are standing on cell 26, red's `X`:

```
red player rolled 6.
red piece R1 is blocked from moving from Base to 26 by green piece G1.
red does not have other pieces in the board to move instead of the blocked piece. Ignoring the throw and moving on to the next player.
red player rolled 4.
red has no piece that can use this roll. Ignoring the throw and moving on to the next player.
```

`addEnterBoardMove` asked `opponentBlockerOn(cell 26, RED)`, found green's block, and recorded a
`BlockedAttempt` from `Base` to `26` with an empty partial move. With nothing else to play, the turn
engine printed the blocked message and *"Ignoring the throw"*. The six still earned another roll
(Rule 4); the 4 could not bring a piece out and nothing was blocked, so it gets the plain
"no piece can use this roll" line.

### Trace 4 — the mystery cell, and Gamma forwarding to Beta

Seed 29, round 52. At the end of round 51 the mystery cell spawned on cell 37; yellow has Y1 and Y2
home, Y3 on cell 43 moving counter-clockwise, and Y4 on cell 25 — already at a briefing since
round 49.

```
yellow player rolled 6.
yellow moves piece Y3 from location 43 to 37 by 6 units in counter-clockwise direction.
yellow player lands on a mystery cell and is teleported to Gamma.
yellow piece Y3 teleported to Gamma.
The yellow piece Y3 is moving in a counterclockwise direction. Teleporting to Beta from Gamma.
yellow piece Y3 teleported to Beta.
yellow piece Y3 attends briefing and cannot move for four rounds.
yellow player rolled 2.
yellow has no piece that can use this roll. Ignoring the throw and moving on to the next player.
```

Y3 walks counter-clockwise from 43 to 37 (`43 - 6 = 37`), and the mystery cell is on 37.

1. `MoveExecutor.execute` completes the walk and calls `applyCaptures` (nothing there to capture).
2. `mysteryCell.isOn(Square.ring(37))` is true, so
   `mysteryEffectResolver.resolveLandingOnMysteryCell(Y3)` runs.
3. `randomSource.pick(DESTINATIONS)` draws **GAMMA** (Rule T-11's third option).
4. `teleport` moves Y3 to `Square.ring(BoardGeometry.GAMMA_CELL)` = **cell 44** — the value derived in
   section 2.5. Neither the BASE reset nor the approach-pass branch applies.
5. `applyDestinationEffect` dispatches to `applyGammaClarification`, and Y3 is moving
   **counter-clockwise**, so Rule T-14's second half applies: *"it would be teleported to Beta"*.
6. `teleport(Y3, BETA)` moves it to **cell 25**. Then `applyBetaBriefing` calls `beginBriefing`,
   which sets `briefingRoundsRemaining = 4` and raises `briefingBegunThisRound`.
7. The six earns another roll, a 2 — and **no yellow piece can use it**. Y1 and Y2 are home; Y3 has
   just been frozen; and Y4 is *still* frozen. Y4's briefing began in round 49, so round 49 itself was
   skipped and its four full rounds are 50, 51, 52 and 53 — it can move again only from round 54
   (section 4.2). `addSinglePieceMoves` skips both briefed pieces, `optionsFor` returns nothing at
   all, and the turn engine prints the "no piece can use this roll" line.

Two things this trace demonstrates that are easy to get wrong:

- **The chain is bounded.** Gamma can forward to Beta, but Beta cannot forward anywhere, so the
  recursion depth is at most 2.
- **Rule T-15 is respected.** Y3's effects fired because it was *teleported* to Gamma. A piece that
  merely walked onto cell 44 would trigger nothing, because `applyDestinationEffect` is private and
  reachable only from `resolveLandingOnMysteryCell`.

### Trace 5 — Rules T-4, T-6 and T-13

A block of three moving together (seed 7, rounds 35 and 36 — green rolls first in each round):

```
green player rolled 5.
green moves its block of 3 pieces (G1, G2, G3) from location 37 to 36 by 1 units in counter-clockwise direction.
```

```
green player rolled 6.
green moves its block of 3 pieces (G1, G2, G3) from location 36 to 34 by 2 units in counter-clockwise direction.
```

`5 / 3 = 1` cell (integer division), then `6 / 3 = 2` cells — Rule T-4's division, visible twice. The
names print in `G1, G2, G3` order because `Board.groupOn` sorts by piece number, which is also why
`G1` is the piece `directionSettingPieceOf` consults when the directions agree. Green's ladder chose
these moves at level 3: no move formed a new block, and on the six there was nothing in the base to
bring out (G4 was already on the board).

A forced break-up with distinct shares (seed 1, round 59):

```
red player rolled 6.
red player moves piece R1 to the starting point.
red player now has 2/4 on pieces on the board and 1/4 pieces on the base.
The coin toss for red piece R1 is heads, so it will move in a clockwise direction.
red player rolled 6.
red player moves piece R3 to the starting point.
red player now has 3/4 on pieces on the board and 0/4 pieces on the base.
The coin toss for red piece R3 is heads, so it will move in a clockwise direction.
red player rolled 6.
red rolled a six three times in a row and holds a blockade of 3 pieces on square 26, which must now be broken (Rule T-6).
red moves piece R3 from location 26 to 30 by 4 units in clockwise direction.
red moves piece R4 from location 26 to 28 by 2 units in clockwise direction.
```

1. R4 was already waiting on cell 26, red's `X`. The first two sixes each brought a piece out onto
   the same cell (red has nothing to capture, so step 2 of its strategy uses the six), building a
   blockade of three: R1, R3 and R4.
2. The third six made `consecutiveSixes` reach 3, so `handleThirdConsecutiveSix` ran instead of
   playing the roll. `blockSquaresOf(RED)` found cell 26 — so Rule 4's "just ignore it" does not
   apply and Rule T-6 takes over.
3. `piecesLeavingTheBlockade` sorted the three by distance to home. All three are clockwise on their
   own `X`, 56 cells from home, so the stable sort keeps them in number order and **R1** — first —
   stays. R3 and R4 leave.
4. Two pieces leave, so `BLOCKADE_BREAK_SHARES.get(1)` = `[4, 2]`: R3 moves 4 cells, R4 moves 2.
5. `forcedMove(piece, piece.initialDirection(), units)` — note **`initialDirection`**, Rule T-6's
   "original direction", clockwise for both: `26 + 4 = 30` and `26 + 2 = 28`.

The two pieces end on **different** cells, and that is the point of the distinct shares (section 9.1).
An equal split would have moved both 3 cells to cell 29, and red would still have been holding a
blockade — just one cell further on.

And a Rule T-13 escape (seed 17). In round 51 a green block move lands on the mystery cell, and each
of the two pieces is teleported on its own:

```
green player rolled 2.
green moves its block of 2 pieces (G2, G4) from location 12 to 11 by 1 units in counter-clockwise direction.
green player lands on a mystery cell and is teleported to Gamma.
green piece G2 teleported to Gamma.
The green piece G2 is moving in a counterclockwise direction. Teleporting to Beta from Gamma.
green piece G2 teleported to Beta.
green piece G2 attends briefing and cannot move for four rounds.
green player lands on a mystery cell and is teleported to Gamma.
green piece G4 teleported to Gamma.
The green piece G4, which was moving clockwise, has changed to moving counterclockwise.
```

G2 is now briefed. Green's rolls in round 52 are a 6 and a 2 — each shown to G2 by `observeRoll`, and
neither is a three. Then, in round 53 and round 54:

```
green player rolled 3.
green moves piece G4 from location 42 to 39 by 3 units in counter-clockwise direction.
```

```
green player rolled 3.
green piece G2 is movement-restricted and has rolled three consecutively. Teleporting piece G2 to base.
green moves its block of 2 pieces (G1, G4) from location 39 to 38 by 1 units in counter-clockwise direction.
```

1. Round 53's 3 is the first three of G2's briefing: `observeRoll(3)` sets its run to 1. The roll is
   played normally — G4 walks onto cell 39, where G1 stands, which is green's level 1: **form a new
   block**.
2. Round 54's 3 is the very next green roll, so `observeRoll(3)` sets the run to 2 =
   `CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING`. `mustLeaveBriefingForBase()` is now true, so
   `releaseBriefedPiecesOnConsecutiveThrees` prints the required message, relocates G2 to its base
   and resets it.
3. The roll is then still played normally — the escape happens *before* the move, not instead of it —
   and green's new block moves `3 / 2 = 1` cell (level 3 of green's ladder).
4. Only rolls made **during** G2's briefing counted. Had green rolled a 3 just before G2 reached Beta,
   it would not have helped: `beginBriefing` starts the run at zero.

---

## 13. The test suite

The tests live in `test/`, in the same packages as the classes they test, and run on **JUnit 5**
with **Mockito** for test doubles. The Maven `pom.xml` points at the project's own layout
(`<sourceDirectory>src</sourceDirectory>`, `<testSourceDirectory>test</testSourceDirectory>`, Java
release 21) and adds JUnit 5.14.4, Mockito 5.24.0 and the JaCoCo 0.8.15 coverage plugin:

```
mvn test        # 224 tests in 21 test classes, all passing
mvn verify      # the same, plus the coverage report in target/site/jacoco/index.html
```

Coverage is **97.6 % of lines and 94.9 % of branches**. Maven is only needed for the tests: the
simulation itself still builds and runs with nothing but the JDK —
`javac -d out $(find src -name '*.java')` then `java -cp out Main [seed]`.

Each test reads as a sentence about the rules (`aCounterClockwisePieceMustPassItsApproachTwiceSoItIs60CellsFromHome`,
`aThirdSixBreaksABlockadeOfThreeWithSharesOfFourAndTwo`), and most start with a comment naming the rule
they check, so the list of test names is itself a readable specification.

### Exact positions: `ludot.Fixtures`

```java
    /** Puts piece {@code number} of {@code colour} on a standard cell, facing {@code direction}. */
    public static Piece place(Board board, PieceColour colour, int number, int cell,
            Direction direction, int captures) {
        return placeOn(board, colour, number, Square.ring(cell), direction, captures);
    }

    /** Puts a piece on any square with a direction and a number of captures already made. */
    public static Piece placeOn(Board board, PieceColour colour, int number, Square square,
            Direction direction, int captures) {
        Piece piece = piece(board, colour, number);
        board.relocate(piece, square);
        piece.assignStartingDirection(direction);
        for (int capture = 0; capture < captures; capture++) {
            piece.recordCapture();
        }
        return piece;
    }
```

The set-up helper is what makes exact positions possible. It uses the **real** `Board.relocate`, so
the occupancy index is correct and the block rules see what they would see in a game — and since
`Piece.setSquare` is package-private, a test in `ludot.player` could not cheat even if it tried. The
`captures` parameter exists purely so a test can satisfy or deliberately violate Rule T-7.

### Forcing chance: `fixedRandom`

```java
    public static RandomSource fixedRandom(int index, boolean heads) {
        RandomSource random = mock(RandomSource.class,
                withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        when(random.nextInt(anyInt())).thenReturn(index);
        when(random.nextBoolean()).thenReturn(heads);
        return random;
    }
```

This is the payoff of the `RandomSource` interface. A Mockito mock answers `nextInt` and
`nextBoolean` with fixed values, while `CALLS_REAL_METHODS` lets the interface's `default` method
`pick` run for real — so `pick(list)` returns element `index` of any list. `fixedRandom(2, true)` is
"always Gamma"; `MysteryEffectResolverTest` then runs the **real** `MysteryEffectResolver` and asserts
that a clockwise piece ends up counter-clockwise on cell 44 and a counter-clockwise one ends up
briefed on cell 25.

### Mocks instead of printed text

Where a test is about *what happened* rather than *how it is worded*, it listens with a mocked
`GameListener` (section 10.1) instead of reading output. `TurnEngineTest` also mocks the `Dice`, so a
turn can be scripted roll by roll:

```java
        place(board, PieceColour.YELLOW, 1, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 2, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 3, 10, Direction.CLOCKWISE, 0);
        place(board, PieceColour.YELLOW, 4, 40, Direction.CLOCKWISE, 0);
        diceRolling(6, 6, 6);

        engine.playTurn(yellow);

        verify(listener).blockadeMustBeBroken(PieceColour.YELLOW, "10", 3);
        assertEquals(Square.ring(10), piece(board, PieceColour.YELLOW, 1).square());
        assertEquals(Square.ring(14), piece(board, PieceColour.YELLOW, 2).square());
        assertEquals(Square.ring(12), piece(board, PieceColour.YELLOW, 3).square());
```

Y4 uses the first two sixes; the third breaks the blockade on cell 10. Y1 stays, Y2 takes the share of
4 and Y3 the share of 2 — Rule T-6 with distinct shares, checked on the real board.

`LudoGameTest` plays complete seeded games (seeds 1–12 must all finish, without ever raising
`gameGridlocked` or reaching the safety limit) against a mocked listener, plays seed 79 to check
that a gridlocked board ends the game instead of running on to the safety limit, and uses an
`ArgumentCaptor` to check that the final standings name all four colours with the finishing order
first. The wording itself is tested once, in `GameLogTest`, which prints into a
`ByteArrayOutputStream` and compares every Section 3.1 message **word for word**.

### What the 224 tests cover

| Test class | Tests | What it pins down |
|---|---|---|
| `GeometryTest` | 11 | all four starts and approaches, Alpha/Beta/Gamma, `R -> G` turn order, wrapping, two approach passes counter-clockwise |
| `SquareTest`, `BoardTest`, `PieceTest` | 6, 8, 5 | Flyweight identity, labels, the occupancy index, block grouping in number order, Rule 11's "all four home", Rule T-9's reset |
| `PathResolverTest` | 18 | the 56- and 60-cell journeys (T-1), Rule T-7 for a piece and **for a block**, Rule 10's exact roll, Rule 5, T-3 stopping before a block or not moving at all, T-8 |
| `MoveGeneratorTest` | 15 | **the specification's own Rule T-3 worked example** (G1 on 0, block on 4, roll 6 → cell 3); a block on `X` reported as a blocked attempt; T-4 division and direction; T-5; T-8; T-12; T-13 |
| `MoveExecutorTest` | 6 | the coin toss, captures and resets, T-8 crediting, the mystery teleport, reaching home |
| `PieceEffectsTest` | 12 | T-12 arithmetic; aura and briefing lasting the rest of their round **plus four full rounds**; two threes during a briefing; any other roll breaking the run; threes before the briefing not counting |
| `MysteryCellTest` | 6 | T-10: two **full** rounds, only on an empty cell, four-round lifetime, never the same cell twice, no cell before it spawns |
| `MysteryEffectResolverTest` | 9 | all six destinations in Rule T-11's order, both auras, both branches of T-14 |
| `RedPlayerTest`, `GreenPlayerTest`, `YellowPlayerTest`, `BluePlayerTest`, `PlayerFactoryTest` | 5, 8, 4, 8, 3 | every sentence of Section 2.1 — including green's ladder and blue's per-round cycle and mystery-cell preferences |
| `TurnEngineTest` | 11 | Rule 4 and the third six, T-2, both T-3 fall-backs, **T-6 with shares 4 + 2 and with 6**, the T-13 escape, no rolling once the last piece is home |
| `FirstPlayerSelectorTest`, `LudoGameTest` | 3, 17 | the roll-off and its tie-break; complete seeded games, replay by seed, all four places, gridlock ending seed 79 |
| `GameLogTest` | 10 | every Section 3.1 message, word for word, in lower-case colours, and the gridlock ending |
| `RandomTest`, `MainTest` | 55, 4 | the dice and coin mapping (including 50 repetitions of a real roll), seeded replay, `pick`; seed parsing and the readable error |

(The counts are test cases as JUnit reports them; a parameterised test counts once per row and a
`@RepeatedTest(50)` fifty times, which is how 157 test methods become 224 tests.)

---

## 14. Viva preparation

Likely questions, and short answers you can give from memory.

**Q. Where did the cell numbers come from? The specification has no numbered board.**
From the Legend's sentence plus Figure 1 — see section 2. The four starts come out 13 apart (0, 13, 26,
39) and every approach cell is 50 cells in front of its own start. Two independent checks confirm the
reading: the white cells count to exactly 52, and the derived turn order reproduces the
specification's only worked example, `R -> G`.

**Q. Why is there a `Square` class instead of just using `int`?**
Because the board has four different kinds of place with different owners and different lengths. With
an `int` you can accidentally compare a yellow home-straight index against a blue one, or ask for "the
next cell" after Home. `Square` makes the kind explicit, so `isApproachCellOf` checks
`isRing()` before comparing the index — that guard is the bug class the type exists to prevent.

**Q. Which design patterns are used, and where?**
Template Method (`Player.chooseMove` is `final` and calls abstract `selectMove`), Strategy (the four
`Player` subclasses, and also `SpeedModifier.apply` / `TeleportDestination.squareFor` as
behaviour-per-enum-constant), Observer (`GameListener` — the rules raise events, `GameLog` turns them
into text, tests listen with a mock), Factory (`PlayerFactory`), Flyweight (`Square`'s 80 shared
instances), Command (`PlannedMove` — a fully described action, built before it is carried out), and
Value Object throughout — the four move-data types are Java records.

**Q. Which SOLID principle would you point at first?**
Dependency Inversion, because it is the one that pays off visibly. The rules depend on the
`RandomSource` *interface*, so `java -cp out Main 42` replays a game exactly; and they report to the
`GameListener` *interface*, never to `GameLog` — only `LudoTSimulation` constructs a `GameLog`. The
tests exploit both: a Mockito mock of `RandomSource` forces any random outcome, and a mock
`GameListener` lets a test verify events instead of parsing text. Single Responsibility is the one
that shaped the design — generate / choose / execute as three classes.

**Q. Why `Optional` everywhere?**
Because "there is no move", "the walk reached no cell", "no mystery cell spawned this round" are
normal outcomes, not errors, and a `null` return lets a caller forget them. `Player.chooseMove`,
`MoveGenerator.forcedMove`, `Walk.destination()`, `BlockedAttempt.partialMove()` and
`MysteryCell.onRoundCompleted()` (an `OptionalInt`) all put the "maybe nothing" into the type, so the
compiler makes every caller decide what to do. The one `null` left is a documented "not decided yet":
a piece in its base has no direction until its coin is tossed.

**Q. How do you know it is correct?**
Three ways. 224 JUnit 5 tests, all passing, covering 97.6 % of lines and 94.9 % of branches —
including the specification's own Rule T-3 worked example. `GameLogTest` checks every Section 3.1
message word for word. And 200 full seeded games run with no exceptions, in which the behaviours come
out measurably distinct — over seeds 1–40 red made the most captures (633) and green moved blocks
3055 times against 33–54 for everyone else.

**Q. What is the time complexity?**
Every bound is a constant, because the board never grows. Occupancy lookup is O(1); one walk is at most
12 steps; `distanceToHome` is at most 111; generating all options for one roll is about 50 elementary
steps. Over seeds 1–200 a finished game averages 214 rounds, and one run of `java -cp out Main <seed>`
takes about 0.21 s including JVM start-up and all the output — start-up and output dominate; the rule
engine is not measurable at this scale.

**Q. What was the hardest rule to get right?**
Rule T-1's second half. "A counter-clockwise piece can only enter the home straight if it passes the
approach cell for the **second** time" means a piece needs a *counter*, not a flag, and that counter has
to be updated mid-walk — a doubled six travels 12 cells and can pass the approach cell during the
walk. That is why `PathResolver` threads `piece.approachPasses() + approachArrivals` through every
step rather than reading the piece's stored value.

**Q. Why does a block move not update each piece's direction?**
Because Rule T-4 asks whether "a block is created by two pieces moving in the opposite direction". If
a block move overwrote every member's direction, they would all agree afterwards and the
mixed-direction clause could never fire again. Leaving each piece's own direction intact also keeps
Rule T-5 honest, since it restores the piece's coin-toss direction when it leaves the block.

**Q. What happens if nobody can win?**
It can genuinely happen — the ruleset has no stalemate provision. In the game from seed 79, red ends
up with four pieces on cell 50, yellow three on cell 51, blue four on cell 0 and green a pair on
cell 1: every block runs into a neighbouring opponent block of a different size, which it can neither
pass (Rule T-3) nor capture (Rule T-8 requires equal sizes), and yellow cannot even leave its base
because blue's block sits on yellow's `X`. Rather than inventing a rule that lets a piece through,
`LudoGame` detects it: when no piece has changed square for `GameRules.GRIDLOCK_ROUNDS` (50)
consecutive rounds, the game ends, says why, and lists every unfinished player. Seed 79 freezes after
round 187 and ends after round 237. Over seeds 1–200 six games (3%) end by gridlock, and none reaches
the 2000-round `MAX_ROUNDS` safety net.

**Q. Where are the places you had to interpret the specification?**
Fifteen, all listed in `REPORT.md` §6 and each isolated to one named constant or one commented
method. The ones most worth defending:

- Rule T-13's "rolls value three consecutively" — two successive threes, counting only rolls made
  while that piece is briefed (`PieceEffects.CONSECUTIVE_ESCAPE_ROLLS_TO_LEAVE_BRIEFING`).
- Rule T-6's "six units cumulatively" — shared out with **distinct** shares, 6 / 4+2 / 3+2+1
  (`GameRules.BLOCKADE_BREAK_SHARES`), because an equal 3+3 split puts both pieces on the same cell
  and simply re-forms the blockade one step on.
- "The next four rounds" (T-12, T-13) — the rest of the round the effect began in, plus four full
  rounds; and Rule T-10's "two rounds" — two *full* rounds with a piece on the path.
- Blue's cycle — fixed for a whole round and advanced from the first piece actually moved, with the
  mystery-cell preferences applied to that piece.
- Rule T-7 for a block — every piece must have captured before the block may turn home.
- Colours in lower case — the Legend defines `Color X` as "red, yellow, blue, or green".
- A gridlocked board — fifty rounds with no piece changing square ends the game, since the rules
  have no stalemate provision.

**Q. If you had to add a fifth player behaviour, what would you change?**
One new `Player` subclass and one line in `PlayerFactory`. No existing class changes, because
`TurnEngine` and `LudoGame` only ever see `Player`, and the new behaviour would reuse the existing
`capturingMoves` / `formsNewBlock` / `endsInBlock` / `closestToHome` vocabulary.

**Q. Why does green have both `formsNewBlock` and red `endsInBlock`?**
Because the two sentences ask different things. Green wants to *create* a block — moving a block it
already has creates nothing, and if that counted, green would spend a six shuffling its block instead
of emptying its base. Red wants to *avoid ending up in* a block — and a block move ends in a block just
as much as forming one does. `endsInBlock` is simply `isBlockMove() || formsNewBlock(move)`.

**Q. How can only `Board` move a piece?**
`Piece.setSquare` has no access modifier, so it is visible only inside `ludot.board`, the package that
holds both `Piece` and `Board`. `Board.relocate` calls it and updates the occupancy index in the same
method; every other class — movement, mystery, players, the tests in other packages — can only call
`relocate`. The compiler enforces the single mutation point.
