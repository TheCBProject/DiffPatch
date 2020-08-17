package codechicken.diffpatch.cli;

import codechicken.diffpatch.patch.Patcher;
import codechicken.diffpatch.util.*;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import codechicken.diffpatch.util.archiver.ArchiveReader;
import codechicken.diffpatch.util.archiver.ArchiveWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static codechicken.diffpatch.util.Utils.*;

/**
 * Created by covers1624 on 11/8/20.
 */
public class CliPatcher extends CliOperation {

    private final boolean summary;
    private final Path basePath;
    private final Path patchesPath;
    private final Path outputPath;
    private final Path rejectsPath;
    private final float minFuzz;
    private final int maxOffset;
    private final PatchMode mode;
    private final String patchesPrefix;

    private final ArchiveFormat baseFormat;
    private final ArchiveFormat patchesFormat;
    private final ArchiveFormat outputFormat;
    private final ArchiveFormat rejectsFormat;

    public CliPatcher(PrintStream logger, PrintStream pipe, Consumer<PrintStream> helpCallback, boolean verbose, boolean summary, Path basePath, Path patchesPath, Path outputPath, Path rejectsPath, float minFuzz, int maxOffset, PatchMode mode, String patchesPrefix, ArchiveFormat outputFormat, ArchiveFormat rejectsFormat) {
        super(logger, pipe, helpCallback, verbose);
        this.summary = summary;
        this.basePath = basePath;
        this.patchesPath = patchesPath;
        this.outputPath = outputPath;
        this.rejectsPath = rejectsPath;
        this.minFuzz = minFuzz;
        this.maxOffset = maxOffset;
        this.mode = mode;
        this.patchesPrefix = patchesPrefix;

        if (outputFormat == null && outputPath != null) {
            outputFormat = ArchiveFormat.findFormat(outputPath.getFileName());
        }
        if (rejectsFormat == null && rejectsPath != null) {
            rejectsFormat = ArchiveFormat.findFormat(rejectsPath.getFileName());
        }

        this.baseFormat = ArchiveFormat.findFormat(basePath.getFileName());
        this.patchesFormat = ArchiveFormat.findFormat(patchesPath.getFileName());
        this.outputFormat = outputFormat;
        this.rejectsFormat = rejectsFormat;
    }

    @Override
    public int operate() throws IOException {
        if (Files.notExists(basePath)) {
            log("Err: Base file doesn't exist.");
            return -1;
        }
        if (Files.notExists(patchesPath)) {
            log("Err: Patch file doesn't exist.");
            return -1;
        }

        FileCollector outputCollector = new FileCollector();
        FileCollector rejectCollector = new FileCollector();
        PatchesSummary summary = new PatchesSummary();
        boolean patchSuccess;

        if (Files.isRegularFile(basePath) && Files.isRegularFile(patchesPath) && baseFormat == null && patchesFormat == null) {
            if (outputFormat != null) {
                log("Err: Can't specify output format when patching regular file.");
                printHelp();
                return -1;
            }
            if (outputPath != null && Files.exists(outputPath) && !Files.isRegularFile(outputPath)) {
                log("Err: Output already exists and is not a file.");
                printHelp();
                return -1;
            }

            if (rejectsFormat != null) {
                log("Err: Can't specify reject format when patching regular file.");
                printHelp();
                return -1;
            }
            if (rejectsPath != null && Files.exists(rejectsPath) && !Files.isRegularFile(rejectsPath)) {
                log("Err: Reject already exists and is not a file.");
                printHelp();
                return -1;
            }

            boolean success = doPatch(outputCollector, rejectCollector, summary, basePath.toString(), Files.readAllLines(basePath), patchesPath.toString(), Files.readAllLines(patchesPath), minFuzz, maxOffset, mode);
            List<String> output = outputCollector.getSingleFile();
            List<String> reject = rejectCollector.getSingleFile();
            if (outputPath != null) {
                Files.write(outputPath, output);
            } else {
                pipe.println(String.join("\n", output) + "\n");
            }

            if (rejectsPath != null && !reject.isEmpty()) {
                Files.write(rejectsPath, reject);
            }
            if (this.summary) {
                summary.print(logger, true);
            }
            return success ? 0 : 1;
        }

        if (outputPath == null && outputFormat == null) {
            log("Err: Output path doesnt exist and output format not specified.");
            printHelp();
            return -1;
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

        if (Files.isRegularFile(basePath) && Files.isRegularFile(patchesPath)) {
            if (baseFormat == null) {
                log("Err: Base file is in an unknown archive format, whilst Patches file is: %s", baseFormat);
                printHelp();
                return -1;
            }
            if (patchesFormat == null) {
                log("Err: Patches file is in an unknown archive format, whilst Base file is: %s", patchesFormat);
                printHelp();
                return -1;
            }

            try (ArchiveReader baseReader = baseFormat.createReader(Files.newInputStream(basePath))) {
                try (ArchiveReader patchesReader = patchesFormat.createReader(Files.newInputStream(patchesPath), patchesPrefix)) {
                    patchSuccess = doPatch(outputCollector, rejectCollector, summary, baseReader.getEntries(), patchesReader.getEntries(), sneakF(baseReader::readLines), sneakF(patchesReader::readLines), minFuzz, maxOffset, mode);
                }
            }
        } else {
            if (Files.isDirectory(basePath) && Files.isDirectory(patchesPath)) {
                Map<String, Path> baseIndex = indexChildren(basePath);
                Map<String, Path> patchIndex = indexChildren(patchesPath, patchesPrefix);
                patchSuccess = doPatch(outputCollector, rejectCollector, summary, baseIndex.keySet(), patchIndex.keySet(), sneakF(e -> Files.readAllLines(baseIndex.get(e))), sneakF(e -> Files.readAllLines(patchIndex.get(e))), minFuzz, maxOffset, mode);
            } else {
                Set<String> baseIndex;
                Function<String, List<String>> baseFunc;
                Set<String> patchIndex;
                Function<String, List<String>> patchFunc;
                if (Files.isDirectory(basePath)) {
                    if (patchesFormat == null) {
                        log("Err: Patches file is in an unknown format, whilst Base file is a directory.");
                        printHelp();
                        return -1;
                    }
                    Map<String, Path> pathIndex = indexChildren(basePath);
                    baseIndex = pathIndex.keySet();
                    baseFunc = sneakF(e -> Files.readAllLines(pathIndex.get(e)));
                    //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                    try (ArchiveReader reader = patchesFormat.createReader(Files.newInputStream(patchesPath), patchesPrefix)) {
                        patchIndex = reader.getEntries();
                        patchFunc = sneakF(reader::readLines);
                    }
                } else {
                    if (baseFormat == null) {
                        log("Err: Base file is in an unknown format, whilst Patches file is a directory.");
                        printHelp();
                        return -1;
                    }
                    Map<String, Path> pathIndex = indexChildren(patchesPath, patchesPrefix);
                    patchIndex = pathIndex.keySet();
                    patchFunc = sneakF(e -> Files.readAllLines(pathIndex.get(e)));
                    //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                    try (ArchiveReader reader = baseFormat.createReader(Files.newInputStream(basePath))) {
                        baseIndex = reader.getEntries();
                        baseFunc = sneakF(reader::readLines);
                    }
                }
                patchSuccess = doPatch(outputCollector, rejectCollector, summary, baseIndex, patchIndex, baseFunc, patchFunc, minFuzz, maxOffset, mode);
            }
        }

        if (outputFormat != null) {
            OutputStream sink;
            if (outputPath != null) {
                Files.deleteIfExists(outputPath);
                sink = Files.newOutputStream(outputPath);
            } else {
                sink = protectClose(pipe);
            }
            try (ArchiveWriter writer = outputFormat.createWriter(sink)) {
                for (Map.Entry<String, List<String>> entry : outputCollector.get().entrySet()) {
                    String file = String.join("\n", entry.getValue()) + "\n";
                    writer.writeEntry(entry.getKey(), file.getBytes(StandardCharsets.UTF_8));
                }
            }
        } else {
            if (Files.exists(outputPath)) {
                Utils.deleteFolder(outputPath);
            }
            for (Map.Entry<String, List<String>> entry : outputCollector.get().entrySet()) {
                Path path = outputPath.resolve(entry.getKey());
                Files.write(makeParentDirs(path), entry.getValue());
            }
        }

        if (rejectsPath != null) {
            if (rejectsFormat != null) {
                try (ArchiveWriter writer = rejectsFormat.createWriter(Files.newOutputStream(rejectsPath))) {
                    for (Map.Entry<String, List<String>> entry : rejectCollector.get().entrySet()) {
                        String file = String.join("\n", entry.getValue()) + "\n";
                        writer.writeEntry(entry.getKey(), file.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } else {
                if (Files.exists(rejectsPath)) {
                    Utils.deleteFolder(rejectsPath);
                }
                for (Map.Entry<String, List<String>> entry : rejectCollector.get().entrySet()) {
                    Path path = rejectsPath.resolve(entry.getKey());
                    Files.write(makeParentDirs(path), entry.getValue());
                }
            }
        }
        if (this.summary) {
            summary.print(logger, false);
        }
        return patchSuccess ? 0 : 1;
    }

    public boolean doPatch(FileCollector oCollector, FileCollector rCollector, PatchesSummary summary, Set<String> bEntries, Set<String> pEntries, Function<String, List<String>> bFunc, Function<String, List<String>> pFunc, float minFuzz, int maxOffset, PatchMode mode) {
        Map<String, String> patchLookupRev = pEntries.stream().collect(Collectors.toMap(e -> e, e -> e.substring(0, e.lastIndexOf(".patch"))));
        Map<String, String> patchLookup = patchLookupRev.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        Set<String> transformedPatches = pEntries.stream().map(patchLookupRev::get).collect(Collectors.toSet());

        List<String> notPatched = bEntries.stream().filter(e -> !transformedPatches.contains(e)).sorted().collect(Collectors.toList());
        List<String> patchedFiles = bEntries.stream().filter(transformedPatches::contains).sorted().collect(Collectors.toList());
        List<String> removedFiles = transformedPatches.stream().filter(e -> !bEntries.contains(e)).sorted().collect(Collectors.toList());

        boolean result = true;
        for (String file : notPatched) {
            summary.unchangedFiles++;
            oCollector.consume(file, bFunc.apply(file));
        }

        for (String file : patchedFiles) {
            summary.changedFiles++;
            String patchFile = patchLookup.get(file);
            List<String> baseLines = bFunc.apply(file);
            List<String> patchLines = pFunc.apply(patchFile);
            result &= doPatch(oCollector, rCollector, summary, file, baseLines, patchFile, patchLines, minFuzz, maxOffset, mode);
        }

        for (String file : removedFiles) {
            summary.missingFiles++;
            String patchName = patchLookup.get(file);
            List<String> lines = new ArrayList<>(pFunc.apply(patchName));
            lines.add(0, "++++ Target missing");
            verbose("Missing patch target for %s", patchName);
            rCollector.consume(patchName, lines);
            result = false;
        }

        return result;
    }

    public boolean doPatch(FileCollector outputCollector, FileCollector rejectCollector, PatchesSummary summary, String baseName, List<String> base, String patchName, List<String> patch, float minFuzz, int maxOffset, PatchMode mode) {
        PatchFile patchFile = PatchFile.fromLines(patch, true);
        Patcher patcher = new Patcher(patchFile, base, minFuzz, maxOffset);
        verbose("Patching: " + baseName);
        List<Patcher.Result> results = patcher.patch(mode).collect(Collectors.toList());
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
            if (verbose) {
                verbose(" Hunk %d: %s", i, result.summary());
            }
            if (!result.success) {
                if (!first) {
                    rejectLines.add("");
                }
                first = false;
                rejectLines.add("++++ REJECTED HUNK: " + (i + 1));
                rejectLines.add(result.patch.getHeader());
                result.patch.diffs.stream().map(Diff::toString).forEach(rejectLines::add);
                rejectLines.add("++++ END HUNK");
            }
        }
        outputCollector.consume(baseName, patcher.lines);
        if (!rejectLines.isEmpty()) {
            rejectCollector.consume(patchName + ".rej", rejectLines);
            return false;
        }
        return true;
    }

    private static class PatchesSummary {

        public int unchangedFiles;
        public int changedFiles;
        public int missingFiles;
        public int failedMatches;
        public int exactMatches;
        public int offsetMatches;
        public int fuzzyMatches;

        public double overallQuality;

        public void print(PrintStream logger, boolean slim) {
            logger.println("Patch Summary:");
            if (!slim) {
                logger.println(" Un-changed files: " + unchangedFiles);
                logger.println(" Changed files:    " + changedFiles);
                logger.println(" Missing files:    " + missingFiles);
            }
            logger.println();
            logger.println(" Failed matches:   " + failedMatches);
            logger.println(" Exact matches:    " + exactMatches);
            logger.println(" Offset matches:   " + offsetMatches);
            logger.println(" Fuzzy matches:    " + fuzzyMatches);

            logger.println(String.format("Overall Quality   %.2f%%", overallQuality / (failedMatches + exactMatches + offsetMatches + fuzzyMatches)));
        }
    }
}
