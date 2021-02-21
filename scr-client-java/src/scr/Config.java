package scr;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class Config {
    public static final String FILE_DIR = "data/"; // directory for serialized networks
    public static final String FILE_EXT = ".dat"; // extension for serialized networks
    public static final String FILE_SEPARATOR = "-";
    public static final String FILE_PREFIX;
    public static final boolean SORTED_OVERRIDE;
    /// ---------------------------------
    public static final int[] LAYER_SIZES;
    public static final ACTIVATION_T ACTIVATION;
    public static final boolean SOFTMAX_OUTPUT_LAYER;
    //
    public static final int MAX_STEPS;
    public static final boolean STAGNATION_REPLACEMENT;
    public static final boolean OFFROAD_KILL;
    public static final String SCORE_MODE;
    public static final String STEERING_SCORE_MODE;
    //
    public static final double MUTATION_PROBABILITY;
    public static final boolean LERP_CROSSOVER_COMMON_RANDOM;
    public static final int POPULATION_SIZE;
    public static final int GENERATIONS;
    public static final int START_GENERATION;
    public static final boolean NETWORK_COMPATIBILITY_MODE;
    public static final int ELITE;
    //
    private static final String CONFIG_FILE = "resources/nn-driver.properties";
    private static final Properties config = new Properties();
    //
    private static final int edgeCnt;
    private static final int nodeCnt;

    static {
        // load bot config from file
        try {
            var is = new FileInputStream(CONFIG_FILE);
            config.load(is);

            // create array of network layer sizes to easily reference later
            var hiddenLayerSizes = Arrays.stream(config.getProperty("hiddenLayerSizes", "6")
                    .split("\s*,\s*"))
                    .mapToInt(Integer::parseInt)
                    .toArray();
            var networkSizes = new ArrayList<Integer>();
            networkSizes.add(TorcsNN.IN_LAYER_SIZE);
            for (int hiddenLayerSize : hiddenLayerSizes) networkSizes.add(hiddenLayerSize);
            networkSizes.add(TorcsNN.OUT_LAYER_SIZE);
            LAYER_SIZES = networkSizes.stream().mapToInt(i -> i + 1).toArray(); // each layer has bias node
            ACTIVATION = config.getProperty("activation", "ReLu").equals("tanh") ?
                    ACTIVATION_T.TANH : ACTIVATION_T.RELU;
            SOFTMAX_OUTPUT_LAYER = Boolean.parseBoolean(config.getProperty("softmaxOutput", "false"));

            // genetic control
            MUTATION_PROBABILITY = Double.parseDouble(config.getProperty("mutationProbability"));
            LERP_CROSSOVER_COMMON_RANDOM = Boolean.parseBoolean(config.getProperty("crossoverCommonLerpRandom", "true"
            ));

            POPULATION_SIZE = Integer.parseInt(config.getProperty("populationSize"));
            GENERATIONS = Integer.parseInt(config.getProperty("generations", "1000"));
            ELITE = Integer.parseInt(config.getProperty("elite", "0"));

            MAX_STEPS = Integer.parseInt(config.getProperty("maxSteps", "0"));
            STAGNATION_REPLACEMENT = Boolean.parseBoolean(config.getProperty("stagnationReplacement", "false"));
            OFFROAD_KILL = Boolean.parseBoolean(config.getProperty("offroadKill", "false"));
            SCORE_MODE = config.getProperty("score", "distance");
            STEERING_SCORE_MODE = config.getProperty("steerPenaltyMode", "magnitude");

            // serialization
            FILE_PREFIX = config.getProperty("filePrefix", "nn");
            START_GENERATION = Integer.parseInt(config.getProperty("startGeneration", "0"));
            NETWORK_COMPATIBILITY_MODE = Boolean.parseBoolean(config.getProperty("networkCompatibilityMode", "false"));
            SORTED_OVERRIDE = Boolean.parseBoolean(config.getProperty("sortedOverride"));

            // calculate edge count
            int connections = 0;
            for (int i = 0; i < LAYER_SIZES.length; i++)
                connections += i == 0 ? 0 : LAYER_SIZES[i - 1] * LAYER_SIZES[1];
            nodeCnt = Arrays.stream(LAYER_SIZES).sum();
            edgeCnt = connections;

            System.out.printf("Config loaded. Target network sizes: %s%s (mind the bias)\n " +
                            "#nodes:[%d] #edges:[%d]%n"
                            + (Config.NETWORK_COMPATIBILITY_MODE ?
                            "!!! COMPATIBILITY MODE ENABLED (not implemented yet) !!!%n" : ""),
                    Arrays.toString(Arrays.stream(Config.LAYER_SIZES).map(i -> i - 1).toArray()),
                    SOFTMAX_OUTPUT_LAYER ? "[+softmax]":"",
                    getNodeCnt(),
                    getEdgeCnt());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Missing config file '" + CONFIG_FILE + "'");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getEdgeCnt() { return edgeCnt; }

    public static int getNodeCnt() { return nodeCnt; }

    public enum ACTIVATION_T {RELU, TANH}
}
