package codechicken.diffpatch.match;

import it.unimi.dsi.fastutil.ints.AbstractInt2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PatienceMatch {

    //working fields for matching
    private String chars1;
    private String chars2;
    private int[] unique1;
    private int[] unique2;
    private int[] matches;

    private void match(int start1, int end1, int start2, int end2) {
        // step 1: match up identical starting lines
        while (start1 < end1 && start2 < end2 && chars1.charAt(start1) == chars2.charAt(start2)) {
            matches[start1++] = start2++;
        }

        // step 2: match up identical ending lines
        while (start1 < end1 && start2 < end2 && chars1.charAt(end1 - 1) == chars2.charAt(end2 - 1)) {
            matches[--end1] = --end2;
        }

        if (start1 == end1 || start2 == end2 || //no lines on a side
                end1 - start1 + end2 - start2 <= 3)//either a 1-2 or 2-1 which would've been matched by steps 1 and 2
        {
            return;
        }

        // step 3: match up common unique lines
        boolean any = false;
        for (Int2IntMap.Entry entry : lcsUnique(start1, end1, start2, end2)) {
            int m1 = entry.getIntKey();
            int m2 = entry.getIntValue();
            matches[m1] = m2;
            any = true;

            //step 4: recurse
            match(start1, m1, start2, m2);

            start1 = m1 + 1;
            start2 = m2 + 1;
        }

        if (any) {
            match(start1, end1, start2, end2);
        }
    }

    private int[] match() {
        matches = new int[chars1.length()];
        for (int i = 0; i < chars1.length(); i++) {
            matches[i] = -1;
        }

        match(0, chars1.length(), 0, chars2.length());
        return matches;
    }

    public int[] match(String chars1, String chars2, int maxChar) {
        if (unique1 == null || unique1.length < maxChar) {
            unique1 = new int[maxChar];
            unique2 = new int[maxChar];
            for (int i = 0; i < maxChar; i++) {
                unique1[i] = unique2[i] = -1;
            }
        }

        this.chars1 = chars1;
        this.chars2 = chars2;

        return match();
    }

    private final IntList subChars = new IntArrayList();

    private List<Int2IntMap.Entry> lcsUnique(int start1, int end1, int start2, int end2) {
        //identify all the unique chars in chars1
        for (int i = start1; i < end1; i++) {
            int c = chars1.charAt(i);

            if (unique1[c] == -1) {//no lines
                unique1[c] = i;
                subChars.add(c);
            } else {
                unique1[c] = -2;//not unique
            }
        }

        //identify all the unique chars in chars2, provided they were unique in chars1
        for (int i = start2; i < end2; i++) {
            int c = chars2.charAt(i);
            if (unique1[c] < 0) {
                continue;
            }

            unique2[c] = unique2[c] == -1 ? i : -2;
        }

        //extract common unique subsequences
        IntList common1 = new IntArrayList();
        IntList common2 = new IntArrayList();
        for (int i : subChars) {
            if (unique1[i] >= 0 && unique2[i] >= 0) {
                common1.add(unique1[i]);
                common2.add(unique2[i]);
            }
            unique1[i] = unique2[i] = -1; //reset for next use
        }
        subChars.clear();

        if (common2.size() == 0) {
            return Collections.emptyList();
        }
        List<Int2IntMap.Entry> ret = new ArrayList<>();

        // repose the longest common subsequence as longest ascending subsequence
        // note that common2 is already sorted by order of appearance in file1 by of char allocation
        for (int i : lasIndices(common2)) {
            ret.add(new AbstractInt2IntMap.BasicEntry(common1.getInt(i), common2.getInt(i)));
        }
        return ret;
    }

    //https://en.wikipedia.org/wiki/Patience_sorting
    public static int[] lasIndices(IntList sequence) {
        if (sequence.size() == 0) {
            return new int[0];
        }

        List<LCANode> pileTops = new ArrayList<>();
        pileTops.add(new LCANode(0, null));
        for (int i = 1; i < sequence.size(); i++) {
            int v = sequence.getInt(i);

            //binary search for the first pileTop > v
            int a = 0;
            int b = pileTops.size();
            while (a != b) {
                int c = (a + b) / 2;
                if (sequence.getInt(pileTops.get(c).value) > v) {
                    b = c;
                } else {
                    a = c + 1;
                }
            }

            if (a < pileTops.size()) {
                pileTops.set(a, new LCANode(i, a > 0 ? pileTops.get(a - 1) : null));
            } else {
                pileTops.add(new LCANode(i, pileTops.get(a - 1)));
            }
        }

        //follow pointers back through path
        int[] las = new int[pileTops.size()];
        int j = pileTops.size() - 1;
        for (LCANode node = pileTops.get(j); node != null; node = node.prev) {
            las[j--] = node.value;
        }

        return las;
    }

    private static class LCANode {

        public final int value;
        public final LCANode prev;

        public LCANode(int value, LCANode prev) {
            this.value = value;
            this.prev = prev;
        }
    }

}
