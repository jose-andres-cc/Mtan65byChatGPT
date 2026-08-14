package org.microtan.core.io.via;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Via6522Test {

    private VIA6522 via;

    @BeforeEach
    void setUp() {
        via = new VIA6522();
    }

    // ---------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------

    @Test
    void resetInitializesRegisters() {

        assertEquals(0x00, via.read(0x00)); // ORB
        assertEquals(0x00, via.read(0x01)); // ORA

        assertEquals(0x00, via.read(0x02)); // DDRB
        assertEquals(0x00, via.read(0x03)); // DDRA

        assertEquals(0x00, via.read(0x0A)); // SR
        assertEquals(0x00, via.read(0x0B)); // ACR
        assertEquals(0x00, via.read(0x0C)); // PCR

        assertEquals(0x00, via.read(0x0D) & 0x7F); // IFR

        // Bit 7 siempre indica la lectura de IER.
        assertEquals(0x80, via.read(0x0E));
    }

    // ---------------------------------------------------------------------
    // DDR
    // ---------------------------------------------------------------------

    @Test
    void dataDirectionRegisterB() {

        via.write(0x02, 0xFF);

        assertEquals(0xFF, via.read(0x02));

        via.write(0x02, 0x55);

        assertEquals(0x55, via.read(0x02));
    }

    @Test
    void dataDirectionRegisterA() {

        via.write(0x03, 0xFF);

        assertEquals(0xFF, via.read(0x03));

        via.write(0x03, 0xAA);

        assertEquals(0xAA, via.read(0x03));
    }

    // ---------------------------------------------------------------------
    // Puerto B
    // ---------------------------------------------------------------------

    @Test
    void portBOutput() {

        via.write(0x02, 0xFF);
        via.write(0x00, 0x55);

        assertEquals(0x55, via.read(0x00));
    }

    @Test
    void portBInput() {

        via.write(0x02, 0x00);

        via.setPortBInput(0xA5);

        assertEquals(0xA5, via.read(0x00));
    }

    @Test
    void portBMixesInputAndOutputBits() {

        /*
         * Bit 0: salida
         * Bit 1: entrada
         * Bit 2: salida
         * etc.
         */
        via.write(0x02, 0x05);

        via.write(0x00, 0x05);

        via.setPortBInput(0xFA);

        /*
         * Resultado:
         *
         * bits 0,2 -> ORB = 1
         * resto    -> entrada = portBInput
         */
        assertEquals(0xFF, via.read(0x00));
    }

    // ---------------------------------------------------------------------
    // Puerto A
    // ---------------------------------------------------------------------

    @Test
    void portAOutput() {

        via.write(0x03, 0xFF);
        via.write(0x01, 0x3C);

        assertEquals(0x3C, via.read(0x01));
    }

    @Test
    void portAInput() {

        via.write(0x03, 0x00);
        via.setPortAInput(0xC3);

        assertEquals(0xC3, via.read(0x01));
    }

    // ---------------------------------------------------------------------
    // ACR / PCR / SR
    // ---------------------------------------------------------------------

    @Test
    void auxiliaryControlRegister() {

        via.write(0x0B, 0x65);

        assertEquals(0x65, via.read(0x0B));
    }

    @Test
    void peripheralControlRegister() {

        via.write(0x0C, 0xAA);

        assertEquals(0xAA, via.read(0x0C));
    }

    @Test
    void shiftRegister() {

        via.write(0x0A, 0x5A);

        assertEquals(0x5A, via.read(0x0A));
    }

    // ---------------------------------------------------------------------
    // Timer 1
    // ---------------------------------------------------------------------

    @Test
    void timer1LowByteCanBeWritten() {

        via.write(0x04, 0x34);

        assertEquals(0x34, via.read(0x06));
    }

    @Test
    void timer1HighByteLoadsCounter() {

        via.write(0x04, 0x34);
        via.write(0x05, 0x12);

        assertEquals(0x34, via.read(0x04));
        assertEquals(0x12, via.read(0x05));
    }

    @Test
    void timer1InterruptFlagIsInitiallyClear() {

        assertEquals(
                0x00,
                via.read(0x0D) & 0x40);
    }

    @Test
    void timer1GeneratesInterruptFlag() {

        /*
         * Cargar T1 con un valor pequeño.
         */
        via.write(0x04, 0x01);
        via.write(0x05, 0x00);

        /*
         * En nuestra implementación actual el contador llega
         * a cero y genera el flag.
         */
        via.tick();
        via.tick();

        assertTrue(
                (via.read(0x0D) & 0x40) != 0);
    }

@Test
void timer1OneShotGeneratesOnlyOneInterrupt() {

    boolean[] irq = { false };

    via.setIrqListener(active -> irq[0] = active);

    // Enable T1 IRQ
    via.write(0x0E, 0xC0);

    // Load T1 = 2
    via.write(0x04, 0x02);
    via.write(0x05, 0x00);

    via.tick();
    assertFalse(irq[0]);

    via.tick();

    assertTrue(irq[0]);
    assertTrue((via.read(0x0D) & 0xC0) == 0xC0);

    // Clear IFR6
    via.write(0x0D, 0x40);

    assertFalse(irq[0]);

    // T1 continúa después del timeout pero no vuelve
    // a generar IFR6.
    via.tick();
    via.tick();
    via.tick();

    assertFalse(irq[0]);
    assertEquals(0x00, via.read(0x0D) & 0x40);
}

@Test
void timer1FreeRunGeneratesRepeatedInterrupts() {

    int[] irqCount = { 0 };

    via.setIrqListener(active -> {
        if (active) {
            irqCount[0]++;
        }
    });

    // ACR6 = 1 -> free running
    via.write(0x0B, 0x40);

    // Enable T1 IRQ
    via.write(0x0E, 0xC0);

    // T1 = 2
    via.write(0x04, 0x02);
    via.write(0x05, 0x00);

    via.tick();
    via.tick();

    assertEquals(1, irqCount[0]);

    // Limpiamos IFR6
    via.write(0x0D, 0x40);

    via.tick();
    via.tick();

    assertEquals(2, irqCount[0]);
}



    // ---------------------------------------------------------------------
    // Timer 2
    // ---------------------------------------------------------------------

    @Test
    void timer2InterruptFlagIsInitiallyClear() {

        assertEquals(
                0x00,
                via.read(0x0D) & 0x20);
    }

    @Test
    void timer2GeneratesInterruptFlag() {

        via.write(0x08, 0x01);
        via.write(0x09, 0x00);

        via.tick();
        via.tick();

        assertTrue(
                (via.read(0x0D) & 0x20) != 0);
    }

@Test
void timer2OneShotGeneratesInterrupt() {

    boolean[] irq = { false };

    via.setIrqListener(active -> irq[0] = active);

    // Enable T2 IRQ
    via.write(0x0E, 0xA0);

    // T2 = 2
    via.write(0x08, 0x02);
    via.write(0x09, 0x00);

    via.tick();
    assertFalse(irq[0]);

    via.tick();

    assertTrue(irq[0]);
    assertTrue((via.read(0x0D) & 0x20) != 0);
}

@Test
void timer2CountsNegativeEdgesOnPB6() {

    boolean[] irq = { false };

    via.setIrqListener(active -> irq[0] = active);

    // ACR5 = 1 -> pulse counting
    via.write(0x0B, 0x20);

    // Enable T2 IRQ
    via.write(0x0E, 0xA0);

    // T2 = 2
    via.write(0x08, 0x02);
    via.write(0x09, 0x00);

    // Inicialmente PB6 HIGH
    via.setPortBInput(0x40);

    // HIGH -> LOW: primer pulso
    via.setPortBInput(0x00);

    assertFalse(irq[0]);

    // LOW -> HIGH
    via.setPortBInput(0x40);

    // HIGH -> LOW: segundo pulso
    via.setPortBInput(0x00);

    assertTrue(irq[0]);
    assertTrue((via.read(0x0D) & 0x20) != 0);
}

    // ---------------------------------------------------------------------
    // IFR
    // ---------------------------------------------------------------------

    @Test
    void writingOneToIFRClearsFlag() {

        via.write(0x04, 0x01);
        via.write(0x05, 0x00);

        via.tick();
        via.tick();

        assertTrue(
                (via.read(0x0D) & 0x40) != 0);

        /*
         * Escribir un 1 en el bit T1 limpia el flag.
         */
        via.write(0x0D, 0x40);

        assertEquals(
                0x00,
                via.read(0x0D) & 0x40);
    }

    @Test
    void writingZeroToIFRDoesNotClearFlag() {

        via.write(0x04, 0x01);
        via.write(0x05, 0x00);

        via.tick();
        via.tick();

        assertTrue(
                (via.read(0x0D) & 0x40) != 0);

        via.write(0x0D, 0x00);

        assertTrue(
                (via.read(0x0D) & 0x40) != 0);
    }

    // ---------------------------------------------------------------------
    // IER
    // ---------------------------------------------------------------------

    @Test
    void ierCanEnableTimer1Interrupt() {

        via.write(0x0E, 0xC0);

        assertEquals(
                0xC0,
                via.read(0x0E));
    }

    @Test
    void ierCanDisableTimer1Interrupt() {

        via.write(0x0E, 0xC0);

        assertEquals(
                0xC0,
                via.read(0x0E));

        /*
         * Bit 7 = 0 significa limpiar los bits indicados.
         */
        via.write(0x0E, 0x40);

        assertEquals(
                0x80,
                via.read(0x0E));
    }

    // ---------------------------------------------------------------------
    // IRQ
    // ---------------------------------------------------------------------

    @Test
    void irqIsNotGeneratedWhenInterruptIsDisabled() {

        boolean[] irq = { false };

        via.setIrqListener(
                active -> irq[0] = active);

        /*
         * T1 timeout.
         */
        via.write(0x04, 0x01);
        via.write(0x05, 0x00);

        via.tick();
        via.tick();

        assertFalse(irq[0]);

        /*
         * El flag sí debe existir.
         */
        assertTrue(
                (via.read(0x0D) & 0x40) != 0);
    }

    @Test
    void irqIsGeneratedWhenTimer1InterruptIsEnabled() {

        boolean[] irq = { false };

        via.setIrqListener(
                active -> irq[0] = active);

        /*
         * Habilitar T1 en IER.
         *
         * 0x80 = set
         * 0x40 = T1
         */
        via.write(0x0E, 0xC0);

        via.write(0x04, 0x01);
        via.write(0x05, 0x00);

        via.tick();
        via.tick();

        assertTrue(irq[0]);

        assertTrue(
                (via.read(0x0D) & 0x80) != 0);
    }

    @Test
    void clearingInterruptFlagRemovesIrq() {

        boolean[] irq = { false };

        via.setIrqListener(
                active -> irq[0] = active);

        via.write(0x0E, 0xC0);

        via.write(0x04, 0x01);
        via.write(0x05, 0x00);

        via.tick();
        via.tick();

        assertTrue(irq[0]);

        /*
         * Limpiar T1 IFR.
         */
        via.write(0x0D, 0x40);

        assertFalse(irq[0]);

        assertEquals(
                0x00,
                via.read(0x0D) & 0xC0);
    }

    // ---------------------------------------------------------------------
    // Reset posterior a actividad
    // ---------------------------------------------------------------------

    @Test
    void resetClearsInterruptState() {

        boolean[] irq = { false };

        via.setIrqListener(
                active -> irq[0] = active);

        via.write(0x0E, 0xC0);

        via.write(0x04, 0x01);
        via.write(0x05, 0x00);

        via.tick();
        via.tick();

        assertTrue(irq[0]);

        via.reset();

        assertFalse(irq[0]);

        assertEquals(
                0x00,
                via.read(0x0D) & 0x7F);

        assertEquals(
                0x80,
                via.read(0x0E));
    }
}
