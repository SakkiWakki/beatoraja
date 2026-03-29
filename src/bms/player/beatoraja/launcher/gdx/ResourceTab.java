package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.TableDataAccessor;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;

import javax.swing.JFileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ResourceBundle;

public class ResourceTab implements LauncherTab {

    private com.badlogic.gdx.scenes.scene2d.ui.List<String> bmsrootList;
    private TextField urlField;
    private com.badlogic.gdx.scenes.scene2d.ui.List<String> tableurlList;
    private CheckBox updatesong;
    private TextField bgmpath;
    private TextField soundpath;

    private Config config;
    private java.util.function.Consumer<String> onPathAdded;

    public void setOnPathAdded(java.util.function.Consumer<String> callback) {
        this.onPathAdded = callback;
    }

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        Table root = new Table(skin);
        root.top().left().pad(8);
        root.defaults().left().pad(6);

        // BMS root paths
        root.add(new Label(bundle.getString("BMS_Path"), skin, "large")).colspan(3).padBottom(4);
        root.row();

        bmsrootList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
        ScrollPane bmsScroll = new ScrollPane(bmsrootList, skin);
        bmsScroll.setFadeScrollBars(false);
        root.add(bmsScroll).width(500).height(100).colspan(2);

        Table bmsButtons = new Table(skin);
        TextButton addPathBtn = new TextButton("Add", skin);
        addPathBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                addSongPath();
            }
        });
        TextButton removePathBtn = new TextButton("Remove", skin);
        removePathBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                removeSongPath();
            }
        });
        bmsButtons.add(addPathBtn).width(80).row();
        bmsButtons.add(removePathBtn).width(80).padTop(4);
        root.add(bmsButtons).top();
        root.row();

        updatesong = new CheckBox(" " + bundle.getString("UPDATE_SONG"), skin);
        root.add(updatesong).colspan(3).padTop(4);
        root.row();

        // BGM / Sound paths
        root.add(new Label(bundle.getString("BGM_Path(LR2)"), skin));
        bgmpath = new TextField("", skin);
        root.add(bgmpath).width(300);
        TextButton bgmBtn = new TextButton("...", skin);
        bgmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String s = showDirectoryChooser("Select BGM folder");
                if (s != null) bgmpath.setText(s);
            }
        });
        root.add(bgmBtn).width(30);
        root.row();

        root.add(new Label(bundle.getString("Sound_Path(LR2)"), skin));
        soundpath = new TextField("", skin);
        root.add(soundpath).width(300);
        TextButton soundBtn = new TextButton("...", skin);
        soundBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String s = showDirectoryChooser("Select Sound folder");
                if (s != null) soundpath.setText(s);
            }
        });
        root.add(soundBtn).width(30);
        root.row();

        // Table URLs
        root.add(new Label("Difficulty Tables", skin, "large")).colspan(3).padTop(12).padBottom(4);
        root.row();

        tableurlList = new com.badlogic.gdx.scenes.scene2d.ui.List<>(skin);
        ScrollPane tableScroll = new ScrollPane(tableurlList, skin);
        tableScroll.setFadeScrollBars(false);
        root.add(tableScroll).width(500).height(120).colspan(2);

        Table tableButtons = new Table(skin);
        TextButton addUrlBtn = new TextButton("Add", skin);
        addUrlBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                addTableURL();
            }
        });
        TextButton removeUrlBtn = new TextButton("Remove", skin);
        removeUrlBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                removeTableURL();
            }
        });
        TextButton loadAllBtn = new TextButton("Load All", skin);
        loadAllBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                loadAllTables();
            }
        });
        TextButton moveUpBtn = new TextButton("Up", skin);
        moveUpBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveTableURL(-1);
            }
        });
        TextButton moveDownBtn = new TextButton("Down", skin);
        moveDownBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                moveTableURL(1);
            }
        });
        tableButtons.add(addUrlBtn).width(80).row();
        tableButtons.add(removeUrlBtn).width(80).padTop(4).row();
        tableButtons.add(moveUpBtn).width(80).padTop(4).row();
        tableButtons.add(moveDownBtn).width(80).padTop(4).row();
        tableButtons.add(loadAllBtn).width(80).padTop(4);
        root.add(tableButtons).top();
        root.row();

        Table urlRow = new Table(skin);
        urlRow.add(new Label("URL:", skin)).padRight(4);
        urlField = new TextField("", skin);
        urlRow.add(urlField).width(400);
        root.add(urlRow).colspan(3).padTop(4);
        root.row();

        ScrollPane scrollPane = new ScrollPane(root, skin);
        scrollPane.setFadeScrollBars(false);
        return scrollPane;
    }

    private void addSongPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select BMS root folder");
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            String defaultPath = new File(".").getAbsoluteFile().getParent() + File.separatorChar;
            String targetPath = f.getAbsolutePath();
            if (targetPath.startsWith(defaultPath)) {
                targetPath = targetPath.substring(defaultPath.length());
            }
            Array<String> items = new Array<>(bmsrootList.getItems());
            for (String path : items) {
                if (path.equals(targetPath) || targetPath.startsWith(path + File.separatorChar)) {
                    return;
                }
            }
            items.add(targetPath);
            bmsrootList.setItems(items);
            if (onPathAdded != null) onPathAdded.accept(targetPath);
        }
    }

    private void removeSongPath() {
        String selected = bmsrootList.getSelected();
        if (selected == null) return;
        Array<String> items = new Array<>(bmsrootList.getItems());
        items.removeValue(selected, false);
        bmsrootList.setItems(items);
    }

    private void addTableURL() {
        String s = urlField.getText();
        if (s != null && s.startsWith("http")) {
            Array<String> items = new Array<>(tableurlList.getItems());
            if (!items.contains(s, false)) {
                items.add(s);
                tableurlList.setItems(items);
            }
        }
    }

    private void moveTableURL(int direction) {
        int idx = tableurlList.getSelectedIndex();
        if (idx < 0) return;
        Array<String> items = new Array<>(tableurlList.getItems());
        int newIdx = idx + direction;
        if (newIdx < 0 || newIdx >= items.size) return;
        items.swap(idx, newIdx);
        tableurlList.setItems(items);
        tableurlList.setSelectedIndex(newIdx);
    }

    private void removeTableURL() {
        String selected = tableurlList.getSelected();
        if (selected == null) return;
        Array<String> items = new Array<>(tableurlList.getItems());
        items.removeValue(selected, false);
        tableurlList.setItems(items);
    }

    private void loadAllTables() {
        if (config == null) return;
        commit(config);
        try {
            Files.createDirectories(Paths.get(config.getTablepath()));
        } catch (IOException ignored) {}

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get(config.getTablepath()))) {
            paths.forEach(p -> {
                if (p.toString().toLowerCase().endsWith(".bmt")) {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                }
            });
        } catch (IOException ignored) {}

        TableDataAccessor tda = new TableDataAccessor(config.getTablepath());
        tda.updateTableData(config.getTableURL());
    }

    private String showDirectoryChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle(title);
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile().getPath();
        }
        return null;
    }

    @Override
    public void update(Config config) {
        this.config = config;
        bmsrootList.setItems(config.getBmsroot());
        updatesong.setChecked(config.isUpdatesong());
        tableurlList.setItems(config.getTableURL());
        bgmpath.setText(config.getBgmpath());
        soundpath.setText(config.getSoundpath());
    }

    @Override
    public void commit(Config config) {
        Array<String> bmsItems = bmsrootList.getItems();
        String[] roots = new String[bmsItems.size];
        for (int i = 0; i < bmsItems.size; i++) roots[i] = bmsItems.get(i);
        config.setBmsroot(roots);

        config.setUpdatesong(updatesong.isChecked());

        Array<String> tableItems = tableurlList.getItems();
        String[] urls = new String[tableItems.size];
        for (int i = 0; i < tableItems.size; i++) urls[i] = tableItems.get(i);
        config.setTableURL(urls);

        config.setBgmpath(bgmpath.getText());
        config.setSoundpath(soundpath.getText());
    }
}
