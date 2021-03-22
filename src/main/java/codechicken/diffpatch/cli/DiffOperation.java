package codechicken.diffpatch.cli;

import codechicken.diffpatch.diff.Differ;
import codechicken.diffpatch.diff.PatienceDiffer;
import codechicken.diffpatch.util.*;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import codechicken.diffpatch.util.archiver.ArchiveReader;
import codechicken.diffpatch.util.archiver.ArchiveWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static codechicken.diffpatch.util.Utils.*;

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
    private final OutputPath outputPath;

    public DiffOperation(PrintStream logger, Consumer<PrintStream> helpCallback, boolean verbose, boolean summary, InputPath aPath, InputPath bPath, String aPrefix, String bPrefix, boolean autoHeader, int context, OutputPath outputPath) {
        super(logger, helpCallback, verbose);
        this.summary = summary;
        this.aPath = aPath;
        this.bPath = bPath;
        this.aPrefix = aPrefix;
        this.bPrefix = bPrefix;
        this.autoHeader = autoHeader;
        this.context = context;
        this.outputPath = outputPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Result<DiffSummary> operate() throws IOException {
        if (!aPath.exists()) {
            log("Err: File A doesn't exist.");
            return new Result<>(-1);
        }
        if (!bPath.exists()) {
            log("Err: File B doesn't exist.");
            return new Result<>(-1);
        }

        FileCollector patches = new FileCollector();
        DiffSummary summary = new DiffSummary();
        //If inputs are both files, and no format is set, we are diffing singular files.
        if (aPath.isFile() && bPath.isFile() && aPath.getFormat() == null && bPath.getFormat() == null) {
            if (outputPath.getFormat() != null) {
                log("Err: Can't specify output format when diffing regular files.");
                printHelp();
                return new Result<>(-1);
            }
            if (outputPath.getType().isPath() && Files.exists(outputPath.toPath()) && !Files.isRegularFile(outputPath.toPath())) {
                log("Err: Output already exists and is not a file.");
                printHelp();
                return new Result<>(-1);
            }
            List<String> lines = doDiff(summary, aPath.toPath().toString(), bPath.toPath().toString(), aPath.readAllLines(), bPath.readAllLines(), context, autoHeader);
            boolean changes = false;
            if (!lines.isEmpty()) {
                changes = true;
                try (PrintWriter out = new PrintWriter(outputPath.open())) {
                    out.println(String.join(System.lineSeparator(), lines) + System.lineSeparator());
                }
            }
            if (this.summary) {
                summary.print(logger, true);
            }
            return new Result<>(changes ? 1 : 0, summary);
        }

        if (outputPath.getType().isPath()) {
            if (outputPath.getFormat() != null) {
                if (Files.exists(outputPath.toPath()) && !Files.isRegularFile(outputPath.toPath())) {
                    log("Err: Output already exists and is not a file.");
                    printHelp();
                    return new Result<>(-1);
                }
            } else {
                if (Files.exists(outputPath.toPath()) && !Files.isDirectory(outputPath.toPath())) {
                    log("Err: Output already exists and is not a directory.");
                    printHelp();
                    return new Result<>(-1);
                }
            }
        }

        //If both inputs are files at this point, must be archives.
        if (aPath.isFile() && bPath.isFile()) {
            if (aPath.getFormat() == null) {
                log("Err: File A is in an unknown archive format.");
                printHelp();
                return new Result<>(-1);
            }
            if (bPath.getFormat() == null) {
                log("Err: File B is in an unknown archive format.");
                printHelp();
                return new Result<>(-1);
            }

            // Diff Archives
            try (ArchiveReader aReader = aPath.getFormat().createReader(aPath.open())) {
                try (ArchiveReader bReader = bPath.getFormat().createReader(bPath.open())) {
                    doDiff(patches, summary, aReader.getEntries(), bReader.getEntries(), aReader::readLines, bReader::readLines, context, autoHeader);
                }
            }
        } else if (!aPath.isFile() && !bPath.isFile()) {
            //Both inputs are directories.
            Map<String, Path> aIndex = indexChildren(aPath.toPath());
            Map<String, Path> bIndex = indexChildren(bPath.toPath());
            doDiff(patches, summary, aIndex.keySet(), bIndex.keySet(), e -> Files.readAllLines(aIndex.get(e)), e -> Files.readAllLines(bIndex.get(e)), context, autoHeader);
        } else {
            //One input is a directory, other is an archive.
            Set<String> aIndex;
            LinesReader aFunc;
            Set<String> bIndex;
            LinesReader bFunc;
            if (!aPath.isFile()) {
                if (bPath.getFormat() == null) {
                    log("Err: File B is in an unknown format, whilst File A is a directory.");
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
                    log("Err: File A is in an unknown format, whilst File B is a directory.");
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
            doDiff(patches, summary, aIndex, bIndex, aFunc, bFunc, context, autoHeader);
        }
        boolean changes = false;
        if (!patches.isEmpty()) {
            changes = true;
            if (outputPath.getType().isPipe() && outputPath.getFormat() == null) {
                try (PrintWriter out = new PrintWriter(outputPath.open())) {
                    for (List<String> lines : patches.values()) {
                        lines.forEach(out::println);
                    }
                }
            } else if (outputPath.getFormat() != null) {
                try (ArchiveWriter writer = outputPath.getFormat().createWriter(outputPath.open())) {
                    for (Map.Entry<String, List<String>> entry : patches.get().entrySet()) {
                        String patchFile = String.join(System.lineSeparator(), entry.getValue()) + System.lineSeparator();
                        writer.writeEntry(entry.getKey(), patchFile.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } else {
                if (Files.exists(outputPath.toPath())) {
                    Utils.deleteFolder(outputPath.toPath());
                }
                for (Map.Entry<String, List<String>> entry : patches.get().entrySet()) {
                    Path path = outputPath.toPath().resolve(entry.getKey());
                    Files.write(makeParentDirs(path), entry.getValue());
                }
            }
        }
        if (this.summary) {
            summary.print(logger, false);
        }
        return new Result<>(changes ? 1 : 0, summary);
    }

    public void doDiff(FileCollector patches, DiffSummary summary, Set<String> aEntries, Set<String> bEntries, LinesReader aFunc, LinesReader bFunc, int context, boolean autoHeader) {
        List<String> added = bEntries.stream().filter(e -> !aEntries.contains(e)).sorted().collect(Collectors.toList());
        List<String> common = aEntries.stream().filter(bEntries::contains).sorted().collect(Collectors.toList());
        List<String> removed = aEntries.stream().filter(e -> !bEntries.contains(e)).sorted().collect(Collectors.toList());
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
                verbose("Failed to read file: %s", file);
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
                verbose("Failed to read file: %s", file);
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
                verbose("Failed to read file: %s", file);
            }
        }
    }

    public List<String> doDiff(DiffSummary summary, String aName, String bName, List<String> aLines, List<String> bLines, int context, boolean autoHeader) {
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
            if (verbose) {
                verbose("%s -> %s\n No changes.", aName, bName);
            }
            return Collections.emptyList();
        }
        if (verbose || this.summary) {
            long added = patchFile.patches.stream()//
                    .flatMap(e -> e.diffs.stream())//
                    .filter(e -> e.op == Operation.INSERT)//
                    .count();
            long removed = patchFile.patches.stream()//
                    .flatMap(e -> e.diffs.stream())//
                    .filter(e -> e.op == Operation.DELETE)//
                    .count();
            if (this.summary) {
                summary.addedLines += added;
                summary.removedLines += removed;
            }
            if (verbose) {
                verbose("%s -> %s\n %d Added.\n %d Removed.", aName, bName, added, removed);
            }
        }
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

        private static final Consumer<PrintStream> NULL_CALLBACK = e -> {};
        private static final PrintStream NULL_STREAM = new PrintStream(NullOutputStream.INSTANCE);

        private PrintStream logger = NULL_STREAM;
        private Consumer<PrintStream> helpCallback = NULL_CALLBACK;
        private boolean verbose;
        private boolean summary;
        private InputPath aPath;
        private InputPath bPath;
        private boolean autoHeader;
        private int context = Differ.DEFAULT_CONTEXT;
        private OutputPath outputPath;
        private String aPrefix = "a/";
        private String bPrefix = "b/";

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

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
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

        public Builder outputPath(OutputPath outputPath) {
            this.outputPath = Objects.requireNonNull(outputPath);
            return this;
        }

        public Builder outputPath(Path output) {
            return outputPath(output, ArchiveFormat.findFormat(output.getFileName()));
        }

        public Builder outputPath(Path output, ArchiveFormat format) {
            return outputPath(new OutputPath.FilePath(Objects.requireNonNull(output), format));
        }

        public Builder outputPath(OutputStream output, ArchiveFormat format) {
            return outputPath(new OutputPath.PipePath(Objects.requireNonNull(output), Objects.requireNonNull(format)));
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
            return new DiffOperation(logger, helpCallback, verbose, summary, aPath, bPath, aPrefix, bPrefix, autoHeader, context, outputPath);
        }

    }
}
