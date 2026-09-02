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
- [4. Package `ludot.piece`](#4-package-ludotpiece)
- [5. Package `ludot.random`](#5-package-ludotrandom)
- [6. Package `ludot.movement`](#6-package-ludotmovement)
- [7. Package `ludot.mystery`](#7-package-ludotmystery)
- [8. Package `ludot.player`](#8-package-ludotplayer)
- [9. Package `ludot.game`](#9-package-ludotgame)
- [10. Package `ludot.ui`](#10-package-ludotui)
- [11. Wiring it all together](#11-wiring-it-all-together)
- [12. Four worked traces from a real game](#12-four-worked-traces-from-a-real-game)
- [13. The test harness](#13-the-test-harness)
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

Code is quoted exactly as it appears in `src/`. Where a method is long it is broken into chunks with
the explanation between them.

### The 36 classes at a glance

```
src/
  Main.java                       the entry point: 6 lines of real work
  ludot/
    LudoTSimulation.java          the composition root: builds every object once

    board/                        WHERE things are - plain geometry and occupancy
      PieceColour.java            the 4 colours; each knows its own X and approach cell
      Direction.java              clockwise / counter-clockwise, and what each implies
      BoardGeometry.java          the board's fixed numbers (52, 5, 4, Alpha/Beta/Gamma)
      Square.java                 an immutable "where is this?" value; 72 shared instances
      Board.java                  the occupancy index and every block question

    piece/                        WHAT a piece knows about itself
      Piece.java                  identity, position, direction, captures, approach passes
      PieceEffects.java           the two four-round timers of Rules T-12 and T-13
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
      PlannedMove.java            one fully-checked, not-yet-applied move
      PieceMovement.java          where one piece inside that move would end up
      BlockedAttempt.java         a move Rule T-3 refused, plus its fall-back
      MoveOptions.java            the playable moves and the refused ones
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
      LudoGame.java               rounds, turn order, end-of-round report, placings
      TurnEngine.java             one turn, including every extra roll
      FirstPlayerSelector.java    the opening roll-off
      GameRules.java              the numeric rules and the documented interpretations

    ui/
      GameLog.java                every line the program prints, one method per message
test/
  RuleChecks.java                 61 deterministic assertions against the rule book
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
                      ├─ player.recordRoll(4)            Rule T-13 bookkeeping
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
                           │   │   └─ RedPlayer.selectMove(...)  -> one PlannedMove
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
3. **A rule is implemented once.** Rule T-3 (blocks) lives inside `PathResolver.blockerAt`. Nothing
   else in the program knows what a block is allowed to do.
4. **The three phases are independently testable.** `RuleChecks` calls `MoveGenerator` directly and
   inspects the returned `MoveOptions` without ever running a game.

### 1.3 Who is allowed to change what

Mutable state in this program is deliberately tiny:

| State | Who may change it | How |
|---|---|---|
| a piece's position | `Board` only | `Board.relocate(piece, square)` |
| a piece's direction, captures, approach passes | `MoveExecutor`, `MysteryEffectResolver` | after a move / a teleport |
| a piece's Alpha / Beta timers | `PieceEffects` | `applyAlphaAura`, `beginBriefing`, `onRoundCompleted` |
| the mystery cell's position | `MysteryCell` only | `onRoundCompleted()` |
| blue's cycle cursor | `BluePlayer` only | `onMoveExecuted(move)` |

Everything else — `Square`, `PlannedMove`, `PieceMovement`, `MoveOptions`, `BlockedAttempt`,
`PathResolver.Walk` — is **immutable**. That is why the program has no "who moved my piece?" class of
bug.

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
> uses for movement, the two can never disagree — and `RuleChecks` asserts `R -> G` directly.

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

This package answers exactly one question: **where is everything?** It contains no rules about
*movement* — those live in `ludot.movement`. Keeping the two apart is why the geometry can be checked
independently of the rules.

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

#### The 72 shared instances (Flyweight)

```java
    /*
     * A LUDO-T board contains exactly 72 distinct squares: 52 shared standard cells plus a base, a
     * five-cell home straight and a home for each of the four colours. Since a Square is immutable,
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

The `static { … }` block runs once, the first time the class is touched, and builds all 72 squares:
52 ring + 4 × (5 + 1 + 1) = 52 + 28 = **72**.

`EnumMap` rather than `HashMap` because the keys are enum constants: an `EnumMap` is internally just
an array indexed by `ordinal()`, so lookups are array accesses with no hashing at all.

> **What does the Flyweight actually buy here?**
> `PathResolver.walk` takes up to 12 steps, and each step asks for the next square. Without sharing,
> a single game (~210 rounds, thousands of candidate moves) would allocate hundreds of thousands of
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
> This is also why `Piece.setSquare` carries a "do not call this directly" warning: it is the second
> half of `relocate`, not a public API. Java has no "visible to one other package" access level, so
> the restriction is documented rather than compiler-enforced.

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

```java
    public boolean isBlockedForTravel(Square square, PieceColour mover) {
        for (Map.Entry<PieceColour, List<Piece>> group : groupsOn(square).entrySet()) {
            if (group.getKey() != mover && group.getValue().size() >= MINIMUM_BLOCK_SIZE) {
                return true;
            }
        }
        return false;
    }
```

"Does an **opponent** hold a block here?" — `group.getKey() != mover` skips the mover's own pieces,
because Rule T-3 only forbids *opponents* from jumping a block; your own block is yours to hop over.
Used in one place: checking that a piece leaving the base has somewhere to land.

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
tops out at 72 entries.

---

## 4. Package `ludot.piece`

Where `ludot.board` answers *where is everything*, this package answers *what does one piece know
about itself*. Crucially, a `Piece` **does not know how to move** — that is `ludot.movement`'s job.

> **Why separate "a piece's state" from "how a piece moves"?**
> Moving depends on the whole board (are there blocks in the way?), so a `move()` method on `Piece`
> would need a reference back to the `Board`, and every piece would be entangled with every other.
> Keeping `Piece` as a small record of its own facts is what lets `PathResolver` be a single readable
> class instead of logic smeared across sixteen objects.

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

### 4.2 `PieceEffects` — the two four-round timers

```java
public final class PieceEffects {

    /** Rules T-12 and T-13 both last "the next four rounds". */
    public static final int EFFECT_DURATION_IN_ROUNDS = 4;

    private SpeedModifier speedModifier = SpeedModifier.NORMAL;
    private int speedRoundsRemaining;
    private int briefingRoundsRemaining;
```

Two effects, two countdowns. `speedModifier` remembers *which* aura; `speedRoundsRemaining` and
`briefingRoundsRemaining` remember *how much longer*. Java initialises `int` fields to `0`, so a fresh
piece starts with no effects.

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
    }

    /** Rule T-13: the piece is sent to a briefing at Beta and cannot move for four rounds. */
    public void beginBriefing() {
        this.briefingRoundsRemaining = EFFECT_DURATION_IN_ROUNDS;
    }

    /** True while Rule T-13 forbids this piece from moving. */
    public boolean isAttendingBriefing() {
        return briefingRoundsRemaining > 0;
    }
```

Both setters *overwrite* rather than accumulate: landing on Alpha twice in three rounds gives four
fresh rounds of the new aura, it does not stack to eight. The specification says "within the next four
rounds" of the teleport, so re-teleporting restarts the clock.

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
    /** Advances both countdowns by one round. Called once per round for every piece. */
    public void onRoundCompleted() {
        if (speedRoundsRemaining > 0) {
            speedRoundsRemaining--;
            if (speedRoundsRemaining == 0) {
                speedModifier = SpeedModifier.NORMAL;
            }
        }
        if (briefingRoundsRemaining > 0) {
            briefingRoundsRemaining--;
        }
    }

    /** Rule T-9: a captured piece loses every piece of information it carried. */
    public void clear() {
        speedModifier = SpeedModifier.NORMAL;
        speedRoundsRemaining = 0;
        briefingRoundsRemaining = 0;
    }
}
```

`onRoundCompleted` is the *only* place time moves. The `> 0` guards stop the counters going negative,
and resetting `speedModifier` to `NORMAL` when the count hits zero keeps the state tidy rather than
leaving a spent `HALVED` lying around.

`clear()` implements Rule T-9's "all information in that piece will be reset" for the effects half;
`Piece.resetAfterCapture()` does the rest and calls this.

### 4.3 `Piece` — sixteen small records of state

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
Rule T-8 talks about incrementing "the number of captures for each piece", and yellow's strategy asks
which pieces still need one.

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
