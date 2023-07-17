package codechicken.diffpatch.util.archiver;

import codechicken.diffpatch.util.Utils;
import net.covers1624.quack.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Created by covers1624 on 19/7/20.
 */
public interface ArchiveReader extends Closeable {

    Set<String> getEntries();

    byte[] getBytes(String entry);

    default List<String> readLines(String entry) throws IOException {
        return IOUtils.readAll(getBytes(entry));
    }
}
