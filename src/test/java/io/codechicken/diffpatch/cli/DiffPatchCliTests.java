package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.diff.Differ;
import io.codechicken.diffpatch.match.FuzzyLineMatcher;
import io.codechicken.diffpatch.test.TestBase;
import io.codechicken.diffpatch.util.ConsumingOutputStream;
import io.codechicken.diffpatch.util.Input;
import io.codechicken.diffpatch.util.Output;
import io.codechicken.diffpatch.util.PatchMode;
import net.covers1624.quack.io.NullOutputStream;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static io.codechicken.diffpatch.cli.DiffPatchCli.parseOperation;
import static net.covers1624.quack.util.SneakyUtils.unsafeCast;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests mostly to ensure that our CLI parsing correctly detects the input/output path combinations
 * and correctly picks the right [Multi/Single]Input/Output implementation.
 * <p>
 * Created by covers1624 on 14/6/24.
 */
public class DiffPatchCliTests extends TestBase {

    // region Help
    @Test
    public void testNothing() throws IOException {
        List<String> help = new ArrayList<>();
        assertNull(parse(help));
        assertFalse(help.isEmpty());
    }

    @Test
    public void testHelpOpt() throws IOException {
        List<String> help = new ArrayList<>();
        assertNull(parse(help, "--help"));
        assertFalse(help.isEmpty());
    }

    @Test
    public void testWithNoPaths() throws IOException {
        List<String> help = new ArrayList<>();
        assertNull(parse(help));
        assertFalse(help.isEmpty());
    }

    @Test
    public void testWithNoOperation() throws IOException {
        List<String> help = new ArrayList<>();
        assertNull(parse(help, "./a", "./b"));
        assertFalse(help.isEmpty());
    }
    // endregion

    // region Diff
    @Test
    public void testDiff() throws IOException {
        List<String> help = new ArrayList<>();
        DiffOperation op = parse(help, "--diff", "./a", "./b");
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.FromPath);
        assertTrue(op.changedInput instanceof Input.SingleInput.FromPath);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertFalse(op.autoHeader);
        assertEquals(Differ.DEFAULT_CONTEXT, op.context);
        assertTrue(op.patchOutput instanceof Output.SingleOutput.ToStream);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testDiffOptions() throws IOException {
        List<String> help = new ArrayList<>();
        DiffOperation op = parse(help, "--diff", "--auto-header", "--context", "32", "--summary", "./a", "./b");
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertTrue(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.FromPath);
        assertTrue(op.changedInput instanceof Input.SingleInput.FromPath);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.autoHeader);
        assertEquals(32, op.context);
        assertTrue(op.patchOutput instanceof Output.SingleOutput.ToStream);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testDiffToFile() throws IOException {
        List<String> help = new ArrayList<>();
        DiffOperation op = parse(help, "--diff", "--output", "./asdf/o", "./asdf/a", "./asdf/b");
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.FromPath);
        assertTrue(op.changedInput instanceof Input.SingleInput.FromPath);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertFalse(op.autoHeader);
        assertEquals(Differ.DEFAULT_CONTEXT, op.context);
        assertTrue(op.patchOutput instanceof Output.SingleOutput.ToPath);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testDiffArchives() throws IOException {
        List<String> help = new ArrayList<>();
        DiffOperation op = parse(help,
                "--diff",
                "--output", "./asdf/o",
                "--archive", "ZIP",
                "--archive-base", "ZIP",
                "--archive-modified", "ZIP",
                "./asdf/a",
                "./asdf/b"
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.MultiInput.PathArchiveMultiInput);
        assertTrue(op.changedInput instanceof Input.MultiInput.PathArchiveMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertFalse(op.autoHeader);
        assertEquals(Differ.DEFAULT_CONTEXT, op.context);
        assertTrue(op.patchOutput instanceof Output.MultiOutput.PathArchiveMultiOutput);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testDiffArchives2() throws IOException {
        List<String> help = new ArrayList<>();
        DiffOperation op = parse(help,
                "--diff",
                "--output", "./asdf/o.zip",
                "./asdf/a.zip",
                "./asdf/b.zip"
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.MultiInput.PathArchiveMultiInput);
        assertTrue(op.changedInput instanceof Input.MultiInput.PathArchiveMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertFalse(op.autoHeader);
        assertEquals(Differ.DEFAULT_CONTEXT, op.context);
        assertTrue(op.patchOutput instanceof Output.MultiOutput.PathArchiveMultiOutput);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testDiffArchives3() throws IOException {
        List<String> help = new ArrayList<>();
        DiffOperation op = parse(help,
                "--diff",
                "--archive", "ZIP",
                "./asdf/a.zip",
                "./asdf/b.zip"
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.MultiInput.PathArchiveMultiInput);
        assertTrue(op.changedInput instanceof Input.MultiInput.PathArchiveMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertFalse(op.autoHeader);
        assertEquals(Differ.DEFAULT_CONTEXT, op.context);
        assertTrue(op.patchOutput instanceof Output.MultiOutput.PipeArchiveMultiOutput);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testDiffToFolder(@TempDir Path folder) throws IOException {
        List<String> help = new ArrayList<>();
        DiffOperation op = parse(help, "--diff", "--output", folder.toAbsolutePath().toString(), folder.toAbsolutePath().toString(), folder.toAbsolutePath().toString());
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.MultiInput.FolderMultiInput);
        assertTrue(op.changedInput instanceof Input.MultiInput.FolderMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertFalse(op.autoHeader);
        assertEquals(Differ.DEFAULT_CONTEXT, op.context);
        assertTrue(op.patchOutput instanceof Output.MultiOutput.FolderMultiOutput);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }
    // endregion

    // region Patch
    @Test
    public void testPatch() throws IOException {
        List<String> help = new ArrayList<>();
        PatchOperation op = parse(help, "--patch", "./asdf/a", "./asdf/b");
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.FromPath);
        assertTrue(op.patchesInput instanceof Input.SingleInput.FromPath);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.patchedOutput instanceof Output.SingleOutput.ToStream);
        assertNull(op.rejectsOutput);
        assertEquals(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, op.minFuzz);
        assertEquals(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, op.maxOffset);
        assertEquals(PatchMode.EXACT, op.mode);
        assertEquals("", op.patchesPrefix);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testPatchOptions() throws IOException {
        List<String> help = new ArrayList<>();
        PatchOperation op = parse(help, "--patch", "--summary", "--fuzz", "69.0", "-offset", "32", "--mode", "FUZZY", "--prefix", "asdf/", "./asdf/a", "./asdf/b");
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertTrue(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.FromPath);
        assertTrue(op.patchesInput instanceof Input.SingleInput.FromPath);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.patchedOutput instanceof Output.SingleOutput.ToStream);
        assertNull(op.rejectsOutput);
        assertEquals(69.0F, op.minFuzz);
        assertEquals(32, op.maxOffset);
        assertEquals(PatchMode.FUZZY, op.mode);
        assertEquals("asdf/", op.patchesPrefix);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testPatchToFile() throws IOException {
        List<String> help = new ArrayList<>();
        PatchOperation op = parse(help, "--patch",
                "--output", "./asdf/o",
                "--reject", "./asdf/r",
                "./asdf/a",
                "./asdf/b"
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.FromPath);
        assertTrue(op.patchesInput instanceof Input.SingleInput.FromPath);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.patchedOutput instanceof Output.SingleOutput.ToPath);
        assertTrue(op.rejectsOutput instanceof Output.SingleOutput.ToPath);
        assertEquals(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, op.minFuzz);
        assertEquals(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, op.maxOffset);
        assertEquals(PatchMode.EXACT, op.mode);
        assertEquals("", op.patchesPrefix);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testPatchToArchives() throws IOException {
        List<String> help = new ArrayList<>();
        PatchOperation op = parse(help, "--patch",
                "--output", "./asdf/o",
                "--archive", "ZIP",
                "--reject", "./asdf/r",
                "--archive-base", "ZIP",
                "--archive-patches", "ZIP",
                "--archive-rejects", "ZIP",
                "./asdf/a",
                "./asdf/b"
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.PathArchiveMultiInput);
        assertTrue(op.patchesInput instanceof Input.SingleInput.PathArchiveMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.patchedOutput instanceof Output.SingleOutput.PathArchiveMultiOutput);
        assertTrue(op.rejectsOutput instanceof Output.SingleOutput.PathArchiveMultiOutput);
        assertEquals(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, op.minFuzz);
        assertEquals(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, op.maxOffset);
        assertEquals(PatchMode.EXACT, op.mode);
        assertEquals("", op.patchesPrefix);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testPatchToArchives3() throws IOException {
        List<String> help = new ArrayList<>();
        PatchOperation op = parse(help, "--patch",
                "--archive", "ZIP",
                "--reject", "./asdf/r.zip",
                "./asdf/a.zip",
                "./asdf/b.zip"
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.PathArchiveMultiInput);
        assertTrue(op.patchesInput instanceof Input.SingleInput.PathArchiveMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.patchedOutput instanceof Output.SingleOutput.PipeArchiveMultiOutput);
        assertTrue(op.rejectsOutput instanceof Output.SingleOutput.PathArchiveMultiOutput);
        assertEquals(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, op.minFuzz);
        assertEquals(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, op.maxOffset);
        assertEquals(PatchMode.EXACT, op.mode);
        assertEquals("", op.patchesPrefix);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testPatchToArchives2() throws IOException {
        List<String> help = new ArrayList<>();
        PatchOperation op = parse(help, "--patch",
                "--output", "./asdf/o.zip",
                "--reject", "./asdf/r.zip",
                "./asdf/a.zip",
                "./asdf/b.zip"
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.SingleInput.PathArchiveMultiInput);
        assertTrue(op.patchesInput instanceof Input.SingleInput.PathArchiveMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.patchedOutput instanceof Output.SingleOutput.PathArchiveMultiOutput);
        assertTrue(op.rejectsOutput instanceof Output.SingleOutput.PathArchiveMultiOutput);
        assertEquals(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, op.minFuzz);
        assertEquals(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, op.maxOffset);
        assertEquals(PatchMode.EXACT, op.mode);
        assertEquals("", op.patchesPrefix);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }

    @Test
    public void testPatchToArchives2(@TempDir Path folder) throws IOException {
        List<String> help = new ArrayList<>();
        PatchOperation op = parse(help, "--patch",
                "--output", folder.toAbsolutePath().toString(),
                "--reject", folder.toAbsolutePath().toString(),
                folder.toAbsolutePath().toString(),
                folder.toAbsolutePath().toString()
        );
        assertTrue(help.isEmpty());
        assertNotNull(op);
        assertFalse(op.summary);
        assertTrue(op.baseInput instanceof Input.FolderMultiInput);
        assertTrue(op.patchesInput instanceof Input.FolderMultiInput);
        assertEquals("a/", op.aPrefix);
        assertEquals("b/", op.bPrefix);
        assertTrue(op.patchedOutput instanceof Output.FolderMultiOutput);
        assertTrue(op.rejectsOutput instanceof Output.FolderMultiOutput);
        assertEquals(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE, op.minFuzz);
        assertEquals(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET, op.maxOffset);
        assertEquals(PatchMode.EXACT, op.mode);
        assertEquals("", op.patchesPrefix);
        assertEquals(System.lineSeparator(), op.lineEnding);
        assertEquals(0, op.ignorePrefixes.length);
    }
    // endregion

    private static @Nullable <T extends CliOperation<?>> T parse(List<String> help, String... args) throws IOException {
        return unsafeCast(parseOperation(new PrintStream(new ConsumingOutputStream(help::add), true), new PrintStream(NullOutputStream.INSTANCE, true), args));
    }
}
