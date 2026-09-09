package baritone.api;

public final class BaritoneAPI {
    private static IBaritoneProvider provider;

    public static IBaritoneProvider getProvider() {
        return provider;
    }
}
