package mirp;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Random;

/**
 * Problem instance for the FPP maritime inventory routing problem.
 *
 * Node indexing: 0 = depot, 1..n = plants, n+1 = dummy terminal (open route end).
 * All time units are HOURS, distances NAUTICAL MILES, quantities TONS.
 *
 * VERI POLITIKASI: Test seti tamamen sentetiktir; cografya gercek liman
 * koordinatlari, operasyonel parametreler kamuya acik endustri rakamlarina
 * kalibre edilmistir (base5() ustundeki kalibrasyon notu ve README).
 */
public class Instance {

    public final String name;
    public final int n;              // number of plants
    public final double[][] dist;    // (n+2)x(n+2) nautical miles; dummy column = 0
    public final double v;           // vessel speed (knots = nm/h)
    public final double F;           // charter cost per hour ($/h)
    public final double c;           // unit fuel cost per ton-mile ($)
    public final double w;           // empty weight of vessel (tons)
    public final double PR;          // pumping rate (tons/h)
    public final double S;           // fixed preparation time (h)
    public final double P;           // global lateness penalty coefficient ($ per ton-hour)
    public final double Qmax;        // physical cargo capacity (tons)
    public final double[] CAP;       // [1..n] tank capacity
    public final double[] I0;        // [1..n] initial inventory
    public final double[] CR;        // [1..n] consumption rate (tons/h)

    // Pre-processed parameters (paper Table 2)
    public final double[] alpha;     // [0..n+1], 0 for depot/dummy
    public final double[] beta;      // [0..n+1], 0 for depot/dummy
    public final double[] deadline;  // l_i = I0/CR, [1..n]
    public final double Q;           // demand upper bound
    public final double[] L;         // lateness penalty rate L_i = P*CAP_i
    public final double bigM;

    public Instance(String name, double[][] dist, double v, double F, double c, double w,
                    double PR, double S, double P, double Qmax,
                    double[] CAP, double[] I0, double[] CR) {
        this.name = name;
        this.n = CAP.length - 1;                // arrays are 1-based with dummy slot 0
        this.dist = dist;
        this.v = v; this.F = F; this.c = c; this.w = w;
        this.PR = PR; this.S = S; this.P = P; this.Qmax = Qmax;
        this.CAP = CAP; this.I0 = I0; this.CR = CR;

        alpha = new double[n + 2];
        beta = new double[n + 2];
        deadline = new double[n + 1];
        L = new double[n + 1];
        double q = 0;
        for (int i = 1; i <= n; i++) {
            alpha[i] = S + (CAP[i] - I0[i]) / PR;            // eq (1.1)
            beta[i] = CR[i] / PR;                            // eq (1.2)
            deadline[i] = I0[i] / CR[i];                     // eq (1.3)
            q += CAP[i] - I0[i] + CR[i] * deadline[i];       // eq (1.4)
            L[i] = P * CAP[i];                               // eq (1.5)
        }
        Q = q;
        bigM = computeBigM();
    }

    public double travel(int i, int j) { return dist[i][j] / v; }

    public int dummy() { return n + 1; }

    /** Valid big-M: worst-case completion time bound (paper Sec. 3.3, EKSIK-5). */
    private double computeBigM() {
        double aMax = 0, bMax = 0, tMax = 0, lMax = 0;
        for (int i = 1; i <= n; i++) {
            aMax = Math.max(aMax, alpha[i]);
            bMax = Math.max(bMax, beta[i]);
            lMax = Math.max(lMax, deadline[i]);
        }
        for (int i = 0; i <= n; i++)
            for (int j = 1; j <= n; j++)
                if (i != j) tMax = Math.max(tMax, travel(i, j));
        double u = 0;
        for (int k = 0; k < n; k++) u = (1 + bMax) * u + aMax + tMax;
        return Math.max(u, lMax) * 1.1 + 1.0;
    }

    // ------------------------------------------------------------------
    // Base case study instance — SENTETIK ama kamuya acik endustri
    // rakamlarina kalibre edilmis (makale Sec. 5.1 / EKSIK-7).
    //
    // Kalibrasyon zinciri (kaynaklar README'de, H2'de guncel teyit):
    //  * Santral gucu 8-20 MW; ozgul tuketim ~0.20 kg/kWh (HFO jenerator)
    //      -> CR = MW * 0.20 t/h
    //  * Tank otonomisi tam yukte ~500 saat -> CAP = CR * 500
    //  * Baslangic stoku %70 -> deadline = 0.7 * 500 = 350 h (~14.6 gun)
    //  * Gemi: ~3,000 dwt kiyi urun tankeri; lightship w ~ 1,500 t,
    //      hiz 12.5 kn, kargo pompasi PR ~ 150 t/h, hazirlik S = 1 h
    //  * Kiralama: ~$12,000/gun -> F = 500 $/h
    //  * Yakit: ~7 g / ton-mil, HFO ~$450/ton -> c ~ 0.0032 $/ton-mil
    //  * Ceza: P = 1 $/(ton kapasite x saat gecikme)
    //  * Qmax = 2,000 t (kargo dwt'nin operasyonel kismi)
    // ------------------------------------------------------------------
    public static Instance base5() {
        double[][] coords = {
            {14.69, -17.45},  // 0 depot  (Dakar)
            { 9.51, -13.71},  // 1        (Conakry)   15 MW
            { 8.48, -13.23},  // 2        (Freetown)  10 MW
            { 6.30, -10.80},  // 3        (Monrovia)   8 MW
            { 5.31,  -4.02},  // 4        (Abidjan)   20 MW
            { 5.62,  -0.01},  // 5        (Tema)      12 MW
        };
        int n = 5;
        double[][] d = distMatrix(coords, n);
        double[] CR  = {0, 3.0, 2.0, 1.6, 4.0, 2.4};          // MW * 0.20 t/h
        double[] CAP = new double[n + 1], I0 = new double[n + 1];
        for (int i = 1; i <= n; i++) { CAP[i] = CR[i] * 500; I0[i] = 0.70 * CAP[i]; }
        return new Instance("base5", d, 12.5, 500.0, 0.0032, 1500.0,
                150.0, 1.0, 1.0, 2000.0, CAP, I0, CR);
    }

    /** Random coastal instance for the scaling experiments (paper Sec. 5.1 protocol). */
    public static Instance random(int n, long seed) {
        Random rnd = new Random(seed);
        double[][] coords = new double[n + 1][2];
        coords[0] = new double[]{14.69, -17.45};              // depot fixed at Dakar
        for (int i = 1; i <= n; i++) {
            // coastal band roughly Dakar -> Lagos
            double lat = 4.5 + rnd.nextDouble() * 10.0;
            double lon = -17.0 + rnd.nextDouble() * 20.0;
            coords[i] = new double[]{lat, lon};
        }
        double[][] d = distMatrix(coords, n);
        double[] CAP = new double[n + 1], I0 = new double[n + 1], CR = new double[n + 1];
        for (int i = 1; i <= n; i++) {
            double mw = 5 + rnd.nextDouble() * 25;            // 5-30 MW plant
            CR[i] = mw * 0.20;                                // t/h
            CAP[i] = CR[i] * (400 + rnd.nextDouble() * 200);  // 400-600 h autonomy
            I0[i] = CAP[i] * (0.60 + rnd.nextDouble() * 0.20);// 60-80% full
        }
        // SERVICEABILITY GUARANTEE: all-or-nothing requires CAP_i <= Qmax.
        // Scale the vessel with the fleet it serves (>= calibrated 2,000 t floor).
        double maxCap = 0;
        for (int i = 1; i <= n; i++) maxCap = Math.max(maxCap, CAP[i]);
        double qmax = Math.max(2000.0, 1.2 * maxCap);
        for (int i = 1; i <= n; i++)
            if (CAP[i] > qmax) throw new IllegalStateException(
                    "generator invariant violated: CAP[" + i + "]=" + CAP[i] + " > Qmax=" + qmax);
        return new Instance("rnd-n" + n + "-s" + seed, d, 12.5, 500.0, 0.0032, 1500.0,
                150.0, 1.0, 1.0, qmax, CAP, I0, CR);
    }

    private static double[][] distMatrix(double[][] coords, int n) {
        double[][] d = new double[n + 2][n + 2];
        for (int i = 0; i <= n; i++)
            for (int j = 0; j <= n; j++)
                d[i][j] = haversineNm(coords[i], coords[j]);
        // dummy node: zero distance from everywhere (open route)
        for (int i = 0; i <= n + 1; i++) { d[i][n + 1] = 0; d[n + 1][i] = 0; }
        return d;
    }

    private static double haversineNm(double[] a, double[] b) {
        double R = 6371.0;
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0]))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = 2 * R * Math.asin(Math.sqrt(h));
        return km * 0.539957;                                  // km -> nautical miles
    }

    /**
     * Reproducibility export (paper "Data availability"): writes the full
     * instance -- global parameters, plant table, distance matrix -- as CSV.
     */
    public void exportCsv(String path) throws IOException {
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (PrintWriter out = new PrintWriter(f)) {
            out.printf(Locale.US, "# instance,%s%n", name);
            out.printf(Locale.US, "# n,v_knots,F_per_h,c_per_tonmile,w_tons,PR_tons_h,S_h,P,Qmax_tons%n");
            out.printf(Locale.US, "%d,%.4f,%.4f,%.6f,%.2f,%.2f,%.2f,%.4f,%.2f%n",
                    n, v, F, c, w, PR, S, P, Qmax);
            out.println("# plant,CAP_tons,I0_tons,CR_tons_h,deadline_h");
            for (int i = 1; i <= n; i++)
                out.printf(Locale.US, "%d,%.2f,%.2f,%.4f,%.2f%n", i, CAP[i], I0[i], CR[i], deadline[i]);
            out.println("# distance matrix (nm), rows/cols 0..n+1");
            for (int i = 0; i <= n + 1; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j <= n + 1; j++) {
                    if (j > 0) sb.append(',');
                    sb.append(String.format(Locale.US, "%.2f", dist[i][j]));
                }
                out.println(sb);
            }
        }
    }

    /** Scenario clones for the sensitivity experiments. */
    public Instance withFuelCost(double factor) {
        return new Instance(name + "-fuel" + factor, dist, v, F, c * factor, w, PR, S, P, Qmax, CAP, I0, CR);
    }
    public Instance withCharter(double factor) {
        return new Instance(name + "-charter" + factor, dist, v, F * factor, c, w, PR, S, P, Qmax, CAP, I0, CR);
    }
    public Instance withPumpRate(double factor) {
        return new Instance(name + "-pump" + factor, dist, v, F, c, w, PR * factor, S, P, Qmax, CAP, I0, CR);
    }
    public Instance withConsumption(double factor) {
        double[] cr = CR.clone();
        for (int i = 1; i <= n; i++) cr[i] *= factor;
        return new Instance(name + "-cr" + factor, dist, v, F, c, w, PR, S, P, Qmax, CAP, I0, cr);
    }
    /** Dispatch delayed by `hours`: every tank depletes before the voyage starts. */
    public Instance withDispatchDelay(double hours) {
        double[] i0 = I0.clone();
        for (int i = 1; i <= n; i++) i0[i] = Math.max(0.0, I0[i] - CR[i] * hours);
        return new Instance(name + "-delay" + hours, dist, v, F, c, w, PR, S, P, Qmax, CAP, i0, CR);
    }
    /** Penalty sweep (EKSIK-12): vary the global lateness coefficient P. */
    public Instance withPenalty(double p) {
        return new Instance(name + "-P" + p, dist, v, F, c, w, PR, S, p, Qmax, CAP, I0, CR);
    }
    public Instance withCapacity(double qmax) {
        return new Instance(name + "-cap" + (int) qmax, dist, v, F, c, w, PR, S, P, qmax, CAP, I0, CR);
    }
}
