package dev.eann.atlasvfx;

/**
 * A surface as a grid of points in the world, each with its own colour and opacity: what
 * {@link FxPainter#surface} draws.
 *
 * <p>Rows run along the surface and columns across it. Kept and refilled frame after frame by
 * whatever draws it, so a trail of any length allocates nothing while it plays: {@link #reset}
 * only grows the arrays when a bigger grid is asked for.</p>
 */
public final class SurfaceGrid {

    private int rows;
    private int columns;
    private double[] x = new double[0];
    private double[] y = new double[0];
    private double[] z = new double[0];
    private int[] rgb = new int[0];
    private float[] alpha = new float[0];

    /**
     * Gets it ready for a grid of this many segments each way: {@code rows + 1} by
     * {@code columns + 1} points. Whatever was in it before is left to be overwritten.
     */
    public void reset(int rows, int columns) {
        this.rows = Math.max(1, rows);
        this.columns = Math.max(1, columns);
        int points = (this.rows + 1) * (this.columns + 1);
        if (x.length < points) {
            x = new double[points];
            y = new double[points];
            z = new double[points];
            rgb = new int[points];
            alpha = new float[points];
        }
    }

    /** Sets one point: where it is in the world, its colour and how strongly it shows. */
    public void set(int row, int column, double px, double py, double pz, int colour, float opacity) {
        int i = index(row, column);
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        rgb[i] = colour;
        alpha[i] = opacity;
    }

    /** Segments along it. */
    public int rows() {
        return rows;
    }

    /** Segments across it. */
    public int columns() {
        return columns;
    }

    public double x(int row, int column) {
        return x[index(row, column)];
    }

    public double y(int row, int column) {
        return y[index(row, column)];
    }

    public double z(int row, int column) {
        return z[index(row, column)];
    }

    public int rgb(int row, int column) {
        return rgb[index(row, column)];
    }

    public float alpha(int row, int column) {
        return alpha[index(row, column)];
    }

    private int index(int row, int column) {
        return row * (columns + 1) + column;
    }
}
