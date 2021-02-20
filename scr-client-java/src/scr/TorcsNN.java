package scr;

import java.io.Serial;
import java.io.Serializable;

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
        return new Action() {{
            steering = networkOutput[0] - 1.0; // tweak ReLu output to negatives
            accelerate = networkOutput[1]; // 0-1
            brake = networkOutput[2]; // 0-1
            clutch = networkOutput[3]; // 0-1
            gear = (int) (Math.round(networkOutput[4])  - 1); // tweak ReLu to allow reverse with -1
        }};
    }
}
