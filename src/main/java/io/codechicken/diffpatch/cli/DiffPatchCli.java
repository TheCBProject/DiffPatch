package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.diff.Differ;
import io.codechicken.diffpatch.match.FuzzyLineMatcher;
import io.codechicken.diffpatch.util.Input;
import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.LogLevel;
import io.codechicken.diffpatch.util.Output;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import io.codechicken.diffpatch.util.PatchMode;
import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.EnumConverter;
import joptsimple.util.PathConverter;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

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
public class DiffPatchCli {

    public static void main(String[] args) throws IOException {
        System.exit(mainI(args, System.err, System.out));
    }

    public static int mainI(String[] args, PrintStream logger, PrintStream pipe) throws IOException {
        CliOperation<?> operation = parseOperation(logger, pipe, args);
        if (operation == null) return -1;

        return operation.operate().exit;
    }

    @VisibleForTesting
    static @Nullable CliOperation<?> parseOperation(PrintStream logger, PrintStream pipe, String... args) throws IOException {
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
        OptionSpec<ArchiveFormat> outputArchiveOpt = parser.acceptsAll(asList("A", "archive"), "Treat output as an archive. Allows printing multi-output to STDOUT.")
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());
        OptionSpec<ArchiveFormat> baseArchiveOpt = parser.acceptsAll(asList("B", "archive-base"), "Treat the base path as an archive.")
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());
        OptionSpec<String> basePathPrefixOpt = parser.acceptsAll(asList("base-path-prefix"), "The prefix to assume for paths of base files.")
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("a/");
        OptionSpec<String> modifiedPathPrefixOpt = parser.acceptsAll(asList("modified-path-prefix"), "The prefix to assume for paths of modified files.")
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("b/");
        OptionSpec<LineEnding> lineEndingOpt = parser.acceptsAll(asList("line-endings"), "Set the Line Endings to use. Defaults to system line endings.")
                .withRequiredArg()
                .withValuesConvertedBy(new EnumConverter<LineEnding>(LineEnding.class) { })
                .defaultsTo(LineEnding.system());

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
        OptionSpec<ArchiveFormat> patchesArchiveOpt = parser.acceptsAll(asList("N", "archive-patches"), "Treat the patches path as an archive.")
                .availableIf(doPatchOpt)
                .withRequiredArg()
                .withValuesConvertedBy(new ArchiveFormatValueConverter());

        //Patch Baking specific
        OptionSpec<Void> doBakeOpt = parser.acceptsAll(asList("b", "bake"), "Bake the patches, removing auto-header and DiffPatch specific formats.");

        // Patch shared
        OptionSpec<String> patchPrefix = parser.acceptsAll(asList("P", "prefix"), "Prefix path for reading patches from patches input.")
                .availableIf(doPatchOpt, doBakeOpt)
                .withRequiredArg()
                .ofType(String.class)
                .defaultsTo("");

        OptionSet optSet = parser.parse(args);
        if (optSet.has(helpOpt)) {
            parser.printHelpOn(logger);
            return null;
        }

        LogLevel level = optSet.valueOf(logLevelOpt);
        if (level == null) {
            level = optSet.has(summaryOpt) ? LogLevel.DEBUG : LogLevel.INFO;
        }

        boolean summary = optSet.has(summaryOpt);
        LineEnding lineEnding = optSet.valueOf(lineEndingOpt);
        List<String> arguments = optSet.valuesOf(nonOptions);

        if (optSet.has(doDiffOpt)) {
            if (arguments.size() != 2) {
                logger.println("Expected 2 arguments, got: " + arguments.size());
                parser.printHelpOn(logger);
                return null;
            }

            Path aPath = Paths.get(arguments.get(0));
            Path bPath = Paths.get(arguments.get(1));
            Path outputPath = optSet.valueOf(outputOpt);

            return DiffOperation.builder()
                    .logTo(logger)
                    .helpCallback(SneakyUtils.<PrintStream>sneak(parser::printHelpOn))
                    .baseInput(getInput(
                            detectFormat(optSet.valueOf(baseArchiveOpt), aPath),
                            aPath
                    ))
                    .changedInput(getInput(
                            detectFormat(optSet.valueOf(modifiedArchiveOpt), bPath),
                            bPath
                    ))
                    .patchesOutput(getOutput(
                            detectFormat(optSet.valueOf(outputArchiveOpt), outputPath),
                            outputPath,
                            pipe
                    ))
                    .level(level)
                    .summary(summary)
                    .autoHeader(optSet.has(autoHeaderOpt))
                    .context(optSet.valueOf(contextOpt))
                    .aPrefix(optSet.valueOf(basePathPrefixOpt))
                    .bPrefix(optSet.valueOf(modifiedPathPrefixOpt))
                    .lineEnding(lineEnding.chars)
                    .build();
        }
        if (optSet.has(doPatchOpt)) {
            if (arguments.size() != 2) {
                logger.println("Expected 2 arguments, got: " + arguments.size());
                parser.printHelpOn(logger);
                return null;
            }
            Path base = Paths.get(arguments.get(0));
            Path patches = Paths.get(arguments.get(1));
            Path outputPath = optSet.valueOf(outputOpt);
            Path rejectsPath = optSet.valueOf(rejectOpt);

            return PatchOperation.builder()
                    .logTo(logger)
                    .helpCallback(SneakyUtils.<PrintStream>sneak(parser::printHelpOn))
                    .level(level)
                    .summary(summary)
                    .baseInput(getInput(
                            detectFormat(optSet.valueOf(baseArchiveOpt), base),
                            base
                    ))
                    .patchesInput(getInput(
                            detectFormat(optSet.valueOf(patchesArchiveOpt), patches),
                            patches
                    ))
                    .patchedOutput(getOutput(
                            detectFormat(optSet.valueOf(outputArchiveOpt), outputPath),
                            outputPath,
                            pipe
                    ))
                    .rejectsOutput(getOutput(
                            detectFormat(optSet.valueOf(rejectArchiveOpt), rejectsPath),
                            rejectsPath,
                            null
                    ))
                    .minFuzz(optSet.valueOf(fuzzOpt))
                    .maxOffset(optSet.valueOf(offsetOpt))
                    .mode(optSet.valueOf(modeOpt))
                    .patchesPrefix(optSet.valueOf(patchPrefix))
                    .aPrefix(optSet.valueOf(basePathPrefixOpt))
                    .bPrefix(optSet.valueOf(modifiedPathPrefixOpt))
                    .lineEnding(lineEnding.chars)
                    .build();
        }

        if (optSet.has(doBakeOpt)) {
            if (arguments.size() != 1) {
                logger.println("Expected one argument, got: " + arguments.size());
                parser.printHelpOn(logger);
                return null;
            }
            Path patchesPath = Paths.get(arguments.get(0));
            Path outputPath = optSet.valueOf(outputOpt);

            return BakePatchesOperation.builder()
                    .logTo(logger)
                    .helpCallback(SneakyUtils.<PrintStream>sneak(parser::printHelpOn))
                    .level(level)
                    .summary(summary)
                    .patchesInput(getInput(
                            detectFormat(optSet.valueOf(baseArchiveOpt), patchesPath),
                            patchesPath
                    ))
                    .bakedOutput(getOutput(
                            detectFormat(optSet.valueOf(outputArchiveOpt), outputPath),
                            outputPath,
                            pipe
                    ))
                    .patchesPrefix(optSet.valueOf(patchPrefix))
                    .lineEnding(lineEnding.chars)
                    .build();
        }

        logger.println("Expected --diff, --patch, or --bake.");
        parser.printHelpOn(logger);
        return null;
    }

    private static Input getInput(@Nullable ArchiveFormat format, Path path) {
        if (format != null) {
            return MultiInput.archive(format, path);
        }
        return Files.isDirectory(path) ? MultiInput.folder(path) : Input.SingleInput.path(path);
    }

    @Contract ("!null,!null,_->!null;!null,_,!null->!null")
    private static @Nullable Output getOutput(@Nullable ArchiveFormat outputFormat, @Nullable Path outputPath, @Nullable PrintStream pipe) {
        if (outputFormat != null) {
            if (outputPath != null) {
                return MultiOutput.archive(outputFormat, outputPath);
            }
            if (pipe != null) {
                return MultiOutput.archive(outputFormat, pipe);
            }
            return null;
        }
        if (outputPath != null) {
            return Files.isDirectory(outputPath) ? MultiOutput.folder(outputPath) : SingleOutput.path(outputPath);
        }
        if (pipe != null) {
            return SingleOutput.pipe(pipe);
        }
        return null;
    }

    private static @Nullable ArchiveFormat detectFormat(@Nullable ArchiveFormat existing, @Nullable Path detectFrom) {
        if (existing != null) return existing;
        if (detectFrom != null) return ArchiveFormat.findFormat(detectFrom.getFileName());

        return null;
    }

    public enum LineEnding {
        CR("\r"),
        LF("\n"),
        CRLF("\r\n");
        public final String chars;

        LineEnding(String chars) {
            this.chars = chars;
        }

        public static LineEnding system() {
            switch (System.lineSeparator()) {
                case "\r":
                    return CR;
                case "\r\n":
                    return CRLF;
                case "\n":
                default: // No idea, just return LF
                    return LF;
            }
        }
    }
}
