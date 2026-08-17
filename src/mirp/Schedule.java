package mirp;

import java.util.ArrayList;
import java.util.List;

/** A fully evaluated (possibly multi-sub-tour) delivery schedule with cost breakdown. */
public class Schedule {

    public final List<List<Integer>> subTours = new ArrayList<>(); // plants per sub-tour
    public final double[] arrival;      // [1..n] arrival time at each plant
    public final double[] delivered;    // [1..n] delivered quantity f_d
    public double totalTime;            // completion time (departure from last plant)
    public double charterCost, emptyFuelCost, cargoFuelCost, penaltyCost;
    public boolean feasible = true;

    public Schedule(int n) {
        arrival = new double[n + 1];
        delivered = new double[n + 1];
    }

    public double totalCost() { return charterCost + emptyFuelCost + cargoFuelCost + penaltyCost; }

    public String routeString() {
        StringBuilder sb = new StringBuilder("0");
        for (int t = 0; t < subTours.size(); t++) {
            if (t > 0) sb.append(" -> 0");
            for (int p : subTours.get(t)) sb.append(" -> ").append(p);
        }
        return sb.toString();
    }

    @Override public String toString() {
        if (!feasible) return "INFEASIBLE (all-or-nothing talep Qmax'i asti — parametreleri kontrol edin)";
        return String.format("cost=%.2f (charter=%.2f, emptyFuel=%.2f, cargoFuel=%.2f, penalty=%.2f) time=%.2f route=[%s]",
                totalCost(), charterCost, emptyFuelCost, cargoFuelCost, penaltyCost, totalTime, routeString());
    }
}
