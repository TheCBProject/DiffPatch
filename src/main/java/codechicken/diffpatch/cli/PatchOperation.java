package codechicken.diffpatch.cli;

import codechicken.diffpatch.patch.Patcher;
import codechicken.diffpatch.util.*;
import codechicken.diffpatch.util.archiver.ArchiveReader;
import codechicken.diffpatch.util.archiver.ArchiveWriter;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
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
public class PatchOperation extends CliOperation {

    private final boolean summary;
    private final InputPath basePath;
    private final InputPath patchesPath;
    private final OutputPath outputPath;
    private final OutputPath rejectsPath;
    private final float minFuzz;
    private final int maxOffset;
    private final PatchMode mode;
    private final String patchesPrefix;

    public PatchOperation(PrintStream logger, Consumer<PrintStream> helpCallback, boolean verbose, boolean summary, InputPath basePath, InputPath patchesPath, OutputPath outputPath, OutputPath rejectsPath, float minFuzz, int maxOffset, PatchMode mode, String patchesPrefix) {
        super(logger, helpCallback, verbose);
        this.summary = summary;
        this.basePath = basePath;
        this.patchesPath = patchesPath;
        this.outputPath = outputPath;
        this.rejectsPath = rejectsPath;
        this.minFuzz = minFuzz;
        this.maxOffset = maxOffset;
        this.mode = mode;
        this.patchesPrefix = patchesPrefix;
    }

    @Override
    public int operate() throws IOException {
        if (basePath.getType().isPath() && Files.notExists(basePath.toPath())) {
            log("Err: Base file doesn't exist.");
            return -1;
        }
        if (patchesPath.getType().isPath() && Files.notExists(patchesPath.toPath())) {
            log("Err: Patch file doesn't exist.");
            return -1;
        }

        FileCollector outputCollector = new FileCollector();
        FileCollector rejectCollector = new FileCollector();
        PatchesSummary summary = new PatchesSummary();
        boolean patchSuccess;

        if (basePath.isFile() && patchesPath.isFile() && basePath.getFormat() == null && patchesPath.getFormat() == null) {
            if (outputPath.getFormat() != null) {
                log("Err: Can't specify output format when patching regular file.");
                printHelp();
                return -1;
            }
            if (outputPath.getType().isPath()) {
                Path out = outputPath.toPath();
                if (Files.exists(out) && !Files.isRegularFile(out)) {
                    log("Err: Output already exists and is not a file.");
                    printHelp();
                    return -1;
                }
            }
            if (rejectsPath != null) {
                if (rejectsPath.getFormat() != null) {
                    log("Err: Can't specify reject format when patching regular file.");
                    printHelp();
                    return -1;
                }
                if (rejectsPath.getType().isPath()) {
                    Path out = rejectsPath.toPath();
                    if (Files.exists(out) && !Files.isRegularFile(out)) {
                        log("Err: Reject already exists and is not a file.");
                        printHelp();
                        return -1;
                    }
                }
            }

            boolean success = doPatch(outputCollector, rejectCollector, summary, basePath.toString(), basePath.readAllLines(), patchesPath.toString(), patchesPath.readAllLines(), minFuzz, maxOffset, mode);
            List<String> output = outputCollector.getSingleFile();
            List<String> reject = rejectCollector.getSingleFile();
            try (PrintWriter out = new PrintWriter(outputPath.open())) {
                out.println(String.join("\n", output) + "\n");
            }

            if (rejectsPath != null && !reject.isEmpty()) {
                try (PrintWriter out = new PrintWriter(rejectsPath.open())) {
                    out.println(String.join("\n", reject + "\n"));
                }
            }
            if (this.summary) {
                summary.print(logger, true);
            }
            return success ? 0 : 1;
        }

        if (outputPath.getType().isPipe() && outputPath.getFormat() == null) {
            log("Err: Output detected as pipe but no format is specified.");
            printHelp();
            return -1;
        }

        if (outputPath.isFile()) {
            Path out = outputPath.toPath();
            if (outputPath.getFormat() == null) {
                if (Files.exists(out) && !Files.isRegularFile(out)) {
                    log("Err: Output already exists and is not a file.");
                    printHelp();
                    return -1;
                }

            } else {
                if (Files.exists(out) && !Files.isDirectory(out)) {
                    log("Err: Output already exists and is not a directory.");
                    printHelp();
                    return -1;
                }
            }
        }

        if (basePath.isFile() && patchesPath.isFile()) {
            if (basePath.getFormat() == null) {
                log("Err: Base path is in an unknown archive format");
                printHelp();
                return -1;
            }
            if (patchesPath.getFormat() == null) {
                log("Err: Patches path is in an unknown archive format");
                printHelp();
                return -1;
            }

            try (ArchiveReader baseReader = basePath.getFormat().createReader(basePath.open())) {
                try (ArchiveReader patchesReader = patchesPath.getFormat().createReader(patchesPath.open(), patchesPrefix)) {
                    patchSuccess = doPatch(outputCollector, rejectCollector, summary, baseReader.getEntries(), patchesReader.getEntries(), sneakF(baseReader::readLines), sneakF(patchesReader::readLines), minFuzz, maxOffset, mode);
                }
            }
        } else {
            if (!basePath.isFile() && !patchesPath.isFile()) {
                Map<String, Path> baseIndex = indexChildren(basePath.toPath());
                Map<String, Path> patchIndex = indexChildren(patchesPath.toPath(), patchesPrefix);
                patchSuccess = doPatch(outputCollector, rejectCollector, summary, baseIndex.keySet(), patchIndex.keySet(), sneakF(e -> Files.readAllLines(baseIndex.get(e))), sneakF(e -> Files.readAllLines(patchIndex.get(e))), minFuzz, maxOffset, mode);
            } else {
                Set<String> baseIndex;
                Function<String, List<String>> baseFunc;
                Set<String> patchIndex;
                Function<String, List<String>> patchFunc;
                if (!basePath.isFile()) {
                    if (patchesPath.getFormat() == null) {
                        log("Err: Patches file is in an unknown format, whilst Base file is a directory.");
                        printHelp();
                        return -1;
                    }
                    Map<String, Path> pathIndex = indexChildren(basePath.toPath());
                    baseIndex = pathIndex.keySet();
                    baseFunc = sneakF(e -> Files.readAllLines(pathIndex.get(e)));
                    //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                    try (ArchiveReader reader = patchesPath.getFormat().createReader(patchesPath.open(), patchesPrefix)) {
                        patchIndex = reader.getEntries();
                        patchFunc = sneakF(reader::readLines);
                    }
                } else {
                    if (basePath.getFormat() == null) {
                        log("Err: Base file is in an unknown format, whilst Patches file is a directory.");
                        printHelp();
                        return -1;
                    }
                    Map<String, Path> pathIndex = indexChildren(patchesPath.toPath(), patchesPrefix);
                    patchIndex = pathIndex.keySet();
                    patchFunc = sneakF(e -> Files.readAllLines(pathIndex.get(e)));
                    //ArchiveReaders should Greedy load all data inside the archive into memory, this is safe.
                    try (ArchiveReader reader = basePath.getFormat().createReader(basePath.open())) {
                        baseIndex = reader.getEntries();
                        baseFunc = sneakF(reader::readLines);
                    }
                }
                patchSuccess = doPatch(outputCollector, rejectCollector, summary, baseIndex, patchIndex, baseFunc, patchFunc, minFuzz, maxOffset, mode);
            }
        }

        if (outputPath.getFormat() != null) {
            try (ArchiveWriter writer = outputPath.getFormat().createWriter(outputPath.open())) {
                for (Map.Entry<String, List<String>> entry : outputCollector.get().entrySet()) {
                    String file = String.join("\n", entry.getValue()) + "\n";
                    writer.writeEntry(entry.getKey(), file.getBytes(StandardCharsets.UTF_8));
                }
            }
        } else {
            if (Files.exists(outputPath.toPath())) {
                Utils.deleteFolder(outputPath.toPath());
            }
            for (Map.Entry<String, List<String>> entry : outputCollector.get().entrySet()) {
                Path path = outputPath.toPath().resolve(entry.getKey());
                Files.write(makeParentDirs(path), entry.getValue());
            }
        }

        if (rejectsPath != null) {
            if (rejectsPath.getFormat() != null) {
                try (ArchiveWriter writer = rejectsPath.getFormat().createWriter(rejectsPath.open())) {
                    for (Map.Entry<String, List<String>> entry : rejectCollector.get().entrySet()) {
                        String file = String.join("\n", entry.getValue()) + "\n";
                        writer.writeEntry(entry.getKey(), file.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } else {
                if (Files.exists(rejectsPath.toPath())) {
                    Utils.deleteFolder(rejectsPath.toPath());
                }
                for (Map.Entry<String, List<String>> entry : rejectCollector.get().entrySet()) {
                    Path path = rejectsPath.toPath().resolve(entry.getKey());
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
