package io.codechicken.diffpatch.match;

import java.util.Objects;

public class FuzzyMatchOptions {
    public static final FuzzyMatchOptions DEFAULT = new FuzzyMatchOptions(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, true, FuzzyLineMatcher.MatchMatrix.DEFAULT_INSERTED_LINE_PENALTY);
    
    private final int maxMatchOffset;
    private final float minMatchScore;
    private final boolean enableDistancePenalty;
    private final float insertedLinePenalty;
    
    public FuzzyMatchOptions(int maxMatchOffset, float minMatchScore, boolean enableDistancePenalty, float insertedLinePenalty) {
        this.maxMatchOffset = maxMatchOffset;
        this.minMatchScore = minMatchScore;
        this.enableDistancePenalty = enableDistancePenalty;
        this.insertedLinePenalty = insertedLinePenalty;
    }

    public int maxMatchOffset() {
        return maxMatchOffset;
    }

    public float minMatchScore() {
        return minMatchScore;
    }

    public boolean enableDistancePenalty() {
        return enableDistancePenalty;
    }

    public float insertedLinePenalty() {
        return insertedLinePenalty;
    }

    public FuzzyMatchOptions maxMatchOffset(int maxMatchOffset) {
        return new FuzzyMatchOptions(maxMatchOffset, this.minMatchScore, this.enableDistancePenalty, this.insertedLinePenalty);
    }

    public FuzzyMatchOptions minMatchScore(float minMatchScore) {
        return new FuzzyMatchOptions(this.maxMatchOffset, minMatchScore, this.enableDistancePenalty, this.insertedLinePenalty);
    }

    public FuzzyMatchOptions enableDistancePenalty(boolean enableDistancePenalty) {
        return new FuzzyMatchOptions(this.maxMatchOffset, this.minMatchScore, enableDistancePenalty, this.insertedLinePenalty);
    }

    public FuzzyMatchOptions insertedLinePenalty(float insertedLinePenalty) {
        return new FuzzyMatchOptions(this.maxMatchOffset, this.minMatchScore, this.enableDistancePenalty, insertedLinePenalty);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FuzzyMatchOptions)) return false;
        FuzzyMatchOptions other = (FuzzyMatchOptions) obj;
        return this.maxMatchOffset == other.maxMatchOffset
                && this.minMatchScore == other.minMatchScore
                && this.enableDistancePenalty == other.enableDistancePenalty
                && this.insertedLinePenalty == other.insertedLinePenalty;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.maxMatchOffset, this.minMatchScore, this.enableDistancePenalty, this.insertedLinePenalty);
    }
}
