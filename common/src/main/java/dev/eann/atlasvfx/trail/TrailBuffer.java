package dev.eann.atlasvfx.trail;

/**
 * Where something has been, one sample a tick, newest last: the record a recorded trail is drawn
 * through.
 *
 * <p>A fixed ring: once full, each new sample takes the place of the oldest, so a trail of any
 * length costs the same and never allocates after it is made.</p>
 */
public final class TrailBuffer {

    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final long[] time;
    private int start;
    private int count;

    /** @param capacity the most samples kept */
    public TrailBuffer(int capacity) {
        int size = Math.max(2, capacity);
        x = new double[size];
        y = new double[size];
        z = new double[size];
        time = new long[size];
    }

    /** Adds the newest sample, dropping the oldest if it is full. */
    public void push(double px, double py, double pz, long at) {
        int capacity = x.length;
        int slot;
        if (count < capacity) {
            slot = (start + count) % capacity;
            count++;
        } else {
            slot = start;
            start = (start + 1) % capacity;
        }
        x[slot] = px;
        y[slot] = py;
        z[slot] = pz;
        time[slot] = at;
    }

    /** Forgets everything: the trail starts again from its next sample. */
    public void clear() {
        start = 0;
        count = 0;
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    /** The {@code i}th sample's x, counting from the oldest kept. */
    public double x(int i) {
        return x[slot(i)];
    }

    public double y(int i) {
        return y[slot(i)];
    }

    public double z(int i) {
        return z[slot(i)];
    }

    /** When the {@code i}th sample was taken. */
    public long time(int i) {
        return time[slot(i)];
    }

    /** The newest sample's time; meaningless when empty. */
    public long newestTime() {
        return time(count - 1);
    }

    /** How far the newest sample is from a point, squared. */
    public double newestDistanceSquared(double px, double py, double pz) {
        int i = slot(count - 1);
        double dx = x[i] - px;
        double dy = y[i] - py;
        double dz = z[i] - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private int slot(int i) {
        return (start + i) % x.length;
    }
}
