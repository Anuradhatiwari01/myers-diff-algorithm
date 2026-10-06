/**
 * The lines of a file, kept as raw bytes.
 *
 * Rules from the assignment:
 *   1. split the content on the newline byte '\n'
 *   2. if the last piece is empty, drop it (so "a\n" is one line, and an empty file has no lines)
 *   3. keep any '\r' as part of the line
 *   4. lines are compared as exact bytes
 *
 * We do not copy every line into its own array. We keep the whole file in `data`
 * and remember where each line starts and ends:
 *   line i = data[start[i]] ... data[end[i] - 1]     (end is not included, the '\n' is not part of it)
 */
public class FileLines {

    public final byte[] data;
    public final int[] start;
    public final int[] end;
    public final int count;     // number of lines

    public FileLines(byte[] data) {
        this.data = data;

        // First pass: count the lines
        int newlines = 0;
        for (byte value : data) {
            if (value == '\n') {
                newlines++;
            }
        }
        boolean lastPieceNotEmpty = data.length > 0 && data[data.length - 1] != '\n';
        count = newlines + (lastPieceNotEmpty ? 1 : 0);

        // Second pass: remember where each line starts and ends
        start = new int[count];
        end = new int[count];
        int line = 0;
        int lineStart = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                start[line] = lineStart;
                end[line] = i;
                line++;
                lineStart = i + 1;
            }
        }
        if (lastPieceNotEmpty) {
            start[line] = lineStart;
            end[line] = data.length;
        }
    }

    /** Length of line i in bytes. */
    public int length(int i) {
        return end[i] - start[i];
    }
}
