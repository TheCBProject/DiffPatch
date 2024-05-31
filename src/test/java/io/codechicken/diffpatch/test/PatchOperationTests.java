package io.codechicken.diffpatch.test;

import io.codechicken.diffpatch.cli.CliOperation;
import io.codechicken.diffpatch.cli.PatchOperation;
import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.LogLevel;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveReader;
import io.codechicken.diffpatch.util.archiver.ArchiveWriter;
import net.covers1624.quack.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by covers1624 on 11/2/21.
 */
public class PatchOperationTests {

    @Test
    public void testFolderToFolder() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path cmp = tempDir.resolve("cmp");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        copyResource("/data/orig/A.txt", orig.resolve("A.txt"));
        copyResource("/data/src/PatchFile.java", cmp.resolve("PatchFile.java"));
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .ignorePrefix("A")
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src.resolve("PatchFile.java")));
        assertFalse(Files.exists(src.resolve("A.txt")), "A is ignored, A.txt should not have been copied");
        List<String> output = Files.readAllLines(src.resolve("PatchFile.java"));
        List<String> original = Files.readAllLines(cmp.resolve("PatchFile.java"));
        assertEquals(output, original);
    }

    @Test
    public void testFolderInplace() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path cmp = tempDir.resolve("cmp");
        Path patches = tempDir.resolve("patches");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        copyResource("/data/orig/A.txt", orig.resolve("A.txt"));
        copyResource("/data/src/PatchFile.java", cmp.resolve("PatchFile.java"));
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(orig))
                .ignorePrefix("A")
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(orig.resolve("PatchFile.java")));
        assertTrue(Files.exists(orig.resolve("A.txt")), "A is ignored, A.txt should not have been deleted");
        List<String> output = Files.readAllLines(orig.resolve("PatchFile.java"));
        List<String> original = Files.readAllLines(cmp.resolve("PatchFile.java"));
        assertEquals(output, original);
    }

    @Test
    public void testFolderToZip() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src.zip");
        Path patches = tempDir.resolve("patches");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.archive(ArchiveFormat.ZIP, src))
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(src))) {
        }
    }

    @Test
    public void testFolderToPipe() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src.zip");
        Path patches = tempDir.resolve("patches");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        OutputStream os = Files.newOutputStream(src);
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.archive(ArchiveFormat.ZIP, os))
                .build()
                .operate();
        os.close();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(src))) {
        }
    }

    @Test
    public void testFolderToFolderRejectsFolder() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        Path rejects = tempDir.resolve("rejects");
        copyResource("/data/src/PatchFile.java", orig.resolve("PatchFile.java"));//Use src to simulate rejects
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .rejectsOutput(MultiOutput.folder(rejects))
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(rejects.resolve("PatchFile.java.patch.rej")));
    }

    @Test
    public void testFolderToFolderRejectsZip() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        Path rejects = tempDir.resolve("rejects.zip");
        copyResource("/data/src/PatchFile.java", orig.resolve("PatchFile.java"));//Use src to simulate rejects
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .rejectsOutput(MultiOutput.archive(ArchiveFormat.ZIP, rejects))
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(rejects));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(rejects))) {
        }
    }

    @Test
    public void testFolderToFolderRejectsPipe() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        Path rejects = tempDir.resolve("rejects.zip");
        copyResource("/data/src/PatchFile.java", orig.resolve("PatchFile.java"));//Use src to simulate rejects
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        OutputStream os = Files.newOutputStream(rejects);
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .rejectsOutput(MultiOutput.archive(ArchiveFormat.ZIP, rejects))
                .build()
                .operate();
        os.close();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(rejects));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(rejects))) {
        }
    }

    @Test
    public void testOrigZipToFolder() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig.zip");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(orig))) {
            copyResource("/data/orig/PatchFile.java", writer, "PatchFile.java");
        }
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ArchiveFormat.ZIP, orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src.resolve("PatchFile.java")));
    }

    @Test
    public void testOrigPipeToFolder() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig.zip");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(orig))) {
            copyResource("/data/orig/PatchFile.java", writer, "PatchFile.java");
        }
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.archive(ArchiveFormat.ZIP, new ByteArrayInputStream(Files.readAllBytes(orig))))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src.resolve("PatchFile.java")));
    }

    @Test
    public void testPatchesZipToFolder() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches.zip");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(patches))) {
            copyResource("/data/patches/PatchFile.java.patch", writer, "PatchFile.java.patch");
        }
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.archive(ArchiveFormat.ZIP, patches))
                .patchedOutput(MultiOutput.folder(src))
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src.resolve("PatchFile.java")));
    }

    @Test
    public void testPatchesPipeToFolder() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches.zip");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(patches))) {
            copyResource("/data/patches/PatchFile.java.patch", writer, "PatchFile.java.patch");
        }
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.archive(ArchiveFormat.ZIP, new ByteArrayInputStream(Files.readAllBytes(patches))))
                .patchedOutput(MultiOutput.folder(src))
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src.resolve("PatchFile.java")));
    }

    @Test
    public void testFolderToFolderDeletion() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        copyResource("/data/orig/A.txt", orig.resolve("A.txt"));
        copyResource("/data/orig/B.txt", orig.resolve("B.txt"));
        copyResource("/data/patches/DeleteA.txt.patch", patches.resolve("A.txt.patch"));
        copyResource("/data/patches/DeleteB.txt.patch", patches.resolve("B.txt.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .summary(true)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .build()
                .operate();

        assertAll(
                () -> assertEquals(0, result.exit),
                () -> assertTrue(Files.notExists(src.resolve("A.txt"))),
                () -> assertTrue(Files.notExists(src.resolve("B.txt")))
        );
    }

    @Test
    public void testFolderToFolderCreation() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Files.createDirectories(orig);
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        copyResource("/data/patches/CreateA.txt.patch", patches.resolve("A.txt.patch"));
        copyResource("/data/patches/CreateB.txt.patch", patches.resolve("B.txt.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .summary(true)
                .baseInput(MultiInput.folder(orig))
                .patchesInput(MultiInput.folder(patches))
                .patchedOutput(MultiOutput.folder(src))
                .build()
                .operate();

        assertAll(
                () -> assertEquals(0, result.exit),
                () -> assertTrue(Files.exists(src.resolve("A.txt"))),
                () -> assertTrue(Files.exists(src.resolve("B.txt")))
        );
    }

    private static void copyResource(String resource, Path to) throws IOException {
        to = to.toAbsolutePath();
        Files.createDirectories(to.getParent());
        try (InputStream is = DiffOperationTests.class.getResourceAsStream(resource)) {
            Files.copy(is, to);
        }
    }

    private static void copyResource(String resource, ArchiveWriter writer, String to) throws IOException {
        try (InputStream is = DiffOperationTests.class.getResourceAsStream(resource)) {
            writer.writeEntry(to, IOUtils.toBytes(is));
        }
    }
}
