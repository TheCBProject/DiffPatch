package codechicken.diffpatch.util;

/**
 * Created by covers1624 on 17/7/23.
 */
public enum LogLevel {
    OFF,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    ALL;

    public boolean shouldLog(LogLevel required) {
        return ordinal() >= required.ordinal();
    }
}
