package ludot.player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.movement.MoveOptions;
import ludot.movement.PathResolver;
import ludot.movement.PlannedMove;

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
 * each of the four strategies reads like the paragraph of the specification it implements. The
 * helpers return {@link Optional} rather than {@code null}, so "no such move" can never be
 * forgotten by a caller.
 *
 * <p>Adding a fifth behaviour means adding one subclass; no existing class has to change. That is
 * the Open/Closed Principle in practice.
 */
public abstract class Player {

    private final PieceColour colour;
    protected final Board board;
    protected final PathResolver pathResolver;

    protected Player(PieceColour colour, Board board, PathResolver pathResolver) {
        this.colour = colour;
        this.board = board;
        this.pathResolver = pathResolver;
    }

    public final PieceColour colour() {
        return colour;
    }


    /**
     * Picks the move to play, or empty when the roll cannot be used at all.
     *
     * <p>This method is intentionally {@code final}: it guarantees that a behaviour can never return
     * a move that was not in the legal list, and that a behaviour which cannot make up its mind
     * still plays something rather than wasting the roll.
     */
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

    /** Hook for behaviours that keep state between turns; blue uses it to follow its cycle. */
    public void onMoveExecuted(PlannedMove move) {
        // Most behaviours are stateless and have nothing to remember.
    }

    /** Hook called once at the end of every round; blue uses it to advance its cycle. */
    public void onRoundCompleted() {
        // Most behaviours are stateless and have nothing to remember.
    }

    // ------------------------------------------------------------ helpers shared by behaviours

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

    /** True when this move takes a single piece out of an existing block, breaking it up. */
    protected final boolean movesPieceOutOfBlock(PlannedMove move) {
        return !move.isBlockMove() && !move.isEnteringBoard()
                && board.isPartOfBlock(move.primaryPiece());
    }

    /**
     * The move whose piece has the shortest journey left to home.
     *
     * <p>Used by every behaviour that has to break a tie in favour of progress - yellow's "moves the
     * piece closest to its home" is exactly this. Ties keep the earlier move.
     */
    protected final Optional<PlannedMove> closestToHome(List<PlannedMove> options) {
        return options.stream()
                .min(Comparator.comparingInt(move -> pathResolver.distanceToHome(move.primaryPiece())));
    }
}
