import java.util.Arrays;

/**
 * Myers' O(ND) diff algorithm, linear-space version
 * (Eugene Myers, "An O(ND) Difference Algorithm and Its Variations", 1986, section 4b).
 *
 * Input:  two sequences of numbers, a and b.
 *         For Part A every number is a line id (same line text = same id).
 *         For Part B every number is one character (a Unicode code point).
 * Output: deleted[i] = true  if a[i] is deleted   (it is only in a)
 *         inserted[j] = true if b[j] is inserted  (it is only in b)
 *         Everything not marked is kept. The number of marks is the minimum possible.
 *
 * The edit graph: x walks through a, y walks through b.
 *   move right  (x+1)       = delete a[x]               cost 1
 *   move down   (y+1)       = insert b[y]               cost 1
 *   move diagonal (x+1,y+1) = keep, only if a[x] == b[y] cost 0   (a run of these is a "snake")
 *   k = x - y is the number of the diagonal.
 *
 * Why linear space: the simple version saves a copy of V for every d, which needs O(D^2) memory.
 * Here we search from the start (forward) and from the end (backward) at the same time.
 * Where the two searches meet is the "middle snake". It is on a shortest path, so we split
 * the problem there into a left half and a right half and solve each half the same way.
 * We only ever need two V arrays, so the memory is O(N + M).
 */
public class Myers {

    private final int[] a;
    private final int[] b;
    private final boolean[] deleted;
    private final boolean[] inserted;

    // V arrays. forward[offset + k]  = furthest x reached on diagonal k, searching from the start.
    //           backward[offset + k] = furthest distance reached on diagonal k, searching from the end.
    // They are created once here and reused by every middleSnake call (no copying inside the d loop).
    private final int[] forward;
    private final int[] backward;

    private Myers(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        this.a = a;
        this.b = b;
        this.deleted = deleted;
        this.inserted = inserted;
        int maxD = (a.length + b.length + 1) / 2;
        this.forward = new int[2 * maxD + 2];
        this.backward = new int[2 * maxD + 2];
    }

    /** Finds a shortest edit script and writes it into deleted[] and inserted[]. */
    public static void diff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        new Myers(a, b, deleted, inserted).compare(0, a.length, 0, b.length);
    }

    /** Diffs the box a[aStart..aEnd) against b[bStart..bEnd). */
    private void compare(int aStart, int aEnd, int bStart, int bEnd) {
        // Items that match at the start are kept: skip them.
        while (aStart < aEnd && bStart < bEnd && a[aStart] == b[bStart]) {
            aStart++;
            bStart++;
        }
        // Items that match at the end are kept: skip them.
        while (aStart < aEnd && bStart < bEnd && a[aEnd - 1] == b[bEnd - 1]) {
            aEnd--;
            bEnd--;
        }

        // Only b is left: everything in it is inserted.
        if (aStart == aEnd) {
            for (int j = bStart; j < bEnd; j++) {
                inserted[j] = true;
            }
            return;
        }
        // Only a is left: everything in it is deleted.
        if (bStart == bEnd) {
            for (int i = aStart; i < aEnd; i++) {
                deleted[i] = true;
            }
            return;
        }

        int[] split = middleSnake(aStart, aEnd, bStart, bEnd);
        if (split == null) {
            // a and b have nothing in common: delete all of a and insert all of b
            for (int i = aStart; i < aEnd; i++) {
                deleted[i] = true;
            }
            for (int j = bStart; j < bEnd; j++) {
                inserted[j] = true;
            }
            return;
        }

        // Solve the part before the split point, then the part after it.
        compare(aStart, split[0], bStart, split[1]);
        compare(split[0], aEnd, split[1], bEnd);
    }

    /**
     * Searches forward from the top-left and backward from the bottom-right, one d at a time,
     * until the two searches overlap on the same diagonal.
     * Returns {x, y}: a point (absolute positions in a and b) that lies on a shortest path,
     * or null if the two boxes share nothing.
     *
     * Inside this method x and y are relative to the box: 0 <= x <= n and 0 <= y <= m.
     * The backward search works on the reversed box, so its "x" counts from the end of a.
     */
    private int[] middleSnake(int aStart, int aEnd, int bStart, int bEnd) {
        int n = aEnd - aStart;
        int m = bEnd - bStart;
        int maxD = (n + m + 1) / 2;
        int offset = maxD;                 // diagonal k is stored at index k + offset
        int size = 2 * maxD + 2;
        int delta = n - m;                 // the diagonal that the end point (n, m) is on
        boolean deltaIsOdd = (delta % 2 != 0);

        // -1 means "this diagonal has not been reached yet"
        Arrays.fill(forward, 0, size, -1);
        Arrays.fill(backward, 0, size, -1);
        forward[offset + 1] = 0;
        backward[offset + 1] = 0;

        // When a path runs off the right or bottom edge of the box, we stop
        // looking at the diagonals beyond it.
        int forwardStartTrim = 0;
        int forwardEndTrim = 0;
        int backwardStartTrim = 0;
        int backwardEndTrim = 0;

        for (int d = 0; d < maxD; d++) {

            // ---------- forward search: one more step from the top-left ----------
            for (int k = -d + forwardStartTrim; k <= d - forwardEndTrim; k += 2) {
                int index = offset + k;
                int x;
                // Come down from diagonal k+1 (an insert) or go right from diagonal k-1 (a delete),
                // whichever got further. On a tie we go right, so deletes are preferred.
                if (k == -d || (k != d && forward[index - 1] < forward[index + 1])) {
                    x = forward[index + 1];          // move down
                } else {
                    x = forward[index - 1] + 1;      // move right
                }
                int y = x - k;

                // Follow the snake: matching items are free.
                while (x < n && y < m && a[aStart + x] == b[bStart + y]) {
                    x++;
                    y++;
                }
                forward[index] = x;

                if (x > n) {
                    forwardEndTrim += 2;             // ran off the right edge
                } else if (y > m) {
                    forwardStartTrim += 2;           // ran off the bottom edge
                } else if (deltaIsOdd) {
                    // Has the backward search already reached this diagonal, and do the two overlap?
                    int backIndex = offset + delta - k;
                    if (backIndex >= 0 && backIndex < size && backward[backIndex] != -1) {
                        int backDistX = backward[backIndex];
                        int backDistY = backDistX - (backIndex - offset);
                        int backX = n - backDistX;             // turn it back into a forward x
                        if (x >= backX && backDistX <= n && backDistY <= m) {
                            return new int[] { aStart + x, bStart + y };
                        }
                    }
                }
            }

            // ---------- backward search: one more step from the bottom-right ----------
            // Same steps as above, but reading a and b from the end.
            for (int k = -d + backwardStartTrim; k <= d - backwardEndTrim; k += 2) {
                int index = offset + k;
                int x;
                if (k == -d || (k != d && backward[index - 1] < backward[index + 1])) {
                    x = backward[index + 1];
                } else {
                    x = backward[index - 1] + 1;
                }
                int y = x - k;

                while (x < n && y < m && a[aEnd - 1 - x] == b[bEnd - 1 - y]) {
                    x++;
                    y++;
                }
                backward[index] = x;

                if (x > n) {
                    backwardEndTrim += 2;
                } else if (y > m) {
                    backwardStartTrim += 2;
                } else if (!deltaIsOdd) {
                    // Has the forward search already reached this diagonal, and do the two overlap?
                    int forwardIndex = offset + delta - k;
                    if (forwardIndex >= 0 && forwardIndex < size && forward[forwardIndex] != -1) {
                        int forwardX = forward[forwardIndex];
                        int forwardY = forwardX - (forwardIndex - offset);
                        int backX = n - x;                     // turn it back into a forward x
                        if (forwardX >= backX && forwardX <= n && forwardY <= m) {
                            return new int[] { aStart + forwardX, bStart + forwardY };
                        }
                    }
                }
            }
        }
        return null;
    }
}
