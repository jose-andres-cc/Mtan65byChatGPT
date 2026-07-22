package org.microtan.core.video;

import org.microtan.core.memory.RAM;
import org.microtan.core.bus.Bus;
import org.microtan.core.video.CharacterROM;

public class VideoController {

    public static final int COLUMNS = 32;
    public static final int ROWS = 16;

    public static final int CHAR_WIDTH = 8;
    public static final int CHAR_HEIGHT = 16;

    public static final int SCREEN_WIDTH = COLUMNS * CHAR_WIDTH;
    public static final int SCREEN_HEIGHT = ROWS * CHAR_HEIGHT;

    /**
     * Dirección inicial de la memoria de pantalla.
     * Ajustar según la configuración de la máquina.
     */
    public static final int VIDEO_RAM = 0x0200;

    //FRAME_CYCLES = CPU_FREQUENCY / FRAME_RATE
    //CPU = 750 kHz (750 000 Hz)
    //Vídeo = 50 Hz (PAL)
    // tambien
    //frameCycles =
    //    cpuFrequency / video.refreshRate();
    private static final int FRAME_CYCLES = 15000;

    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xFFFFFF;

    private final Bus bus;

    private final CharacterROM characterRom;

    private final FrameBuffer frameBuffer;

    private int accumulatedCycles = 0;

    //Mejora: VideoController configurable
// public VideoController(
//         Bus bus,
//         CharacterROM rom,
//         VideoConfiguration config)


    public VideoController(Bus bus, CharacterROM characterRom) {

        this.bus = bus;
        this.characterRom = characterRom;

        this.frameBuffer = new FrameBuffer(
                SCREEN_WIDTH,
                SCREEN_HEIGHT);
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

    /**
     * Genera un frame completo.
     */
    public void render() {

        for (int row = 0; row < ROWS; row++) {

            int screenAddress = VIDEO_RAM + row * COLUMNS;

            for (int column = 0; column < COLUMNS; column++) {

                int character =
                        bus.read(screenAddress + column);

                drawCharacter(column, row, character);
            }
        }
    }

    /**
     * Dibuja un carácter de 8x16.
     */
    private void drawCharacter(
            int column,
            int row,
            int character) {

        int x0 = column * CHAR_WIDTH;
        int y0 = row * CHAR_HEIGHT;

        for (int scanLine = 0;
             scanLine < CHAR_HEIGHT;
             scanLine++) {

            int pattern =
                    characterRom.getScanLine(
                            character,
                            scanLine);

            for (int bit = 0; bit < 8; bit++) {

                boolean pixel =
                        (pattern & (0x80 >> bit)) != 0;

                frameBuffer.setPixel(
                        x0 + bit,
                        y0 + scanLine,
                        pixel ? WHITE : BLACK);
            }
        }
    }


public void tick(int cycles) {

    accumulatedCycles += cycles;

    if (accumulatedCycles >= FRAME_CYCLES) {

        accumulatedCycles -= FRAME_CYCLES;

        render();
    }

    // Mejora: dibujar linea a linea con drawScanLine();
}


}