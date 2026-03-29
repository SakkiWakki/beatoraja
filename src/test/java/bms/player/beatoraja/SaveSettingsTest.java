package bms.player.beatoraja;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SaveSettingsTest {

    @TempDir Path tempDir;

    private Path sysConfigFile;
    private Path playerConfigFile;
    private Config config;
    private PlayerConfig player;

    @BeforeEach
    void setUp() throws Exception {
        sysConfigFile = tempDir.resolve("config_sys.json");
        Path playerDir = tempDir.resolve("player/testplayer");
        Files.createDirectories(playerDir);
        playerConfigFile = playerDir.resolve("config_player.json");

        config = new Config();
        config.setPlayerpath(tempDir.resolve("player").toString());
        config.setPlayername("testplayer");

        player = new PlayerConfig();
        player.setId("testplayer");
        player.setName("TestPlayer");
    }

    private void writeSystemConfig() {
        Json json = new Json();
        json.setUsePrototypes(false);
        json.setOutputType(OutputType.json);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(sysConfigFile.toFile()), StandardCharsets.UTF_8)) {
            w.write(json.prettyPrint(config));
        } catch (IOException e) {
            fail("Failed to write system config: " + e.getMessage());
        }
    }

    private void writePlayerConfig() {
        Json json = new Json();
        json.setOutputType(OutputType.json);
        json.setUsePrototypes(false);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(playerConfigFile.toFile()), StandardCharsets.UTF_8)) {
            w.write(json.prettyPrint(player));
        } catch (IOException e) {
            fail("Failed to write player config: " + e.getMessage());
        }
    }

    private Config readSystemConfig() throws Exception {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        try (Reader r = new InputStreamReader(new FileInputStream(sysConfigFile.toFile()), StandardCharsets.UTF_8)) {
            return json.fromJson(Config.class, r);
        }
    }

    private PlayerConfig readPlayerConfig() throws Exception {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        try (Reader r = new InputStreamReader(new FileInputStream(playerConfigFile.toFile()), StandardCharsets.UTF_8)) {
            return json.fromJson(PlayerConfig.class, r);
        }
    }

    // Regression: saving system config writes config_sys.json with correct data
    @Test
    void saveSystemConfig_writesCorrectData() throws Exception {
        config.setPlayername("sysTest");
        config.setVsync(true);
        writeSystemConfig();

        Config loaded = readSystemConfig();
        assertEquals("sysTest", loaded.getPlayername());
        assertTrue(loaded.isVsync());
    }

    // Regression: saving player config writes config_player.json with correct data
    @Test
    void savePlayerConfig_writesCorrectData() throws Exception {
        player.setGauge(3);
        player.setJudgetiming(42);
        writePlayerConfig();

        PlayerConfig loaded = readPlayerConfig();
        assertEquals("TestPlayer", loaded.getName());
        assertEquals(3, loaded.getGauge());
        assertEquals(42, loaded.getJudgetiming());
    }

    // Regression: saving system config does not create or modify player config
    @Test
    void saveSystemConfig_doesNotTouchPlayerConfig() throws Exception {
        player.setGauge(2);
        writePlayerConfig();
        long playerModified = playerConfigFile.toFile().lastModified();

        // small delay so timestamps differ
        Thread.sleep(50);

        config.setPlayername("changed");
        writeSystemConfig();

        assertEquals(playerModified, playerConfigFile.toFile().lastModified());
        PlayerConfig loaded = readPlayerConfig();
        assertEquals(2, loaded.getGauge());
    }

    // Regression: saving player config does not create or modify system config
    @Test
    void savePlayerConfig_doesNotTouchSystemConfig() throws Exception {
        config.setPlayername("original");
        writeSystemConfig();
        long sysModified = sysConfigFile.toFile().lastModified();

        Thread.sleep(50);

        player.setGauge(5);
        writePlayerConfig();

        assertEquals(sysModified, sysConfigFile.toFile().lastModified());
        Config loaded = readSystemConfig();
        assertEquals("original", loaded.getPlayername());
    }

    // Regression: saving both configs writes both files
    @Test
    void saveBothConfigs_writesBothFiles() throws Exception {
        config.setPlayername("bothTest");
        player.setName("BothPlayer");
        player.setGauge(4);

        writeSystemConfig();
        writePlayerConfig();

        Config loadedSys = readSystemConfig();
        PlayerConfig loadedPlayer = readPlayerConfig();
        assertEquals("bothTest", loadedSys.getPlayername());
        assertEquals("BothPlayer", loadedPlayer.getName());
        assertEquals(4, loadedPlayer.getGauge());
    }

    // Regression: multiple saves of same type overwrite correctly
    @Test
    void multipleSaves_overwriteCorrectly() throws Exception {
        player.setGauge(1);
        writePlayerConfig();
        assertEquals(1, readPlayerConfig().getGauge());

        player.setGauge(3);
        writePlayerConfig();
        assertEquals(3, readPlayerConfig().getGauge());

        player.setGauge(5);
        writePlayerConfig();
        assertEquals(5, readPlayerConfig().getGauge());
    }

    // Regression: skin config changes (part of PlayerConfig) survive save
    @Test
    void skinConfigChanges_savedInPlayerConfig() throws Exception {
        player.getSkin()[0].setPath("newskin/path.json");
        writePlayerConfig();

        PlayerConfig loaded = readPlayerConfig();
        loaded.validate();
        assertEquals("newskin/path.json", loaded.getSkin()[0].getPath());
    }

    // Regression: key config changes (part of PlayerConfig) survive save
    @Test
    void keyConfigChanges_savedInPlayerConfig() throws Exception {
        player.setMusicselectinput(2);
        writePlayerConfig();

        PlayerConfig loaded = readPlayerConfig();
        assertEquals(2, loaded.getMusicselectinput());
    }
}
