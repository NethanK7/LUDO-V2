package ludot.movement;

import java.util.ArrayList;
import java.util.List;
import ludot.board.Board;
import ludot.board.Direction;
import ludot.board.PieceColour;
import ludot.board.Square;
import ludot.piece.Piece;
import ludot.random.Dice;

/**
 * Turns "the {@code red} player rolled a 4" into the complete list of things red is allowed to do.
 *
 * <p>The generator answers only the question <em>what is legal?</em>. Which of the legal moves is
 * actually the best one is the business of each player's own strategy, and what happens to the board
 * afterwards is the business of {@link MoveExecutor}. Splitting the turn into these three steps -
 * generate, choose, execute - keeps every one of them small, and means a new player behaviour can be
 * added without touching the rules at all.
 *
 * <p>There are exactly three shapes of move to consider:
 *
 * <ol>
 *   <li>a six lifting one piece out of the base onto "X" (Rules 2 and 3);</li>
 *   <li>one piece walking the value of the dice (Rule 1), possibly leaving a block, in which case
 *       Rule&nbsp;T-5 restores the direction it was given at "X";</li>
 *   <li>a whole block walking together, {@code roll / blockSize} cells each (Rule T-4).</li>
 * </ol>
 */
public final class MoveGenerator {

    private final Board board;
    private final PathResolver pathResolver;

    public MoveGenerator(Board board, PathResolver pathResolver) {
        this.board = board;
        this.pathResolver = pathResolver;
    }

    /** Every legal move - and every block-refused attempt - for this colour and this dice value. */
    public MoveOptions optionsFor(PieceColour colour, int rollValue) {
        List<PlannedMove> playable = new ArrayList<>();
        List<BlockedAttempt> blocked = new ArrayList<>();

        addEnterBoardMove(colour, rollValue, playable);
        addSinglePieceMoves(colour, rollValue, playable, blocked);
        addBlockMoves(colour, rollValue, playable);

        return new MoveOptions(playable, blocked);
    }

    /**
     * The direction a piece would actually travel in.
     *
     * <p>Normally it is simply the direction the piece is facing. Rule&nbsp;T-5 makes one exception:
     * a piece that steps out of a block on its own reverts to "the original direction of the piece
     * when it was placed in X", because while it was part of a block it may have been carried along
     * in the block's direction instead of its own.
     */
    public Direction travelDirectionOf(Piece piece) {
        return board.isPartOfBlock(piece) ? piece.initialDirection() : piece.direction();
    }

    /**
     * Builds a move for a fixed distance, outside the normal "one roll, one move" flow.
     *
     * <p>Rule&nbsp;T-6 needs this: a player that rolls a third consecutive six while holding a
     * blockade must break it up by moving its pieces "in their original direction by six units
     * cumulatively", which is a distance the dice never produced directly.
     *
     * @return the planned move, or {@code null} when the piece cannot travel that far.
     */
    public PlannedMove forcedMove(Piece piece, Direction direction, int steps) {
        PathResolver.Walk walk = pathResolver.walk(piece, direction, steps, 1);
        if (!walk.isCompleted()) {
            return null;
        }
        return singlePieceMove(MoveKind.ADVANCE, piece, direction, walk, null);
    }

    /** Rules 2 and 3: only a six brings a piece out of the base, and only onto its own "X". */
    private void addEnterBoardMove(PieceColour colour, int rollValue, List<PlannedMove> playable) {
        if (rollValue != Dice.SIX) {
            return;
        }
        List<Piece> waitingInBase = board.piecesInBase(colour);
        if (waitingInBase.isEmpty()) {
            return;
        }
        Square startSquare = Square.ring(colour.startCell());
        if (board.isBlockedForTravel(startSquare, colour)) {
            // An opponent block is sitting on "X", so there is nowhere to step out to (Rule T-3).
            return;
        }

        // The pieces waiting in the base are interchangeable, so the lowest numbered one is used.
        Piece piece = waitingInBase.get(0);
        PieceMovement movement = new PieceMovement(piece, piece.square(), startSquare, null, 0, 0);
        List<Piece> captured = pathResolver.capturesOnLanding(startSquare, colour);
        playable.add(new PlannedMove(MoveKind.ENTER_BOARD, List.of(movement), captured, null));
    }

    /** Rule 1: each piece already on the board walks the value of the dice on its own. */
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

            Direction direction = travelDirectionOf(piece);
            PathResolver.Walk walk = pathResolver.walk(piece, direction, steps, 1);
            switch (walk.outcome()) {
                case COMPLETED -> playable.add(
                        singlePieceMove(MoveKind.ADVANCE, piece, direction, walk, null));
                case BLOCKED -> blocked.add(blockedAttempt(piece, direction, steps, walk));
                case IMPOSSIBLE -> {
                    // Rule 10: the roll is not the exact number needed to finish the home straight.
                }
            }
        }
    }

    /**
     * Rule T-4: a block travels as one body, every piece moving {@code roll / blockSize} cells.
     *
     * <p>The direction is the block's own when all its pieces agree, and otherwise "the direction of
     * the longest distance from home". Because the pieces move as one body, the walk is resolved
     * once for the piece that sets the direction and applied to all of them, which is what keeps a
     * block a block.
     */
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

            Piece leader = directionSettingPieceOf(block);
            Direction direction = leader.direction();
            PathResolver.Walk walk = pathResolver.walk(leader, direction, steps, block.size());
            if (!walk.isCompleted()) {
                continue;
            }

            Square destination = walk.destination();
            List<PieceMovement> movements = new ArrayList<>();
            for (Piece piece : block) {
                movements.add(new PieceMovement(piece, blockSquare, destination, direction, steps,
                        piece.approachPasses() + walk.approachArrivals()));
            }
            List<Piece> captured = pathResolver.capturesOnLanding(destination, colour);
            playable.add(new PlannedMove(MoveKind.BLOCK_ADVANCE, movements, captured, null));
        }
    }

    /**
     * Rule T-4: "If a block is created by two pieces moving in the opposite direction, the block
     * shall move in the direction of the longest distance from home."
     *
     * <p>When every piece in the block already faces the same way there is nothing to decide, so the
     * first piece sets the direction. Otherwise the piece with the longest journey left decides.
     */
    private Piece directionSettingPieceOf(List<Piece> block) {
        Piece firstPiece = block.get(0);
        boolean directionsAgree = block.stream()
                .allMatch(piece -> piece.direction() == firstPiece.direction());
        if (directionsAgree) {
            return firstPiece;
        }

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

    private boolean containsRestrictedPiece(List<Piece> block) {
        return block.stream().anyMatch(piece -> piece.effects().isAttendingBriefing());
    }

    /** Builds the planned move for one piece that has finished (or partly finished) a walk. */
    private PlannedMove singlePieceMove(MoveKind kind, Piece piece, Direction direction,
            PathResolver.Walk walk, Piece blockingPiece) {
        Square destination = walk.destination();
        PieceMovement movement = new PieceMovement(piece, piece.square(), destination, direction,
                walk.stepsTaken(), piece.approachPasses() + walk.approachArrivals());
        List<Piece> captured = pathResolver.capturesOnLanding(destination, piece.colour());
        return new PlannedMove(kind, List.of(movement), captured, blockingPiece);
    }

    /**
     * Records a move refused by Rule T-3, together with the shortened "up to the cell before the
     * block" move that the specification allows as a fall-back.
     */
    private BlockedAttempt blockedAttempt(Piece piece, Direction direction, int steps,
            PathResolver.Walk walk) {
        Square intendedDestination = pathResolver.destinationIgnoringBlocks(piece, direction, steps);
        PlannedMove partialMove = walk.destination() == null
                ? null
                : singlePieceMove(MoveKind.PARTIAL_ADVANCE, piece, direction, walk,
                        walk.blockingPiece());
        return new BlockedAttempt(piece, piece.square(), intendedDestination, walk.blockingPiece(),
                partialMove);
    }
}
