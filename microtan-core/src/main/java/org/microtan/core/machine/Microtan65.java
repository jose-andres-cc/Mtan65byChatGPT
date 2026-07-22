package org.microtan.core.machine;

import java.io.IOException;
import java.nio.file.Path;

import org.microtan.core.bus.Bus;
import org.microtan.core.cpu.Cpu6502;
import org.microtan.core.io.VIA6522;
import org.microtan.core.memory.RAM;
import org.microtan.core.memory.ROM;
import org.microtan.core.memory.RomLoader;
import org.microtan.core.video.CharacterROM;
import org.microtan.core.video.VideoController;

public class Microtan65 {

    private final Bus bus;

    private final Cpu6502 cpu;

    private final RAM ram;

    private final ROM tanbug;

    private final VIA6522 via;

    private final CharacterROM charset;
private final VideoController video;

    private volatile boolean running;

    public Microtan65() throws IOException {

        bus = new Bus();

        ram = new RAM(32 * 1024);

        tanbug = RomLoader.load(
                Path.of("roms", "TANBUG.BIN"));

        charset = new CharacterROM(
                Path.of("roms", "CHARSET.BIN"));

        via = new VIA6522();

        //
        // Mapa de memoria
        //

        // 0000-7FFF RAM (32K)
        bus.map(ram, 0, 32, 0);

        // F000-FFFF TANBUG (4K)
        bus.map(tanbug, 60, 4, 0);

        // VIA (provisional)
        // bus.map(via, ...);

        cpu = new Cpu6502(bus);

    video = new VideoController(bus, charset);


    }

    public void reset() {

        cpu.reset();

        running = false;
    }

    public void start() {

        reset();

        running = true;

        run();
    }

    public void stop() {

        running = false;
    }

    public void run() {

        while (running) {

            int cycles = 5; // JAC temporal

            cpu.step();
            // mejora a futuro -> int cycles = cpu.step();

           // bus.tick();

    via.tick();
    //via.tick(cycles);

    video.tick(cycles);
    // video.tick(cycles);


    // cassette.tick(cycles);


        }

    }

    public Cpu6502 getCpu() {
        return cpu;
    }

    public Bus getBus() {
        return bus;
    }

    public RAM getRam() {
        return ram;
    }

    public CharacterROM getCharacterRom() {
        return charset;
    }

    public VideoController getVideoController() {
        return video;
    }


}


// Version futura con builder
// Microtan65 machine = new Microtan65Builder()
//         .withTanbug()
//         .with32KRam()
//         .withCharacterRom()
//         .withTanex()
//         .build();

// Este enfoque tiene una gran ventaja: permite crear distintas configuraciones sin modificar Microtan65. Por ejemplo:

// Microtan básico (1 KB RAM + TANBUG).
// Microtan + TANEX.
// Microtan + TANEX + BASIC.
// Microtan + TANRAM.
// Microtan + TANDOS.