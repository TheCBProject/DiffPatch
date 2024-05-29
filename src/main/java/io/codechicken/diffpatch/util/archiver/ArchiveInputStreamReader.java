package io.codechicken.diffpatch.util.archiver;

import io.codechicken.diffpatch.util.Utils;
import net.covers1624.quack.io.IOUtils;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper for an {@link ArchiveInputStream} that indexes and stores
 * each entries content.
 * <p>
 * Created by covers1624 on 19/7/20.
 */
public class ArchiveInputStreamReader implements ArchiveReader {

    private final Map<String, byte[]> archiveIndex = new LinkedHashMap<>();
    private final ArchiveInputStream is;

    public ArchiveInputStreamReader(ArchiveInputStream is, String prefix) {
        this.is = is;
        try {
            ArchiveEntry entry;
            while ((entry = is.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = Utils.stripStart('/', entry.getName());
                if (!prefix.isEmpty() && !entry.getName().startsWith(prefix)) continue;

                archiveIndex.put(Utils.stripStart('/', name.substring(prefix.length())), IOUtils.toBytes(is));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to index archive", e);
        }
    }

    @Override
    public Set<String> getEntries() {
        return Collections.unmodifiableSet(archiveIndex.keySet());
    }

    @Override
    public byte[] getBytes(String entry) {
        return archiveIndex.get(entry);
    }

    @Override
    public void close() throws IOException {
        is.close();
    }
}
