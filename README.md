# LUDO-T — Simulation

A command-line simulation of *LUDO with a TWIST*. Four programmed players play a complete game with
no user interaction.

## Build and run

The project is a Maven build (Java 21, JUnit 5, Mockito). The sources keep a plain `src/` and
`test/` layout, so the simulation also builds with `javac` alone.

```bash
# run all unit tests
mvn test

# tests plus a coverage report in target/site/jacoco/index.html
mvn verify

# build and play without Maven
javac -d out $(find src -name '*.java')
java -cp out Main          # a new random game
java -cp out Main 42       # replay the exact same game (any whole number is a seed)
```

In IntelliJ: open `pom.xml` as a project, then run `Main` or the `test` folder.

## Where to look first

Read the code in the order a turn happens:

1. **`ludot/board/PieceColour`** — the whole board geometry derives from one number per colour.
2. **`ludot/board/Square`** — how a position is represented.
3. **`ludot/board/Board`** — who is standing where; the only place a piece can be moved.
4. **`ludot/movement/PathResolver`** — the heart of the rules: walking one cell at a time.
5. **`ludot/movement/MoveGenerator`** — turns "red rolled a 4" into the list of legal moves.
6. **`ludot/player/RedPlayer`** (and the other three) — one behaviour each, one method each.
7. **`ludot/movement/MoveExecutor`** — applies the chosen move: relocate, capture, teleport.
8. **`ludot/game/TurnEngine`** — one turn, including the extra rolls of Rules 4 and T-2.
9. **`ludot/game/LudoGame`** — rounds, turn order, end-of-round report, placings.

Three files are worth knowing about on their own:

- **`ludot/ui/GameListener`** — every event the game can raise. The rules depend only on this
  interface, which is why the tests can replace it with a Mockito mock.
- **`ludot/ui/GameLog`** — the console implementation: every line the program prints, one method
  per required message.
- **`ludot/game/GameRules`** — the numeric rules, and the documented interpretations of the
  specification's ambiguous wording.

## Package layout

```
src/
  Main.java                       entry point and seed parsing
  ludot/
    LudoTSimulation.java          composition root: wires everything together
    board/       PieceColour, Direction, BoardGeometry, Square, Board, Piece
    effects/     PieceEffects, SpeedModifier
    movement/    PathResolver, MoveGenerator, MoveExecutor,
                 PlannedMove, PieceMovement, MoveOptions, BlockedAttempt, MoveKind
    mystery/     MysteryCell, MysteryEffectResolver, TeleportDestination
    player/      Player, RedPlayer, GreenPlayer, YellowPlayer, BluePlayer, PlayerFactory
    game/        LudoGame, TurnEngine, FirstPlayerSelector, GameRules
    random/      RandomSource, SeededRandomSource, Dice, Coin
    ui/          GameListener, GameLog
test/
  MainTest.java
  ludot/         one test class per production class (224 JUnit 5 tests), plus Fixtures
```

## The board numbering

Derived from the Legend ("numbering starts with the Yellow starting square and continues clockwise…
zero (0) to 51") read against Figure 1:

| Colour | Start `X` | Approach | Home straight        |
|--------|-----------|----------|----------------------|
| Yellow | 0         | 50       | `yellowhomepath0..4` |
| Blue   | 13        | 11       | `bluehomepath0..4`   |
| Red    | 26        | 24       | `redhomepath0..4`    |
| Green  | 39        | 37       | `greenhomepath0..4`  |

Alpha, Beta and Gamma are the 9th, 27th and 46th cells from the yellow approach cell (Rule T-11),
which is cells **7**, **25** and **44**.

Turn order is **yellow → blue → red → green**, matching the specification's *"if R rolled the dice,
the next player to roll would be G"*.

## Testing

Every production class has its own test class in the same package under `test/`. Each test sets up
an exact board position by hand, so the expected cells were worked out on the numbered board rather
than copied from the code. Randomness never leaks into a unit test: the dice, the coin and the
mystery cell all take their chance from `RandomSource` or `Dice`, which the tests replace with
Mockito mocks (`Fixtures.fixedRandom`, `when(dice.roll()).thenReturn(6, 6, 6)`), and the printed
output is checked by verifying `GameListener` events or by capturing `GameLog` word for word.

`mvn verify` reports about 98% line and 95% branch coverage.

## Documentation

| File | What it is for |
|---|---|
| `README.md` | this file — how to build and run, and where to look first |
| `REPORT.md` | the design report: structures, justification, SOLID and patterns, efficiency, the rule-to-class map, and the documented interpretations |
| `WALKTHROUGH.md` | a line-by-line explanation of every class and method, plus worked traces from real games — the one to read before explaining or defending the code |
