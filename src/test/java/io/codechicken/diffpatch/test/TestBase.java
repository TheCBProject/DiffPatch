package io.codechicken.diffpatch.test;

import io.codechicken.diffpatch.diff.Differ;
import io.codechicken.diffpatch.diff.PatienceDiffer;
import io.codechicken.diffpatch.match.FuzzyMatchOptions;
import io.codechicken.diffpatch.patch.Patcher;
import io.codechicken.diffpatch.util.ConsumingOutputStream;
import io.codechicken.diffpatch.util.Patch;
import io.codechicken.diffpatch.util.PatchFile;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.codechicken.diffpatch.util.PatchMode;
import net.covers1624.quack.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by covers1624 on 30/5/24.
 */
public abstract class TestBase {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static Map<String, List<String>> generateRandomFiles(Random randy) {
        Map<String, List<String>> files = new LinkedHashMap<>();

        int numFiles = 10 + randy.nextInt(100);
        for (int i = 0; i < numFiles; i++) {
            String fileName = randomFileNameAndPath(randy, 4) + ".txt";
            int numLines = 10 + randy.nextInt(100);
            List<String> lines = new ArrayList<>(numLines);
            for (int j = 0; j < numLines; j++) {
                lines.add(generateRandomHex(randy, 5 + randy.nextInt(100)));
            }
            files.put(fileName, lines);
        }

        return files;
    }

    private static String randomFileNameAndPath(Random randy, int segments) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            if (i != 0) {
                ret.append("/");
            }
            ret.append(generateRandomHex(randy, 1 + randy.nextInt(5)));
        }
        return ret.toString();
    }

    public static String generateRandomHex(Random randy, int len) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            builder.append(HEX[randy.nextInt(HEX.length)]);
        }
        return builder.toString();
    }

    public static PrintStream collectLines(List<String> lines) {
        return new PrintStream(new ConsumingOutputStream(lines::add), true);
    }

    public static String testResourceString(String resource) throws IOException {
        try (InputStream is = TestBase.class.getResourceAsStream(resource)) {
            if (is == null) throw new FileNotFoundException("Did not find test resource: " + resource);

            return new String(IOUtils.toBytes(is), StandardCharsets.UTF_8);
        }
    }

    public static InputStream testResourceStream(String resource) throws IOException {
        return new ByteArrayInputStream(testResource(resource));
    }

    public static byte[] testResource(String resource) throws IOException {
        // We read this to bytes and return a ByteArrayInputStream, so we never need to close this resource.
        // It makes tests more concise, whilst still being resource safe.
        try (InputStream is = TestBase.class.getResourceAsStream(resource)) {
            if (is == null) throw new FileNotFoundException("Did not find test resource: " + resource);

            return IOUtils.toBytes(is);
        }
    }

    public static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    public static Path testResourceDirectory(String directory) {
        try {
            return Paths.get(Objects.requireNonNull(TestBase.class.getResource("/" + directory)).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to convert test resource", e);
        }
    }

    public static Path testFile(String directory, String file) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testResourceDirectory(directory), file)) {
            return single(stream);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("Failed to find single file '" + file + "' under " + testResourceDirectory(directory), e);
        }
    }

    public static void assertDiff(@Nullable Differ differ, @Nullable String testName, @Nullable String patchName, int numContextLines, boolean autoOffset) throws IOException {
        // Get the name of the caller
        // To get the caller member name, get a stack trace and step back 1 + 'offset' times
        // An offset of 1 would mean the caller of whoever called this method
        patchName = patchName != null ? patchName : new Throwable().getStackTrace()[1].getMethodName();
        differ = differ != null ? differ : new PatienceDiffer();
        testName = testName != null ? testName : patchName;
        numContextLines = numContextLines > 0 ? numContextLines : Differ.DEFAULT_CONTEXT;

        List<String> lines1 = Files.readAllLines(testFile("files", testName + "_a.cs"));
        List<String> lines2 = Files.readAllLines(testFile("files", testName + "_b.cs"));
        List<Patch> patches = differ.makePatches(lines1, lines2, numContextLines);
        PatchFile patchFile = new PatchFile();
        patchFile.patches = patches;
        List<String> actual = patchFile.toLines(autoOffset);

        Path patchPath = testFile("patches", patchName + ".patch");
        List<String> expected = Files.exists(patchPath) ? Files.readAllLines(patchPath) : Collections.emptyList();
        // Files.readAllLines already strips out the newlines
        Assertions.assertLinesMatch(expected, actual);
    }
    
    public static <T> T single(Iterable<T> iterable) {
        Iterator<T> it = iterable.iterator();
        T value = it.next();
        if (it.hasNext()) {
            throw new IllegalStateException("More than one element found");
        }
        return value;
    }
    
    private static class PatcherAndResult {
        Patcher patcher;
        List<Patcher.Result> results;
        
        PatcherAndResult(Patcher patcher, List<Patcher.Result> results) {
            this.patcher = patcher;
            this.results = results;
        }
    }
    
    private static PatcherAndResult runPatcher(String testName, String patchName, PatchMode mode, @Nullable FuzzyMatchOptions fuzzyOptions) throws IOException {
        List<String> linesA = Files.readAllLines(testFile("files", testName + "_a.cs"));
        PatchFile patchFile = PatchFile.fromLines(patchName, Files.readAllLines(testFile("patches", patchName + ".patch")), true);

        fuzzyOptions = fuzzyOptions != null ? fuzzyOptions : FuzzyMatchOptions.DEFAULT; 
        Patcher patcher = new Patcher(patchFile, linesA, fuzzyOptions);
        List<Patcher.Result> results = patcher.patch(mode);
        
        int lastOffset = 0;
        for (Patcher.Result r : results) {
            if (!r.success) continue;
            int totalOffset = r.appliedPatch.start2 - r.patch.start1;
            assertEquals(totalOffset - lastOffset, r.offset);
            lastOffset = totalOffset;
        }

        return new PatcherAndResult(patcher, results);
    }
    
    public static Patcher.Result patch(@Nullable PatchMode mode, @Nullable FuzzyMatchOptions fuzzyOptions, @Nullable String testName, @Nullable String patchName) throws IOException {
        mode = mode != null ? mode : PatchMode.EXACT;
        // Get the name of the caller
        // To get the caller member name, get a stack trace and step back 1 + 'offset' times
        // An offset of 1 would mean the caller of whoever called this method
        patchName = patchName != null ? patchName : new Throwable().getStackTrace()[1].getMethodName();
        testName = testName != null ? testName : patchName;
        
        return single(runPatcher(testName, patchName, mode, fuzzyOptions).results);
    }

    public static List<Patcher.Result> assertMultiPatch(@Nullable PatchMode mode, @Nullable FuzzyMatchOptions fuzzyOptions, @Nullable String testName, @Nullable String patchName, @Nullable String outputName) throws IOException {
        mode = mode != null ? mode : PatchMode.EXACT;
        // Get the name of the caller
        // To get the caller member name, get a stack trace and step back 1 + 'offset' times
        // An offset of 1 would mean the caller of whoever called this method
        patchName = patchName != null ? patchName : new Throwable().getStackTrace()[1].getMethodName();
        testName = testName != null ? testName : patchName;
        outputName = outputName != null ? outputName : testName;
        PatcherAndResult actual = runPatcher(testName, patchName, mode, fuzzyOptions);

        List<String> expected = Files.readAllLines(testFile("files", outputName + "_b.cs"));
        Assertions.assertLinesMatch(expected, actual.patcher.lines);

        return actual.results;
    }
    
    public static Patcher.Result assertPatch(@Nullable PatchMode mode, @Nullable FuzzyMatchOptions fuzzyOptions, @Nullable String testName, @Nullable String patchName, @Nullable String outputName) throws IOException {
        // Get the name of the caller
        // To get the caller member name, get a stack trace and step back 1 + 'offset' times
        // An offset of 1 would mean the caller of whoever called this method
        patchName = patchName != null ? patchName : new Throwable().getStackTrace()[1].getMethodName();

        return single(assertMultiPatch(mode, fuzzyOptions, testName, patchName, outputName));
    }
}
