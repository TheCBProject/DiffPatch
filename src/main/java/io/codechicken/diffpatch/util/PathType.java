package io.codechicken.diffpatch.util;

/**
 * Created by covers1624 on 25/8/20.
 */
public enum PathType {
    PIPE,
    PATH,
    NULL;

    public boolean isPipe() {
        return this == PIPE;
    }

    public boolean isPath() {
        return this == PATH;
    }

    public boolean isNull() {
        return this == NULL;
    }
}
