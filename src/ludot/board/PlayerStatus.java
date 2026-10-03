package ludot.board;

import java.util.List;

/**
 * A snapshot of one player's pieces, handed to whoever reports on the game (Data Transfer Object).
 *
 * <p>The snapshot is immutable and holds only text and numbers, so the printing code never needs the
 * {@link Board} itself and can never change it by accident.
 *
 * @param piecesOnBoard pieces on the standard path or in the home straight.
 * @param pieces        every piece with the label of its square (a cell number, "Base" or "Home").
 */
public record PlayerStatus(PieceColour colour, int piecesOnBoard, int piecesInBase,
        int piecesHome, List<PieceLocation> pieces) {

    /** One piece and where it stands, e.g. {@code R1 -> 26}. */
    public record PieceLocation(String pieceName, String location) {
    }

    public PlayerStatus {
        pieces = List.copyOf(pieces);
    }

    /** True once all four pieces have reached home. */
    public boolean hasFinished() {
        return piecesHome == BoardGeometry.PIECES_PER_PLAYER;
    }
}
