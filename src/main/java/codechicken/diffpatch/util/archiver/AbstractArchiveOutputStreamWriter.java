package codechicken.diffpatch.util.archiver;

import org.apache.commons.compress.archivers.ArchiveOutputStream;

import java.io.IOException;

/**
 * Created by covers1624 on 19/7/20.
 */
public abstract class AbstractArchiveOutputStreamWriter implements ArchiveWriter {

    protected final ArchiveOutputStream os;

    public AbstractArchiveOutputStreamWriter(ArchiveOutputStream os) {
        this.os = os;
    }

    @Override
    public void close() throws IOException {
        os.close();
    }
}
