package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.test.TestBase;
import io.codechicken.diffpatch.util.ArchiveBuilder;
import io.codechicken.diffpatch.util.Input;
import io.codechicken.diffpatch.util.LogLevel;
import io.codechicken.diffpatch.util.Output;
import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static io.codechicken.diffpatch.cli.BakePatchesOperation.*;
import static io.codechicken.diffpatch.util.archiver.ArchiveFormat.ZIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by covers1624 on 25/6/24.
 */
public class BakePatchesTest extends TestBase {

    @Test
    public void testBakeSingle() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<BakeSummary> result = BakePatchesOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .patchesInput(Input.SingleInput.pipe(testResourceStream("/patches/ModifiedAAutoHeader.txt.patch"), "A.txt.patch"))
                .bakedOutput(Output.SingleOutput.pipe(output))
                .build()
                .operate();
        assertEquals(0, result.exit);
        assertEquals(testResourceString("/patches/ModifiedA.txt.patch"), output.toString("UTF-8"));
    }

    @Test
    public void testBakeMulti() throws IOException {
        byte[] patches = new ArchiveBuilder()
                .put("A.txt.patch", testResource("/patches/ModifiedAAutoHeader.txt.patch"))
                .put("B.txt.patch", testResource("/patches/ModifiedBAutoHeader.txt.patch"))
                .toBytes(ZIP);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CliOperation.Result<BakeSummary> result = BakePatchesOperation.builder()
                .logTo(System.out)
                .level(LogLevel.ALL)
                .patchesInput(Input.MultiInput.archive(ZIP, patches))
                .bakedOutput(Output.MultiOutput.archive(ZIP, output))
                .build()
                .operate();
        assertEquals(0, result.exit);

        try (ArchiveReader input = ZIP.createReader(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals(testResourceString("/patches/ModifiedA.txt.patch"), new String(input.getBytes("A.txt.patch"), StandardCharsets.UTF_8));
            assertEquals(testResourceString("/patches/ModifiedB.txt.patch"), new String(input.getBytes("B.txt.patch"), StandardCharsets.UTF_8));
        }
    }

    @Test
    @Deprecated
    public void testBakeLegacy() throws IOException {
        byte[] patches = new ArchiveBuilder()
                .put("A.txt.patch", testResource("/patches/ModifiedAAutoHeader.txt.patch"))
                .put("B.txt.patch", testResource("/patches/ModifiedBAutoHeader.txt.patch"))
                .toBytes(ZIP);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PatchOperation.bakePatches(
                Input.MultiInput.archive(ZIP, patches),
                Output.MultiOutput.archive(ZIP, bos),
                System.lineSeparator()
        );

        try (ArchiveReader input = ZIP.createReader(new ByteArrayInputStream(bos.toByteArray()))) {
            assertEquals(testResourceString("/patches/ModifiedA.txt.patch"), new String(input.getBytes("A.txt.patch"), StandardCharsets.UTF_8));
            assertEquals(testResourceString("/patches/ModifiedB.txt.patch"), new String(input.getBytes("B.txt.patch"), StandardCharsets.UTF_8));
        }
    }
}
