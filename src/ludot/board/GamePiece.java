package ludot.board;

import ludot.effects.PieceStatusEffects;

/** One of the 16 pieces: where it is, which way it moves and what it has done so far. */
public final class GamePiece {

    private final PlayerColour colour;
    private final int number;
    private final String name;

    private BoardSquare square;
    private TravelDirection direction;
    private TravelDirection initialDirection;
    private int captureCount;
    private int approachPasses;
    private final PieceStatusEffects effects = new PieceStatusEffects();

    public GamePiece(PlayerColour colour, int number) {
        this.colour = colour;
        this.number = number;
        this.name = "" + colour.getInitial() + number;
        this.square = BoardSquare.ofBase(colour);
    }

    public PlayerColour getColour() {
        return colour;
    }

    public int getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    public BoardSquare getSquare() {
        return square;
    }

    void setSquare(BoardSquare square) {
        this.square = square;
    }

    public boolean isInBase() {
        return square.isBase();
    }

    public boolean isOnRing() {
        return square.isRing();
    }

    public boolean isAtHome() {
        return square.isHome();
    }

    public boolean isInPlay() {
        return square.isRing() || square.isHomeStraight();
    }

    public TravelDirection getDirection() {
        return direction;
    }

    public TravelDirection getInitialDirection() {
        return initialDirection;
    }

    public void assignStartingDirection(TravelDirection tossedDirection) {
        this.direction = tossedDirection;
        this.initialDirection = tossedDirection;
    }

    public void setDirection(TravelDirection direction) {
        this.direction = direction;
    }

    public int getCaptureCount() {
        return captureCount;
    }

    public void recordCapture() {
        captureCount++;
    }

    public boolean hasEarnedHomeStraightEntry() {
        return captureCount > 0;
    }

    public int getApproachPasses() {
        return approachPasses;
    }

    public void setApproachPasses(int approachPasses) {
        this.approachPasses = approachPasses;
    }

    public void recordApproachPass() {
        approachPasses++;
    }

    public PieceStatusEffects getEffects() {
        return effects;
    }

    public void resetAfterCapture() {
        direction = null;
        initialDirection = null;
        captureCount = 0;
        approachPasses = 0;
        effects.clear();
    }
}
