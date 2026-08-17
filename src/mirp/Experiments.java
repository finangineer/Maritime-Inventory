package mirp;

import com.gurobi.gurobi.GRBException;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Full experimental suite for the paper (Sec. 5). Writes CSV files to results/.
 *
 * Usage:
 *   java mirp.Experiments all
 *   java mirp.Experiments scaling|capacity|rank|sensitivity|baselines
 *
 * Deney esleme:
 *   scaling      -> Table 3 + EKSIK-10 (8-30 santral olcekleme, gap raporu)
 *   capacity     -> Table 4  (kapasite etkisi, 1000t vs 350t)
 *   rank         -> Table 5-6 + EKSIK-11 (top-5 exclusion cut + yeniden siralama)
 *   sensitivity  -> Table 7-8 + EKSIK-12 (gemi ve santral duyarliligi)
 *   baselines    -> EKSIK-2/6 (NN+split, optimal split, exhaustive optimum, gap)
 */
public class Experiments {

    static final double TIME_LIMIT = 1800.0;            // s, per MILP solve
    static final int[] SCALING_SIZES = {2, 3, 4, 5, 6, 8, 10, 15, 20, 30};
    static final int SEEDS = 10;                        // instances per size (H2 protokolu)

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        new File("results").mkdirs();
        String what = args.length > 0 ? args[0] : "all";
        try (MilpSolver solver = new MilpSolver()) {
            if (what.equals("all") || what.equals("scaling"))     scaling(solver);
            if (what.equals("all") || what.equals("capacity"))    capacity(solver);
            if (what.equals("all") || what.equals("rank"))        rankReversal(solver);
            if (what.equals("all") || what.equals("sensitivity")) sensitivity(solver);
            if (what.equals("all") || what.equals("baselines"))   baselines(solver);
	    if (what.equals("all") || what.equals("penalty"))     penaltySweep(solver);
        }
        System.out.println("Bitti. CSV ciktilari: results/");
    }

    // ---------------------------------------------------------------
    // 1) Model size + runtime scaling (Table 3, EKSIK-10)
    // ---------------------------------------------------------------
    static void scaling(MilpSolver solver) throws Exception {
        try (PrintWriter out = csv("results/scaling.csv",
                "n,seed,vars,constrs,milp_time_s,milp_obj,milp_bound,milp_gap,heur_time_s,heur_cost,heur_gap_vs_bound")) {
            for (int n : SCALING_SIZES) {
                for (int seed = 1; seed <= SEEDS; seed++) {
                    Instance in = Instance.random(n, seed);
                    in.exportCsv("results/instances/" + in.name + ".csv");   // reproducibility dump
                    long t0 = System.nanoTime();
                    MilpSolver.Result r = solver.solve(in, TIME_LIMIT);
                    double milpTime = sec(t0);
                    if (r.sequence == null) {
                        out.printf("%d,%d,%d,%d,%.2f,NA,NA,NA,NA,NA,NA%n", n, seed, r.numVars, r.numConstrs, milpTime);
                        continue;
                    }
                    long t1 = System.nanoTime();
                    Schedule s = TourSplitter.split(in, r.sequence, in.Qmax);
                    double heurTime = sec(t1);
                    if (!s.feasible) {
                        out.printf("%d,%d,%d,%d,%.2f,%.2f,%.2f,%.4f,%.4f,NA,NA%n",
                                n, seed, r.numVars, r.numConstrs, milpTime, r.objVal, r.objBound,
                                r.mipGap, heurTime);
                        continue;
                    }
                    double gap = (s.totalCost() - r.objBound) / r.objBound;
                    out.printf("%d,%d,%d,%d,%.2f,%.2f,%.2f,%.4f,%.4f,%.2f,%.4f%n",
                            n, seed, r.numVars, r.numConstrs, milpTime, r.objVal, r.objBound,
                            r.mipGap, heurTime, s.totalCost(), gap);
                    System.out.printf("scaling n=%d seed=%d milp=%.1fs obj=%.0f heur=%.0f%n",
                            n, seed, milpTime, r.objVal, s.totalCost());
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // 2) Cost of cargo capacity (Table 4)
    // ---------------------------------------------------------------
    static void capacity(MilpSolver solver) throws Exception {
        try (PrintWriter out = csv("results/capacity.csv",
                "capacity,mode,total_cost,charter,empty_fuel,cargo_fuel,penalty,voyage_h,route")) {
            Instance base = Instance.base5();
            base.exportCsv("results/instances/base5.csv");
            for (double cap : new double[]{4000, 2000}) {
                MilpSolver.Result r = solver.solve(base, TIME_LIMIT);
                // incapacitated evaluation = split with non-binding capacity
                Schedule inc = TourSplitter.split(base, r.sequence, Double.MAX_VALUE);
                Schedule lim = TourSplitter.split(base, r.sequence, cap);
                row(out, cap, "incapacitated", inc);
                row(out, cap, "capacitated", lim);
                System.out.printf("capacity cap=%.0f inc=%.2f lim=%.2f route=[%s]%n",
                        cap, inc.totalCost(), lim.totalCost(), lim.routeString());
            }
        }
    }

    private static void row(PrintWriter out, double cap, String mode, Schedule s) {
        out.printf("%.0f,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s%n", cap, mode, s.totalCost(),
                s.charterCost, s.emptyFuelCost, s.cargoFuelCost, s.penaltyCost, s.totalTime,
                "\"" + s.routeString() + "\"");
    }

    // ---------------------------------------------------------------
    // 3) Top-5 alternatives + capacity-induced rank reversal (Tables 5-6)
    // ---------------------------------------------------------------
    static void rankReversal(MilpSolver solver) throws Exception {
        try (PrintWriter out = csv("results/rank_reversal.csv",
                "incap_rank,incap_cost,cap_cost,cap_rank,voyage_incap_h,voyage_cap_h,route_cap")) {
            Instance in = Instance.base5();
            List<int[][]> excluded = new ArrayList<>();
            List<double[]> rows = new ArrayList<>();          // [incapCost, capCost, tInc, tCap]
            List<String> routes = new ArrayList<>();
            for (int k = 0; k < 5; k++) {
                MilpSolver.Result r = solver.solve(in, TIME_LIMIT, excluded);
                if (r.sequence == null) break;
                excluded.add(MilpSolver.arcsOf(in, r.sequence));
                Schedule inc = TourSplitter.split(in, r.sequence, Double.MAX_VALUE);
                Schedule cap = TourSplitter.split(in, r.sequence, in.Qmax);
                rows.add(new double[]{inc.totalCost(), cap.totalCost(), inc.totalTime, cap.totalTime});
                routes.add(cap.routeString());
                System.out.printf("rank k=%d incap=%.2f cap=%.2f%n", k + 1, inc.totalCost(), cap.totalCost());
            }
            // capacitated re-ranking
            Integer[] order = new Integer[rows.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            List<Integer> sorted = new ArrayList<>(List.of(order));
            sorted.sort(Comparator.comparingDouble(i -> rows.get(i)[1]));
            for (int i = 0; i < rows.size(); i++) {
                int capRank = sorted.indexOf(i) + 1;
                double[] rr = rows.get(i);
                out.printf("%d,%.2f,%.2f,%d,%.2f,%.2f,%s%n",
                        i + 1, rr[0], rr[1], capRank, rr[2], rr[3], "\"" + routes.get(i) + "\"");
            }
        }
    }

    // ---------------------------------------------------------------
    // 4) Sensitivity analyses (Tables 7-8, EKSIK-12: kapasite izgarasi + P taramasi)
    // ---------------------------------------------------------------
    static void sensitivity(MilpSolver solver) throws Exception {
        Instance base = Instance.base5();
        try (PrintWriter out = csv("results/sensitivity.csv",
                "scenario,total_cost,charter,empty_fuel,cargo_fuel,penalty,voyage_h")) {
            runScenario(solver, out, "base", base);
            // vessel side (Table 7)
            runScenario(solver, out, "fuel_cost_+20%", base.withFuelCost(1.20));
            runScenario(solver, out, "charter_-15%", base.withCharter(0.85));
            runScenario(solver, out, "pump_rate_+30%", base.withPumpRate(1.30));
            // plant side (Table 8)
            runScenario(solver, out, "consumption_+10%", base.withConsumption(1.10));
            runScenario(solver, out, "consumption_-25%", base.withConsumption(0.75));
            runScenario(solver, out, "dispatch_delay_10d", base.withDispatchDelay(240));
        }
        // EKSIK-12: capacity grid + penalty sweep
        try (PrintWriter out = csv("results/capacity_grid.csv", "qmax,total_cost,voyage_h,n_subtours")) {
            for (double q : new double[]{1200, 1500, 2000, 2500, 3000, 4000, 6000}) {
                MilpSolver.Result r = solver.solve(base, TIME_LIMIT);
                Schedule s = TourSplitter.split(base, r.sequence, q);
                if (!s.feasible) {
                    // q < max CAP_i: all-or-nothing altinda yapisal infeasible
                    out.printf("%.0f,NA,NA,NA%n", q);
                    continue;
                }
                out.printf("%.0f,%.2f,%.2f,%d%n", q, s.totalCost(), s.totalTime, s.subTours.size());
            }
        }
    }

    static void runScenario(MilpSolver solver, PrintWriter out, String label, Instance in) throws GRBException {
        MilpSolver.Result r = solver.solve(in, TIME_LIMIT);
        Schedule s = TourSplitter.split(in, r.sequence, Double.MAX_VALUE);   // incapacitated (thesis Tables 4.3-4.4)
        out.printf("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n", label, s.totalCost(),
                s.charterCost, s.emptyFuelCost, s.cargoFuelCost, s.penaltyCost, s.totalTime);
        System.out.printf("sensitivity %-20s cost=%.2f voyage=%.2f%n", label, s.totalCost(), s.totalTime);
    }

    // ---------------------------------------------------------------
    // 6) Penalty coefficient sweep (EKSIK-12)
    // ---------------------------------------------------------------
    static void penaltySweep(MilpSolver solver) throws Exception {
        try (PrintWriter out = csv("results/penalty_sweep.csv",
                "P,total_cost,charter,empty_fuel,cargo_fuel,penalty,voyage_h,late_plants,total_tardiness_h")) {
            Instance base = Instance.base5().withDispatchDelay(30);
            for (double p : new double[]{0.0, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 25.0, 100.0}) {
                Instance in = base.withPenalty(p);
                MilpSolver.Result r = solver.solve(in, TIME_LIMIT);
                Schedule s = TourSplitter.split(in, r.sequence, in.Qmax);
                int late = 0; double tard = 0;
                for (int i = 1; i <= in.n; i++) {
                    double d = s.arrival[i] - in.deadline[i];
                    if (d > 1e-6) { late++; tard += d; }
                }
                out.printf("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%.2f%n",
                        p, s.totalCost(), s.charterCost, s.emptyFuelCost, s.cargoFuelCost,
                        s.penaltyCost, s.totalTime, late, tard);
                System.out.printf("penalty P=%.2f cost=%.2f penalty=%.2f late=%d tard=%.1fh%n",
                        p, s.totalCost(), s.penaltyCost, late, tard);
            }
        }
    }

    // ---------------------------------------------------------------
    // 5) Baseline comparison + heuristic quality (EKSIK-2, EKSIK-6)
    // ---------------------------------------------------------------
    static void baselines(MilpSolver solver) throws Exception {
        try (PrintWriter out = csv("results/baselines.csv",
                "n,seed,milp_greedy_split,milp_optimal_split,nn_greedy_split,exhaustive_opt,"
                        + "gap_greedy_vs_optsplit,gap_milp_vs_exhaustive")) {
            int[] sizes = {5, 6, 7, 8, 10, 12};
            for (int n : sizes) {
                for (int seed = 1; seed <= SEEDS; seed++) {
                    Instance in = Instance.random(n, seed);
                    MilpSolver.Result r = solver.solve(in, TIME_LIMIT);
                    if (r.sequence == null) continue;
                    Schedule greedy = TourSplitter.split(in, r.sequence, in.Qmax);
                    Schedule optSplit = Baselines.optimalSplit(in, r.sequence, in.Qmax);
                    Schedule nn = TourSplitter.split(in, Baselines.nearestNeighbour(in), in.Qmax);
                    Schedule exh = n <= 7 ? Baselines.exhaustive(in, in.Qmax) : null;
                    boolean gOk = greedy.feasible, oOk = optSplit != null && optSplit.feasible;
                    boolean nOk = nn.feasible, eOk = exh != null && exh.feasible;
                    String gapGreedy = (gOk && oOk)
                            ? String.format("%.4f", (greedy.totalCost() - optSplit.totalCost()) / optSplit.totalCost()) : "NA";
                    String gapExh = (gOk && eOk)
                            ? String.format("%.4f", (greedy.totalCost() - exh.totalCost()) / exh.totalCost()) : "NA";
                    out.printf("%d,%d,%s,%s,%s,%s,%s,%s%n", n, seed,
                            gOk ? String.format("%.2f", greedy.totalCost()) : "NA",
                            oOk ? String.format("%.2f", optSplit.totalCost()) : "NA",
                            nOk ? String.format("%.2f", nn.totalCost()) : "NA",
                            eOk ? String.format("%.2f", exh.totalCost()) : "NA",
                            gapGreedy, gapExh);
                    System.out.printf("baselines n=%d seed=%d greedy=%.0f optsplit=%.0f nn=%.0f%n",
                            n, seed, greedy.totalCost(),
                            optSplit != null ? optSplit.totalCost() : -1, nn.totalCost());
                }
            }
        }
    }

    // ---------------------------------------------------------------
    static PrintWriter csv(String path, String header) throws Exception {
        PrintWriter out = new PrintWriter(path);
        out.println(header);
        return out;
    }

    static double sec(long t0) { return (System.nanoTime() - t0) / 1e9; }
}
