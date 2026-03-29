package bms.player.beatoraja.launcher.gdx;

import bms.player.beatoraja.Config;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.SkinConfig;
import bms.player.beatoraja.skin.SkinHeader;
import bms.player.beatoraja.skin.SkinHeader.*;
import bms.player.beatoraja.skin.SkinType;
import bms.player.beatoraja.skin.json.JSONSkinLoader;
import bms.player.beatoraja.skin.lr2.LR2SkinHeaderLoader;
import bms.player.beatoraja.skin.lua.LuaSkinLoader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;

import java.io.IOException;
import java.nio.file.*;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import static bms.player.beatoraja.skin.SkinProperty.*;

/**
 * Skin configuration tab. Handles skin type selection, skin file selection,
 * and dynamic custom option/file/offset properties per skin.
 */
public class SkinTab implements LauncherTab {
    /** Label column takes 30% of available width */
    private static final float LABEL_RATIO = 0.3f;

    private SelectBox<SkinType> skintypeSelector;
    private SelectBox<SkinHeaderItem> skinheaderSelector;
    private Table skinOptionsContainer;
    private ScrollPane optionsScrollPane;
    private Skin skin;

    private PlayerConfig player;
    private SkinType currentSkinType;
    private SkinHeader selectedHeader;

    private final java.util.List<SkinHeader> allSkinHeaders = new java.util.ArrayList<>();

    // Custom property widgets
    private final java.util.Map<CustomOption, SelectBox<String>> optionBoxes = new java.util.LinkedHashMap<>();
    private final java.util.Map<CustomFile, SelectBox<String>> fileBoxes = new java.util.LinkedHashMap<>();
    private final java.util.Map<CustomOffset, SpinnerWidget[]> offsetBoxes = new java.util.LinkedHashMap<>();

    @Override
    public Actor build(Skin skin, ResourceBundle bundle) {
        this.skin = skin;
        float screenW = Gdx.graphics.getWidth();

        Table root = new Table(skin);
        root.top().left().pad(screenW * 0.005f);
        root.defaults().left().pad(screenW * 0.004f);

        float labelW = screenW * 0.1f;
        float selectorW = screenW * 0.3f;

        root.add(new Label("Skin Type", skin)).width(labelW).padRight(screenW * 0.005f);
        skintypeSelector = new SelectBox<>(skin);
        skintypeSelector.setItems(SkinType.values());
        skintypeSelector.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                commitSkinType();
                updateSkinType(skintypeSelector.getSelected());
            }
        });
        root.add(skintypeSelector).growX().maxWidth(selectorW);
        root.row();

        root.add(new Label("Skin", skin)).width(labelW).padRight(screenW * 0.005f);
        skinheaderSelector = new SelectBox<>(skin);
        skinheaderSelector.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                SkinHeaderItem item = skinheaderSelector.getSelected();
                if (item != null) {
                    commitSkinHeader();
                    updateSkinHeader(item.header);
                }
            }
        });
        root.add(skinheaderSelector).growX();
        root.row();

        // Container for dynamic skin options
        skinOptionsContainer = new Table(skin);
        skinOptionsContainer.top().left();
        optionsScrollPane = new ScrollPane(skinOptionsContainer, skin);
        optionsScrollPane.setFadeScrollBars(false);
        optionsScrollPane.setScrollingDisabled(true, false);
        optionsScrollPane.setOverscroll(false, false);
        root.add(optionsScrollPane).colspan(2).grow().padTop(screenW * 0.005f);
        root.row();

        return root;
    }

    private float getLabelWidth() {
        return Gdx.graphics.getWidth() * LABEL_RATIO;
    }

    /** Reset column defaults on skinOptionsContainer (Table.clear() wipes them). */
    private void resetContainerColumns() {
        float labelW = getLabelWidth();
        skinOptionsContainer.columnDefaults(0).width(labelW).left();
        skinOptionsContainer.columnDefaults(1).growX().left();
    }

    public void scanSkins(Config config) {
        allSkinHeaders.clear();
        java.util.List<Path> skinpaths = new java.util.ArrayList<>();
        scanDir(Paths.get(config.getSkinpath()), skinpaths);
        for (Path path : skinpaths) {
            String pathStr = path.toString().toLowerCase();
            try {
                if (pathStr.endsWith(".json")) {
                    SkinHeader header = new JSONSkinLoader().loadHeader(path);
                    if (header != null) allSkinHeaders.add(header);
                } else if (pathStr.endsWith(".luaskin")) {
                    SkinHeader header = new LuaSkinLoader().loadHeader(path);
                    if (header != null) allSkinHeaders.add(header);
                } else {
                    LR2SkinHeaderLoader loader = new LR2SkinHeaderLoader(null);
                    SkinHeader header = loader.loadSkin(path, null);
                    allSkinHeaders.add(header);
                    // 7/14 key skins also apply to 5/10 key
                    if (header.getType() == SkinHeader.TYPE_LR2SKIN &&
                            (header.getSkinType() == SkinType.PLAY_7KEYS || header.getSkinType() == SkinType.PLAY_14KEYS)) {
                        SkinHeader dup = loader.loadSkin(path, null);
                        dup.setSkinType(dup.getSkinType() == SkinType.PLAY_7KEYS ? SkinType.PLAY_5KEYS : SkinType.PLAY_10KEYS);
                        allSkinHeaders.add(dup);
                    }
                }
            } catch (Exception e) {
                // Skip invalid skin files
            }
        }
    }

    private void scanDir(Path p, java.util.List<Path> paths) {
        if (Files.isDirectory(p)) {
            try (Stream<Path> sub = Files.list(p)) {
                sub.forEach(t -> scanDir(t, paths));
            } catch (IOException ignored) {}
        } else {
            String name = p.getFileName().toString().toLowerCase();
            if (name.endsWith(".lr2skin") || name.endsWith(".luaskin") || name.endsWith(".json")) {
                paths.add(p);
            }
        }
    }

    private SkinHeader[] getSkinHeaders(SkinType type) {
        java.util.List<SkinHeader> result = new java.util.ArrayList<>();
        for (SkinHeader h : allSkinHeaders) {
            if (h.getSkinType() == type) result.add(h);
        }
        return result.toArray(new SkinHeader[0]);
    }

    private void updateSkinType(SkinType type) {
        currentSkinType = type;
        SkinHeader[] headers = getSkinHeaders(type);
        Array<SkinHeaderItem> items = new Array<>();
        for (SkinHeader h : headers) items.add(new SkinHeaderItem(h));
        skinheaderSelector.setItems(items);

        if (player != null && player.getSkin()[type.getId()] != null) {
            SkinConfig skinconf = player.getSkin()[type.getId()];
            for (int i = 0; i < items.size; i++) {
                SkinHeader h = items.get(i).header;
                if (h != null && h.getPath().equals(Paths.get(skinconf.getPath()))) {
                    skinheaderSelector.setSelectedIndex(i);
                    updateSkinHeader(h);
                    return;
                }
            }
        }
        if (items.size > 0) {
            skinheaderSelector.setSelectedIndex(0);
            updateSkinHeader(items.get(0).header);
        } else {
            updateSkinHeader(null);
        }
    }

    private void updateSkinHeader(SkinHeader header) {
        selectedHeader = header;
        skinOptionsContainer.clear();
        resetContainerColumns();
        optionBoxes.clear();
        fileBoxes.clear();
        offsetBoxes.clear();

        if (header == null) return;

        float pad = Gdx.graphics.getWidth() * 0.003f;

        // Load existing properties from history
        SkinConfig.Property property = null;
        if (player != null) {
            for (SkinConfig skinc : player.getSkinHistory()) {
                if (skinc.getPath().equals(header.getPath().toString())) {
                    property = skinc.getProperties();
                    break;
                }
            }
        }
        if (property == null) property = new SkinConfig.Property();

        // Build custom options UI
        for (CustomOption option : header.getCustomOptions()) {
            SelectBox<String> combo = new SelectBox<>(skin);
            String[] items = new String[option.contents.length + 1];
            System.arraycopy(option.contents, 0, items, 0, option.contents.length);
            items[items.length - 1] = "Random";
            combo.setItems(items);

            int selection = 0;
            for (SkinConfig.Option o : property.getOption()) {
                if (o.name.equals(option.name)) {
                    if (o.value != OPTION_RANDOM_VALUE) {
                        for (int idx = 0; idx < option.option.length; idx++) {
                            if (option.option[idx] == o.value) { selection = idx; break; }
                        }
                    } else {
                        selection = items.length - 1;
                    }
                    break;
                }
            }
            combo.setSelectedIndex(selection);
            optionBoxes.put(option, combo);
            addPropertyEntry(option.name, combo, pad);
        }

        // Custom files
        for (CustomFile file : header.getCustomFiles()) {
            String name = file.path.substring(file.path.lastIndexOf('/') + 1);
            if (file.path.contains("|")) {
                int pipeIdx = file.path.lastIndexOf('|');
                if (pipeIdx + 1 < file.path.length()) {
                    name = file.path.substring(file.path.lastIndexOf('/') + 1, file.path.indexOf('|'))
                            + file.path.substring(pipeIdx + 1);
                } else {
                    name = file.path.substring(file.path.lastIndexOf('/') + 1, file.path.indexOf('|'));
                }
            }

            int slashIdx = file.path.lastIndexOf('/');
            Path dirpath = slashIdx != -1 ? Paths.get(file.path.substring(0, slashIdx)) : Paths.get(file.path);
            if (!Files.exists(dirpath)) continue;

            try (DirectoryStream<Path> paths = Files.newDirectoryStream(dirpath,
                    "{" + name.toLowerCase() + "," + name.toUpperCase() + "}")) {
                SelectBox<String> combo = new SelectBox<>(skin);
                Array<String> fileNames = new Array<>();
                for (Path p : paths) fileNames.add(p.getFileName().toString());
                fileNames.add("Random");
                combo.setItems(fileNames);

                String selectedPath = null;
                for (SkinConfig.FilePath f : property.getFile()) {
                    if (f.name.equals(file.name)) { selectedPath = f.path; break; }
                }
                if (selectedPath != null) combo.setSelected(selectedPath);
                fileBoxes.put(file, combo);
                addPropertyEntry(file.name, combo, pad);
            } catch (IOException ignored) {}
        }

        // Custom offsets
        float screenW = Gdx.graphics.getWidth();
        float spinnerBtnW = screenW * 0.015f;
        float spinnerTextW = screenW * 0.03f;
        float spinnerH = Gdx.graphics.getHeight() * 0.025f;

        for (CustomOffset offset : header.getCustomOffsets()) {
            Table controls = new Table(skin);
            controls.defaults().left().pad(pad * 0.5f);
            SpinnerWidget[] spinners = new SpinnerWidget[6];
            String[] labels = {"x", "y", "w", "h", "r", "a"};
            int[] defaults = {0, 0, 0, 0, 0, 0};
            for (SkinConfig.Offset o : property.getOffset()) {
                if (o.name.equals(offset.name)) {
                    defaults = new int[]{o.x, o.y, o.w, o.h, o.r, o.a};
                    break;
                }
            }
            for (int i = 0; i < 6; i++) {
                spinners[i] = new SpinnerWidget(skin, -9999, 9999, defaults[i], 1);
                spinners[i].setCompact(spinnerBtnW, spinnerTextW, spinnerH);
                controls.add(new Label(labels[i], skin)).padRight(pad * 0.5f);
                controls.add(spinners[i]);
                if (i == 2) {
                    controls.row();
                }
            }
            offsetBoxes.put(offset, spinners);
            skinOptionsContainer.add(new Label(offset.name, skin)).left().pad(pad);
            skinOptionsContainer.add(controls).left().growX().pad(pad);
            skinOptionsContainer.row();
        }
    }

    private void addPropertyEntry(String label, Actor field, float pad) {
        skinOptionsContainer.add(new Label(label, skin)).left().pad(pad);
        skinOptionsContainer.add(field).growX().left().pad(pad);
        skinOptionsContainer.row();
    }

    private SkinConfig.Property getProperty() {
        if (selectedHeader == null) return new SkinConfig.Property();

        SkinConfig.Property property = new SkinConfig.Property();

        java.util.List<SkinConfig.Option> options = new java.util.ArrayList<>();
        for (var entry : optionBoxes.entrySet()) {
            CustomOption option = entry.getKey();
            SelectBox<String> combo = entry.getValue();
            int index = combo.getSelectedIndex();
            SkinConfig.Option o = new SkinConfig.Option();
            o.name = option.name;
            o.value = (index < option.option.length) ? option.option[index] : OPTION_RANDOM_VALUE;
            options.add(o);
        }
        property.setOption(options.toArray(new SkinConfig.Option[0]));

        java.util.List<SkinConfig.FilePath> files = new java.util.ArrayList<>();
        for (var entry : fileBoxes.entrySet()) {
            SkinConfig.FilePath f = new SkinConfig.FilePath();
            f.name = entry.getKey().name;
            f.path = entry.getValue().getSelected();
            files.add(f);
        }
        property.setFile(files.toArray(new SkinConfig.FilePath[0]));

        java.util.List<SkinConfig.Offset> offsets = new java.util.ArrayList<>();
        for (var entry : offsetBoxes.entrySet()) {
            SpinnerWidget[] sw = entry.getValue();
            SkinConfig.Offset o = new SkinConfig.Offset();
            o.name = entry.getKey().name;
            o.x = sw[0].getIntValue();
            o.y = sw[1].getIntValue();
            o.w = sw[2].getIntValue();
            o.h = sw[3].getIntValue();
            o.r = sw[4].getIntValue();
            o.a = sw[5].getIntValue();
            offsets.add(o);
        }
        property.setOffset(offsets.toArray(new SkinConfig.Offset[0]));

        return property;
    }

    private void commitSkinType() {
        if (player == null) return;
        if (selectedHeader != null) {
            SkinConfig sc = new SkinConfig(selectedHeader.getPath().toString());
            sc.setProperties(getProperty());
            player.getSkin()[selectedHeader.getSkinType().getId()] = sc;
        } else if (currentSkinType != null) {
            player.getSkin()[currentSkinType.getId()] = null;
        }
    }

    private void commitSkinHeader() {
        if (selectedHeader == null || player == null) return;
        SkinConfig.Property property = getProperty();
        SkinConfig[] history = player.getSkinHistory();
        int index = -1;
        for (int i = 0; i < history.length; i++) {
            if (history[i].getPath().equals(selectedHeader.getPath().toString())) {
                index = i;
                break;
            }
        }
        SkinConfig sc = new SkinConfig();
        sc.setPath(selectedHeader.getPath().toString());
        sc.setProperties(property);
        if (index >= 0) {
            history[index] = sc;
        } else {
            SkinConfig[] newHistory = java.util.Arrays.copyOf(history, history.length + 1);
            newHistory[newHistory.length - 1] = sc;
            player.setSkinHistory(newHistory);
        }
    }

    @Override
    public void update(Config config) {
        scanSkins(config);
    }

    @Override
    public void updatePlayer(PlayerConfig player) {
        this.player = player;
        skintypeSelector.setSelected(SkinType.PLAY_7KEYS);
        updateSkinType(SkinType.PLAY_7KEYS);
    }

    @Override
    public void commitPlayer(PlayerConfig player) {
        commitSkinType();
        commitSkinHeader();
    }

    /** Wrapper for SelectBox display of SkinHeaders. */
    private static class SkinHeaderItem {
        final SkinHeader header;
        SkinHeaderItem(SkinHeader header) { this.header = header; }
        @Override
        public String toString() {
            return header != null ? header.getName() : "(none)";
        }
    }
}
