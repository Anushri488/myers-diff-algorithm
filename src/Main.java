import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Main {

    /*
     * ------------------------------------------------------------
     * Line
     * ------------------------------------------------------------
     *
     * A Line does NOT copy the bytes.
     * It points to a section of the original file byte[].
     */
    static class Line {
        byte[] data;
        int start;
        int end;

        Line(byte[] data, int start, int end) {
            this.data = data;
            this.start = start;
            this.end = end;
        }

        int length() {
            return end - start;
        }
    }

    /*
     * ------------------------------------------------------------
     * Read a file as RAW BYTES and split it on '\n'
     * ------------------------------------------------------------
     *
     * '\r' is NOT removed.
     * A final '\n' does not create an extra empty line.
     */
    static Line[] readLines(String fileName) throws IOException {

        byte[] data = Files.readAllBytes(Path.of(fileName));

        List<Line> lines = new ArrayList<>();

        int start = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                lines.add(new Line(data, start, i));
                start = i + 1;
            }
        }

        if (start < data.length) {
            lines.add(new Line(data, start, data.length));
        }

        return lines.toArray(new Line[0]);
    }
        /*
     * ------------------------------------------------------------
     * Give every distinct line a number
     * ------------------------------------------------------------
     *
     * Two lines get the same number exactly when their bytes are
     * equal. Myers then compares ints instead of byte arrays.
     *
     * ISO_8859_1 maps each byte to one char, so the String key
     * keeps the exact bytes, even when they are not valid UTF-8.
     */
    static int[][] toIds(Line[] oldLines, Line[] newLines) {

        HashMap<String, Integer> idOf = new HashMap<>();

        int[] oldIds = new int[oldLines.length];
        int[] newIds = new int[newLines.length];

        for (int i = 0; i < oldLines.length; i++) {
            oldIds[i] = idFor(idOf, oldLines[i]);
        }

        for (int i = 0; i < newLines.length; i++) {
            newIds[i] = idFor(idOf, newLines[i]);
        }

        return new int[][] {oldIds, newIds};
    }

    static int idFor(HashMap<String, Integer> idOf, Line line) {

        String key = new String(
                line.data,
                line.start,
                line.length(),
                StandardCharsets.ISO_8859_1
        );

        Integer id = idOf.get(key);

        if (id == null) {
            id = idOf.size();
            idOf.put(key, id);
        }

        return id;
    }
        /*
     * ------------------------------------------------------------
     * Myers diff, linear space version
     * ------------------------------------------------------------
     *
     * Works on any int[] sequence:
     *   Part A: line ids
     *   Part B: Unicode code points
     *
     * Result:
     *   deleted[i]  == true  -> oldValues[i] is a '-' element
     *   inserted[j] == true  -> newValues[j] is a '+' element
     *   everything else is kept
     *
     * Instead of storing the V array for every d (which needs
     * O(D^2) memory), we search from both ends at once until the
     * two searches meet (the "middle snake"). That point is on a
     * shortest edit path, so we split there and solve both halves
     * the same way. Memory stays O(N + M).
     */
    static class MyersDiff {

        final int[] oldValues;
        final int[] newValues;

        final boolean[] deleted;
        final boolean[] inserted;

        /*
         * Split point found by middleSnake().
         */
        int splitX;
        int splitY;

        MyersDiff(int[] oldValues, int[] newValues) {

            this.oldValues = oldValues;
            this.newValues = newValues;

            deleted = new boolean[oldValues.length];
            inserted = new boolean[newValues.length];

            compare(0, oldValues.length, 0, newValues.length);
        }

        /*
         * Diff oldValues[oldLo..oldHi) against newValues[newLo..newHi).
         */
        void compare(int oldLo, int oldHi, int newLo, int newHi) {

            /*
             * Equal elements at the start are kept.
             */
            while (oldLo < oldHi
                    && newLo < newHi
                    && oldValues[oldLo] == newValues[newLo]) {
                oldLo++;
                newLo++;
            }

            /*
             * Equal elements at the end are kept.
             */
            while (oldLo < oldHi
                    && newLo < newHi
                    && oldValues[oldHi - 1] == newValues[newHi - 1]) {
                oldHi--;
                newHi--;
            }

            /*
             * Old side empty: everything left in new is inserted.
             */
            if (oldLo == oldHi) {
                for (int j = newLo; j < newHi; j++) {
                    inserted[j] = true;
                }
                return;
            }

            /*
             * New side empty: everything left in old is deleted.
             */
            if (newLo == newHi) {
                for (int i = oldLo; i < oldHi; i++) {
                    deleted[i] = true;
                }
                return;
            }

            if (!middleSnake(oldLo, oldHi, newLo, newHi)) {

                /*
                 * The two sides have nothing in common.
                 */
                for (int i = oldLo; i < oldHi; i++) {
                    deleted[i] = true;
                }
                for (int j = newLo; j < newHi; j++) {
                    inserted[j] = true;
                }
                return;
            }

            /*
             * Copy the split point into locals, because the
             * recursive calls overwrite splitX and splitY.
             */
            int x = splitX;
            int y = splitY;

            compare(oldLo, x, newLo, y);
            compare(x, oldHi, y, newHi);
        }

        /*
         * Find a point on a shortest edit path by running Myers
         * forwards from the top-left corner and backwards from the
         * bottom-right corner at the same time.
         *
         * Coordinates inside this method are relative:
         *   x = steps into old, y = steps into new
         *   n = old length,     m = new length
         *
         * forward[k]  = furthest x on diagonal k = x - y, from the start
         * backward[k] = furthest x on diagonal k, counted from the end
         *
         * Returns true and sets splitX, splitY when the searches meet.
         */
        boolean middleSnake(int oldLo, int oldHi, int newLo, int newHi) {

            int n = oldHi - oldLo;
            int m = newHi - newLo;

            int maxD = (n + m + 1) / 2;

            /*
             * Offset lets us access negative k:
             *   index = k + offset
             */
            int offset = maxD;
            int size = 2 * maxD + 2;

            int[] forward = new int[size];
            int[] backward = new int[size];

            /*
             * -1 means "diagonal not reached yet".
             */
            Arrays.fill(forward, -1);
            Arrays.fill(backward, -1);

            /*
             * Same trick as basic Myers: at d = 0 the "move down
             * from diagonal 1" rule starts at x = 0.
             */
            forward[offset + 1] = 0;
            backward[offset + 1] = 0;

            /*
             * delta is the diagonal that holds the end point.
             * If delta is odd, the paths can only meet while the
             * forward search is running; if even, while the
             * backward search is running.
             */
            int delta = n - m;
            boolean odd = (delta & 1) != 0;

            /*
             * Diagonals that have left the grid are skipped.
             */
            int forwardStart = 0;
            int forwardEnd = 0;
            int backwardStart = 0;
            int backwardEnd = 0;

            for (int d = 0; d < maxD; d++) {

                /*
                 * ---------- forward search, d edits ----------
                 */
                for (int k = -d + forwardStart; k <= d - forwardEnd; k += 2) {

                    int index = offset + k;

                    int x;

                    /*
                     * INSERT (move down from k + 1)
                     * or DELETE (move right from k - 1),
                     * whichever reaches further.
                     */
                    if (k == -d
                            || (k != d && forward[index - 1] < forward[index + 1])) {
                        x = forward[index + 1];
                    } else {
                        x = forward[index - 1] + 1;
                    }

                    int y = x - k;

                    /*
                     * Snake: follow equal elements diagonally.
                     */
                    while (x < n
                            && y < m
                            && oldValues[oldLo + x] == newValues[newLo + y]) {
                        x++;
                        y++;
                    }

                    forward[index] = x;

                    if (x > n) {
                        /*
                         * Ran off the right edge.
                         */
                        forwardEnd += 2;

                    } else if (y > m) {
                        /*
                         * Ran off the bottom edge.
                         */
                        forwardStart += 2;

                    } else if (odd) {

                        /*
                         * Forward diagonal k is backward diagonal
                         * delta - k. Have the two paths overlapped?
                         */
                        int backIndex = offset + delta - k;

                        if (backIndex >= 0
                                && backIndex < size
                                && backward[backIndex] != -1
                                && x >= n - backward[backIndex]) {

                            splitX = oldLo + x;
                            splitY = newLo + y;
                            return true;
                        }
                    }
                }

                /*
                 * ---------- backward search, d edits ----------
                 *
                 * Same steps, but comparing from the end of both
                 * sequences towards the start.
                 */
                for (int k = -d + backwardStart; k <= d - backwardEnd; k += 2) {

                    int index = offset + k;

                    int x;

                    if (k == -d
                            || (k != d && backward[index - 1] < backward[index + 1])) {
                        x = backward[index + 1];
                    } else {
                        x = backward[index - 1] + 1;
                    }

                    int y = x - k;

                    while (x < n
                            && y < m
                            && oldValues[oldHi - 1 - x] == newValues[newHi - 1 - y]) {
                        x++;
                        y++;
                    }

                    backward[index] = x;

                    if (x > n) {
                        backwardEnd += 2;

                    } else if (y > m) {
                        backwardStart += 2;

                    } else if (!odd) {

                        int forwardIndex = offset + delta - k;

                        if (forwardIndex >= 0
                                && forwardIndex < size
                                && forward[forwardIndex] != -1) {

                            int forwardX = forward[forwardIndex];
                            int forwardY = forwardX - (forwardIndex - offset);

                            if (forwardX >= n - x) {
                                splitX = oldLo + forwardX;
                                splitY = newLo + forwardY;
                                return true;
                            }
                        }
                    }
                }
            }

            return false;
        }
    }
        /*
     * ------------------------------------------------------------
     * Write one raw line: prefix, the exact bytes, '\n'
     * ------------------------------------------------------------
     */
    static void writeLine(OutputStream output, Line line, byte prefix) throws IOException {
        output.write(prefix);
        output.write(line.data, line.start, line.length());
        output.write('\n');
    }

    /*
     * ------------------------------------------------------------
     * Print the diff (Part A)
     * ------------------------------------------------------------
     *
     * Walk through both files together. A position that is neither
     * deleted nor inserted is a keep line. Otherwise we are in a
     * change block: print all its '-' lines, then all its '+' lines
     * (delete-first rule).
     */
    static void printDiff(
            Line[] oldLines,
            Line[] newLines,
            MyersDiff diff,
            OutputStream output) throws IOException {

        int i = 0;
        int j = 0;

        while (i < oldLines.length || j < newLines.length) {

            boolean oldChanged = i < oldLines.length && diff.deleted[i];
            boolean newChanged = j < newLines.length && diff.inserted[j];

            if (!oldChanged && !newChanged) {

                /*
                 * Keep line.
                 */
                writeLine(output, oldLines[i], (byte) ' ');
                i++;
                j++;
                continue;
            }

            /*
             * One change block.
             */
            int deleteStart = i;
            while (i < oldLines.length && diff.deleted[i]) {
                i++;
            }

            int insertStart = j;
            while (j < newLines.length && diff.inserted[j]) {
                j++;
            }

            for (int del = deleteStart; del < i; del++) {
                writeLine(output, oldLines[del], (byte) '-');
            }

            for (int ins = insertStart; ins < j; ins++) {
                writeLine(output, newLines[ins], (byte) '+');
            }
        }
    }



        /*
     * ------------------------------------------------------------
     * Main
     * ------------------------------------------------------------
     */
    public static void main(String[] args) {

        boolean known = args.length == 3
                && (args[0].equals("lines") || args[0].equals("highlight"));

        if (!known) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }

        String oldFileName = args[1];
        String newFileName = args[2];

        Line[] oldLines;
        Line[] newLines;

        try {
            oldLines = readLines(oldFileName);
            newLines = readLines(newFileName);
        } catch (IOException exception) {

            /*
             * Nothing on stdout, message on stderr, exit code 2.
             */
            System.err.println("error: cannot read file: " + exception.getMessage());
            System.exit(2);
            return;
        }

        int[][] ids = toIds(oldLines, newLines);

        MyersDiff diff = new MyersDiff(ids[0], ids[1]);

        try {
            OutputStream output = new BufferedOutputStream(System.out, 1 << 16);

            printDiff(oldLines, newLines, diff, output);

            output.flush();
        } catch (IOException exception) {
            System.err.println("error: cannot write output: " + exception.getMessage());
            System.exit(1);
        }
    }
}

