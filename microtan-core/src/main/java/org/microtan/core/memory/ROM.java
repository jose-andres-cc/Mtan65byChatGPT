package org.microtan.core.memory;

import org.microtan.core.bus.MemoryDevice;

public class ROM implements MemoryDevice {

    private final byte[] memory;

    public ROM(byte[] rom) {
        this.memory = rom.clone();
    }

    @Override
    public int read(int address) {

        if (address < 0 || address >= memory.length) {
            return 0xFF;
        }
        return memory[address] & 0xFF;
    }

    @Override
    public void write(int address, int value) {
        // ROM
    }

}