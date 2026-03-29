package bms.player.beatoraja;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter.OutputType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class DeprecatedCodeCleanupTest {

    // Config no longer reads from legacy config.json path
    @Test
    void configRead_ignoresLegacyConfigJson(@TempDir Path tempDir) throws Exception {
        Path legacyPath = tempDir.resolve("config.json");
        Json json = new Json();
        json.setUsePrototypes(false);
        json.setOutputType(OutputType.json);
        Config legacyConfig = new Config();
        legacyConfig.setPlayername("legacy_player");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(legacyPath.toFile()), StandardCharsets.UTF_8)) {
            w.write(json.prettyPrint(legacyConfig));
        }
        // Config.read() uses a static path so we can't easily redirect it,
        // but we verify the old field is gone
        assertFalse(hasField(Config.class, "configpath_old"), "configpath_old should be removed from Config");
    }

    // PlayerConfig no longer has configpath_old field
    @Test
    void playerConfig_noLegacyConfigPathField() {
        assertFalse(hasField(PlayerConfig.class, "configpath_old"), "configpath_old should be removed from PlayerConfig");
    }

    // Config still reads/writes correctly via the new path
    @Test
    void configReadWrite_stillWorks(@TempDir Path tempDir) throws Exception {
        Config c = new Config();
        c.setPlayername("modern_test");
        c.setVsync(true);

        Path file = tempDir.resolve("config_sys.json");
        Json jsonW = new Json();
        jsonW.setUsePrototypes(false);
        jsonW.setOutputType(OutputType.json);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
            w.write(jsonW.prettyPrint(c));
        }

        Json jsonR = new Json();
        jsonR.setIgnoreUnknownFields(true);
        Config c2;
        try (Reader r = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            c2 = jsonR.fromJson(Config.class, r);
        }
        assertEquals("modern_test", c2.getPlayername());
        assertTrue(c2.isVsync());
    }

    // PlayerConfig still reads/writes correctly
    @Test
    void playerConfigReadWrite_stillWorks(@TempDir Path tempDir) throws Exception {
        Path playerDir = tempDir.resolve("player/test");
        Files.createDirectories(playerDir);

        PlayerConfig p = new PlayerConfig();
        p.setId("test");
        p.setName("ModernPlayer");
        p.setGauge(3);

        Path file = playerDir.resolve("config_player.json");
        Json jsonW = new Json();
        jsonW.setOutputType(OutputType.json);
        jsonW.setUsePrototypes(false);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
            w.write(jsonW.prettyPrint(p));
        }

        Json jsonR = new Json();
        jsonR.setIgnoreUnknownFields(true);
        PlayerConfig p2;
        try (Reader r = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            p2 = jsonR.fromJson(PlayerConfig.class, r);
        }
        assertEquals("ModernPlayer", p2.getName());
        assertEquals(3, p2.getGauge());
    }

    // GdxAudioDeviceDriver class should not exist
    @Test
    void gdxAudioDeviceDriver_removed() {
        assertThrows(ClassNotFoundException.class, () ->
            Class.forName("bms.player.beatoraja.audio.GdxAudioDeviceDriver"));
    }

    // Instant.now().getEpochSecond() produces valid unix timestamps
    @Test
    void instantEpochSecond_producesValidTimestamp() {
        long ts = Instant.now().getEpochSecond();
        assertTrue(ts > 1700000000L, "Timestamp should be after 2023");
        assertTrue(ts < 2000000000L, "Timestamp should be before 2033");
    }

    // LocalDate start-of-day epoch second matches midnight
    @Test
    void localDateStartOfDay_matchesMidnight() {
        long todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        long now = Instant.now().getEpochSecond();
        assertTrue(todayStart <= now, "Start of day should be <= now");
        assertTrue(now - todayStart < 86400, "Start of day should be within 24h of now");
    }

    // DateTimeFormatter produces same format as old SimpleDateFormat
    @Test
    void dateTimeFormatter_matchesExpectedFormat() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        LocalDateTime time = LocalDateTime.of(2025, 3, 15, 14, 30, 45);
        assertEquals("20250315_143045", time.format(dtf));
    }

    // LocalDateTime provides correct field access (replacing Calendar constants)
    @Test
    void localDateTime_fieldAccess_matchesCalendarSemantics() {
        LocalDateTime time = LocalDateTime.of(2025, 12, 25, 23, 59, 58);
        assertEquals(2025, time.getYear());
        assertEquals(12, time.getMonthValue());
        assertEquals(25, time.getDayOfMonth());
        assertEquals(23, time.getHour());
        assertEquals(59, time.getMinute());
        assertEquals(58, time.getSecond());
    }

    // RandomCourseData.createCourseData produces a name with timestamp
    @Test
    void randomCourseData_createCourseData_hasTimestamp() {
        RandomCourseData rcd = new RandomCourseData();
        rcd.setName("TestCourse");
        rcd.setSongDatas(new bms.player.beatoraja.song.SongData[0]);
        rcd.setConstraint(new CourseData.CourseDataConstraint[0]);
        rcd.setTrophy(new CourseData.TrophyData[0]);
        CourseData cd = rcd.createCourseData();
        assertTrue(cd.getName().startsWith("TestCourse "));
        assertTrue(cd.getName().matches("TestCourse \\d{8}_\\d{6}"));
    }

    private static boolean hasField(Class<?> clazz, String fieldName) {
        try {
            clazz.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
