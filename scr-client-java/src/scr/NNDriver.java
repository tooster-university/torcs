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

    private static final int LOWER_RPM = 2000;
    private static final int UPPER_RPM = 3200;
    private static final int GEAR_CHANGE_TIMEFRAME = 100;
    // valid ranges of RPM
    private static final int[] GEAR_LOWER_RPM = new int[]{-15000, 0, 0, 4600, 4900, 5100, 5200, 5300};
    private static final int[] GEAR_UPPER_RPM = new int[]{15000, 0, 7700, 7000, 6700, 6500, 6300, 15000};
    // abs(steer) < magnitude is said to be steady
    private static final double STEER_MAGNITUDE = 0.1;
    //
    int actionId = -1;
    private NNRankEntry[] rank = new NNRankEntry[POPULATION_SIZE];
    private Genetics.Roulette roulette = null;
    private int currentNetworkID = 0;
    private int currentGenerationId = START_GENERATION;
    private int gearChangeTimestep;

    {
        if (currentGenerationId == 0) { // if we start anew - generate random networks
            for (int i = 0; i < rank.length; i++) {
                rank[i] = new NNRankEntry();
                if (roulette == null) {
                    rank[i].network = new TorcsNN(NN.nextRandomWeights());
                } else {
                    var cloned = Genetics.cloneGene(rank[roulette.nextRandom()].network.weights);
                    Genetics.mutate(cloned, MUTATION_PROBABILITY);
                    new TorcsNN(cloned);
                }
            }
        } else { // otherwise we have to load ranking from config
            deserializeRank();
        }
    }

    private NNRankEntry getCurrentSpecimen() {return rank[currentNetworkID];}

    @Override
    public Action control(SensorModel sensors) {
        ++actionId;
        var specimen = getCurrentSpecimen();

        var action = specimen.network.eval(sensors);

        // use time and distance for scoring the driver
        // specimen.currentLapTime = sensors.getCurrentLapTime();
        // ! sensors.getDistanceFromStartLine() doesn't start from 0 but from ~1.5k :/
        // ! but would be better to exclude cars maximizing distance travelled in wrong way
        specimen.distance = sensors.getDistanceRaced();
        if (Math.abs(sensors.getTrackPosition()) >= 1.0) {
            specimen.offtrackPenaltyScaler = 0.8;
            if (OFFROAD_KILL && Math.abs(sensors.getTrackPosition()) >= 1.3)
                action.restartRace = true;
        }

//        if (actionId % 100 == 0) System.err.println(sensors.getDistanceRaced());


        // dead simple network evaluation
        // early kill non-moving cars - replace them with randomly generated networks

        if (actionId > 500 && Math.abs(specimen.distance) < 5.0 && STAGNATION_REPLACEMENT) {
            System.err.println("Stagnation - replacing with random network");
            specimen.network = new TorcsNN(TorcsNN.nextRandomWeights());
            action.restartRace = true;
        }

        // simple gearbox algorithm
        var currentGear = sensors.getGear();
        var currentRPM = sensors.getRPM();
        // steer overshoot counter
        if (action.steering < -STEER_MAGNITUDE)
            specimen.steerCounter[0]++;
        else if (action.steering > +STEER_MAGNITUDE)
            specimen.steerCounter[2]++;
        else
            specimen.steerCounter[1]++;


        // RPM under/overshoot counter
        if (currentRPM < GEAR_LOWER_RPM[currentGear + 1])
            specimen.rpmCounter[0]++; // under RPM
        else if (currentRPM > GEAR_UPPER_RPM[currentGear + 1])
            specimen.rpmCounter[2]++;
        else
            specimen.rpmCounter[1]++;

//        if(currentGear == 0) {
//            action.gear = 1;
//            gearChangeTimestep = actionId;
//        }
//        if(currentRPM > UPPER_RPM && actionId - gearChangeTimestep > GEAR_CHANGE_TIMEFRAME) {
//            action.gear = currentGear + 1;
//            gearChangeTimestep = actionId;
//        }
//        if(currentGear < LOWER_RPM && currentGear > 1 && actionId - gearChangeTimestep > GEAR_CHANGE_TIMEFRAME) {
//            action.gear = currentGear - 1;
//            gearChangeTimestep = actionId;
//        }

        return action;
    }

    @Override
    public void reset() {
        actionId = -1;
        var specimen = getCurrentSpecimen();


        specimen.assignScore();

        System.out.println("generation:" + currentGenerationId +
                "\tspecimen:" + currentNetworkID +
                "\ttraveled:" + specimen.distance +
                "\tscored:" + specimen.score);

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
    public void shutdown() { serializeRank("end"); }

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
        System.out.println("Evolving to generation " + currentGenerationId + "\n" +
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
            var genPath = String.join(FILE_SEPARATOR,
                    FILE_DIR + FILE_PREFIX,
                    currentGenerationId + (postfix.isBlank() ? "" : FILE_SEPARATOR + postfix)
            ) + FILE_EXT;
            System.out.println("Serializing population to file `" + genPath + "`");
            FileOutputStream fos = new FileOutputStream(genPath);
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
        //        private transient double currentLapTime = 0.0;
        private static final double RPM_PENALTY = 0.2;
        private static final double STEER_PENALTY = 0.2; // overshoot get this part of score
        public TorcsNN network;
        public double score = 0.0; //score of neural net
        public double offtrackPenaltyScaler = 1.0;
        // to avoid calculating wobbling frequency by fft, simple heuristic to assign bonus for cars, that steer
        // for the majority of time to the middle
        public transient int[] steerCounter = new int[3]; // counts fraction of actions taken to steer left, ~middle,
        // right
        public transient int[] rpmCounter = new int[3]; // counts actions for RPM range. steady RPM get bonus
        private double distance;

        public void assignScore() {
            switch (SCORE_MODE) {
                case "distance":
                    score = offtrackPenaltyScaler * Math.signum(distance) * Math.pow(Math.abs(distance), 1.5);
                    break;
                case "steady":
                    if (offtrackPenaltyScaler < 1.0) {
                        score = 0.2 * Math.signum(distance) * Math.pow(Math.abs(distance), 1.5);
                        break;
                    }
                    score = offtrackPenaltyScaler * Math.signum(distance) * Math.pow(Math.abs(distance), 1.5);
                    double totalSteer = Arrays.stream(steerCounter).sum();
                    score = score * (steerCounter[0] + steerCounter[2]) * STEER_PENALTY / totalSteer +
                            score * steerCounter[1] * (1.0 - STEER_PENALTY) / totalSteer;
                    double totalRPM = Arrays.stream(rpmCounter).sum();
                    score = score * (rpmCounter[0] + rpmCounter[2]) * RPM_PENALTY / totalSteer +
                            score * rpmCounter[1] * (1.0 - RPM_PENALTY) / totalSteer;
            }
        }

        /**
         * Called when object is to be deserialized from a stream.
         *
         * @param stream the stream to read the object from.
         *
         * @throws IOException            if the object could not be read.
         * @throws ClassNotFoundException if a class required to read the object could not be found.
         * @see <a href="http://download.oracle.com/javase/1.3/docs/guide/serialization/spec/input.doc4.html">The Java
         * Object Serialization Specification</a>
         */
        @Serial
        private void readObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
            stream.defaultReadObject();
            steerCounter = new int[3];
            rpmCounter = new int[3];
        }

        @Override
        public int compareTo(NNRankEntry other) {
            return (int) Math.round(Math.signum(score - other.score));
        }
    }
}
