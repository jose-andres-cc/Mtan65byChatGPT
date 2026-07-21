package org.microtan.core.bus;

public class Bus {

    public static final int PAGE_SIZE = 1024;
    public static final int PAGE_COUNT = 64;

    private final PageDescriptor[] pages = new PageDescriptor[PAGE_COUNT];

    public Bus() {

        MemoryDevice empty = new EmptyDevice();

        for (int i = 0; i < PAGE_COUNT; i++) {
            pages[i] = new PageDescriptor(empty, 0);
        }
    }

    public void map(MemoryDevice device,
                    int firstPage,
                    int pageCount,
                    int deviceOffset) {

        int offset = deviceOffset;

        for (int i = 0; i < pageCount; i++) {
            pages[firstPage + i] =
                    new PageDescriptor(device, offset);

            offset += PAGE_SIZE;
        }
    }

    public int read(int address) {

        address &= 0xFFFF;

        PageDescriptor page = pages[address >>> 10];

        int offset = page.baseOffset() + (address & 0x03FF);

        return page.device().read(offset) & 0xFF;
    }

    public void write(int address, int value) {

        address &= 0xFFFF;

        PageDescriptor page = pages[address >>> 10];

        int offset = page.baseOffset() + (address & 0x03FF);

        page.device().write(offset, value & 0xFF);
    }

}