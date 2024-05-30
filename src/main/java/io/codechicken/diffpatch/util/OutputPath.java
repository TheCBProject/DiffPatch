package io.codechicken.diffpatch.util;

import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import net.covers1624.quack.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * Created by covers1624 on 25/8/20.
 */
public abstract class OutputPath {

    private final PathType type;

    public OutputPath(PathType type) {
        this.type = type;
    }

    /**
     * They type of Output this is.
     *
     * @return The type.
     */
    public PathType getType() {
        return type;
    }

    /**
     * Gets if this output should be treated as a singular file,
     * opposed to a folder.
     * <p>
     * Always true for PipePath outputs.
     *
     * @return If this output represents a singular file.
     */
    public abstract boolean isFile();

    /**
     * Gets if this output exists or not.
     *
     * @return If this output exists.
     */
    public abstract boolean exists();

    /**
     * Gets the underlying path representing this output.
     * Unsupported for {@link PathType#PIPE}
     *
     * @return The Path.
     */
    public abstract Path toPath();

    /**
     * Creates a stream representing this output.
     *
     * @return The stream.
     */
    public abstract OutputStream open() throws IOException;

    /**
     * Gets the name for this output.
     * Unsupported for {@link PathType#PIPE}
     *
     * @return The name.
     */
    public abstract String getName();

    public abstract @Nullable ArchiveFormat getFormat();

    public static class FilePath extends OutputPath {

        private final Path path;
        private final @Nullable ArchiveFormat format;
        private final OpenOption[] opts;

        public FilePath(Path path, @Nullable ArchiveFormat format, OpenOption... opts) {
            super(PathType.PATH);
            this.path = path.toAbsolutePath();
            this.format = format;
            this.opts = opts;
        }

        @Override
        public boolean isFile() {
            return Files.isRegularFile(path);
        }

        @Override
        public boolean exists() {
            return Files.exists(path);
        }

        @Override
        public Path toPath() {
            return path;
        }

        @Override
        public OutputStream open() throws IOException {
            if (Files.notExists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            return Files.newOutputStream(path, opts);
        }

        @Override
        public String getName() {
            return path.getFileName().toString();
        }

        @Override
        public @Nullable ArchiveFormat getFormat() {
            return format;
        }
    }

    public static class PipePath extends OutputPath {

        private final OutputStream pipe;
        private final @Nullable ArchiveFormat format;

        public PipePath(OutputStream pipe, @Nullable ArchiveFormat format) {
            super(PathType.PIPE);
            this.pipe = pipe;
            this.format = format;
        }

        @Override
        public OutputStream open() {
            return IOUtils.protectClose(pipe);
        }

        @Override
        public @Nullable ArchiveFormat getFormat() {
            return format;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public Path toPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }
    }

    //@formatter:off
    public static class NullPath extends OutputPath {
        public static final NullPath INSTANCE = new NullPath();
        public NullPath() { super(PathType.NULL); }
        @Override public boolean isFile() { return false; }
        @Override public boolean exists() { return false; }
        @Override public Path toPath() { throw new UnsupportedOperationException(); }
        @Override public OutputStream open() { throw new UnsupportedOperationException(); }
        @Override public String getName() { throw new UnsupportedOperationException(); }
        @Override public ArchiveFormat getFormat() { throw new UnsupportedOperationException(); }
    }
    //@formatter:on

}
