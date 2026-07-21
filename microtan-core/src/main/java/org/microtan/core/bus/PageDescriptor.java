package org.microtan.core.bus;

public final class PageDescriptor {

    private final MemoryDevice device;
    private final int baseOffset;

    public PageDescriptor(MemoryDevice device, int baseOffset) {
        this.device = device;
        this.baseOffset = baseOffset;
    }

    public MemoryDevice device() {
        return device;
    }

    public int baseOffset() {
        return baseOffset;
    }
}
