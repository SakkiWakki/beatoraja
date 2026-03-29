package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.TableData;
import bms.player.beatoraja.song.SongDatabaseAccessor;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

/**
 * Table editor tab. Provides basic table name editing and save functionality.
 * Course and folder editing requires further porting of CourseEditorView/FolderEditorView.
 */
public class TableEditorTab implements LauncherTab {

    private TextField tableName;
    private Label statusLabel;
    private Path filepath;
    private SongDatabaseAccessor songdb;

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table root = new Table(skin);
        root.top().left().pad(8);
        root.defaults().left().pad(6);

        root.add(new Label("Table Editor", skin, "large")).colspan(2).padBottom(8);
        root.row();

        root.add(new Label("Table Name", skin)).width(100);
        tableName = new TextField("", skin);
        root.add(tableName).width(300);
        root.row();

        TextButton saveBtn = new TextButton("Save", skin);
        saveBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                commit();
            }
        });
        root.add(saveBtn).width(80).padTop(8);
        root.row();

        statusLabel = new Label("", skin, "small");
        root.add(statusLabel).colspan(2).padTop(4);
        root.row();

        // TODO: Course editor and folder editor sub-panels

        ScrollPane scrollPane = new ScrollPane(root, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    public void init(SongDatabaseAccessor songdb) {
        this.songdb = songdb;
    }

    public void update(Config config) {
        Path p = Paths.get(config.getTablepath() + "/default.json");
        this.filepath = p;
        TableData td = TableData.read(p);
        if (td == null) {
            td = new TableData();
            td.setName("New Table");
        }
        tableName.setText(td.getName());
    }

    public void commit() {
        if (filepath == null) return;
        TableData existing = TableData.read(filepath);
        if (existing == null) existing = new TableData();
        existing.setName(tableName.getText());
        TableData.write(filepath, existing);
        if (statusLabel != null) statusLabel.setText("Saved.");
    }
}
