package org.microtan.core.memory;

import org.microtan.core.bus.MemoryPage;

public class RamPage implements MemoryPage {

    private final byte[] memory = new byte[1024];

    @Override
    public int read(int offset) {
        return memory[offset] & 0xFF;
    }

    @Override
    public void write(int offset, int value) {
        memory[offset] = (byte) value;
    }

}