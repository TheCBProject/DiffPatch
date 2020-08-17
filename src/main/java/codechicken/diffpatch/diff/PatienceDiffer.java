package codechicken.diffpatch.diff;

import codechicken.diffpatch.match.PatienceMatch;
import codechicken.diffpatch.util.CharRepresenter;

import java.util.List;

public class PatienceDiffer extends Differ {

    public PatienceDiffer() {
        this(null);
    }

    public PatienceDiffer(CharRepresenter charRep) {
        super(charRep);
    }

    @Override
    public int[] match(List<String> lines1, List<String> lines2) {
        String lineModeString1 = charRep.linesToChars(lines1);
        String lineModeString2 = charRep.linesToChars(lines2);
        return new PatienceMatch().match(lineModeString1, lineModeString2, charRep.getMaxLineChar());
    }
}
