package io.codechicken.diffpatch.util;

import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveReader;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents either a {@link SingleInput} or {@link MultiInput}.
 * <p>
 * Created by covers1624 on 30/5/24.
 *
 * @see SingleInput
 * @see MultiInput
 */
public abstract class Input {

    /**
     * Check any preconditions about the input prior to any work occurring.
     * <p>
     * Used to check if inputs exist, folders are actually folders, etc.
     *
     * @param kind Descriptive name of the input. I.E patches, original, changed, etc.
     */
    public abstract void validate(String kind) throws IOValidationException;

    /**
     * Represents an input with a single file source.
     */
    public static abstract class SingleInput extends Input {

        /**
         * Single file input for an existing {@link InputStream}, such as stdin.
         * <p>
         * This stream will have the name of 'pipe', diffing against this
         * will produce this name in the +/- header lines.
         *
         * @param is The stream.
         * @return The input.
         */
        public static SingleInput pipe(InputStream is) {
            return pipe(is, "pipe");
        }

        /**
         * Single file input for an existing {@link InputStream}, such as stdin.
         *
         * @param is   The stream.
         * @param name The name of the stream. Used in +/- header lines of diffs.
         * @return The input.
         */
        public static SingleInput pipe(InputStream is, String name) {
            return new SingleInput() {
                @Override
                public void validate(String kind) {
                    // Always valid.
                }

                @Override
                public InputStream open() throws IOException {
                    return IOUtils.protectClose(is);
                }

                @Override
                public String name() {
                    return name;
                }
            };
        }

        /**
         * A single file input. for a {@link Path}.
         *
         * @param path The path.
         * @param opts Any open options for the path.
         * @return The input.
         */
        public static SingleInput path(Path path, OpenOption... opts) {
            return new SingleInput() {
                @Override
                public void validate(String kind) throws IOValidationException {
                    if (Files.notExists(path)) throw new IOValidationException("Input '" + kind + "' does not exist.");
                    if (!Files.isRegularFile(path)) throw new IOValidationException("Input '" + kind + "' is not a file.");
                }

                @Override
                public InputStream open() throws IOException {
                    return Files.newInputStream(path, opts);
                }

                @Override
                public String name() {
                    return path.toString();
                }
            };
        }

        public abstract InputStream open() throws IOException;

        public List<String> readLines() throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(open(), StandardCharsets.UTF_8))) {
                return FastStream.of(reader.lines()).toList();
            }
        }

        public abstract String name();
    }

    /**
     * Represents an input capable or providing multiple files,
     * such as an Archive or a Folder.
     */
    public static abstract class MultiInput extends Input implements AutoCloseable {

        /**
         * Create a {@link MultiInput} which reads from an archive file.
         *
         * @param format The format of the archive.
         * @param path   The path.
         * @return The input.
         */
        public static MultiInput archive(ArchiveFormat format, Path path) {
            return new PathArchiveMultiInput(format, path);
        }

        /**
         * Create a {@link MultiInput} which reads from an existing stream.
         *
         * @param format The format of the archive.
         * @param stream The path.
         * @return The input.
         */
        public static MultiInput archive(ArchiveFormat format, InputStream stream) {
            return new PipeArchiveMultiInput(format, stream);
        }

        /**
         * Create a {@link MultiInput} which reads from a folder.
         *
         * @param folder The folder.
         * @return The input.
         */
        public static MultiInput folder(Path folder) {
            return new FolderMultiInput(folder);
        }

        /**
         * Called to open any internal resources and set up the input for reading.
         *
         * @param prefix A prefix directory to read from.
         */
        public abstract void open(String prefix) throws IOException;

        /**
         * Get the index for this input.
         *
         * @return The index of this input.
         */
        public abstract Set<String> index() throws IOException;

        /**
         * Read the given entry as a List of Strings.
         *
         * @param key The entry to read.
         * @return The List of Strings for this entry.
         */
        public List<String> readLines(String key) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(read(key)), StandardCharsets.UTF_8))) {
                return FastStream.of(reader.lines()).toList();
            }
        }

        /**
         * Read the given entry as an array of bytes.
         *
         * @param key The entry to read.
         * @return The bytes of the entry.
         */
        public byte[] read(String key) throws IOException {
            byte[] data = tryRead(key);
            if (data != null) return data;

            throw new FileNotFoundException("Failed to find " + key + " in MultiInput.");
        }

        /**
         * Try and read the given entry as an array of bytes.
         *
         * @param key The entry to read.
         * @return The bytes of the entry, or {@code null}.
         */
        public abstract byte @Nullable [] tryRead(String key) throws IOException;

        @Override
        public abstract void close() throws IOException;
    }

    public static abstract class ArchiveMultiInput extends MultiInput {

        private final ArchiveFormat format;
        private @Nullable ArchiveReader ar;

        protected ArchiveMultiInput(ArchiveFormat format) {
            this.format = format;
        }

        protected abstract InputStream openStream() throws IOException;

        @Override
        public void open(String prefix) throws IOException {
            if (ar != null) throw new IllegalStateException("Already opened.");

            ar = format.createReader(openStream(), prefix);
        }

        @Override
        public Set<String> index() {
            if (ar == null) throw new IllegalStateException("Not opened.");

            return ar.getEntries();
        }

        @Override
        public byte @Nullable [] tryRead(String key) {
            if (ar == null) throw new IllegalStateException("Not opened.");

            return ar.getBytes(key);
        }

        @Override
        public void close() throws IOException {
            if (ar == null) throw new IllegalStateException("Not opened.");

            ar.close();
        }
    }

    public static class PathArchiveMultiInput extends ArchiveMultiInput {

        private final Path path;

        protected PathArchiveMultiInput(ArchiveFormat format, Path path) {
            super(format);
            this.path = path;
        }

        @Override
        public void validate(String kind) throws IOValidationException {
            if (Files.notExists(path)) {
                throw new IOValidationException("Input '" + kind + "' does not exist.");
            }
        }

        @Override
        protected InputStream openStream() throws IOException {
            return Files.newInputStream(path);
        }
    }

    public static class PipeArchiveMultiInput extends ArchiveMultiInput {

        private final InputStream is;

        protected PipeArchiveMultiInput(ArchiveFormat format, InputStream is) {
            super(format);
            this.is = is;
        }

        @Override
        public void validate(String kind) {
            // Always valid.
        }

        @Override
        protected InputStream openStream() {
            return IOUtils.protectClose(is);
        }
    }

    public static class FolderMultiInput extends MultiInput {

        public final Path folder;
        private @Nullable Map<String, Path> index;

        public FolderMultiInput(Path folder) {
            this.folder = folder;
        }

        @Override
        public void validate(String kind) throws IOValidationException {
            if (Files.notExists(folder)) throw new IOValidationException("Input '" + kind + "' does not exist.");
            if (!Files.isDirectory(folder)) throw new IOValidationException("Expected input '" + kind + "' to be a directory.");
        }

        @Override
        public void open(String prefix) throws IOException {
            if (index != null) throw new IllegalStateException("Already opened.");

            Path toIndex;
            if (!prefix.isEmpty()) {
                toIndex = folder.resolve(prefix);
            } else {
                toIndex = folder;
            }
            try (Stream<Path> stream = Files.walk(toIndex)) {
                index = stream.filter(Files::isRegularFile)
                        .collect(Collectors.toMap(e -> Utils.stripStart('/', toIndex.relativize(e).toString().replace("\\", "/")), Function.identity()));
            }
        }

        @Override
        public Set<String> index() throws IOException {
            if (index == null) throw new IllegalStateException("Not opened.");

            return Collections.unmodifiableSet(index.keySet());
        }

        @Override
        public byte @Nullable [] tryRead(String key) throws IOException {
            if (index == null) throw new IllegalStateException("Not opened.");

            Path path = index.get(key);
            if (path == null) return null;

            return Files.readAllBytes(path);
        }

        @Override
        public void close() {
        }
    }
}
