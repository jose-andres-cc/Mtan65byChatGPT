package org.microtan.core.expansion;

import org.microtan.core.bus.Bus;

public interface ExpansionBoard {

    void install(Bus bus);

    default void reset() {
    }

    default void tick() {
    }

}