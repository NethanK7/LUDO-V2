package ludot.board;

import java.util.List;

/** DTO: a read-only snapshot of one player's pieces for the round report. */
public record PlayerStatusSnapshot(PlayerColour colour, int piecesOnBoard, int piecesInBase,
        int piecesHome, List<PieceLocation> pieces) {

    public record PieceLocation(String pieceName, String location) {
    }

    public PlayerStatusSnapshot {
        pieces = List.copyOf(pieces);
    }

    public boolean hasFinished() {
        return piecesHome == BoardSpecification.PIECES_PER_PLAYER;
    }
}
