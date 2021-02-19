package scr;

import java.util.Arrays;
import java.util.Random;

public class Genetics {

    public static class Roulette {
        /** cumulative contribution to roulette score. Index represents one solution */
        double[] ranking;

        public Roulette(double[] rawScores) {
            double sum = Arrays.stream(rawScores).sum();
            ranking = new double[rawScores.length];
            ranking[0] = rawScores[0] / sum;
            for (int i = 1; i < ranking.length; i++)
                ranking[i] = rawScores[i] / sum + ranking[i - 1];
        }

        /**
         * @return returns index of next random child weighted by roulette
         */
        int nextRandom() {
            int idx = Arrays.binarySearch(ranking, Math.random());
            if (idx < 0) idx = -idx - 1; // this is the usual case, cuz it's fairly impossible for random double to be
            // in array. We get lower bound of rank to find matching specimen
            return idx;
        }
    }

    /**
     * lerps between two genes: result = w1 + f(w2-w1)
     *
     * @param w1     first parent
     * @param w2     second parent
     * @param common should it use common random value for lerping or individual for each weight
     *
     * @return returns new child weights gene
     */
    public static double[][][] lerpCrossover(double[][][] w1, double[][][] w2, boolean common) {
        assertGenesCompatible(w1, w2);
        var result = cloneGene(w1);
        double f = Math.random(); // crossover point
        for (int srcLayer = 0; srcLayer < result.length; srcLayer++)
            for (int dst = 0; dst < result[srcLayer].length; dst++)
                for (int src = 0; src < result[srcLayer][dst].length; src++)
                    result[srcLayer][dst][src] +=
                            (common ? f : Math.random()) *
                                    (w2[srcLayer][dst][src] - result[srcLayer][dst][src]);

        return result;
    }

    /**
     * Randomly mutates each weight in network (beside bias input) with given probability
     * @param w weights to mutate
     * @param singleMutationProbability probability of mutating a single edge
     */
    public static void mutate(double[][][] w, double singleMutationProbability){
        for (int srcLayer = 0; srcLayer < w.length; srcLayer++)
            for (int dst = 1; dst < w[srcLayer].length; dst++) // skip bias
                for (int src = 0; src < w[srcLayer][dst].length; src++)
                    if(Math.random() < singleMutationProbability)
                        w[srcLayer][dst][src] = NN.nextRandomWeight();
    }

    /** Clones weights */
    public static double[][][] cloneGene(double[][][] w) {
        double[][][] result = new double[w.length][][];
        for (int srcLayer = 0; srcLayer < w.length; srcLayer++) {
            result[srcLayer] = new double[w[srcLayer].length][];
            for (int dst = 0; dst < w[srcLayer].length; dst++)
                result[srcLayer][dst] = w[srcLayer][dst].clone();
        }
        return result;
    }

    // used to check if weights have compatible sizes
    public static void assertGenesCompatible(double[][][] w1, double[][][] w2) {
        assert (w1.length == w2.length);
        for (int srcLayer = 0; srcLayer < w1.length; srcLayer++) {
            assert (w1[srcLayer].length == w2[srcLayer].length);
            for (int dst = 0; dst < w1[srcLayer].length; dst++)
                assert (w1[srcLayer][dst].length == w1[srcLayer][dst].length);
        }
    }

}
