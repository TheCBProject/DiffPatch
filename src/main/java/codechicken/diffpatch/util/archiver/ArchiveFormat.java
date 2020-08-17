package codechicken.diffpatch.util.archiver;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by covers1624 on 19/7/20.
 */
public enum ArchiveFormat {
    //@formatter:off
    ZIP("ZIP", ".zip", ".jar") {
        @Override public ArchiveReader createReader(InputStream is, String prefix) { return new ArchiveInputStreamReader(new ZipArchiveInputStream(is), prefix); }
        @Override public ArchiveWriter createWriter(OutputStream os)  { return new ZipArchiveOutputStreamWriter(new ZipArchiveOutputStream(os)); }
    },
    TAR("TAR", ".tar") {
        @Override public ArchiveReader createReader(InputStream is, String prefix)  { return ArchiveFormat.makeTarReader(is, prefix); }
        @Override public ArchiveWriter createWriter(OutputStream os)  { return ArchiveFormat.makeTarWriter(os); }
    },
    TAR_XZ("TAR_XZ", ".tar.xz", ".txz") {
        @Override public ArchiveReader createReader(InputStream is, String prefix) throws IOException { return ArchiveFormat.makeTarReader(new XZCompressorInputStream(is), prefix); }
        @Override public ArchiveWriter createWriter(OutputStream os) throws IOException { return ArchiveFormat.makeTarWriter(new XZCompressorOutputStream(os)); }
    },
    TAR_GZIP("TAR_GZIP", ".tar.gz", ".taz", ".tgz") {
        @Override public ArchiveReader createReader(InputStream is, String prefix) throws IOException { return ArchiveFormat.makeTarReader(new GzipCompressorInputStream(is), prefix); }
        @Override public ArchiveWriter createWriter(OutputStream os) throws IOException { return ArchiveFormat.makeTarWriter(new GzipCompressorOutputStream(os)); }
    },
    TAR_BZIP2("TAR_BZIP2", ".tar.bz2", ".tb2", ".tbz", ".tbz2", ".tz2") {
        @Override public ArchiveReader createReader(InputStream is, String prefix) throws IOException { return ArchiveFormat.makeTarReader(new BZip2CompressorInputStream(is), prefix); }
        @Override public ArchiveWriter createWriter(OutputStream os) throws IOException { return ArchiveFormat.makeTarWriter(new BZip2CompressorOutputStream(os)); }
    };
    //@formatter:on

    private final String name;
    private final Set<String> fileExtensions;

    ArchiveFormat(String name, String... extensions) {
        this.name = name;
        this.fileExtensions = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(extensions)));
    }

    public String getName() {
        return name;
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    /**
     * Tries to find a supported {@link ArchiveFormat} for a given file name.
     * This makes assumptions based on the Extension of the file.
     *
     * @param fName The File name.
     * @return The assumed {@link ArchiveFormat} or null if one cannot be found.
     */
    public static ArchiveFormat findFormat(String fName) {
        for (ArchiveFormat format : values()) {
            for (String ext : format.fileExtensions) {
                if (fName.endsWith(ext)) {
                    return format;
                }
            }
        }
        return null;
    }

    public static ArchiveFormat findFormat(Path fName) {
        return findFormat(fName.toString());
    }

    private static ArchiveReader makeTarReader(InputStream is, String prefix) {
        return new ArchiveInputStreamReader(new TarArchiveInputStream(is), prefix);
    }

    private static ArchiveWriter makeTarWriter(OutputStream os) {
        return new TarArchiveOutputStreamWriter(new TarArchiveOutputStream(os));
    }

    public ArchiveReader createReader(InputStream is) throws IOException {
        return createReader(is, "");
    }

    public abstract ArchiveReader createReader(InputStream is, String prefix) throws IOException;

    public abstract ArchiveWriter createWriter(OutputStream os) throws IOException;

}
