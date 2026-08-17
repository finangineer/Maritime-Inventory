package mirp;

/**
 * Baseline algorithms for the computational study (paper Sec. 5, EKSIK-2/6):
 *  - nearest-neighbour construction + forward-looking split
 *  - optimal split of a FIXED sequence (exhaustive over break positions,
 *    feasible for n <= ~20 since there are 2^(n-1) break patterns)
 *  - fully exhaustive optimum (all permutations x optimal split) for n <= 8
 */
public class Baselines {

    /** Greedy nearest-neighbour sequence by travel time. */
    public static int[] nearestNeighbour(Instance in) {
        int n = in.n;
        boolean[] used = new boolean[n + 1];
        int[] seq = new int[n];
        int cur = 0;
        for (int k = 0; k < n; k++) {
            int best = -1; double bestT = Double.MAX_VALUE;
            for (int j = 1; j <= n; j++)
                if (!used[j] && in.travel(cur, j) < bestT) { bestT = in.travel(cur, j); best = j; }
            seq[k] = best; used[best] = true; cur = best;
        }
        return seq;
    }

    /**
     * Optimal split for a fixed sequence: enumerate all 2^(n-1) break patterns
     * and simulate each (demands re-forecast inside the simulation), keeping the
     * cheapest feasible schedule. This is the exact counterpart of the greedy
     * forward-looking split; the gap between the two quantifies what the greedy
     * single pass loses (paper EKSIK-6).
     */
    public static Schedule optimalSplit(Instance in, int[] seq, double qmax) {
        int n = seq.length;
        if (n > 22) throw new IllegalArgumentException("optimalSplit: n too large for enumeration");
        Schedule best = null;
        long patterns = 1L << (n - 1);
        for (long mask = 0; mask < patterns; mask++) {
            boolean[] breaks = new boolean[n];
            for (int k = 1; k < n; k++) breaks[k] = ((mask >> (k - 1)) & 1L) == 1L;
            Schedule s = TourSplitter.simulateWithBreaks(in, seq, qmax, breaks);
            if (s.feasible && (best == null || s.totalCost() < best.totalCost()))
                best = s;
        }
        return best;
    }

    /** Exhaustive optimum over ALL sequences (n <= 8): true capacitated optimum. */
    public static Schedule exhaustive(Instance in, double qmax) {
        if (in.n > 8) throw new IllegalArgumentException("exhaustive: n > 8");
        int[] seq = new int[in.n];
        for (int i = 0; i < in.n; i++) seq[i] = i + 1;
        Schedule[] best = new Schedule[1];
        permute(in, seq, 0, qmax, best);
        return best[0];
    }

    private static void permute(Instance in, int[] seq, int k, double qmax, Schedule[] best) {
        if (k == seq.length) {
            Schedule s = optimalSplit(in, seq.clone(), qmax);
            if (s != null && s.feasible && (best[0] == null || s.totalCost() < best[0].totalCost()))
                best[0] = s;
            return;
        }
        for (int i = k; i < seq.length; i++) {
            swap(seq, k, i);
            permute(in, seq, k + 1, qmax, best);
            swap(seq, k, i);
        }
    }

    private static void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
}
