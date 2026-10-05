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
