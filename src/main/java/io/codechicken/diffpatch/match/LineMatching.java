package io.codechicken.diffpatch.match;

import io.codechicken.diffpatch.util.Diff;
import io.codechicken.diffpatch.util.LineRange;
import io.codechicken.diffpatch.util.Operation;
import io.codechicken.diffpatch.util.Patch;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class LineMatching {

    public static List<Pair<LineRange, LineRange>> unmatchedRanges(int[] matches, int len2) {
        List<Pair<LineRange, LineRange>> ret = new ArrayList<>();
        int len1 = matches.length;
        int start1 = 0, start2 = 0;
        do {
            //search for a matchpoint
            int end1 = start1;
            while (end1 < len1 && matches[end1] < 0) {
                end1++;
            }

            int end2 = end1 == len1 ? len2 : matches[end1];
            if (end1 != start1 || end2 != start2) {
                ret.add(Pair.of(new LineRange(start1, end1), new LineRange(start2, end2)));
                start1 = end1;
                start2 = end2;
            } else {//matchpoint follows on from start, no unmatched lines
                start1++;
                start2++;
            }
        }
        while (start1 < len1 || start2 < len2);
        return ret;
    }

    public static int[] fromUnmatchedRanges(List<Pair<LineRange, LineRange>> unmatchedRanges, int len1) {
        int[] matches = new int[len1];
        int start1 = 0, start2 = 0;
        for (Pair<LineRange, LineRange> entry : unmatchedRanges) {
            LineRange range1 = entry.getLeft();
            LineRange range2 = entry.getRight();
            while (start1 < range1.getStart()) {
                matches[start1++] = start2++;
            }

            if (start2 != range2.getStart()) {
                throw new IllegalArgumentException("Unequal number of lines between umatched ranges on each side");
            }

            while (start1 < range1.getEnd()) {
                matches[start1++] = -1;
            }

            start2 = range2.getEnd();
        }

        while (start1 < len1) {
            matches[start1++] = start2++;
        }

        return matches;
    }

    public static List<Pair<LineRange, LineRange>> unmatchedRanges(List<Patch> patches) {
        List<Pair<LineRange, LineRange>> ret = new ArrayList<>();
        for (Patch patch : patches) {
            List<Diff> diffs = patch.diffs;
            int start1 = patch.start1, start2 = patch.start2;
            for (int i = 0; i < diffs.size(); ) {
                //skip matched
                while (i < diffs.size() && diffs.get(i).op == Operation.EQUAL) {
                    start1++;
                    start2++;
                    i++;
                }

                int end1 = start1, end2 = start2;
                while (i < diffs.size() && diffs.get(i).op != Operation.EQUAL) {
                    if (diffs.get(i++).op == Operation.DELETE) {
                        end1++;
                    } else {
                        end2++;
                    }
                }

                if (end1 != start1 || end2 != start2) {
                    ret.add(Pair.of(new LineRange(start1, end1), new LineRange(start2, end2)));
                }

                start1 = end1;
                start2 = end2;
            }
        }
        return ret;
    }

    public static int[] fromPatches(List<Patch> patches, int len1) {
        return fromUnmatchedRanges(unmatchedRanges(patches), len1);
    }

    public static List<Diff> makeDiffList(int[] matches, List<String> lines1, List<String> lines2) {
        List<Diff> list = new ArrayList<>();
        int l = 0;
        int r = 0;
        for (int i = 0; i < matches.length; i++) {
            if (matches[i] < 0) {
                continue;
            }

            while (l < i) {
                list.add(new Diff(Operation.DELETE, lines1.get(l++)));
            }
            while (r < matches[i]) {
                list.add(new Diff(Operation.INSERT, lines2.get(r++)));
            }
            if (!lines1.get(l).equals(lines2.get(r))) {
                list.add(new Diff(Operation.DELETE, lines1.get(l)));
                list.add(new Diff(Operation.INSERT, lines2.get(r)));
            } else {
                list.add(new Diff(Operation.EQUAL, lines1.get(l)));
            }
            l++;
            r++;
        }
        while (l < lines1.size()) {
            list.add(new Diff(Operation.DELETE, lines1.get(l++)));
        }
        while (r < lines2.size()) {
            list.add(new Diff(Operation.INSERT, lines2.get(r++)));
        }
        return list;
    }
}
