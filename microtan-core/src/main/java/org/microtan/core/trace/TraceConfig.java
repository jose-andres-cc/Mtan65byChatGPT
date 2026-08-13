package org.microtan.core.trace;
import java.util.EnumSet;

public final class TraceConfig {

    private boolean enabled;

    private final EnumSet<TraceOption> options =
            EnumSet.noneOf(TraceOption.class);

    public boolean isEnabled() {

        return enabled;
    }

    public void setEnabled(boolean enabled) {

        this.enabled = enabled;
    }

    public void enable(TraceOption option) {

        options.add(option);
    }

    public void disable(TraceOption option) {

        options.remove(option);
    }

    public boolean isEnabled(TraceOption option) {

        return options.contains(option);
    }

    public void clear() {

        options.clear();
    }

    public EnumSet<TraceOption> getOptions() {

        return EnumSet.copyOf(options);
    }

}
