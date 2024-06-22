package io.codechicken.diffpatch.util;

import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveWriter;

import javax.annotation.WillClose;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by covers1624 on 22/6/24.
 */
public class ArchiveBuilder {

    private final Map<String, byte[]> entries = new LinkedHashMap<>();

    public Set<String> getEntries() {
        return entries.keySet();
    }

    public byte[] getBytes(String entry) {
        return entries.get(entry);
    }

    public ArchiveBuilder put(String name, byte[] data) {
        entries.put(name, data);
        return this;
    }

    public byte[] toBytes(ArchiveFormat format) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        write(format, bos);
        return bos.toByteArray();
    }

    public void write(ArchiveFormat format, @WillClose OutputStream os) throws IOException {
        try (ArchiveWriter writer = format.createWriter(os)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                writer.writeEntry(entry.getKey(), entry.getValue());
            }
        }
    }
}
