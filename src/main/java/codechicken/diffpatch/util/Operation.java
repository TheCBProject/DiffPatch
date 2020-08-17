package codechicken.diffpatch.util;

/**
 * A patch operation.
 */
public enum Operation {
    DELETE("-"),
    INSERT("+"),
    EQUAL(" ");

    private final String prefix;

    Operation(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
