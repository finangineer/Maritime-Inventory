package mirp;

import java.util.ArrayList;
import java.util.List;

/**
 * Forward-looking tour-splitting heuristic (paper Algorithm 1, thesis eqs 4.1-4.8).
 *
 * Sweeps a fixed visiting sequence once; before each movement it projects the
 * arrival time and the time-dependent demand, and inserts a depot return when
 * the residual cargo capacity cannot satisfy the forecast (all-or-nothing policy).
 * A second accounting pass computes leg-by-leg fuel costs with decreasing payload.
 */
public class TourSplitter {

    /** Split with automatic (greedy forward-looking) depot returns. */
    public static Schedule split(Instance in, int[] seq, double qmax) {
        return simulate(in, seq, qmax, null);
    }

    /**
     * Simulate the sequence with a FIXED set of break positions (used by the
     * optimal-split baseline). breaks[k] == true means a depot return is made
     * before serving seq[k]. Returns an infeasible Schedule if any sub-tour
     * load exceeds qmax.
     */
    public static Schedule simulateWithBreaks(Instance in, int[] seq, double qmax, boolean[] breaks) {
        return simulate(in, seq, qmax, breaks);
    }

    private static Schedule simulate(Instance in, int[] seq, double qmax, boolean[] forcedBreaks) {
        int n = in.n;
        Schedule s = new Schedule(n);
        double ct = 0;          // clock
        int cpos = 0;           // current position (0 = depot)
        double cq = 0;          // cargo committed in current sub-tour
        List<Integer> current = new ArrayList<>();

        for (int k = 0; k < seq.length; k++) {
            int j = seq[k];
            boolean forceBreak = forcedBreaks != null && forcedBreaks[k] && !current.isEmpty();
            // projected arrival and time-dependent demand (eqs 4.1-4.2).
            // PHYSICAL DEMAND CAP: a tank can never absorb more than CAP_j --
            // once the tank runs dry the plant is offline and stops consuming.
            // This also guarantees single-visit serviceability whenever
            // CAP_j <= Qmax (see Instance.random()).
            double tArr = ct + in.travel(cpos, j);
            double fd = Math.min(in.CAP[j],
                    Math.max(0.0, in.CAP[j] - in.I0[j] + in.CR[j] * tArr));

            boolean fits = cq + fd <= qmax + 1e-9;
            if (forcedBreaks != null) fits = !forceBreak;      // fixed-break mode ignores greedy test

            if (!fits) {
                // depot return (eqs 4.7-4.8), then re-process j from the depot
                ct += in.travel(cpos, 0);
                cpos = 0; cq = 0;
                if (!current.isEmpty()) { s.subTours.add(current); current = new ArrayList<>(); }
                tArr = ct + in.travel(0, j);
                fd = Math.min(in.CAP[j],
                        Math.max(0.0, in.CAP[j] - in.I0[j] + in.CR[j] * tArr));
                if (forcedBreaks == null && fd > qmax + 1e-9) { s.feasible = false; return s; }
            }
            if (forcedBreaks != null && cq + fd > qmax + 1e-9) { s.feasible = false; return s; }

            // deliver (eqs 4.4-4.6); service duration = S + fd/PR = alpha_j + beta_j * tArr
            double tDep = tArr + in.S + fd / in.PR;
            s.arrival[j] = tArr;
            s.delivered[j] = fd;
            cq += fd;
            ct = tDep;
            cpos = j;
            current.add(j);
        }
        if (!current.isEmpty()) s.subTours.add(current);
        s.totalTime = ct;

        accountCosts(in, s);
        return s;
    }

    /** Second pass: charter, empty-weight fuel, payload-dependent fuel, penalties. */
    private static void accountCosts(Instance in, Schedule s) {
        s.charterCost = in.F * s.totalTime;
        double emptyFuel = 0, cargoFuel = 0, penalty = 0;
        for (List<Integer> tour : s.subTours) {
            double load = 0;
            for (int p : tour) load += s.delivered[p];        // initial load of this sub-tour
            int prev = 0;                                     // every sub-tour starts at the depot
            for (int p : tour) {
                double d = in.dist[prev][p];
                emptyFuel += in.c * in.w * d;
                cargoFuel += in.c * load * d;
                load -= s.delivered[p];                       // vessel gets lighter after delivery
                prev = p;
            }
            // open route: no return leg after the LAST sub-tour; intermediate
            // returns are travelled empty and are added here for earlier tours
        }
        // travel cost of intermediate depot returns (empty vessel)
        for (int t = 0; t < s.subTours.size() - 1; t++) {
            List<Integer> tour = s.subTours.get(t);
            int last = tour.get(tour.size() - 1);
            emptyFuel += in.c * in.w * in.dist[last][0];
        }
        for (int i = 1; i <= in.n; i++) {
            double late = Math.max(0.0, s.arrival[i] - in.deadline[i]);
            penalty += in.L[i] * late;
        }
        s.emptyFuelCost = emptyFuel;
        s.cargoFuelCost = cargoFuel;
        s.penaltyCost = penalty;
    }
}
