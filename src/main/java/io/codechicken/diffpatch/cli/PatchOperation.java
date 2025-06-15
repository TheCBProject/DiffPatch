package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.match.FuzzyLineMatcher;
import io.codechicken.diffpatch.patch.Patcher;
import io.codechicken.diffpatch.util.*;
import io.codechicken.diffpatch.util.FileCollector.CollectedEntry;
import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.Input.SingleInput;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.io.NullOutputStream;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.codechicken.diffpatch.util.LogLevel.*;
import static io.codechicken.diffpatch.util.Utils.filterPrefixed;
import static org.apache.commons.lang3.StringUtils.removeStart;

/**
 * Created by covers1624 on 11/8/20.
 */
public class PatchOperation extends CliOperation<PatchOperation.PatchesSummary> {

    final boolean summary;
    final Input baseInput;
    final Input patchesInput;
    final String aPrefix;
    final String bPrefix;
    final Output patchedOutput;
    final @Nullable Output rejectsOutput;
    final float minFuzz;
    final int maxOffset;
    final PatchMode mode;
    final String patchesPrefix;
    final String lineEnding;
    final String[] ignorePrefixes;

    private PatchOperation(PrintStream logger, LogLevel level, Consumer<PrintStream> helpCallback, boolean summary, Input baseInput, Input patchesInput, String aPrefix, String bPrefix, Output patchedOutput, @Nullable Output rejectsOutput, float minFuzz, int maxOffset, PatchMode mode, String patchesPrefix, String lineEnding, String[] ignorePrefixes) {
        super(logger, level, helpCallback);
        this.summary = summary;
        this.baseInput = baseInput;
        this.patchesInput = patchesInput;
        this.aPrefix = aPrefix;
        this.bPrefix = bPrefix;
        this.patchedOutput = patchedOutput;
        this.rejectsOutput = rejectsOutput;
        this.minFuzz = minFuzz;
        this.maxOffset = maxOffset;
        this.mode = mode;
        this.patchesPrefix = patchesPrefix;
        this.lineEnding = lineEnding;
        this.ignorePrefixes = ignorePrefixes;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Result<PatchesSummary> operate() throws IOException {
        try {
            baseInput.validate("base input");
            baseInput.validate("patches input");
            patchedOutput.validate("patched output");
            if (rejectsOutput != null) {
                rejectsOutput.validate("rejects output");
            }
        } catch (IOValidationException ex) {
            log(ERROR, ex.getMessage());
            printHelp();
            return new Result<>(-1);
        }

        FileCollector outputCollector = new FileCollector();
        FileCollector rejectCollector = new FileCollector();
        PatchesSummary summary = new PatchesSummary();
        boolean patchSuccess;

        //Base path and patch path are both singular files.
        if (baseInput instanceof SingleInput && patchesInput instanceof SingleInput) {
            SingleInput base = (SingleInput) baseInput;
            SingleInput patches = (SingleInput) patchesInput;
            if (!(patchedOutput instanceof SingleOutput)) {
                log(ERROR, "Can't specify patched output directory or archive when patching single file.");
                printHelp();
                return new Result<>(-1);
            }
            SingleOutput output = (SingleOutput) patchedOutput;

            if (rejectsOutput != null && !(rejectsOutput instanceof SingleOutput)) {
                log(ERROR, "Can't specify reject output directory or archive when patching single file.");
                printHelp();
                return new Result<>(-1);
            }
            SingleOutput rejects = (SingleOutput) rejectsOutput;

            PatchFile patchFile = PatchFile.fromLines(patches.name(), patches.readLines(), true);
            boolean success = doPatch(outputCollector, rejectCollector, summary, base.name(), base.readLines(), patchFile, minFuzz, maxOffset, mode);
            CollectedEntry outputEntry = outputCollector.getSingleFile();
            CollectedEntry rejectEntry = rejectCollector.getSingleFile();
            try (OutputStream os = output.open()) {
                os.write(outputEntry.toBytes(lineEnding, false));
                os.flush();
            }

            if (rejectEntry != null && rejects != null) {
                try (OutputStream os = rejects.open()) {
                    os.write(rejectEntry.toBytes(lineEnding, false));
                }
            }
            if (this.summary) {
                summary.print(logger, true);
            }
            return new Result<>(success ? 0 : 1, summary);
        }

        if (!(baseInput instanceof MultiInput)) {
            log(ERROR, "Can't patch between single files and folders/archives.");
            printHelp();
            return new Result<>(-1);
        }

        if (!(patchesInput instanceof MultiInput)) {
            log(ERROR, "Can't patch between folders/archives and single files.");
            printHelp();
            return new Result<>(-1);
        }

        try (MultiInput base = (MultiInput) baseInput;
             MultiInput patches = (MultiInput) patchesInput) {
            base.open("");
            patches.open(patchesPrefix);
            Set<String> baseIndex = filterPrefixed(base.index(), ignorePrefixes);
            Set<String> patchesIndex = patches.index();
            patchSuccess = doPatch(outputCollector, rejectCollector, summary, baseIndex, patchesIndex, base, patches, minFuzz, maxOffset, mode);
        }

        try (MultiOutput output = (MultiOutput) patchedOutput) {
            output.open(!patchedOutput.isSamePath(baseInput));
            for (Map.Entry<String, CollectedEntry> entry : outputCollector.get().entrySet()) {
                output.write(entry.getKey(), entry.getValue().toBytes(lineEnding, false));
            }
        }

        if (rejectsOutput != null) {
            try (MultiOutput output = (MultiOutput) rejectsOutput) {
                output.open(true);
                for (Map.Entry<String, CollectedEntry> entry : rejectCollector.get().entrySet()) {
                    output.write(entry.getKey(), entry.getValue().toBytes(lineEnding, true));
                }
            }
        }
        if (this.summary) {
            summary.print(logger, false);
        }
        return new Result<>(patchSuccess ? 0 : 1, summary);
    }

    private boolean doPatch(FileCollector oCollector, FileCollector rCollector, PatchesSummary summary, Set<String> bEntries, Set<String> pEntries, MultiInput baseInput, MultiInput patchesInput, float minFuzz, int maxOffset, PatchMode mode) throws IOException {
        Map<String, PatchFile> patchFiles = FastStream.of(pEntries)
                .map(e -> {
                    try {
                        return PatchFile.fromLines(e, patchesInput.readLines(e), true);
                    } catch (IOException ex) {
                        throw new RuntimeException("Failed to read patch file.", ex);
                    }
                })
                .toMap(e -> {
                            if (e.patchedPath == null || "/dev/null".equals(e.patchedPath)) {
                                return e.name.substring(0, e.name.lastIndexOf(".patch"));
                            } else if (e.patchedPath.startsWith("b/")) {
                                return e.patchedPath.substring(2);
                            } else if (e.patchedPath.startsWith(bPrefix)) {
                                return removeStart(e.patchedPath.substring(bPrefix.length()), "/");
                            }
                            return e.patchedPath;
                        },
                        Function.identity()
                );

        Set<String> addedFiles = FastStream.of(patchFiles.keySet()).filter(e -> "/dev/null".equals(patchFiles.get(e).basePath)).sorted().toLinkedHashSet();
        Set<String> removedFiles = FastStream.of(patchFiles.keySet()).filter(e -> "/dev/null".equals(patchFiles.get(e).patchedPath)).sorted().toLinkedHashSet();
        List<String> notPatched = FastStream.of(bEntries).filter(e -> !patchFiles.containsKey(e)).sorted().toList();
        List<String> patchedFiles = FastStream.of(bEntries).filterNot(removedFiles::contains).filter(patchFiles::containsKey).sorted().toList();
        List<String> missingFiles = FastStream.of(patchFiles.keySet()).filterNot(addedFiles::contains).filter(e -> !bEntries.contains(e)).sorted().toList();

        boolean result = true;
        for (String file : notPatched) {
            summary.unchangedFiles++;
            oCollector.consume(file, baseInput.read(file));
        }

        for (String file : addedFiles) {
            summary.addedFiles++;
            PatchFile patchFile = patchFiles.get(file);
            log(DEBUG, "Added: " + file);
            oCollector.consume(file, FastStream.of(patchFile.patches).flatMap(Patch::getPatchedLines).toList());
        }

        for (String file : removedFiles) {
            summary.removedFiles++;
            log(DEBUG, "Removed: " + file);
        }

        for (String file : patchedFiles) {
            summary.changedFiles++;
            PatchFile patchFile = patchFiles.get(file);
            List<String> baseLines = baseInput.readLines(file);
            result &= doPatch(oCollector, rCollector, summary, file, baseLines, patchFile, minFuzz, maxOffset, mode);
        }

        for (String file : missingFiles) {
            summary.missingFiles++;
            PatchFile patchFile = patchFiles.get(file);
            List<String> lines = new ArrayList<>(patchFile.toLines(false));
            lines.add(0, "++++ Target missing");
            log(WARN, "Missing patch target for %s", patchFile.name);
            rCollector.consume(patchFile.name, lines);
            result = false;
        }

        return result;
    }

    private boolean doPatch(FileCollector outputCollector, FileCollector rejectCollector, PatchesSummary summary, String baseName, List<String> base, PatchFile patchFile, float minFuzz, int maxOffset, PatchMode mode) {
        Patcher patcher = new Patcher(patchFile, base, minFuzz, maxOffset);
        log(DEBUG, "Patching: " + baseName);
        List<Patcher.Result> results = patcher.patch(mode);
        List<String> rejectLines = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < results.size(); i++) {
            Patcher.Result result = results.get(i);
            if (result.mode != null) {
                switch (result.mode) {
                    case EXACT:
                        summary.exactMatches++;
                        summary.overallQuality += 100;
                        break;
                    case ACCESS:
                        summary.accessMatches++;
                        summary.overallQuality += 100;
                        break;
                    case OFFSET:
                        summary.offsetMatches++;
                        summary.overallQuality += 100;
                        break;
                    case FUZZY:
                        summary.fuzzyMatches++;
                        summary.overallQuality += (result.fuzzyQuality * 100);
                        break;
                }
            } else {
                summary.failedMatches++;
            }

            if (!result.success) {
                if (!first) {
                    rejectLines.add("");
                } else if (!level.shouldLog(DEBUG)) { // Log the patch name as warn, only if its failed, and we haven't logged it already (top of this function.)
                    log(WARN, "Patching: " + baseName);
                }
                log(WARN, " Hunk %d: %s", i, result.summary());
                first = false;
                rejectLines.add("++++ REJECTED HUNK: " + (i + 1));
                rejectLines.add(result.patch.getHeader());
                FastStream.of(result.patch.diffs).map(Diff::toString).forEach(rejectLines::add);
                rejectLines.add("++++ END HUNK");
            } else {
                log(DEBUG, " Hunk %d: %s", i, result.summary());
            }
        }
        List<String> lines = patcher.lines;
        if (!lines.isEmpty()) {
            if (patchFile.noNewLine) {
                if (lines.get(lines.size() - 1).isEmpty()) {
                    lines.remove(lines.size() - 1);
                }
            } else {
                lines.add("");
            }
        }
        outputCollector.consume(baseName, lines);
        if (!rejectLines.isEmpty()) {
            rejectCollector.consume(patchFile.name + ".rej", rejectLines);
            return false;
        }
        return true;
    }

    public static void bakePatches(MultiInput input, MultiOutput output, String lineEnding) throws IOException {
        bakePatches(input, "", output, lineEnding);
    }

    public static void bakePatches(MultiInput input, String prefix, MultiOutput output, String lineEnding) throws IOException {
        try {
            input.validate("bake input");
        } catch (IOValidationException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        try (MultiInput in = input;
             MultiOutput out = output) {
            in.open(prefix);
            out.open(true);
            for (String file : in.index()) {
                PatchFile patchFile = PatchFile.fromLines(file, in.readLines(file), true);
                out.write(file, bakePatch(patchFile, lineEnding).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    public static String bakePatch(PatchFile patchFile, String lineEnding) {
        List<String> lines = patchFile.toLines(false);
        return String.join(lineEnding, lines) + lineEnding;
    }

    public static class PatchesSummary {

        public int unchangedFiles;
        public int addedFiles;
        public int changedFiles;
        public int removedFiles;
        public int missingFiles;
        public int failedMatches;
        public int exactMatches;
        public int accessMatches;
        public int offsetMatches;
        public int fuzzyMatches;

        public double overallQuality;

        public void print(PrintStream logger, boolean slim) {
            logger.println("Patch Summary:");
            if (!slim) {
                logger.println(" Un-changed files: " + unchangedFiles);
                logger.println(" Added files:      " + addedFiles);
                logger.println(" Changed files:    " + changedFiles);
                logger.println(" Removed files:    " + removedFiles);
                logger.println(" Missing files:    " + missingFiles);
            }
            logger.println();
            logger.println(" Failed matches:   " + failedMatches);
            logger.println(" Exact matches:    " + exactMatches);
            logger.println(" Access matches:   " + accessMatches);
            logger.println(" Offset matches:   " + offsetMatches);
            logger.println(" Fuzzy matches:    " + fuzzyMatches);

            logger.printf("Overall Quality   %.2f%%%n", overallQuality / (failedMatches + exactMatches + accessMatches + offsetMatches + fuzzyMatches));
        }
    }

    public static class Builder {

        private static final PrintStream NULL_STREAM = new PrintStream(NullOutputStream.INSTANCE);

        private PrintStream logger = NULL_STREAM;
        private Consumer<PrintStream> helpCallback = SneakyUtils.nullCons();
        private LogLevel level = LogLevel.WARN;
        private boolean summary;
        private @Nullable Input baseInput;
        private @Nullable Input patchesInput;
        private @Nullable Output patchedOutput;
        private @Nullable Output rejectsOutput;
        private float minFuzz = FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE;
        private int maxOffset = FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET;
        private PatchMode mode = PatchMode.EXACT;
        private String patchesPrefix = "";

        private String aPrefix = "a/";
        private String bPrefix = "b/";
        private String lineEnding = System.lineSeparator();

        private final List<String> ignorePrefixes = new LinkedList<>();

        private Builder() {
        }

        public Builder logTo(Consumer<String> func) {
            return logTo(new ConsumingOutputStream(func));
        }

        public Builder logTo(PrintStream logger) {
            this.logger = Objects.requireNonNull(logger);
            return this;
        }

        public Builder logTo(OutputStream logger) {
            return logTo(new PrintStream(logger));
        }

        public Builder helpCallback(Consumer<PrintStream> helpCallback) {
            this.helpCallback = Objects.requireNonNull(helpCallback);
            return this;
        }

        public Builder level(LogLevel level) {
            this.level = level;
            return this;
        }

        public Builder summary(boolean summary) {
            this.summary = summary;
            return this;
        }

        public Builder baseInput(Input baseInput) {
            this.baseInput = Objects.requireNonNull(baseInput);
            return this;
        }

        public Builder patchesInput(Input patchesInput) {
            this.patchesInput = Objects.requireNonNull(patchesInput);
            return this;
        }

        public Builder aPrefix(String aPrefix) {
            this.aPrefix = aPrefix;
            return this;
        }

        public Builder bPrefix(String bPrefix) {
            this.bPrefix = bPrefix;
            return this;
        }

        public Builder patchedOutput(Output patchedOutput) {
            this.patchedOutput = Objects.requireNonNull(patchedOutput);
            return this;
        }

        public Builder rejectsOutput(@Nullable Output rejectsOutput) {
            this.rejectsOutput = rejectsOutput;
            return this;
        }

        public Builder minFuzz(float minFuzz) {
            this.minFuzz = minFuzz;
            return this;
        }

        public Builder maxOffset(int maxOffset) {
            this.maxOffset = maxOffset;
            return this;
        }

        public Builder mode(PatchMode mode) {
            this.mode = Objects.requireNonNull(mode);
            return this;
        }

        public Builder patchesPrefix(String patchesPrefix) {
            this.patchesPrefix = Objects.requireNonNull(patchesPrefix);
            return this;
        }

        public Builder lineEnding(String lineEnding) {
            this.lineEnding = lineEnding;
            return this;
        }

        public Builder ignorePrefix(String prefix) {
            ignorePrefixes.add(prefix);
            return this;
        }

        public PatchOperation build() {
            if (baseInput == null) throw new IllegalStateException("baseInput is required.");
            if (patchesInput == null) throw new IllegalStateException("patchesInput is required.");
            if (patchedOutput == null) throw new IllegalStateException("patchedOutput is required.");

            return new PatchOperation(logger, level, helpCallback, summary, baseInput, patchesInput, aPrefix, bPrefix, patchedOutput, rejectsOutput, minFuzz, maxOffset, mode, patchesPrefix, lineEnding, ignorePrefixes.toArray(new String[0]));
        }
    }
}
