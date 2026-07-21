package org.microtan.core.memory;

import org.microtan.core.bus.MemoryPage;

public class RomPage implements MemoryPage {

    private final byte[] memory;

    public RomPage(byte[] data) {

        if (data.length != 1024)
            throw new IllegalArgumentException("Una ROM ocupa exactamente una página.");

        this.memory = data.clone();
    }

    @Override
    public int read(int offset) {
        return memory[offset] & 0xFF;
    }

    @Override
    public void write(int offset, int value) {
        // Ignorar escrituras
    }

}
