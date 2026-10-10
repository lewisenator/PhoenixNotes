package com.lewisenator.phoenixnotes.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 * Dims the window and shows a message in the middle, so an update is impossible to miss. Used as the
 * window's glass pane, which sits over everything and so catches the clicks while it's up.
 */
final class Overlay extends JPanel {

    private static final Color DIM = new Color(0, 0, 0, 110);

    private final JLabel message = new JLabel();
    private final JProgressBar progress = new JProgressBar();

    Overlay() {
        super(new GridBagLayout());
        setOpaque(false);
        message.setFont(message.getFont().deriveFont(Font.BOLD, 18f));
        progress.setIndeterminate(true);

        var box = new JPanel(new BorderLayout(0, 12));
        box.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));
        box.add(message, BorderLayout.CENTER);
        box.add(progress, BorderLayout.SOUTH);
        add(box);
        // Listening at all is what stops clicks reaching the window underneath.
        addMouseListener(new MouseAdapter() {});
    }

    /** @param busy whether to show a progress bar, for something still happening */
    void show(String text, boolean busy) {
        message.setText(text);
        progress.setVisible(busy);
        setVisible(true);
    }

    JLabel message() {
        return message;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(DIM);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }
}
