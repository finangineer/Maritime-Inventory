package mirp;

/**
 * Gurobi GEREKTIRMEYEN hizli test: base5 orneginde sabit bir sirayi
 * kapasiteli/kapasitesiz bolerek Algorithm 1'in davranisini gosterir.
 * Lisans beklemeden sezgisel tarafini dogrulamak icin kullanin:
 *   java -cp out mirp.SplitterDemo
 */
public class SplitterDemo {
    public static void main(String[] args) {
        Instance in = Instance.base5();
        int[] seq = {1, 2, 3, 4, 5};                     // kiyidan asagi dogal sira

        System.out.println("Instance: " + in.name + "  Qmax=" + in.Qmax);
        for (int i = 1; i <= in.n; i++)
            System.out.printf("  plant %d: CAP=%.0f I0=%.0f CR=%.1f deadline=%.1fh alpha=%.2f beta=%.3f%n",
                    i, in.CAP[i], in.I0[i], in.CR[i], in.deadline[i], in.alpha[i], in.beta[i]);

        Schedule inc = TourSplitter.split(in, seq, Double.MAX_VALUE);
        System.out.println("\nIncapacitated : " + inc);

        Schedule cap = TourSplitter.split(in, seq, in.Qmax);
        System.out.println("Capacitated   : " + cap);

        Schedule opt = Baselines.optimalSplit(in, seq, in.Qmax);
        System.out.println("Optimal split : " + opt);

        Schedule nn = TourSplitter.split(in, Baselines.nearestNeighbour(in), in.Qmax);
        System.out.println("NN + split    : " + nn);

        Schedule exh = Baselines.exhaustive(in, in.Qmax);
        System.out.println("Exhaustive opt: " + exh);
    }
}
