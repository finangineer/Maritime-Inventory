package mirp;

import com.gurobi.gurobi.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Incapacitated MILP (paper eqs (1)-(10)) solved with Gurobi.
 *
 * Gurobi 11/12 Java API (package com.gurobi.gurobi).
 * Gurobi <= 10 kullaniyorsaniz import satirini `import gurobi.*;` yapin.
 */
public class MilpSolver implements AutoCloseable {

    public static class Result {
        public int[] sequence;          // plants in visiting order
        public double objVal, objBound, mipGap, runtime;
        public int numVars, numConstrs;
        public double[] u;              // arrival times, [0..n+1]
        public boolean optimal;
    }

    private final GRBEnv env;

    public MilpSolver() throws GRBException {
        env = new GRBEnv();
        env.set(GRB.IntParam.OutputFlag, 0);
    }

    public Result solve(Instance in, double timeLimitSec) throws GRBException {
        return solve(in, timeLimitSec, new ArrayList<>());
    }

    /**
     * Solve with optional exclusion cuts: each element of `excludedRoutes` is a
     * previously found arc set; the cut sum_{(i,j) in A*} x_ij <= |A*|-1 forbids
     * exactly that route (thesis "constraint injection" for Top-N alternatives).
     */
    public Result solve(Instance in, double timeLimitSec, List<int[][]> excludedRoutes) throws GRBException {
        int n = in.n, D = in.dummy();
        GRBModel model = new GRBModel(env);
        model.set(GRB.DoubleParam.TimeLimit, timeLimitSec);

        // ---- variables ------------------------------------------------
        // arcs: 0->C, C->C (i!=j), C->D
        GRBVar[][] x = new GRBVar[n + 2][n + 2];
        GRBVar[][] f = new GRBVar[n + 2][n + 2];
        for (int i = 0; i <= n; i++) {
            for (int j = 1; j <= n + 1; j++) {
                if (i == j) continue;
                if (i == 0 && j == D) continue;               // no empty voyage
                x[i][j] = model.addVar(0, 1, 0, GRB.BINARY, "x_" + i + "_" + j);
                f[i][j] = model.addVar(0, in.Q, 0, GRB.CONTINUOUS, "f_" + i + "_" + j);
            }
        }
        GRBVar[] u = new GRBVar[n + 2];
        for (int i = 0; i <= n + 1; i++)
            u[i] = model.addVar(0, in.bigM, 0, GRB.CONTINUOUS, "u_" + i);
        GRBVar[] sigma = new GRBVar[n + 1];
        for (int i = 1; i <= n; i++)
            sigma[i] = model.addVar(0, in.bigM, 0, GRB.CONTINUOUS, "sigma_" + i);

        // ---- objective (1) -------------------------------------------
        GRBLinExpr obj = new GRBLinExpr();
        obj.addTerm(in.F, u[D]);
        for (int i = 0; i <= n; i++)
            for (int j = 1; j <= n + 1; j++)
                if (x[i][j] != null) {
                    obj.addTerm(in.c * in.dist[i][j] * in.w, x[i][j]);
                    obj.addTerm(in.c * in.dist[i][j], f[i][j]);
                }
        for (int i = 1; i <= n; i++) obj.addTerm(in.L[i], sigma[i]);
        model.setObjective(obj, GRB.MINIMIZE);

        // ---- constraints ---------------------------------------------
        // (2) depart depot once
        GRBLinExpr e = new GRBLinExpr();
        for (int j = 1; j <= n; j++) e.addTerm(1, x[0][j]);
        model.addConstr(e, GRB.EQUAL, 1, "depart");
        // (3) arrive dummy once
        e = new GRBLinExpr();
        for (int i = 1; i <= n; i++) e.addTerm(1, x[i][D]);
        model.addConstr(e, GRB.EQUAL, 1, "dummy");
        // (4) flow conservation at every plant (in-degree = out-degree = 1)
        for (int j = 1; j <= n; j++) {
            GRBLinExpr inFlow = new GRBLinExpr(), outFlow = new GRBLinExpr();
            for (int i = 0; i <= n; i++) if (x[i][j] != null) inFlow.addTerm(1, x[i][j]);
            for (int k = 1; k <= n + 1; k++) if (x[j][k] != null) outFlow.addTerm(1, x[j][k]);
            model.addConstr(inFlow, GRB.EQUAL, 1, "in_" + j);
            model.addConstr(outFlow, GRB.EQUAL, 1, "out_" + j);
        }
        // (5) time propagation + subtour elimination (time-indexed MTZ)
        for (int i = 0; i <= n; i++)
            for (int j = 1; j <= n + 1; j++) {
                if (x[i][j] == null) continue;
                GRBLinExpr lhs = new GRBLinExpr();
                lhs.addTerm(1 + in.beta[i], u[i]);
                lhs.addConstant(in.alpha[i] + in.travel(i, j) - in.bigM);
                lhs.addTerm(in.bigM, x[i][j]);
                lhs.addTerm(-1, u[j]);
                model.addConstr(lhs, GRB.LESS_EQUAL, 0, "mtz_" + i + "_" + j);
            }
        // (6) continuous-time inventory balance
        for (int i = 1; i <= n; i++) {
            GRBLinExpr bal = new GRBLinExpr();
            for (int k = 0; k <= n; k++) if (f[k][i] != null) bal.addTerm(1, f[k][i]);
            for (int j = 1; j <= n + 1; j++) if (f[i][j] != null) bal.addTerm(-1, f[i][j]);
            bal.addTerm(-in.CR[i], u[i]);
            model.addConstr(bal, GRB.EQUAL, in.CAP[i] - in.I0[i], "inv_" + i);
        }
        // (7) flow-arc linking
        for (int i = 0; i <= n; i++)
            for (int j = 1; j <= n + 1; j++)
                if (f[i][j] != null) {
                    GRBLinExpr link = new GRBLinExpr();
                    link.addTerm(1, f[i][j]);
                    link.addTerm(-in.Q, x[i][j]);
                    model.addConstr(link, GRB.LESS_EQUAL, 0, "cap_" + i + "_" + j);
                }
        // (8) soft deadlines
        for (int i = 1; i <= n; i++) {
            GRBLinExpr dl = new GRBLinExpr();
            dl.addTerm(1, u[i]);
            dl.addTerm(-1, sigma[i]);
            model.addConstr(dl, GRB.LESS_EQUAL, in.deadline[i], "dl_" + i);
        }
        // (9) clock start
        GRBLinExpr clock = new GRBLinExpr();
        clock.addTerm(1, u[0]);
        model.addConstr(clock, GRB.EQUAL, 0, "clock");

        // exclusion cuts for Top-N alternatives
        int cutId = 0;
        for (int[][] arcs : excludedRoutes) {
            GRBLinExpr cut = new GRBLinExpr();
            for (int[] a : arcs) cut.addTerm(1, x[a[0]][a[1]]);
            model.addConstr(cut, GRB.LESS_EQUAL, arcs.length - 1, "excl_" + (cutId++));
        }

        // ---- solve ----------------------------------------------------
        model.optimize();

        Result r = new Result();
        r.numVars = model.get(GRB.IntAttr.NumVars);
        r.numConstrs = model.get(GRB.IntAttr.NumConstrs);
        r.runtime = model.get(GRB.DoubleAttr.Runtime);
        int status = model.get(GRB.IntAttr.Status);
        r.optimal = status == GRB.Status.OPTIMAL;
        if (model.get(GRB.IntAttr.SolCount) > 0) {
            r.objVal = model.get(GRB.DoubleAttr.ObjVal);
            r.objBound = model.get(GRB.DoubleAttr.ObjBound);
            r.mipGap = model.get(GRB.DoubleAttr.MIPGap);
            r.u = new double[n + 2];
            for (int i = 0; i <= n + 1; i++) r.u[i] = u[i].get(GRB.DoubleAttr.X);
            r.sequence = extractSequence(in, x);
        }
        model.dispose();
        return r;
    }

    /** Follow the x arcs from the depot to recover the visiting order. */
    private int[] extractSequence(Instance in, GRBVar[][] x) throws GRBException {
        int n = in.n;
        int[] seq = new int[n];
        int cur = 0;
        for (int k = 0; k < n; k++) {
            for (int j = 1; j <= n; j++) {
                if (j == cur || x[cur][j] == null) continue;
                if (x[cur][j].get(GRB.DoubleAttr.X) > 0.5) { seq[k] = j; cur = j; break; }
            }
        }
        return seq;
    }

    /** Arc set of a sequence (for exclusion cuts), incl. depot and dummy arcs. */
    public static int[][] arcsOf(Instance in, int[] seq) {
        int[][] arcs = new int[seq.length + 1][2];
        int prev = 0;
        for (int k = 0; k < seq.length; k++) {
            arcs[k] = new int[]{prev, seq[k]};
            prev = seq[k];
        }
        arcs[seq.length] = new int[]{prev, in.dummy()};
        return arcs;
    }

    @Override public void close() throws GRBException { env.dispose(); }
}
