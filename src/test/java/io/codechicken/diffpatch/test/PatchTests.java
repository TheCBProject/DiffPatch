package io.codechicken.diffpatch.test;

import io.codechicken.diffpatch.match.FuzzyMatchOptions;
import io.codechicken.diffpatch.patch.Patcher;
import io.codechicken.diffpatch.util.PatchMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class PatchTests extends TestBase {
    @Test
    public void SimpleInsert() throws IOException {
        assertPatch(null, null, null, null, null);
    }

    @Test
    public void OffsetPatch() throws IOException {
        Patcher.Result result = assertPatch(PatchMode.OFFSET, null, "SimpleInsert", null, null);
        assertEquals(PatchMode.OFFSET, result.mode);
        assertEquals(2, result.offset);
    }

    @Test
    public void FuzzyPatch() throws IOException {
        Patcher.Result result = assertPatch(PatchMode.FUZZY, null, "SimpleInsert", null, null);
        assertEquals(PatchMode.FUZZY, result.mode);
        assertTrue(result.fuzzyQuality > 0.8f, "Expected quality > 80%, got " + result.fuzzyQuality * 100);
    }

    @Test
    public void FuzzyPatchHighThreshold() throws IOException {
        Patcher.Result result = patch(PatchMode.FUZZY, FuzzyMatchOptions.DEFAULT.minMatchScore(0.95f), "SimpleInsert", "FuzzyPatch");
        assertFalse(result.success, "Patch should fail when min match score exceeds actual quality");
    }

    @Test
    public void FailedPatch() throws IOException {
        Patcher.Result result = patch(PatchMode.FUZZY, null, "SimpleInsert", null);
        assertFalse(result.success, "Patch should fail against a completely different file");
    }

    @Test
    public void FuzzyInsertedLines_LineMatched() throws IOException {
        Patcher.Result result = assertPatch(PatchMode.FUZZY, null, "ConfigInsertedLines", "Config_LineMatched", "ConfigInsertedLines_LineMatched");
        assertEquals(PatchMode.FUZZY, result.mode);
        // Patch is 7 lines long which perfectly match (Quality 100%)
        // Context has 4 inserted lines, with penalty 0.5 each
        assertEquals((7 - 0.5f * 4) / 7, result.fuzzyQuality, "Expected quality");
    }

    @Test
    public void FuzzyInsertedLines_Patience() throws IOException {
        Patcher.Result result = assertPatch(PatchMode.FUZZY, null, "ConfigInsertedLines", "Config_Patience", "ConfigInsertedLines_Patience");
        assertEquals(PatchMode.FUZZY, result.mode);
        assertEquals((7 - 0.5f * 4) / 7, result.fuzzyQuality, "Expected quality");
    }

    @Test
    public void FuzzyInsertedBulkLines() throws IOException {
        Patcher.Result result = assertPatch(PatchMode.FUZZY, null, "ConfigInsertedBulkLines", "Config_LineMatched", "ConfigInsertedBulkLines");
        assertEquals(PatchMode.FUZZY, result.mode);
        // 0:[%%][%%][%%][%%] 45  00  00 
        // 1: 00  00  00  64  00  00  00 
        // 2: 00  00  00  00  00  64  00 
        // 3: 00  00  00  00 [%%][%%][%%]
        // 4: 00  00  00  64  64  00  00 
        // 5: 00  00  00  64  00  00  00 
        // -:              -150        
        // Total Score: 550 / 700 = 79%
        assertEquals((7 - 0.5f * 3) / 7, result.fuzzyQuality, "Expected quality");
    }

    @Test
    public void MaxMatchOffset1() throws IOException {
        // With MaxMatchOffset = 1 we can only skip line 4 (public int Zero = 0; // extra tokens make this an extra bad match)

        // 0:[%%][%%][%%] 00  64  20  00 
        // 1: 00  00  00 [%%][20][64] 00 
        // -:          -50             
        // Total Score: 434 / 700 = 62%

        // 20% = Score("public int Alpha2 = Alpha * Alpha;", "public int Beta = 2;")
        // 64% = Score("public int Beta = 2;", "public int Gamma = 3;")

        Patcher.Result result = assertPatch(
                PatchMode.FUZZY,
                FuzzyMatchOptions.DEFAULT.maxMatchOffset(1),
                "ConfigInsertedLines",
                "Config_LineMatched",
                "ConfigInsertedLines_MaxMatchOffset1"
        );

        assertEquals(0.62f, result.fuzzyQuality, 0.01f, "Expected quality");
    }

    @Test
    public void MaxMatchOffset2() throws IOException {
        // With MaxMatchOffset = 2, the results are the same as with MaxMatchOffset = 1
        // The added line penalty is too high to get the better match on "public int Beta = 2;"
        // If the score for mismatching Beta and Gamma lines was lower than 50%, or the inserted line penalty was lower, we would have a different output

        // 0:[%%][%%][%%] 00  64  20  00 
        // 1: 00  00  00 [%%][20][64] 00 
        // 2: 00  00  00  20  %%  20  00 
        // -:          -50             
        // Total Score: 434 / 700 = 62%
        Patcher.Result result = assertPatch(
                PatchMode.FUZZY,
                FuzzyMatchOptions.DEFAULT.maxMatchOffset(2),
                "ConfigInsertedLines",
                "Config_LineMatched",
                "ConfigInsertedLines_MaxMatchOffset2"
        );
        assertEquals(0.62f, result.fuzzyQuality, 0.01f, "Expected quality");
    }

    @Test
    public void MaxMatchOffsetLocation() throws IOException {
        // 0: 00  00  00  00  00  64  00 
        // 1: 00  00  00  00  64  20  00 
        // 2:[%%][%%][%%][%%] 20  64  00 
        // 3: 00  00  00  20 [%%] 20  00 
        // 4: 00  00  00  64  20 [%%] 00 
        // 5: 00  00  00  20  64  20 [%%]
        // -:              -50 -50 -50 
        // Total Score: 550 / 700 = 79%
        Patcher.Result defaultOffset = assertPatch(
                PatchMode.FUZZY,
                null,
                "MaxMatchOffsetLocation",
                "Config_LineMatched",
                null
        );
        assertEquals(0.79f, defaultOffset.fuzzyQuality, 0.01f, "Expected quality");

        // 0:[%%][00][%%][64][64][64][%%]
        // -: 
        // Total Score: 491 / 700 = 70%
        Patcher.Result zeroOffset = assertPatch(
                PatchMode.FUZZY,
                FuzzyMatchOptions.DEFAULT.maxMatchOffset(0),
                "MaxMatchOffsetLocation",
                "Config_LineMatched",
                "MaxMatchOffsetLocation0"
        );
        assertEquals(0.70f, zeroOffset.fuzzyQuality, 0.01f, "Expected quality");
    }
    
    @Test
    public void DistancePenalty() throws IOException {
        // File has two fuzzy matches: a close one and a distant better one
        // With distance penalty, the close match wins despite lower quality
        // Without penalty, the far match wins due to higher quality

        // We use MaxMatchOffset = 0 to get a more precise test, because the MaxOffset of the matcher also gets subtracted from the warn distance when matching forward

        // 0:[%%][78][%%][%%][50][%%]
        // Total Score: 528 / 600 = 
        Patcher.Result withPenalty = assertPatch(PatchMode.FUZZY, FuzzyMatchOptions.DEFAULT.maxMatchOffset(0), null, null, null);
        assertEquals(0.88f, withPenalty.fuzzyQuality, 0.01f, "Expected quality");

        // 0:[%%][%%][%%][%%][75][%%]
        // Total Score: 575 / 600 = 96%
        Patcher.Result withoutPenalty = assertPatch(
                PatchMode.FUZZY,
                FuzzyMatchOptions.DEFAULT.maxMatchOffset(0).enableDistancePenalty(false),
                null,
                null,
                "DistancePenaltyOff"
        );
        assertEquals(0.96f, withoutPenalty.fuzzyQuality, 0.01f, "Expected quality");

        // OffsetWarnDistance impl
        //   Max(patchLength * 10, fileLength / 10) = Max(6 * 10, 136 / 10)
        //
        // The penalty is 10% for every 60 lines, after the first 10%
        // For penalty to be large enough to beat the quality difference (8%) we need to be offset at least (60 + 60*0.8) = 108 lines
        double minOffset = 60 / 0.1 * (withoutPenalty.fuzzyQuality - withPenalty.fuzzyQuality + 0.1);
        assertNotNull(withPenalty.appliedPatch);
        assertNotNull(withoutPenalty.appliedPatch);

        assertTrue(withPenalty.appliedPatch.start2 + minOffset > withoutPenalty.appliedPatch.start2, "Patch without penalty should apply at a much higher offset; expected value greater than " + withoutPenalty.appliedPatch.start2 + ", got " + withPenalty.appliedPatch.start2);
    }
}
