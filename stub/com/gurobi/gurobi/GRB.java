// COMPILE-CHECK STUB ONLY — gercek gurobi.jar'in yerine gecmez, dagitima dahil edilmez.
package com.gurobi.gurobi;

public final class GRB {
    public static final char BINARY = 'B';
    public static final char CONTINUOUS = 'C';
    public static final char INTEGER = 'I';
    public static final char LESS_EQUAL = '<';
    public static final char GREATER_EQUAL = '>';
    public static final char EQUAL = '=';
    public static final int MINIMIZE = 1;
    public static final int MAXIMIZE = -1;

    public static final class Status {
        public static final int OPTIMAL = 2;
        public static final int INFEASIBLE = 3;
        public static final int TIME_LIMIT = 9;
    }

    public enum DoubleAttr { X, Xn, Obj, ObjVal, ObjBound, Runtime, MIPGap, PoolObjVal }
    public enum IntAttr { NumVars, NumConstrs, Status, SolCount }
    public enum StringAttr { VarName }
    public enum DoubleParam { TimeLimit, MIPGap }
    public enum IntParam { OutputFlag, Threads, PoolSolutions, PoolSearchMode, SolutionNumber }
}
