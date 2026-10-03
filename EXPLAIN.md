# LUDO-T — explain it in plain English

A cheat sheet for the demo and the viva. Every class and method named here exists in `src/`.

## 1. The 30-second pitch

"It is a console simulation of LUDO-T. Four computer players, each with its own strategy, play a full
game with no input. Each turn works in three steps: **`MoveGenerator` lists every legal move,
the player's strategy picks one, and `MoveExecutor` applies it.** The rules live in the generator and
the executor, the strategies only choose, and everything printed goes through one interface,
`GameListener`. All chance (dice, coin, mystery cell) comes from one `RandomSource`, so a seed replays
the same game and tests can control it with Mockito."

## 2. One turn, step by step

1. `LudoGame.playRound` gives each player a turn, in the order decided by `FirstPlayerSelector`.
2. `TurnEngine.playTurn` rolls the `Dice` and the log prints "player rolled N".
3. `TurnEngine` shows the roll to briefed pieces (T-13), and counts consecutive sixes (Rule 4).
4. `MoveGenerator.optionsFor(colour, roll)` builds every legal move: enter the board, move one piece,
   move a block. It uses `PathResolver` to walk the board one cell at a time.
5. `Player.chooseMove` calls the colour's `selectMove`, which picks one move. No move means the
   throw is lost, or a blocked piece moves up to the block (`handleRollThatCannotBePlayed`).
6. `MoveExecutor.execute` applies the move: relocate, toss the coin on entry, capture, teleport.
7. A six or a capture earns another roll (Rule 4, T-2). Otherwise the turn ends.
8. After everybody has played, `LudoGame` prints the round report, moves the mystery cell on and
   ticks the Alpha and Beta timers.

## 3. Where each rule lives

| Rule | Where |
|---|---|
| 2, 3, 4 enter on a six, extra rolls, third six | `MoveGenerator.addEnterBoardMove`, `TurnEngine.playTurn` |
| 5, 6 jump over, capture | `PathResolver.walk`, `capturesOnLanding`, `MoveExecutor.applyCaptures` |
| 7, T-3 own pieces, blocks | `PathResolver.blockerAt`, `TurnEngine.handleRollThatCannotBePlayed` |
| 9, 10, T-1, T-7 home straight | `PathResolver.nextSquare` and `walk` |
| 11 places | `LudoGame.recordIfFinished`, `placings` |
| T-1 coin toss | `Coin`, `MoveExecutor.enterBoard` |
| T-2 bonus roll | `TurnEngine.playTurn` |
| T-4, T-5 block moves, direction | `MoveGenerator.addBlockMoves`, `directionSettingPieceOf`, `travelDirectionOf` |
| T-6 break a blockade | `TurnEngine.handleThirdConsecutiveSix`, `breakUpBlockade`, `GameRules.BLOCKADE_BREAK_SHARES` |
| T-8 blockade captures blockade | `PathResolver.blockerAt`, `MoveExecutor.applyCaptures` |
| T-9 reset on capture | `Piece.resetAfterCapture` |
| T-10 mystery cell | `MysteryCell` |
| T-11, T-14, T-15 teleports | `MysteryEffectResolver`, `TeleportDestination` |
| T-12, T-13 Alpha and Beta effects | `PieceEffects`, `SpeedModifier`, `TurnEngine.releaseBriefedPiecesOnConsecutiveThrees` |

T-15 holds because `resolveLandingOnMysteryCell` is the only way the effects start. Walking onto
cell 7, 25 or 44 never reaches that code.

## 4. The four players, one line each

- **Red** captures whenever it can (victim closest to home first), enters only if it cannot capture,
  and avoids ending in a block.
- **Green** forms new blocks first, then empties its base, then moves blocks, and only captures with a
  piece that still needs a capture to reach home. It breaks a block last.
- **Yellow** enters on every six, captures only with a piece that has not captured yet, otherwise moves
  the piece closest to home.
- **Blue** goes through B1, B2, B3, B4, one step per round. Counter-clockwise pieces go for the mystery
  cell and clockwise pieces avoid it.

## 5. Patterns and principles that are really in the code

| Idea | Example | Why |
|---|---|---|
| Template Method | `Player.chooseMove` is `final` and calls the abstract `selectMove` | No colour can return an illegal move or forget to play |
| Factory | `PlayerFactory.create(colour)` | One place says which colour behaves how |
| Dependency Inversion (the Observer idea, with one listener) | `GameListener` is the interface and `GameLog` prints | The rules never touch `System.out`, and tests use a mock |
| Dependency Injection | `LudoTSimulation` is the only place that calls `new` on the main objects | Every class gets its collaborators through its constructor |
| Flyweight | `Square` creates its 80 squares once and shares them | No allocation while walking the board |
| Immutability and records | `Square`, `PlannedMove`, `PieceMovement`, `MoveOptions`, `BlockedAttempt` | A planned move cannot change while a player compares options |
| `Optional` | `chooseMove`, `forcedMove`, `Walk.destination()` | The compiler forces the "no such move" case to be handled |
| Encapsulation | `Piece.setSquare` is package-private, only `Board.relocate` calls it | The board's index and the piece cannot disagree |

SOLID in one line each. **S:** each class has one job (`GameLog` only prints, `PathResolver` only walks).
**O:** a fifth player is one new subclass. **L:** every `Player` can be used wherever a `Player` is
expected. **I:** `Dice`, `Coin` and `RandomSource` are tiny. **D:** rules depend on `GameListener` and
`RandomSource`, never on the console or `java.util.Random`.

## 6. How the tests work

- 265 JUnit 5 tests in 22 classes, mirroring the packages of `src/`. About 97% line coverage
  (`mvn verify`, report in `target/site/jacoco/index.html`).
- `Fixtures.place(...)` sets up an exact board position, and the expected cell is worked out by hand on
  the numbered board.
- Randomness is controlled: `Dice` is a Mockito mock (`when(dice.roll()).thenReturn(6, 6, 6)`), and
  `Fixtures.fixedRandom` is a mock `RandomSource`.
- `GameLogTest` checks every Section 3.1 message word for word.
  `OutputMatchesBriefTest` plays 40 whole games and fails if any line is not a Section 3.1 message.
- `LudoGameTest` plays whole seeded games and checks one winner, four places and no endless game.

## 7. Choices a marker may question, and the short answer

- **T-6 split of 4+2 (or 3+2+1):** "six units cumulatively" means the pieces share six units. An equal
  split lands the pieces on the same cell and re-forms the blockade.
- **T-13 "three consecutively" is two threes:** one 3 cannot be "consecutive". Only rolls made during the
  briefing count.
- **T-10 "after two rounds":** the round in which the first piece arrives is only partly spent on the
  path, so two *full* rounds are counted.
- **Effects last "the next four rounds":** the rest of the current round does not count, so the piece
  gets four full rounds.
- **Blue's cycle moves once per round:** the brief says "if B1 is moved in the current round, B2 is
  considered in the next".
- **Gridlock rule:** blocks of several colours can freeze the whole board for good. After 50 rounds
  with no piece moving, the game ends. The limit is a safety net, not a game rule.
- **Lower-case colours:** the Legend defines Color X as "red, yellow, blue, or green".

## 8. Likely questions

**Why `Optional` instead of `null`?** A method that returns `Optional` tells the caller there may be no
answer, and the compiler makes them handle it. Some `null`s are left on purpose: `Piece.direction` is
`null` while the piece is in its base, because the coin has not been tossed yet.

**Why a `GameListener` interface?** So the rule classes never print. A test passes a Mockito mock and
verifies the events, and the real program passes `GameLog`.

**How do you know the output matches the brief?** `OutputMatchesBriefTest` checks every printed line of
40 full games against a template for each Section 3.1 message.

**Why separate `MoveGenerator` and `MoveExecutor`?** The generator only reads the board, so a strategy
can compare moves safely. Only the executor changes it, so a chosen move is never applied halfway.

**Why a `Square` class and not an integer?** The board has four kinds of place: the shared ring, home
straights, bases and homes. A `Square` says which one it is, so there are no hidden number ranges.

**How is the game repeatable?** All chance comes from one `RandomSource`. `java -cp out Main 42` replays
the same game every time.

**How efficient is it?** A roll costs at most 12 steps of the walk, and the board lookup is a hash
map, so one game takes a fraction of a second.

**What would you change with more time?** Make each kind of move its own Command object, which would
shrink `MoveExecutor`. I would also add a UML diagram per package.
