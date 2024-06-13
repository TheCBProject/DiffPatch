package io.codechicken.diffpatch.util;

import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.Input.SingleInput;
import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveWriter;
import net.covers1624.quack.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static io.codechicken.diffpatch.test.TestBase.generateRandomFiles;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by covers1624 on 31/5/24.
 */
public class InputTests {

    @Test
    public void testSingleInputPipe() {
        SingleInput input = SingleInput.pipe(new FilterInputStream(null) {
            @Override
            public void close() {
                fail("Should never be called.");
            }
        });

        assertDoesNotThrow(() -> input.validate("asdf"));
        assertDoesNotThrow(() -> input.open().close());
    }

    @Test
    public void testSingleInputFilePreconditions(@TempDir Path tempDir) throws IOException {
        assertThrows(IOValidationException.class, () -> SingleInput.path(tempDir).validate("asdf"));
        assertThrows(IOValidationException.class, () -> SingleInput.path(tempDir.resolve("test.txt")).validate("asdf"));
        Files.createFile(tempDir.resolve("test.txt"));
        assertDoesNotThrow(() -> SingleInput.path(tempDir.resolve("test.txt")).validate("asdf"));
    }

    @Test
    public void testMultiInputFolderPreconditions(@TempDir Path tempDir) throws IOException {
        assertDoesNotThrow(() -> MultiInput.folder(tempDir).validate("asdf"));
        assertThrows(IOValidationException.class, () -> MultiInput.folder(tempDir.resolve("test/")).validate("asdf"));
        assertThrows(IOValidationException.class, () -> MultiInput.folder(tempDir.resolve("test.txt")).validate("asdf"));
        Files.createFile(tempDir.resolve("test.txt"));
        assertThrows(IOValidationException.class, () -> MultiInput.folder(tempDir.resolve("test.txt")).validate("asdf"));
    }

    @Test
    public void testMultiInputFolder(@TempDir Path tempDir) throws IOException {
        Map<String, List<String>> randomFiles = generateRandomFiles(new Random());
        writeFiles(tempDir, randomFiles);
        try (MultiInput input = MultiInput.folder(tempDir)) {
            input.open("");
            assertEquals(randomFiles.keySet(), input.index());
            assertEquals(randomFiles, readFiles(input));
        }
    }

    @Test
    public void testMultiInputFolderWithPrefix(@TempDir Path tempDir) throws IOException {
        Map<String, List<String>> randomFiles = generateRandomFiles(new Random());

        writeFiles(tempDir.resolve("nested/"), randomFiles);
        try (MultiInput input = MultiInput.folder(tempDir)) {
            input.open("nested/");
            assertEquals(randomFiles.keySet(), input.index());
            assertEquals(randomFiles, readFiles(input));
        }
    }

    @Test
    public void testMultiInputArchive(@TempDir Path tempDir) throws IOException {
        Map<String, List<String>> randomFiles = generateRandomFiles(new Random());

        writeZip(tempDir.resolve("test.zip"), randomFiles);
        Map<String, List<String>> readFiles = new HashMap<>();
        try (MultiInput input = MultiInput.archive(ArchiveFormat.ZIP, tempDir.resolve("test.zip"))) {
            input.open("");
            for (String index : input.index()) {
                readFiles.put(index, input.readLines(index));
            }
        }
        assertEquals(randomFiles, readFiles);
    }

    @Test
    public void testMultiInputArchivePrefix(@TempDir Path tempDir) throws IOException {
        Map<String, List<String>> randomFiles = generateRandomFiles(new Random());
        Map<String, List<String>> randomFiles2 = generateRandomFiles(new Random());

        Map<String, List<String>> written = new HashMap<>(randomFiles);
        randomFiles2.forEach((k, v) -> written.put("nested/" + k, v));
        writeZip(tempDir.resolve("test.zip"), written);
        Map<String, List<String>> readFiles = new HashMap<>();
        try (MultiInput input = MultiInput.archive(ArchiveFormat.ZIP, tempDir.resolve("test.zip"))) {
            input.open("nested/");
            for (String index : input.index()) {
                readFiles.put(index, input.readLines(index));
            }
        }
        assertEquals(randomFiles2, readFiles);
    }

    private static void writeZip(Path zip, Map<String, List<String>> files) throws IOException {
        try (ArchiveWriter aw = ArchiveFormat.ZIP.createWriter(Files.newOutputStream(IOUtils.makeParents(zip)))) {
            for (Map.Entry<String, List<String>> entry : files.entrySet()) {
                aw.writeEntry(entry.getKey(), String.join("\n", entry.getValue()).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void writeFiles(Path tempDir, Map<String, List<String>> randomFiles) throws IOException {
        for (Map.Entry<String, List<String>> entry : randomFiles.entrySet()) {
            Files.write(IOUtils.makeParents(tempDir.resolve(entry.getKey())), String.join("\n", entry.getValue()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, List<String>> readFiles(MultiInput input) throws IOException {
        Map<String, List<String>> files = new HashMap<>();
        for (String index : input.index()) {
            files.put(index, input.readLines(index));
        }
        return files;
    }
}
