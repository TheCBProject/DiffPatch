package codechicken.diffpatch.cli;

import codechicken.diffpatch.diff.Differ;
import codechicken.diffpatch.diff.PatienceDiffer;
import codechicken.diffpatch.util.FileCollector;
import codechicken.diffpatch.util.Operation;
import codechicken.diffpatch.util.PatchFile;
import codechicken.diffpatch.util.Utils;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import codechicken.diffpatch.util.archiver.ArchiveReader;
import codechicken.diffpatch.util.archiver.ArchiveWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static codechicken.diffpatch.util.Utils.*;

/**
 * Handles doing a Diff operation from the CLI.
 * <p>
 * Created by covers1624 on 11/8/20.
 */
public class CliDiffer extends CliOperation {

    private final boolean summary;
    private final Path aPath;
    private final Path bPath;
    private final boolean autoHeader;
    private final int context;
    private final Path outputPath;

    private final ArchiveFormat aFormat;
    private final ArchiveFormat bFormat;
    private final ArchiveFormat outputFormat;

    public CliDiffer(PrintStream logger, PrintStream pipe, Consumer<PrintStream> helpCallback, boolean verbose, boolean summary, Path aPath, Path bPath, boolean autoHeader, int context, Path outputPath, ArchiveFormat outputFormat) {
        super(logger, pipe, helpCallback, verbose);
        this.summary = summary;
        this.aPath = aPath;
        this.bPath = bPath;
        this.autoHeader = autoHeader;
        this.context = context;
        this.outputPath = outputPath;

        if (outputFormat == null && outputPath != null) {
            outputFormat = ArchiveFormat.findFormat(outputPath.getFileName());
        }
        this.aFormat = ArchiveFormat.findFormat(aPath.getFileName());
        this.bFormat = ArchiveFormat.findFormat(bPath.getFileName());
        this.outputFormat = outputFormat;
    }

    @Override
    public int operate() throws IOException {
        if (Files.notExists(aPath)) {
            log("Err: File A doesn't exist.");
            return -1;
        }
        if (Files.notExists(bPath)) {
            log("Err: File B doesn't exist.");
            return -1;
        }

        FileCollector patches = new FileCollector();
        DiffSummary summary = new DiffSummary();
        if (Files.isRegularFile(aPath) && Files.isRegularFile(bPath) && aFormat == null && bFormat == null) {
            if (outputFormat != null) {
                log("Err: Can't specify output format when diffing regular files.");
                printHelp();
                return -1;
            }
            if (outputPath != null && Files.exists(outputPath) && !Files.isRegularFile(outputPath)) {
                log("Err: Output already exists and is not a file.");
                printHelp();
                return -1;
            }
            if (outputPath == null && pipe == null) {
                log("Err: Output and pipe not specified.");
                return -1;
            }
            List<String> lines = doDiff(summary, aPath.toString(), bPath.toString(), Files.readAllLines(aPath), Files.readAllLines(bPath), context, autoHeader);
            boolean changes = false;
            if (!lines.isEmpty()) {
                changes = true;
                if (outputPath != null) {
                    Files.write(outputPath, lines);
                } else {
                    pipe.println(String.join("\n", lines) + "\n");
                }
            }
            if (this.summary) {
                summary.print(logger, true);
            }
            return changes ? 1 : 0;
        }

        if (outputFormat != null) {
            if (outputPath != null && Files.exists(outputPath) && !Files.isRegularFile(outputPath)) {
                log("Err: Output already exists and is not a file.");
                printHelp();
                return -1;
            }
        } else {
            if (outputPath != null && Files.exists(outputPath) && !Files.isDirectory(outputPath)) {
                log("Err: Output already exists and is not a directory.");
                printHelp();
                return -1;
            }
        }
        if (outputPath == null && pipe == null) {
            log("Err: Output and pipe not specified.");
            return -1;
        }

        if (Files.isRegularFile(aPath) && Files.isRegularFile(bPath)) {
            if (aFormat == null) {
                log("Err: File A is in an unknown archive format, whilst File B is: %s", bFormat);
                printHelp();
                return -1;
            }
            if (bFormat == null) {
                log("Err: File B is in an unknown archive format, whilst File A is: %s", aFormat);
                printHelp();
                return -1;
            }

            // Diff Archives
            try (ArchiveReader aReader = aFormat.createReader(Files.newInputStream(aPath))) {
                try (ArchiveReader bReader = bFormat.createReader(Files.newInputStream(bPath))) {
                    doDiff(patches, summary, aReader.getEntries(), bReader.getEntries(), sneakF(aReader::readLines), sneakF(bReader::readLines), context, autoHeader);
                }
            }
        } else if (Files.isDirectory(aPath) && Files.isDirectory(bPath)) {
            //Diff Directories
            Map<String, Path> aIndex = indexChildren(aPath);
            Map<String, Path> bIndex = indexChildren(bPath);
            doDiff(patches, summary, aIndex.keySet(), bIndex.keySet(), sneakF(e -> Files.readAllLines(aIndex.get(e))), sneakF(e -> Files.readAllLines(bIndex.get(e))), context, autoHeader);
        } else {
            Set<String> aIndex;
            Function<String, List<String>> aFunc;
            Set<String> bIndex;
            Function<String, List<String>> bFunc;
            if (Files.isDirectory(aPath)) {
                if (bFormat == null) {
                    log("Err: File B is in an unknown format, whilst File A is a directory.");
                    printHelp();
                    return -1;
                }
                Map<String, Path> pathIndex = indexChildren(aPath);
                aIndex = pathIndex.keySet();
                aFunc = sneakF(e -> Files.readAllLines(pathIndex.get(e)));
                //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                try (ArchiveReader reader = bFormat.createReader(Files.newInputStream(bPath))) {
                    bIndex = reader.getEntries();
                    bFunc = sneakF(reader::readLines);
                }
            } else {
                if (aFormat == null) {
                    log("Err: File A is in an unknown format, whilst File B is a directory.");
                    printHelp();
                    return -1;
                }
                //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                try (ArchiveReader reader = aFormat.createReader(Files.newInputStream(aPath))) {
                    aIndex = reader.getEntries();
                    aFunc = sneakF(reader::readLines);
                }
                Map<String, Path> pathIndex = indexChildren(bPath);
                bIndex = pathIndex.keySet();
                bFunc = sneakF(e -> Files.readAllLines(pathIndex.get(e)));
            }
            doDiff(patches, summary, aIndex, bIndex, aFunc, bFunc, context, autoHeader);
        }
        boolean changes = false;
        if (!patches.isEmpty()) {
            changes = true;
            if (outputPath == null && outputFormat == null) {
                for (List<String> lines : patches.values()) {
                    lines.forEach(pipe::println);
                }
            } else if (outputFormat != null) {
                OutputStream sink;
                if (outputPath != null) {
                    sink = Files.newOutputStream(outputPath);
                } else {
                    sink = protectClose(pipe);
                }
                try (ArchiveWriter writer = outputFormat.createWriter(sink)) {
                    for (Map.Entry<String, List<String>> entry : patches.get().entrySet()) {
                        String patchFile = String.join("\n", entry.getValue()) + "\n";
                        writer.writeEntry(entry.getKey(), patchFile.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } else {
                if (Files.exists(outputPath)) {
                    Utils.deleteFolder(outputPath);
                }
                for (Map.Entry<String, List<String>> entry : patches.get().entrySet()) {
                    Path path = outputPath.resolve(entry.getKey());
                    Files.write(makeParentDirs(path), entry.getValue());
                }
            }
        }
        if (this.summary) {
            summary.print(logger, false);
        }
        return changes ? 1 : 0;
    }

    public void doDiff(FileCollector patches, DiffSummary summary, Set<String> aEntries, Set<String> bEntries, Function<String, List<String>> aFunc, Function<String, List<String>> bFunc, int context, boolean autoHeader) {
        List<String> added = bEntries.stream().filter(e -> !aEntries.contains(e)).sorted().collect(Collectors.toList());
        List<String> common = aEntries.stream().filter(bEntries::contains).sorted().collect(Collectors.toList());
        List<String> removed = aEntries.stream().filter(e -> !bEntries.contains(e)).sorted().collect(Collectors.toList());
        for (String file : added) {
            String bName = 'b' + ensureStartsWith('/', file);
            List<String> aLines = Collections.emptyList();
            List<String> bLines = bFunc.apply(file);
            List<String> patchLines = doDiff(summary, null, bName, aLines, bLines, context, autoHeader);
            if (!patchLines.isEmpty()) {
                summary.addedFiles++;
                patches.consume(file + ".patch", patchLines);
            } else {
                summary.unchangedFiles++;
            }
        }
        for (String file : common) {
            String aName = 'a' + ensureStartsWith('/', file);
            String bName = 'b' + ensureStartsWith('/', file);
            List<String> aLines = aFunc.apply(file);
            List<String> bLines = bFunc.apply(file);
            List<String> patchLines = doDiff(summary, aName, bName, aLines, bLines, context, autoHeader);
            if (!patchLines.isEmpty()) {
                summary.changedFiles++;
                patches.consume(file + ".patch", patchLines);
            } else {
                summary.unchangedFiles++;
            }
        }
        for (String file : removed) {
            String aName = 'a' + ensureStartsWith('/', file);
            List<String> aLines = aFunc.apply(file);
            List<String> bLines = Collections.emptyList();
            List<String> patchLines = doDiff(summary, aName, null, aLines, bLines, context, autoHeader);
            if (!patchLines.isEmpty()) {
                summary.removedFiles++;
                patches.consume(file + ".patch", patchLines);
            } else {
                summary.unchangedFiles++;
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

    private static class DiffSummary {

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
}
