import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Main: the entry point. It controls the whole program and does the printing.
 *
 * Usage:
 *   java Main lines A B        Part A: minimal line diff of file A to file B
 *   java Main highlight A B    Part B: the same diff, plus changed-character ranges
 *
 * Output format (one output line per line of the edit script):
 *   " text"  keep   - the line is in both files
 *   "-text"  delete - the line is only in A
 *   "+text"  insert - the line is only in B
 *   "? old | new"   - (highlight only) changed character ranges, after each paired "+" line
 *
 * The 4 steps of main:
 *   1. read both files as raw bytes and split them into lines   (FileLines)
 *   2. give every distinct line an id number                     (lineIds)
 *   3. run Myers to mark deleted lines of A and inserted lines of B (Myers.diff)
 *   4. print the edit script                                      (printDiff)
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // Argument check (from the starter code): exactly 3 arguments, first is "lines" or "highlight".
        // Otherwise print a usage message on stderr and stop with exit code 2.
        boolean known = args.length == 3 && (args[0].equals("lines") || args[0].equals("highlight"));
        if (!known) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }
        String command = args[0];
        String aPath = args[1];
        String bPath = args[2];

        // 1. Read both files as raw bytes. If one cannot be read: nothing on stdout, exit code 2.
        //    Both files are read BEFORE anything is printed, so a failure never leaves half an output.
        byte[] aBytes = readFileOrExit(aPath);
        byte[] bBytes = readFileOrExit(bPath);
        FileLines a = new FileLines(aBytes);
        FileLines b = new FileLines(bBytes);

        // 2. Give every distinct line a number. Equal lines get equal numbers.
        //    One shared map for both files, so the same text in A and in B gets the same id.
        //    Paper example: a=0, b=1, c=2, so A "a b c a b b a" -> [0,1,2,0,1,1,0].
        Map<String, Integer> ids = new HashMap<>();
        int[] aIds = lineIds(a, ids);
        int[] bIds = lineIds(b, ids);
        ids = null; // not needed any more, let the garbage collector free it

        // 3. Myers: mark which lines of A are deleted and which lines of B are inserted.
        //    deleted[i] = true -> line i of A is only in A.  inserted[j] = true -> line j of B is only in B.
        //    Unmarked lines are kept. The total number of marks is the minimum possible.
        boolean[] deleted = new boolean[a.count];
        boolean[] inserted = new boolean[b.count];
        Myers.diff(aIds, bIds, deleted, inserted);

        // 4. Print the edit script.
        //    BufferedOutputStream collects output in a 64 KB buffer (1 << 16 = 65536 bytes) and writes
        //    it in big pieces: much faster than printing 500,000 lines one by one. We write raw bytes,
        //    so lines come out exactly as in the file, and we always end lines with '\n' (println would
        //    use "\r\n" on Windows).
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16);
        printDiff(a, b, deleted, inserted, command.equals("highlight"), out);
        out.flush();   // send whatever is still in the buffer
    }

    /**
     * Reads the whole file as raw bytes. If anything goes wrong (file missing, it is a folder,
     * no permission, invalid path ...), print a message on stderr and exit with code 2,
     * as the assignment requires. We catch Exception (not only IOException) because Path.of
     * can also throw InvalidPathException.
     */
    private static byte[] readFileOrExit(String path) {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (Exception e) {
            System.err.println("error: cannot read file " + path + " (" + e + ")");
            System.exit(2);
            return null; // never reached (exit stops the program), but Java needs a return here
        }
    }

    /**
     * Turns each line into a number (id). Same line text -> same id. New text -> next free number.
     *
     * Why numbers: Myers compares items again and again. Comparing two ints is one step;
     * comparing two strings checks every byte. Also Myers.diff works on int[], so the same code
     * works for lines (Part A) and for characters (Part B).
     *
     * Why ISO-8859-1 for the key: that charset turns every byte into exactly one char, and every
     * byte value is allowed. So two keys are equal only if the bytes are exactly equal. This also
     * works for bytes that are not valid UTF-8 (UTF-8 decoding would turn them into the same
     * replacement character, and different lines could wrongly look equal).
     */
    private static int[] lineIds(FileLines file, Map<String, Integer> ids) {
        int[] result = new int[file.count];
        for (int i = 0; i < file.count; i++) {
            String key = new String(file.data, file.start[i], file.length(i), StandardCharsets.ISO_8859_1);
            Integer id = ids.get(key);
            if (id == null) {          // first time we see this line text
                id = ids.size();       // next free number: 0, 1, 2, ...
                ids.put(key, id);
            }
            result[i] = id;
        }
        return result;
    }

    /**
     * Walks through A (with i) and B (with j) at the same time. Each round prints one change block:
     *   all deleted lines of A that come next   ("-" lines, so deletes come first)
     *   all inserted lines of B that come next  ("+" lines, each followed by its "?" line in highlight mode)
     * and then one kept line                    (" " line).
     *
     * The delete-first rule (all "-" before any "+" in a change block) is guaranteed here,
     * because inside each round the "-" loop always runs before the "+" loop.
     *
     * Paper example result (deleted a0,a2,a5 / inserted b0,b5) prints:
     *   -a +c " b" -c " a" " b" -b " a" +c        (5 edits)
     *
     * Identical files: nothing is marked, so every round prints one keep line.
     * Two empty files: the while loop never runs, so nothing is printed.
     */
    private static void printDiff(FileLines a, FileLines b, boolean[] deleted, boolean[] inserted,
                                  boolean highlight, BufferedOutputStream out) throws IOException {
        int i = 0;   // next line of A
        int j = 0;   // next line of B
        while (i < a.count || j < b.count) {
            // Skip over the run of deleted lines in A: they are lines deleteFrom .. i-1
            int deleteFrom = i;
            while (i < a.count && deleted[i]) {
                i++;
            }
            // Skip over the run of inserted lines in B: they are lines insertFrom .. j-1
            int insertFrom = j;
            while (j < b.count && inserted[j]) {
                j++;
            }

            // Print all "-" lines of this block first ...
            for (int x = deleteFrom; x < i; x++) {
                writeLine(out, '-', a, x);
            }
            // ... then all "+" lines.
            for (int y = insertFrom; y < j; y++) {
                writeLine(out, '+', b, y);
                // Pair the 1st "+" with the 1st "-", the 2nd with the 2nd, and so on.
                // (y - insertFrom) = position of this "+" in the block; the "-" at the same position is the partner.
                int partner = deleteFrom + (y - insertFrom);
                // partner < i means such a "-" line exists. Extra "+" lines are unpaired and get no "?" line.
                if (highlight && partner < i) {
                    String ranges = Highlight.rangesLine(a.data, a.start[partner], a.length(partner),
                                                         b.data, b.start[y], b.length(y));
                    out.write(ranges.getBytes(StandardCharsets.US_ASCII));   // only digits, '-', ',', '.', '?', '|', ' '
                    out.write('\n');
                }
            }

            // After a change block, the next lines of A and B are the same kept line.
            // (Removing all marked lines leaves the same list of kept lines in A and in B, in the same order.)
            if (i < a.count && j < b.count) {
                writeLine(out, ' ', a, i);
                i++;
                j++;
            } else if (i < a.count || j < b.count) {
                // Safety check only: a valid edit script can never reach this line.
                throw new IllegalStateException("edit script does not line up"); // should never happen
            }
        }
    }

    /**
     * Writes one output line: the prefix (' ', '-' or '+'), the line's exact bytes
     * (including any '\r', even bytes that are not valid UTF-8), and '\n'.
     * It writes straight from the big data array, so no copy of the line is made.
     */
    private static void writeLine(BufferedOutputStream out, char prefix, FileLines file, int line)
            throws IOException {
        out.write(prefix);
        out.write(file.data, file.start[line], file.length(line));
        out.write('\n');
    }
}
