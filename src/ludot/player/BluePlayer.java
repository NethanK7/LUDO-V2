package ludot.player;

import java.util.List;
import ludot.board.Board;
import ludot.board.BoardGeometry;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.mystery.MysteryCell;
import ludot.piece.Piece;

/**
 * Blue: "a random player that prioritises mystery cells" (Section 2.1.4).
 *
 * <p>Blue is the only behaviour with memory. It "always moves in a cyclic manner. That is, if B1 is
 * moved in the current round, B2 is considered in the next and so on", so a cursor remembers which
 * piece comes next and is advanced in {@link #onMoveExecuted(PlannedMove)}.
 *
 * <p>On top of the cycle sit blue's two feelings about the mystery cell: a counter-clockwise piece
 * wants to land on it, and a clockwise piece wants to stay off it. The counter-clockwise craving is
 * the stronger of the two and can pull blue out of turn; the clockwise aversion only makes blue skip
 * a piece and try the next one in the cycle.
 */
public final class BluePlayer extends Player {

    private final MysteryCell mysteryCell;

    /** Number (1..4) of the piece blue considers first; the "B1, then B2, then B3..." cycle. */
    private int nextPieceNumber = 1;

    public BluePlayer(Board board, PathResolver pathResolver, MysteryCell mysteryCell) {
        super(PieceColour.BLUE, board, pathResolver);
        this.mysteryCell = mysteryCell;
    }

    @Override
    public String behaviourSummary() {
        return "cyclic - moves B1, B2, B3, B4 in turn, chasing or dodging the mystery cell";
    }

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

        // "the blue player prioritizes avoiding landing on the mystery cell if it is moving
        // clockwise", so the cycle is walked once while skipping such moves...
        PlannedMove preferred = firstInCycle(options, true);
        if (preferred != null) {
            return preferred;
        }

        // ...and only if that leaves nothing at all is the cycle walked again without the dodge.
        return firstInCycle(options, false);
    }

    @Override
    public void onMoveExecuted(PlannedMove move) {
        int movedNumber = move.primaryPiece().number();
        nextPieceNumber = movedNumber % BoardGeometry.PIECES_PER_PLAYER + 1;
    }

    /**
     * Walks the cycle B{@code n}, B{@code n+1}, ... and returns the first move belonging to a piece
     * that has one.
     *
     * @param avoidMysteryCell when true, a clockwise move onto the mystery cell is skipped.
     */
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

    private boolean involvesPieceNumber(PlannedMove move, int pieceNumber) {
        for (Piece piece : move.movedPieces()) {
            if (piece.number() == pieceNumber) {
                return true;
            }
        }
        return false;
    }

    private boolean landsOnMysteryCell(PlannedMove move) {
        return mysteryCell.isActive() && move.landsOnRingCell(mysteryCell.cell());
    }

    /** A clockwise piece would rather not step onto the mystery cell. */
    private boolean dodgesMysteryCell(PlannedMove move) {
        return landsOnMysteryCell(move) && move.direction() == Direction.CLOCKWISE;
    }
}
