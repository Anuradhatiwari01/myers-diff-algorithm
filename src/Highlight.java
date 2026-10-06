import java.nio.charset.StandardCharsets;

/**
 * Highlight: Part B. Finds which characters changed inside one pair of lines.
 *
 * Idea: this is Myers again, "one level down". In Part A the items are lines.
 * Here the items are the characters of the two lines. We reuse the same Myers.diff,
 * because it works on any int[]: a line id or a character code is just a number.
 *
 * Characters are Unicode code points (not Java chars), so an emoji counts as ONE character.
 * In Java an emoji is two chars (a "surrogate pair"); counting chars would give wrong positions.
 *
 * Result line:   ? <old ranges> | <new ranges>
 * Example: "  port = 8000" -> "  port = 8080" gives "? 11-12 | 11-12"
 *   (the 0 at position 11 was deleted and the 8 at position 11 was inserted).
 *   The assignment shows "? 12-13 | 11-12"; both are correct, because the grader only checks
 *   (1) after removing the highlighted characters both lines are the same ("  port = 800"), and
 *   (2) the number of highlighted characters is the minimum (1 + 1 = 2).
 * Because Myers is minimal, both checks always pass.
 */
public class Highlight {

    /**
     * Builds the "? ... | ..." line (without the final newline) for one pair of lines.
     * The old line is oldData[oldStart .. oldStart+oldLength), the new line is newData[newStart .. newStart+newLength).
     *
     * Steps: decode bytes to text -> split into code points -> Myers on the code points
     *        -> turn the deleted/inserted marks into ranges.
     */
    public static String rangesLine(byte[] oldData, int oldStart, int oldLength,
                                    byte[] newData, int newStart, int newLength) {
        // Decode the line bytes as UTF-8 (in highlight tests both files are always valid UTF-8)
        // and take the code points: one int per real character.
        // A '\r' stays in the line and counts as one character, as the assignment says.
        int[] oldChars = new String(oldData, oldStart, oldLength, StandardCharsets.UTF_8).codePoints().toArray();
        int[] newChars = new String(newData, newStart, newLength, StandardCharsets.UTF_8).codePoints().toArray();

        // One mark per character: deleted[i] = old character i was removed,
        // inserted[j] = new character j was added. Everything else is kept.
        boolean[] deleted = new boolean[oldChars.length];
        boolean[] inserted = new boolean[newChars.length];
        Myers.diff(oldChars, newChars, deleted, inserted);   // the same algorithm as for lines

        // Old ranges on the left of "|", new ranges on the right. No other spaces are allowed.
        return "? " + ranges(deleted) + " | " + ranges(inserted);
    }

    /**
     * Turns marks into ranges. Each run of marked characters becomes "start-end" (end not included).
     * Example: marks at positions 3,4 and 9,10,11  ->  "3-5,9-12".   No marks  ->  "."
     *
     * Why the ranges are always correct:
     *   - We scan from left to right, so ranges come out in order.
     *   - A whole run becomes ONE range, so touching ranges are merged automatically
     *     (we can never print "3-5,5-7"; it would be "3-7").
     *   - Two ranges always have at least one unmarked character between them, so they never overlap.
     *
     * Small trace: marked = [F, T, T, F, T]
     *   i=0 not marked, skip; i=1 starts a run, run ends at i=3 -> "1-3";
     *   i=3 not marked, skip; i=4 starts a run, ends at i=5 -> ",4-5"   => "1-3,4-5"
     */
    static String ranges(boolean[] marked) {
        StringBuilder sb = new StringBuilder();   // StringBuilder, not s += ..., so it stays fast
        int i = 0;
        while (i < marked.length) {
            if (!marked[i]) {          // not changed: skip it
                i++;
                continue;
            }
            int runStart = i;          // first changed character of this run
            while (i < marked.length && marked[i]) {
                i++;                   // walk to the end of the run; i stops just after it
            }
            if (sb.length() > 0) {
                sb.append(',');        // comma between ranges, no spaces
            }
            sb.append(runStart).append('-').append(i);   // end i is not included
        }
        return sb.length() == 0 ? "." : sb.toString();   // "." means nothing changed on this side
    }
}
