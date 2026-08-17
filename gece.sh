#!/bin/bash
CP="out;C:/gurobi1301/win64/lib/gurobi.jar"
echo "=== [1/3] baselines basliyor: $(date) ==="
java -cp "$CP" mirp.Experiments baselines
echo "=== [2/3] capacity_grid basliyor: $(date) ==="
java -cp "$CP" mirp.Experiments capacity_grid
NA=$(grep -c "NA" results/baselines.csv)
echo "=== baselines.csv icinde NA gecen satir sayisi: $NA ==="
if [ "$NA" -ge 20 ]; then
  echo "!!! DURDURULDU: NA sayisi yuksek ($NA). scaling BASLATILMADI."
  echo "!!! Sabah baselines.csv dosyasini Claude'a gonderin."
  exit 1
fi
echo "=== [3/3] scaling basliyor: $(date) ==="
java -cp "$CP" mirp.Experiments scaling
echo "=== HEPSI BITTI: $(date) ==="
