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
    private static final String CONFIG_FILE = "resources/nn-driver.properties";
    private static final Properties config = new Properties();
    public static String FILE_PREFIX;
    public static int[] LAYER_SIZES;
    public static int MAX_STEPS;
    public static boolean STAGNATION_REPLACEMENT;
    public static boolean OFFROAD_KILL;
    public static String SCORE_MODE;
    //
    public static double MUTATION_PROBABILITY;
    public static boolean LERP_CROSSOVER_COMMON_RANDOM;
    public static int POPULATION_SIZE;
    public static int GENERATIONS;
    public static int START_GENERATION;
    public static int ELITE;
    //
    private static int edgeCnt;
    private static int nodeCnt;

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

            // serialization
            FILE_PREFIX = config.getProperty("filePrefix", "nn");
            START_GENERATION = Integer.parseInt(config.getProperty("startGeneration", "0"));

            // calculate edge count
            for (int i = 0; i < LAYER_SIZES.length; i++) {
                nodeCnt += LAYER_SIZES[i];
                edgeCnt += i == 0 ? 0 : LAYER_SIZES[i - 1] * LAYER_SIZES[1];
            }

            System.out.println("Config loaded. Target network size:\n" +
                    " #nodes:[" + getNodeCnt() + "]\n" +
                    " #edges:[" + getEdgeCnt() + "]");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("Missing config file '" + CONFIG_FILE + "'");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getEdgeCnt() { return edgeCnt; }

    public static int getNodeCnt() { return nodeCnt; }
}
