package ludot.strategy;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import ludot.board.BoardSpecification;
import ludot.board.TravelDirection;
import ludot.movement.CandidateMove;
import ludot.mystery.MysteryCellScheduler;

/** Blue (2.1.4): moves B1, B2, B3, B4 in turn, one step per round, and chases or avoids the mystery cell. */
public final class BlueMysteryStrategy implements MoveSelectionStrategy {

    private static final int NO_PIECE = 0;

    private final MysteryCellScheduler mysteryCell;

    private int cycleStartNumber = 1;

    private int firstPieceMovedThisRound = NO_PIECE;

    public BlueMysteryStrategy(MysteryCellScheduler mysteryCell) {
        this.mysteryCell = mysteryCell;
    }

    @Override
    public Optional<CandidateMove> chooseMove(List<CandidateMove> options) {
        for (int offset = 0; offset < BoardSpecification.PIECES_PER_COLOUR; offset++) {
            List<CandidateMove> movesOfPiece = findMovesOfPiece(options, getPieceNumberAt(offset));
            Optional<CandidateMove> choice = choosePreferredMove(movesOfPiece);
            if (choice.isPresent()) {
                return choice;
            }
        }
        return findFirstMoveInCycle(options);
    }

    @Override
    public void rememberMove(CandidateMove move) {
        if (firstPieceMovedThisRound == NO_PIECE) {
            firstPieceMovedThisRound = move.getPrimaryPiece().getNumber();
        }
    }

    @Override
    public void finishRound() {
        if (firstPieceMovedThisRound != NO_PIECE) {
            cycleStartNumber = firstPieceMovedThisRound % BoardSpecification.PIECES_PER_COLOUR + 1;
            firstPieceMovedThisRound = NO_PIECE;
        }
    }

    public int getCycleStartNumber() {
        return cycleStartNumber;
    }

    private Optional<CandidateMove> choosePreferredMove(List<CandidateMove> movesOfPiece) {
        if (movesOfPiece.isEmpty()) {
            return Optional.empty();
        }
        TravelDirection direction = movesOfPiece.get(0).getPrimaryPiece().getDirection();
        if (direction == TravelDirection.COUNTER_CLOCKWISE) {
            return Optional.of(movesOfPiece.stream()
                    .filter(this::landsOnMysteryCell)
                    .findFirst()
                    .orElse(movesOfPiece.get(0)));
        }
        if (direction == TravelDirection.CLOCKWISE) {
            return movesOfPiece.stream().filter(move -> !landsOnMysteryCell(move)).findFirst();
        }
        return Optional.of(movesOfPiece.get(0));
    }

    private Optional<CandidateMove> findFirstMoveInCycle(List<CandidateMove> options) {
        return IntStream.range(0, BoardSpecification.PIECES_PER_COLOUR)
                .mapToObj(offset -> findMovesOfPiece(options, getPieceNumberAt(offset)))
                .flatMap(List::stream)
                .findFirst();
    }

    private int getPieceNumberAt(int offsetInCycle) {
        return (cycleStartNumber - 1 + offsetInCycle) % BoardSpecification.PIECES_PER_COLOUR + 1;
    }

    private List<CandidateMove> findMovesOfPiece(List<CandidateMove> options, int pieceNumber) {
        return options.stream()
                .filter(move -> move.getMovedPieces().stream()
                        .anyMatch(piece -> piece.getNumber() == pieceNumber))
                .toList();
    }

    private boolean landsOnMysteryCell(CandidateMove move) {
        return mysteryCell.isOn(move.getDestination());
    }
}
