package com.lewisenator.phoenixnotes;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
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

    private Notepad(Note note, String version, String savedText, Optional<Rectangle> window) {
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
        // After a handoff, open exactly where the old version's window was.
        window.ifPresentOrElse(frame::setBounds, () -> {
            frame.setSize(640, 480);
            frame.setLocationByPlatform(true);
        });
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

    /**
     * Saves the note for the version taking over, and returns where the window is so it can open in
     * the same place. Unlike {@link #save()}, fails if the note can't be saved, so nothing typed is lost.
     */
    Rectangle saveForHandoff() throws IOException {
        note.write(onEventThread(text::getText));
        return onEventThread(frame::getBounds);
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

    /**
     * Builds and shows the window on Swing's event thread. Opens at
     * {@code window} if given (after a handoff), otherwise wherever the OS puts new windows.
     */
    static Notepad open(Note note, String version, Optional<Rectangle> window) throws IOException {
        FlatLightLaf.setup();
        var savedText = note.read();
        return onEventThread(() -> {
            var notepad = new Notepad(note, version, savedText, window);
            notepad.frame.setVisible(true);
            return notepad;
        });
    }

    /** Runs {@code work} on Swing's event thread, where all UI work must happen, and waits for it. */
    private static <T> T onEventThread(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, SwingUtilities::invokeLater).join();
    }
}
