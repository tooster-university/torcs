package test;

import scr.NN;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NNTest {

    private static class In {
        double x1, x2;
    }

    private static class Out {
        double y1, y2, y3;
    }

    private static class SimpleNNTest extends NN<In, Out> {

        public SimpleNNTest(double[][][] weights) { super(weights); }

        @Override
        public double[] parseInput(In in) { return new double[]{in.x1, in.x2}; }

        @Override
        public Out parseOutput(double[] networkOutput) {
            return new Out() {{
                y1 = networkOutput[0];
                y2 = networkOutput[1];
                y3 = networkOutput[2];
            }};
        }
    }

    @org.junit.jupiter.api.Test
    void eval() {
        var a = new double[]{1, 2, 3};// a0, a1, a2
        var b = new double[]{11, 13, 17};
        var c = new double[]{-1, -2, -3};
        SimpleNNTest nn = new SimpleNNTest(new double[][][]{
                new double[][]{ // layer 0
                        new double[]{0, 0, 0}, // destination - bias node. Artificial 0.0
                        a.clone(), // y1 = a0 + a1*x1 + a2*x2
                        b.clone(), // y2 = b0 + b1*x1 + b2*x2
                        c.clone(), // y3 should eval to 0 with ReLu
                }}
        );

        var in1 = new In() {{
            x1 = 5;
            x2 = 7;
        }};
        var out1 = nn.eval(in1);
        assertEquals(a[0] + in1.x1 * a[1] + in1.x2 * a[2], out1.y1, 1e-6);
        assertEquals(b[0] + in1.x1 * b[1] + in1.x2 * b[2], out1.y2, 1e-6);
        assertEquals(0.0, out1.y3,  1e-6, "ReLu test");
        System.err.println(nn.toString());
    }
}