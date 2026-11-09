package gg.vyro.core;

public final class TimeFmt {
    private TimeFmt() {}

    /** In-game clock. Tick 0 of a day is 06:00, so 6000 is noon and 18000 is midnight. */
    public static String mcClock(long dayTime) {
        long t = ((dayTime % 24000) + 24000) % 24000;
        int hour = (int) ((t / 1000 + 6) % 24);
        int minute = (int) ((t % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hour, minute);
    }

    /** m:ss for effect timers. */
    public static String mmss(int ticks) {
        int s = Math.max(0, ticks) / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
