package foodassistant.map;

import java.io.PrintStream;
import java.util.List;

/**
 * Stand-in for an external map API. Its method names are intentionally
 * different from {@link MapProvider} so the adapter has a real translation job.
 */
public final class ExternalMapClient {
    private final boolean online;
    private final PrintStream out;

    public ExternalMapClient(boolean online, PrintStream out) {
        this.online = online;
        this.out = out;
    }

    public boolean ping() {
        return online;
    }

    public void renderPins(List<String> pinLabels) {
        out.println("Map pins:");
        if (pinLabels.isEmpty()) {
            out.println("  (no pins)");
            return;
        }
        for (String label : pinLabels) {
            out.println("  * " + label);
        }
    }
}
