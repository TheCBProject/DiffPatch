package io.codechicken.diffpatch.util;

/**
 * Represents a single line Difference.
 */
public class Diff {

    public final Operation op;
    public String text;

    public Diff(Operation op, String text) {
        this.op = op;
        this.text = text;
    }

    public Diff(Diff other) {
        this(other.op, other.text);
    }

    @Override
    public String toString() {
        return op.getPrefix() + text;
    }
}
