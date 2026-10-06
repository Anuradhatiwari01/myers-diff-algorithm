import java.nio.charset.StandardCharsets;

/**
 * Part B: which characters changed inside one pair of lines.
 *
 * This is Myers again, "one level down": the items are now the characters of the two lines.
 * Characters are Unicode code points, so an emoji counts as one character.
 *
 * Result line:   ? <old ranges> | <new ranges>
 * Example: "  port = 8000" and "  port = 8080"  ->  "? 12-13 | 11-12"
 */
public class Highlight {

    /** Builds the "? ... | ..." line (without the final newline) for one pair of lines. */
    public static String rangesLine(byte[] oldData, int oldStart, int oldLength,
                                    byte[] newData, int newStart, int newLength) {
        // In highlight tests both files are valid UTF-8. A '\r' stays in and counts as a character.
        int[] oldChars = new String(oldData, oldStart, oldLength, StandardCharsets.UTF_8).codePoints().toArray();
        int[] newChars = new String(newData, newStart, newLength, StandardCharsets.UTF_8).codePoints().toArray();

        boolean[] deleted = new boolean[oldChars.length];
        boolean[] inserted = new boolean[newChars.length];
        Myers.diff(oldChars, newChars, deleted, inserted);

        return "? " + ranges(deleted) + " | " + ranges(inserted);
    }

    /**
     * Turns marks into ranges. Each run of marked characters becomes "start-end" (end not included).
     * Example: marks at positions 3,4 and 9,10,11  ->  "3-5,9-12".   No marks  ->  "."
     * A run is always taken whole, so touching ranges are merged automatically.
     */
    static String ranges(boolean[] marked) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < marked.length) {
            if (!marked[i]) {
                i++;
                continue;
            }
            int runStart = i;
            while (i < marked.length && marked[i]) {
                i++;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(runStart).append('-').append(i);
        }
        return sb.length() == 0 ? "." : sb.toString();
    }
}
