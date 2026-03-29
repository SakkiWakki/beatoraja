package bms.player.beatoraja.launcher;

import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.AudioConfig.DriverType;
import bms.player.beatoraja.AudioConfig.FrequencyType;
import bms.player.beatoraja.Config;
import bms.player.beatoraja.Config.DisplayMode;
import bms.player.beatoraja.Config.SongPreview;
import bms.player.beatoraja.PlayerConfig;
import bms.player.beatoraja.Resolution;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip serialization tests for Config and PlayerConfig.
 *
 * These tests verify that every launcher-visible field survives a
 * serialize → deserialize cycle via libGDX Json, matching the exact
 * read/write path used in production (Config.read / Config.write).
 */
class ConfigRoundTripTest {

    /** Serialize an object the same way production does. */
    private static String toJson(Object obj) {
        Json json = new Json();
        json.setUsePrototypes(false);
        json.setOutputType(OutputType.json);
        return json.prettyPrint(obj);
    }

    /** Deserialize using the same settings as production. */
    private static <T> T fromJson(Class<T> type, String data) {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        return json.fromJson(type, data);
    }

    // ---- Config round-trip ----

    @Test
    void configDisplaySettings_roundTrip() {
        Config c = new Config();
        c.setDisplaymode(DisplayMode.FULLSCREEN);
        c.setVsync(true);
        c.setResolution(Resolution.FULLHD);
        c.setMaxFramePerSecond(120);

        Config c2 = fromJson(Config.class, toJson(c));
        assertEquals(DisplayMode.FULLSCREEN, c2.getDisplaymode());
        assertTrue(c2.isVsync());
        assertEquals(Resolution.FULLHD, c2.getResolution());
        assertEquals(120, c2.getMaxFramePerSecond());
    }

    @Test
    void configBgaSettings_roundTrip() {
        Config c = new Config();
        c.setBga(Config.BGA_OFF);
        c.setBgaExpand(Config.BGAEXPAND_FULL);

        Config c2 = fromJson(Config.class, toJson(c));
        assertEquals(Config.BGA_OFF, c2.getBga());
        assertEquals(Config.BGAEXPAND_FULL, c2.getBgaExpand());
    }

    @Test
    void configPathSettings_roundTrip() {
        Config c = new Config();
        c.setPlayername("testplayer");
        c.setBmsroot(new String[]{"/music/bms", "/data/songs"});
        c.setTableURL(new String[]{"https://example.com/table.html"});
        c.setSongpath("custom_songdata.db");
        c.setSonginfopath("custom_songinfo.db");
        c.setPlayerpath("custom_player");
        c.setSkinpath("custom_skin");
        c.setBgmpath("custom_bgm");
        c.setSoundpath("custom_sound");
        c.setSystemfontpath("custom_font.ttf");
        c.setMessagefontpath("custom_msg_font.ttf");

        Config c2 = fromJson(Config.class, toJson(c));
        assertEquals("testplayer", c2.getPlayername());
        assertArrayEquals(new String[]{"/music/bms", "/data/songs"}, c2.getBmsroot());
        assertArrayEquals(new String[]{"https://example.com/table.html"}, c2.getTableURL());
        assertEquals("custom_songdata.db", c2.getSongpath());
        assertEquals("custom_songinfo.db", c2.getSonginfopath());
        assertEquals("custom_player", c2.getPlayerpath());
        assertEquals("custom_skin", c2.getSkinpath());
        assertEquals("custom_bgm", c2.getBgmpath());
        assertEquals("custom_sound", c2.getSoundpath());
        assertEquals("custom_font.ttf", c2.getSystemfontpath());
        assertEquals("custom_msg_font.ttf", c2.getMessagefontpath());
    }

    @Test
    void configMiscSettings_roundTrip() {
        Config c = new Config();
        c.setFolderlamp(false);
        c.setShowNoSongExistingBar(false);
        c.setAnalogScroll(false);
        c.setSongPreview(SongPreview.ONCE);
        c.setCacheSkinImage(true);
        c.setUseSongInfo(false);
        c.setUseDiscordRPC(true);

        Config c2 = fromJson(Config.class, toJson(c));
        assertFalse(c2.isFolderlamp());
        assertFalse(c2.isShowNoSongExistingBar());
        assertFalse(c2.isAnalogScroll());
        assertEquals(SongPreview.ONCE, c2.getSongPreview());
        assertTrue(c2.isCacheSkinImage());
        assertFalse(c2.isUseSongInfo());
        assertTrue(c2.isUseDiscordRPC());
    }

    @Test
    void configAudioSettings_roundTrip() {
        AudioConfig audio = new AudioConfig();
        audio.setDriver(DriverType.PortAudio);
        audio.setDriverName("hw:0,0");
        audio.setDeviceBufferSize(512);
        audio.setDeviceSimultaneousSources(128);
        audio.setSampleRate(48000);
        audio.setFreqOption(FrequencyType.UNPROCESSED);
        audio.setFastForward(FrequencyType.UNPROCESSED);
        audio.setSystemvolume(0.8f);
        audio.setKeyvolume(0.6f);
        audio.setBgvolume(0.4f);
        audio.setLoopResultSound(true);
        audio.setLoopCourseResultSound(true);

        Config c = new Config();
        c.setAudioConfig(audio);

        Config c2 = fromJson(Config.class, toJson(c));
        AudioConfig a2 = c2.getAudioConfig();
        assertNotNull(a2);
        assertEquals(DriverType.PortAudio, a2.getDriver());
        assertEquals("hw:0,0", a2.getDriverName());
        assertEquals(512, a2.getDeviceBufferSize());
        assertEquals(128, a2.getDeviceSimultaneousSources());
        assertEquals(48000, a2.getSampleRate());
        assertEquals(FrequencyType.UNPROCESSED, a2.getFreqOption());
        assertEquals(FrequencyType.UNPROCESSED, a2.getFastForward());
        assertEquals(0.8f, a2.getSystemvolume(), 0.001f);
        assertEquals(0.6f, a2.getKeyvolume(), 0.001f);
        assertEquals(0.4f, a2.getBgvolume(), 0.001f);
        assertTrue(a2.isLoopResultSound());
        assertTrue(a2.isLoopCourseResultSound());
    }

    @Test
    void configValidate_clampsValues() {
        Config c = new Config();
        c.setMaxFramePerSecond(-10);
        c.validate();
        assertTrue(c.getMaxFramePerSecond() >= 0, "FPS should be non-negative after validate");
    }

    @Test
    void configFileRoundTrip(@TempDir Path tempDir) throws Exception {
        // Write config to a temp file and read it back, simulating production I/O
        Config c = new Config();
        c.setPlayername("filetest");
        c.setDisplaymode(DisplayMode.BORDERLESS);
        c.setVsync(true);
        c.setBga(Config.BGA_AUTO);

        Path file = tempDir.resolve("config_sys.json");
        Json json = new Json();
        json.setUsePrototypes(false);
        json.setOutputType(OutputType.json);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
            writer.write(json.prettyPrint(c));
        }

        Json json2 = new Json();
        json2.setIgnoreUnknownFields(true);
        Config c2;
        try (Reader reader = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            c2 = json2.fromJson(Config.class, reader);
        }
        c2.validate();

        assertEquals("filetest", c2.getPlayername());
        assertEquals(DisplayMode.BORDERLESS, c2.getDisplaymode());
        assertTrue(c2.isVsync());
        assertEquals(Config.BGA_AUTO, c2.getBga());
    }

    // ---- PlayerConfig round-trip ----

    @Test
    void playerConfigBasicFields_roundTrip() {
        PlayerConfig p = new PlayerConfig();
        p.setName("TestPlayer");
        p.setGauge(3);
        p.setRandom(5);
        p.setRandom2(7);
        p.setDoubleoption(2);
        p.setJudgetiming(42);
        p.setLnmode(1);
        p.setTargetid("RATE_AAA");
        p.setMisslayerDuration(1500);

        PlayerConfig p2 = fromJson(PlayerConfig.class, toJson(p));
        assertEquals("TestPlayer", p2.getName());
        assertEquals(3, p2.getGauge());
        assertEquals(5, p2.getRandom());
        assertEquals(7, p2.getRandom2());
        assertEquals(2, p2.getDoubleoption());
        assertEquals(42, p2.getJudgetiming());
        assertEquals(1, p2.getLnmode());
        assertEquals("RATE_AAA", p2.getTargetid());
        assertEquals(1500, p2.getMisslayerDuration());
    }

    @Test
    void playerConfigJudgeWindow_roundTrip() {
        PlayerConfig p = new PlayerConfig();
        p.setCustomJudge(true);
        p.setKeyJudgeWindowRatePerfectGreat(200);
        p.setKeyJudgeWindowRateGreat(300);
        p.setKeyJudgeWindowRateGood(50);
        p.setScratchJudgeWindowRatePerfectGreat(150);
        p.setScratchJudgeWindowRateGreat(250);
        p.setScratchJudgeWindowRateGood(75);

        PlayerConfig p2 = fromJson(PlayerConfig.class, toJson(p));
        assertTrue(p2.isCustomJudge());
        assertEquals(200, p2.getKeyJudgeWindowRatePerfectGreat());
        assertEquals(300, p2.getKeyJudgeWindowRateGreat());
        assertEquals(50, p2.getKeyJudgeWindowRateGood());
        assertEquals(150, p2.getScratchJudgeWindowRatePerfectGreat());
        assertEquals(250, p2.getScratchJudgeWindowRateGreat());
        assertEquals(75, p2.getScratchJudgeWindowRateGood());
    }

    @Test
    void playerConfigBooleanFlags_roundTrip() {
        PlayerConfig p = new PlayerConfig();
        p.setBpmguide(true);
        p.setNotesDisplayTimingAutoAdjust(true);
        p.setShowhiddennote(true);
        p.setShowpastnote(true);
        p.setChartPreview(false);
        p.setGuideSE(true);
        p.setWindowHold(true);
        p.setRandomSelect(true);
        p.setShowjudgearea(true);
        p.setMarkprocessednote(true);

        PlayerConfig p2 = fromJson(PlayerConfig.class, toJson(p));
        assertTrue(p2.isBpmguide());
        assertTrue(p2.isNotesDisplayTimingAutoAdjust());
        assertTrue(p2.isShowhiddennote());
        assertTrue(p2.isShowpastnote());
        assertFalse(p2.isChartPreview());
        assertTrue(p2.isGuideSE());
        assertTrue(p2.isWindowHold());
        assertTrue(p2.isRandomSelect());
        assertTrue(p2.isShowjudgearea());
        assertTrue(p2.isMarkprocessednote());
    }

    @Test
    void playerConfigAssistOptions_roundTrip() {
        PlayerConfig p = new PlayerConfig();
        p.setGaugeAutoShift(PlayerConfig.GAUGEAUTOSHIFT_BESTCLEAR);
        p.setHranThresholdBPM(200);
        p.setSevenToNinePattern(3);
        p.setSevenToNineType(2);
        p.setExitPressDuration(2000);
        p.setExtranoteDepth(50);

        PlayerConfig p2 = fromJson(PlayerConfig.class, toJson(p));
        assertEquals(PlayerConfig.GAUGEAUTOSHIFT_BESTCLEAR, p2.getGaugeAutoShift());
        assertEquals(200, p2.getHranThresholdBPM());
        assertEquals(3, p2.getSevenToNinePattern());
        assertEquals(2, p2.getSevenToNineType());
        assertEquals(2000, p2.getExitPressDuration());
        assertEquals(50, p2.getExtranoteDepth());
    }

    @Test
    void playerConfigValidate_clampsOutOfRange() {
        PlayerConfig p = new PlayerConfig();
        p.setGauge(99);
        p.setRandom(99);
        p.setJudgetiming(9999);
        p.setMisslayerDuration(99999);
        p.setLnmode(99);
        p.setKeyJudgeWindowRatePerfectGreat(1);
        p.validate();

        assertTrue(p.getGauge() <= 5);
        assertTrue(p.getRandom() <= 9);
        assertTrue(p.getJudgetiming() <= PlayerConfig.JUDGETIMING_MAX);
        assertTrue(p.getMisslayerDuration() <= 5000);
        assertTrue(p.getLnmode() <= 2);
        assertTrue(p.getKeyJudgeWindowRatePerfectGreat() >= 25);
    }

    @Test
    void playerConfigValidate_reconstructsNullModes() {
        // Simulate deserialization that leaves modes null
        String json = "{\"name\":\"TestNull\",\"gauge\":2}";
        PlayerConfig p = fromJson(PlayerConfig.class, json);
        p.validate();

        assertNotNull(p.getPlayConfig(bms.model.Mode.BEAT_7K));
        assertNotNull(p.getPlayConfig(bms.model.Mode.BEAT_5K));
        assertNotNull(p.getPlayConfig(bms.model.Mode.POPN_9K));
    }

    @Test
    void playerConfigFileRoundTrip(@TempDir Path tempDir) throws Exception {
        Path playerDir = tempDir.resolve("player/testplayer");
        Files.createDirectories(playerDir);

        PlayerConfig p = new PlayerConfig();
        p.setId("testplayer");
        p.setName("Round Trip Player");
        p.setGauge(4);
        p.setRandom(3);
        p.setJudgetiming(-50);
        p.setBpmguide(true);
        p.setMisslayerDuration(750);

        // Write like production
        Path file = playerDir.resolve("config_player.json");
        Json jsonW = new Json();
        jsonW.setOutputType(OutputType.json);
        jsonW.setUsePrototypes(false);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
            writer.write(jsonW.prettyPrint(p));
        }

        // Read like production
        Json jsonR = new Json();
        jsonR.setIgnoreUnknownFields(true);
        PlayerConfig p2;
        try (Reader reader = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            p2 = jsonR.fromJson(PlayerConfig.class, reader);
        }
        p2.setId("testplayer");
        p2.validate();

        assertEquals("Round Trip Player", p2.getName());
        assertEquals(4, p2.getGauge());
        assertEquals(3, p2.getRandom());
        assertEquals(-50, p2.getJudgetiming());
        assertTrue(p2.isBpmguide());
        assertEquals(750, p2.getMisslayerDuration());
    }

    @Test
    void audioConfigValidate_clampsValues() {
        AudioConfig a = new AudioConfig();
        a.setDeviceBufferSize(1);     // min is 4
        a.setSystemvolume(2.0f);      // max is 1.0
        a.setKeyvolume(-1.0f);        // min is 0
        a.validate();

        assertTrue(a.getDeviceBufferSize() >= 4);
        assertTrue(a.getSystemvolume() <= 1.0f);
        assertTrue(a.getKeyvolume() >= 0f);
    }

    @Test
    void configDefaultValues_areValid() {
        Config c = new Config();
        assertTrue(c.validate(), "Default Config should pass validation");
    }

    @Test
    void playerConfigDefaultValues_afterValidate() {
        PlayerConfig p = new PlayerConfig();
        p.validate();
        assertEquals("NO NAME", p.getName());
        assertEquals(0, p.getGauge());
        assertEquals(0, p.getRandom());
        assertEquals("MAX", p.getTargetid());
    }
}
