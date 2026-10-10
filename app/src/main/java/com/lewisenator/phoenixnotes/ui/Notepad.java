package com.lewisenator.phoenixnotes.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.lewisenator.phoenixnotes.DataFolder;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.desktop.QuitStrategy;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.PlainDocument;
import javax.swing.undo.UndoManager;

/** The notepad window. Kept thin: reading and writing files is {@link Note}'s job. */
public final class Notepad {

    /** Saves this long after the last keystroke, so typing doesn't write the file on every key. */
    private static final int SAVE_DELAY_MILLIS = 1000;

    /** How long a message over the window stays up. */
    private static final int MESSAGE_MILLIS = 4000;

    private static final boolean MAC = System.getProperty("os.name").startsWith("Mac");

    private final DataFolder folder;
    private final String version;
    private Note note;
    private final JFrame frame = new JFrame();
    private final JTextArea text = new JTextArea();
    private final UndoManager undo = new UndoManager();
    private final JLabel status = new JLabel();
    private final JLabel updateStatus = new JLabel();
    private final JMenuItem checkForUpdates = new JMenuItem("Check for Updates");
    private final Timer autosave = new Timer(SAVE_DELAY_MILLIS, event -> save());
    private final Overlay overlay = new Overlay();
    private final Timer hideOverlay = new Timer(MESSAGE_MILLIS, event -> overlay.setVisible(false));

    private Notepad(DataFolder folder, Note note, String version, String savedText, Optional<Rectangle> window) {
        this.folder = folder;
        this.version = version;
        this.note = note;
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        autosave.setRepeats(false);
        load(note, savedText);

        var statusLine = new JPanel(new BorderLayout());
        statusLine.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLine.add(status, BorderLayout.CENTER);
        statusLine.add(updateStatus, BorderLayout.EAST);

        hideOverlay.setRepeats(false);
        frame.setGlassPane(overlay);
        frame.setJMenuBar(menuBar());
        frame.add(new JScrollPane(text), BorderLayout.CENTER);
        frame.add(statusLine, BorderLayout.SOUTH);
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
    boolean save() {
        try {
            note.write(text.getText());
            showStatus("Saved");
            return true;
        } catch (IOException e) {
            showStatus("Couldn't save: " + e.getMessage());
            return false;
        }
    }

    /**
     * Opens a text file in place of the current note, which is saved first. Files that aren't UTF-8
     * aren't opened, and the current note stays.
     */
    void open(Path file) {
        autosave.stop();
        if (!save()) {
            return;
        }
        var opened = new Note(file);
        try {
            var savedText = opened.read();
            opened.rememberIn(folder);
            load(opened, savedText);
        } catch (IOException e) {
            overlay.show("Couldn't open " + file.getFileName(), false);
            hideOverlay.restart();
            showStatus(e.getMessage());
        }
    }

    /**
     * Saves the note for the version taking over, and returns where the window is so it can open in
     * the same place. Unlike {@link #save()}, fails if the note can't be saved, so nothing typed is lost.
     */
    public Rectangle saveForHandoff() throws IOException {
        var current = onEventThread(() -> note);
        current.write(onEventThread(text::getText));
        return onEventThread(frame::getBounds);
    }

    /** What to do when Help → Check for Updates is chosen. */
    public void onCheckForUpdates(Runnable check) {
        SwingUtilities.invokeLater(() -> checkForUpdates.addActionListener(event -> check.run()));
    }

    /**
     * Shows how a check for updates is going on the status line: "Checking…" while it runs, when it
     * can't be started again, then the result.
     */
    public void showCheck(String result, boolean done) {
        SwingUtilities.invokeLater(() -> {
            updateStatus.setText(result);
            checkForUpdates.setEnabled(done);
        });
    }

    /**
     * Shows "Updating to X…" over the window and stops typing, so nothing is typed after the note is
     * saved for the new version. Stays up until this version exits, or {@link #showBriefly} replaces it.
     */
    public void showUpdating(String newVersion) {
        SwingUtilities.invokeLater(() -> {
            hideOverlay.stop();
            text.setEditable(false);
            overlay.show("Updating to " + newVersion + "…", true);
        });
    }

    /** Shows a message over the window for a few seconds, like "Updated to 1.0.13". */
    public void showBriefly(String message) {
        SwingUtilities.invokeLater(() -> {
            text.setEditable(true);
            overlay.show(message, false);
            hideOverlay.restart();
        });
    }

    public JFrame frame() {
        return frame;
    }

    public JTextArea text() {
        return text;
    }

    public JLabel updateStatus() {
        return updateStatus;
    }

    JLabel status() {
        return status;
    }

    Overlay overlay() {
        return overlay;
    }

    UndoManager undo() {
        return undo;
    }

    /**
     * Shows a note's text in a fresh document, so loading it is neither an edit to undo nor a reason
     * to save. Edits after that restart the autosave timer, so it only fires once typing pauses.
     */
    private void load(Note opened, String savedText) {
        note = opened;
        text.setDocument(new PlainDocument());
        text.setText(savedText);
        text.setCaretPosition(0);
        text.getDocument().addUndoableEditListener(event -> {
            undo.addEdit(event.getEdit());
            autosave.restart();
        });
        undo.discardAllEdits();
        var name = note.file().equals(folder.note()) ? "" : note.file().getFileName() + " — ";
        frame.setTitle(name + "Phoenix Notes " + version);
        showStatus("Saved");
    }

    /**
     * File, Edit and Help, with the usual shortcuts: ⌘ on macOS, Ctrl elsewhere. On macOS they're in
     * the menu bar at the top of the screen, which also has Quit; elsewhere they're in the window.
     */
    private JMenuBar menuBar() {
        var file = new JMenu("File");
        file.add(item("Open…", KeyEvent.VK_O, 0, event -> chooseFile()));
        file.add(item("Save", KeyEvent.VK_S, 0, event -> save()));
        if (!MAC) {
            file.addSeparator();
            file.add(item(
                    "Exit", 0, 0, event -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
        }

        var edit = new JMenu("Edit");
        edit.add(item("Undo", KeyEvent.VK_Z, 0, event -> {
            if (undo.canUndo()) {
                undo.undo();
            }
        }));
        edit.add(item("Redo", MAC ? KeyEvent.VK_Z : KeyEvent.VK_Y, MAC ? InputEvent.SHIFT_DOWN_MASK : 0, event -> {
            if (undo.canRedo()) {
                undo.redo();
            }
        }));
        edit.addSeparator();
        edit.add(item("Cut", KeyEvent.VK_X, 0, new DefaultEditorKit.CutAction()));
        edit.add(item("Copy", KeyEvent.VK_C, 0, new DefaultEditorKit.CopyAction()));
        edit.add(item("Paste", KeyEvent.VK_V, 0, new DefaultEditorKit.PasteAction()));
        edit.addSeparator();
        edit.add(item("Select All", KeyEvent.VK_A, 0, event -> text.selectAll()));

        var help = new JMenu("Help");
        help.add(checkForUpdates);

        var bar = new JMenuBar();
        bar.add(file);
        bar.add(edit);
        bar.add(help);
        return bar;
    }

    /** Asks which file to open, with the OS's own dialog. */
    private void chooseFile() {
        var dialog = new FileDialog(frame, "Open", FileDialog.LOAD);
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            open(Path.of(dialog.getDirectory(), dialog.getFile()));
        }
    }

    private void showStatus(String message) {
        status.setText("Version " + version + " · " + message);
    }

    /**
     * A menu item with a shortcut: {@code key} with ⌘ (macOS) or Ctrl, plus any {@code extraModifiers}.
     * A {@code key} of 0 means no shortcut.
     */
    private static JMenuItem item(String name, int key, int extraModifiers, ActionListener action) {
        var item = new JMenuItem(name);
        if (key != 0) {
            var menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            item.setAccelerator(KeyStroke.getKeyStroke(key, menuKey | extraModifiers));
        }
        item.addActionListener(action);
        return item;
    }

    /**
     * Builds and shows the window on Swing's event thread, with the note opened last (or the app's own
     * note). Opens at {@code window} if given (after a handoff), otherwise wherever the OS puts new
     * windows.
     */
    public static Notepad open(DataFolder folder, String version, Optional<Rectangle> window) throws IOException {
        // On macOS: menus in the screen's menu bar, under the app's name. Must be set before Swing starts.
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Phoenix Notes");
        FlatLightLaf.setup();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_STRATEGY)) {
            // ⌘Q closes the window, which saves, instead of exiting straight away.
            Desktop.getDesktop().setQuitStrategy(QuitStrategy.CLOSE_ALL_WINDOWS);
        }
        var note = Note.last(folder);
        var savedText = note.read();
        return onEventThread(() -> {
            var notepad = new Notepad(folder, note, version, savedText, window);
            notepad.frame.setVisible(true);
            return notepad;
        });
    }

    /** Runs {@code work} on Swing's event thread, where all UI work must happen, and waits for it. */
    private static <T> T onEventThread(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, SwingUtilities::invokeLater).join();
    }
}
