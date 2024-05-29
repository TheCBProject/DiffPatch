package io.codechicken.diffpatch.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LineRange {

    private int start;
    private int end;

    public LineRange() {
    }

    public LineRange(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public boolean contains(int i) {
        return start <= i && i < end;
    }

    public boolean contains(LineRange r) {
        return r.start >= start && r.end <= end;
    }

    public boolean intersects(LineRange r) {
        return r.start < end || r.end > start;
    }

    public LineRange add(int i) {
        return new LineRange(start + i, end + i);
    }

    public LineRange sub(int i) {
        return new LineRange(start - i, end - i);
    }

    public List<LineRange> except(List<LineRange> except) {
        return except(except, false);
    }

    public List<LineRange> except(List<LineRange> except, boolean presorted) {
        if (!presorted) {
            except = new ArrayList<>(except);
            except.sort(Comparator.comparingInt(e -> e.start));
        }
        List<LineRange> ret = new ArrayList<>();
        int start = this.start;
        for (LineRange r : except) {
            if (r.start - start > 0) {
                ret.add(new LineRange(start, r.start));
            }
            start = r.end;
        }
        if (this.end - start > 0) {
            ret.add(new LineRange(start, end));
        }
        return ret;
    }

    //@formatter:off
    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }
    public int getEnd() { return end; }
    public void setEnd(int end) { this.end = end; }
    public int getLength() { return end - start; }
    public void setLength(int len) { this.end = start + len; }
    public int getLast() { return end - 1; }
    public void setLast(int last) { this.end = last + 1; }
    public int getFirst() { return getStart(); }
    public void setFirst(int first) { setStart(first); }
    //@formatter:on

    public static LineRange fromFirstLast(int first, int last) {
        LineRange range = new LineRange();
        range.setFirst(first);
        range.setLast(last);
        return range;
    }

    public static LineRange fromStartLen(int start, int len) {
        LineRange range = new LineRange();
        range.setStart(start);
        range.setLength(len);
        return range;
    }

    public static LineRange union(LineRange r1, LineRange r2) {
        return new LineRange(Math.min(r1.start, r2.start), Math.max(r1.end, r2.end));
    }

    public static LineRange intersection(LineRange r1, LineRange r2) {
        return new LineRange(Math.max(r1.start, r2.start), Math.min(r1.end, r2.end));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LineRange lineRange = (LineRange) o;

        if (getStart() != lineRange.getStart()) {
            return false;
        }
        return getEnd() == lineRange.getEnd();
    }

    @Override
    public int hashCode() {
        int result = getStart();
        result = 31 * result + getEnd();
        return result;
    }

    @Override
    public String toString() {
        return "[" + start + "," + end + ")";
    }
}
