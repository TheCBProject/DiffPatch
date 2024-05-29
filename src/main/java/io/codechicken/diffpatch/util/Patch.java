package io.codechicken.diffpatch.util;

import net.covers1624.quack.collection.FastStream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Also known as a Hunk
 * Represents a sequence of Diffs.
 */
public class Patch {

    public List<Diff> diffs;
    public int start1;
    public int start2;
    public int length1;
    public int length2;

    public Patch() {
        diffs = new ArrayList<>();
    }

    public Patch(Patch other) {
        this.diffs = FastStream.of(other.diffs).map(Diff::new).toList();
        this.start1 = other.start1;
        this.start2 = other.start2;
        this.length1 = other.length1;
        this.length2 = other.length2;
    }

    private LineRange trimRange(LineRange range) {
        int start = 0;
        while (start < diffs.size() && diffs.get(start).op == Operation.EQUAL) {
            start++;
        }
        if (start == diffs.size()) {
            return LineRange.fromStartLen(range.getStart(), 0);
        }

        int end = diffs.size();
        while (end > start && diffs.get(end - 1).op == Operation.EQUAL) {
            end--;
        }
        return new LineRange(range.getStart() + start, range.getEnd() - (diffs.size() - end));
    }

    public void recalculateLength() {
        length1 = diffs.size();
        length2 = diffs.size();
        for (Diff diff : diffs) {
            if (diff.op == Operation.DELETE) {
                length2--;
            } else if (diff.op == Operation.INSERT) {
                length1--;
            }
        }
    }

    public void trim(int numContextLines) {
        LineRange r = trimRange(LineRange.fromStartLen(0, diffs.size()));

        if (r.getLength() == 0) {
            length1 = length2 = 0;
            diffs.clear();
            return;
        }

        int trimStart = r.getStart() - numContextLines;
        int trimEnd = diffs.size() - r.getEnd() - numContextLines;
        if (trimStart > 0) {
            diffs.subList(0, trimStart).clear();
            start1 += trimStart;
            start2 += trimStart;
            length1 -= trimStart;
            length2 -= trimStart;
        }

        if (trimEnd > 0) {
            diffs.subList(diffs.size() - trimEnd, diffs.size()).clear();
            length1 -= trimEnd;
            length2 -= trimEnd;
        }
    }

    public void uncollate() {
        List<Diff> unCollatedDiffs = new ArrayList<>(diffs.size());
        List<Diff> addDiffs = new ArrayList<>();
        for (Diff d : diffs) {
            if (d.op == Operation.DELETE) {
                unCollatedDiffs.add(d);
            } else if (d.op == Operation.INSERT) {
                addDiffs.add(d);
            } else {
                unCollatedDiffs.addAll(addDiffs);
                addDiffs.clear();
                unCollatedDiffs.add(d);
            }
        }
        unCollatedDiffs.addAll(addDiffs); //patches may not end with context diffs
        diffs = unCollatedDiffs;
    }

    public List<Patch> split(int numContextLines) {
        if (diffs.isEmpty()) {
            return new ArrayList<>();
        }
        List<LineRange> ranges = new ArrayList<>();
        int start = 0;
        int n = 0;
        for (int i = 0; i < diffs.size(); i++) {
            if (diffs.get(i).op == Operation.EQUAL) {
                n++;
                continue;
            }

            if (n > numContextLines * 2) {
                ranges.add(new LineRange(start, i - n + numContextLines));
                start = i - numContextLines;
            }

            n = 0;
        }

        ranges.add(new LineRange(start, diffs.size()));

        List<Patch> patches = new ArrayList<>(diffs.size());
        int end1 = start1;
        int end2 = start2;
        int endDiffIndex = 0;
        for (LineRange r : ranges) {
            int skip = r.getStart() - endDiffIndex;
            Patch patch = new Patch();
            patch.start1 = end1 + skip;
            patch.start2 = end2 + skip;
            patch.diffs = new ArrayList<>(diffs.subList(r.getStart(), r.getEnd()));
            patch.recalculateLength();
            patches.add(patch);
            end1 = patch.start1 + patch.length1;
            end2 = patch.start2 + patch.length2;
            endDiffIndex = r.getEnd();
        }
        return patches;
    }

    public void combine(Patch patch2, List<String> lines1) {
        if (getRange1().intersects(patch2.getRange1()) || getRange2().intersects(patch2.getRange2())) {
            throw new IllegalArgumentException("Patches overlap");
        }

        while (start1 + length1 < patch2.start1) {
            diffs.add(new Diff(Operation.EQUAL, lines1.get(start1 + length1)));
            length1++;
            length2++;
        }

        if (start2 + length2 != patch2.start2) {
            throw new IllegalArgumentException("Unequal distance between end of patch1 and start of patch2 in context and patched");
        }
        diffs.addAll(patch2.diffs);
        length1 += patch2.length1;
        length2 += patch2.length2;
    }

    //@formatter:off
    public String getHeader() { return String.format("@@ -%d,%d +%d,%d @@", start1 + 1, length1, start2 + 1, length2); }
    public String getAutoHeader() { return String.format("@@ -%d,%d +_,%d @@", start1 + 1, length1, length2); }
    public List<String> getContextLines() { return getContextLines(Function.identity()); }
    public List<String> getContextLines(Function<String, String> f) { return FastStream.of(diffs).filter(e -> e.op != Operation.INSERT).map(e -> f.apply(e.text)).toList(); }
    public List<String> getPatchedLines() { return getPatchedLines(Function.identity()); }
    public List<String> getPatchedLines(Function<String, String> f) { return FastStream.of(diffs).filter(e -> e.op != Operation.DELETE).map(e -> f.apply(e.text)).toList(); }
    public LineRange getRange1() { return LineRange.fromStartLen(start1, length1); }
    public LineRange getRange2() { return LineRange.fromStartLen(start2, length2); }
    public LineRange getTrimmedRange1() { return trimRange(getRange1()); }
    public LineRange getTrimmedRange2() { return trimRange(getRange2()); }
    //@formatter:on

    @Override
    public String toString() {
        return getHeader() + "\n" + FastStream.of(diffs).map(Diff::toString).join("\n");
    }
}
