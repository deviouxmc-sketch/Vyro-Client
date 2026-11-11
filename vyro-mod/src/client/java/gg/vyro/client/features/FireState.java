package gg.vyro.client.features;

/** Mixin classes cannot hold shared static state, so it lives here. */
public final class FireState {
    private FireState() {}

    public static boolean pushed;
}
