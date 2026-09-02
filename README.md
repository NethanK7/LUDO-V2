# LUDO-T — Simulation

A command-line simulation of *LUDO with a TWIST*. Four programmed players play a complete game with
no user interaction.

## Build and run

```bash
# compile everything (simulation + rule checks)
javac -d out $(find src test -name '*.java')

# play a new random game
java -cp out Main

# replay the exact same game (any number works as a seed)
java -cp out Main 42

# run the 61 rule checks
java -cp out RuleChecks
```

In IntelliJ: mark `src` as the sources root and `test` as a test root, then run `Main`.

## Where to look first

Read the code in the order a turn happens:

1. **`ludot/board/PieceColour`** — the whole board geometry derives from one number per colour.
2. **`ludot/board/Square`** — how a position is represented.
3. **`ludot/board/Board`** — who is standing where.
4. **`ludot/movement/PathResolver`** — the heart of the rules: walking one cell at a time.
5. **`ludot/movement/MoveGenerator`** — turns "red rolled a 4" into the list of legal moves.
6. **`ludot/player/RedPlayer`** (and the other three) — one behaviour each, one method each.
7. **`ludot/movement/MoveExecutor`** — applies the chosen move: relocate, capture, teleport.
8. **`ludot/game/TurnEngine`** — one turn, including the extra rolls of Rules 4 and T-2.
9. **`ludot/game/LudoGame`** — rounds, turn order, end-of-round report, placings.

Two files are worth knowing about on their own:

- **`ludot/ui/GameLog`** — every line the program prints, one method per required message.
- **`ludot/game/GameRules`** — the numeric rules, and the documented interpretations of the
  specification's ambiguous wording.

## Package layout

```
src/
  Main.java                       entry point
  ludot/
    LudoTSimulation.java          composition root: wires everything together
    board/       PieceColour, Direction, BoardGeometry, Square, Board
    piece/       Piece, PieceEffects, SpeedModifier
    movement/    PathResolver, MoveGenerator, MoveExecutor,
                 PlannedMove, PieceMovement, MoveOptions, BlockedAttempt, MoveKind
    mystery/     MysteryCell, MysteryEffectResolver, TeleportDestination
    player/      Player, RedPlayer, GreenPlayer, YellowPlayer, BluePlayer, PlayerFactory
    game/        LudoGame, TurnEngine, FirstPlayerSelector, GameRules
    random/      RandomSource, SeededRandomSource, Dice, Coin
    ui/          GameLog
test/
  RuleChecks.java                 61 deterministic rule assertions
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

## Documentation

| File | What it is for |
|---|---|
| `README.md` | this file — how to build and run, and where to look first |
| `REPORT.md` | the design report: structures, justification, SOLID and patterns, efficiency, the rule-to-class map, and the nine documented interpretations |
| `WALKTHROUGH.md` | a line-by-line explanation of every class and method, plus worked traces from real games — the one to read before explaining or defending the code |
