#!/bin/bash
# Gercek Gurobi ile derle+kostur. GUROBI_HOME ayarlanmis olmali.
set -e
javac -cp "$GUROBI_HOME/lib/gurobi.jar" -d out $(find src -name "*.java")
java -cp "out:$GUROBI_HOME/lib/gurobi.jar" mirp.Experiments "${1:-all}"
