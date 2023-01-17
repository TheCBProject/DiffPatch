package codechicken.diffpatch.util.archiver;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.IOException;

/**
 * Created by covers1624 on 19/7/20.
 */
public class TarArchiveOutputStreamWriter extends AbstractArchiveOutputStreamWriter {

    public TarArchiveOutputStreamWriter(TarArchiveOutputStream os) {
        super(os);
        os.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
    }

    @Override
    public void writeEntry(String name, byte[] bytes) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        os.putArchiveEntry(entry);
        os.write(bytes);
        os.closeArchiveEntry();
    }
}
