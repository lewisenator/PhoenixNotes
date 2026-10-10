package com.lewisenator.phoenixnotes;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/** The notepad window. Kept thin: reading and writing the note is {@link Note}'s job. */
final class Notepad {

    /** Saves this long after the last keystroke, so typing doesn't write the file on every key. */
    private static final int SAVE_DELAY_MILLIS = 1000;

    private final Note note;
    private final String version;
    private final JFrame frame = new JFrame();
    private final JTextArea text = new JTextArea();
    private final JLabel status = new JLabel();
    private final Timer autosave = new Timer(SAVE_DELAY_MILLIS, event -> save());

    private Notepad(Note note, String version, String savedText) {
        this.note = note;
        this.version = version;
        text.setText(savedText);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        // Every edit restarts the timer, so it only fires once typing pauses.
        autosave.setRepeats(false);
        text.getDocument().addUndoableEditListener(event -> autosave.restart());

        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        showStatus("Saved");

        frame.setTitle("Phoenix Notes " + version);
        frame.add(new JScrollPane(text), BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.setSize(640, 480);
        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                autosave.stop();
                save();
            }
        });
    }

    /** Saves the note now, and shows whether it worked. */
    void save() {
        try {
            note.write(text.getText());
            showStatus("Saved");
        } catch (IOException e) {
            showStatus("Couldn't save: " + e.getMessage());
        }
    }

    JFrame frame() {
        return frame;
    }

    JTextArea text() {
        return text;
    }

    JLabel status() {
        return status;
    }

    private void showStatus(String message) {
        status.setText("Version " + version + " · " + message);
    }

    /** Builds and shows the window on Swing's event thread, where all UI work must happen. */
    static Notepad open(Note note, String version) throws IOException {
        FlatLightLaf.setup();
        var savedText = note.read();
        return CompletableFuture.supplyAsync(
                        () -> {
                            var notepad = new Notepad(note, version, savedText);
                            notepad.frame.setVisible(true);
                            return notepad;
                        },
                        SwingUtilities::invokeLater)
                .join();
    }
}
