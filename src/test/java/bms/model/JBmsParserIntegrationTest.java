package bms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JBmsParserIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void decodesBmsChartFromPath() throws Exception {
        Path chart = createBmsChart(tempDir.resolve("chart.bms"), "Parser Song");

        BMSModel model = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(chart);

        assertNotNull(model);
        assertEquals("Parser Song", model.getTitle());
        assertFalse(model.getMD5().isEmpty());
        assertFalse(model.getSHA256().isEmpty());
    }

    @Test
    void decodesBmsonChartFromPath() throws Exception {
        Path chart = createBmsonChart(tempDir.resolve("chart.bmson"), "Parser BMSON");

        BMSModel model = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE).decode(chart);

        assertNotNull(model);
        assertEquals("Parser BMSON", model.getTitle());
        assertFalse(model.getSHA256().isEmpty());
    }

    private Path createBmsChart(Path path, String title) throws IOException {
        String chart = ""
                + "#PLAYER 1\n"
                + "#GENRE TEST\n"
                + "#TITLE " + title + "\n"
                + "#ARTIST Test Artist\n"
                + "#BPM 130\n"
                + "#PLAYLEVEL 3\n"
                + "#RANK 2\n"
                + "#TOTAL 100\n"
                + "#WAV01 sample.wav\n"
                + "#00111:0100\n";
        return createFile(path, chart);
    }

    private Path createBmsonChart(Path path, String title) throws IOException {
        String chart = "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"" + title + "\",\n"
                + "    \"artist\": \"Test Artist\",\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-7k\",\n"
                + "    \"chart_name\": \"NORMAL\",\n"
                + "    \"level\": 3,\n"
                + "    \"init_bpm\": 130,\n"
                + "    \"judge_rank\": 100,\n"
                + "    \"total\": 100,\n"
                + "    \"resolution\": 240\n"
                + "  },\n"
                + "  \"lines\": [{ \"y\": 960 }],\n"
                + "  \"sound_channels\": [\n"
                + "    {\n"
                + "      \"name\": \"sample.wav\",\n"
                + "      \"notes\": [\n"
                + "        { \"x\": 1, \"y\": 0, \"l\": 0, \"c\": false }\n"
                + "      ]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"bga\": {\n"
                + "    \"bga_header\": [],\n"
                + "    \"bga_events\": [],\n"
                + "    \"layer_events\": [],\n"
                + "    \"poor_events\": []\n"
                + "  }\n"
                + "}\n";
        return createFile(path, chart);
    }

    private Path createFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }
}
