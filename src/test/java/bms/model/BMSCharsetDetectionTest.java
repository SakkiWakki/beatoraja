package bms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for BMS charset auto-detection.
 * Verifies that BMSDecoder correctly decodes titles from files
 * encoded in Shift-JIS, EUC-KR, UTF-8, and UTF-8 with BOM.
 */
class BMSCharsetDetectionTest {

    @TempDir
    Path tempDir;

    private static final String MINIMAL_BMS_TEMPLATE =
            "#PLAYER 1\r\n" +
            "#GENRE Test\r\n" +
            "#TITLE %s\r\n" +
            "#ARTIST Test\r\n" +
            "#BPM 120\r\n" +
            "#PLAYLEVEL 1\r\n" +
            "#RANK 3\r\n" +
            "#TOTAL 100\r\n" +
            "#00111:01\r\n" +
            "#WAV01 test.wav\r\n";

    private BMSModel decodeBms(String title, Charset charset, boolean addBom) throws IOException {
        String content = String.format(MINIMAL_BMS_TEMPLATE, title);
        byte[] encoded = content.getBytes(charset);

        byte[] data;
        if (addBom) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            bos.write(encoded);
            data = bos.toByteArray();
        } else {
            data = encoded;
        }

        Path bmsFile = tempDir.resolve("test.bms");
        Files.write(bmsFile, data);
        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        return decoder.decode(bmsFile);
    }

    @Test
    void shiftJisTitle() throws IOException {
        // Japanese title in Shift-JIS
        String title = "発狂皆伝";
        BMSModel model = decodeBms(title, Charset.forName("MS932"), false);
        assertNotNull(model, "Shift-JIS BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }

    @Test
    void eucKrTitle() throws IOException {
        // Korean title in EUC-KR
        String title = "커플 브레이킹";
        BMSModel model = decodeBms(title, Charset.forName("EUC-KR"), false);
        assertNotNull(model, "EUC-KR BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }

    @Test
    void utf8Title() throws IOException {
        // Japanese title in UTF-8 (no BOM)
        String title = "発狂皆伝";
        BMSModel model = decodeBms(title, StandardCharsets.UTF_8, false);
        assertNotNull(model, "UTF-8 BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }

    @Test
    void utf8BomTitle() throws IOException {
        // Japanese title in UTF-8 with BOM
        String title = "発狂皆伝";
        BMSModel model = decodeBms(title, StandardCharsets.UTF_8, true);
        assertNotNull(model, "UTF-8 BOM BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }

    @Test
    void utf8KoreanTitle() throws IOException {
        // Korean title in UTF-8 (no BOM)
        String title = "커플 브레이킹";
        BMSModel model = decodeBms(title, StandardCharsets.UTF_8, false);
        assertNotNull(model, "UTF-8 Korean BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }

    @Test
    void asciiOnlyTitle() throws IOException {
        // Pure ASCII — should default to Shift-JIS but produce correct output
        String title = "Couple Breaking";
        BMSModel model = decodeBms(title, StandardCharsets.US_ASCII, false);
        assertNotNull(model, "ASCII BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }

    @Test
    void mixedJapaneseEnglishShiftJis() throws IOException {
        // Mixed Japanese/English in Shift-JIS
        String title = "発狂 (Hard Version)";
        BMSModel model = decodeBms(title, Charset.forName("MS932"), false);
        assertNotNull(model, "Mixed Shift-JIS BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }

    @Test
    void mixedKoreanEnglishEucKr() throws IOException {
        // Mixed Korean/English in EUC-KR
        String title = "커플 (Couple Breaking)";
        BMSModel model = decodeBms(title, Charset.forName("EUC-KR"), false);
        assertNotNull(model, "Mixed EUC-KR BMS should decode successfully");
        assertEquals(title, model.getTitle());
    }
}
