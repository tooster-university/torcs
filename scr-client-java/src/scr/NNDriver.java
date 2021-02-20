package scr;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;

import static scr.Config.*;

/**
 * Simple neural net driver. Reads config file from `nn-driver.properties` and stores in static fields.
 */
@SuppressWarnings("unused")
public class NNDriver extends Controller {

    int actionId = -1;
    private NNRankEntry[] rank = new NNRankEntry[POPULATION_SIZE];
    private Genetics.Roulette roulette = null;
    private int currentNetworkID = 0;
    private int currentGenerationId = START_GENERATION;

    {
        for (int i = 0; i < rank.length; i++) {
            if (currentGenerationId == 0) { // if we start anew - generate random networks
                rank[i] = new NNRankEntry();
                if (roulette == null) {
                    rank[i].network = new TorcsNN(NN.nextRandomWeights());
                } else {
                    var cloned = Genetics.cloneGene(rank[roulette.nextRandom()].network.weights);
                    Genetics.mutate(cloned, MUTATION_PROBABILITY);
                    new TorcsNN(cloned);
                }
            } else { // otherwise we have to load ranking from config
                deserializeRank();
            }
        }
    }

    private NNRankEntry getCurrentSpecimen() {return rank[currentNetworkID];}

    @Override
    public Action control(SensorModel sensors) {
        ++actionId;
        var specimen = getCurrentSpecimen();


        // use time and distance for scoring the driver
        // specimen.currentLapTime = sensors.getCurrentLapTime();
        // ! sensors.getDistanceFromStartLine() doesn't start from 0 but from ~1.5k :/
        // ! but would be better to exclude cars maximizing distance travelled in wrong way
        specimen.distance = sensors.getDistanceRaced();
//        if (actionId % 100 == 0) System.err.println(sensors.getDistanceRaced());

        // dead simple network evaluation
        // early kill non-moving cars - replace them with randomly generated networks
        if (actionId > 500 && Math.abs(specimen.distance) < 5.0) {
            actionId = -1;
            System.err.println("Stagnation (replacing)");
            specimen.network = new TorcsNN(TorcsNN.nextRandomWeights());
        }
        return specimen.network.eval(sensors);
    }

    @Override
    public void reset() {
        actionId = -1;
        var specimen = getCurrentSpecimen();


        specimen.assignScore();

        System.out.println("generation:" + currentGenerationId +
                " specimen:" + currentNetworkID +
                " traveled:" + specimen.distance +
                " scored:" + specimen.score);

        if (Client.verbose)
            System.err.println("here is the network:\n" + specimen.network.toString());

        // fixme: remove this ???
//        specimen.distance = 0.0;
//        specimen.currentLapTime = 0.0;

        ++currentNetworkID;
        if (currentNetworkID == POPULATION_SIZE) {
            currentNetworkID = 0;
            evolvePopulation();
        }
    }

    @Override
    public void shutdown() { serializeRank("cmpl"); }

    private void evolvePopulation() {
        // crossover children
        var newRank = new NNRankEntry[POPULATION_SIZE];

        Arrays.sort(rank, Collections.reverseOrder()); // make best first for convenience
        System.out.println("Overriding current population with sorted one"); // GUI will show best individual first
        serializeRank("");

        // advance generation
        ++currentGenerationId;

        var rawScores = Arrays.stream(rank).mapToDouble(rank -> rank.score).toArray();
        var mean = Arrays.stream(rawScores).average().getAsDouble();
        System.out.println("Evolving to generation " + currentGenerationId + "\n"+
                "\tBest score was:\t" + rank[0].score + "\n" +
                "\tBest distance:\t" + rank[0].distance + "\n" +
                "\tAverage score:\t" + mean);
        roulette = new Genetics.Roulette(rawScores);
        for (int i = 0; i < newRank.length; i++) {
            if (i < ELITE) { // elitism
                newRank[i] = rank[i];
            } else { // crossover and mutation
                newRank[i] = new NNRankEntry();
                // perform crossover on parents chosen using roulette and produce 1 offspring
                newRank[i].network = new TorcsNN(Genetics.lerpCrossover(
                        rank[roulette.nextRandom()].network.weights,
                        rank[roulette.nextRandom()].network.weights,
                        LERP_CROSSOVER_COMMON_RANDOM)
                );
                // randomly mutate edges with given probability
                    Genetics.mutate(newRank[i].network.weights, MUTATION_PROBABILITY);
            }
        }

        rank = newRank;
        serializeRank("");
    }

    /**
     * Serializes network to file (default format is data/nn-GEN-IDX.dat, where GEN is generation number and IDX
     * specimen)
     */
    private void serializeRank(String postfix) {
        try {
            var path = String.join(FILE_SEPARATOR,
                    FILE_DIR + FILE_PREFIX,
                    currentGenerationId + (postfix.isBlank() ? "" : FILE_SEPARATOR + postfix)
            ) + FILE_EXT;
            System.out.println("Serializing population to file `" + path + "`");
            FileOutputStream fos = new FileOutputStream(path);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(rank);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // deserializes from file using startGeneration, prefix and index
    private void deserializeRank() {

        var path = String.join(FILE_SEPARATOR,
                FILE_DIR + FILE_PREFIX,
                START_GENERATION + FILE_EXT);
        try {
            FileInputStream fis = new FileInputStream(path);
            ObjectInputStream ois = new ObjectInputStream(fis);
            rank = (NNRankEntry[]) ois.readObject();
            roulette = new Genetics.Roulette(Arrays.stream(rank).mapToDouble(r -> r.score).toArray());
        } catch (FileNotFoundException e) {
            System.err.println("Can't find serialized file '" + path + "'");
            e.printStackTrace();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /** stores network with it's score and params used to calculate it */
    public static class NNRankEntry implements Serializable, Comparable<NNRankEntry> {
        @Serial
        private static final long serialVersionUID = 6953670818228571475L;

        public TorcsNN network;

        public double score = 0.0; //score of neural net
        private transient double distance;
//        private transient double currentLapTime = 0.0;

        public void assignScore() {
            score = Math.signum(distance) * Math.pow(Math.abs(distance), 1.5);
        }

        @Override
        public int compareTo(NNRankEntry other) {
            return (int) Math.round(Math.signum(score - other.score));
        }
    }
}
