# MIRP/FPP Deney Paketi — Java + Gurobi

Makaledeki tüm hesaplamalı çalışmanın (Section 5) Java uygulaması.
MILP formülasyonu (denklem 1–10) Gurobi ile, tur bölme sezgiseli (Algorithm 1)
ve baseline'lar saf Java ile yazılmıştır.

## Dosyalar

| Dosya | İçerik |
|---|---|
| `src/mirp/Instance.java`    | Veri modeli, ön-işleme (alpha, beta, deadline, Q, L, big-M), Batı Afrika taban örneği, rastgele instance üreteci, duyarlılık senaryo klonları |
| `src/mirp/MilpSolver.java`  | Gurobi MILP (denklem 1–10) + Top-N alternatif için exclusion cut |
| `src/mirp/TourSplitter.java`| İleri-bakışlı tur bölme (Algorithm 1) + maliyet muhasebesi |
| `src/mirp/Baselines.java`   | Nearest-neighbour, optimal split (2^(n-1) enumerasyon), exhaustive optimum (n≤8) |
| `src/mirp/Experiments.java` | Tüm deney koşuları, `results/*.csv` çıktıları |
| `src/mirp/SplitterDemo.java`| Gurobi GEREKTİRMEYEN hızlı doğrulama demosu |
| `stub/`                     | Yalnızca derleme/demo için Gurobi API taklidi — DENEYLERDE KULLANILMAZ |

## Gereksinimler

- JDK 17+ (21 önerilir)
- Gurobi 11 veya 12 + lisans (akademik lisans ücretsiz: gurobi.com/academia)
  - Gurobi ≤ 10 kullanıyorsanız `MilpSolver.java` ve `Experiments.java` başındaki
    `import com.gurobi.gurobi.*;` satırını `import gurobi.*;` yapın.

## Derleme ve çalıştırma (gerçek Gurobi ile)

```bash
export GUROBI_HOME=/opt/gurobi1200/linux64        # kendi kurulumunuza gore
javac -cp "$GUROBI_HOME/lib/gurobi.jar" -d out $(find src -name "*.java")

# tum deneyler (uzun surer; once tekil deneyle test edin):
java -cp "out:$GUROBI_HOME/lib/gurobi.jar" mirp.Experiments all

# tekil deneyler:
java -cp "out:$GUROBI_HOME/lib/gurobi.jar" mirp.Experiments capacity
java -cp "out:$GUROBI_HOME/lib/gurobi.jar" mirp.Experiments rank
java -cp "out:$GUROBI_HOME/lib/gurobi.jar" mirp.Experiments sensitivity
java -cp "out:$GUROBI_HOME/lib/gurobi.jar" mirp.Experiments baselines
java -cp "out:$GUROBI_HOME/lib/gurobi.jar" mirp.Experiments scaling
```

Windows'ta classpath ayıracı `;` ve `gurobi.jar` yolu tipik olarak
`C:\gurobi1200\win64\lib\gurobi.jar`.

## Gurobi olmadan hızlı test

```bash
javac -d out-stub $(find stub src -name "*.java")
java -cp out-stub mirp.SplitterDemo
```

Bu demo sezgiseli, optimal split'i ve exhaustive optimumu taban örnekte
çalıştırır; MILP çağırmaz. `stub/` klasörü gerçek çözücü DEĞİLDİR —
`mirp.Experiments`'ı stub ile çalıştırırsanız MILP anlamsız (sıfır) sonuç verir.

## Deney ↔ makale eşlemesi

| Komut | Makaledeki yeri | CSV |
|---|---|---|
| `scaling`     | Table 3 + EKSIK-10 (8–30 santral, gap) | `results/scaling.csv` |
| `capacity`    | Table 4 (4000t vs 2000t)               | `results/capacity.csv` |
| `rank`        | Table 5–6 + EKSIK-11 (rank reversal)   | `results/rank_reversal.csv` |
| `sensitivity` | Table 7–8 + EKSIK-12 (kapasite ızgarası)| `results/sensitivity.csv`, `results/capacity_grid.csv` |
| `baselines`   | EKSIK-2/6 (NN, optimal split, exhaustive, gap) | `results/baselines.csv` |

## Veri politikası — sentetik, kalibre edilmiş, yayınlanabilir

Test seti TAMAMEN SENTETİKTİR (28 Tem kararı): coğrafya gerçek liman
koordinatları (Dakar deposu + Conakry/Freetown/Monrovia/Abidjan/Tema),
operasyonel parametreler kamuya açık endüstri rakamlarına kalibredir:

| Parametre | Değer | Dayanak (H2'de güncel teyit) |
|---|---|---|
| Santral gücü → tüketim | 5–30 MW × 0.20 kg/kWh → CR 1–6 t/h | HFO jeneratör özgül tüketimi |
| Tank otonomisi | ~400–600 h → CAP = CR × otonomi | FPP operasyon pratiği |
| Gemi | ~3.000 dwt kıyı tankeri, w=1.500 t, 12.5 kn | tanker sınıf verileri |
| Kiralama F | 500 $/h (≈ $12.000/gün) | küçük tanker time-charter seviyeleri |
| Yakıt c | 0.0032 $/ton-mil (≈7 g/ton-mil × ~$450/t HFO) | bunker fiyat endeksleri |
| Pompa PR | 150 t/h | kargo pompası katalog değerleri |
| Qmax | 2.000 t | kargo dwt'nin operasyonel kısmı |

Deney koşuları her instance'ı `results/instances/*.csv` olarak dışa aktarır;
bu klasör GitHub'a konarak makaledeki "Data availability" beyanı karşılanır.
Üretilen sayılar artık doğrudan makaleye girer (tez tablolarıyla eşleşme
beklenmez — tez sayıları taslakta geçici yer tutucudur).

## Doğrulanmış davranışlar (stub demo çıktısı, kalibre edilmiş taban örnek)

- Kapasitesiz mod: tek açık rota, 127.8 saat, ceza 0, maliyet ~$78k.
- 2.000 t limitle açgözlü bölme: 3 santralden sonra depo dönüşü + son
  teslimatlarda deadline aşımı cezası (~$27k) — kapasite kısıtının bedeli görünür.
- Optimal split açgözlüden %16.6 daha ucuz ve cezasız — EKSIK-6 kıyasının verisi.
- Exhaustive optimum (farklı sıra: 3-4-5 | 1-2) açgözlüden %38 daha ucuz —
  kapasite altında sıra seçiminin önemi; rank-reversal anlatısını güçlendirir.
