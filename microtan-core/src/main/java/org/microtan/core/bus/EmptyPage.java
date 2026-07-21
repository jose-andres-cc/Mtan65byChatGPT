package org.microtan.core.bus;

public class EmptyPage implements MemoryPage {

    @Override
    public int read(int offset) {
        return 0xFF;
    }

    @Override
    public void write(int offset, int value) {
    }

}