package codechicken.diffpatch;

import codechicken.diffpatch.cli.*;
import codechicken.diffpatch.diff.Differ;
import codechicken.diffpatch.match.FuzzyLineMatcher;
import codechicken.diffpatch.util.InputPath;
import codechicken.diffpatch.util.OutputPath;
import codechicken.diffpatch.util.PatchMode;
import codechicken.diffpatch.util.Utils;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;

import java.io.IOException;
import java.io.PrintStream;
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
        OptionSpec<Void> verboseOpt = parser.acceptsAll(asList("v", "verbose"), "Prints more stuff.");
        OptionSpec<Void> summaryOpt = parser.acceptsAll(asList("s", "summary"), "Prints a changes summary at the end.");

        //Diff specific
        OptionSpec<Void> doDiffOpt = parser.acceptsAll(asList("d", "diff"), "Does a Diff operation.");
        OptionSpec<Void> autoHeaderOpt = parser.acceptsAll(asList("h", "auto-header"), "Enables the generation of auto-headers. Using _ as the start2 index.")//
                .availableIf(doDiffOpt);
        OptionSpec<Integer> contextOpt = parser.acceptsAll(asList("c", "context"), "Number of context lines to generate in diffs.")//
                .availableIf(doDiffOpt)//
                .withRequiredArg()//
                .ofType(Integer.class)//
                .defaultsTo(Differ.DEFAULT_CONTEXT);

        //Shared
        OptionSpec<Path> outputOpt = parser.acceptsAll(asList("o", "output"), "Sets the output path.")//
                .withRequiredArg()//
                .withValuesConvertedBy(new PathConverter());
        OptionSpec<ArchiveFormat> archiveOpt = parser.acceptsAll(asList("A", "archive"), "Treat output as an archive. Allows printing multi-output to STDOUT.")//
                .withRequiredArg()//
                .withValuesConvertedBy(new ArchiveFormatValueConverter());

        //Patch specific
        OptionSpec<Void> doPatchOpt = parser.acceptsAll(asList("p", "patch"), "Does a Patch operation.");
        OptionSpec<Path> rejectOpt = parser.acceptsAll(asList("r", "reject"), "Saves patch rejects to the specified path / archive")//
                .availableIf(doPatchOpt)//
                .withRequiredArg()//
                .withValuesConvertedBy(new PathConverter());
        OptionSpec<ArchiveFormat> rejectArchiveOpt = parser.acceptsAll(asList("H", "archive-rejects"), "Treat reject output as an archive.")//
                .availableIf(doPatchOpt)//
                .withRequiredArg()//
                .withValuesConvertedBy(new ArchiveFormatValueConverter());
        OptionSpec<Float> fuzzOpt = parser.acceptsAll(asList("f", "fuzz"), "The minimum fuzz match quality, anything lower will be treated as a failure.")//
                .availableIf(doPatchOpt)//
                .withRequiredArg()//
                .ofType(Float.class)//
                .defaultsTo(FuzzyLineMatcher.DEFAULT_MIN_MATCH_SCORE);
        OptionSpec<Integer> offsetOpt = parser.acceptsAll(asList("O", "offset"), "The max line offset allowed for fuzzy matching, larger than this will be treated as a failure.")//
                .availableIf(doPatchOpt)//
                .withRequiredArg()//
                .ofType(Integer.class)//
                .defaultsTo(FuzzyLineMatcher.MatchMatrix.DEFAULT_MAX_OFFSET);
        OptionSpec<PatchMode> modeOpt = parser.acceptsAll(asList("m", "mode"), "The desired patching mode.")//
                .availableIf(doPatchOpt)//
                .withRequiredArg()//
                .withValuesConvertedBy(new PatchModeValueConverter())//
                .defaultsTo(PatchMode.EXACT);
        OptionSpec<String> patchPrefix = parser.acceptsAll(asList("P", "prefix"), "Prefix path for reading patches from patches input.")//
                .availableIf(doPatchOpt)//
                .withRequiredArg()//
                .ofType(String.class)//
                .defaultsTo("");

        OptionSet optSet = parser.parse(args);
        if (optSet.has(helpOpt)) {
            parser.printHelpOn(logger);
            return -1;
        }
        boolean verbose = optSet.has(verboseOpt);
        boolean summary = optSet.has(summaryOpt);
        List<String> arguments = optSet.valuesOf(nonOptions);

        if (arguments.size() != 2) {
            logger.println("Expected 2 arguments, got: " + arguments.size());
            parser.printHelpOn(logger);
            return -1;
        }

        CliOperation<?> operation;
        if (optSet.has(doDiffOpt)) {

            Path a = Paths.get(arguments.get(0));
            Path b = Paths.get(arguments.get(1));
            Path output = optSet.valueOf(outputOpt);

            ArchiveFormat outputFormat = optSet.valueOf(archiveOpt);

            if (outputFormat == null && output != null) {
                outputFormat = ArchiveFormat.findFormat(output.getFileName());
            }

            OutputPath outputPath;
            if (output != null) {
                outputPath = new OutputPath.FilePath(output, outputFormat);
            } else {
                outputPath = new OutputPath.PipePath(pipe, outputFormat);
            }

            operation = DiffOperation.builder()
                    .logTo(logger)
                    .helpCallback(Utils.sneakC(parser::printHelpOn))
                    .aPath(a, ArchiveFormat.findFormat(a.getFileName()))
                    .bPath(b, ArchiveFormat.findFormat(b.getFileName()))
                    .outputPath(outputPath)
                    .verbose(verbose)
                    .summary(summary)
                    .autoHeader(optSet.has(autoHeaderOpt))
                    .context(optSet.valueOf(contextOpt))
                    .build();
        } else if (optSet.has(doPatchOpt)) {

            Path base = Paths.get(arguments.get(0));
            Path patches = Paths.get(arguments.get(1));
            Path output = optSet.valueOf(outputOpt);
            Path rejects = optSet.valueOf(rejectOpt);
            ArchiveFormat outputFormat = optSet.valueOf(archiveOpt);
            ArchiveFormat rejectsFormat = optSet.valueOf(rejectArchiveOpt);

            if (outputFormat == null && output != null) {
                outputFormat = ArchiveFormat.findFormat(output.getFileName());
            }

            if (rejectsFormat == null && rejects != null) {
                rejectsFormat = ArchiveFormat.findFormat(rejects.getFileName());
            }

            InputPath basePath = new InputPath.FilePath(base, ArchiveFormat.findFormat(base.getFileName()));
            InputPath patchesPath = new InputPath.FilePath(patches, ArchiveFormat.findFormat(patches.getFileName()));
            OutputPath outputPath;
            if (output != null) {
                outputPath = new OutputPath.FilePath(output, outputFormat);
            } else {
                outputPath = new OutputPath.PipePath(pipe, outputFormat);
            }
            OutputPath rejectsPath = new OutputPath.FilePath(rejects, rejectsFormat);

            operation = PatchOperation.builder()
                    .logTo(logger)
                    .helpCallback(Utils.sneakC(parser::printHelpOn))
                    .verbose(verbose)
                    .summary(summary)
                    .basePath(basePath)
                    .patchesPath(patchesPath)
                    .outputPath(outputPath)
                    .rejectsPath(rejectsPath)
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
}
