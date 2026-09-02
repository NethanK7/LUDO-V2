package ludot.mystery;

import java.util.ArrayList;
import java.util.List;
import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.Square;
import ludot.random.RandomSource;

/**
 * The wandering mystery cell (Rule T-10).
 *
 * <p>The rule has four separate conditions, and this class is the only place that knows about them:
 *
 * <ul>
 *   <li>it appears only "after two rounds have passed from pieces in the standard path";</li>
 *   <li>it spawns on a random standard cell that has no piece on it at that moment;</li>
 *   <li>it lives for exactly four rounds, then immediately reappears somewhere else;</li>
 *   <li>it never reappears on the cell it has just left.</li>
 * </ul>
 *
 * <p>Time only advances through {@link #onRoundCompleted()}, which the game calls once at the end of
 * each round, so the whole life cycle can be read top to bottom in one method.
 */
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

    public MysteryCell(Board board, RandomSource randomSource) {
        this.board = board;
        this.randomSource = randomSource;
    }

    /** True once the mystery cell is somewhere on the board. */
    public boolean isActive() {
        return currentCell != null;
    }

    /** The standard-path cell it currently occupies. Only meaningful while {@link #isActive()}. */
    public int cell() {
        return currentCell;
    }

    /** Rounds it will still stay where it is, used by the end-of-round status report. */
    public int roundsRemaining() {
        return roundsRemaining;
    }

    /** True when the given square is the mystery cell right now. */
    public boolean isOn(Square square) {
        return isActive() && square.isRing() && square.index() == currentCell;
    }

    /**
     * Advances the mystery cell by one round.
     *
     * @return the cell it has just spawned on, or {@code null} if nothing spawned this round.
     */
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

    /** Picks a fresh home for the mystery cell, or leaves it off the board if none is free. */
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
}
