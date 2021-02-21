package scr;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

public class TorcsNN extends NN<SensorModel, Action> implements Serializable {
    protected static final int IN_LAYER_SIZE = 5 + 19;
    protected static final int OUT_LAYER_SIZE = 5;
    @Serial
    private static final long serialVersionUID = 4132367282716822317L;

    /** @see NN#NN(double[][][]) */
    public TorcsNN(double[][][] weights) { super(weights); }

    @Override
    public double[] parseInput(SensorModel sensors) {

        var inputLayer = new double[IN_LAYER_SIZE];
        inputLayer[0] = sensors.getAngleToTrackAxis();
        inputLayer[1] = sensors.getSpeed();
        inputLayer[2] = sensors.getTrackPosition();
        inputLayer[3] = sensors.getRPM();
        inputLayer[4] = sensors.getGear();

        var trackEdgeSensors = sensors.getTrackEdgeSensors();
        System.arraycopy(trackEdgeSensors, 0, inputLayer, 5, trackEdgeSensors.length);

        return inputLayer;
    }


    @Override
    public Action parseOutput(double[] networkOutput) {

        var action = new Action();

        if (Config.SOFTMAX_OUTPUT_LAYER) {
            networkOutput = Arrays.stream(Genetics.softmax(networkOutput)).map(v -> v / 2.0 + 0.5).toArray();
            // all in [0, 1] here
            action.steering = networkOutput[0] * 2.0 - 1.0;
            action.accelerate = networkOutput[1];
            action.brake = networkOutput[2];
            action.clutch = networkOutput[3];
            action.gear = (int) Math.round(networkOutput[4] * 7.0 - 1.0);

        } else {
            switch (Config.ACTIVATION) {
                case RELU -> { // old schema, bad cuz ReLu can throw random range > 0 (300 for example)
                    // kept for compatibility
                    // all are [0, +inf]
                    action.steering = networkOutput[0] - 1.0;
                    action.accelerate = networkOutput[1];
                    action.brake = networkOutput[2];
                    action.clutch = networkOutput[3];
                    action.gear = (int) Math.round(networkOutput[4]) - 1;
                }
                case TANH -> {
                    // all are [-1, 1]
                    action.steering = networkOutput[0];
                    action.accelerate = networkOutput[1] / 2.0 + 0.5;
                    action.brake = networkOutput[2] / 2.0 + 0.5;
                    action.clutch = networkOutput[3] / 2.0 + 0.5;
                    action.gear = (int) Math.round(networkOutput[4] * 3.5 + 2.5);
                }
            }
        }
        
        action.limitValues();
        return action;
    }


    /***
     * Validates if network matches the config file size.
     * @return returns 0 is all sizes match, -1 if any of the sizes is smaller than config, +1 if not equal and all
     * sizes are at least as big as in config
     * @implNote For now, only networks with matching (=0) sizes will work, all the other will break
     */
    public int compareToConfig() {
        if (values.length != Config.LAYER_SIZES.length) return -1;
        boolean existsGreater = false;
        for (int i = 0; i < values.length; i++) {
            if (values[i].length < Config.LAYER_SIZES[i]) return -1;
            else if (values[i].length > Config.LAYER_SIZES[i]) existsGreater = true;
        }
        return existsGreater ? 1 : 0;
    }
}
