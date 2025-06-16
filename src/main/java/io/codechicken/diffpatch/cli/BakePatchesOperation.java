package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.util.*;
import io.codechicken.diffpatch.util.Input.MultiInput;
import io.codechicken.diffpatch.util.Input.SingleInput;
import io.codechicken.diffpatch.util.Output.MultiOutput;
import io.codechicken.diffpatch.util.Output.SingleOutput;
import net.covers1624.quack.io.NullOutputStream;
import net.covers1624.quack.util.SneakyUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static io.codechicken.diffpatch.util.LogLevel.ERROR;

/**
 * Created by covers1624 on 6/16/25.
 */
public class BakePatchesOperation extends CliOperation<BakePatchesOperation.BakeSummary> {

    final boolean summary;
    final Input patchesInput;
    final Output bakedOutput;
    final String patchesPrefix;
    final String lineEnding;

    private BakePatchesOperation(PrintStream logger, LogLevel level, Consumer<PrintStream> helpCallback, boolean summary, Input patchesInput, Output bakedOutput, String patchesPrefix, String lineEnding) {
        super(logger, level, helpCallback);
        this.summary = summary;
        this.patchesInput = patchesInput;
        this.bakedOutput = bakedOutput;
        this.patchesPrefix = patchesPrefix;
        this.lineEnding = lineEnding;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Result<BakeSummary> operate() throws IOException {
        try {
            patchesInput.validate("bake input");
        } catch (IOValidationException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        if (patchesInput instanceof SingleInput) {
            SingleInput input = (SingleInput) patchesInput;
            if (!(bakedOutput instanceof SingleOutput)) {
                log(ERROR, "Can't specify baked output directory or archive when baking a single file.");
                printHelp();
                return new Result<>(-1);
            }
            SingleOutput output = (SingleOutput) bakedOutput;
            try (OutputStream os = output.open()) {
                os.write(bakePatch(input.name(), input.readLines(), lineEnding).getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            return new Result<>(0, new BakeSummary());
        }

        if (!(patchesInput instanceof MultiInput)) {
            log(ERROR, "Can't patch between single files and folders/archives.");
            printHelp();
            return new Result<>(-1);
        }

        try (MultiInput in = (MultiInput) patchesInput;
             MultiOutput out = (MultiOutput) bakedOutput) {
            in.open(patchesPrefix);
            out.open(true);
            for (String file : in.index()) {
                out.write(file, bakePatch(file, in.readLines(file), lineEnding).getBytes(StandardCharsets.UTF_8));
            }
        }
        return new Result<>(0, new BakeSummary());
    }

    private static String bakePatch(String fileName, List<String> lines, String lineEnding) {
        PatchFile patch = PatchFile.fromLines(fileName, lines, true);
        String baked = String.join(lineEnding, patch.toLines(false));
        return baked + lineEnding;
    }

    public static class BakeSummary {
    }

    public static class Builder {

        private static final PrintStream NULL_STREAM = new PrintStream(NullOutputStream.INSTANCE);

        private PrintStream logger = NULL_STREAM;
        private Consumer<PrintStream> helpCallback = SneakyUtils.nullCons();
        private LogLevel level = LogLevel.WARN;
        private boolean summary;
        private @Nullable Input patchesInput;
        private @Nullable Output bakedOutput;
        private String patchesPrefix = "";
        private String lineEnding = System.lineSeparator();

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

        public Builder patchesInput(Input patchesInput) {
            this.patchesInput = Objects.requireNonNull(patchesInput);
            return this;
        }

        public Builder bakedOutput(Output bakedOutput) {
            this.bakedOutput = Objects.requireNonNull(bakedOutput);
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

        public BakePatchesOperation build() {
            if (patchesInput == null) throw new IllegalStateException("patchesInput is required.");
            if (bakedOutput == null) throw new IllegalStateException("bakedOutput is required.");

            return new BakePatchesOperation(logger, level, helpCallback, summary, patchesInput, bakedOutput, patchesPrefix, lineEnding);
        }
    }
}
