package testdata;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a singular Patch file.
 */
public class PatchFile {

    private static final Pattern HUNK_OFFSET = Pattern.compile("@@ -(\\d+),(\\d+) \\+([_\\d]+),(\\d+) @@");

    public String name;
    public String basePath;
    public String patchedPath;

    public List<Patch> patches = new ArrayList<>();

    public static PatchFile fromLines(String name, List<String> lines, boolean verifyHeaders) {
        PatchFile patchFile = new PatchFile();
        patchFile.name = name;
        Patch patch = null;
        int delta = 0;
        int i = 0;
        for (String line : lines) {
            i++;

            //ignore blank lines
            if (line.isEmpty()) {
                continue;
            }

            //context
            if (patch == null && line.charAt(0) != '@') {
                if (i == 1 && line.startsWith("--- ")) {
                    patchFile.basePath = line.substring(4);
                } else if (i == 2) {
                    patchFile.patchedPath = line.substring(4);
                } else {
                    throw new IllegalArgumentException(String.format("Invalid context line %s:'%s'", i, line));
                }
                continue;
            }

            switch (line.charAt(0)) {
                case '@': {
                    Matcher matcher = HUNK_OFFSET.matcher(line);
                    if (!matcher.find()) {
                        throw new IllegalArgumentException(String.format("Invalid patch line %s:'%s'", i, line));
                    }
                    patch = new Patch();
                    patch.start1 = Integer.parseInt(matcher.group(1)) - 1;
                    patch.length1 = Integer.parseInt(matcher.group(2));
                    patch.length2 = Integer.parseInt(matcher.group(4));

                    String start2Str = matcher.group(3);
                    if (start2Str.equals("_")) {
                        patch.start2 = patch.start1 + delta;
                    } else {
                        patch.start2 = Integer.parseInt(start2Str) - 1;
                        if (verifyHeaders && patch.start2 != patch.start1 + delta) {
                            throw new IllegalArgumentException(String.format("Applied Offset Mismatch. Expected: %d, Actual: %d", patch.start1 + delta + 1, patch.start2 + 1));
                        }
                    }
                    delta += patch.length2 - patch.length1;
                    patchFile.patches.add(patch);
                    break;
                }
                case ' ':
                    patch.diffs.add(new Diff(Operation.EQUAL, line.substring(1)));
                    break;
                case '+':
                    patch.diffs.add(new Diff(Operation.INSERT, line.substring(1)));
                    break;
                case '-':
                    patch.diffs.add(new Diff(Operation.DELETE, line.substring(1)));
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Invalid patch line %s:'%s'", i, line));
            }
        }
        return patchFile;
    }

    @Override
    public String toString() {
        return String.join("\n", toLines(false));
    }

    public List<String> toLines(boolean autoHeader) {
        List<String> lines = new ArrayList<>();
        if (basePath != null && patchedPath != null) {
            lines.add("--- " + basePath);
            lines.add("+++ " + patchedPath);
        }

        for (Patch p : patches) {
            lines.add(autoHeader ? p.getAutoHeader() : p.getHeader());
            for (Diff diff : p.diffs) {
                lines.add(diff.toString());
            }
        }
        return lines;
    }

}
