package io.codechicken.diffpatch.util;

/**
 * Created by covers1624 on 17/7/23.
 */
public enum LogLevel {
    OFF(true, false),
    ERROR(true, false),
    WARN(true, false),
    INFO(false, false),
    DEBUG(true, true),
    ALL(true, true);

    public final boolean printLevelName;
    public final boolean printAllLevelNames;

    LogLevel(boolean printLevelName, boolean printAllLevelNames) {
        this.printLevelName = printLevelName;
        this.printAllLevelNames = printAllLevelNames;
    }

    public boolean shouldLog(LogLevel required) {
        return ordinal() >= required.ordinal();
    }
}
