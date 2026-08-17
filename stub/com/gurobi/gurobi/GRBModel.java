// COMPILE-CHECK STUB ONLY
package com.gurobi.gurobi;

public class GRBModel {
    public GRBModel(GRBEnv env) throws GRBException {}
    public GRBVar addVar(double lb, double ub, double obj, char type, String name) throws GRBException { return new GRBVar(); }
    public GRBConstr addConstr(GRBLinExpr lhs, char sense, double rhs, String name) throws GRBException { return new GRBConstr(); }
    public GRBConstr addConstr(GRBLinExpr lhs, char sense, GRBLinExpr rhs, String name) throws GRBException { return new GRBConstr(); }
    public void setObjective(GRBLinExpr obj, int sense) throws GRBException {}
    public void optimize() throws GRBException {}
    public void update() throws GRBException {}
    public void dispose() {}
    public int get(GRB.IntAttr a) throws GRBException { return 0; }
    public double get(GRB.DoubleAttr a) throws GRBException { return 0; }
    public void set(GRB.DoubleParam p, double v) throws GRBException {}
    public void set(GRB.IntParam p, int v) throws GRBException {}
}
