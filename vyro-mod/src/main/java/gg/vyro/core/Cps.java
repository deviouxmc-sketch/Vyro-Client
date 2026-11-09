package gg.vyro.core;

import java.util.ArrayDeque;

/** Clicks-per-second over a sliding one second window. Pure logic, no Minecraft types. */
public final class Cps {
    private final ArrayDeque<Long> clicks = new ArrayDeque<>();

    public void click(long nowMs) {
        clicks.addLast(nowMs);
        trim(nowMs);
    }

    public int get(long nowMs) {
        trim(nowMs);
        return clicks.size();
    }

    private void trim(long nowMs) {
        while (!clicks.isEmpty() && nowMs - clicks.peekFirst() >= 1000) {
            clicks.removeFirst();
        }
    }
}
