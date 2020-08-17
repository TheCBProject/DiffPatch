package codechicken.diffpatch.match;

import codechicken.diffpatch.util.LineRange;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

public class FuzzyLineMatcher {

    public static final float DEFAULT_MIN_MATCH_SCORE = 0.5f;

    public int maxMatchOffset = MatchMatrix.DEFAULT_MAX_OFFSET;
    public float minMatchScore = DEFAULT_MIN_MATCH_SCORE;

    public void matchLinesByWords(int[] matches, List<String> wmLines1, List<String> wmLines2) {
        for (Pair<LineRange, LineRange> entry : LineMatching.unmatchedRanges(matches, wmLines2.size())) {
            LineRange range1 = entry.getLeft();
            LineRange range2 = entry.getRight();
            if (range1.getLength() == 0 || range2.getLength() == 0) {
                continue;
            }

            int[] match = match(wmLines1.subList(range1.getStart(), range1.getEnd()), wmLines2.subList(range2.getStart(), range2.getEnd()));
            for (int i = 0; i < match.length; i++) {
                if (match[i] >= 0) {
                    matches[range1.getStart() + i] = range2.getStart() + match[i];
                }
            }
        }
    }

    public int[] match(List<String> pattern, List<String> search) {
        if (search.size() < pattern.size()) {
            int[] rMatch = match(search, pattern);
            int[] nMatch = new int[pattern.size()];
            Arrays.fill(nMatch, -1);

            for (int i = 0; i < rMatch.length; i++) {
                if (rMatch[i] >= 0) {
                    nMatch[rMatch[i]] = i;
                }
            }

            return nMatch;
        }

        if (pattern.size() == 0) {
            return new int[0];
        }

        float bestScore = minMatchScore;
        int[] bestMatch = null;

        MatchMatrix mm = new MatchMatrix(pattern, search, maxMatchOffset, null);
        for (int i = mm.workingRange.getFirst(); ; i++) {
            Pair<Boolean, Float> pair = mm.match(i);
            if (!pair.getLeft()) {
                break;
            }
            float score = pair.getRight();
            if (score > bestScore) {
                bestScore = score;
                bestMatch = mm.path();
            }
        }

        if (bestMatch == null) {
            int[] ret = new int[pattern.size()];
            Arrays.fill(ret, -1);
            return ret;
        }

        return bestMatch;
    }

    //assumes the lines are in word to char mode
    //return 0.0 poor match to 1.0 perfect match
    //uses LevenshtienDistance. A distance with half the maximum number of errors is considered a 0.0 scored match
    public static float matchLines(String s, String t) {
        int d = levenshteinDistance(s, t);
        if (d == 0) {
            return 1f;//perfect match
        }

        float max = Math.max(s.length(), t.length()) / 2f;
        return Math.max(0f, 1f - d / max);
    }

    //https://en.wikipedia.org/wiki/Levenshtein_distance
    public static int levenshteinDistance(String s, String t) {
        // degenerate cases
        if (s.equals(t)) {
            return 0;
        }
        if (s.length() == 0) {
            return t.length();
        }
        if (t.length() == 0) {
            return s.length();
        }

        // create two work vectors of integer distances
        //previous
        int[] v0 = new int[t.length() + 1];
        //current
        int[] v1 = new int[t.length() + 1];

        // initialize v1 (the current row of distances)
        // this row is A[0][i]: edit distance for an empty s
        // the distance is just the number of characters to delete from t
        for (int i = 0; i < v1.length; i++) {
            v1[i] = i;
        }

        for (int i = 0; i < s.length(); i++) {
            // swap v1 to v0, reuse old v0 as new v1
            int[] tmp = v0;
            v0 = v1;
            v1 = tmp;

            // calculate v1 (current row distances) from the previous row v0

            // first element of v1 is A[i+1][0]
            //   edit distance is delete (i+1) chars from s to match empty t
            v1[0] = i + 1;

            // use formula to fill in the rest of the row
            for (int j = 0; j < t.length(); j++) {
                int del = v0[j + 1] + 1;
                int ins = v1[j] + 1;
                int subs = v0[j] + (s.charAt(i) == t.charAt(j) ? 0 : 1);
                v1[j + 1] = Math.min(del, Math.min(ins, subs));
            }
        }

        return v1[t.length()];
    }

    public static class MatchMatrix {

        public static final int DEFAULT_MAX_OFFSET = 5;

        private final int patternLength;
        private final LineRange range;
        //maximum offset between line matches in a run
        private final int maxOffset;

        public LineRange workingRange;

        // location of first pattern line in search lines. Starting offset for a match
        private int pos = Integer.MIN_VALUE;
        //consecutive matches for pattern offset from loc by up to maxOffset
        //first entry is for pattern starting at loc in text, last entry is offset +maxOffset
        private final StraightMatch[] matches;
        //offset index of first node in best path
        private int firstNode;

        public MatchMatrix(List<String> pattern, List<String> search) {
            this(pattern, search, DEFAULT_MAX_OFFSET, null);
        }

        public MatchMatrix(List<String> pattern, List<String> search, int maxOffset, LineRange range) {
            if (range == null) {
                range = LineRange.fromStartLen(0, search.size());
            }
            patternLength = pattern.size();
            this.range = range;
            this.maxOffset = maxOffset;
            workingRange = LineRange.fromFirstLast(range.getStart() - maxOffset, range.getEnd() - patternLength);

            matches = new StraightMatch[maxOffset + 1];
            for (int i = 0; i <= maxOffset; i++) {
                matches[i] = new StraightMatch(pattern, search, range);
            }
        }

        public Pair<Boolean, Float> match(int loc) {
            MutablePair<Boolean, Float> ret = new MutablePair<>();
            ret.setRight(0f);
            if (!workingRange.contains(loc)) {
                ret.setLeft(false);
                return ret;
            }

            if (loc == pos + 1) {
                stepForward();
            } else if (loc == pos - 1) {
                stepBackward();
            } else {
                init(loc);
            }

            ret.setRight(recalculate());
            ret.setLeft(true);
            return ret;
        }

        private void init(int loc) {
            pos = loc;

            for (int i = 0; i <= maxOffset; i++) {
                matches[i].update(loc + i);
            }
        }

        private void stepForward() {
            pos++;

            StraightMatch reuse = matches[0];
            for (int i = 1; i <= maxOffset; i++) {
                matches[i - 1] = matches[i];
            }

            matches[maxOffset] = reuse;
            reuse.update(pos + maxOffset);
        }

        private void stepBackward() {
            pos--;

            StraightMatch reuse = matches[maxOffset];
            for (int i = maxOffset; i > 0; i--) {
                matches[i] = matches[i - 1];
            }

            matches[0] = reuse;
            reuse.update(pos);
        }

        //calculates the best path through the match matrix
        //all paths must start with the first line of pattern matched to the line at loc (0 offset)
        private float recalculate() {
            //tail nodes have sum = score
            for (int j = 0; j <= maxOffset; j++) {
                MatchNode node = matches[j].nodes[patternLength - 1];
                node.sum = node.score;
                node.next = -1;//no next
            }

            //calculate best paths for all nodes excluding head
            for (int i = patternLength - 2; i >= 0; i--) {
                for (int j = 0; j <= maxOffset; j++) {
                    //for each node
                    MatchNode node = matches[j].nodes[i];
                    int maxk = -1;
                    float maxsum = 0;
                    for (int k = 0; k <= maxOffset; k++) {
                        int l = i + offsetsToPatternDistance(j, k);
                        if (l >= patternLength) {
                            continue;
                        }

                        float sum = matches[k].nodes[l].sum;
                        if (k > j) {
                            sum -= 0.5f * (k - j); //penalty for skipping lines in search text
                        }

                        if (sum > maxsum) {
                            maxk = k;
                            maxsum = sum;
                        }
                    }

                    node.sum = maxsum + node.score;
                    node.next = maxk;
                }
            }

            //find starting node
            {
                firstNode = 0;
                float maxsum = matches[0].nodes[0].sum;
                for (int k = 1; k <= maxOffset; k++) {
                    float sum = matches[k].nodes[0].sum;
                    if (sum > maxsum) {
                        firstNode = k;
                        maxsum = sum;
                    }
                }
            }

            //return best path value
            return matches[firstNode].nodes[0].sum / patternLength;
        }

        private int locInRange(int loc) {
            return range.contains(loc) ? loc : -1;
        }

        public int[] path() {
            int[] path = new int[patternLength];

            int offset = firstNode; //offset of current node
            MatchNode node = matches[firstNode].nodes[0];
            path[0] = locInRange(pos + offset);

            int i = 0; //index in pattern of current node
            while (node.next >= 0) {
                int delta = offsetsToPatternDistance(offset, node.next);
                while (delta-- > 1) //skipped pattern lines
                {
                    path[++i] = -1;
                }

                offset = node.next;
                node = matches[offset].nodes[++i];
                path[i] = locInRange(pos + i + offset);
            }

            while (++i < path.length)//trailing lines with no match
            {
                path[i] = -1;
            }

            return path;
        }

        public String visualise() {
            int[] path = path();
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j <= maxOffset; j++) {
                sb.append(j).append(':');
                StraightMatch line = matches[j];
                for (int i = 0; i < patternLength; i++) {
                    boolean inPath = path[i] > 0 && path[i] == pos + i + j;
                    sb.append(inPath ? '[' : ' ');
                    int score = Math.round(line.nodes[i].score * 100);
                    sb.append(score == 100 ? "%%" : score);
                    sb.append(inPath ? ']' : ' ');
                }
                sb.append("\n");
            }
            return sb.toString();
        }

        private static int offsetsToPatternDistance(int i, int j) {
            return j >= i ? 1 : 1 + i - j;
        }

        //contains match entries for consecutive characters of a pattern and the search text starting at line offset loc
        private static class StraightMatch {

            private final int patternLength;
            private final List<String> pattern;
            private final List<String> search;
            private final LineRange range;

            public final MatchNode[] nodes;

            public StraightMatch(List<String> pattern, List<String> search, LineRange range) {
                patternLength = pattern.size();
                this.pattern = pattern;
                this.search = search;
                this.range = range;

                nodes = new MatchNode[patternLength];
                for (int i = 0; i < patternLength; i++) {
                    nodes[i] = new MatchNode();
                }
            }

            public void update(int loc) {
                for (int i = 0; i < patternLength; i++) {
                    int l = i + loc;
                    if (l < range.getStart() || l >= range.getEnd()) {
                        nodes[i].score = 0;
                    } else {
                        nodes[i].score = matchLines(pattern.get(i), search.get(l));
                    }
                }
            }
        }

        private static class MatchNode {

            //score of this match (1.0 = perfect, 0.0 = no match)
            public float score;
            //sum of the match scores in the best path up to this node
            public float sum;
            //offset index of the next node in the path
            public int next;
        }
    }
}
