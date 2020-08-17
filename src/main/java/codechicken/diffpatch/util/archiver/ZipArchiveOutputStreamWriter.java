package codechicken.diffpatch.util.archiver;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;

/**
 * Created by covers1624 on 19/7/20.
 */
public class ZipArchiveOutputStreamWriter extends AbstractArchiveOutputStreamWriter {

    public ZipArchiveOutputStreamWriter(ZipArchiveOutputStream os) {
        super(os);
    }

    @Override
    public void writeEntry(String name, byte[] bytes) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        entry.setSize(bytes.length);
        os.putArchiveEntry(entry);
        os.write(bytes);
        os.closeArchiveEntry();
    }
}
