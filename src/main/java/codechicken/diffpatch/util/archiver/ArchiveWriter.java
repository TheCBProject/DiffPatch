package codechicken.diffpatch.util.archiver;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created by covers1624 on 19/7/20.
 */
public interface ArchiveWriter extends Closeable {

    void writeEntry(String name, byte[] bytes) throws IOException;
}
