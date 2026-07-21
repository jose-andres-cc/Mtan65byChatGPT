package org.microtan.core.bus;

public interface MemoryPage {

    int read(int offset);

    void write(int offset, int value);

    default void reset() {
    }

    default void tick() {
    }

}
