// programa 6502 → VIA → T1 → IFR → IER → IRQ → vector $FFFE/$FFFF → rutina de interrupción.

package org.microtan.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.microtan.core.bus.Bus;
import org.microtan.core.cpu.Cpu6502;
import org.microtan.core.io.via.VIA6522;
import org.microtan.core.memory.RAM;
import org.microtan.core.trace.TraceConfig;

class CpuViaIrqTest {

    private static final int RESET_VECTOR = 0xFFFC;
    private static final int IRQ_VECTOR   = 0xFFFE;

    private static final int PROGRAM      = 0x8000;
    private static final int IRQ_HANDLER  = 0x8010;

    private static final int COUNTER      = 0x9000;

    private static final int VIA_BASE     = 0xBC00;

    private static final int T1CL         = VIA_BASE + 0x04;
    private static final int T1CH         = VIA_BASE + 0x05;
    private static final int ACR          = VIA_BASE + 0x0B;
    private static final int IFR          = VIA_BASE + 0x0D;
    private static final int IER          = VIA_BASE + 0x0E;

        private final TraceConfig traceConfig =
    new TraceConfig();

    @Test
    void timer1GeneratesCpuIrq() {

        RAM ram = new RAM(0x10000);
        VIA6522 via = new VIA6522();
        Bus bus = new Bus();
        Cpu6502 cpu = new Cpu6502(bus, traceConfig);

        /*
         * RAM ocupa todas las páginas excepto la página BC00.
         *
         * BC00 >>> 10 = 47
         *
         * Por tanto:
         *
         * RAM: páginas 0..46
         * VIA: página 47
         * RAM: páginas 48..63
         */

        bus.map(
                ram,
                0,
                47,
                0);

        bus.map(
                via,
                47,
                1,
                0);

        bus.map(
                ram,
                48,
                16,
                48 * 1024);

        /*
         * Conectar IRQ VIA -> CPU.
         */
        via.setIrqListener(active -> {
            if (active) {
                cpu.requestIRQ();
            }
        });

        /*
         * Programa principal.
         */
        loadOneShotProgram(ram);

        /*
         * Vector RESET.
         */
        writeWord(
                ram,
                RESET_VECTOR,
                PROGRAM);

        /*
         * Vector IRQ.
         */
        writeWord(
                ram,
                IRQ_VECTOR,
                IRQ_HANDLER);

        /*
         * Inicializamos la CPU.
         */
        cpu.reset();

        /*
         * Ejecutamos bastantes instrucciones/ciclos.
         *
         * Como step() no devuelve ciclos, hacemos avanzar
         * la VIA una vez por step().
         */
        for (int i = 0; i < 100; i++) {

            cpu.step();

            via.tick();

            /*
             * Una vez que el handler ha incrementado el contador,
             * podemos terminar.
             */
            if (ram.read(COUNTER) == 1) {
                break;
            }
        }

        assertEquals(
                1,
                ram.read(COUNTER));

        /*
         * El IFR de T1 debería haber sido limpiado
         * por la rutina IRQ.
         */
        assertEquals(
                0,
                via.read(0x0D) & VIA6522.IFR_T1);
    }

    /**
     * Programa que configura T1 en one-shot.
     */
    private void loadOneShotProgram(RAM ram) {

        int[] program = {

                // ---------------------------------------------------------
                // SEI
                // ---------------------------------------------------------

                0x78,

                // ---------------------------------------------------------
                // contador = 0
                // ---------------------------------------------------------

                0xA9, 0x00,             // LDA #$00
                0x8D, 0x00, 0x90,      // STA $9000

                // ---------------------------------------------------------
                // ACR = 0
                //
                // T1 one-shot
                // ---------------------------------------------------------

                0xA9, 0x00,
                0x8D, 0x0B, 0xBC,      // STA $BC0B

                // ---------------------------------------------------------
                // IER = $C0
                //
                // Bit 7 = 1 -> set
                // Bit 6 = 1 -> enable T1
                // ---------------------------------------------------------

                0xA9, 0xC0,
                0x8D, 0x0E, 0xBC,      // STA $BC0E

                // ---------------------------------------------------------
                // T1 = 5
                //
                // Primero low latch
                // después high counter.
                // ---------------------------------------------------------

                0xA9, 0x05,
                0x8D, 0x04, 0xBC,      // STA $BC04

                0xA9, 0x00,
                0x8D, 0x05, 0xBC,      // STA $BC05

                // ---------------------------------------------------------
                // CLI
                // ---------------------------------------------------------

                0x58,

                // ---------------------------------------------------------
                // Bucle infinito
                // ---------------------------------------------------------

                0x4C,
                0x1D,
                0x80
        };

        load(ram, PROGRAM, program);

        /*
         * Rutina IRQ:
         *
         * INC $9000
         *
         * Limpiar IFR6:
         *
         * LDA #$40
         * STA $BC0D
         *
         * RTI
         */

        int[] irq = {

                0xEE, 0x00, 0x90,      // INC $9000

                0xA9, 0x40,             // LDA #$40
                0x8D, 0x0D, 0xBC,      // STA $BC0D

                0x40                    // RTI
        };

        load(
                ram,
                IRQ_HANDLER,
                irq);
    }

    private void load(
            RAM ram,
            int address,
            int[] data) {

        for (int i = 0; i < data.length; i++) {
            ram.write(
                    address + i,
                    data[i]);
        }
    }

    private void writeWord(
            RAM ram,
            int address,
            int value) {

        ram.write(
                address,
                value & 0xFF);

        ram.write(
                address + 1,
                (value >>> 8) & 0xFF);
    }
}