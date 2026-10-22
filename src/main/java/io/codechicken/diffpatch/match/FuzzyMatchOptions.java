package io.codechicken.diffpatch.match;

import java.util.Objects;

public class FuzzyMatchOptions {
    public static final FuzzyMatchOptions DEFAULT = new FuzzyMatchOptions(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, true);
    
    private final int maxMatchOffset;
    private final float minMatchScore;
    private final boolean enableDistancePenalty;
    
    public FuzzyMatchOptions(int maxMatchOffset, float minMatchScore, boolean enableDistancePenalty) {
        this.maxMatchOffset = maxMatchOffset;
        this.minMatchScore = minMatchScore;
        this.enableDistancePenalty = enableDistancePenalty;
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

    public FuzzyMatchOptions maxMatchOffset(int maxMatchOffset) {
        return new FuzzyMatchOptions(maxMatchOffset, this.minMatchScore, this.enableDistancePenalty);
    }

    public FuzzyMatchOptions minMatchScore(float minMatchScore) {
        return new FuzzyMatchOptions(this.maxMatchOffset, minMatchScore, this.enableDistancePenalty);
    }

    public FuzzyMatchOptions enableDistancePenalty(boolean enableDistancePenalty) {
        return new FuzzyMatchOptions(this.maxMatchOffset, this.minMatchScore, enableDistancePenalty);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FuzzyMatchOptions)) return false;
        FuzzyMatchOptions other = (FuzzyMatchOptions) obj;
        return this.maxMatchOffset == other.maxMatchOffset
                && this.minMatchScore == other.minMatchScore
                && this.enableDistancePenalty == other.enableDistancePenalty;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.maxMatchOffset, this.minMatchScore, this.enableDistancePenalty);
    }
}
