package ludot.effects;

/** How the Alpha aura changes a roll: normal, doubled or halved. */
public enum MovementModifier {

    NORMAL {
        @Override
        public int apply(int rollValue) {
            return rollValue;
        }
    },

    DOUBLED {
        @Override
        public int apply(int rollValue) {
            return rollValue * 2;
        }
    },

    HALVED {
        @Override
        public int apply(int rollValue) {
            return rollValue / 2;
        }
    };

    public abstract int apply(int rollValue);
}
