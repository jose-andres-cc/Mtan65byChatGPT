package org.microtan.ui.video;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import org.microtan.core.video.FrameBuffer;
import org.microtan.core.video.VideoController;

public class VideoPanel extends JPanel {

    private final VideoController video;

    private final BufferedImage image;

    private final int scale;

    public VideoPanel(VideoController video) {
        this(video, 2);
    }

    public VideoPanel(VideoController video, int scale) {

        this.video = video;
        this.scale = scale;

        FrameBuffer fb = video.getFrameBuffer();

        image = new BufferedImage(
                fb.getWidth(),
                fb.getHeight(),
                BufferedImage.TYPE_INT_RGB);

        setPreferredSize(new Dimension(
                fb.getWidth() * scale,
                fb.getHeight() * scale));
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);

        FrameBuffer fb = video.getFrameBuffer();

        image.setRGB(
                0,
                0,
                fb.getWidth(),
                fb.getHeight(),
                fb.getPixels(),
                0,
                fb.getWidth());

        g.drawImage(
                image,
                0,
                0,
                fb.getWidth() * scale,
                fb.getHeight() * scale,
                null);
    }

    /**
     * Solicita el refresco de la pantalla.
     */
    public void refresh() {
        repaint();
    }

}