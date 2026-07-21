package org.microtan.core.video;

public class FrameBuffer {

    private final int width;
    private final int height;
    private final int[] pixels;

    public FrameBuffer(int width, int height) {

        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[] getPixels() {
        return pixels;
    }

    public void setPixel(int x, int y, int colour) {
        pixels[y * width + x] = colour;
    }

    public int getPixel(int x, int y) {
        return pixels[y * width + x];
    }
}