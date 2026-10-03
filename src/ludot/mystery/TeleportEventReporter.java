package ludot.mystery;

import ludot.board.GamePiece;
import ludot.effects.MovementModifier;

/** The teleport messages, kept separate so the mystery code does not need the whole log. */
public interface TeleportEventReporter {

    void reportMysteryCellLanding(GamePiece piece, TeleportTarget destination);

    void reportTeleport(GamePiece piece, TeleportTarget destination);

    void reportAlphaAura(GamePiece piece, MovementModifier modifier);

    void reportBetaBriefing(GamePiece piece);

    void reportGammaReversal(GamePiece piece);

    void reportGammaToBeta(GamePiece piece);
}
