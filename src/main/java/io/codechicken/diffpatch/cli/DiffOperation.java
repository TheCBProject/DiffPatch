package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.diff.Differ;
import io.codechicken.diffpatch.diff.PatienceDiffer;
import io.codechicken.diffpatch.util.*;
import io.codechicken.diffpatch.util.FileCollector.CollectedEntry;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import io.codechicken.diffpatch.util.archiver.ArchiveReader;
import net.covers1624.quack.collection.FastStream;
import net.covers1624.quack.io.NullOutputStream;
import net.covers1624.quack.util.SneakyUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static io.codechicken.diffpatch.util.LogLevel.*;
import static io.codechicken.diffpatch.util.Utils.filterPrefixed;
import static io.codechicken.diffpatch.util.Utils.indexChildren;

/**
 * Handles doing a Diff operation from the CLI.
 * <p>
 * Created by covers1624 on 11/8/20.
 */
public class DiffOperation extends CliOperation<DiffOperation.DiffSummary> {

    private final boolean summary;
    private final InputPath aPath;
    private final InputPath bPath;
    private final String aPrefix;
    private final String bPrefix;
    private final boolean autoHeader;
    private final int context;
    private final Output patchOutput;
    private final String lineEnding;
    private final String[] ignorePrefixes;

    private DiffOperation(PrintStream logger, LogLevel level, Consumer<PrintStream> helpCallback, boolean summary, InputPath aPath, InputPath bPath, String aPrefix, String bPrefix, boolean autoHeader, int context, Output patchOutput, String lineEnding, String[] ignorePrefixes) {
        super(logger, level, helpCallback);
        this.summary = summary;
        this.aPath = aPath;
        this.bPath = bPath;
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
        if (!aPath.exists()) {
            log(ERROR, "File A doesn't exist.");
            return new Result<>(-1);
        }
        if (!bPath.exists()) {
            log(ERROR, "File B doesn't exist.");
            return new Result<>(-1);
        }

        try {
            patchOutput.validate("patch output");
        } catch (Output.OutputValidationException ex) {
            log(ERROR, ex.getMessage());
            printHelp();
            return new Result<>(-1);
        }

        FileCollector patches = new FileCollector();
        DiffSummary summary = new DiffSummary();
        // If inputs are both files, and no format is set, we are diffing singular files.
        if (aPath.isFile() && bPath.isFile() && aPath.getFormat() == null && bPath.getFormat() == null) {
            if (!(patchOutput instanceof SingleOutput)) {
                log(ERROR, "Can't specify output directory or archive when diffing single files.");
                printHelp();
                return new Result<>(-1);
            }
            SingleOutput output = (SingleOutput) patchOutput;

            List<String> lines = doDiff(summary, aPath.toPath().toString(), bPath.toPath().toString(), aPath.readAllLines(), bPath.readAllLines(), context, autoHeader);
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

        //If both inputs are files at this point, must be archives.
        if (aPath.isFile() && bPath.isFile()) {
            if (aPath.getFormat() == null) {
                log(ERROR, "File A is in an unknown archive format.");
                printHelp();
                return new Result<>(-1);
            }
            if (bPath.getFormat() == null) {
                log(ERROR, "File B is in an unknown archive format.");
                printHelp();
                return new Result<>(-1);
            }

            // Diff Archives
            try (ArchiveReader aReader = aPath.getFormat().createReader(aPath.open())) {
                try (ArchiveReader bReader = bPath.getFormat().createReader(bPath.open())) {
                    Set<String> aIndex = filterPrefixed(aReader.getEntries(), ignorePrefixes);
                    Set<String> bIndex = filterPrefixed(bReader.getEntries(), ignorePrefixes);
                    doDiff(patches, summary, aIndex, bIndex, aReader::readLines, bReader::readLines, context, autoHeader);
                }
            }
        } else if (!aPath.isFile() && !bPath.isFile()) {
            //Both inputs are directories.
            Map<String, Path> aFiles = indexChildren(aPath.toPath());
            Map<String, Path> bFiles = indexChildren(bPath.toPath());
            Set<String> aIndex = filterPrefixed(aFiles.keySet(), ignorePrefixes);
            Set<String> bIndex = filterPrefixed(bFiles.keySet(), ignorePrefixes);
            doDiff(patches, summary, aIndex, bIndex, e -> Files.readAllLines(aFiles.get(e)), e -> Files.readAllLines(bFiles.get(e)), context, autoHeader);
        } else {
            //One input is a directory, other is an archive.
            Set<String> aIndex;
            LinesReader aFunc;
            Set<String> bIndex;
            LinesReader bFunc;
            if (!aPath.isFile()) {
                if (bPath.getFormat() == null) {
                    log(ERROR, "File B is in an unknown format, whilst File A is a directory.");
                    printHelp();
                    return new Result<>(-1);
                }
                Map<String, Path> pathIndex = indexChildren(aPath.toPath());
                aIndex = pathIndex.keySet();
                aFunc = e -> Files.readAllLines(pathIndex.get(e));
                //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                try (ArchiveReader reader = bPath.getFormat().createReader(bPath.open())) {
                    bIndex = reader.getEntries();
                    bFunc = reader::readLines;
                }
            } else {
                if (aPath.getFormat() == null) {
                    log(ERROR, "File A is in an unknown format, whilst File B is a directory.");
                    printHelp();
                    return new Result<>(-1);
                }
                //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                try (ArchiveReader reader = aPath.getFormat().createReader(aPath.open())) {
                    aIndex = reader.getEntries();
                    aFunc = reader::readLines;
                }
                Map<String, Path> pathIndex = indexChildren(bPath.toPath());
                bIndex = pathIndex.keySet();
                bFunc = e -> Files.readAllLines(pathIndex.get(e));
            }
            aIndex = filterPrefixed(aIndex, ignorePrefixes);
            bIndex = filterPrefixed(bIndex, ignorePrefixes);
            doDiff(patches, summary, aIndex, bIndex, aFunc, bFunc, context, autoHeader);
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

    private void doDiff(FileCollector patches, DiffSummary summary, Set<String> aEntries, Set<String> bEntries, LinesReader aFunc, LinesReader bFunc, int context, boolean autoHeader) {
        List<String> added = FastStream.of(bEntries).filter(e -> !aEntries.contains(e)).sorted().toList();
        List<String> common = FastStream.of(aEntries).filter(bEntries::contains).sorted().toList();
        List<String> removed = FastStream.of(aEntries).filter(e -> !bEntries.contains(e)).sorted().toList();
        String aPrefix = StringUtils.appendIfMissing(StringUtils.isEmpty(this.aPrefix) ? "a" : this.aPrefix, "/");
        String bPrefix = StringUtils.appendIfMissing(StringUtils.isEmpty(this.bPrefix) ? "b" : this.bPrefix, "/");
        for (String file : added) {
            try {
                String bName = bPrefix + StringUtils.removeStart(file, "/");
                List<String> aLines = Collections.emptyList();
                List<String> bLines = bFunc.apply(file);
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
                List<String> aLines = aFunc.apply(file);
                List<String> bLines = bFunc.apply(file);
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
                List<String> aLines = aFunc.apply(file);
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

    private interface LinesReader {

        List<String> apply(String path) throws IOException;
    }

    public static class Builder {

        private static final PrintStream NULL_STREAM = new PrintStream(NullOutputStream.INSTANCE);

        private PrintStream logger = NULL_STREAM;
        private Consumer<PrintStream> helpCallback = SneakyUtils.nullCons();
        private LogLevel level = LogLevel.WARN;
        private boolean summary;
        private @Nullable InputPath aPath;
        private @Nullable InputPath bPath;
        private boolean autoHeader;
        private int context = Differ.DEFAULT_CONTEXT;
        private @Nullable Output outputPath;
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

        public Builder aPath(InputPath aPath) {
            if (this.aPath != null) {
                throw new IllegalStateException("Unable to replace aPath.");
            }
            this.aPath = Objects.requireNonNull(aPath);
            return this;
        }

        public Builder aPath(Path aPath) {
            return aPath(aPath, ArchiveFormat.findFormat(aPath.getFileName()));
        }

        public Builder aPath(Path aPath, ArchiveFormat format) {
            return aPath(new InputPath.FilePath(Objects.requireNonNull(aPath), format));
        }

        public Builder aPath(byte[] aPath, ArchiveFormat format) {
            InputStream is = new ByteArrayInputStream(Objects.requireNonNull(aPath));
            return aPath(new InputPath.PipePath(is, Objects.requireNonNull(format)));
        }

        public Builder bPath(InputPath bPath) {
            if (this.bPath != null) {
                throw new IllegalStateException("Unable to replace bPath.");
            }
            this.bPath = Objects.requireNonNull(bPath);
            return this;
        }

        public Builder aPrefix(String aPrefix) {
            this.aPrefix = aPrefix;
            return this;
        }

        public Builder bPath(Path bPath) {
            return bPath(bPath, ArchiveFormat.findFormat(bPath.getFileName()));
        }

        public Builder bPath(Path bPath, ArchiveFormat format) {
            return bPath(new InputPath.FilePath(Objects.requireNonNull(bPath), format));
        }

        public Builder bPath(byte[] bPath, ArchiveFormat format) {
            InputStream is = new ByteArrayInputStream(Objects.requireNonNull(bPath));
            return bPath(new InputPath.PipePath(is, Objects.requireNonNull(format)));
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

        public Builder outputPath(Output outputPath) {
            this.outputPath = Objects.requireNonNull(outputPath);
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
            if (aPath == null) {
                throw new IllegalStateException("aPath not set.");
            }
            if (bPath == null) {
                throw new IllegalStateException("bPath not set.");
            }
            if (outputPath == null) {
                throw new IllegalStateException("output not set.");
            }
            return new DiffOperation(logger, level, helpCallback, summary, aPath, bPath, aPrefix, bPrefix, autoHeader, context, outputPath, lineEnding, ignorePrefixes.toArray(new String[0]));
        }

    }
}
