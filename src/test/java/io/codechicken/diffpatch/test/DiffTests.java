package io.codechicken.diffpatch.test;

import java.io.IOException;

import io.codechicken.diffpatch.diff.LineMatchedDiffer;
import io.codechicken.diffpatch.diff.PatienceDiffer;
import org.junit.jupiter.api.Test;

public class DiffTests extends TestBase {
    @Test
    public void SimpleInsert() throws IOException {
        assertDiff(null, null, null, -1, false);
    }

    @Test
    public void AutoOffset() throws IOException {
        assertDiff(null, "SimpleInsert", null, -1, true);
    }

    @Test
    public void LineMatched() throws IOException {
        assertDiff(new LineMatchedDiffer(), "Config", "Config_LineMatched", -1, false);
    }

    @Test
    public void LineMatchedWithPatience() throws IOException {
        assertDiff(new PatienceDiffer(), "Config", "Config_Patience", -1, false);
    }

    @Test
    public void MergedContext() throws IOException {
        assertDiff(null, null, null, 2, false);
    }

    @Test
    public void SplitContext() throws IOException {
        assertDiff(null, "MergedContext", null, 1, false);
    }
}
