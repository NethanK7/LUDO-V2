package ludot.ui;

import java.util.List;
import ludot.board.GamePiece;
import ludot.board.PlayerColour;
import ludot.board.PlayerStatusSnapshot;
import ludot.movement.BlockedMoveAttempt;
import ludot.movement.CandidateMove;
import ludot.movement.PieceTransition;
import ludot.mystery.MysteryCellScheduler;
import ludot.mystery.TeleportEventReporter;

/** Everything the game reports. The rules only use this interface, never the console. */
public interface GameEventReporter extends TeleportEventReporter {

    void introducePlayer(PlayerColour colour, List<GamePiece> pieces);

    void reportOpeningRoll(PlayerColour colour, int value);

    void reportFirstPlayer(PlayerColour colour);

    void reportRoundOrder(List<PlayerColour> order);

    void reportTurnStart(PlayerColour colour);

    void reportDiceRoll(PlayerColour colour, int value);

    void reportPieceReleased(GamePiece piece);

    void reportPieceMoved(PieceTransition movement);

    void reportPieceBlocked(BlockedMoveAttempt attempt);

    void reportThrowIgnored(PlayerColour colour);

    void reportMovedUpToBlock(PlayerColour colour, CandidateMove partialMove);

    void reportCapture(GamePiece capturer, GamePiece captured, String squareLabel);

    void reportMysteryCellSpawn(int cell);

    void reportMysteryCellStatus(MysteryCellScheduler mysteryCell);

    void reportBriefingEscape(GamePiece piece);

    void reportPieceCounts(PlayerStatusSnapshot status);

    void reportPieceLocations(PlayerStatusSnapshot status);

    void reportRoundEnd();

    void announceWinner(PlayerColour colour);

    void announceFinalStandings(List<PlayerColour> placings);

    void reportRoundLimitReached(int roundLimit, List<PlayerStatusSnapshot> statuses);

    void reportGridlock(int round, int stillRounds, List<PlayerStatusSnapshot> statuses);
}
