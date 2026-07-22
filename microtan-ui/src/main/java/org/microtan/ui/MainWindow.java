package org.microtan.ui;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.microtan.ui.video.FrameListener;
//import org.microtan.core.machine.Microtan65;
import org.microtan.core.video.VideoController;
import org.microtan.ui.video.VideoPanel;

public class MainWindow extends JFrame implements FrameListener {

    private final VideoPanel videoPanel;

    public MainWindow(VideoController videoController) {

        super("Microtan 65");

        videoPanel =
                new VideoPanel(videoController);

        add(videoPanel);

        pack();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    public VideoPanel getVideoPanel() {

        return videoPanel;

    }

    @Override
    public void frameCompleted() {

        SwingUtilities.invokeLater(
                videoPanel::refresh);

    }

}