/**
 * FileLines: the lines of one file, kept as raw bytes.
 *
 * What it does:
 *   It takes all the bytes of a file and finds where every line starts and ends.
 *   It does NOT copy each line. The whole file stays in one array (`data`), and for
 *   every line we only remember two numbers:
 *       line i = data[start[i]] ... data[end[i] - 1]
 *   `end` is not included, and the '\n' itself is not part of the line.
 *
 * Rules from the assignment (section "Reading a file into lines"):
 *   1. split the content on the newline byte '\n'
 *   2. if the last piece is empty, drop it (so "a\n" is one line, and an empty file has no lines)
 *   3. keep any '\r' as part of the line ("a\r\n" gives the line "a\r")
 *   4. lines are compared as exact bytes
 *
 * Examples:
 *   ""          -> 0 lines
 *   "\n"        -> 1 line: ""          (start 0, end 0)
 *   "a"  "a\n"  -> 1 line: "a"
 *   "a\n\nb"    -> 3 lines: "a", "", "b"
 *   "a\r\nb\r\n"-> 2 lines: "a\r", "b\r"
 *
 * Why bytes and not Strings: the tests contain '\r' and bytes that are not valid UTF-8.
 * Reading as text would change or lose them. Why no copying: files can have 500,000 lines,
 * and copying every line would cost extra time and memory (the limit is 512 MiB).
 */
public class FileLines {

    public final byte[] data;   // all bytes of the file, exactly as on disk
    public final int[] start;   // start[i] = index in data where line i begins
    public final int[] end;     // end[i]   = index in data just after line i (the '\n' is not included)
    public final int count;     // number of lines

    /**
     * Splits the bytes into lines in two passes.
     * Pass 1 counts the lines, so we know how big the arrays must be.
     * Pass 2 fills start[] and end[]. Using two passes means we can use plain int arrays
     * (no ArrayList, no resizing).
     */
    public FileLines(byte[] data) {
        this.data = data;

        // First pass: count the '\n' bytes. Each '\n' ends one line.
        int newlines = 0;
        for (byte value : data) {
            if (value == '\n') {
                newlines++;
            }
        }
        // If the file does not end with '\n', the text after the last '\n' is one more line.
        // (If it ends with '\n', the last piece is empty and is dropped, as the rules say.)
        boolean lastPieceNotEmpty = data.length > 0 && data[data.length - 1] != '\n';
        count = newlines + (lastPieceNotEmpty ? 1 : 0);

        // Second pass: remember where each line starts and ends
        start = new int[count];
        end = new int[count];
        int line = 0;        // which line we are filling in
        int lineStart = 0;   // where the current line began
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                start[line] = lineStart;   // the line runs from lineStart ...
                end[line] = i;             // ... up to (not including) this '\n'
                line++;
                lineStart = i + 1;         // the next line begins after the '\n'
            }
        }
        // The last line has no '\n' after it, so the loop above did not save it.
        if (lastPieceNotEmpty) {
            start[line] = lineStart;
            end[line] = data.length;
        }
    }

    /** Length of line i in bytes (the '\n' is not counted, a '\r' is). */
    public int length(int i) {
        return end[i] - start[i];
    }
}
