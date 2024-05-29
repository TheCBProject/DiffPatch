package io.codechicken.diffpatch.cli;

import io.codechicken.diffpatch.util.LogLevel;

import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Consumer;

/**
 * Created by covers1624 on 11/8/20.
 */
public abstract class CliOperation<T> {

    protected final PrintStream logger;
    protected final LogLevel level;

    private final Consumer<PrintStream> helpCallback;

    protected CliOperation(PrintStream logger, LogLevel level, Consumer<PrintStream> helpCallback) {
        this.logger = logger;
        this.level = level;
        this.helpCallback = helpCallback;
    }

    public abstract Result<T> operate() throws IOException;

    public final void printHelp() throws IOException {
        helpCallback.accept(logger);
    }

    public final void log(LogLevel level, String str, Object... args) {
        if (this.level.shouldLog(level)) {
            if (this.level.printAllLevelNames || level.printLevelName) {
                logger.print("[" + level + "] ");
            }
            logger.println(String.format(str, args));
        }
    }

    public static class Result<T> {

        public final int exit;
        public final T summary;

        public Result(int exit) {
            this(exit, null);
        }

        public Result(int exit, T summary) {
            this.exit = exit;
            this.summary = summary;
        }

        public void throwOnError() {
            if (exit != 0) {
                throw new RuntimeException("Operation has non zero exit code: " + exit);
            }
        }
    }

}
