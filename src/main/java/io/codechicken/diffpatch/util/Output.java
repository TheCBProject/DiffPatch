package io.codechicken.diffpatch.util;

import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveWriter;
import net.covers1624.quack.io.IOUtils;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Represents either a {@link SingleOutput} or {@link MultiOutput}
 * <p>
 * Created by covers1624 on 30/5/24.
 *
 * @see SingleOutput
 * @see MultiOutput
 */
public abstract class Output {

    /**
     * Check any preconditions about the output prior to any work occurring.
     * <p>
     * Used to check if folder outputs are actually folders, or archives are actually zips, etc.
     *
     * @param kind Descriptive name of the output. I.E patches, outputs, rejects, etc.
     */
    public abstract void validate(String kind) throws IOValidationException;

    /**
     * Used to compare if an input and an output point to the same underlying file.
     *
     * @param input The input.
     * @return If the input points to the same file as the output.
     */
    public boolean isSamePath(Input input) {
        return false;
    }

    /**
     * Represents an output with a single file destination.
     */
    public static abstract class SingleOutput extends Output {

        /**
         * Single file output, for an existing {@link OutputStream} such as stdout.
         *
         * @param out The stream.
         * @return The output.
         */
        public static SingleOutput pipe(OutputStream out) {
            return new ToStream(out);
        }

        /**
         * Single file output, for a {@link Path}.
         *
         * @param path The path.
         * @param opts Any open options for the path.
         * @return The output.
         */
        public static SingleOutput path(Path path, OpenOption... opts) {
            return new ToPath(path, opts);
        }

        /**
         * Open the output for writing.
         *
         * @return The stream.
         */
        public abstract OutputStream open() throws IOException;

        public static class ToStream extends SingleOutput {

            private final OutputStream out;

            public ToStream(OutputStream out) { this.out = out; }

            @Override
            public void validate(String kind) {
                // Always valid.
            }

            @Override
            public OutputStream open() {
                return IOUtils.protectClose(out);
            }
        }

        public static class ToPath extends SingleOutput {

            private final Path path;
            private final OpenOption[] opts;

            public ToPath(Path path, OpenOption... opts) {
                this.path = path;
                this.opts = opts;
            }

            @Override
            public void validate(String kind) throws IOValidationException {
                if (Files.exists(path) && !Files.isRegularFile(path)) {
                    throw new IOValidationException("Output '" + kind + "' already exists and is not a file.");
                }
            }

            @Override
            public OutputStream open() throws IOException {
                return Files.newOutputStream(IOUtils.makeParents(path), opts);
            }
        }
    }

    /**
     * Represents an output capable of receiving multiple files,
     * such as an Archive or a Folder.
     */
    public static abstract class MultiOutput extends Output implements AutoCloseable {

        /**
         * Create a {@link MultiOutput} which writes an archive to a file.
         *
         * @param format The format of the archive.
         * @param path   The destination.
         * @return The output.
         */
        public static MultiOutput archive(ArchiveFormat format, Path path) {
            return new PathArchiveMultiOutput(format, path);
        }

        /**
         * Create a {@link MultiOutput} which writes an archive to the given stream.
         *
         * @param format The format of the archive.
         * @param stream The destination.
         * @return The output.
         */
        public static MultiOutput archive(ArchiveFormat format, OutputStream stream) {
            return new PipeArchiveMultiOutput(format, stream);
        }

        /**
         * Create a {@link MultiOutput} which writes an archive to a folder.
         *
         * @param output The destination folder.
         * @return The output.
         */
        public static MultiOutput folder(Path output) {
            return new FolderMultiOutput(output);
        }

        /**
         * Called to open any internal resources and set up the output for writing.
         *
         * @param clearOutput If the output should be wiped, or written over top of.
         *                    This only effects {@link FolderMultiOutput}. Archives
         *                    are always rewritten.
         */
        public abstract void open(boolean clearOutput) throws IOException;

        /**
         * Called to write a file to the output.
         *
         * @param path The relative path of the output. Will not contain a starting slash.
         * @param data The data.
         */
        public abstract void write(String path, byte[] data) throws IOException;

        @Override
        public abstract void close() throws IOException;
    }

    public static abstract class ArchiveMultiOutput extends MultiOutput {

        private final ArchiveFormat format;
        private @Nullable ArchiveWriter aw;

        public ArchiveMultiOutput(ArchiveFormat format) {
            this.format = format;
        }

        protected abstract OutputStream openStream() throws IOException;

        @Override
        public void open(boolean clearOutput) throws IOException {
            if (aw != null) throw new IllegalStateException("Already opened.");

            aw = format.createWriter(openStream());
        }

        @Override
        public void write(String path, byte[] data) throws IOException {
            if (aw == null) throw new IllegalStateException("Not opened.");

            aw.writeEntry(path, data);
        }

        @Override
        public void close() throws IOException {
            if (aw == null) throw new IllegalStateException("Not opened.");

            aw.close();
        }
    }

    public static class PathArchiveMultiOutput extends ArchiveMultiOutput {

        private final Path path;

        public PathArchiveMultiOutput(ArchiveFormat format, Path path) {
            super(format);
            this.path = path;
        }

        @Override
        public void validate(String kind) throws IOValidationException {
            if (Files.exists(path) && !Files.isRegularFile(path)) {
                throw new IOValidationException("Output '" + kind + "' already exists and is not a file.");
            }
        }

        @Override
        protected OutputStream openStream() throws IOException {
            return Files.newOutputStream(path);
        }
    }

    public static class PipeArchiveMultiOutput extends ArchiveMultiOutput {

        private final OutputStream os;

        public PipeArchiveMultiOutput(ArchiveFormat format, OutputStream os) {
            super(format);
            this.os = os;
        }

        @Override
        public void validate(String kind) {
            // Always valid.
        }

        @Override
        protected OutputStream openStream() {
            return IOUtils.protectClose(os);
        }
    }

    public static class FolderMultiOutput extends MultiOutput {

        public final Path folder;

        public FolderMultiOutput(Path folder) {
            this.folder = folder;
        }

        @Override
        public void validate(String kind) throws IOValidationException {
            if (Files.exists(folder) && !Files.isDirectory(folder)) {
                throw new IOValidationException("Output '" + kind + "' already exists and is not a file.");
            }
        }

        @Override
        public void open(boolean clearOutput) throws IOException {
            if (clearOutput && Files.exists(folder)) {
                try (Stream<Path> stream = Files.walk(folder)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(SneakyUtils.sneak(Files::delete));
                }
            }
        }

        @Override
        public void write(String path, byte[] data) throws IOException {
            Files.write(IOUtils.makeParents(folder.resolve(path)), data);
        }

        @Override
        public void close() { }

        @Override
        public boolean isSamePath(Input input) {
            if (!(input instanceof Input.FolderMultiInput)) return false;

            return folder.equals(((Input.FolderMultiInput) input).folder);
        }
    }
}
