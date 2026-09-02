package ludot.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ludot.piece.Piece;

/**
 * The board: who is standing where.
 *
 * <p>The board keeps one index from {@link Square} to the pieces occupying it. Because
 * {@code Square} is an immutable value object with proper {@code equals}/{@code hashCode}, a plain
 * hash map is enough to answer "what is on cell 37?" in constant time, and the same index naturally
 * covers the bases, the four home straights and the four homes without any special cases.
 *
 * <p>{@link #relocate(Piece, Square)} is the <em>only</em> way a piece changes position. Keeping a
 * single mutation point is what guarantees the index and the pieces can never disagree.
 */
public final class Board {

    /** Two or more pieces of the same player on one cell form a "block" (Rule T-3). */
    public static final int MINIMUM_BLOCK_SIZE = 2;

    private final Map<PieceColour, List<Piece>> piecesByColour = new EnumMap<>(PieceColour.class);
    private final Map<Square, List<Piece>> occupants = new LinkedHashMap<>();

    /** Builds a board with four pieces per colour, all of them sitting in their base. */
    public Board() {
        for (PieceColour colour : PieceColour.values()) {
            List<Piece> pieces = new ArrayList<>();
            for (int number = 1; number <= BoardGeometry.PIECES_PER_PLAYER; number++) {
                Piece piece = new Piece(colour, number);
                pieces.add(piece);
                occupantsAt(piece.square()).add(piece);
            }
            piecesByColour.put(colour, Collections.unmodifiableList(pieces));
        }
    }

    /** The four pieces of one colour, in the order R1, R2, R3, R4. */
    public List<Piece> piecesOf(PieceColour colour) {
        return piecesByColour.get(colour);
    }

    /** Every piece in the game, used by players that need to look at their opponents. */
    public List<Piece> allPieces() {
        List<Piece> all = new ArrayList<>();
        for (PieceColour colour : PieceColour.values()) {
            all.addAll(piecesByColour.get(colour));
        }
        return all;
    }

    /** Moves one piece and keeps the occupancy index in step with it. */
    public void relocate(Piece piece, Square destination) {
        occupantsAt(piece.square()).remove(piece);
        piece.setSquare(destination);
        occupantsAt(destination).add(piece);
    }

    /**
     * The pieces on {@code square}, grouped by owning colour.
     *
     * <p>This is the shape the block rules need: a group of one is a lone piece that can be captured
     * (Rule 6), a group of two or more is a block that cannot be jumped over (Rule T-3) and can only
     * be captured by a blockade of the same size (Rule T-8).
     */
    public Map<PieceColour, List<Piece>> groupsOn(Square square) {
        Map<PieceColour, List<Piece>> groups = new LinkedHashMap<>();
        for (Piece piece : occupantsAt(square)) {
            groups.computeIfAbsent(piece.colour(), colour -> new ArrayList<>()).add(piece);
        }
        return groups;
    }

    /** The pieces of one colour standing on one square, e.g. to measure the size of a block. */
    public List<Piece> groupOn(Square square, PieceColour colour) {
        List<Piece> group = new ArrayList<>();
        for (Piece piece : occupantsAt(square)) {
            if (piece.colour() == colour) {
                group.add(piece);
            }
        }
        return group;
    }

    /** True when {@code colour} has a block (two or more pieces) on {@code square}. */
    public boolean hasBlockOn(Square square, PieceColour colour) {
        return groupOn(square, colour).size() >= MINIMUM_BLOCK_SIZE;
    }

    /** True when this piece is currently part of one of its own player's blocks. */
    public boolean isPartOfBlock(Piece piece) {
        return piece.isInPlay() && hasBlockOn(piece.square(), piece.colour());
    }

    /**
     * True when any opponent of {@code mover} holds a block on {@code square}.
     *
     * <p>Rule T-3: "No opponent piece can jump over the block." This is the test used for every cell
     * a piece travels <em>through</em>.
     */
    public boolean isBlockedForTravel(Square square, PieceColour mover) {
        for (Map.Entry<PieceColour, List<Piece>> group : groupsOn(square).entrySet()) {
            if (group.getKey() != mover && group.getValue().size() >= MINIMUM_BLOCK_SIZE) {
                return true;
            }
        }
        return false;
    }

    /** Every square on which {@code colour} currently holds a block, nearest-to-home first. */
    public List<Square> blockSquaresOf(PieceColour colour) {
        List<Square> squares = new ArrayList<>();
        for (Piece piece : piecesOf(colour)) {
            if (piece.isInPlay() && !squares.contains(piece.square())
                    && hasBlockOn(piece.square(), colour)) {
                squares.add(piece.square());
            }
        }
        return squares;
    }

    /** True when no piece stands on the given standard-path cell (needed by Rule T-10). */
    public boolean isRingCellEmpty(int cell) {
        return occupantsAt(Square.ring(cell)).isEmpty();
    }

    /** True when at least one piece of any colour is on the standard path (Rule T-10 trigger). */
    public boolean hasAnyPieceOnRing() {
        for (Piece piece : allPieces()) {
            if (piece.isOnRing()) {
                return true;
            }
        }
        return false;
    }

    public List<Piece> piecesInBase(PieceColour colour) {
        return groupOn(Square.base(colour), colour);
    }

    public List<Piece> piecesAtHome(PieceColour colour) {
        return groupOn(Square.home(colour), colour);
    }

    /** Pieces of this colour that are out on the board, i.e. neither in the base nor home. */
    public List<Piece> piecesInPlay(PieceColour colour) {
        List<Piece> inPlay = new ArrayList<>();
        for (Piece piece : piecesOf(colour)) {
            if (piece.isInPlay()) {
                inPlay.add(piece);
            }
        }
        return inPlay;
    }

    /** Rule 11: a player wins once all four of its pieces have reached home. */
    public boolean hasAllPiecesHome(PieceColour colour) {
        return piecesAtHome(colour).size() == BoardGeometry.PIECES_PER_PLAYER;
    }

    private List<Piece> occupantsAt(Square square) {
        return occupants.computeIfAbsent(square, key -> new ArrayList<>());
    }
}
