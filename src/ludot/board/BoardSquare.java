package ludot.board;

/** A place a piece can stand: a standard cell, a home-straight cell, the base or home. */
public record BoardSquare(Kind kind, PlayerColour owner, int index) {

    public enum Kind {
        RING,
        HOME_STRAIGHT,
        BASE,
        HOME
    }

    public BoardSquare {
        int size = kind == Kind.RING ? BoardSpecification.RING_SIZE : BoardSpecification.HOME_STRAIGHT_LENGTH;
        if ((kind == Kind.RING || kind == Kind.HOME_STRAIGHT) && (index < 0 || index >= size)) {
            throw new IllegalArgumentException(kind + " cell out of range: " + index);
        }
    }

    public static BoardSquare ofRing(int cell) {
        return new BoardSquare(Kind.RING, null, cell);
    }

    public static BoardSquare ofHomeStraight(PlayerColour owner, int cell) {
        return new BoardSquare(Kind.HOME_STRAIGHT, owner, cell);
    }

    public static BoardSquare ofBase(PlayerColour owner) {
        return new BoardSquare(Kind.BASE, owner, 0);
    }

    public static BoardSquare ofHome(PlayerColour owner) {
        return new BoardSquare(Kind.HOME, owner, 0);
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

    public boolean isApproachCellOf(PlayerColour colour) {
        return isRing() && index == colour.getApproachCell();
    }

    public String getLabel() {
        return switch (kind) {
            case RING -> Integer.toString(index);
            case HOME_STRAIGHT -> owner.getDisplayName() + "homepath" + index;
            case BASE -> "Base";
            case HOME -> "Home";
        };
    }
}
