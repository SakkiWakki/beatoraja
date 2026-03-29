package bms.player.beatoraja.launcher.gdx;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import bms.player.beatoraja.*;

/**
 * GDX-based launcher replacing the JavaFX PlayConfigurationView.
 * Runs as a standalone Lwjgl3Application before the game starts.
 */
public class LauncherApp extends ApplicationAdapter {

    private Stage stage;
    private Skin skin;
    private Config config;
    private PlayerConfig player;
    private ResourceBundle bundle;

    private Runnable onStart;
    private Runnable onExit;

    private SelectBox<String> playerSelect;
    private TextField playerNameField;


    private boolean songUpdated;
    private boolean importing;
    private ProgressBar importProgressBar;
    private Label importStatusLabel;
    private TextButton startBtn;
    private TextButton updateDbBtn;

    public boolean isSongUpdated() { return songUpdated; }

    private final java.util.List<LauncherTab> tabs = new java.util.ArrayList<>();

    public LauncherApp(Config config, PlayerConfig player, Runnable onStart, Runnable onExit) {
        this.config = config;
        this.player = player;
        this.onStart = onStart;
        this.onExit = onExit;
    }

    @Override
    public void create() {
        bundle = ResourceBundle.getBundle("resources.UIResources");
        launcherCharacterSet = buildLauncherCharacterSet();
        stage = new Stage(new ScreenViewport());
        skin = buildSkin();
        Gdx.input.setInputProcessor(stage);

        // Auto-focus ScrollPanes on mouse hover so scrolling works without clicking first
        stage.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                Actor hit = stage.hit(x, y, true);
                while (hit != null) {
                    if (hit instanceof ScrollPane) {
                        stage.setScrollFocus(hit);
                        break;
                    }
                    hit = hit.getParent();
                }
                return false;
            }
        });

        buildUI();
    }

    private Skin buildSkin() {
        Skin skin = new Skin();

        // Generate fonts at native pixel sizes proportional to screen height
        int screenH = Gdx.graphics.getHeight();
        int sizeDefault = Math.max(14, Math.round(screenH * 0.028f));
        int sizeSmall = Math.max(12, Math.round(screenH * 0.022f));
        int sizeLarge = Math.max(16, Math.round(screenH * 0.036f));
        BitmapFont font = generateFont(sizeDefault);
        BitmapFont fontSmall = generateFont(sizeSmall);
        BitmapFont fontLarge = generateFont(sizeLarge);
        skin.add("default-font", font);
        skin.add("small-font", fontSmall);
        skin.add("large-font", fontLarge);

        // Create simple colored drawables — each needs its own Pixmap
        // because Texture uploads happen immediately and reusing a single
        // Pixmap corrupts previously created textures.
        skin.add("dark-bg", makeColorTexture(0.25f, 0.25f, 0.25f, 1f));
        skin.add("mid-bg", makeColorTexture(0.35f, 0.35f, 0.35f, 1f));
        skin.add("light-bg", makeColorTexture(0.45f, 0.45f, 0.45f, 1f));
        skin.add("accent", makeColorTexture(0.2f, 0.5f, 0.8f, 1f));
        skin.add("border", makeColorTexture(0.6f, 0.6f, 0.6f, 1f));
        Pixmap pm1 = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm1.setColor(1f, 1f, 1f, 1f);
        pm1.fill();
        skin.add("white", new Texture(pm1));

        pm1.dispose();

        // Label style
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        skin.add("default", labelStyle);
        Label.LabelStyle labelSmall = new Label.LabelStyle(fontSmall, Color.LIGHT_GRAY);
        skin.add("small", labelSmall);
        Label.LabelStyle labelLarge = new Label.LabelStyle(fontLarge, Color.WHITE);
        skin.add("large", labelLarge);

        // TextButton style
        TextButton.TextButtonStyle tbStyle = new TextButton.TextButtonStyle();
        tbStyle.font = font;
        tbStyle.fontColor = Color.WHITE;
        tbStyle.up = newDrawable(skin, "mid-bg");
        tbStyle.over = newDrawable(skin, "light-bg");
        tbStyle.down = newDrawable(skin, "accent");
        skin.add("default", tbStyle);

        TextButton.TextButtonStyle linkStyle = new TextButton.TextButtonStyle();
        linkStyle.font = font;
        linkStyle.fontColor = new Color(0.4f, 0.7f, 1f, 1f);
        linkStyle.overFontColor = new Color(0.6f, 0.85f, 1f, 1f);
        skin.add("link", linkStyle);

        // TextField style
        TextField.TextFieldStyle tfStyle = new TextField.TextFieldStyle();
        tfStyle.font = font;
        tfStyle.fontColor = Color.WHITE;
        tfStyle.background = newDrawable(skin, "dark-bg", 0, (int)(font.getLineHeight() + 8));
        tfStyle.cursor = newDrawable(skin, "white", 1, (int)font.getLineHeight());
        tfStyle.selection = newDrawable(skin, "accent");
        skin.add("default", tfStyle);

        // CheckBox style
        CheckBox.CheckBoxStyle cbStyle = new CheckBox.CheckBoxStyle();
        cbStyle.font = font;
        cbStyle.fontColor = Color.WHITE;
        cbStyle.checkboxOff = newDrawable(skin, "mid-bg", 18, 18);
        cbStyle.checkboxOn = newDrawable(skin, "accent", 18, 18);
        skin.add("default", cbStyle);

        // SelectBox style
        SelectBox.SelectBoxStyle sbStyle = new SelectBox.SelectBoxStyle();
        sbStyle.font = font;
        sbStyle.fontColor = Color.WHITE;
        sbStyle.background = newDrawable(skin, "mid-bg");
        sbStyle.listStyle = new List.ListStyle();
        sbStyle.listStyle.font = font;
        sbStyle.listStyle.fontColorSelected = Color.WHITE;
        sbStyle.listStyle.fontColorUnselected = Color.LIGHT_GRAY;
        sbStyle.listStyle.selection = newDrawable(skin, "accent");
        sbStyle.listStyle.background = newDrawable(skin, "dark-bg");
        sbStyle.scrollStyle = new ScrollPane.ScrollPaneStyle();
        skin.add("default", sbStyle);

        // List style
        skin.add("default", sbStyle.listStyle);

        // ScrollPane style
        ScrollPane.ScrollPaneStyle spStyle = new ScrollPane.ScrollPaneStyle();
        spStyle.background = newDrawable(skin, "dark-bg");
        skin.add("default", spStyle);

        // Slider style
        Slider.SliderStyle slStyle = new Slider.SliderStyle();
        slStyle.background = newDrawable(skin, "mid-bg", 0, 6);
        slStyle.knob = newDrawable(skin, "accent", 14, 14);
        skin.add("default-horizontal", slStyle);

        // ProgressBar style
        ProgressBar.ProgressBarStyle pbStyle = new ProgressBar.ProgressBarStyle();
        pbStyle.background = newDrawable(skin, "dark-bg", 0, 8);
        pbStyle.knobBefore = newDrawable(skin, "accent", 0, 8);
        skin.add("default-horizontal", pbStyle);

        // Window style (for dialogs)
        Window.WindowStyle wStyle = new Window.WindowStyle();
        wStyle.titleFont = fontLarge;
        wStyle.titleFontColor = Color.WHITE;
        wStyle.background = newDrawable(skin, "dark-bg");
        skin.add("default", wStyle);

        return skin;
    }

    private static TextureRegionDrawable newDrawable(Skin skin, String texName) {
        return new TextureRegionDrawable(new TextureRegion(skin.get(texName, Texture.class)));
    }

    private static TextureRegionDrawable newDrawable(Skin skin, String texName, int minW, int minH) {
        TextureRegionDrawable d = new TextureRegionDrawable(new TextureRegion(skin.get(texName, Texture.class)));
        d.setMinWidth(minW);
        d.setMinHeight(minH);
        return d;
    }

    private static Texture makeColorTexture(float r, float g, float b, float a) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(r, g, b, a);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private BitmapFont generateFont(int size) {
        String resolvedFontPath = resolveFontPath(config.getSystemfontpath());
        com.badlogic.gdx.files.FileHandle fontFile = Gdx.files.absolute(resolvedFontPath);
        FreeTypeFontGenerator gen = fontFile.extension().equalsIgnoreCase("ttc")
                ? new FreeTypeFontGenerator(fontFile, 0)
                : new FreeTypeFontGenerator(fontFile);
        try {
            FreeTypeFontParameter param = new FreeTypeFontParameter();
            param.size = size;
            param.color = Color.WHITE;
            param.incremental = false;
            param.characters = launcherCharacterSet;
            param.hinting = FreeTypeFontGenerator.Hinting.AutoFull;
            param.minFilter = Texture.TextureFilter.Linear;
            param.magFilter = Texture.TextureFilter.Linear;
            param.genMipMaps = false;
            return gen.generateFont(param);
        } finally {
            gen.dispose();
        }
    }

    /** Pre-computed character set for non-incremental font generation. */
    private String launcherCharacterSet;

    /**
     * Collect every character the launcher UI may need to display.
     * Reads all locale resource bundles so every translation is covered,
     * plus ASCII printable range and common CJK/Hangul for user input.
     */
    private String buildLauncherCharacterSet() {
        java.util.LinkedHashSet<Character> chars = new java.util.LinkedHashSet<>();
        // ASCII printable
        for (char c = 0x20; c < 0x7F; c++) chars.add(c);
        // All resource bundles (covers every supported locale)
        String[] locales = {"", "ja_JP", "fr_FR", "es_ES", "pt_PT", "nb_NO"};
        for (String loc : locales) {
            try {
                java.util.Locale locale = loc.isEmpty() ? java.util.Locale.ROOT
                        : java.util.Locale.forLanguageTag(loc.replace('_', '-'));
                ResourceBundle rb = ResourceBundle.getBundle("resources.UIResources", locale);
                for (String key : rb.keySet()) {
                    for (char c : rb.getString(key).toCharArray()) chars.add(c);
                }
            } catch (Exception ignored) {}
        }
        // Common CJK punctuation + fullwidth (U+3000-303F, FF01-FF5E)
        for (char c = 0x3000; c <= 0x303F; c++) chars.add(c);
        for (char c = 0xFF01; c <= 0xFF5E; c++) chars.add(c);
        // Hiragana + Katakana (U+3040-30FF)
        for (char c = 0x3040; c <= 0x30FF; c++) chars.add(c);
        // Digits and symbols that may appear in paths/configs
        for (char c : ":/\\.@#%&(){}[]<>|~`^".toCharArray()) chars.add(c);
        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) sb.append(c);
        return sb.toString();
    }

    private String resolveFontPath(String configuredFontPath) {
        Path configured = Paths.get(configuredFontPath);
        if (Files.isRegularFile(configured)) {
            return configured.toAbsolutePath().toString();
        }

        String[] fontQueries = {
                "Noto Sans CJK JP",
                "Arial",
                "sans-serif"
        };
        for (String query : fontQueries) {
            String matched = resolveFontConfigMatch(query);
            if (matched != null) {
                Logger.getGlobal().info("Using fallback launcher font: " + matched);
                return matched;
            }
        }

        return configuredFontPath;
    }

    private String resolveFontConfigMatch(String family) {
        try {
            Process process = new ProcessBuilder("fc-match", "-f", "%{file}\\n", family).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String match = reader.readLine();
                process.waitFor();
                if (match != null && !match.isBlank() && Files.isRegularFile(Paths.get(match))) {
                    return match;
                }
            }
        } catch (Exception e) {
            Logger.getGlobal().fine("fc-match failed for " + family + ": " + e.getMessage());
        }
        return null;
    }

    private void buildUI() {
        // Create all tabs
        VideoTab videoTab = new VideoTab();
        AudioTab audioTab = new AudioTab();
        InputTab inputTab = new InputTab();
        ResourceTab resourceTab = new ResourceTab();
        resourceTab.setOnPathAdded(path -> startImport(path, false));
        MusicSelectTab musicSelectTab = new MusicSelectTab();
        PlayOptionsTab playOptionsTab = new PlayOptionsTab();
        SkinTab skinTab = new SkinTab();
        OtherTab otherTab = new OtherTab();
        IRTab irTab = new IRTab();
        TableEditorTab tableEditorTab = new TableEditorTab();
        StreamTab streamTab = new StreamTab();

        tabs.add(videoTab);
        tabs.add(audioTab);
        tabs.add(inputTab);
        tabs.add(resourceTab);
        tabs.add(musicSelectTab);
        tabs.add(playOptionsTab);
        tabs.add(skinTab);
        tabs.add(otherTab);
        tabs.add(irTab);
        tabs.add(tableEditorTab);
        tabs.add(streamTab);

        // Build tab content
        String[] tabNames = {
                bundle.getString("TAB_VIDEO"), bundle.getString("TAB_AUDIO"),
                bundle.getString("TAB_INPUT"), bundle.getString("Resource"),
                bundle.getString("TAB_MUSICSELECT"), bundle.getString("Play_Option"),
                "Skin", bundle.getString("Other"), "IR", "Table", "Stream"
        };

        // Root layout
        Table root = new Table(skin);
        root.setFillParent(true);
        root.top().left();
        root.pad(8);

        // Title / version
        root.add(new Label(MainController.getVersion() + " configuration", skin, "large"))
                .left().colspan(2);
        root.row();

        // Version check
        Label versionLabel = new Label("", skin, "small");
        root.add(versionLabel).left().colspan(2).padBottom(4);
        root.row();
        new Thread(() -> {
            String message = MainLoader.getVersionChecker().getMessage();
            Gdx.app.postRunnable(() -> versionLabel.setText(message));
        }).start();

        // Player selector row
        Table playerRow = new Table(skin);
        playerRow.add(new Label(bundle.getString("PLAYER_ID"), skin)).padRight(8);

        playerSelect = new SelectBox<>(skin);
        String[] playerIds = PlayerConfig.readAllPlayerID(config.getPlayerpath());
        if (playerIds.length > 0) {
            playerSelect.setItems(playerIds);
            if (config.getPlayername() != null) {
                playerSelect.setSelected(config.getPlayername());
            }
        }
        playerSelect.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                changePlayer();
            }
        });
        playerRow.add(playerSelect).width(150).padRight(8);

        playerNameField = new TextField(player.getName(), skin);
        playerRow.add(playerNameField).width(200).padRight(8);

        TextButton addPlayerBtn = new TextButton("+", skin);
        addPlayerBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                addPlayer();
            }
        });
        playerRow.add(addPlayerBtn).width(30);

        root.add(playerRow).left().colspan(2).padBottom(8);
        root.row();

        // Tab buttons row
        Table tabButtonRow = new Table(skin);
        Table[] tabContents = new Table[tabs.size()];
        Stack tabStack = new Stack();

        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            Actor content = tabs.get(i).build(skin, bundle);
            Table wrapper = new Table(skin);
            wrapper.add(content).grow();
            wrapper.setVisible(i == 0);
            tabContents[i] = wrapper;
            tabStack.add(wrapper);

            TextButton tabBtn = new TextButton(tabNames[i], skin);
            tabBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    for (int j = 0; j < tabContents.length; j++) {
                        tabContents[j].setVisible(j == idx);
                    }
                    // Set scroll focus to the tab's ScrollPane so scrolling works immediately
                    Actor child = tabContents[idx].getChildren().first();
                    if (child instanceof ScrollPane) {
                        stage.setScrollFocus(child);
                    }
                }
            });
            tabButtonRow.add(tabBtn).uniformX().fillX().expandX();
        }

        root.add(tabButtonRow).growX().colspan(2).padBottom(4);
        root.row();
        Container<Stack> tabContainer = new Container<>(tabStack);
        tabContainer.fill();
        tabContainer.clip(true);
        root.add(tabContainer).grow().colspan(2);
        root.row();

        // Import progress panel
        Table progressPanel = new Table(skin);
        importStatusLabel = new Label("", skin, "small");
        importProgressBar = new ProgressBar(0, 1, 0.01f, false, skin);
        importProgressBar.setVisible(false);
        progressPanel.add(importStatusLabel).left().growX().padRight(8);
        progressPanel.add(importProgressBar).width(300).height(16);
        root.add(progressPanel).growX().colspan(2).padTop(4).padBottom(4);
        root.row();

        // Bottom control panel
        Table controlPanel = new Table(skin);

        updateDbBtn = new TextButton(bundle.getString("UPDATE_DATABASE"), skin);
        updateDbBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (importing) return;
                startImport(null, false);
            }
        });

        startBtn = new TextButton(bundle.getString("START"), skin);
        startBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (importing) return;
                commitAll();
                saveConfig();
                if (onStart != null) onStart.run();
            }
        });

        TextButton exitBtn = new TextButton(bundle.getString("EXIT"), skin);
        exitBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                commitAll();
                saveConfig();
                if (onExit != null) onExit.run();
                else Gdx.app.exit();
            }
        });

        TextButton rebuildDbBtn = new TextButton(bundle.getString("REBUILD_DATABASE"), skin);
        rebuildDbBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (importing) return;
                startImport(null, true);
            }
        });

        controlPanel.add(updateDbBtn).uniformX().fillX().expandX().padRight(10);
        controlPanel.add(rebuildDbBtn).uniformX().fillX().expandX().padRight(10);
        controlPanel.add(startBtn).uniformX().fillX().expandX().padRight(10);
        controlPanel.add(exitBtn).uniformX().fillX().expandX();

        root.add(controlPanel).growX().colspan(2).padTop(8);

        stage.addActor(root);

        // No automatic window resize — the initial 800x600 is conservative
        // and the window is resizable by the user. On Wayland, GLFW monitor
        // APIs return physical pixels but window APIs use logical coordinates
        // (glfw/glfw#2527), making programmatic resizing unreliable.

        // Initial data load
        updateAllTabs();

        // Set scroll focus on the first tab so scrolling works immediately
        Actor firstChild = tabContents[0].getChildren().first();
        if (firstChild instanceof ScrollPane) {
            stage.setScrollFocus(firstChild);
        }
    }

    private void updateAllTabs() {
        for (LauncherTab tab : tabs) {
            tab.update(config);
            tab.updatePlayer(player);
        }
    }

    private void commitAll() {
        if (playerNameField.getText().length() > 0) {
            player.setName(playerNameField.getText());
        }
        config.setPlayername(playerSelect.getSelected());
        for (LauncherTab tab : tabs) {
            tab.commit(config);
            tab.commitPlayer(player);
        }
    }

    private void changePlayer() {
        commitAll();
        player = PlayerConfig.readPlayerConfig(config.getPlayerpath(), playerSelect.getSelected());
        playerNameField.setText(player.getName());
        for (LauncherTab tab : tabs) {
            tab.updatePlayer(player);
        }
    }

    private void addPlayer() {
        String[] ids = PlayerConfig.readAllPlayerID(config.getPlayerpath());
        for (int i = 1; i < 1000; i++) {
            String playerid = "player" + i;
            boolean unique = true;
            for (String id : ids) {
                if (playerid.equals(id)) { unique = false; break; }
            }
            if (unique) {
                PlayerConfig.create(config.getPlayerpath(), playerid);
                String[] newIds = PlayerConfig.readAllPlayerID(config.getPlayerpath());
                playerSelect.setItems(newIds);
                playerSelect.setSelected(playerid);
                changePlayer();
                break;
            }
        }
    }

    private void saveConfig() {
        Config.write(config);
        PlayerConfig.write(config.getPlayerpath(), player);
        Logger.getGlobal().info("Configuration saved");
    }

    /**
     * Start a song database import on a background thread with progress reporting.
     * @param path specific path to scan, or null for all configured roots
     * @param updateAll true to rebuild entire database, false for incremental
     */
    private void startImport(String path, boolean updateAll) {
        if (importing) return;
        importing = true;
        commitAll();
        saveConfig();
        updateDbBtn.setDisabled(true);
        startBtn.setDisabled(true);
        importProgressBar.setVisible(true);
        importProgressBar.setValue(0);
        importStatusLabel.setText("Starting...");

        bms.player.beatoraja.song.SongDatabaseAccessor.SongDatabaseImportListener listener = progress -> {
            Gdx.app.postRunnable(() -> {
                importStatusLabel.setText(progress.getMessage());
                if (progress.getTotalSongs() > 0) {
                    importProgressBar.setValue((float) progress.getProcessedSongs() / progress.getTotalSongs());
                } else {
                    importProgressBar.setValue(0);
                }
                switch (progress.getPhase()) {
                    case COMPLETE:
                        importFinished(true);
                        break;
                    case FAILED:
                        importFinished(false);
                        break;
                    default:
                        break;
                }
            });
        };

        new Thread(() -> {
            try {
                bms.player.beatoraja.song.SongDatabaseAccessor songdb = MainLoader.getScoreDatabaseAccessor();
                bms.player.beatoraja.song.SongInformationAccessor infodb = config.isUseSongInfo()
                        ? new bms.player.beatoraja.song.SongInformationAccessor(
                                java.nio.file.Paths.get("songinfo.db").toString())
                        : null;
                songdb.updateSongDatas(path, config.getBmsroot(), updateAll, infodb, listener);
                songUpdated = true;
            } catch (Exception e) {
                java.util.logging.Logger.getGlobal().severe("DB update failed: " + e.getMessage());
                Gdx.app.postRunnable(() -> importFinished(false));
            }
        }).start();
    }

    private void importFinished(boolean success) {
        importing = false;
        updateDbBtn.setDisabled(false);
        startBtn.setDisabled(false);
        if (success) {
            importStatusLabel.setText("Import complete.");
            importProgressBar.setValue(1);
        } else {
            importStatusLabel.setText("Import failed.");
        }
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.18f, 1f);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (skin != null) skin.dispose();
    }

    public Stage getStage() {
        return stage;
    }

    public Skin getSkin() {
        return skin;
    }

    public Config getConfig() {
        return config;
    }

    public PlayerConfig getPlayerConfig() {
        return player;
    }

    public ResourceBundle getBundle() {
        return bundle;
    }
}
