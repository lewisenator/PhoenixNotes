package com.lewisenator.phoenixnotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataFolderTest {

    @Test
    void listsItsFiles() {
        var folder = new DataFolder(Path.of("data"));

        assertThat(folder.note()).isEqualTo(Path.of("data", "note.txt"));
        assertThat(folder.lock()).isEqualTo(Path.of("data", "app.lock"));
    }

    @Test
    void usesThisUsersAppDataFolder() {
        var path = DataFolder.forCurrentUser().path();

        assertThat(path.getFileName().toString()).isEqualToIgnoringCase("PhoenixNotes");
        assertThat(path).startsWithRaw(Path.of(System.getProperty("user.home")).getRoot());
    }
}
