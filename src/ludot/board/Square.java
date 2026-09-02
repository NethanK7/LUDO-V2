package ludot.board;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable "where is this piece?" value.
 *
 * <p>A LUDO-T board is not one flat list of cells: the 52 standard cells are shared by everybody,
 * while each colour additionally owns a base, a five-cell home straight and a home. Instead of
 * encoding all of that into one integer with hidden ranges, a {@code Square} says explicitly which
 * of the four kinds of place it is, who owns it, and which cell inside that place.
 *
 * <p>Because it is immutable it can be stored, compared and logged freely without any risk of one
 * part of the program accidentally editing another part's position.
 */
public final class Square {

    /** The four kinds of place a piece can occupy. */
    public enum Kind {
        /** One of the 52 shared standard cells. */
        RING,
        /** One of the five colour-specific cells between the approach cell and home. */
        HOME_STRAIGHT,
        /** The player's base, where pieces wait to roll a six. */
        BASE,
        /** The player's home; a piece that reaches it leaves the game (Section 1.1). */
        HOME
    }

    private final Kind kind;
    /** Owning colour for BASE / HOME_STRAIGHT / HOME; {@code null} for the shared ring. */
    private final PieceColour owner;
    /** 0..51 on the ring, 0..4 in a home straight, and 0 for BASE / HOME. */
    private final int index;

    /*
     * A LUDO-T board contains exactly 72 distinct squares: 52 shared standard cells plus a base, a
     * five-cell home straight and a home for each of the four colours. Since a Square is immutable,
     * every one of them can be created once, up front, and shared by everybody who refers to it -
     * the Flyweight pattern. Walking a path then costs no object allocation at all, and identical
     * squares are also identical objects, which makes comparing them as cheap as it can be.
     */
    private static final Square[] RING_SQUARES = new Square[BoardGeometry.RING_SIZE];
    private static final Map<PieceColour, Square[]> HOME_STRAIGHT_SQUARES =
            new EnumMap<>(PieceColour.class);
    private static final Map<PieceColour, Square> BASE_SQUARES = new EnumMap<>(PieceColour.class);
    private static final Map<PieceColour, Square> HOME_SQUARES = new EnumMap<>(PieceColour.class);

    static {
        for (int cell = 0; cell < BoardGeometry.RING_SIZE; cell++) {
            RING_SQUARES[cell] = new Square(Kind.RING, null, cell);
        }
        for (PieceColour colour : PieceColour.values()) {
            Square[] homeStraight = new Square[BoardGeometry.HOME_STRAIGHT_LENGTH];
            for (int cell = 0; cell < BoardGeometry.HOME_STRAIGHT_LENGTH; cell++) {
                homeStraight[cell] = new Square(Kind.HOME_STRAIGHT, colour, cell);
            }
            HOME_STRAIGHT_SQUARES.put(colour, homeStraight);
            BASE_SQUARES.put(colour, new Square(Kind.BASE, colour, 0));
            HOME_SQUARES.put(colour, new Square(Kind.HOME, colour, 0));
        }
    }

    private Square(Kind kind, PieceColour owner, int index) {
        this.kind = kind;
        this.owner = owner;
        this.index = index;
    }

    /** One of the 52 shared standard cells, numbered as in the Legend of the specification. */
    public static Square ring(int cell) {
        if (cell < 0 || cell >= BoardGeometry.RING_SIZE) {
            throw new IllegalArgumentException("Standard-path cell out of range: " + cell);
        }
        return RING_SQUARES[cell];
    }

    /** One of a colour's five home-straight cells, {@code [colour]homepath0} .. {@code 4}. */
    public static Square homeStraight(PieceColour owner, int cell) {
        if (cell < 0 || cell >= BoardGeometry.HOME_STRAIGHT_LENGTH) {
            throw new IllegalArgumentException("Home-straight cell out of range: " + cell);
        }
        return HOME_STRAIGHT_SQUARES.get(owner)[cell];
    }

    public static Square base(PieceColour owner) {
        return BASE_SQUARES.get(owner);
    }

    public static Square home(PieceColour owner) {
        return HOME_SQUARES.get(owner);
    }

    public Kind kind() {
        return kind;
    }

    /** Cell index inside this square's kind: 0..51 on the ring, 0..4 in a home straight. */
    public int index() {
        return index;
    }

    public boolean isRing() {
        return kind == Kind.RING;
    }

    public boolean isHomeStraight() {
        return kind == Kind.HOME_STRAIGHT;
    }

    public boolean isBase() {
        return kind == Kind.BASE;
    }

    public boolean isHome() {
        return kind == Kind.HOME;
    }

    /** True when this is the given colour's approach circle, the doorway to its home straight. */
    public boolean isApproachCellOf(PieceColour colour) {
        return isRing() && index == colour.approachCell();
    }

    /**
     * The square identifier used by every status message.
     *
     * <p>Per the Legend: standard cells are printed as their number 0..51, and home-straight cells
     * are printed as {@code [colour]homepath[cell number]} starting at zero.
     */
    public String label() {
        return switch (kind) {
            case RING -> Integer.toString(index);
            case HOME_STRAIGHT -> owner.displayName() + "homepath" + index;
            case BASE -> "Base";
            case HOME -> "Home";
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Square that)) {
            return false;
        }
        return kind == that.kind && owner == that.owner && index == that.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, owner, index);
    }

    @Override
    public String toString() {
        return label();
    }
}
