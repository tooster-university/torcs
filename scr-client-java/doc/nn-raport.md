# TNN torcs (WIP raport)

Genetically evolved neural network.

- Crossover selection: roulette
- Gene crossover: LERPing two parents with random weight
- Mutation: random of every edge weight with given probability (individual or common for chromosome)
- Population: 35
- Elite: 5

## 66 - 2 hidden layers, 6 nodes each. Locked (no) clutch, no reverse

## 777 - 3 hidden layers, 7 nodes each

starting params:

```
- mutation: 0.06
- population: 35, elite: 5
- steps: 3000
- stagnation replacement: false
- common crossover param: true
```

- gen 4 - first breakthrough to 30m
- gen 5 - 40m
- gen 7 - ~90mm, best score 800 (distance ^1.5 * offroad penalty scaler(=0.8)), mean 90
- gen 8 - ~126.477m, mean score 167.01819142690783

Evolving to generation 9

> - Best score was: 3215.781360075472
> - Best distance: 252.812
> - Average score: 438.89902279463996

Evolving to generation 12

> - Best score was: 24946.20842788038
> - Best distance: 990.702
> - Average score: 2046.0870321802881

Evolving to generation 13

> - score was: 26997.770673084844
> - distance: 1044.3
> - score: 3438.7470625269048

Evolving to generation 15

> - Best score was: 41585.84817786897
> - Best distance: 1200.32
> - Average score: 5490.692676345854

```
common problem at this stage - frequent oscillations and gearbox on max
```

```
unlocking clutch and gearbox
```

Evolving to generation 38

> - Best score was: 43133.559180938086
> - Best distance: 1229.92
> - Average score: 5459.644658234455

Evolving to generation 58

> - Best score was: 54230.29728168239
> - Best distance: 1432.72
> - Average score: 10778.900093151786

Evolving to generation 65

> - Best score was: 59264.60331719383
> - Best distance: 1520.07
> - Average score: 12705.936206154924

Evolving to generation 66

> - Best score was: 71766.7728674596
> - Best distance: 1726.96
> - Average score: 13891.231962500839

Evolving to generation 67

> - Best score was: 93300.99452760637
> - Best distance: 2057.11
> - Average score: 16061.305600201671

Evolving to generation 95

> - Best score was: 121672.936489382
> - Best distance: 2455.44
> - Average score: 26359.494209272914

Evolving to generation 101

> - Best score was: 148710.08120671913
> - Best distance: 2806.9
> - Average score: 36447.81137088817

```
Slight oscillations on straight segments, soft start, high RPM oscilating between 3-4, doesn't collide. I'm increasing generations to 6k, max steps to 30000, launching road track - spring (tight and frequent turns, long track). I also add fast quit if we go offroad by 1.3 of track sensor
```

### **WOW**

Evolving to generation 118

> - Best score was: 777708.0308409263
> - Best distance: 9813.36
> - Average score: 37403.10728081172

### ***WOW !!1!11!***

Evolving to generation 135

> - Best score was: 1657336.5765238586
> - Best distance: 14004.7
> - Average score: 206388.66118308454

^ BUT it drives on 1st gear and wobbles XDDD

```
I'm going to sleep and leaving it to evolve
```

```
It's wobbly and it constantly on gear 1... But it drifts on turns and stays on track.
```

```
Adding penalty for wobbling and RPM outside of range and changing cost function
```

Evolving to generation 186

> - Best score was: 16362.06205953353
> - Best distance: 1884.54
> - Average score: 4281.173629222307

```
Shieeet happened - I accidentially launched oval learning on incompatible generation save - deserialized verrsion had [6] hidden layer, but the running config was [7, 7], so sometimes population just broke. On the other hand, it clearly showed, that roulette was rather picky, and [6] networks weren't picked up frequently (once every 5 gens maybe?). That's something worth considering - better scale scores to reduce spread (exp to fractional power???).
```
