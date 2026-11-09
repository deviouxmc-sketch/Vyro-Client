package gg.vyro.core;

/** Consecutive hits on enemies; resets when you are hit or stop attacking for a while. */
public final class Combo {
    private final long timeoutMs;
    private int count;
    private long last = Long.MIN_VALUE / 2;

    public Combo(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int hit(long nowMs) {
        if (nowMs - last > timeoutMs) count = 0;
        count++;
        last = nowMs;
        return count;
    }

    public void reset() {
        count = 0;
    }

    public int get(long nowMs) {
        return nowMs - last > timeoutMs ? 0 : count;
    }
}
