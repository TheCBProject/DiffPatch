package io.codechicken.diffpatch.diff;

import io.codechicken.diffpatch.match.LineMatching;
import io.codechicken.diffpatch.util.CharRepresenter;
import io.codechicken.diffpatch.util.Diff;
import io.codechicken.diffpatch.util.Operation;
import io.codechicken.diffpatch.util.Patch;
import net.covers1624.quack.collection.FastStream;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class Differ {

    public static final int DEFAULT_CONTEXT = 3;

    protected final CharRepresenter charRep;

    public Differ() {
        this(null);
    }

    public Differ(@Nullable CharRepresenter charRep) {
        if (charRep == null) {
            charRep = new CharRepresenter();
        }
        this.charRep = charRep;
    }

    public abstract int[] match(List<String> lines1, List<String> lines2);

    public List<Diff> diff(List<String> lines1, List<String> lines2) {
        return LineMatching.makeDiffList(match(lines1, lines2), lines1, lines2);
    }

    public List<Patch> makePatches(List<String> lines1, List<String> lines2) {
        return makePatches(lines1, lines2, DEFAULT_CONTEXT, true);
    }

    public List<Patch> makePatches(List<String> lines1, List<String> lines2, int numContextLines, boolean collate) {
        return makePatches(diff(lines1, lines2), numContextLines, collate);
    }

    public static List<Patch> makeFileAdded(List<String> lines) {
        return Collections.singletonList(make(lines, Operation.INSERT));
    }

    public static List<Patch> makeFileRemoved(List<String> lines) {
        return Collections.singletonList(make(lines, Operation.DELETE));
    }

    public static List<Patch> makePatches(List<Diff> diffs, int numContextLines, boolean collate) {
        Patch p = new Patch();
        p.diffs = diffs;
        p.recalculateLength();
        p.trim(numContextLines);
        if (p.length1 == 0) {
            return Collections.emptyList();
        }
        if (!collate) {
            p.uncollate();
        }
        return p.split(numContextLines);
    }

    private static Patch make(List<String> lines, Operation op) {
        Patch patch = new Patch();
        patch.diffs = FastStream.of(lines).map(l -> new Diff(op, l)).toList();
        patch.recalculateLength();
        return patch;
    }

    public CharRepresenter getCharRep() {
        return charRep;
    }
}
