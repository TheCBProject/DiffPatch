package io.codechicken.diffpatch.util;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;

/**
 * Created by covers1624 on 29/11/20.
 */
public class LoggingOutputStream extends ConsumingOutputStream {

    public LoggingOutputStream(Logger logger, LogLevel level) {
        super(e -> logger.log(level, e));
    }
}
