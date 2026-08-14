package org.microtan.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.microtan.core.io.keyboard.Keyboard;
import org.microtan.core.io.via.Via6522;

class MicrotanIoTest {

    private MicrotanIo io;
    private Via6522 via;
    private Keyboard keyboard;

    @BeforeEach
    void setUp() {

        via = new Via6522();
        keyboard = new Keyboard();

        io = new MicrotanIo(
                via,
                keyboard);
    }

    @Test
    void viaIsMappedAtBfc0() {

        /*
         * La dirección que recibe MicrotanIo es relativa a BC00.
         *
         * BFC2 - BC00 = 03C2
         *
         * 03C2 - 03C0 = 02 -> DDRB
         */
        io.write(0x03C2, 0xFF);

        assertEquals(
                0xFF,
                io.read(0x03C2));
    }

    @Test
    void keyboardRemainsMappedAtBff3() {

        keyboard.keyPressed('A');

        assertEquals(
                0xC1,
                io.read(0x03F3));
    }

    @Test
    void keyboardInterruptCanBeClearedAtBff0() {

        keyboard.keyPressed('A');

        assertEquals(
                0xC1,
                io.read(0x03F3));

        io.write(0x03F0, 0x00);

        assertEquals(
                0x41,
                io.read(0x03F3));
    }
}