package codechicken.diffpatch.diff;

import codechicken.diffpatch.match.FuzzyLineMatcher;
import codechicken.diffpatch.util.CharRepresenter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by covers1624 on 15/5/21.
 */
public class LineMatchedDiffer extends PatienceDiffer {

    private List<String> wordModeLines1;
    private List<String> wordModeLines2;

    private int maxMatchOffset;
    private int minMatchScore;

    public LineMatchedDiffer() {
        super();
    }

    public LineMatchedDiffer(CharRepresenter charRep) {
        super(charRep);
    }

    @Override
    public int[] match(List<String> lines1, List<String> lines2) {
        int[] matches = super.match(lines1, lines2);
        wordModeLines1 = lines1.stream().map(charRep::wordsToChars).collect(Collectors.toList());
        wordModeLines2 = lines2.stream().map(charRep::wordsToChars).collect(Collectors.toList());
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
    public int getMinMatchScore() { return minMatchScore; }
    public void setMinMatchScore(int minMatchScore) { this.minMatchScore = minMatchScore; }
    //@formatter:on
}
