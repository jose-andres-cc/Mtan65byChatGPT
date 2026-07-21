package org.microtan.core.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CharacterROM {

        private final byte[] rom;

public CharacterROM(Path path) throws IOException {

    rom = Files.readAllBytes(path);

    if (rom.length != 1024 &&
        rom.length != 2048 &&
        rom.length != 4096) {

        throw new IllegalArgumentException(
            "Tamaño de ROM no soportado.");
    }
}

    public int getScanLine(int character, int row) {
        return rom[(character << 4) + row] & 0xFF;
    }

}
