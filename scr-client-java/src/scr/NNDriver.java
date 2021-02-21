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

    // valid ranges of RPM for consecutive gears
    // @fuckoff
    //                                                     R      N  1     2     3     4     5     6
    private static final int[] GEAR_LOWER_RPM = new int[]{ 100,   0, 0,    4600, 4900, 5100, 5200, 5300  };
    private static final int[] GEAR_UPPER_RPM = new int[]{ 15000, 0, 7700, 7000, 6700, 6500, 6300, 15000 };
    // @fuckon

    private static final double STEER_MAGNITUDE                 = 0.1; // abs(steer) < magnitude is said to be steady
    private static final double MAXIMUM_ACCEPTABLE_TRACK_OFFSET = 1.3; // in track width, how far is acceptable
    //
    private int actionId = -1;
    private double lastSteer = 0.0;
    private NNRankEntry[]     rank                = new NNRankEntry[POPULATION_SIZE];
    private Genetics.Roulette roulette            = null;
    private int               currentNetworkID    = 0;
    private int               currentGenerationId = START_GENERATION;

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

        // calculate average steering difference for ride smoothing
        specimen.avgAbsSteerDiff.add(Math.abs(action.steering - lastSteer));
        lastSteer = action.steering;

        // use time and distance for scoring the driver
        // specimen.currentLapTime = sensors.getCurrentLapTime();
        // ! sensors.getDistanceFromStartLine() doesn't start from 0 but from ~1.5k :/
        // ! but would be better to exclude cars maximizing distance travelled in wrong way
        specimen.distance = sensors.getDistanceRaced();
        if (Math.abs(sensors.getTrackPosition()) >= 1.0) {
            specimen.offroadCounter++;
            if (OFFROAD_KILL && Math.abs(sensors.getTrackPosition()) >= MAXIMUM_ACCEPTABLE_TRACK_OFFSET) {
                System.err.println("Out of track kill");
                action.restartRace = true;
            }
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
        lastSteer = 0.0;
        var specimen = getCurrentSpecimen();


        specimen.assignScore();

        System.out.println("generation:" + currentGenerationId +
                                   " specimen:" + String.format("%-3s", currentNetworkID) +
                                   " traveled:" + String.format("%+-10.2f", specimen.distance) +
                                   " scored:" + String.format("%+-13.3f", specimen.score) +
                                   " offroad:" + String.format("%-8d", specimen.offroadCounter) +
                                   " avgSteerDiff:" + String.format("%-8.2f", specimen.avgAbsSteerDiff.avg) +
                                   " steer:" + String.format("%-20s", Arrays.toString(specimen.steerCounter)) +
                                   " rpm:" + String.format("%-24s", Arrays.toString(specimen.rpmCounter))
        );

        if (Client.verbose)
            System.out.println("network:\n" + specimen.network.toString());

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
        serializeRank(SORTED_OVERRIDE ? "" : "sorted");

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
        private static final long   serialVersionUID = 6953670818228571475L;
        //        private transient double currentLapTime = 0.0;
        private static final double RPM_PENALTY      = -0.2;
        private static final double STEER_PENALTY    = -0.2; //
        private static final double OFFROAD_PENALTY  = 0.7; // multiply score by this if out of track
        private static final    int OFFROAD_THRESHOLD  = 30; // how long to be offroad to count as shit


        public  TorcsNN network;
        private double  score = 0.0; //score of neural net
        private double  distance;

        private transient int                     offroadCounter; // counts actions where car is off the road
        private transient int[]                   steerCounter    = new int[3]; // counts # of actions taken to steer
        // left, ~middle, right
        private transient int[]                   rpmCounter      = new int[3]; // counts # of actions for RPM
        // below, in and above range
        // running average for absolute steer difference. High values would mean rapid steering
        private transient Genetics.RunningAverage avgAbsSteerDiff = new Genetics.RunningAverage();

        public void assignScore() {
            var OFFROAD_SCALER = offroadCounter > OFFROAD_THRESHOLD ? OFFROAD_PENALTY : 1.0; // lowers score considerably
            switch (SCORE_MODE) {
                case "distance" -> score = OFFROAD_SCALER * Math.signum(distance) * Math.pow(Math.abs(distance), 1.5);
                case "steady" -> {

                    final double SCORE_EXP = 0.8; // controls score concaveness - 1.0 is linear


                    switch (STEERING_SCORE_MODE) {
                        case "differential" -> {
                            final double AVG_BIAS = 2.0; // bigger bias contributes more to score difference with
                            final double AVG_EXP = 1.5; // controls how much extra concaveness is added to curve for
                            final double AVG_SCALER = 0.5;
//                                score = score * (1.5 - Math.pow(avgAbsSteerDiff.avg, 1.5)); // inverse scale
                            // whole formula:
                            score = OFFROAD_SCALER * Math.signum(distance) * Math.pow(Math.abs(distance), SCORE_EXP)
                                    * Math.pow(AVG_BIAS - AVG_SCALER *avgAbsSteerDiff.avg, AVG_EXP);
                        }
                        case "differential2" -> {
                            final double AVG_BIAS = 1.45; // bigger bias contributes more to score difference with
                            final double AVG_EXP = 2.6; // controls how much extra concaveness is added to curve for
                            final double AVG_SCALER = 1.6;
                            // steering penalty
                            score = OFFROAD_SCALER * Math.signum(distance) * Math.pow(Math.abs(distance), SCORE_EXP)
                                    * Math.pow(AVG_SCALER, Math.pow(AVG_BIAS - avgAbsSteerDiff.avg, AVG_EXP));
                        }
                        case "magnitude" -> {
                            score = Math.pow((offroadCounter > 0 ? OFFROAD_PENALTY : 1.0), 0.7) *
                                    Math.signum(distance) * Math.pow(Math.abs(distance), SCORE_EXP);
                            // to avoid calculating wobbling frequency by fft, simple heuristic to assign bonus for
                            // cars, that steer for the majority of time to the middle
                            double totalSteer = Arrays.stream(steerCounter).sum();
                            score = score * (steerCounter[0] + steerCounter[2]) * STEER_PENALTY / totalSteer +
                                    score * steerCounter[1] * (1.0 - STEER_PENALTY) / totalSteer;
                        }
                    }

                    // RPM penalization
                    double totalRPM = Arrays.stream(rpmCounter).sum();
                    final double RPM_EXP = 1.7;
                    score = score*Math.pow(1.0+rpmCounter[1]/totalRPM, RPM_EXP);

                }
            }

            if(score < 0) score = 0.0; // without this roulette can take specimen with high negative score
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
            if (network.compareToConfig() != 0) {
                throw new RuntimeException(
                        "Current network %s doesn't match config setup: %s (bias nodes excluded from sizes)".formatted(
                                Arrays.toString(Arrays.stream(network.values).mapToInt(n -> n.length - 1).toArray()),
                                Arrays.toString(Arrays.stream(LAYER_SIZES).map(i -> i - 1).toArray()))
                );
            }
            steerCounter = new int[3];
            rpmCounter = new int[3];
            avgAbsSteerDiff = new Genetics.RunningAverage();
        }

        @Override
        public int compareTo(NNRankEntry other) {
            return (int) Math.round(Math.signum(score - other.score));
        }
    }
}
