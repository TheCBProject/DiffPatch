package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.diff.Differ;
import io.codechicken.diffpatch.diff.PatienceDiffer;
import io.codechicken.diffpatch.util.*;
import io.codechicken.diffpatch.util.FileCollector.CollectedEntry;
import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.Input.SingleInput;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.io.NullOutputStream;
import net.covers1624.quack.util.SneakyUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Consumer;

import static io.codechicken.diffpatch.util.LogLevel.*;
import static io.codechicken.diffpatch.util.Utils.filterPrefixed;

/**
 * Handles doing a Diff operation from the CLI.
 * <p>
 * Created by covers1624 on 11/8/20.
 */
public class DiffOperation extends CliOperation<DiffOperation.DiffSummary> {

    private final boolean summary;
    private final Input baseInput;
    private final Input changedInput;
    private final String aPrefix;
    private final String bPrefix;
    private final boolean autoHeader;
    private final int context;
    private final Output patchOutput;
    private final String lineEnding;
    private final String[] ignorePrefixes;

    private DiffOperation(PrintStream logger, LogLevel level, Consumer<PrintStream> helpCallback, boolean summary, Input baseInput, Input changedInput, String aPrefix, String bPrefix, boolean autoHeader, int context, Output patchOutput, String lineEnding, String[] ignorePrefixes) {
        super(logger, level, helpCallback);
        this.summary = summary;
        this.baseInput = baseInput;
        this.changedInput = changedInput;
        this.aPrefix = aPrefix;
        this.bPrefix = bPrefix;
        this.autoHeader = autoHeader;
        this.context = context;
        this.patchOutput = patchOutput;
        this.lineEnding = lineEnding;
        this.ignorePrefixes = ignorePrefixes;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Result<DiffSummary> operate() throws IOException {
        try {
            baseInput.validate("base input");
            changedInput.validate("changed input");
            patchOutput.validate("patch output");
        } catch (IOValidationException ex) {
            log(ERROR, ex.getMessage());
            printHelp();
            return new Result<>(-1);
        }

        FileCollector patches = new FileCollector();
        DiffSummary summary = new DiffSummary();
        // If inputs are both files, and no format is set, we are diffing singular files.
        if (baseInput instanceof SingleInput && changedInput instanceof SingleInput) {
            SingleInput base = (SingleInput) baseInput;
            SingleInput changed = (SingleInput) changedInput;
            if (!(patchOutput instanceof SingleOutput)) {
                log(ERROR, "Can't specify output directory or archive when diffing single files.");
                printHelp();
                return new Result<>(-1);
            }
            SingleOutput output = (SingleOutput) patchOutput;

            List<String> lines = doDiff(summary, base.name(), changed.name(), base.readLines(), changed.readLines(), context, autoHeader);
            boolean changes = false;
            if (!lines.isEmpty()) {
                changes = true;
                try (PrintWriter out = new PrintWriter(output.open())) {
                    out.println(String.join(lineEnding, lines) + lineEnding);
                }
            }
            if (this.summary) {
                summary.print(logger, true);
            }
            return new Result<>(changes ? 1 : 0, summary);
        }

        if (!(baseInput instanceof MultiInput)) {
            log(ERROR, "Can't diff between single files and folders/archives.");
            printHelp();
            return new Result<>(-1);
        }

        if (!(changedInput instanceof MultiInput)) {
            log(ERROR, "Can't diff between folders/archives and single files.");
            printHelp();
            return new Result<>(-1);
        }

        try (MultiInput base = (MultiInput) baseInput;
             MultiInput changed = (MultiInput) changedInput) {
            base.open("");
            changed.open("");
            Set<String> aIndex = filterPrefixed(base.index(), ignorePrefixes);
            Set<String> bIndex = filterPrefixed(changed.index(), ignorePrefixes);
            doDiff(patches, summary, aIndex, bIndex, base, changed, context, autoHeader);
        }

        boolean changes = false;
        if (!patches.isEmpty()) {
            changes = true;
            if (patchOutput instanceof SingleOutput) {
                SingleOutput singleOut = (SingleOutput) patchOutput;
                try (PrintWriter out = new PrintWriter(singleOut.open())) {
                    for (CollectedEntry entry : patches.values()) {
                        // Safe, we only add generated lines to this collector.
                        List<String> lines = ((FileCollector.LinesCollectedEntry) entry).lines;
                        lines.forEach(line -> {
                            out.print(line);
                            out.print(lineEnding);
                        });
                    }
                }
            } else {
                try (MultiOutput output = (MultiOutput) patchOutput) {
                    output.open(true);
                    for (Map.Entry<String, CollectedEntry> entry : patches.get().entrySet()) {
                        output.write(entry.getKey(), entry.getValue().toBytes(lineEnding, true));
                    }
                }
            }
        }
        if (this.summary) {
            summary.print(logger, false);
        }
        return new Result<>(changes ? 1 : 0, summary);
    }

    private void doDiff(FileCollector patches, DiffSummary summary, Set<String> aEntries, Set<String> bEntries, MultiInput aInput, MultiInput bInput, int context, boolean autoHeader) {
        List<String> added = FastStream.of(bEntries).filter(e -> !aEntries.contains(e)).sorted().toList();
        List<String> common = FastStream.of(aEntries).filter(bEntries::contains).sorted().toList();
        List<String> removed = FastStream.of(aEntries).filter(e -> !bEntries.contains(e)).sorted().toList();
        String aPrefix = StringUtils.appendIfMissing(StringUtils.isEmpty(this.aPrefix) ? "a" : this.aPrefix, "/");
        String bPrefix = StringUtils.appendIfMissing(StringUtils.isEmpty(this.bPrefix) ? "b" : this.bPrefix, "/");
        for (String file : added) {
            try {
                String bName = bPrefix + StringUtils.removeStart(file, "/");
                List<String> aLines = Collections.emptyList();
                List<String> bLines = bInput.readLines(file);
                List<String> patchLines = doDiff(summary, null, bName, aLines, bLines, context, autoHeader);
                if (!patchLines.isEmpty()) {
                    summary.addedFiles++;
                    patches.consume(file + ".patch", patchLines);
                } else {
                    summary.unchangedFiles++;
                }
            } catch (IOException e) {
                log(ERROR, "Failed to read file: %s", file);
            }
        }
        for (String file : common) {
            try {
                String aName = aPrefix + StringUtils.removeStart(file, "/");
                String bName = bPrefix + StringUtils.removeStart(file, "/");
                List<String> aLines = aInput.readLines(file);
                List<String> bLines = bInput.readLines(file);
                List<String> patchLines = doDiff(summary, aName, bName, aLines, bLines, context, autoHeader);
                if (!patchLines.isEmpty()) {
                    summary.changedFiles++;
                    patches.consume(file + ".patch", patchLines);
                } else {
                    summary.unchangedFiles++;
                }
            } catch (IOException e) {
                log(ERROR, "Failed to read file: %s", file);
            }
        }
        for (String file : removed) {
            try {
                String aName = aPrefix + StringUtils.removeStart(file, "/");
                List<String> aLines = aInput.readLines(file);
                List<String> bLines = Collections.emptyList();
                List<String> patchLines = doDiff(summary, aName, null, aLines, bLines, context, autoHeader);
                if (!patchLines.isEmpty()) {
                    summary.removedFiles++;
                    patches.consume(file + ".patch", patchLines);
                } else {
                    summary.unchangedFiles++;
                }
            } catch (IOException e) {
                log(ERROR, "Failed to read file: %s", file);
            }
        }
    }

    private List<String> doDiff(DiffSummary summary, @Nullable String aName, @Nullable String bName, List<String> aLines, List<String> bLines, int context, boolean autoHeader) {
        PatienceDiffer differ = new PatienceDiffer();
        PatchFile patchFile = new PatchFile();
        patchFile.basePath = aName != null ? aName : "/dev/null";
        patchFile.patchedPath = bName != null ? bName : "/dev/null";
        if (aLines.isEmpty()) {
            patchFile.patches = Differ.makeFileAdded(bLines);
        } else if (bLines.isEmpty()) {
            patchFile.patches = Differ.makeFileRemoved(aLines);
        } else {
            patchFile.patches = differ.makePatches(aLines, bLines, context, true);
        }
        if (patchFile.patches.isEmpty()) {
            log(DEBUG, "%s -> %s\n No changes.", aName, bName);
            return Collections.emptyList();
        }
        long added = FastStream.of(patchFile.patches)
                .flatMap(e -> e.diffs)
                .filter(e -> e.op == Operation.INSERT)
                .count();
        long removed = FastStream.of(patchFile.patches)
                .flatMap(e -> e.diffs)
                .filter(e -> e.op == Operation.DELETE)
                .count();
        if (this.summary) {
            summary.addedLines += added;
            summary.removedLines += removed;
        }
        log(this.summary ? INFO : DEBUG, "%s -> %s\n %d Added.\n %d Removed.", aName, bName, added, removed);

        return patchFile.toLines(autoHeader);
    }

    public static class DiffSummary {

        public int unchangedFiles;
        public int addedFiles;
        public int changedFiles;
        public int removedFiles;

        public long addedLines;
        public long removedLines;

        public void print(PrintStream logger, boolean slim) {
            logger.println("Diff Summary:");
            if (!slim) {
                logger.println(" UnChanged files: " + unchangedFiles);
                logger.println(" Added files:     " + addedFiles);
                logger.println(" Changed files:   " + changedFiles);
                logger.println(" Removed files:   " + removedFiles);
            }

            logger.println(" Added lines:     " + addedLines);
            logger.println(" Removed lines:   " + removedLines);
        }
    }

    public static class Builder {

        private static final PrintStream NULL_STREAM = new PrintStream(NullOutputStream.INSTANCE);

        private PrintStream logger = NULL_STREAM;
        private Consumer<PrintStream> helpCallback = SneakyUtils.nullCons();
        private LogLevel level = LogLevel.WARN;
        private boolean summary;
        private @Nullable Input baseInput;
        private @Nullable Input changedInput;
        private boolean autoHeader;
        private int context = Differ.DEFAULT_CONTEXT;
        private @Nullable Output patchesOutput;
        private String aPrefix = "a/";
        private String bPrefix = "b/";
        private String lineEnding = System.lineSeparator();

        private final List<String> ignorePrefixes = new LinkedList<>();

        private Builder() {
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

        public Builder changedInput(Input changedInput) {
            this.changedInput = Objects.requireNonNull(changedInput);
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

        public Builder autoHeader(boolean autoHeader) {
            this.autoHeader = autoHeader;
            return this;
        }

        public Builder context(int context) {
            this.context = context;
            return this;
        }

        public Builder patchesOutput(Output patchesOutput) {
            this.patchesOutput = Objects.requireNonNull(patchesOutput);
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

        public DiffOperation build() {
            if (baseInput == null) throw new IllegalStateException("baseInput is required.");
            if (changedInput == null) throw new IllegalStateException("changedInput is required.");
            if (patchesOutput == null) throw new IllegalStateException("patchesOutput is required.");

            return new DiffOperation(logger, level, helpCallback, summary, baseInput, changedInput, aPrefix, bPrefix, autoHeader, context, patchesOutput, lineEnding, ignorePrefixes.toArray(new String[0]));
        }
    }
}
