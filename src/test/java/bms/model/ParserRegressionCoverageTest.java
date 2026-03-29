package bms.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserRegressionCoverageTest {

    @TempDir
    Path tempDir;

    @Test
    void chartDecoderUtilitiesWorkAcrossSupportedEncodings() {
        assertInstanceOf(BMSDecoder.class, ChartDecoder.getDecoder(Path.of("chart.bms")));
        assertInstanceOf(BMSDecoder.class, ChartDecoder.getDecoder(Path.of("chart.pms")));
        assertInstanceOf(BMSONDecoder.class, ChartDecoder.getDecoder(Path.of("chart.bmson")));
        assertEquals(null, ChartDecoder.getDecoder(Path.of("chart.txt")));

        assertEquals(36, ChartDecoder.parseInt36('1', '0'));
        assertEquals(370, ChartDecoder.parseInt62('5', 'y'));
        assertEquals(3843, ChartDecoder.parseInt62("zz", 0));
        assertEquals("zz", ChartDecoder.toBase62(3843));
    }

    @Test
    void bmsDecoderParsesRichChartMetadataAndTimelineData() throws Exception {
        Path chart = createRichBmsChart(tempDir.resolve("rich.bms"));
        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);

        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertEquals("Rich Chart", model.getTitle());
        assertEquals("Sub", model.getSubTitle());
        assertEquals("Artist", model.getArtist());
        assertEquals("Guest", model.getSubArtist());
        assertEquals("TEST", model.getGenre());
        assertEquals("7", model.getPlaylevel());
        assertEquals(4, model.getDifficulty());
        assertEquals(3, model.getJudgerank());
        assertEquals(BMSModel.JudgeRankType.BMS_RANK, model.getJudgerankType());
        assertEquals(180.0, model.getTotal());
        assertEquals(BMSModel.TotalType.BMS, model.getTotalType());
        assertEquals(75, model.getVolwav());
        assertEquals("stage.png", model.getStagefile());
        assertEquals("back.png", model.getBackbmp());
        assertEquals("prev.ogg", model.getPreview());
        assertEquals("banner.png", model.getBanner());
        assertEquals(2, model.getLnmode());
        assertEquals("https://example.com", model.getValues().get("URL"));
        assertEquals("cid123", model.getValues().get("IPFS"));
        assertFalse(model.getMD5().isEmpty());
        assertFalse(model.getSHA256().isEmpty());
        assertTrue(model.containsLongNote());
        assertTrue(model.containsMineNote());
        assertFalse(Arrays.stream(decoder.getDecodeLog()).anyMatch(log -> log.getState() == DecodeLog.State.ERROR));

        TimeLine timeline = findTimeline(model, 1.0);
        assertNotNull(timeline);
        assertEquals(180.0, timeline.getBPM());
        assertEquals(0.5, timeline.getScroll(), 0.0001);
        assertTrue(timeline.getStop() > 0);
        assertEquals(0, timeline.getBGA());
        assertEquals(0, timeline.getLayer());
        assertTrue(timeline.getBackGroundNotes().length > 0);
        assertNotNull(timeline.getHiddenNote(0));
        assertInstanceOf(LongNote.class, timeline.getNote(0));
        assertInstanceOf(MineNote.class, timeline.getNote(1));
        assertEquals(2, model.getTotalNotes());
    }

    @Test
    void bmsDecoderSupportsSelectedRandomBranchAndByteArrayEntryPoint() throws Exception {
        Path chart = createRandomizedChart(tempDir.resolve("randomized.bms"));
        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);

        BMSModel selected = decoder.decode(new ChartInformation(chart, BMSModel.LNTYPE_LONGNOTE, new int[] { 2 }));
        BMSModel bytes = decoder.decode(Files.readAllBytes(chart), false, new int[] { 1 });

        assertNotNull(selected);
        assertEquals("Branch Two", selected.getTitle());
        assertArrayEquals(new int[] { 2 }, selected.getRandom());
        assertEquals("Branch One", bytes.getTitle());
    }

    @Test
    void bmsDecoderHandlesBase62AndPmsMode() throws Exception {
        Path chart = createBase62PmsChart(tempDir.resolve("base62.pms"));
        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);

        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertEquals(Mode.POPN_9K, model.getMode());
        assertEquals(62, model.getBase());

        TimeLine timeline = findTimeline(model, 1.0);
        assertNotNull(timeline);
        assertEquals(180.0, timeline.getBPM());
        assertTrue(timeline.getStop() > 0);
        assertEquals(0.5, timeline.getScroll(), 0.0001);
        assertNotNull(timeline.getNote(0));
    }

    @Test
    void bmsDecoderReportsWarningsForMalformedHeadersAndUndefinedReferences() throws Exception {
        Path chart = createInvalidBmsChart(tempDir.resolve("invalid.bms"));
        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);

        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertTrue(hasWarning(decoder, "#DIFFICULTYに数字が定義されていません"));
        assertTrue(hasWarning(decoder, "#TOTALに数字が定義されていません"));
        assertTrue(hasWarning(decoder, "未定義のBPM変化を参照しています"));
        assertTrue(hasWarning(decoder, "未定義のSTOPを参照しています"));
        assertTrue(hasWarning(decoder, "未定義のSCROLLを参照しています"));
    }

    @Test
    void bmsonDecoderParsesRichChartData() throws Exception {
        Path chart = createRichBmsonChart(tempDir.resolve("rich.bmson"));
        BMSONDecoder decoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);

        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertEquals("BMSON Rich", model.getTitle());
        assertEquals("Sub [HYPER]", model.getSubTitle());
        assertEquals("Artist", model.getArtist());
        assertEquals("Guest1,Guest2", model.getSubArtist());
        assertEquals("TEST", model.getGenre());
        assertEquals(Mode.BEAT_7K, model.getMode());
        assertEquals(2, model.getLnmode());
        assertEquals("cover.png", model.getBanner());
        assertEquals("back.png", model.getBackbmp());
        assertEquals("eye.png", model.getStagefile());
        assertEquals("preview.ogg", model.getPreview());
        assertEquals(BMSModel.JudgeRankType.BMSON_JUDGERANK, model.getJudgerankType());
        assertEquals(BMSModel.TotalType.BMSON, model.getTotalType());
        assertFalse(model.getSHA256().isEmpty());
        assertTrue(model.containsLongNote());
        assertTrue(model.containsMineNote());

        TimeLine first = findTimeline(model, 0.0);
        TimeLine bpmChange = findTimeline(model, 0.25);
        TimeLine stop = findTimeline(model, 0.5);
        TimeLine scroll = findTimeline(model, 0.75);

        assertNotNull(first);
        assertNotNull(bpmChange);
        assertNotNull(stop);
        assertNotNull(scroll);
        assertNotNull(first.getHiddenNote(2));
        assertTrue(first.getBackGroundNotes().length > 0);
        assertEquals(180.0, bpmChange.getBPM());
        assertTrue(stop.getStop() > 0);
        assertEquals(0.5, scroll.getScroll(), 0.0001);
        assertEquals(0, first.getBGA());
        assertTrue(bpmChange.getEventlayer().length > 0);
        assertTrue(stop.getEventlayer().length > 0);
    }

    @Test
    void bmsonDecoderReportsWarningsForInvalidMetadata() throws Exception {
        Path chart = createInvalidBmsonChart(tempDir.resolve("invalid.bmson"));
        BMSONDecoder decoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);

        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertEquals(Mode.BEAT_7K, model.getMode());
        assertTrue(hasWarning(decoder, "judge_rankの定義が仕様通りでない可能性があります"));
        assertTrue(hasWarning(decoder, "totalが0以下です"));
        assertTrue(hasWarning(decoder, "非対応のmode_hintです"));
    }

    private boolean hasWarning(ChartDecoder decoder, String fragment) {
        return Arrays.stream(decoder.getDecodeLog())
                .anyMatch(log -> log.getState() == DecodeLog.State.WARNING && log.getMessage().contains(fragment));
    }

    private TimeLine findTimeline(BMSModel model, double section) {
        return Arrays.stream(model.getAllTimeLines())
                .filter(tl -> Math.abs(tl.getSection() - section) < 0.0001)
                .findFirst()
                .orElse(null);
    }

    private Path createRichBmsChart(Path path) throws IOException {
        String chart = ""
                + "#PLAYER 1\n"
                + "#GENRE TEST\n"
                + "#TITLE Rich Chart\n"
                + "#SUBTITLE Sub\n"
                + "#ARTIST Artist\n"
                + "#SUBARTIST Guest\n"
                + "#PLAYLEVEL 7\n"
                + "#RANK 3\n"
                + "#TOTAL 180\n"
                + "#VOLWAV 75\n"
                + "#STAGEFILE stage.png\n"
                + "#BACKBMP back.png\n"
                + "#PREVIEW prev.ogg\n"
                + "#BANNER banner.png\n"
                + "#LNMODE 2\n"
                + "#LNOBJ 02\n"
                + "#DIFFICULTY 4\n"
                + "#BPM 120\n"
                + "#BPM01 180\n"
                + "#STOP01 192\n"
                + "#SCROLL01 0.5\n"
                + "#WAV01 bg.wav\n"
                + "#WAV02 ln-end.wav\n"
                + "#WAV03 ln-start.wav\n"
                + "#WAV04 autoplay.wav\n"
                + "#BMP01 image.bmp\n"
                + "%URL https://example.com\n"
                + "@IPFS cid123\n"
                + "#00101:0400\n"
                + "#00104:0100\n"
                + "#00107:0100\n"
                + "#00108:0100\n"
                + "#00109:0100\n"
                + "#001SC:0100\n"
                + "#00111:0302\n"
                + "#00131:0200\n"
                + "#001D2:0100\n";
        return writeFile(path, chart);
    }

    private Path createRandomizedChart(Path path) throws IOException {
        String chart = ""
                + "#RANDOM 2\n"
                + "#IF 1\n"
                + "#TITLE Branch One\n"
                + "#ENDIF\n"
                + "#IF 2\n"
                + "#TITLE Branch Two\n"
                + "#ENDIF\n"
                + "#ENDRANDOM\n"
                + "#ARTIST Artist\n"
                + "#BPM 120\n"
                + "#TOTAL 100\n"
                + "#WAV01 sample.wav\n"
                + "#00111:0100\n";
        return writeFile(path, chart);
    }

    private Path createBase62PmsChart(Path path) throws IOException {
        String chart = ""
                + "#PLAYER 1\n"
                + "#TITLE Base62 Chart\n"
                + "#BPM 120\n"
                + "#TOTAL 100\n"
                + "#BASE 62\n"
                + "#WAVAA sample.wav\n"
                + "#BPMAA 180\n"
                + "#STOPAB 192\n"
                + "#SCROLLAC 0.5\n"
                + "#00108:AA00\n"
                + "#00109:AB00\n"
                + "#001SC:AC00\n"
                + "#00111:AA00\n";
        return writeFile(path, chart);
    }

    private Path createInvalidBmsChart(Path path) throws IOException {
        String chart = ""
                + "#PLAYER 1\n"
                + "#TITLE Invalid Chart\n"
                + "#BPM 120\n"
                + "#DIFFICULTY %\n"
                + "#TOTAL 516,5\n"
                + "#WAV01 sample.wav\n"
                + "#00108:0100\n"
                + "#00109:0100\n"
                + "#001SC:0100\n"
                + "#00111:0100\n";
        return writeFile(path, chart);
    }

    private Path createRichBmsonChart(Path path) throws IOException {
        String chart = "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"BMSON Rich\",\n"
                + "    \"subtitle\": \"Sub\",\n"
                + "    \"artist\": \"Artist\",\n"
                + "    \"subartists\": [\"Guest1\", \"Guest2\"],\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-7k\",\n"
                + "    \"chart_name\": \"HYPER\",\n"
                + "    \"level\": 5,\n"
                + "    \"init_bpm\": 120,\n"
                + "    \"judge_rank\": 100,\n"
                + "    \"total\": 160,\n"
                + "    \"preview_music\": \"preview.ogg\",\n"
                + "    \"banner_image\": \"cover.png\",\n"
                + "    \"back_image\": \"back.png\",\n"
                + "    \"eyecatch_image\": \"eye.png\",\n"
                + "    \"resolution\": 240,\n"
                + "    \"ln_type\": 2\n"
                + "  },\n"
                + "  \"lines\": [{ \"y\": 240 }, { \"y\": 960 }],\n"
                + "  \"bpm_events\": [{ \"y\": 240, \"bpm\": 180 }],\n"
                + "  \"stop_events\": [{ \"y\": 480, \"duration\": 120 }],\n"
                + "  \"scroll_events\": [{ \"y\": 720, \"rate\": 0.5 }],\n"
                + "  \"sound_channels\": [\n"
                + "    {\n"
                + "      \"name\": \"bg.wav\",\n"
                + "      \"notes\": [{ \"x\": 0, \"y\": 0, \"l\": 0, \"c\": false }]\n"
                + "    },\n"
                + "    {\n"
                + "      \"name\": \"keys.wav\",\n"
                + "      \"notes\": [\n"
                + "        { \"x\": 1, \"y\": 0, \"l\": 0, \"c\": false },\n"
                + "        { \"x\": 2, \"y\": 240, \"l\": 240, \"c\": false, \"t\": 1 }\n"
                + "      ]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"key_channels\": [\n"
                + "    { \"name\": \"hidden.wav\", \"notes\": [{ \"x\": 3, \"y\": 0, \"damage\": 1 }] }\n"
                + "  ],\n"
                + "  \"mine_channels\": [\n"
                + "    { \"name\": \"mine.wav\", \"notes\": [{ \"x\": 4, \"y\": 960, \"damage\": 8 }] }\n"
                + "  ],\n"
                + "  \"bga\": {\n"
                + "    \"bga_header\": [{ \"id\": 1, \"name\": \"bga.png\" }],\n"
                + "    \"bga_events\": [{ \"y\": 0, \"id\": 1 }],\n"
                + "    \"layer_events\": [{ \"y\": 240, \"id\": 1, \"condition\": \"play\", \"interval\": 1 }],\n"
                + "    \"poor_events\": [{ \"y\": 480, \"id\": 1 }]\n"
                + "  }\n"
                + "}\n";
        return writeFile(path, chart);
    }

    private Path createInvalidBmsonChart(Path path) throws IOException {
        String chart = "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"Invalid BMSON\",\n"
                + "    \"artist\": \"Artist\",\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"unknown-mode\",\n"
                + "    \"chart_name\": \"NORMAL\",\n"
                + "    \"level\": 3,\n"
                + "    \"init_bpm\": 120,\n"
                + "    \"judge_rank\": 0,\n"
                + "    \"total\": 0,\n"
                + "    \"resolution\": 240\n"
                + "  },\n"
                + "  \"lines\": [{ \"y\": 960 }],\n"
                + "  \"sound_channels\": [\n"
                + "    {\n"
                + "      \"name\": \"sample.wav\",\n"
                + "      \"notes\": [{ \"x\": 1, \"y\": 0, \"l\": 0, \"c\": false }]\n"
                + "    }\n"
                + "  ],\n"
                + "  \"bga\": {\n"
                + "    \"bga_header\": [],\n"
                + "    \"bga_events\": [],\n"
                + "    \"layer_events\": [],\n"
                + "    \"poor_events\": []\n"
                + "  }\n"
                + "}\n";
        return writeFile(path, chart);
    }

    private Path writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }
}
