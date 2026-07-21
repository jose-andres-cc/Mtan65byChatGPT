package org.microtan.core.bus;

public class EmptyDevice implements MemoryDevice {

    @Override
    public int read(int address) {
        return 0xFF;
    }

    @Override
    public void write(int address, int value) {
    }

}