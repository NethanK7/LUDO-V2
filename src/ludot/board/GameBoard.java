package ludot.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps track of which piece stands on which square. */
public final class GameBoard {

    public static final int MINIMUM_BLOCK_SIZE = 2;

    private static final Comparator<GamePiece> BY_PIECE_NUMBER =
            Comparator.comparingInt(GamePiece::getNumber);

    private final Map<PlayerColour, List<GamePiece>> piecesByColour = new EnumMap<>(PlayerColour.class);
    private final Map<BoardSquare, List<GamePiece>> occupants = new LinkedHashMap<>();

    public GameBoard() {
        for (PlayerColour colour : PlayerColour.values()) {
            List<GamePiece> pieces = new ArrayList<>();
            for (int number = 1; number <= BoardSpecification.PIECES_PER_COLOUR; number++) {
                GamePiece piece = new GamePiece(colour, number);
                pieces.add(piece);
                getOccupantsAt(piece.getSquare()).add(piece);
            }
            piecesByColour.put(colour, Collections.unmodifiableList(pieces));
        }
    }

    public List<GamePiece> getPiecesOf(PlayerColour colour) {
        return piecesByColour.get(colour);
    }

    public List<GamePiece> getAllPieces() {
        return piecesByColour.values().stream().flatMap(List::stream).toList();
    }

    public void relocate(GamePiece piece, BoardSquare destination) {
        getOccupantsAt(piece.getSquare()).remove(piece);
        piece.setSquare(destination);
        getOccupantsAt(destination).add(piece);
    }

    public Map<PlayerColour, List<GamePiece>> getGroupsOn(BoardSquare square) {
        Map<PlayerColour, List<GamePiece>> groups = new LinkedHashMap<>();
        for (GamePiece piece : getOccupantsAt(square)) {
            groups.computeIfAbsent(piece.getColour(), colour -> new ArrayList<>()).add(piece);
        }
        groups.values().forEach(group -> group.sort(BY_PIECE_NUMBER));
        return groups;
    }

    public List<GamePiece> getGroupOn(BoardSquare square, PlayerColour colour) {
        return getOccupantsAt(square).stream()
                .filter(piece -> piece.getColour() == colour)
                .sorted(BY_PIECE_NUMBER)
                .toList();
    }

    public boolean hasBlockOn(BoardSquare square, PlayerColour colour) {
        return getGroupOn(square, colour).size() >= MINIMUM_BLOCK_SIZE;
    }

    public boolean isPartOfBlock(GamePiece piece) {
        return piece.isInPlay() && hasBlockOn(piece.getSquare(), piece.getColour());
    }

    public List<BoardSquare> findBlockSquares(PlayerColour colour) {
        List<BoardSquare> squares = new ArrayList<>();
        for (GamePiece piece : getPiecesOf(colour)) {
            if (piece.isInPlay() && !squares.contains(piece.getSquare())
                    && hasBlockOn(piece.getSquare(), colour)) {
                squares.add(piece.getSquare());
            }
        }
        return squares;
    }

    public boolean isRingCellEmpty(int cell) {
        return getOccupantsAt(BoardSquare.ofRing(cell)).isEmpty();
    }

    public boolean hasAnyPieceOnRing() {
        return getAllPieces().stream().anyMatch(GamePiece::isOnRing);
    }

    public List<GamePiece> getPiecesInBase(PlayerColour colour) {
        return getGroupOn(BoardSquare.ofBase(colour), colour);
    }

    public List<GamePiece> getPiecesAtHome(PlayerColour colour) {
        return getGroupOn(BoardSquare.ofHome(colour), colour);
    }

    public List<GamePiece> getPiecesInPlay(PlayerColour colour) {
        return getPiecesOf(colour).stream().filter(GamePiece::isInPlay).toList();
    }

    public PlayerStatusSnapshot createSnapshot(PlayerColour colour) {
        List<PlayerStatusSnapshot.PieceSnapshot> pieces = getPiecesOf(colour).stream()
                .map(piece -> new PlayerStatusSnapshot.PieceSnapshot(piece.getName(), piece.getSquare()))
                .toList();
        return new PlayerStatusSnapshot(colour, pieces);
    }

    public boolean hasAllPiecesHome(PlayerColour colour) {
        return getPiecesAtHome(colour).size() == BoardSpecification.PIECES_PER_COLOUR;
    }

    private List<GamePiece> getOccupantsAt(BoardSquare square) {
        return occupants.computeIfAbsent(square, key -> new ArrayList<>());
    }
}
