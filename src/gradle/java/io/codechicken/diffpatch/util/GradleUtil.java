package io.codechicken.diffpatch.util;

import io.codechicken.diffpatch.util.archiver.ArchiveFormat;
import groovy.lang.Closure;
import org.gradle.api.Project;

import java.io.File;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Created by covers1624 on 29/11/20.
 */
public class GradleUtil {

    public static File resolveFile(Project project, Object obj) {
        if (obj instanceof Closure<?>) {
            return resolveFile(project, ((Closure<?>) obj).call());
        } else if (obj instanceof Supplier<?>) {
            return resolveFile(project, ((Supplier<?>) obj).get());
        } else {
            return project.file(obj);
        }
    }

    public static PatchMode resolvePatchMode(Object value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (value instanceof PatchMode) {
            return (PatchMode) value;
        }
        if (value instanceof CharSequence) {
            String upper = value.toString().toUpperCase(Locale.ROOT);
            try {
                return PatchMode.valueOf(upper);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown PatchMode String value: " + value.toString());
            }
        }
        throw new IllegalArgumentException("Unable to parse PatchMode, Unknown value: " + value.toString());
    }

    public static ArchiveFormat resolveArchiveFormat(Object value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (value instanceof ArchiveFormat) {
            return (ArchiveFormat) value;
        }
        if (value instanceof CharSequence) {
            String upper = value.toString().toUpperCase(Locale.ROOT);
            try {
                return ArchiveFormat.valueOf(upper);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown ArchiveFormat String value: " + value.toString());
            }
        }
        throw new IllegalArgumentException("Unable to parse ArchiveFormat, Unknown value: " + value.toString());
    }
}
