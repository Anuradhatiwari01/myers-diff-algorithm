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
 *     right move: k goes up by 1.  down move: k goes down by 1.  diagonal move: k stays the same.
 *   d = number of edits (right + down moves) used so far. D = the minimum, the final answer.
 *   After d edits we can only be on diagonals -d, -d+2, ..., d (same odd/even as d).
 *
 * V array: V[k] = the furthest x reached on diagonal k using d edits (y is not stored: y = x - k).
 *   Only the furthest point matters: a point further along the same diagonal is never worse.
 *   We try d = 0, 1, 2, ... in order, so the first time we reach the goal, d is the minimum (D).
 *
 * Why linear space: the simple version (T2 slides) saves a copy of V for every d, and walks back
 * through the copies at the end. That needs O(D^2) memory. Here we search from the start (forward)
 * and from the end (backward) at the same time. Where the two searches meet is the "middle snake".
 * It is on a shortest path, so we split the problem there into a left half and a right half and
 * solve each half the same way (recursion). We only ever need two V arrays, so memory is O(N + M).
 *
 * Time: O((N + M) * D). Fast when the files are similar (D small), which is the usual case.
 *
 * Paper example: a = a b c a b b a, b = c b a b a c  ->  D = 5
 *   (delete a0, a2, a5 and insert b0, b5). The first split point is (5,4).
 */
public class Myers {

    private final int[] a;              // first sequence (old file / old line)
    private final int[] b;              // second sequence (new file / new line)
    private final boolean[] deleted;    // result: deleted[i] = a[i] is removed
    private final boolean[] inserted;   // result: inserted[j] = b[j] is added

    // V arrays. forward[offset + k]  = furthest x reached on diagonal k, searching from the start.
    //           backward[offset + k] = furthest distance reached on diagonal k, searching from the end.
    // They are created once here and reused by every middleSnake call (no copying inside the d loop).
    private final int[] forward;
    private final int[] backward;

    /**
     * Private constructor: stores the inputs and creates the two V arrays ONCE,
     * big enough for the largest box (the whole problem). Every smaller box reuses them.
     * maxD = (n + m + 1) / 2 is half of the largest possible D, rounded up. Each search
     * (forward or backward) needs at most this many steps before they meet.
     * Size 2 * maxD + 2: diagonals -maxD .. +maxD, plus room for the index + 1 read.
     */
    private Myers(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        this.a = a;
        this.b = b;
        this.deleted = deleted;
        this.inserted = inserted;
        int maxD = (a.length + b.length + 1) / 2;
        this.forward = new int[2 * maxD + 2];
        this.backward = new int[2 * maxD + 2];
    }

    /**
     * The only public method. Finds a shortest edit script and writes it into deleted[] and inserted[].
     * Used by Main (on line ids) and by Highlight (on characters).
     * It solves the whole box: all of a (0 .. a.length) against all of b (0 .. b.length).
     */
    public static void diff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        new Myers(a, b, deleted, inserted).compare(0, a.length, 0, b.length);
    }

    /**
     * Diffs the box a[aStart..aEnd) against b[bStart..bEnd) (the end positions are not included).
     * This is the divide-and-conquer part:
     *   1. skip matching items at the start and at the end (they are kept)
     *   2. if one side is empty, the rest of the other side is all inserts or all deletes
     *   3. otherwise find the middle snake (a split point on a shortest path),
     *      then solve the box before it and the box after it (recursion).
     * The recursion always ends: after step 1 a non-empty box needs at least 2 edits, and the
     * split gives two boxes that each need fewer edits. Depth is about log2(D).
     */
    private void compare(int aStart, int aEnd, int bStart, int bEnd) {
        // Items that match at the start are kept: skip them.
        // (Safe for minimality: matching equal first items never makes the script longer.)
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

        // Find a point {x, y} that lies on a shortest path through this box.
        int[] split = middleSnake(aStart, aEnd, bStart, bEnd);
        if (split == null) {
            // a and b have nothing in common: delete all of a and insert all of b
            // (example: "0" vs "8" in 8000 -> 8080; this is minimal because nothing can be kept)
            for (int i = aStart; i < aEnd; i++) {
                deleted[i] = true;
            }
            for (int j = bStart; j < bEnd; j++) {
                inserted[j] = true;
            }
            return;
        }

        // Solve the part before the split point, then the part after it.
        // shortest(whole box) = shortest(start -> split) + shortest(split -> end)
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
     *
     * Paper example (n=7, m=6, delta=1): the searches meet at d=3, forward, k=1, where the forward
     * search reaches (5,4) and the backward search on the same diagonal is at x=3. Split = (5,4).
     */
    private int[] middleSnake(int aStart, int aEnd, int bStart, int bEnd) {
        int n = aEnd - aStart;             // size of the box in a
        int m = bEnd - bStart;             // size of the box in b
        int maxD = (n + m + 1) / 2;        // each search needs at most this many steps before they meet
        int offset = maxD;                 // diagonal k is stored at index k + offset (k can be negative)
        int size = 2 * maxD + 2;           // the part of the arrays used for this box
        int delta = n - m;                 // the diagonal that the end point (n, m) is on
        boolean deltaIsOdd = (delta % 2 != 0);   // decides when we check for overlap (see below)

        // -1 means "this diagonal has not been reached yet".
        // Arrays.fill is done once per middleSnake call (not inside the d loop), so it stays fast.
        Arrays.fill(forward, 0, size, -1);
        Arrays.fill(backward, 0, size, -1);
        // Starting trick: at d = 0, k = 0 the code reads V[k + 1] (the "move down" case),
        // so V[1] = 0 makes the search start at x = 0, y = 0.
        forward[offset + 1] = 0;
        backward[offset + 1] = 0;

        // When a path runs off the right or bottom edge of the box, we stop
        // looking at the diagonals beyond it. (+2 each time, because k moves in steps of 2.)
        int forwardStartTrim = 0;
        int forwardEndTrim = 0;
        int backwardStartTrim = 0;
        int backwardEndTrim = 0;

        // One round = one more edit (d) for the forward search AND one more for the backward search.
        for (int d = 0; d < maxD; d++) {

            // ---------- forward search: one more step from the top-left ----------
            // With d edits we can only be on diagonals -d, -d+2, ..., d (step 2).
            for (int k = -d + forwardStartTrim; k <= d - forwardEndTrim; k += 2) {
                int index = offset + k;
                int x;
                // Come down from diagonal k+1 (an insert) or go right from diagonal k-1 (a delete),
                // whichever got further. On a tie we go right, so deletes are preferred.
                //   k == -d : lowest diagonal, no k-1 was reached, so we must come down from k+1
                //   k ==  d : highest diagonal, no k+1 was reached, so we must go right from k-1
                if (k == -d || (k != d && forward[index - 1] < forward[index + 1])) {
                    x = forward[index + 1];          // move down  (insert): x stays the same
                } else {
                    x = forward[index - 1] + 1;      // move right (delete): x grows by 1
                }
                int y = x - k;                       // y always follows from x and k

                // Follow the snake: matching items are free.
                // Keep moving diagonally while a[x] == b[y] (and we stay inside the box).
                while (x < n && y < m && a[aStart + x] == b[bStart + y]) {
                    x++;
                    y++;
                }
                forward[index] = x;                  // save the furthest x on diagonal k

                if (x > n) {
                    forwardEndTrim += 2;             // ran off the right edge
                } else if (y > m) {
                    forwardStartTrim += 2;           // ran off the bottom edge
                } else if (deltaIsOdd) {
                    // Has the backward search already reached this diagonal, and do the two overlap?
                    // When delta is odd, D is odd: the forward search (d steps) meets the backward
                    // search from the previous round (d-1 steps), so we check here, in the forward half.
                    // The same diagonal in backward (reversed) numbers is delta - k,
                    // because x' = n - x, y' = m - y  ->  x' - y' = (n - m) - (x - y) = delta - k.
                    int backIndex = offset + delta - k;
                    if (backIndex >= 0 && backIndex < size && backward[backIndex] != -1) {
                        int backDistX = backward[backIndex];               // distance from the end in x
                        int backDistY = backDistX - (backIndex - offset);  // distance from the end in y
                        int backX = n - backDistX;             // turn it back into a forward x
                        // Overlap: forward got at least as far as backward on this diagonal.
                        // The extra checks make sure the backward point is a real point inside the box.
                        if (x >= backX && backDistX <= n && backDistY <= m) {
                            return new int[] { aStart + x, bStart + y };   // split point (absolute)
                        }
                    }
                }
            }

            // ---------- backward search: one more step from the bottom-right ----------
            // Same steps as above, but reading a and b from the end.
            // Here x and y are distances from the end of the box (n, m).
            for (int k = -d + backwardStartTrim; k <= d - backwardEndTrim; k += 2) {
                int index = offset + k;
                int x;
                // Same down-or-right choice as in the forward search (on the reversed box).
                if (k == -d || (k != d && backward[index - 1] < backward[index + 1])) {
                    x = backward[index + 1];
                } else {
                    x = backward[index - 1] + 1;
                }
                int y = x - k;

                // Follow the snake backward: compare items counted from the end of the box.
                while (x < n && y < m && a[aEnd - 1 - x] == b[bEnd - 1 - y]) {
                    x++;
                    y++;
                }
                backward[index] = x;                 // save the furthest distance on diagonal k

                if (x > n) {
                    backwardEndTrim += 2;            // ran off the edge (in reversed terms)
                } else if (y > m) {
                    backwardStartTrim += 2;
                } else if (!deltaIsOdd) {
                    // Has the forward search already reached this diagonal, and do the two overlap?
                    // When delta is even, D is even: both searches have done d steps, so we check
                    // here, after the backward step.
                    int forwardIndex = offset + delta - k;                   // same diagonal in forward numbers
                    if (forwardIndex >= 0 && forwardIndex < size && forward[forwardIndex] != -1) {
                        int forwardX = forward[forwardIndex];
                        int forwardY = forwardX - (forwardIndex - offset);   // y = x - k
                        int backX = n - x;                     // turn it back into a forward x
                        // Overlap, and the forward point must be a real point inside the box.
                        if (forwardX >= backX && forwardX <= n && forwardY <= m) {
                            return new int[] { aStart + forwardX, bStart + forwardY };
                        }
                    }
                }
            }
        }
        // The loop ended without the searches meeting: this only happens when a and b have
        // nothing in common (D = n + m). compare() then deletes all and inserts all.
        return null;
    }
}
