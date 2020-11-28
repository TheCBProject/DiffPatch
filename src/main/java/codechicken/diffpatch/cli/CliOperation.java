package codechicken.diffpatch.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Consumer;

/**
 * Created by covers1624 on 11/8/20.
 */
public abstract class CliOperation<T> {

    protected final PrintStream logger;

    private final Consumer<PrintStream> helpCallback;
    protected boolean verbose;

    protected CliOperation(PrintStream logger, Consumer<PrintStream> helpCallback, boolean verbose) {
        this.logger = logger;
        this.helpCallback = helpCallback;
        this.verbose = verbose;
    }

    public abstract Result<T> operate() throws IOException;

    public final void printHelp() throws IOException {
        helpCallback.accept(logger);
    }

    public final void log(String str, Object... args) {
        logger.println(String.format(str, args));
    }

    public final void verbose(String str, Object... args) {
        if (verbose) {
            log(str, args);
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
