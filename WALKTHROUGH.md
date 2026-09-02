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
> Second, **testability**: `RuleChecks` verifies Rule T-14 by passing a stub whose `nextInt` always
> returns `2` — i.e. always "Gamma" — so the test can force the exact situation it wants to check.
> That test runs the real `MysteryEffectResolver`, not a copy of it. Neither of those is possible if
> the rule classes call `Math.random()` directly.

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

**This is the rule engine.** Nine classes: four inert data types, one path walker, one legality
checker, one applier, and one enum. If you only have time to understand one package, understand this
one.

### 6.1 The four data types

These carry information between the three phases of a turn. All four are immutable.

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
public final class PieceMovement {

    private final Piece piece;
    private final Square from;
    private final Square to;
    private final Direction direction;
    private final int stepsTaken;
    private final int approachPassesAtDestination;
```

Six fields describing one piece's journey. `from` and `to` feed the required message *"moves piece R1
**from location 26 to 30**"*; `stepsTaken` feeds *"**by 4 units**"*; `direction` feeds *"in
**clockwise** direction"*.

`approachPassesAtDestination` is the Rule T-1 bookkeeping: `PathResolver` counted how many times this
walk touched the piece's approach cell, and this field carries the **new total** so `MoveExecutor` can
write it back with a single `setApproachPasses`.

> **Why is this a separate class from `PlannedMove`?**
> Because of Rule T-4. A normal move relocates one piece; a block move relocates two, three or four.
> If `PlannedMove` held `from`/`to` directly it would need special-casing everywhere. Instead a
> `PlannedMove` holds a **list** of `PieceMovement`, of length one in the ordinary case, and both the
> executor and the log just loop over it. The four-piece case and the one-piece case are the same code
> path — which is why there is no "block" branch in `MoveExecutor.advance`'s loop body.

#### `PlannedMove` — one fully-checked, not-yet-applied move

```java
public final class PlannedMove {

    private final MoveKind kind;
    private final List<PieceMovement> movements;
    private final List<Piece> capturedPieces;

    public PlannedMove(MoveKind kind, List<PieceMovement> movements, List<Piece> capturedPieces) {
        this.kind = kind;
        this.movements = List.copyOf(movements);
        this.capturedPieces = List.copyOf(capturedPieces);
    }
```

`List.copyOf` makes **unmodifiable defensive copies**. Without it, a caller could keep a reference to
the list it passed in and mutate the move after it had been validated — precisely the kind of hole
that would let a player behaviour change a move after the rules approved it.

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
    /** Opponent pieces sent back to their base by this move (Rules 6 and T-8). */
    public List<Piece> capturedPieces() {
        return capturedPieces;
    }

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

    /** True when the destination is the given standard-path cell (used by the blue strategy). */
    public boolean landsOnRingCell(int cell) {
        Square destination = destination();
        return destination.isRing() && destination.index() == cell;
    }
```

**These questions are the entire vocabulary the player strategies speak.** Read them next to the
specification's player descriptions and the mapping is exact:

| Strategy sentence | Method it uses |
|---|---|
| red: "if any opponent piece can be captured" | `capturesAnything()` |
| red/green/yellow: "moved to X whenever a six is thrown" | `isEnteringBoard()` |
| green: "attempts to move forward using the block move" | `isBlockMove()` |
| blue: "prioritizes landing on the mystery cell" | `landsOnRingCell(mysteryCell.cell())` |
| red: "prioritises capturing the opponent piece closest to its home" | `capturedPieces()` |

`landsOnRingCell` checks `isRing()` before comparing the index — the same namespace discipline as
`isApproachCellOf`. Without it, a move ending on `bluehomepath3` would compare its index `3` against
mystery cell `3` and wrongly report a match.

#### `BlockedAttempt` — a refused move and its fall-back

```java
public final class BlockedAttempt {

    private final Piece piece;
    private final Square from;
    private final Square intendedDestination;
    private final Piece blockingPiece;
    private final PlannedMove partialMove;
```

This class exists to serve two specific required messages:

```
[Color X] piece [Name] is blocked from moving from L1 to L2 by [Color X/Y] piece [Name].
[Color X] does not have other pieces ... Moved the piece to square L3 which is the cell before the block.
```

Mapping the fields onto them: `piece` and `from` give the first two blanks, `intendedDestination` is
**L2** (where it *would* have gone), `blockingPiece` names the culprit, and `partialMove` is the
shortened move ending on **L3**.

```java
    /** True when the piece can at least advance up to the cell before the block. */
    public boolean hasPartialMove() {
        return partialMove != null;
    }
```

The whole reason both possibilities are packaged together: `TurnEngine` asks this one question to
decide between the two Section 3 messages. If the block sits *immediately* in front of the piece there
is no cell before it to move to, `partialMove` is `null`, and the throw is ignored instead.

#### `MoveOptions` — the result of phase 1

```java
public final class MoveOptions {

    private final List<PlannedMove> playableMoves;
    private final List<BlockedAttempt> blockedAttempts;

    public MoveOptions(List<PlannedMove> playableMoves, List<BlockedAttempt> blockedAttempts) {
        this.playableMoves = List.copyOf(playableMoves);
        this.blockedAttempts = List.copyOf(blockedAttempts);
    }
    ...
    public boolean hasBlockedAttempt() {
        return !blockedAttempts.isEmpty();
    }
}
```

Two lists rather than one, because the specification treats them completely differently. The player
chooses freely from `playableMoves`; `blockedAttempts` is consulted **only** when `playableMoves` is
empty, which is exactly the condition in the required message *"does not have other pieces in the
board to move instead of the blocked piece"*.

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

> **Why an enum instead of returning `null` for failure?**
> Because there are *two* different failures and they need opposite treatment. `BLOCKED` must be
> reported to the user and may still produce a partial move; `IMPOSSIBLE` must be silently skipped.
> A single `null` would collapse that distinction, and `MoveGenerator` would have to re-derive it.

```java
    /** The result of walking a piece a given number of cells. */
    public static final class Walk {

        private final Outcome outcome;
        private final Square destination;
        private final int stepsTaken;
        private final int approachArrivals;
        private final Piece blockingPiece;

        private Walk(Outcome outcome, Square destination, int stepsTaken, int approachArrivals,
                Piece blockingPiece) { ... }
```

An immutable result object with a `private` constructor — only `PathResolver` can create one, so a
`Walk` always reflects an actual walk. `destination` means different things per outcome, which the
javadoc spells out:

```java
        /**
         * Where the piece ends up. For {@link Outcome#BLOCKED} this is the furthest cell it could
         * still reach - "the cell before the block" - and it is {@code null} when the block sits
         * immediately in front of the piece.
         */
        public Square destination() {
            return destination;
        }
```

That `null` is the "no room at all" case, and it is what `hasPartialMove()` ends up reporting.

#### `walk` — the main method, line by line

```java
    public Walk walk(Piece piece, Direction direction, int steps, int groupSize) {
        if (!piece.isInPlay() || direction == null || steps <= 0) {
            return new Walk(Outcome.IMPOSSIBLE, null, 0, 0, null);
        }
```

Three guards, each a real case: a piece in its base or at home cannot walk; a piece with no coin toss
yet has no direction; and a sick piece's halved roll can be zero (Rule T-12). All three mean "this
roll cannot be played by this piece", which is `IMPOSSIBLE`.

```java
        Square current = piece.square();
        int approachArrivals = 0;
        Square furthestReached = null;
        int stepsToFurthestReached = 0;
```

Four locals — and note that **none of them touch the piece**. The walk is a simulation: it computes
what *would* happen. `furthestReached` starts as `null` precisely so that "blocked immediately, could
not move at all" is distinguishable from "blocked after three steps".

```java
        for (int step = 1; step <= steps; step++) {
            Square next = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, piece.hasEarnedHomeStraightEntry());
            if (next == null) {
                return new Walk(Outcome.IMPOSSIBLE, null, 0, 0, null);
            }
```

The loop takes one step at a time. The fourth argument is the subtle one:
`piece.approachPasses() + approachArrivals` is the piece's **stored** pass count plus the passes
accumulated *so far in this very walk*. That addition is what makes a single long roll work correctly:
a doubled six travelling 12 cells can reach the approach cell and then continue, and the second half
of the walk must know that the first half already ticked the counter.

`next == null` means `nextSquare` refused — the step would go past Home, which is Rule 10. Note it
returns `stepsTaken = 0`: an inexact roll is not a shortened move, it is **no move**.

```java
            boolean isFinalStep = step == steps;
            Piece blocker = blockerAt(next, piece.colour(), groupSize, isFinalStep);
            if (blocker != null) {
                return new Walk(Outcome.BLOCKED, furthestReached, stepsToFurthestReached,
                        approachArrivals, blocker);
            }
```

`isFinalStep` is essential, because **passing through** a square and **landing on** it obey different
rules. Passing through an opponent block is always forbidden (Rule T-3); landing on one is forbidden
*unless* the arriving group is a blockade of equal size (Rule T-8). One boolean carries that
distinction into `blockerAt`.

When blocked, the walk returns `furthestReached` — the last square it actually stood on. That is
literally *"the cell before the block"* from the required message.

```java
            if (next.isApproachCellOf(piece.colour())) {
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
> The specification says *"G1 can move up until cell 3"*. `RuleChecks` asserts exactly this.

#### `nextSquare` — one step, and three rules in four lines

```java
    private Square nextSquare(Square current, PieceColour colour, Direction direction,
            int approachPasses, boolean entryEarned) {
        if (current.isHomeStraight()) {
            int nextCell = current.index() + 1;
            return nextCell < BoardGeometry.HOME_STRAIGHT_LENGTH
                    ? Square.homeStraight(colour, nextCell)
                    : Square.home(colour);
        }
```

Inside the home straight there is no direction and no wrapping — it is a dead-end corridor of five
cells. From `homepath4` (index 4), `nextCell` is 5, which is not `< 5`, so the next square is **Home**.

This is also where **Rule 10** is enforced, though it takes a moment to see. From Home there is
nowhere to go, and the next branch is what says so:

```java
        if (!current.isRing()) {
            return null;
        }
```

A square that is neither a home straight nor a ring cell is a base or a home. Returning `null` makes
the whole walk `IMPOSSIBLE`. So a piece on `homepath3` asked to move 3 goes `homepath4` → `Home` →
`null`, and the roll is refused — *"the player must roll the exact number to reach home"*. A roll of
exactly 2 lands on Home and completes.

```java
        if (current.isApproachCellOf(colour)
                && entryEarned && approachPasses >= direction.requiredApproachPasses()) {
            return Square.homeStraight(colour, 0);
        }
        return Square.ring(direction.nextRingCell(current.index()));
    }
```

The turn into the home straight, and it is **three rules in one condition**:

- `current.isApproachCellOf(colour)` — **Rule 9**: the approach cell is the only doorway.
- `entryEarned` — **Rule T-7**: the piece must have captured at least once.
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
    private Piece blockerAt(Square square, PieceColour mover, int groupSize, boolean isFinalStep) {
        if (!square.isRing()) {
            return null;
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
                return group.getValue().get(0);
            }
        }
        return null;
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
            Square next = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, piece.hasEarnedHomeStraightEntry());
            if (next == null) {
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

    public int distanceToHome(Piece piece, Direction direction) {
        if (!piece.isInPlay() || direction == null) {
            return UNREACHABLE;
        }
        Square current = piece.square();
        int approachArrivals = 0;
        for (int steps = 1; steps <= MAXIMUM_JOURNEY_LENGTH; steps++) {
            Square next = nextSquare(current, piece.colour(), direction,
                    piece.approachPasses() + approachArrivals, true);
            if (next == null) {
                return UNREACHABLE;
            }
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

The two-argument overload exists for the third case: Rule T-4 needs to ask *"how far from home would
this piece be if it travelled that way?"* about a direction the piece is not currently facing.

Two checkable results, both asserted in `RuleChecks`:

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

        addEnterBoardMove(colour, rollValue, playable);
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
> `addSinglePieceMoves` is the only one that can produce a `BlockedAttempt`, and `addEnterBoardMove`
> produces zero or one move. Passing the accumulators in keeps each helper to a single job and avoids
> three intermediate collections.

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
    public PlannedMove forcedMove(Piece piece, Direction direction, int steps) {
        PathResolver.Walk walk = pathResolver.walk(piece, direction, steps, 1);
        if (!walk.isCompleted()) {
            return null;
        }
        return singlePieceMove(MoveKind.ADVANCE, piece, direction, walk);
    }
```

The one method that steps outside the "one roll, one move" flow. Rule T-6 forces a player to break up
a blockade by moving pieces "by six units cumulatively" — a distance that no dice value produced, so
`TurnEngine` needs a way to ask for an arbitrary number of steps. Returning `null` when the walk did
not complete lets the caller report that a piece was stuck rather than crashing.

#### Rules 2 and 3 — leaving the base

```java
    private void addEnterBoardMove(PieceColour colour, int rollValue, List<PlannedMove> playable) {
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
        Square startSquare = Square.ring(colour.startCell());
        if (board.isBlockedForTravel(startSquare, colour)) {
            // An opponent block is sitting on "X", so there is nowhere to step out to (Rule T-3).
            return;
        }
```

An edge case worth knowing about, because it is the sort of thing a marker probes. If an opponent has
parked a block on your `X`, there is nowhere to step out to: Rule T-3 says you cannot land on an
opponent block, and `X` is a landing. A *lone* opponent piece there is different — it gets captured,
which the next lines handle.

```java
        // The pieces waiting in the base are interchangeable, so the lowest numbered one is used.
        Piece piece = waitingInBase.get(0);
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
            PathResolver.Walk walk = pathResolver.walk(piece, direction, steps, 1);
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

`groupSize = 1` because this is a single piece walking alone — that is what tells `blockerAt` it may
not capture a blockade.

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
            PathResolver.Walk walk = pathResolver.walk(leader, direction, steps, block.size());
            if (!walk.isCompleted()) {
                continue;
            }
```

The walk is resolved **once**, for the piece that sets the direction, and `groupSize = block.size()`
is passed so Rule T-8 can apply. If the block cannot complete its walk it simply does not get a move —
there is no "partial block move" in the specification.

> **Why resolve once rather than walking each piece separately?**
> Because a block is one body. Walked individually, the members could diverge: one might have captured
> and be allowed into its home straight while another has not, and the "block" would tear in half
> mid-move. Resolving once and applying the destination to everyone is what keeps a block a block.
> This is documented as an interpretation in `REPORT.md` §6.

```java
            Square destination = walk.destination();
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

> **A concrete case, from `RuleChecks`.** Two yellow pieces share cell 20, one clockwise, one
> counter-clockwise. Yellow's approach is cell 50, so the clockwise piece has 30 cells to the approach
> plus 6 = **36** to go. The counter-clockwise one must reach cell 50 twice: 22 cells back to 50, then
> a full 52-cell lap, then 6 = **80**. So 80 > 36, the counter-clockwise piece leads, and the block
> moves *backwards*. On a roll of 6 a block of two moves `6 / 2 = 3` cells, landing on cell **17**.

#### The two builders

```java
    private PlannedMove singlePieceMove(MoveKind kind, Piece piece, Direction direction,
            PathResolver.Walk walk) {
        Square destination = walk.destination();
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
        PlannedMove partialMove = walk.destination() == null
                ? null
                : singlePieceMove(MoveKind.PARTIAL_ADVANCE, piece, direction, walk);
        return new BlockedAttempt(piece, piece.square(), intendedDestination, walk.blockingPiece(),
                partialMove);
    }
```

Two extra pieces of information are gathered for the report: `intendedDestination` (the `L2` of the
message, computed by the block-blind walk) and the shortened `partialMove`. The ternary is where
"there was no room to move at all" becomes `null`, which `hasPartialMove()` later turns into the
"ignore the throw" branch.

### 6.4 `MoveExecutor` — "make it happen"

The **only** class that changes the board as the result of a move.

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
            log.playerPieceCounts(board, captured.colour());
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
home straight. This is the mechanical link between "capturing" and "winning", and it is why red — the
player that captures most — also wins most.

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

    private final Board board;
    private final RandomSource randomSource;

    private int roundsWithPiecesOnPath;
    private Integer currentCell;
    private Integer previousCell;
    private int roundsRemaining;
```

Rule T-10 has four separate conditions, and this class is the only place that knows any of them:

> "The mystery cell should appear on the board **after two rounds have passed from pieces in the
> standard path**. The mystery cell can occur randomly at any cell location in the standard path (52
> cells) **on a cell that, at the time of spawning, has no pieces on it**. Once the mystery cell has
> appeared, it will **remain in the same cell for four rounds** and reappear at another random
> location. Mystery cells **cannot appear in the same place consecutively**." — Rule T-10

The four fields map one-to-one onto those conditions: `roundsWithPiecesOnPath` counts towards the
first, the board is consulted for the second, `roundsRemaining` counts down the third, and
`previousCell` enforces the fourth.

> **Why `Integer` and not `int` for `currentCell` and `previousCell`?**
> Because both genuinely have a "there is no such cell" state, and cell **0** is a perfectly valid
> cell. Using `int` would force a sentinel like `-1`, which then has to be remembered and checked
> everywhere. `null` says "not on the board" unambiguously, and `isActive()` names the check.

```java
    /** True when the given square is the mystery cell right now. */
    public boolean isOn(Square square) {
        return isActive() && square.isRing() && square.index() == currentCell;
    }
```

The question `MoveExecutor` asks after every move. Three conditions, in cheap-first order, and again
the `isRing()` guard so a home-straight index is never compared against a ring cell number.

```java
    public Integer onRoundCompleted() {
        if (board.hasAnyPieceOnRing()) {
            roundsWithPiecesOnPath++;
        }

        if (isActive()) {
            roundsRemaining--;
            if (roundsRemaining > 0) {
                return null;
            }
            previousCell = currentCell;
            currentCell = null;
            return spawn();
        }

        return roundsWithPiecesOnPath >= ROUNDS_BEFORE_FIRST_SPAWN ? spawn() : null;
    }
```

The whole life cycle, readable top to bottom, called once per round by `LudoGame`.

- The counter only advances **while there are pieces on the path** — that is what "two rounds have
  passed *from pieces in the standard path*" means. Rounds 1–3 of a typical game, where everyone is
  still stuck in their base waiting for a six, do not count.
- If the cell is already active, tick it down. Still alive → nothing to report. Expired → remember
  where it was (so Rule T-10's "not consecutively" can be enforced) and immediately spawn elsewhere,
  because the rule says it "will … reappear at another random location", not "will disappear".
- If it is not active, spawn as soon as the two-round condition is met.

Returning the newly spawned cell (or `null`) is how `LudoGame` knows whether to print *"A mystery cell
has spawned in location L1…"*.

> **Why return a value instead of logging from here?**
> `MysteryCell` would then need a `GameLog`, and a class whose job is "track where the mystery cell is"
> would acquire a second job, "describe itself to the user". Returning the fact and letting the caller
> narrate keeps them separable — and it is why `RuleChecks` can assert the spawn timing with no output
> at all.

```java
    private Integer spawn() {
        List<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < BoardGeometry.RING_SIZE; cell++) {
            boolean sameCellAsBefore = previousCell != null && previousCell == cell;
            if (!sameCellAsBefore && board.isRingCellEmpty(cell)) {
                candidates.add(cell);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        currentCell = randomSource.pick(candidates);
        roundsRemaining = LIFETIME_IN_ROUNDS;
        return currentCell;
    }
```

Build the list of legal cells, then pick one. Both of Rule T-10's placement conditions are in the
`if`: not the previous cell, and not occupied.

> **Why build a candidate list instead of picking at random and retrying?**
> A retry loop can spin — in the extreme it never terminates — and its running time depends on luck.
> Filtering first is 52 cheap checks with a guaranteed answer, and it makes the impossible case
> ("every cell is occupied or forbidden") explicit rather than a hang. Sixteen pieces can never fill
> 52 cells, so in practice the list is never empty, but the code says what it would do.
>
> It also makes the *distribution* obviously uniform over legal cells, which is what "can occur
> randomly at any cell location" asks for.

`previousCell == cell` compares an `Integer` with an `int`, so Java unboxes the `Integer` — safe here
because the `previousCell != null` check comes first in the same `&&`.

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
to the base if the player rolls value three consecutively"* — depends on the *player's* sequence of
rolls, not on the piece, so it lives in `TurnEngine`. See section 9.2.

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

    /** Rule T-13: the value whose repetition frees a piece from its Beta briefing. */
    public static final int BRIEFING_ESCAPE_ROLL = 3;

    private final PieceColour colour;
    protected final Board board;
    protected final PathResolver pathResolver;

    private int consecutiveEscapeRolls;
```

`colour` is `private` (nobody needs to change it), while `board` and `pathResolver` are `protected`
because the subclasses' helpers need them. `consecutiveEscapeRolls` is Rule T-13's counter.

```java
    public final PlannedMove chooseMove(MoveOptions options, int rollValue) {
        List<PlannedMove> playable = options.playableMoves();
        if (playable.isEmpty()) {
            return null;
        }
        PlannedMove chosen = selectMove(playable, rollValue);
        return chosen != null ? chosen : playable.get(0);
    }

    /** The behaviour of this colour: choose one of the legal moves. */
    protected abstract PlannedMove selectMove(List<PlannedMove> options, int rollValue);
```

This pair is the **Template Method pattern**, and the `final` on `chooseMove` is the whole point.

`chooseMove` fixes two invariants that must hold for every behaviour:

1. **Never ask a behaviour to choose from nothing.** If `playable` is empty it returns `null` at once,
   so no `selectMove` implementation has to handle an empty list — and none can crash on `get(0)`.
2. **Never waste a roll.** If a behaviour returns `null` — a bug, or a filter chain that eliminated
   everything — the fall-back `playable.get(0)` plays a legal move anyway.

Because `chooseMove` is `final`, a subclass **cannot** bypass those invariants. And because
`selectMove` only ever receives `playableMoves`, a behaviour has no way to return an illegal move: it
must return an element of the list it was given.

> **Why Template Method rather than a `MoveStrategy` interface held by a concrete `Player`?**
> Both are defensible; this one was chosen because the four behaviours also need shared *vocabulary* —
> `capturingMoves`, `createsBlock`, `closestToHome` and the rest. With a separate strategy interface
> those helpers would have to live in a utility class and be passed the board and the path resolver on
> every call. As a base class they are simply `protected` methods, and each behaviour reads like the
> paragraph it implements. The polymorphism is the same either way.

```java
    /** Hook for behaviours that keep state between turns; blue uses it to advance its cycle. */
    public void onMoveExecuted(PlannedMove move) {
        // Most behaviours are stateless and have nothing to remember.
    }
```

A **no-op hook**, not abstract. Three of the four behaviours are stateless, so making this abstract
would force three empty overrides. Only `BluePlayer` overrides it, to advance its `B1 → B2 → B3 → B4`
cursor.

#### Rule T-13's roll counter

```java
    public final void recordRoll(int value) {
        consecutiveEscapeRolls = value == BRIEFING_ESCAPE_ROLL ? consecutiveEscapeRolls + 1 : 0;
    }
```

One line implementing "consecutively": a 3 increments the streak, **anything else resets it to zero**.
`TurnEngine` calls this after every roll.

> **Why is this on `Player` and not on `Piece`?**
> Because Rule T-13 says *"if **the player** rolls value three consecutively"*. It is a property of the
> player's sequence of rolls, and it frees *every* briefed piece that player owns, not just one. Putting
> it on the piece would need four synchronised copies of the same streak.

#### The shared vocabulary

```java
    /** Moves that send at least one opponent piece back to its base (Rules 6 and T-8). */
    protected final List<PlannedMove> capturingMoves(List<PlannedMove> options) {
        return options.stream().filter(PlannedMove::capturesAnything).toList();
    }

    /** The move that lifts a piece out of the base onto "X", if a six made one available. */
    protected final PlannedMove enterBoardMove(List<PlannedMove> options) {
        return firstOrNull(options.stream().filter(PlannedMove::isEnteringBoard).toList());
    }

    /** Moves in which a whole block travels together (Rule T-4). */
    protected final List<PlannedMove> blockMoves(List<PlannedMove> options) {
        return options.stream().filter(PlannedMove::isBlockMove).toList();
    }
```

Three one-line filters. They exist so the behaviours read as prose: `RedPlayer` says
`capturingMoves(options)`, not `options.stream().filter(...)`. All are `final` so no behaviour can
redefine what "a capturing move" means.

`enterBoardMove` returns a single move rather than a list because the generator only ever produces one
(the base pieces are interchangeable).

```java
    protected final boolean createsBlock(PlannedMove move) {
        if (move.isBlockMove()) {
            // A block that travels as one body is still a block when it arrives.
            return true;
        }
        Square destination = move.destination();
        if (!destination.isRing()) {
            return false;
        }
        for (Piece piece : board.groupOn(destination, colour)) {
            if (!move.movedPieces().contains(piece)) {
                return true;
            }
        }
        return false;
    }
```

"Would this move leave two or more of my pieces on one cell?" — the definition of a block in Rule T-3.
Used by green (which wants blocks) and by red (which avoids them), from opposite directions.

The logic has three parts:

- a block move arrives as a block, so it trivially qualifies;
- a home straight cannot hold a block that matters — it is private to one colour, and no opponent can
  ever be obstructed there — so `!destination.isRing()` returns `false`;
- otherwise, look at my own pieces already standing on the destination and ask whether any of them is
  **not** part of this move. If one is staying put while another arrives, that is a new block.

That last check is the subtle one. Without `!move.movedPieces().contains(piece)`, a piece moving
*within* its own group would see itself at the destination and wrongly report a block.

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
    protected final PlannedMove closestToHome(List<PlannedMove> options) {
        PlannedMove best = null;
        int bestDistance = PathResolver.UNREACHABLE;
        for (PlannedMove move : options) {
            int distance = pathResolver.distanceToHome(move.primaryPiece());
            if (best == null || distance < bestDistance) {
                best = move;
                bestDistance = distance;
            }
        }
        return best;
    }
```

Yellow's *"moves the piece closest to its home"*, and the tie-break every other behaviour falls back
on. Three details:

- it returns `null` for an empty list, which is what makes the "try this, else try that" chains in the
  behaviours read cleanly — each step can just test the result for `null`;
- the `best == null ||` clause means the first option is always taken even when its distance is
  `UNREACHABLE`, so a list of only-unreachable moves still yields a move rather than `null`;
- strict `<` keeps it deterministic: on a tie the earlier option wins, and the option order is itself
  deterministic because it comes from `piecesInPlay` in `R1..R4` order.

### 8.2 `RedPlayer` — aggressive

> "The red player is a **very aggressive** player who prioritises capturing opponent pieces rather than
> winning the game." — Section 2.1.1

```java
    @Override
    protected PlannedMove selectMove(List<PlannedMove> options, int rollValue) {
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
        PlannedMove enterBoard = enterBoardMove(options);
        if (enterBoard != null) {
            return enterBoard;
        }
```

**Step 2 — the base, but only as a fall-back.** This is where the control flow *is* the rule. The
specification says red brings a piece out only *"unless it cannot capture any piece by moving six
cells"* — and "cannot capture" is precisely the condition of having fallen through step 1. So the
ordering of the two `if`s encodes the rule with no extra test.

```java
        // "Red will always avoid creating blocks unless it is unavoidable."
        List<PlannedMove> withoutNewBlocks = options.stream()
                .filter(move -> !createsBlock(move))
                .toList();
        return closestToHome(withoutNewBlocks.isEmpty() ? options : withoutNewBlocks);
    }
```

**Step 3 — avoid blocks "unless it is unavoidable".** Filter them out; if that empties the list then
blocking genuinely *is* unavoidable, so fall back to the unfiltered list. The `isEmpty() ? options :
filtered` idiom is how "unless unavoidable" is expressed throughout this codebase.

```java
    private PlannedMove mostDamagingCapture(List<PlannedMove> captures) {
        PlannedMove best = null;
        int bestVictimDistance = PathResolver.UNREACHABLE;
        for (PlannedMove move : captures) {
            int victimDistance = shortestVictimDistanceToHome(move);
            if (best == null || victimDistance < bestVictimDistance) {
                best = move;
                bestVictimDistance = victimDistance;
            }
        }
        return best;
    }
```

> "If more than one piece can be captured by moving different red pieces, red prioritises capturing
> **the opponent piece closest to its home**." — Section 2.1.1

"Its home" means the *victim's* home, so red targets the opponent that has made the most progress —
the capture that destroys the most work. `distanceToHome` measures that, and the smallest distance is
the most advanced victim.

`shortestVictimDistanceToHome` takes the minimum across a move's victims, because one move can capture
more than one piece (a blockade capture under Rule T-8, or two different colours sharing a cell after
teleports). A move is judged by the best victim it can reach.

### 8.3 `GreenPlayer` — the blocker

> "The green player prioritises **winning by blocking**." — Section 2.1.2

Green is the only behaviour with a five-level preference ladder, because the specification gives it
three interacting sentences.

```java
    @Override
    protected PlannedMove selectMove(List<PlannedMove> options, int rollValue) {
        PlannedMove newBlock = closestToHome(options.stream().filter(this::createsBlock).toList());
        if (newBlock != null) {
            return newBlock;
        }
```

**Level 1 — form a block.** This outranks even emptying the base, and that ordering comes straight
from the rule's own wording:

> "any pieces in the base will be moved to X whenever a six is thrown, if there are any pieces in the
> base **unless moving six cells enables green to create a block**." — Section 2.1.2

The `unless` clause makes block-creation the higher priority, so it is tested first.

```java
        PlannedMove enterBoard = enterBoardMove(options);
        if (enterBoard != null) {
            return enterBoard;
        }
```

**Level 2 — keep the base empty.** The main clause of the same sentence, reached only when level 1
found nothing.

```java
        PlannedMove blockMove = closestToHome(blockMoves(options));
        if (blockMove != null) {
            return blockMove;
        }
```

**Level 3 — move a whole block forward.** *"Green always attempts to move forward using the block move
explained in Rule T-4."* Moving the block keeps it intact while still making progress, which is
exactly green's strategy.

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

**Level 4 — move something that is not in a block.** *"Green always prioritises moving its other
pieces home before breaking a block."*

**Level 5 — break a block, but only now.** *"Green will only break a block … if and only if the value
of the roll cannot be performed by green using the pieces in front of the block."* Reaching this line
means every single option breaks a block, i.e. the roll cannot be played any other way — which is the
rule's condition, established by exhaustion rather than by a separate test.

> **Does this actually behave like a blocker?** Measurably yes. Over 40 seeded games green moved blocks
> **2369** times; red, yellow and blue managed 44, 29 and 17 between them. The ladder produces the
> intended personality.

### 8.4 `YellowPlayer` — the racer

> "The yellow player **always prioritises winning**. It will not look to capture opponent pieces more
> than what is required to enter the home straight." — Section 2.1.3

```java
    @Override
    protected PlannedMove selectMove(List<PlannedMove> options, int rollValue) {
        // "Yellow always like to keep an empty base. Therefore, anytime a six is thrown, if there
        // are any pieces in the base, they will be moved to X."
        PlannedMove enterBoard = enterBoardMove(options);
        if (enterBoard != null) {
            return enterBoard;
        }
```

**Step 1 — the base, unconditionally.** Note the contrast with green: yellow's rule has no *unless*
clause, so the enter-board move is first with no test in front of it. The two behaviours differ by
exactly the ordering of two `if` statements, which is what the specification differs by.

```java
        List<PlannedMove> capturesThatUnlockHome = capturingMoves(options).stream()
                .filter(move -> stillNeedsACapture(move.primaryPiece()))
                .toList();
        if (!capturesThatUnlockHome.isEmpty()) {
            return closestToHome(capturesThatUnlockHome);
        }
```

**Step 2 — capture, but only where Rule T-7 requires it.** This is the interesting half of yellow.

> "Yellow will prioritise **the pieces that need captures first** to see whether any opponent piece is
> within range." — Section 2.1.3

Combined with *"will not look to capture … more than what is required to enter the home straight"*, the
filter is: only consider captures made by pieces that have **not yet** captured. A yellow piece that
already has its Rule T-7 ticket ignores captures entirely and just runs.

```java
        // "In case no captures could be done, Yellow moves the piece closest to its home by the
        // number specified in the roll."
        return closestToHome(options);
    }

    /** Rule T-7: this piece cannot enter its home straight until it has captured an opponent. */
    private boolean stillNeedsACapture(Piece piece) {
        return !piece.hasEarnedHomeStraightEntry();
    }
```

**Step 3 — pure progress.** The `stillNeedsACapture` helper exists only to give the negation a name, so
the filter above reads as the rule rather than as a double negative.

### 8.5 `BluePlayer` — the cyclic mystery-chaser

> "The blue player is a **random player that prioritises mystery cells**." — Section 2.1.4

Blue is the only behaviour that remembers anything between turns.

```java
public final class BluePlayer extends Player {

    private final MysteryCell mysteryCell;

    /** Number (1..4) of the piece blue considers first; the "B1, then B2, then B3..." cycle. */
    private int nextPieceNumber = 1;
```

`mysteryCell` is injected — blue is the only behaviour that needs to know where it is, which is why
`PlayerFactory` passes it to `BluePlayer` alone.

```java
    @Override
    protected PlannedMove selectMove(List<PlannedMove> options, int rollValue) {
        // "the blue player prioritizes landing on the mystery cell if it is moving counterclockwise"
        PlannedMove mysteryHunt = firstOrNull(options.stream()
                .filter(this::landsOnMysteryCell)
                .filter(move -> move.direction() == Direction.COUNTER_CLOCKWISE)
                .toList());
        if (mysteryHunt != null) {
            return mysteryHunt;
        }
```

**Priority 1 — the counter-clockwise craving.** A counter-clockwise piece that can land on the mystery
cell does so, and this **overrides the cycle**: it is a stated priority, so it can pull blue out of
turn order.

Note `move.direction() == Direction.COUNTER_CLOCKWISE` is null-safe. An enter-board move has a `null`
direction (the coin has not been tossed), and `null == COUNTER_CLOCKWISE` is simply `false` — no
`NullPointerException`, and the right answer.

```java
        // "the blue player prioritizes avoiding landing on the mystery cell if it is moving
        // clockwise", so the cycle is walked once while skipping such moves...
        PlannedMove preferred = firstInCycle(options, true);
        if (preferred != null) {
            return preferred;
        }

        // ...and only if that leaves nothing at all is the cycle walked again without the dodge.
        return firstInCycle(options, false);
    }
```

**Priority 2 — the cycle, with the clockwise dodge.** The aversion is weaker than the craving: it only
makes blue *skip* a piece and try the next one. If skipping leaves nothing at all, the cycle is walked
again without the dodge, so blue never wastes a roll purely out of superstition.

```java
    @Override
    public void onMoveExecuted(PlannedMove move) {
        int movedNumber = move.primaryPiece().number();
        nextPieceNumber = movedNumber % BoardGeometry.PIECES_PER_PLAYER + 1;
    }
```

> "if B1 is moved in the current round, B2 is considered in the next and so on" — Section 2.1.4

`movedNumber % 4 + 1` maps 1→2, 2→3, 3→4 and **4→1**, so the cursor wraps. It is set from the piece
that actually moved, not by blind incrementing, which keeps the cycle correct even when priority 1
jumped the queue.

```java
    private PlannedMove firstInCycle(List<PlannedMove> options, boolean avoidMysteryCell) {
        for (int offset = 0; offset < BoardGeometry.PIECES_PER_PLAYER; offset++) {
            int pieceNumber = (nextPieceNumber - 1 + offset) % BoardGeometry.PIECES_PER_PLAYER + 1;
            for (PlannedMove move : options) {
                if (!involvesPieceNumber(move, pieceNumber)) {
                    continue;
                }
                if (avoidMysteryCell && dodgesMysteryCell(move)) {
                    continue;
                }
                return move;
            }
        }
        return null;
    }
```

Walks the cycle starting from the cursor. The index arithmetic converts between 1-based piece numbers
and 0-based offsets: subtract 1, add the offset, wrap on 4, add 1 back. With `nextPieceNumber = 3` it
visits 3, 4, 1, 2.

The inner loop finds a move belonging to that piece. Returning the first match rather than the best
one is right: blue is *"a random player"*, so within the cycle it has no preference.

> **Why does the cycle sometimes appear to stall on one piece?** Because *"if the piece to be moved is
> movable"* is a real precondition. Early on, B2, B3 and B4 are in the base with no six available, so
> the cycle visits them, finds no move, and comes back to B1. The transcript shows B1 repeating and
> then a clean `B2 → B3 → B4 → B2 → B3 → B4 → B1` rotation once the pieces are out.

```java
    private boolean involvesPieceNumber(PlannedMove move, int pieceNumber) {
        for (Piece piece : move.movedPieces()) {
            if (piece.number() == pieceNumber) {
                return true;
            }
        }
        return false;
    }
```

Uses `movedPieces()` rather than just `primaryPiece()`, so a **block move** counts as involving every
piece in the block. Blue's cycle should be satisfied by B2 moving as part of a block, not only by B2
moving alone.

```java
    private boolean landsOnMysteryCell(PlannedMove move) {
        return mysteryCell.isActive() && move.landsOnRingCell(mysteryCell.cell());
    }

    /** A clockwise piece would rather not step onto the mystery cell. */
    private boolean dodgesMysteryCell(PlannedMove move) {
        return landsOnMysteryCell(move) && move.direction() == Direction.CLOCKWISE;
    }
}
```

`isActive()` first, because `cell()` is only meaningful when the mystery cell is on the board — for the
first few rounds it is not, and blue then behaves as a plain cyclic player.

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
     * cumulatively". Cumulatively means the six units are shared out between the pieces that move,
     * in the same way that Rule T-4 divides a roll between the pieces of a block.
     */
    public static final int BLOCKADE_BREAK_UNITS = 6;

    /**
     * Rule T-13: a piece at a Beta briefing escapes to its base if "the player rolls value three
     * consecutively".
     *
     * <p><b>Interpretation.</b> The rule names the <em>value</em> three but not how many times in a
     * row it must appear; "consecutively" needs at least two rolls to mean anything, so two
     * successive threes are used here.
     */
    public static final int CONSECUTIVE_THREES_TO_LEAVE_BRIEFING = 2;
```

**These two are the honest admissions**, and gathering them here is deliberate: an examiner who reads
a rule differently can change the interpretation in one line, and both are cross-referenced from
`REPORT.md` §6.

```java
    /**
     * A safety net rather than a rule. Rule T-7 only lets a piece enter its home straight after it
     * has captured an opponent, so an unlucky run of dice can keep a simulation going for a very
     * long time. The limit guarantees the program always terminates and says so when it stops.
     */
    public static final int MAX_ROUNDS = 2000;

    /**
     * Another safety net. Rules 4 and T-2 both grant extra rolls, and although a chain of captures
     * is naturally limited by the twelve opponent pieces on the board, a hard cap makes it
     * impossible for one turn to run away.
     */
    public static final int MAX_ROLLS_PER_TURN = 24;

    /** Places 1st to 3rd decide the game; the remaining player is last by elimination. */
    public static final int PLACES_TO_DECIDE = 3;
```

The two safety nets are labelled as such, so nobody mistakes them for rules. `MAX_ROUNDS` matters:
measured over 200 seeded games it is reached **twice (1%)**, because the block rules can genuinely
deadlock — see section 14.

### 9.2 `TurnEngine` — one turn, and every extra roll

A turn is *not* one roll. Rule 4 grants extra rolls for sixes, Rule T-2 grants one for every capture,
and Rule T-6 turns the third six into a forced blockade break-up.

```java
    public void playTurn(Player player) {
        int consecutiveSixes = 0;

        for (int rollNumber = 1; rollNumber <= GameRules.MAX_ROLLS_PER_TURN; rollNumber++) {
            int value = dice.roll();
            log.diceRolled(player.colour(), value);

            player.recordRoll(value);
            releaseBriefedPiecesOnConsecutiveThrees(player);
```

The loop is bounded by `MAX_ROLLS_PER_TURN` rather than being a `while (true)`, so no turn can run
away. `consecutiveSixes` is a **local**, which is exactly right: Rule 4's streak is per turn, and it
resets naturally when the method returns.

`recordRoll` then the Rule T-13 check, in that order — the streak must include the roll just made
before it is tested.

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
            if (!earnedAnotherRoll) {
                return;
            }
        }
    }
```

The turn continues on two conditions, one from each rule: a six (Rule 4) **or** a capture (Rule T-2).
Anything else ends the turn.

> **Why is a capture-bonus roll able to reset the six streak?**
> Because it is a genuine roll of the dice. If a player rolls 6, then captures with the bonus roll and
> rolls a 2, the streak is broken — the next 6 starts counting from one again. That follows from
> reading "consecutive" as "consecutive rolls", which is the only reading available.

```java
    private boolean playSingleRoll(Player player, int value) {
        MoveOptions options = moveGenerator.optionsFor(player.colour(), value);
        PlannedMove chosen = player.chooseMove(options, value);
        if (chosen != null) {
            return applyMove(player, chosen);
        }
        return handleRollThatCannotBePlayed(player, options);
    }
```

**The three phases, in five lines.** Generate, choose, execute. This is the method to point at when
asked how the program is structured.

```java
    private boolean handleRollThatCannotBePlayed(Player player, MoveOptions options) {
        if (!options.hasBlockedAttempt()) {
            log.rollCannotBeUsed(player.colour());
            return false;
        }

        BlockedAttempt attempt = options.blockedAttempts().get(0);
        log.pieceIsBlocked(attempt);
        if (attempt.hasPartialMove()) {
            log.blockedButMovedUpToTheBlock(player.colour(), attempt.partialMove());
            return applyMove(player, attempt.partialMove());
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
   which is the cell before the block."*
3. **Blocked with no room** — the block is immediately adjacent, so *"Ignoring the throw and moving on
   to the next player."*

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
    private void releaseBriefedPiecesOnConsecutiveThrees(Player player) {
        if (player.consecutiveEscapeRolls() < GameRules.CONSECUTIVE_THREES_TO_LEAVE_BRIEFING) {
            return;
        }

        boolean anyReleased = false;
        for (Piece piece : board.piecesOf(player.colour())) {
            if (piece.effects().isAttendingBriefing()) {
                log.briefingEndedByConsecutiveThrees(piece);
                board.relocate(piece, Square.base(piece.colour()));
                piece.resetAfterCapture();
                anyReleased = true;
            }
        }
        if (anyReleased) {
            player.clearConsecutiveEscapeRolls();
        }
    }
```

Rule T-13's escape clause:

> "during the next four rounds, the piece will be **teleported to the base** if the player rolls value
> three consecutively." — Rule T-13

Every briefed piece the player owns is freed — the rule says "the piece", but the trigger is a property
of the player, so any piece it applies to is released. `resetAfterCapture` is reused because a piece
in a base must not keep a direction or a history; it is the same "back to the start" state.

The streak is cleared **only if something was actually released**, so a player who rolls threes with no
briefed pieces keeps accumulating — and the very next briefing is released at once. That is the more
literal reading of the rule.

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

The `pieces.size() < MINIMUM_BLOCK_SIZE` re-check inside the loop matters because `blockades` is a
**snapshot** taken before any pieces moved. Breaking up one blockade can dissolve another (a piece
moving out of block A might have been the second member of block B), so the group is re-read from the
board and skipped if it is no longer a block.

```java
    private void breakUpBlockade(Player player, List<Piece> blockade) {
        List<Piece> leaving = piecesLeavingTheBlockade(blockade);
        int stepsEach = GameRules.BLOCKADE_BREAK_UNITS / leaving.size();

        for (Piece piece : leaving) {
            PlannedMove move = moveGenerator.forcedMove(piece, piece.initialDirection(), stepsEach);
            if (move == null) {
                log.blockadePieceCannotBeMoved(piece, stepsEach);
                continue;
            }
            applyMove(player, move);
        }
    }

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
  exactly what `initialDirection` stores.
- **`6 / leaving.size()`** — "cumulatively" read as *shared out*, the same arithmetic Rule T-4 uses. A
  blockade of 2 moves one piece 6 cells; a blockade of 3 moves two pieces 3 cells each; a blockade of 4
  moves three pieces 2 cells each.
- **which piece stays** — the specification does not say, so the piece **closest to home** is kept
  (sort ascending by distance, drop the first with `subList(1, …)`), on the reasoning that it gains
  least from being pushed on.

A piece that cannot travel its share — blocked, or would overshoot home — is reported and left where it
is, rather than crashing.

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

        for (int round = 1; round <= GameRules.MAX_ROUNDS; round++) {
            log.roundHeader(round);
            playRound(turnOrder);
            reportEndOfRound();

            if (finishingOrder.size() >= GameRules.PLACES_TO_DECIDE) {
                log.announceFinalStandings(finishingOrder, board);
                return;
            }
        }

        log.gameStoppedAtRoundLimit(GameRules.MAX_ROUNDS);
        log.announceFinalStandings(finishingOrder, board);
    }
```

The shortest interesting method in the program, and that is the point: `LudoGame` knows the *shape* of
a game and delegates everything else.

The bounded `for` rather than `while (nobody has won)` guarantees termination. Both exits print the
final standings, so a deadlocked game still reports what it achieved.

```java
    private void playRound(List<PieceColour> turnOrder) {
        for (PieceColour colour : turnOrder) {
            if (finishingOrder.contains(colour)) {
                // All four pieces are already home, so this player has nothing left to move.
                continue;
            }
            log.blankLine();
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

> "The first player to bring all its pieces home wins the game. The game may continue to find second,
> third, and fourth places." — Rule 11

`finishingOrder` is the placings table. The `size() == 1` test is what makes *"[Color X] player
wins!!!"* print for the winner only; second and third are reported in the final standings.

This is checked **after every turn**, not at the end of the round, so the winner is announced at the
moment it happens.

```java
    private void reportEndOfRound() {
        log.blankLine();
        for (PieceColour colour : PieceColour.values()) {
            log.playerPieceCounts(board, colour);
            log.pieceLocations(board, colour);
        }

        Integer spawnedCell = mysteryCell.onRoundCompleted();
        if (spawnedCell != null) {
            log.mysteryCellSpawned(spawnedCell);
        }
        log.mysteryCellStatus(mysteryCell);

        for (Piece piece : board.allPieces()) {
            piece.effects().onRoundCompleted();
        }
    }
```

Section 3's *"After each round, status of each player has to be shown"*, plus the two clocks.

> **Why are the clocks advanced here and nowhere else?**
> Because "a round" has to mean exactly one thing across the whole program. Rule T-10's four-round
> lifetime, Rule T-12's four-round aura and Rule T-13's four-round briefing must all tick on the same
> boundary. Doing it in one method, once, is what guarantees that. If the mystery cell were advanced in
> `TurnEngine` and the auras here, "four rounds" would silently mean two different things.

The mystery cell is advanced **before** the status line is printed, so *"will be at that location for
the next N values"* shows the freshly-decremented count — and a cell that has just spawned correctly
reports 4.
