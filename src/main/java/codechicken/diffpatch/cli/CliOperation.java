package codechicken.diffpatch.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Consumer;

/**
 * Created by covers1624 on 11/8/20.
 */
public abstract class CliOperation {

    protected final PrintStream logger;

    private final Consumer<PrintStream> helpCallback;
    protected boolean verbose;

    protected CliOperation(PrintStream logger, Consumer<PrintStream> helpCallback, boolean verbose) {
        this.logger = logger;
        this.helpCallback = helpCallback;
        this.verbose = verbose;
    }

    public abstract int operate() throws IOException;

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

}
