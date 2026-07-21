package org.microtan.core.memory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RomLoader {

    private RomLoader() {
    }

    public static ROM load(Path file) throws IOException {

        byte[] data = Files.readAllBytes(file);

        return new ROM(data);
    }

}