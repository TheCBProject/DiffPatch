package codechicken.diffpatch.test;

import codechicken.diffpatch.util.archiver.ArchiveFormat;
import codechicken.diffpatch.util.archiver.ArchiveReader;
import codechicken.diffpatch.util.archiver.ArchiveWriter;
import joptsimple.internal.Strings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Created by covers1624 on 11/2/21.
 */
public class ArchiveFormatTest {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    @Test
    public void testDetection() {
        //Zip
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.findFormat("some/path/abcd.zip"));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.findFormat("some\\path\\abcd.zip"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.nozip"));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.findFormat("some/path/abcd.jar"));
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.findFormat("some\\path\\abcd.jar"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.nojar"));

        //Tar
        assertEquals(ArchiveFormat.TAR, ArchiveFormat.findFormat("some/path/abcd.tar"));
        assertEquals(ArchiveFormat.TAR, ArchiveFormat.findFormat("some\\path\\abcd.tar"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notar"));

        //Tar XZ
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.findFormat("some/path/abcd.tar.xz"));
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.findFormat("some\\path\\abcd.tar.xz"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notar.xz"));

        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.findFormat("some/path/abcd.txz"));
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.findFormat("some\\path\\abcd.txz"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notxz"));

        //Tar GZIP
        assertEquals(ArchiveFormat.TAR_GZIP, ArchiveFormat.findFormat("some/path/abcd.tar.gz"));
        assertEquals(ArchiveFormat.TAR_GZIP, ArchiveFormat.findFormat("some\\path\\abcd.tar.gz"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notar.gz"));

        assertEquals(ArchiveFormat.TAR_GZIP, ArchiveFormat.findFormat("some/path/abcd.taz"));
        assertEquals(ArchiveFormat.TAR_GZIP, ArchiveFormat.findFormat("some\\path\\abcd.taz"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notaz"));

        assertEquals(ArchiveFormat.TAR_GZIP, ArchiveFormat.findFormat("some/path/abcd.tgz"));
        assertEquals(ArchiveFormat.TAR_GZIP, ArchiveFormat.findFormat("some\\path\\abcd.tgz"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notgz"));

        //Tar BZIP2
        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some/path/abcd.tar.bz2"));
        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some\\path\\abcd.tar.bz2"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notar.bz2"));

        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some/path/abcd.tb2"));
        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some\\path\\abcd.tb2"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notb2"));

        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some/path/abcd.tbz"));
        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some\\path\\abcd.tbz"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notbz"));

        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some/path/abcd.tbz2"));
        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some\\path\\abcd.tbz2"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notbz2"));

        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some/path/abcd.tz2"));
        assertEquals(ArchiveFormat.TAR_BZIP2, ArchiveFormat.findFormat("some\\path\\abcd.tz2"));
        assertNull(ArchiveFormat.findFormat("some/path/abcd.notz2"));
    }

    @Test
    public void testZipReadWrite() throws Throwable {
        doReadWrite(ArchiveFormat.ZIP);
        doReadWrite(ArchiveFormat.TAR);
        doReadWrite(ArchiveFormat.TAR_XZ);
        doReadWrite(ArchiveFormat.TAR_GZIP);
        doReadWrite(ArchiveFormat.TAR_BZIP2);
    }

    public static void doReadWrite(ArchiveFormat format) throws IOException {
        Random randy = new Random();
        Map<String, List<String>> origFiles = generateRandomFiles(randy);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ArchiveWriter writer = format.createWriter(bos)) {
            for (Map.Entry<String, List<String>> entry : origFiles.entrySet()) {
                String str = Strings.join(entry.getValue(), "\n");
                writer.writeEntry(entry.getKey(), str.getBytes(StandardCharsets.UTF_8));
            }
        }
        try (ArchiveReader reader = format.createReader(new ByteArrayInputStream(bos.toByteArray()))) {
            Set<String> archiveKeys = reader.getEntries();
            assertEquals(origFiles.size(), archiveKeys.size());
            assertEquals(origFiles.keySet(), archiveKeys);

            for (Map.Entry<String, List<String>> entry : origFiles.entrySet()) {
                List<String> expected = entry.getValue();
                List<String> archive = reader.readLines(entry.getKey());
                assertEquals(expected.size(), archive.size());
                assertEquals(expected, archive);
            }
        }
    }

    public static Map<String, List<String>> generateRandomFiles(Random randy) {
        Map<String, List<String>> files = new HashMap<>();

        int numFiles = 10 + randy.nextInt(100);
        for (int i = 0; i < numFiles; i++) {
            String fileName = generateRandomHex(randy, 8) + ".txt";
            int numLines = 10 + randy.nextInt(100);
            List<String> lines = new ArrayList<>(numLines);
            for (int j = 0; j < numLines; j++) {
                lines.add(generateRandomHex(randy, 5 + randy.nextInt(100)));
            }
            files.put(fileName, lines);
        }

        return files;
    }

    public static String generateRandomHex(Random randy, int len) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            builder.append(HEX[randy.nextInt(HEX.length)]);
        }
        return builder.toString();
    }

}
