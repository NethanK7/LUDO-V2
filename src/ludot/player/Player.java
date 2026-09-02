package ludot.player;

import java.util.List;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.MoveOptions;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;
import ludot.piece.Piece;

/**
 * A player of LUDO-T, and the base class for the four different behaviours.
 *
 * <p>The class is a Template Method: {@link #chooseMove(MoveOptions, int)} fixes the part that is
 * the same for everybody - never choose from an empty list, always end up with a valid move - and
 * delegates the only interesting decision to {@link #selectMove(List, int)}, which each colour
 * implements in its own way.
 *
 * <p>Everything a behaviour typically needs to ask about a move ("does it capture?", "does it form a
 * block?", "which piece is nearest home?") is provided here once as a small protected helper, so
 * each of the four strategies reads like the paragraph of the specification it implements.
 *
 * <p>Adding a fifth behaviour means adding one subclass; no existing class has to change. That is
 * the Open/Closed Principle in practice.
 */
public abstract class Player {

    /** Rule T-13: the value whose repetition frees a piece from its Beta briefing. */
    public static final int BRIEFING_ESCAPE_ROLL = 3;

    private final PieceColour colour;
    protected final Board board;
    protected final PathResolver pathResolver;

    private int consecutiveEscapeRolls;

    protected Player(PieceColour colour, Board board, PathResolver pathResolver) {
        this.colour = colour;
        this.board = board;
        this.pathResolver = pathResolver;
    }

    public final PieceColour colour() {
        return colour;
    }

    /** A one-line description of this behaviour, printed when the game introduces the players. */
    public abstract String behaviourSummary();

    /**
     * Picks the move to play, or {@code null} when the roll cannot be used at all.
     *
     * <p>This method is intentionally {@code final}: it guarantees that a behaviour can never return
     * a move that was not in the legal list, and that a behaviour which cannot make up its mind
     * still plays something rather than wasting the roll.
     */
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

    /** Hook for behaviours that keep state between turns; blue uses it to advance its cycle. */
    public void onMoveExecuted(PlannedMove move) {
        // Most behaviours are stateless and have nothing to remember.
    }

    // ------------------------------------------------------------ Rule T-13 roll bookkeeping

    /**
     * Records the value just rolled so Rule&nbsp;T-13 can tell when "the player rolls value three
     * consecutively" and a piece stuck at a Beta briefing is sent back to its base.
     */
    public final void recordRoll(int value) {
        consecutiveEscapeRolls = value == BRIEFING_ESCAPE_ROLL ? consecutiveEscapeRolls + 1 : 0;
    }

    public final int consecutiveEscapeRolls() {
        return consecutiveEscapeRolls;
    }

    public final void clearConsecutiveEscapeRolls() {
        consecutiveEscapeRolls = 0;
    }

    // ------------------------------------------------------------ helpers shared by behaviours

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

    /**
     * True when the move leaves two or more of this player's pieces standing on one cell, which is
     * the definition of a block in Rule&nbsp;T-3.
     */
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

    /** True when this move takes a single piece out of an existing block, breaking it up. */
    protected final boolean movesPieceOutOfBlock(PlannedMove move) {
        return !move.isBlockMove() && !move.isEnteringBoard()
                && board.isPartOfBlock(move.primaryPiece());
    }

    /**
     * The move whose piece has the shortest journey left to home.
     *
     * <p>Used by every behaviour that has to break a tie in favour of progress - yellow's "moves the
     * piece closest to its home" is exactly this.
     */
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

    protected final PlannedMove firstOrNull(List<PlannedMove> options) {
        return options.isEmpty() ? null : options.get(0);
    }
}
