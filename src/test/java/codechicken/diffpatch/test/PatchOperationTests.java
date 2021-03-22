package codechicken.diffpatch.test;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        copyResource("/data/src/PatchFile.java", cmp.resolve("PatchFile.java"));
        copyResource("/data/patches/PatchFile.java.patch", patches.resolve("PatchFile.java.patch"));
        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .basePath(orig)
                .outputPath(src)
                .patchesPath(patches)
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src.resolve("PatchFile.java")));
        List<String> output = Files.readAllLines(src.resolve("PatchFile.java"));
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
                .basePath(orig)
                .outputPath(src)
                .patchesPath(patches)
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
                .basePath(orig)
                .outputPath(os, ArchiveFormat.ZIP)
                .patchesPath(patches)
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
                .basePath(orig)
                .outputPath(src)
                .patchesPath(patches)
                .rejectsPath(rejects)
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
                .basePath(orig)
                .outputPath(src)
                .patchesPath(patches)
                .rejectsPath(rejects)
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
                .basePath(orig)
                .outputPath(src)
                .patchesPath(patches)
                .rejectsPath(os, ArchiveFormat.ZIP)
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
                .basePath(orig)
                .outputPath(src)
                .patchesPath(patches)
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
                .basePath(Files.readAllBytes(orig), ArchiveFormat.ZIP)
                .outputPath(src)
                .patchesPath(patches)
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
                .basePath(orig)
                .outputPath(src)
                .patchesPath(patches)
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
                .basePath(orig)
                .outputPath(src)
                .patchesPath(Files.readAllBytes(patches), ArchiveFormat.ZIP)
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertTrue(Files.exists(src.resolve("PatchFile.java")));
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
