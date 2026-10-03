package ludot.mystery;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import ludot.board.BoardSpecification;
import ludot.board.BoardSquare;
import ludot.board.GameBoard;
import ludot.random.RandomnessProvider;

/** Rule T-10: when and where the mystery cell appears, and how long it stays. */
public final class MysteryCellScheduler {

    public static final int ROUNDS_BEFORE_FIRST_SPAWN = 2;

    public static final int LIFETIME_IN_ROUNDS = 4;

    private static final int NO_CELL = -1;

    private final GameBoard board;
    private final RandomnessProvider randomSource;

    private boolean piecesWereOnPathAtRoundStart;
    private int fullRoundsWithPiecesOnPath;
    private int currentCell = NO_CELL;
    private int previousCell = NO_CELL;
    private int roundsRemaining;

    public MysteryCellScheduler(GameBoard board, RandomnessProvider randomSource) {
        this.board = board;
        this.randomSource = randomSource;
    }

    public boolean isActive() {
        return currentCell != NO_CELL;
    }

    public int getCell() {
        if (!isActive()) {
            throw new IllegalStateException("The mystery cell has not spawned yet");
        }
        return currentCell;
    }

    public int getRoundsRemaining() {
        return roundsRemaining;
    }

    public boolean isOn(BoardSquare square) {
        return isActive() && square.isRing() && square.index() == currentCell;
    }

    public OptionalInt finishRound() {
        // T-10: only full rounds count, so the round the first piece arrives in is skipped.
        boolean piecesAreOnPath = board.hasAnyPieceOnRing();
        if (piecesWereOnPathAtRoundStart && piecesAreOnPath) {
            fullRoundsWithPiecesOnPath++;
        }
        piecesWereOnPathAtRoundStart = piecesAreOnPath;

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

    private OptionalInt spawn() {
        List<Integer> candidates = IntStream.range(0, BoardSpecification.RING_SIZE)
                .filter(cell -> cell != previousCell && board.isRingCellEmpty(cell))
                .boxed()
                .toList();
        currentCell = randomSource.pick(candidates);
        roundsRemaining = LIFETIME_IN_ROUNDS;
        return OptionalInt.of(currentCell);
    }
}
