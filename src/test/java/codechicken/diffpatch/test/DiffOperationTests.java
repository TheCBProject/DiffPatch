package codechicken.diffpatch.test;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.DiffOperation;
import codechicken.diffpatch.util.Utils;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import codechicken.diffpatch.util.archiver.ArchiveReader;
import codechicken.diffpatch.util.archiver.ArchiveWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests assume that ArchiveReader and ArchiveWriter behave the same across all formats,
 * and are tested separately, we simply use zip here for convenience.
 * <p>
 * Created by covers1624 on 11/2/21.
 */
public class DiffOperationTests {

    @Test
    public void testFolderToFolder() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        copyResource("/data/src/PatchFile.java", src.resolve("PatchFile.java"));
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .aPath(orig)
                .bPath(src)
                .outputPath(patches)
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(patches.resolve("PatchFile.java.patch")));
    }

    @Test
    public void testFolderToZip() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches.zip");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        copyResource("/data/src/PatchFile.java", src.resolve("PatchFile.java"));
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .aPath(orig)
                .bPath(src)
                .outputPath(patches)
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(patches));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(patches))) {
        }
    }

    @Test
    public void testFolderToPipe() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches.zip");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        copyResource("/data/src/PatchFile.java", src.resolve("PatchFile.java"));
        OutputStream os = Files.newOutputStream(patches);
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .aPath(orig)
                .bPath(src)
                .outputPath(os, ArchiveFormat.ZIP)
                .build()
                .operate();
        os.close();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(patches));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(patches))) {
        }
    }

    @Test
    public void testOrigZipToZip() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig.zip");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches.zip");
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(orig))) {
            copyResource("/data/orig/PatchFile.java", writer, "PatchFile.java");
        }
        copyResource("/data/src/PatchFile.java", src.resolve("PatchFile.java"));
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .aPath(orig)
                .bPath(src)
                .outputPath(patches)
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(patches));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(patches))) {
        }
    }

    @Test
    public void testOrigPipeToZip() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig.zip");
        Path src = tempDir.resolve("src");
        Path patches = tempDir.resolve("patches.zip");
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(orig))) {
            copyResource("/data/orig/PatchFile.java", writer, "PatchFile.java");
        }
        copyResource("/data/src/PatchFile.java", src.resolve("PatchFile.java"));
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .aPath(Files.readAllBytes(orig), ArchiveFormat.ZIP)
                .bPath(src)
                .outputPath(patches)
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(patches));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(patches))) {
        }
    }

    @Test
    public void testSrcZipToZip() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src.zip");
        Path patches = tempDir.resolve("patches.zip");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(src))) {
            copyResource("/data/src/PatchFile.java", writer, "PatchFile.java");
        }
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .aPath(orig)
                .bPath(src)
                .outputPath(patches)
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(patches));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(patches))) {
        }
    }

    @Test
    public void testSrcPipeToZip() throws Throwable {
        Path tempDir = Files.createTempDirectory("dir_test");
        tempDir.toFile().deleteOnExit();
        Path orig = tempDir.resolve("orig");
        Path src = tempDir.resolve("src.zip");
        Path patches = tempDir.resolve("patches.zip");
        copyResource("/data/orig/PatchFile.java", orig.resolve("PatchFile.java"));
        try (ArchiveWriter writer = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(src))) {
            copyResource("/data/src/PatchFile.java", writer, "PatchFile.java");
        }
        CliOperation.Result<DiffOperation.DiffSummary> result = DiffOperation.builder()
                .aPath(orig)
                .bPath(Files.readAllBytes(src), ArchiveFormat.ZIP)
                .outputPath(patches)
                .build()
                .operate();
        assertEquals(1, result.exit);
        assertTrue(Files.exists(patches));
        //Reads entire zip into memory.
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(patches))) {
        }
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
            writer.writeEntry(to, Utils.toBytes(is));
        }
    }
}
