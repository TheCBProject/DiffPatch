package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.test.TestBase;
import io.codechicken.diffpatch.util.ArchiveBuilder;
import io.codechicken.diffpatch.util.Input;
import io.codechicken.diffpatch.util.LogLevel;
import io.codechicken.diffpatch.util.Output;
import io.codechicken.diffpatch.util.archiver.ArchiveReader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.codechicken.diffpatch.util.archiver.ArchiveFormat.ZIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Created by covers1624 on 22/6/24.
 */
public class DiffOperationTests extends TestBase {

    @Test
    public void testDiffSingle() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(Input.SingleInput.pipe(testResourceStream("/files/A.txt"), "a/A.txt"))
                .changedInput(Input.SingleInput.pipe(testResourceStream("/files/B.txt"), "b/A.txt"))
                .patchesOutput(Output.SingleOutput.pipe(output))
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertEquals(testResourceString("/patches/ModifiedA.txt.patch"), output.toString("UTF-8"));
    }

    @Test
    public void testDiffSingleReverse() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(Input.SingleInput.pipe(testResourceStream("/files/B.txt"), "a/A.txt"))
                .changedInput(Input.SingleInput.pipe(testResourceStream("/files/A.txt"), "b/A.txt"))
                .patchesOutput(Output.SingleOutput.pipe(output))
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertEquals(testResourceString("/patches/ModifiedB.txt.patch"), output.toString("UTF-8"));
    }

    @Test
    public void testDiffArchive() throws IOException {
        byte[] a = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .toBytes(ZIP);
        byte[] b = new ArchiveBuilder()
                .put("A.txt", testResource("/files/B.txt"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(Input.MultiInput.archive(ZIP, a))
                .changedInput(Input.MultiInput.archive(ZIP, b))
                .patchesOutput(Output.MultiOutput.archive(ZIP, output))
                .build()
                .operate();
        assertEquals(1, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/patches/ModifiedA.txt.patch"), new String(ar.getBytes("A.txt.patch"), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testCreatePatch() throws IOException {
        byte[] a = new ArchiveBuilder()
                .toBytes(ZIP);
        byte[] b = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(Input.MultiInput.archive(ZIP, a))
                .changedInput(Input.MultiInput.archive(ZIP, b))
                .patchesOutput(Output.MultiOutput.archive(ZIP, output))
                .build()
                .operate();
        assertEquals(1, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/patches/CreateA.txt.patch"), new String(ar.getBytes("A.txt.patch"), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testDeletePatch() throws IOException {
        byte[] a = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .toBytes(ZIP);
        byte[] b = new ArchiveBuilder()
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(Input.MultiInput.archive(ZIP, a))
                .changedInput(Input.MultiInput.archive(ZIP, b))
                .patchesOutput(Output.MultiOutput.archive(ZIP, output))
                .build()
                .operate();
        assertEquals(1, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/patches/DeleteA.txt.patch"), new String(ar.getBytes("A.txt.patch"), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testRemoveTrailingNewlineBroken() {
        assertThrows(AssertionError.class, this::testRemoveTrailingNewline);
    }

    @Test
    @Disabled ("Currently we are unable to detect these and emit these patches.")
    public void testRemoveTrailingNewline() throws IOException {
        byte[] a = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .toBytes(ZIP);
        byte[] b = new ArchiveBuilder()
                .put("A.txt", testResource("/files/ANoNewline.txt"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(Input.MultiInput.archive(ZIP, a))
                .changedInput(Input.MultiInput.archive(ZIP, b))
                .patchesOutput(Output.MultiOutput.archive(ZIP, output))
                .build()
                .operate();
        assertEquals(1, result.exit);

        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/patches/AToANoNewline.txt.patch"), new String(ar.getBytes("A.txt.patch"), StandardCharsets.UTF_8));
        }
    }
}
