package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.test.TestBase;
import io.codechicken.diffpatch.util.ArchiveBuilder;
import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.Input.SingleInput;
import io.codechicken.diffpatch.util.LogLevel;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import io.codechicken.diffpatch.util.archiver.ArchiveReader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.codechicken.diffpatch.util.archiver.ArchiveFormat.ZIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by covers1624 on 22/6/24.
 */
public class PatchOperationTests extends TestBase {

    @Test
    public void testPatchSingle() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(SingleInput.pipe(testResourceStream("/files/A.txt")))
                .patchesInput(SingleInput.pipe(testResourceStream("/patches/ModifiedA.txt.patch")))
                .rejectsOutput(SingleOutput.pipe(rejects))
                .patchedOutput(SingleOutput.pipe(output))
                .build()
                .operate();

        assertEquals(0, result.exit);
        assertEquals(0, rejects.size());
        assertEquals(testResourceString("/files/B.txt"), output.toString("UTF-8"));
    }

    @Test
    public void testPatchSingleReverse() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(SingleInput.pipe(testResourceStream("/files/B.txt")))
                .patchesInput(SingleInput.pipe(testResourceStream("/patches/ModifiedB.txt.patch")))
                .rejectsOutput(SingleOutput.pipe(rejects))
                .patchedOutput(SingleOutput.pipe(output))
                .build()
                .operate();

        assertEquals(0, result.exit);
        assertEquals(0, rejects.size());
        assertEquals(testResourceString("/files/A.txt"), output.toString("UTF-8"));
    }

    @Test
    public void testPatchArchive() throws IOException {
        byte[] base = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .toBytes(ZIP);
        byte[] patches = new ArchiveBuilder()
                .put("A.txt.patch", testResource("/patches/ModifiedA.txt.patch"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ZIP, base))
                .patchesInput(MultiInput.archive(ZIP, patches))
                .rejectsOutput(MultiOutput.archive(ZIP, rejects))
                .patchedOutput(MultiOutput.archive(ZIP, output))
                .build()
                .operate();

        assertEquals(0, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/files/B.txt"), new String(ar.getBytes("A.txt"), StandardCharsets.UTF_8));
        }
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(rejects.toByteArray()))) {
            assertTrue(ar.getEntries().isEmpty());
        }
    }

    @Test
    public void testPatchReject() throws IOException {
        // We try and patch B with the A -> B patch.
        byte[] base = new ArchiveBuilder()
                .put("A.txt", testResource("/files/B.txt"))
                .toBytes(ZIP);
        byte[] patches = new ArchiveBuilder()
                .put("A.txt.patch", testResource("/patches/ModifiedA.txt.patch"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ZIP, base))
                .patchesInput(MultiInput.archive(ZIP, patches))
                .rejectsOutput(MultiOutput.archive(ZIP, rejects))
                .patchedOutput(MultiOutput.archive(ZIP, output))
                .build()
                .operate();

        assertEquals(1, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            // The 'A' file (which was B as input), should just be 'B' as we rejected the hunk.
            assertEquals(testResourceString("/files/B.txt"), new String(ar.getBytes("A.txt"), StandardCharsets.UTF_8));
        }
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(rejects.toByteArray()))) {
            assertEquals(testResourceString("/rejects/ModifiedA.txt.patch.rej"), new String(ar.getBytes("A.txt.patch.rej"), StandardCharsets.UTF_8));
        }
    }

    @Test
    @Disabled ("Creating diffs and patching new files currently does not respect trailing newlines.")
    public void testCreatePatch() throws IOException {
        byte[] base = new ArchiveBuilder()
                .toBytes(ZIP);
        byte[] patches = new ArchiveBuilder()
                .put("A.txt.patch", testResource("/patches/CreateA.txt.patch"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ZIP, base))
                .patchesInput(MultiInput.archive(ZIP, patches))
                .rejectsOutput(MultiOutput.archive(ZIP, rejects))
                .patchedOutput(MultiOutput.archive(ZIP, output))
                .build()
                .operate();

        assertEquals(0, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/files/A.txt"), new String(ar.getBytes("A.txt"), StandardCharsets.UTF_8));
        }
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(rejects.toByteArray()))) {
            assertTrue(ar.getEntries().isEmpty());
        }
    }

    @Test
    public void testDeletePatch() throws IOException {
        byte[] base = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .toBytes(ZIP);
        byte[] patches = new ArchiveBuilder()
                .put("A.txt.patch", testResource("/patches/DeleteA.txt.patch"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ZIP, base))
                .patchesInput(MultiInput.archive(ZIP, patches))
                .rejectsOutput(MultiOutput.archive(ZIP, rejects))
                .patchedOutput(MultiOutput.archive(ZIP, output))
                .build()
                .operate();

        assertEquals(0, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertTrue(ar.getEntries().isEmpty());
        }
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(rejects.toByteArray()))) {
            assertTrue(ar.getEntries().isEmpty());
        }
    }

    @Test
    public void testDeleteMultiplePatch() throws IOException {
        byte[] base = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .put("B.txt", testResource("/files/B.txt"))
                .toBytes(ZIP);
        byte[] patches = new ArchiveBuilder()
                .put("A.txt.patch", testResource("/patches/DeleteA.txt.patch"))
                .put("B.txt.patch", testResource("/patches/DeleteB.txt.patch"))
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ZIP, base))
                .patchesInput(MultiInput.archive(ZIP, patches))
                .rejectsOutput(MultiOutput.archive(ZIP, rejects))
                .patchedOutput(MultiOutput.archive(ZIP, output))
                .build()
                .operate();

        assertEquals(0, result.exit);
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertTrue(ar.getEntries().isEmpty());
        }
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(rejects.toByteArray()))) {
            assertTrue(ar.getEntries().isEmpty());
        }
    }

    @Test
    public void patchNoFiles() throws IOException {
        byte[] base = new ArchiveBuilder()
                .put("A.txt", testResource("/files/A.txt"))
                .put("B.txt", testResource("/files/B.txt"))
                .put("ANoNewline.txt", testResource("/files/ANoNewline.txt"))
                .toBytes(ZIP);

        byte[] patches = new ArchiveBuilder()
                .toBytes(ZIP);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ZIP, base))
                .patchesInput(MultiInput.archive(ZIP, patches))
                .rejectsOutput(MultiOutput.archive(ZIP, rejects))
                .patchedOutput(MultiOutput.archive(ZIP, output))
                .build()
                .operate();

        assertEquals(0, result.exit);

        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/files/A.txt"), new String(ar.getBytes("A.txt"), StandardCharsets.UTF_8));
            assertEquals(testResourceString("/files/B.txt"), new String(ar.getBytes("B.txt"), StandardCharsets.UTF_8));
            // File without trailing newline should not be modified.
            assertEquals(testResourceString("/files/ANoNewline.txt"), new String(ar.getBytes("ANoNewline.txt"), StandardCharsets.UTF_8));
        }
        try (ArchiveReader ar = ZIP.createReader(new ByteArrayInputStream(rejects.toByteArray()))) {
            assertTrue(ar.getEntries().isEmpty());
        }
    }
}
