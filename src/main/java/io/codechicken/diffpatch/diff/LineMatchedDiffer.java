package io.codechicken.diffpatch.diff;

import io.codechicken.diffpatch.match.FuzzyLineMatcher;
import io.codechicken.diffpatch.util.CharRepresenter;
import net.covers1624.quack.collection.FastStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by covers1624 on 15/5/21.
 */
public class LineMatchedDiffer extends PatienceDiffer {

    private List<String> wordModeLines1 = new ArrayList<>();
    private List<String> wordModeLines2 = new ArrayList<>();

    private int maxMatchOffset = FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET;
    private float minMatchScore = FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE;

    public LineMatchedDiffer() {
        super();
    }

    public LineMatchedDiffer(CharRepresenter charRep) {
        super(charRep);
    }

    @Override
    public int[] match(List<String> lines1, List<String> lines2) {
        int[] matches = super.match(lines1, lines2);
        wordModeLines1 = FastStream.of(lines1).map(charRep::wordsToChars).toList();
        wordModeLines2 = FastStream.of(lines2).map(charRep::wordsToChars).toList();
        FuzzyLineMatcher matcher = new FuzzyLineMatcher();
        matcher.maxMatchOffset = maxMatchOffset;
        matcher.minMatchScore = minMatchScore;
        matcher.matchLinesByWords(matches, wordModeLines1, wordModeLines2);
        return matches;
    }

    //@formatter:off
    public List<String> getWordModeLines1() { return wordModeLines1; }
    public List<String> getWordModeLines2() { return wordModeLines2; }
    public int getMaxMatchOffset() { return maxMatchOffset; }
    public void setMaxMatchOffset(int maxMatchOffset) { this.maxMatchOffset = maxMatchOffset; }
    public float getMinMatchScore() { return minMatchScore; }
    public void setMinMatchScore(float minMatchScore) { this.minMatchScore = minMatchScore; }
    //@formatter:on
}
