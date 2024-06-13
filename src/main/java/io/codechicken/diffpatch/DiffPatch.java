package io.codechicken.diffpatch;

import io.codechicken.diffpatch.cli.*;
import io.codechicken.diffpatch.diff.Differ;
import io.codechicken.diffpatch.match.FuzzyLineMatcher;
import io.codechicken.diffpatch.util.*;
import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.EnumConverter;
import joptsimple.util.PathConverter;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Created by covers1624 on 16/7/20.
 */
public class DiffPatch {

    public static void main(String[] args) throws IOException {
        System.exit(mainI(args, System.err, System.out));
    }

    public static int mainI(String[] args, PrintStream logger, PrintStream pipe) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<String> nonOptions = parser.nonOptions();

        //Utility
        OptionSpec<Void> helpOpt = parser.acceptsAll(asList("h", "help"), "Prints this help.").forHelp();
        OptionSpec<Void> verboseOpt = parser.acceptsAll(asList("v", "verbose"), "Prints more stuff. Alias for --log-level ALL");
        OptionSpec<LogLevel> logLevelOpt = parser.acceptsAll(asList("l", "log-level"), "Set the Logging level.")
                .availableUnless(verboseOpt)
                .withRequiredArg()
                .withValuesConvertedBy(new EnumConverter<LogLevel>(LogLevel.class) { })
                .defaultsTo(LogLevel.INFO);
        OptionSpec<Void> summaryOpt = parser.acceptsAll(asList("s", "summary"), "Prints a changes summary at the end.");

        //Shared
        OptionSpec<Path> outputOpt = parser.acceptsAll(asList("o", "output"), "Sets the output path.")
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter());
        OptionSpec<ArchiveFormat> archiveOpt = parser.acceptsAll(asList("A", "archive"), "Treat output as an archive. Allows printing multi-output to STDOUT.")
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());
        OptionSpec<ArchiveFormat> baseArchiveOpt = parser.acceptsAll(asList("B", "archive-base"), "Treat the base path as an archive.")
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());

        //Diff specific
        OptionSpec<Void> doDiffOpt = parser.acceptsAll(asList("d", "diff"), "Does a Diff operation.");
        OptionSpec<Void> autoHeaderOpt = parser.acceptsAll(asList("h", "auto-header"), "Enables the generation of auto-headers. Using _ as the start2 index.")
                .availableIf(doDiffOpt);
        OptionSpec<Integer> contextOpt = parser.acceptsAll(asList("c", "context"), "Number of context lines to generate in diffs.")
                .availableIf(doDiffOpt)
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(Differ.DEFAULT_CONTEXT);
        OptionSpec<ArchiveFormat> modifiedArchiveOpt = parser.acceptsAll(asList("M", "archive-modified"), "Treat the modified path as an archive.")
                .availableIf(doDiffOpt)
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());

        //Patch specific
        OptionSpec<Void> doPatchOpt = parser.acceptsAll(asList("p", "patch"), "Does a Patch operation.");
        OptionSpec<Path> rejectOpt = parser.acceptsAll(asList("r", "reject"), "Saves patch rejects to the specified path / archive")
                .availableIf(doPatchOpt)
                .withRequiredArg()
                .withValuesConvertedBy(new PathConverter());
        OptionSpec<ArchiveFormat> rejectArchiveOpt = parser.acceptsAll(asList("H", "archive-rejects"), "Treat reject output as an archive.")
                .availableIf(doPatchOpt)
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());
        OptionSpec<Float> fuzzOpt = parser.acceptsAll(asList("f", "fuzz"), "The minimum fuzz match quality, anything lower will be treated as a failure.")
                .availableIf(doPatchOpt)
                .withRequiredArg()
                .ofType(Float.class)
                .defaultsTo(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE);
        OptionSpec<Integer> offsetOpt = parser.acceptsAll(asList("O", "offset"), "The max line offset allowed for fuzzy matching, larger than this will be treated as a failure.")
                .availableIf(doPatchOpt)
                .withRequiredArg()
                .ofType(Integer.class)
                .defaultsTo(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET);
        OptionSpec<PatchMode> modeOpt = parser.acceptsAll(asList("m", "mode"), "The desired patching mode.")
                .availableIf(doPatchOpt)
                .withRequiredArg()
                .withValuesConvertedBy(new PatchModeValueConverter())
                .defaultsTo(PatchMode.EXACT);
        OptionSpec<String> patchPrefix = parser.acceptsAll(asList("P", "prefix"), "Prefix path for reading patches from patches input.")
                .availableIf(doPatchOpt)
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("");
        OptionSpec<ArchiveFormat> patchesArchiveOpt = parser.acceptsAll(asList("M", "archive-patches"), "Treat the patches path as an archive.")
                .availableIf(doDiffOpt)
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());

        OptionSet optSet = parser.parse(args);
        if (optSet.has(helpOpt)) {
            parser.printHelpOn(logger);
            return -1;
        }

        LogLevel level = optSet.valueOf(logLevelOpt);
        if (level == null) {
            level = optSet.has(summaryOpt) ? LogLevel.DEBUG : LogLevel.INFO;
        }

        boolean summary = optSet.has(summaryOpt);
        List<String> arguments = optSet.valuesOf(nonOptions);

        if (arguments.size() != 2) {
            logger.println("Expected 2 arguments, got: " + arguments.size());
            parser.printHelpOn(logger);
            return -1;
        }

        CliOperation<?> operation;
        if (optSet.has(doDiffOpt)) {
            Path aPath = Paths.get(arguments.get(0));
            Path bPath = Paths.get(arguments.get(1));
            Path outputPath = optSet.valueOf(outputOpt);

            ArchiveFormat aFormat = detectFormat(optSet.valueOf(baseArchiveOpt), aPath);
            ArchiveFormat bFormat = detectFormat(optSet.valueOf(modifiedArchiveOpt), bPath);
            ArchiveFormat outputFormat = detectFormat(optSet.valueOf(archiveOpt), outputPath);

            Output output;
            if (outputFormat != null) {
                output = outputPath != null ? MultiOutput.archive(outputFormat, outputPath) : MultiOutput.archive(outputFormat, pipe);
            } else if (outputPath != null) {
                output = Files.isDirectory(outputPath) ? MultiOutput.folder(outputPath) : SingleOutput.path(outputPath);
            } else {
                output = SingleOutput.pipe(pipe);
            }

            Input aInput;
            if (aFormat != null) {
                aInput = MultiInput.archive(aFormat, aPath);
            } else {
                aInput = Files.isDirectory(aPath) ? MultiInput.folder(aPath) : Input.SingleInput.path(aPath);
            }

            Input bInput;
            if (bFormat != null) {
                bInput = MultiInput.archive(bFormat, bPath);
            } else {
                bInput = Files.isDirectory(bPath) ? MultiInput.folder(bPath) : Input.SingleInput.path(bPath);
            }
            operation = DiffOperation.builder()
                    .logTo(logger)
                    .helpCallback(SneakyUtils.<PrintStream>sneak(parser::printHelpOn))
                    .aPath(aInput)
                    .bPath(bInput)
                    .outputPath(output)
                    .level(level)
                    .summary(summary)
                    .autoHeader(optSet.has(autoHeaderOpt))
                    .context(optSet.valueOf(contextOpt))
                    .build();
        } else if (optSet.has(doPatchOpt)) {
            Path base = Paths.get(arguments.get(0));
            Path patches = Paths.get(arguments.get(1));
            Path outputPath = optSet.valueOf(outputOpt);
            Path rejectsPath = optSet.valueOf(rejectOpt);
            ArchiveFormat baseFormat = detectFormat(optSet.valueOf(baseArchiveOpt), base);
            ArchiveFormat patchesFormat = detectFormat(optSet.valueOf(patchesArchiveOpt), patches);
            ArchiveFormat outputFormat = detectFormat(optSet.valueOf(archiveOpt), outputPath);
            ArchiveFormat rejectsFormat = detectFormat(optSet.valueOf(rejectArchiveOpt), rejectsPath);

            Input baseInput;
            if (baseFormat != null) {
                baseInput = MultiInput.archive(baseFormat, base);
            } else {
                baseInput = Files.isDirectory(base) ? MultiInput.folder(base) : Input.SingleInput.path(base);
            }
            Input patchesInput;
            if (patchesFormat != null) {
                patchesInput = MultiInput.archive(patchesFormat, patches);
            } else {
                patchesInput = Files.isDirectory(patches) ? MultiInput.folder(patches) : Input.SingleInput.path(patches);
            }
            Output output;
            if (outputFormat != null) {
                output = outputPath != null ? MultiOutput.archive(outputFormat, outputPath) : MultiOutput.archive(outputFormat, pipe);
            } else if (outputPath != null) {
                output = Files.isDirectory(outputPath) ? MultiOutput.folder(outputPath) : SingleOutput.path(outputPath);
            } else {
                output = SingleOutput.pipe(pipe);
            }
            Output rejects = null;
            if (rejectsPath != null) {
                if (rejectsFormat != null) {
                    rejects = MultiOutput.archive(rejectsFormat, rejectsPath);
                } else {
                    rejects = Files.isDirectory(rejectsPath) ? MultiOutput.folder(rejectsPath) : SingleOutput.path(rejectsPath);
                }
            }

            operation = PatchOperation.builder()
                    .logTo(logger)
                    .helpCallback(SneakyUtils.<PrintStream>sneak(parser::printHelpOn))
                    .level(level)
                    .summary(summary)
                    .basePath(baseInput)
                    .patchesPath(patchesInput)
                    .outputPath(output)
                    .rejectsPath(rejects)
                    .minFuzz(optSet.valueOf(fuzzOpt))
                    .maxOffset(optSet.valueOf(offsetOpt))
                    .mode(optSet.valueOf(modeOpt))
                    .patchesPrefix(optSet.valueOf(patchPrefix))
                    .build();
        } else {
            logger.println("Expected --diff or --patch.");
            parser.printHelpOn(logger);
            return -1;
        }
        return operation.operate().exit;
    }

    private static @Nullable ArchiveFormat detectFormat(@Nullable ArchiveFormat existing, @Nullable Path detectFrom) {
        if (existing != null) return existing;
        if (detectFrom != null) return ArchiveFormat.findFormat(detectFrom.getFileName());

        return null;
    }
}
