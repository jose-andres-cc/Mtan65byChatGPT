package org.microtan.core.cpu;

import org.microtan.core.bus.Bus;

//import org.microtan.core.cpu.CPU6510;

public class Cpu6502 extends CPU6510{

    //private final Bus bus;

    public Cpu6502(Bus bus) {
        super(bus);

        //this.bus = bus;

    }

    public void reset() {

    }

    // Cambio de la gestion de cycles
    public int stepWithCycles() {
        super.step();
        return (int) super.getCycles();

    }

}