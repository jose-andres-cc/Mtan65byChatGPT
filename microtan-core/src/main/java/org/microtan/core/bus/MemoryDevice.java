package org.microtan.core.bus;

public interface MemoryDevice {

    int read(int address);

    void write(int address, int value);

    default void reset() {
    }

    default void tick() {
    }
}
