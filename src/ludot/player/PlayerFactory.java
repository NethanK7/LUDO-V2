package ludot.player;

import java.util.ArrayList;
import java.util.List;
import ludot.board.Board;
import ludot.board.PieceColour;
import ludot.movement.PathResolver;
import ludot.mystery.MysteryCell;

/**
 * Creates the player whose behaviour belongs to a colour.
 *
 * <p>A single factory keeps the "which colour behaves how" decision in one place. The rest of the
 * program - the turn engine in particular - only ever sees {@link Player}, so it neither knows nor
 * cares that red hunts captures while blue chases mystery cells.
 */
public final class PlayerFactory {

    private final Board board;
    private final PathResolver pathResolver;
    private final MysteryCell mysteryCell;

    public PlayerFactory(Board board, PathResolver pathResolver, MysteryCell mysteryCell) {
        this.board = board;
        this.pathResolver = pathResolver;
        this.mysteryCell = mysteryCell;
    }

    public Player create(PieceColour colour) {
        return switch (colour) {
            case RED -> new RedPlayer(board, pathResolver);
            case GREEN -> new GreenPlayer(board, pathResolver);
            case YELLOW -> new YellowPlayer(board, pathResolver);
            case BLUE -> new BluePlayer(board, pathResolver, mysteryCell);
        };
    }

    /** All four players, in the fixed board order yellow, blue, red, green. */
    public List<Player> createAll() {
        List<Player> players = new ArrayList<>();
        for (PieceColour colour : PieceColour.values()) {
            players.add(create(colour));
        }
        return players;
    }
}
