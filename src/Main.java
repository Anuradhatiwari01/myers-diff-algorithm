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
 * Usage:
 *   java Main lines A B        Part A: minimal line diff of file A to file B
 *   java Main highlight A B    Part B: the same diff, plus changed-character ranges
 */
public class Main {
    public static void main(String[] args) throws IOException {
        boolean known = args.length == 3 && (args[0].equals("lines") || args[0].equals("highlight"));
        if (!known) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }
        String command = args[0];
        String aPath = args[1];
        String bPath = args[2];

        // 1. Read both files as raw bytes. If one cannot be read: nothing on stdout, exit code 2.
        byte[] aBytes = readFileOrExit(aPath);
        byte[] bBytes = readFileOrExit(bPath);
        FileLines a = new FileLines(aBytes);
        FileLines b = new FileLines(bBytes);

        // 2. Give every distinct line a number. Equal lines get equal numbers.
        Map<String, Integer> ids = new HashMap<>();
        int[] aIds = lineIds(a, ids);
        int[] bIds = lineIds(b, ids);
        ids = null; // not needed any more, let the garbage collector free it

        // 3. Myers: mark which lines of A are deleted and which lines of B are inserted.
        boolean[] deleted = new boolean[a.count];
        boolean[] inserted = new boolean[b.count];
        Myers.diff(aIds, bIds, deleted, inserted);

        // 4. Print the edit script.
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16);
        printDiff(a, b, deleted, inserted, command.equals("highlight"), out);
        out.flush();
    }

    private static byte[] readFileOrExit(String path) {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (Exception e) {
            System.err.println("error: cannot read file " + path + " (" + e + ")");
            System.exit(2);
            return null; // never reached
        }
    }

    /**
     * Turns each line into a number. We use an ISO-8859-1 String as the map key because that
     * charset turns every byte into exactly one char, so two keys are equal only if the
     * bytes are exactly equal (this also works for bytes that are not valid UTF-8).
     */
    private static int[] lineIds(FileLines file, Map<String, Integer> ids) {
        int[] result = new int[file.count];
        for (int i = 0; i < file.count; i++) {
            String key = new String(file.data, file.start[i], file.length(i), StandardCharsets.ISO_8859_1);
            Integer id = ids.get(key);
            if (id == null) {
                id = ids.size();
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
     */
    private static void printDiff(FileLines a, FileLines b, boolean[] deleted, boolean[] inserted,
                                  boolean highlight, BufferedOutputStream out) throws IOException {
        int i = 0;
        int j = 0;
        while (i < a.count || j < b.count) {
            int deleteFrom = i;
            while (i < a.count && deleted[i]) {
                i++;
            }
            int insertFrom = j;
            while (j < b.count && inserted[j]) {
                j++;
            }

            for (int x = deleteFrom; x < i; x++) {
                writeLine(out, '-', a, x);
            }
            for (int y = insertFrom; y < j; y++) {
                writeLine(out, '+', b, y);
                // Pair the 1st "+" with the 1st "-", the 2nd with the 2nd, and so on.
                int partner = deleteFrom + (y - insertFrom);
                if (highlight && partner < i) {
                    String ranges = Highlight.rangesLine(a.data, a.start[partner], a.length(partner),
                                                         b.data, b.start[y], b.length(y));
                    out.write(ranges.getBytes(StandardCharsets.US_ASCII));
                    out.write('\n');
                }
            }

            // After a change block, the next lines of A and B are the same kept line.
            if (i < a.count && j < b.count) {
                writeLine(out, ' ', a, i);
                i++;
                j++;
            } else if (i < a.count || j < b.count) {
                throw new IllegalStateException("edit script does not line up"); // should never happen
            }
        }
    }

    /** Writes the prefix, the line's exact bytes (including any '\r'), and '\n'. */
    private static void writeLine(BufferedOutputStream out, char prefix, FileLines file, int line)
            throws IOException {
        out.write(prefix);
        out.write(file.data, file.start[line], file.length(line));
        out.write('\n');
    }
}
