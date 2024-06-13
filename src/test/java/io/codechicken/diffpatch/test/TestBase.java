package io.codechicken.diffpatch.test;

import java.util.*;

/**
 * Created by covers1624 on 30/5/24.
 */
public abstract class TestBase {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static Map<String, List<String>> generateRandomFiles(Random randy) {
        Map<String, List<String>> files = new LinkedHashMap<>();

        int numFiles = 10 + randy.nextInt(100);
        for (int i = 0; i < numFiles; i++) {
            String fileName = randomFileNameAndPath(randy, 4) + ".txt";
            int numLines = 10 + randy.nextInt(100);
            List<String> lines = new ArrayList<>(numLines);
            for (int j = 0; j < numLines; j++) {
                lines.add(generateRandomHex(randy, 5 + randy.nextInt(100)));
            }
            files.put(fileName, lines);
        }

        return files;
    }

    private static String randomFileNameAndPath(Random randy, int segments) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            if (i != 0) {
                ret.append("/");
            }
            ret.append(generateRandomHex(randy, 1 + randy.nextInt(5)));
        }
        return ret.toString();
    }

    public static String generateRandomHex(Random randy, int len) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            builder.append(HEX[randy.nextInt(HEX.length)]);
        }
        return builder.toString();
    }
}
