package codechicken.diffpatch.patch;

import codechicken.diffpatch.match.FuzzyLineMatcher;
import codechicken.diffpatch.util.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Patcher {

    private static final List<String> ACCESS_WORDS = Arrays.asList("public", "protected", "private", "final", " ", "\t");

    public final List<WorkingPatch> patches;
    public List<String> lines;
    private boolean applied;

    // Last here means highest line number, not necessarily most recent.
    // Patches can only apply before lastAppliedPatch in fuzzy mode
    private Patch lastAppliedPatch = null;

    // we maintain delta as the offset of the last patch (applied location - expected location)
    // this way if a line is inserted, and all patches are offset by 1, only the first patch is reported as offset
    // normally this is equivalent to `lastAppliedPatch?.AppliedOffset` but if a patch fails, we subtract its length delta from the search offset
    private int searchOffset;

    private final CharRepresenter charRep;
    private String lmText;
    private List<String> wmLines;

    public final int maxMatchOffset;
    public final float minMatchScore;

    public Patcher(PatchFile patchFile, List<String> lines) {
        this(patchFile, lines, null, FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET);
    }

    public Patcher(PatchFile patchFile, List<String> lines, float minFuzz, int maxOffset) {
        this(patchFile, lines, null, minFuzz, maxOffset);
    }

    public Patcher(PatchFile patchFile, List<String> lines, CharRepresenter charRep, float minFuzz, int maxOffset) {
        this.patches = patchFile.patches.stream().map(WorkingPatch::new).collect(Collectors.toList());
        this.lines = new ArrayList<>(lines);
        if (charRep == null) {
            charRep = new CharRepresenter();
        }
        this.charRep = charRep;
        this.minMatchScore = minFuzz;
        this.maxMatchOffset = maxOffset;
    }

    public Stream<Result> patch(PatchMode mode) {
        if (applied) {
            throw new RuntimeException("Already applied");
        }
        applied = true;

        for (WorkingPatch patch : patches) {
            if (applyExact(patch)) {
                continue;
            }
            if (mode.ordinal() >= PatchMode.ACCESS.ordinal() && applyAccess(patch)) {
                continue;
            }
            if (mode.ordinal() >= PatchMode.OFFSET.ordinal() && applyOffset(patch)) {
                continue;
            }
            if (mode.ordinal() >= PatchMode.FUZZY.ordinal() && applyFuzzy(patch)) {
                continue;
            }

            patch.fail();
            patch.result.searchOffset = searchOffset;
            searchOffset -= patch.length2 - patch.length1;
        }
        return patches.stream().map(e -> e.result);

    }

    private void linesToChars() {
        for (WorkingPatch patch : patches) {
            patch.linesToChars(charRep);
        }

        lmText = charRep.linesToChars(lines);
    }

    private void wordsToChars() {
        for (WorkingPatch patch : patches) {
            patch.wordsToChars(charRep);
        }

        wmLines = lines.stream().map(charRep::wordsToChars).collect(Collectors.toList());
    }

    private Patch applyExactAt(int loc, WorkingPatch patch) {
        if (!patch.getContextLines().collect(Collectors.toList()).containsAll(lines.subList(loc, loc + patch.length1))) {
            throw new RuntimeException("Patch engine failure");
        }
        if (!canApplySafelyAt(loc, patch)) {
            throw new RuntimeException("Patch affects another patch");
        }

        lines.subList(loc, loc + patch.length1).clear();
        lines.addAll(loc, patch.getPatchedLines().collect(Collectors.toList()));

        //update the lineModeText
        if (lmText != null) {
            lmText = lmText.substring(0, loc) + patch.lmPatched + lmText.substring(loc + patch.length1);
        }

        //update the wordModeLines
        if (wmLines != null) {
            wmLines.subList(loc, loc + patch.length1).clear();
            wmLines.addAll(loc, patch.wmPatched);
        }

        int patchedDelta = patches.stream()//
                .filter(e -> {
                    LineRange r = e.getKeepoutRange2();
                    return r != null && r.getEnd() <= loc;
                })//
                .mapToInt(e -> e.getAppliedDelta().getAsInt())//
                .sum();
        Patch appliedPatch = patch;
        if (appliedPatch.start2 != loc || appliedPatch.start1 != loc - patchedDelta) {
            appliedPatch = new Patch(patch);
            appliedPatch.start1 = loc - patchedDelta;
            appliedPatch.start2 = loc;
        }

        // update the applied location for patches following this one in the file, but preceding it in the patch list
        // can only happen if fuzzy matching causes a patch to move before one of the previously applied patches
        if (loc < getModifiedRange().getEnd()) {
            for (WorkingPatch p : patches) {
                LineRange r = p.getKeepoutRange2();
                if (r != null && r.getStart() > loc) {
                    p.result.appliedPatch.start2 += appliedPatch.length2 - appliedPatch.length1;
                }
            }
        } else {
            lastAppliedPatch = appliedPatch;
        }

        searchOffset = appliedPatch.start2 - patch.start2;
        return appliedPatch;
    }

    private boolean canApplySafelyAt(int loc, Patch patch) {
        if (loc >= getModifiedRange().getEnd()) {
            return true;
        }

        LineRange range = LineRange.fromStartLen(loc, patch.length1);
        return patches.stream().allMatch(p -> {
            LineRange r = p.getKeepoutRange2();
            return r == null || !r.contains(range);
        });
    }

    private boolean applyExact(WorkingPatch patch) {
        int loc = patch.start2 + searchOffset;
        if (loc + patch.length1 > lines.size()) {
            return false;
        }

        if (!patch.getContextLines().collect(Collectors.toList()).containsAll(lines.subList(loc, loc + patch.length1))) {
            return false;
        }

        patch.succeed(PatchMode.EXACT, applyExactAt(loc, patch));
        return true;
    }

    private boolean applyOffset(WorkingPatch patch) {
        if (lmText == null) {
            linesToChars();
        }

        if (patch.length1 > lines.size()) {
            return false;
        }

        int loc = patch.start2 + searchOffset;
        if (loc < 0) {
            loc = 0;
        } else if (loc >= lines.size()) {
            loc = lines.size() - 1;
        }

        int forward = lmText.indexOf(patch.lmContext, loc);
        int reverse = lmText.lastIndexOf(patch.lmContext, Math.min(loc + patch.lmContext.length(), lines.size() - 1));

        if (!canApplySafelyAt(forward, patch)) {
            forward = -1;
        }
        if (!canApplySafelyAt(reverse, patch)) {
            reverse = -1;
        }

        if (forward < 0 && reverse < 0) {
            return false;
        }

        int found = reverse < 0 || forward >= 0 && (forward - loc) < (loc - reverse) ? forward : reverse;
        patch.succeed(PatchMode.OFFSET, applyExactAt(found, patch));
        patch.addOffsetResult(found - loc, lines.size());

        return true;
    }

    private boolean applyAccess(WorkingPatch patch) {
        if (wmLines == null) {
            wordsToChars();
        }

        int loc = patch.start2 + searchOffset;
        if (loc + patch.length1 > lines.size()) {
            return false;
        }

        List<String> wmLines = this.wmLines.subList(loc, loc + patch.length1);

        if (patch.wmContext.size() != wmLines.size()) {
            return false;
        }

        int[] aWordCounts = new int[charRep.getMaxWordChar()];
        int[] bWordCounts = new int[charRep.getMaxWordChar()];

        int[] match = new int[patch.wmContext.size()];

        for (int i = 0; i < patch.wmContext.size(); i++) {
            match[i] = loc + i;
            //Count words in both lines.
            for (char c : patch.wmContext.get(i).toCharArray()) {
                aWordCounts[c]++;
            }
            for (char c : wmLines.get(i).toCharArray()) {
                bWordCounts[c]++;
            }
        }

        //Ensure only the allowed words change in counts.
        for (int i = 0; i < aWordCounts.length; i++) {
            if (aWordCounts[i] != bWordCounts[i] && !ACCESS_WORDS.contains(charRep.getWordForChar((char) i))) {
                return false;
            }
        }

        WorkingPatch fuzzyPatch = new WorkingPatch(adjustPatchToMatchedLines(patch, match, lines));
        if (wmLines != null) {
            fuzzyPatch.wordsToChars(charRep);
        }
        if (lmText != null) {
            fuzzyPatch.linesToChars(charRep);
        }

        patch.succeed(PatchMode.ACCESS, applyExactAt(loc, fuzzyPatch));
        return true;
    }

    private boolean applyFuzzy(WorkingPatch patch) {
        if (wmLines == null) {
            wordsToChars();
        }

        int loc = patch.start2 + searchOffset;
        if (loc + patch.length1 > wmLines.size())//initialise search at end of file if loc is past file length
        {
            loc = wmLines.size() - patch.length1;
        }

        Pair<int[], Float> pair = findMatch(loc, patch.wmContext);
        int[] match = pair.getLeft();
        if (match == null) {
            return false;
        }

        WorkingPatch fuzzyPatch = new WorkingPatch(adjustPatchToMatchedLines(patch, match, lines));
        if (wmLines != null) {
            fuzzyPatch.wordsToChars(charRep);
        }
        if (lmText != null) {
            fuzzyPatch.linesToChars(charRep);
        }

        int at = Arrays.stream(match).filter(i -> i >= 0).findFirst().getAsInt(); //if the patch needs lines trimmed off it, the early match entries will be negative
        patch.succeed(PatchMode.FUZZY, applyExactAt(at, fuzzyPatch));
        patch.addOffsetResult(fuzzyPatch.start2 - loc, lines.size());
        patch.addFuzzyResult(pair.getRight());
        return true;
    }

    public static Patch adjustPatchToMatchedLines(Patch patch, int[] match, List<String> lines) {
        //replace the patch with a copy
        Patch fuzzyPatch = new Patch(patch);
        List<Diff> diffs = fuzzyPatch.diffs; //for convenience

        //keep operations, but replace lines with lines in source text
        //unmatched patch lines (-1) are deleted
        //unmatched target lines (increasing offset) are added to the patch
        for (int i = 0, j = 0, ploc = -1; i < patch.length1; i++) {
            int mloc = match[i];

            //insert extra target lines into patch
            if (mloc >= 0 && ploc >= 0 && mloc - ploc > 1) {
                //delete an unmatched target line if the surrounding diffs are also DELETE, otherwise use it as context
                Operation op = diffs.get(j - 1).op == Operation.DELETE && diffs.get(j).op == Operation.DELETE ? Operation.DELETE : Operation.EQUAL;

                for (int l = ploc + 1; l < mloc; l++) {
                    diffs.add(j++, new Diff(op, lines.get(l)));
                }
            }
            ploc = mloc;

            //keep insert lines the same
            while (diffs.get(j).op == Operation.INSERT) {
                j++;
            }

            if (mloc < 0) //unmatched context line
            {
                diffs.remove(j);
            } else //update context to match target file (may be the same, doesn't matter)
            {
                diffs.get(j++).text = lines.get(mloc);
            }
        }

        //finish our new patch
        fuzzyPatch.recalculateLength();
        return fuzzyPatch;
    }

    private Pair<int[], Float> findMatch(int loc, List<String> wmContext) {
        // fuzzy matching is more complex because we need to split up the patched file to only search _between_ previously applied patches
        List<LineRange> keepoutRanges = patches.stream().map(WorkingPatch::getKeepoutRange2).filter(Objects::nonNull).collect(Collectors.toList());

        // parts of file to search in
        List<LineRange> ranges = LineRange.fromStartLen(0, wmLines.size()).except(keepoutRanges);

        return fuzzyMatch(wmContext, wmLines, loc, maxMatchOffset, minMatchScore, ranges);
    }

    public static Pair<int[], Float> fuzzyMatch(List<String> wmPattern, List<String> wmText, int loc, int maxMatchOffset, float minMatchScore, List<LineRange> ranges) {
        if (ranges == null) {
            ranges = Collections.singletonList(LineRange.fromStartLen(0, wmText.size()));
        }

        // we're creating twice as many MatchMatrix objects as we need, incurring some wasted allocation and setup time, but it reads easier than trying to precompute all the edge cases
        List<FuzzyLineMatcher.MatchMatrix> fwdMatchers = ranges.stream()//
                .map(r -> new FuzzyLineMatcher.MatchMatrix(wmPattern, wmText, maxMatchOffset, r))//
                .filter(m -> loc < m.workingRange.getLast())//
                .collect(Collectors.toList());
        List<FuzzyLineMatcher.MatchMatrix> revMatchers = revRange(0, ranges.size())//
                .mapToObj(ranges::get)//
                .map(r -> new FuzzyLineMatcher.MatchMatrix(wmPattern, wmText, maxMatchOffset, r))//
                .filter(m -> loc > m.workingRange.getFirst())//
                .collect(Collectors.toList());

        int warnDist = offsetWarnDistance(wmPattern.size(), wmText.size());
        float penaltyPerLine = 1f / (10 * warnDist);

        MatchRunner fwd = new MatchRunner(loc, 1, fwdMatchers, penaltyPerLine);
        MatchRunner rev = new MatchRunner(loc, -1, revMatchers, penaltyPerLine);

        AtomicReference<Float> bestScore = new AtomicReference<>(minMatchScore);
        AtomicReference<int[]> bestMatch = new AtomicReference<>(null);
        while (fwd.step(bestScore, bestMatch) | rev.step(bestScore, bestMatch)) {
            ;
        }

        return Pair.of(bestMatch.get(), bestScore.get());
    }

    public static IntStream revRange(int from, int to) {
        return IntStream.range(from, to).map(i -> to - i + from - 1);
    }

    // patches applying within this range (due to fuzzy matching) will cause patch reordering
    private LineRange getModifiedRange() {
        return new LineRange(0, lastAppliedPatch != null ? lastAppliedPatch.getTrimmedRange2().getEnd() : 0);
    }

    //the offset distance which constitutes a warning for a patch
    //currently either 10% of file length, or 10x patch length, whichever is longer
    public static int offsetWarnDistance(int patchLength, int fileLength) {
        return Math.max(patchLength * 10, fileLength / 10);
    }

    private static class MatchRunner {

        private int loc;
        private final int dir;
        private final List<FuzzyLineMatcher.MatchMatrix> mms;
        private final float penaltyPerLine;

        // used as a Range/Slice for the MatchMatrix array
        private LineRange active;
        private float penalty;

        public MatchRunner(int loc, int dir, List<FuzzyLineMatcher.MatchMatrix> mms, float penaltyPerLine) {
            this.loc = loc;
            this.dir = dir;
            this.mms = mms;
            this.penaltyPerLine = penaltyPerLine;
            active = new LineRange();
            penalty = -0.1f; // start penalty at -10%, to give some room for finding the best match if it's not "too far"
        }

        public boolean step(AtomicReference<Float> bestScore, AtomicReference<int[]> bestMatch) {
            if (active.getFirst() == mms.size()) {
                return false;
            }

            if (bestScore.get() > 1f - penalty) {
                return false; //aint getting any better than this
            }

            // activate matchers as we enter their working range
            while (active.getEnd() < mms.size() && mms.get(active.getEnd()).workingRange.contains(loc)) {
                active.setEnd(active.getEnd() + 1);
            }

            // active MatchMatrix runs
            for (int i = active.getFirst(); i <= active.getLast(); i++) {
                FuzzyLineMatcher.MatchMatrix mm = mms.get(i);
                Pair<Boolean, Float> pair = mm.match(loc);
                float score = pair.getRight();
                if (!pair.getLeft()) {
                    //Debug.Assert(i == active.first, "Match matricies out of order?");
                    active.setFirst(active.getFirst() + 1);
                    continue;
                }

                if (penalty > 0) //ignore penalty for the first 10%
                {
                    score -= penalty;
                }

                if (score > bestScore.get()) {
                    bestScore.set(score);
                    bestMatch.set(mm.path());
                }
            }

            loc += dir;
            penalty += penaltyPerLine;

            return true;
        }
    }

    //patch extended with implementation fields
    public class WorkingPatch extends Patch {

        public Result result;
        public String lmContext;
        public String lmPatched;
        public List<String> wmContext;
        public List<String> wmPatched;

        public WorkingPatch(Patch other) {
            super(other);
        }

        public void fail() {
            result = new Result(this, false);
        }

        public void succeed(PatchMode mode, Patch appliedPatch) {
            result = new Result(this, true);
            result.mode = mode;
            result.appliedPatch = appliedPatch;
        }

        public void addOffsetResult(int offset, int fileLength) {
            result.offset = offset;//note that offset is different to at - start2, because offset is relative to the applied position of the last patch
            result.offsetWarning = offset > offsetWarnDistance(length1, fileLength);
        }

        public void addFuzzyResult(float fuzzQuality) {
            result.fuzzyQuality = fuzzQuality;
        }

        public void linesToChars(CharRepresenter rep) {
            lmContext = rep.linesToChars(getContextLines().collect(Collectors.toList()));
            lmPatched = rep.linesToChars(getPatchedLines().collect(Collectors.toList()));
        }

        public void wordsToChars(CharRepresenter rep) {
            wmContext = getContextLines().map(rep::wordsToChars).collect(Collectors.toList());
            wmPatched = getPatchedLines().map(rep::wordsToChars).collect(Collectors.toList());
        }

        public LineRange getKeepoutRange2() {
            if (result != null && result.appliedPatch != null) {
                return result.appliedPatch.getTrimmedRange2();
            }
            return null;
        }

        public OptionalInt getAppliedDelta() {
            if (result != null && result.appliedPatch != null) {
                return OptionalInt.of(result.appliedPatch.length2 - result.appliedPatch.length1);
            }
            return OptionalInt.empty();
        }

    }

    public static class Result {

        public Patch patch;
        public boolean success;
        public PatchMode mode;

        public int searchOffset;
        public Patch appliedPatch;

        public int offset;
        public boolean offsetWarning;
        public float fuzzyQuality;

        public Result() {
        }

        public Result(Patch patch, boolean success) {
            this.patch = patch;
            this.success = success;
        }

        public String summary() {
            if (!success) {
                return "FAILURE: " + patch.getHeader();
            }

            if (mode == PatchMode.ACCESS) {
                return "ACCESS: " + patch.getHeader();
            }

            if (mode == PatchMode.OFFSET) {
                return String.format("%s: %s offset %d lines", offsetWarning ? "WARNING" : "OFFSET", patch.getHeader(), offset);
            }

            if (mode == PatchMode.FUZZY) {
                int q = (int) (fuzzyQuality * 100);
                return String.format("FUZZY: %s quality %s%%%s", patch.getHeader(), q, offset > 0 ? String.format(" offset %s lines", offset) : "");
            }

            return "EXACT: " + patch.getHeader();
        }
    }
}
