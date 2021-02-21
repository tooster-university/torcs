package scr;

import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import static java.lang.Math.max;
import static scr.Config.ACTIVATION_T.TANH;

// neural network with ReLu activation function
public abstract class NN<IN, OUT> implements Serializable {
    @Serial
    private static final long serialVersionUID = 9084587006251951184L;

    private static final double MIN_WEIGHT = -1.0;
    private static final double MAX_WEIGHT = 1.0;

    public final double[][][] weights;
    @SuppressWarnings("TransientFieldNotInitialized") // is initialized using constructor in readResolve()
    public final transient double[][] values;

    /**
     * Constructs network from given weights.
     *
     * @param weights edge weights. Network will consist of {@code (weights.length+1)} layers.
     *
     * @implNote data layout: {@code weights[source layer][destination node idx][source node idx]}
     * <p>
     * Input/output nodes are indexed from 1. Nodes with {@code k=0} are bias nodes, so {@code w [:][0][:]=0.0} (every
     * IN edge into bias node is 0) and {@code value[:][0]=1.0) (bias nodes have constant value of 1.0).
     * </p>
     */
    public NN(double[][][] weights) {

        this.weights = weights;
        this.values = new double[weights.length + 1][];

        // assert network structure is correct
        assert (this.weights.length > 0); // empty networks are meaningless
        for (int i = 0, weightsLength = this.weights.length; i < weightsLength; i++) {
            double[][] srcLayer = this.weights[i];
            // assert equal in-degree of all nodes across one layer (layers are fully connected)
            for (var dstNode : srcLayer) assert (dstNode.length == srcLayer[0].length);
            // assert layer sizes match with connections
            assert i == 0 || (srcLayer[0].length == this.weights[i - 1].length);
        }

        // setup bias nodes
        for (var layer : this.weights) Arrays.fill(layer[0], 0.0); // artificially set weight to bias nodes to 0.0
        values[0] = new double[weights[0][0].length]; // deduced from in-degree of node 1[0]
        for (int srcLayer = 0; srcLayer < weights.length; srcLayer++) // set other layers size based on in
            values[srcLayer + 1] = new double[weights[srcLayer].length]; // deduced from out-degree of previous nodes'

        // Setup bias nodes
        for (var valuesLayer : values)
            valuesLayer[0] = 1.0; // artificially set bias values to 1.0
    }


    /**
     * Creates new random weights with sizes specified in Config. Weights are uniformly random on range [-1.0, 1.0].
     * Layer size is pulled from config (for hidden) and hardcoded in Config class
     */
    public static double[][][] nextRandomWeights() {
        // layout: w[layer][dst][src]
        var randomWeights = new double[Config.LAYER_SIZES.length - 1][][];

        // initialize structure
        for (int srcLayer = 0; srcLayer < Config.LAYER_SIZES.length - 1; srcLayer++) {
            randomWeights[srcLayer] = new double[Config.LAYER_SIZES[srcLayer + 1]][];
            for (int s = 0; s < randomWeights[srcLayer].length; s++)
                randomWeights[srcLayer][s] = new double[Config.LAYER_SIZES[srcLayer]];
        }

        // populate weights, skipping bias node inputs
        for (var srcLayer : randomWeights)
            for (int dst = 1; dst < srcLayer.length; dst++) // skip input to bias nodes
                for (int src = 0; src < srcLayer[dst].length; src++)
                    srcLayer[dst][src] = nextRandomWeight();

        return randomWeights;
    }

    /** generates uniform random number on range [MIN_WEIGHT, MAX_WEIGHT] as instructed by Math.random() api note */
    public static double nextRandomWeight() {
        double f = Math.random() / Math.nextDown(1.0);
        return MIN_WEIGHT * (1.0 - f) + MAX_WEIGHT * f;
    }

    /**
     * Produces Action based on SensorModel
     *
     * @param sensors sensor model to read data from
     *
     * @return action that should be taken by car
     */
    public OUT eval(IN sensors) {
        // TODO: weight normalization

        // parse sensors to network using user-defined overload
        var input = parseInput(sensors);

        // input size should equal first layer size excluding bias node
        assert (input.length == values[0].length - 1);

//        // clean the network - init values to 0
//        for (var layer : values) Arrays.fill(layer, 1, layer.length, 0.0);

        // copy the input into network
        System.arraycopy(input, 0, values[0], 1, input.length);

        for (int srcLayer = 0; srcLayer < weights.length; srcLayer++) { // process network layer by layer
            for (int dst = 1; dst < weights[srcLayer].length; dst++) { // skip overwriting bias
                // weighted sum / reduce of input vector
                var wSum = 0.0;
                for (int src = 0; src < weights[srcLayer][dst].length; src++)
                    wSum += weights[srcLayer][dst][src] * values[srcLayer][src];
                // pass through activation function and write to destination layer (srcLayer+1)
                values[srcLayer + 1][dst] = activationFunction(wSum);
            }
        }

        // parse output using user-defined overload
        var outLayer = values[values.length - 1];
        return parseOutput(Arrays.copyOfRange(outLayer, 1, outLayer.length));
    }

    /**
     * Override this method to tell network how it should parse sensors to network input
     *
     * @param sensors sensors data to use as network input
     *
     * @return array of doubles that will be used in input layer of network. With assertions enabled, network will check
     * if this array matches network input size (without bias node).
     */
    public abstract double[] parseInput(final IN sensors);

    /**
     * Override this method to translate network output to viable action
     *
     * @param networkOutput Copy of relevant output layer values (without bias node).
     *
     * @return action parsed from network output
     */
    public abstract OUT parseOutput(final double[] networkOutput);

    /**
     * Activation function used in neural network. ReLu by default
     *
     * @param input calculated raw input to node (sum of weights times input connections)
     *
     * @return value transformed by activation function
     */
    public double activationFunction(double input) {
        if (Config.ACTIVATION == TANH) return Math.tanh(input);
        return max(input, 0.0); // ReLu by default
    }

    /**
     * Called when object has been deserialized from a stream.
     *
     * @return {@code this}, or a replacement for {@code this}.
     * @throws ObjectStreamException if the object cannot be restored.
     * @see <a href="http://download.oracle.com/javase/1.3/docs/guide/serialization/spec/input.doc6.html">The Java
     * Object Serialization Specification</a>
     */
    @Serial
    protected Object readResolve() throws ObjectStreamException {
        try {
            // this should properly initialize all params and assert correctness using constructor of runtime
            // concrete class
            return this.getClass().getDeclaredConstructor(double[][][].class).newInstance((Object) this.weights);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException("Boo-Boo happened on network deserialization");
        }
    }

    /**
     * Returns representation of graph that can be plugged into
     * <a href="https://csacademy.com/app/graph_editor/">visualizer</a>
     *
     * @param biasDebug if true, bias INs are also included (by design they should be always 0.0, so it's not needed)
     *
     * @implNote format is {@code 'srcLayer[src] dstLayer[dst] weight'}
     */
    public String toString(boolean biasDebug) {
        StringBuilder sb = new StringBuilder();
        for (int srcLayer = 0; srcLayer < weights.length; srcLayer++) {
            for (int dst = biasDebug ? 0 : 1; dst < weights[srcLayer].length; dst++) {
                for (int src = 0; src < weights[srcLayer][dst].length; src++) {
                    sb
                            .append(srcLayer).append("[").append(src).append("] ")
                            .append(srcLayer + 1).append("[").append(dst).append("] ")
                            .append(weights[srcLayer][dst][src])
                            .append("\n");
                }
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() { return this.toString(false); }
}
