package org.microtan.core.memory;

import org.microtan.core.bus.MemoryDevice;

public class RAM implements MemoryDevice {

    private final byte[] memory;

    public RAM(int size) {
        memory = new byte[size];
    }

    @Override
    public int read(int address) {
        return memory[address] & 0xFF;
    }

    @Override
    public void write(int address, int value) {
        memory[address] = (byte) value;
    }

}