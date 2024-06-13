package io.codechicken.diffpatch.util;

import io.codechicken.diffpatch.test.TestBase;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.OutputValidationException;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveReader;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.io.NullOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by covers1624 on 30/5/24.
 */
public class OutputTests extends TestBase {

    @Test
    public void testSingleOutputPipe() {
        SingleOutput output = SingleOutput.pipe(new FilterOutputStream(NullOutputStream.INSTANCE) {
            @Override
            public void close() {
                fail("Should never be called.");
            }
        });
        assertDoesNotThrow(() -> output.validate("asdf"));
        assertDoesNotThrow(() -> output.open().close());
    }

    @Test
    public void testSingleOutputFilePreconditions(@TempDir Path tempDir) throws IOException {
        assertThrows(OutputValidationException.class, () -> SingleOutput.path(tempDir).validate("asdf"));
        assertDoesNotThrow(() -> SingleOutput.path(tempDir.resolve("test.txt")).validate("asdf"));
        Files.createFile(tempDir.resolve("test.txt"));
        assertDoesNotThrow(() -> SingleOutput.path(tempDir.resolve("test.txt")).validate("asdf"));
    }

    @Test
    public void testMultiOutputFolderPreconditions(@TempDir Path tempDir) throws IOException {
        assertDoesNotThrow(() -> MultiOutput.folder(tempDir).validate("asdf"));
        assertDoesNotThrow(() -> MultiOutput.folder(tempDir.resolve("test.txt")).validate("asdf"));
        Files.createFile(tempDir.resolve("test.txt"));
        assertThrows(OutputValidationException.class, () -> MultiOutput.folder(tempDir.resolve("test.txt")).validate("asdf"));
    }

    @Test
    public void testMultiOutputFolderOutputCleared(@TempDir Path tempDir) throws IOException {
        Map<String, List<String>> randomFiles = generateRandomFiles(new Random());
        writeFiles(MultiOutput.folder(tempDir), true, randomFiles);
        assertEquals(randomFiles, readFiles(tempDir));
    }

    @Test
    public void testMultiOutputFolderOutputNotCleared(@TempDir Path tempDir) throws IOException {
        Map<String, List<String>> randomFiles = generateRandomFiles(new Random());
        writeFiles(tempDir, randomFiles);

        Map<String, List<String>> randomFiles2 = generateRandomFiles(new Random());
        writeFiles(MultiOutput.folder(tempDir), false, randomFiles2);

        Map<String, List<String>> merged = new HashMap<>(randomFiles);
        merged.putAll(randomFiles2);
        assertEquals(merged, readFiles(tempDir));
    }

    @Test
    public void testMultiOutputArchivePath(@TempDir Path tempDir) throws IOException {
        assertThrows(OutputValidationException.class, () -> SingleOutput.path(tempDir).validate("asdf"));
        assertDoesNotThrow(() -> MultiOutput.archive(ArchiveFormat.ZIP, tempDir.resolve("test.zip")).validate("asdf"));
        Files.createFile(tempDir.resolve("test.zip"));
        assertDoesNotThrow(() -> MultiOutput.archive(ArchiveFormat.ZIP, tempDir.resolve("test.zip")).validate("asdf"));

        Map<String, List<String>> randomFiles = generateRandomFiles(new Random());
        writeFiles(MultiOutput.archive(ArchiveFormat.ZIP, tempDir.resolve("test.zip")), true, randomFiles);

        Map<String, List<String>> writtenFiles = new HashMap<>();
        try (ArchiveReader reader = ArchiveFormat.ZIP.createReader(Files.newInputStream(tempDir.resolve("test.zip")))) {
            for (String entry : reader.getEntries()) {
                writtenFiles.put(entry, reader.readLines(entry));
            }
        }
        assertEquals(randomFiles, writtenFiles);
    }

    private static void writeFiles(MultiOutput output, boolean clearOutput, Map<String, List<String>> randomFiles2) throws IOException {
        try (MultiOutput out = output) {
            out.open(clearOutput);
            for (Map.Entry<String, List<String>> entry : randomFiles2.entrySet()) {
                out.write(entry.getKey(), String.join("\n", entry.getValue()).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static Map<String, List<String>> readFiles(Path tempDir) throws IOException {
        Map<String, List<String>> writtenFiles = new HashMap<>();
        try (Stream<Path> files = Files.walk(tempDir)) {
            for (Path file : FastStream.of(files)) {
                if (Files.isDirectory(file)) continue;
                writtenFiles.put(tempDir.relativize(file).toString(), Files.readAllLines(file));
            }
        }
        return writtenFiles;
    }

    private static void writeFiles(Path tempDir, Map<String, List<String>> randomFiles) throws IOException {
        for (Map.Entry<String, List<String>> entry : randomFiles.entrySet()) {
            Files.write(IOUtils.makeParents(tempDir.resolve(entry.getKey())), String.join("\n", entry.getValue()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
