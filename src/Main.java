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
     * Temporary main: only checks that both files can be read.
     */
    public static void main(String[] args) {

        if (args.length != 3) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }

        try {
            Line[] oldLines = readLines(args[1]);
            Line[] newLines = readLines(args[2]);

            System.err.println("old: " + oldLines.length + " lines, new: "
                    + newLines.length + " lines");
        } catch (IOException exception) {
            System.err.println("error: cannot read file: " + exception.getMessage());
            System.exit(2);
        }
    }
}
