package org.microtan.core.video;

public record VideoConfiguration(
        int columns,
        int rows,
        int charWidth,
        int charHeight,
        int videoRamAddress,
        int frameRate) {
}