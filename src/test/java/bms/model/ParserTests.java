package bms.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParserTests {

    @TempDir
    Path tempDir;

    @Test
    void commandWordsAndOptionWordsCoverValidAndInvalidBranches() {
        BMSModel model = new BMSModel();

        assertNull(CommandWord.PLAYER.function.apply(model, "2"));
        assertEquals(2, model.getPlayer());
        assertNull(CommandWord.PLAYER.function.apply(model, "3"));
        assertEquals(3, model.getPlayer());
        assertNull(CommandWord.PLAYER.function.apply(model, "4"));
        assertEquals(4, model.getPlayer());
        assertWarning(CommandWord.PLAYER.function.apply(model, "5"), "#PLAYERに規定外の数字");
        assertWarning(CommandWord.PLAYER.function.apply(model, "0"), "#PLAYERに規定外の数字");
        assertWarning(CommandWord.PLAYER.function.apply(model, "x"), "#PLAYERに数字が定義されていません");

        assertNull(CommandWord.GENRE.function.apply(model, "Genre"));
        assertNull(CommandWord.TITLE.function.apply(model, "Title"));
        assertNull(CommandWord.SUBTITLE.function.apply(model, "Sub"));
        assertNull(CommandWord.ARTIST.function.apply(model, "Artist"));
        assertNull(CommandWord.SUBARTIST.function.apply(model, "Guest"));
        assertNull(CommandWord.PLAYLEVEL.function.apply(model, "12"));
        assertEquals("Genre", model.getGenre());
        assertEquals("Title", model.getTitle());
        assertEquals("Sub", model.getSubTitle());
        assertEquals("Artist", model.getArtist());
        assertEquals("Guest", model.getSubArtist());
        assertEquals("12", model.getPlaylevel());

        assertNull(CommandWord.RANK.function.apply(model, "3"));
        assertEquals(3, model.getJudgerank());
        assertEquals(BMSModel.JudgeRankType.BMS_RANK, model.getJudgerankType());
        assertWarning(CommandWord.RANK.function.apply(model, "9"), "#RANKに規定外の数字");
        assertWarning(CommandWord.RANK.function.apply(model, "x"), "#RANKに数字が定義されていません");

        assertNull(CommandWord.DEFEXRANK.function.apply(model, "100"));
        assertEquals(100, model.getJudgerank());
        assertEquals(BMSModel.JudgeRankType.BMS_DEFEXRANK, model.getJudgerankType());
        assertWarning(CommandWord.DEFEXRANK.function.apply(model, "0"), "#DEFEXRANK 1以下はサポートしていません");
        assertWarning(CommandWord.DEFEXRANK.function.apply(model, "x"), "#DEFEXRANKに数字が定義されていません");

        assertNull(CommandWord.TOTAL.function.apply(model, "180.5"));
        assertEquals(180.5, model.getTotal());
        assertEquals(BMSModel.TotalType.BMS, model.getTotalType());
        assertWarning(CommandWord.TOTAL.function.apply(model, "-1"), "#TOTALが0以下です");
        assertWarning(CommandWord.TOTAL.function.apply(model, "5,5"), "#TOTALに数字が定義されていません");

        assertNull(CommandWord.VOLWAV.function.apply(model, "80"));
        assertEquals(80, model.getVolwav());
        assertWarning(CommandWord.VOLWAV.function.apply(model, "x"), "#VOLWAVに数字が定義されていません");

        assertNull(CommandWord.STAGEFILE.function.apply(model, "dir\\stage.png"));
        assertNull(CommandWord.BACKBMP.function.apply(model, "dir\\back.png"));
        assertNull(CommandWord.PREVIEW.function.apply(model, "dir\\preview.ogg"));
        assertNull(CommandWord.BANNER.function.apply(model, "dir\\banner.png"));
        assertEquals("dir/stage.png", model.getStagefile());
        assertEquals("dir/back.png", model.getBackbmp());
        assertEquals("dir/preview.ogg", model.getPreview());
        assertEquals("dir/banner.png", model.getBanner());

        assertNull(CommandWord.LNOBJ.function.apply(model, "10"));
        assertEquals(36, model.getLnobj());
        assertWarning(CommandWord.LNOBJ.function.apply(model, "%"), "#LNOBJに数字が定義されていません");

        assertNull(CommandWord.BASE.function.apply(model, "62"));
        assertEquals(62, model.getBase());
        assertNull(CommandWord.LNOBJ.function.apply(model, "zz"));
        assertEquals(3843, model.getLnobj());
        assertWarning(CommandWord.BASE.function.apply(model, "36"), "#BASEに無効な数字が定義されています");
        assertWarning(CommandWord.BASE.function.apply(model, "x"), "#BASEに数字が定義されていません");

        assertNull(CommandWord.LNMODE.function.apply(model, "3"));
        assertEquals(3, model.getLnmode());
        assertWarning(CommandWord.LNMODE.function.apply(model, "-1"), "#LNMODEに無効な数字が定義されています");
        assertWarning(CommandWord.LNMODE.function.apply(model, "x"), "#LNMODEに数字が定義されていません");

        assertNull(CommandWord.DIFFICULTY.function.apply(model, "4"));
        assertEquals(4, model.getDifficulty());
        assertWarning(CommandWord.DIFFICULTY.function.apply(model, "%"), "#DIFFICULTYに数字が定義されていません");

        assertNull(CommandWord.COMMENT.function.apply(model, "ignored"));
        assertNull(OptionWord.URL.function.apply(model, "https://example.com"));
    }

    @Test
    void chartDecoderDirectUtilitiesAndFileOverloadsAreCovered() throws Exception {
        Path chart = writeFile(tempDir.resolve("decoder.bms"), "#BPM 120\n");
        TestChartDecoder decoder = new TestChartDecoder();
        decoder.lntype = BMSModel.LNTYPE_HELLCHARGENOTE;

        assertNotNull(decoder.decode(chart.toFile()));
        assertNotNull(decoder.decode(chart));
        assertEquals(chart, decoder.lastInfo.path);
        assertEquals(BMSModel.LNTYPE_HELLCHARGENOTE, decoder.lastInfo.lntype);

        assertEquals(11, ChartDecoder.parseInt36('0', 'B'));
        assertEquals(371, ChartDecoder.parseInt36('a', 'b'));
        assertEquals(371, ChartDecoder.parseInt36('A', 'B'));
        assertEquals(655, ChartDecoder.parseInt62('A', 'Z'));
        assertEquals(2293, ChartDecoder.parseInt62('a', 'z'));
        assertEquals("0A", ChartDecoder.toBase62(10));
        assertEquals("00", ChartDecoder.toBase62(0));
        assertEquals("01-", ChartDecoder.toBase62(-1));
        assertTrue(ChartDecoder.getDecoder(Path.of("chart.BME")) instanceof BMSDecoder);
        assertEquals(-1, ChartDecoder.parseInt36('A', '!'));
        assertEquals(-1, ChartDecoder.parseInt62('a', '!'));

        decoder.log.add(new DecodeLog(DecodeLog.State.INFO, "info"));
        decoder.log.add(new DecodeLog(DecodeLog.State.WARNING, "warn"));
        decoder.log.add(new DecodeLog(DecodeLog.State.ERROR, "error"));
        decoder.exposePrintLog(chart);

        try {
            ChartDecoder.parseInt36("**", 0);
            throw new AssertionError("Expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
        try {
            ChartDecoder.parseInt62("**", 0);
            throw new AssertionError("Expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    @Test
    void chartDecoderUtilitiesAndModelAccessorsCoverRemainingBranches() {
        assertEquals("000f", BMSDecoder.convertHexString(new byte[] { 0x00, 0x0f }));
        assertEquals(Mode.BEAT_14K, Mode.getMode("beat-14k"));
        assertEquals(null, Mode.getMode("missing"));
        assertTrue(Mode.BEAT_14K.isScratchKey(7));
        assertFalse(Mode.BEAT_14K.isScratchKey(4));

        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setPlayer(1);
        model.setTitle(null);
        model.setSubTitle(null);
        model.setGenre(null);
        model.setArtist(null);
        model.setSubArtist(null);
        model.setBanner(null);
        model.setStagefile(null);
        model.setBackbmp(null);
        model.setPreview("preview.ogg");
        model.setBase(999);
        assertEquals("", model.getTitle());
        assertEquals("", model.getSubTitle());
        assertEquals("", model.getGenre());
        assertEquals("", model.getArtist());
        assertEquals("", model.getSubArtist());
        assertEquals("", model.getBanner());
        assertEquals("", model.getStagefile());
        assertEquals("", model.getBackbmp());
        assertEquals(36, model.getBase());

        TimeLine tl0 = new TimeLine(0.0, 0, 8);
        tl0.setBPM(120);
        tl0.setSectionLine(true);
        tl0.setBGA(3);
        tl0.setLayer(4);
        tl0.addBackGroundNote(new NormalNote(9));
        tl0.addBackGroundNote(null);
        tl0.setHiddenNote(1, new NormalNote(8));
        tl0.setHiddenNote(2, null);
        NormalNote normal = new NormalNote(1, 3_000, 5_000);
        tl0.setNote(0, normal);
        LongNote undefinedStart = new LongNote(2);
        LongNote undefinedEnd = new LongNote(-2);
        undefinedStart.setSection(0.0);
        undefinedEnd.setSection(0.5);
        tl0.setNote(2, undefinedStart);

        TimeLine tl1 = new TimeLine(0.5, 500_000, 8);
        tl1.setBPM(150);
        tl1.setStop(120_000);
        tl1.setScroll(0.75);
        tl1.setNote(2, undefinedEnd);
        undefinedStart.setPair(undefinedEnd);
        tl1.setNote(3, new MineNote(5, 7.5));

        model.setAllTimeLine(new TimeLine[] { tl0, tl1 });
        model.setChartInformation(new ChartInformation(Path.of("chart.bms"), BMSModel.LNTYPE_CHARGENOTE, new int[] { 2, 1 }));

        assertEquals("chart.bms", model.getPath());
        assertArrayEquals(new int[] { 2, 1 }, model.getRandom());
        assertEquals(BMSModel.LNTYPE_CHARGENOTE, model.getLntype());
        assertEquals(0.0, model.getMinBPM());
        assertEquals(150.0, model.getMaxBPM());
        assertEquals(500, model.getLastMilliTime());
        assertEquals(500, model.getLastNoteMilliTime());
        assertEquals("preview.ogg", model.getPreview());
        assertTrue(model.containsUndefinedLongNote());
        assertTrue(model.containsLongNote());
        assertTrue(model.containsMineNote());
        assertTrue(model.toChartString().contains("m7.5"));
        assertTrue(model.toChartString().contains("L"));

        model.setMode(Mode.BEAT_14K);
        assertEquals(16, model.getAllTimeLines()[0].getLaneCount());
        assertEquals(16, model.getAllTimeLines()[1].getLaneCount());
        assertEquals(0, BMSModelUtils.getTotalNotes(model, 0, Integer.MAX_VALUE, BMSModelUtils.TOTALNOTES_LONG_SCRATCH, 2));

        model.setMode(Mode.BEAT_7K);
        TimeLine tl = model.getAllTimeLines()[0];
        assertTrue(tl.existNote());
        assertTrue(tl.existHiddenNote());
        assertEquals(2, tl.getTotalNotes(BMSModel.LNTYPE_LONGNOTE));
        assertEquals(2, tl.getTotalNotes(BMSModel.LNTYPE_CHARGENOTE));
        assertEquals(0, tl.getTime());
        assertEquals(0L, tl.getMilliTime());
        assertEquals(0L, tl.getMicroTime());
        assertEquals(0, tl.getStop());
        assertEquals(0L, tl.getMilliStop());
        assertEquals(0L, tl.getMicroStop());
        assertEquals(3, tl.getBGA());
        assertEquals(4, tl.getLayer());
        assertEquals(1.0, tl.getScroll());
        assertEquals(8, tl.getLaneCount());

        Note bg = tl.getBackGroundNotes()[0];
        tl.removeBackGroundNote(new NormalNote(999));
        tl.removeBackGroundNote(bg);
        assertEquals(0, tl.getBackGroundNotes().length);

        tl.setSection(2.5);
        assertEquals(2.5, tl.getSection());
        assertEquals(2.5, tl.getNote(0).getSection());
        assertEquals(2.5, tl.getHiddenNote(1).getSection());
        tl.setMicroTime(2_000_000);
        assertEquals(2_000L, tl.getMilliTime());
        assertEquals(2_000L, tl.getNote(0).getMilliTime());
        tl.setLaneCount(5);
        assertEquals(5, tl.getLaneCount());
        tl.setLaneCount(8);
        assertEquals(8, tl.getLaneCount());

        assertTrue(new BMSModelUtils().getAverageNotesPerTime(model, 0, 4_000) > 0.0);
        assertTrue(BMSModelUtils.getMaxNotesPerTime(model, 10_000) >= 1.0);
        assertTrue(BMSModelUtils.setStartNoteTime(model, 3_000) > 0);
        assertEquals(0, model.getAllTimeLines()[0].getMilliTime());
    }

    @Test
    void modelFalseBranchesNoteHelpersAndEventLaneNullPathsAreCovered() {
        BMSModel empty = new BMSModel();
        empty.setMode(Mode.BEAT_7K);
        empty.setTitle("B");
        empty.setWavList(new String[] { "a.wav" });
        empty.setBgaList(new String[] { "bga.png" });
        empty.setAllTimeLine(new TimeLine[0]);

        assertEquals(0, empty.getLastMilliTime());
        assertEquals(0, empty.getLastNoteMilliTime());
        assertEquals(0, empty.getLastTime());
        assertEquals(0, empty.getLastNoteTime());
        assertFalse(empty.containsUndefinedLongNote());
        assertFalse(empty.containsLongNote());
        assertFalse(empty.containsMineNote());
        assertNull(empty.getChartInformation());
        assertNull(empty.getRandom());
        assertNull(empty.getPath());
        assertEquals(BMSModel.LNTYPE_LONGNOTE, empty.getLntype());
        assertEquals("a.wav", empty.getWavList()[0]);
        assertEquals("bga.png", empty.getBgaList()[0]);
        BMSModel sameTitle = new BMSModel();
        sameTitle.setTitle("B");
        assertEquals(0, empty.compareTo(sameTitle));

        NormalNote note = new NormalNote(1);
        note.setMicroStarttime(2_000);
        note.setMicroDuration(3_000);
        note.setMicroPlayTime(4_000);
        note.setMicroTime(5_000);
        note.addLayeredNote(null);
        assertEquals(2_000L, note.getMicroStarttime());
        assertEquals(2L, note.getMilliStarttime());
        assertEquals(3_000L, note.getMicroDuration());
        assertEquals(3L, note.getMilliDuration());
        assertEquals(4_000L, note.getMicroPlayTime());
        assertEquals(4L, note.getMilliPlayTime());
        assertEquals(5_000L, note.getMicroTime());
        assertEquals(5L, note.getMilliTime());
        assertEquals(5, note.getTime());
        assertNotNull(note.clone());

        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        TimeLine tl0 = new TimeLine(0.0, 0, model.getMode().key);
        tl0.setSectionLine(true);
        tl0.setBPM(120);
        tl0.setNote(0, new NormalNote(1));
        TimeLine tl1 = new TimeLine(1.0, 1_000_000, model.getMode().key);
        tl1.setBPM(150);
        tl1.setStop(50_000);
        model.setAllTimeLine(new TimeLine[] { tl0, tl1 });

        EventLane lane = new EventLane(model);
        assertNotNull(lane.getSection());
        assertNull(lane.getSection());
        assertNotNull(lane.getBpm());
        assertNull(lane.getBpm());
        assertNotNull(lane.getStop());
        assertNull(lane.getStop());
        lane.mark(10_000);
        lane.reset();
    }

    @Test
    void timelineAndUtilityEdgeBranchesAreCovered() {
        TimeLine empty = new TimeLine(0.0, 0, 8);
        assertFalse(empty.existNote());
        assertFalse(empty.existHiddenNote());

        empty.addBackGroundNote(new NormalNote(1));
        empty.addBackGroundNote(new NormalNote(2));
        Note survivor = empty.getBackGroundNotes()[1];
        empty.removeBackGroundNote(empty.getBackGroundNotes()[0]);
        assertEquals(1, empty.getBackGroundNotes().length);
        assertEquals(survivor, empty.getBackGroundNotes()[0]);

        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setLnmode(LongNote.TYPE_CHARGENOTE);
        model.setChartInformation(new ChartInformation(null, BMSModel.LNTYPE_HELLCHARGENOTE, null));

        TimeLine tl0 = new TimeLine(3.0, 3_000_000, model.getMode().key);
        tl0.setBPM(120);
        LongNote scratchStart = new LongNote(1);
        LongNote scratchEnd = new LongNote(-2);
        scratchStart.setType(LongNote.TYPE_HELLCHARGENOTE);
        tl0.setNote(7, scratchStart);

        TimeLine tl1 = new TimeLine(4.0, 4_000_000, model.getMode().key);
        tl1.setBPM(120);
        tl1.setNote(7, scratchEnd);
        scratchStart.setPair(scratchEnd);
        tl1.setNote(0, new MineNote(2, 4.0));
        tl1.setNote(7, new MineNote(3, 5.0));

        model.setAllTimeLine(new TimeLine[] { tl0, tl1 });

        assertTrue(model.toChartString().contains("LNMODE:2"));
        assertEquals(1, BMSModelUtils.getTotalNotes(model, 0, Integer.MAX_VALUE, BMSModelUtils.TOTALNOTES_LONG_SCRATCH));
        assertEquals(2, BMSModelUtils.getTotalNotes(model, 0, Integer.MAX_VALUE, BMSModelUtils.TOTALNOTES_MINE));
        assertEquals(0, BMSModelUtils.setStartNoteTime(model, 1_000));
    }

    @Test
    void sectionConstructionAndTimelineGenerationHandleMalformedChannelsAndPromotions() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_5K);
        model.setBpm(120);

        List<DecodeLog> logs = new ArrayList<>();
        Section section = new Section(model, null, List.of(
                "#001@@:0100",
                "#00102:not-a-rate",
                "#00106:0101",
                "#00108:0100",
                "#00109:0100",
                "#001SC:0100",
                "#00118:0100",
                "#00121:0100",
                "#00131:0100",
                "#00111:0100",
                "#00104:0100",
                "#00107:0100"),
                new TreeMap<>(),
                new TreeMap<>(),
                new TreeMap<>(),
                logs);

        TreeMap<Double, ChartDecoder.TimeLineCache> cache = new TreeMap<>();
        TimeLine base = new TimeLine(0.0, 0, model.getMode().key);
        base.setBPM(120);
        cache.put(0.0, new ChartDecoder.TimeLineCache(0.0, base));

        int[] wavmap = new int[512];
        int[] bgamap = new int[512];
        Arrays.fill(bgamap, -2);
        wavmap[1] = 10;
        bgamap[1] = 7;

        @SuppressWarnings("unchecked")
        List<LongNote>[] lnlist = new List[model.getMode().key];
        LongNote[] startln = new LongNote[model.getMode().key];
        section.makeTimeLines(wavmap, bgamap, cache, lnlist, startln);

        assertEquals(Mode.BEAT_14K, model.getMode());
        TimeLine generated = cache.get(0.0).timeline;
        assertTrue(generated.getSectionLine());
        assertNotNull(generated.getNote(0));
        assertNotNull(generated.getHiddenNote(0));
        assertEquals(7, generated.getBGA());
        assertEquals(7, generated.getLayer());
        assertEquals(1, generated.getEventlayer().length);
        assertEquals(Layer.EventType.MISS, generated.getEventlayer()[0].event.type);
        assertEquals(2, generated.getEventlayer()[0].sequence[0].length);

        assertHasWarning(logs, "チャンネル定義が無効です");
        assertHasWarning(logs, "小節の拡大率が不正です");
        assertHasWarning(logs, "未定義のBPM変化を参照しています");
        assertHasWarning(logs, "未定義のSTOPを参照しています");
        assertHasWarning(logs, "未定義のSCROLLを参照しています");
    }

    @Test
    void sectionLongNoteTransitionsAndCollisionsArePreserved() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_5K);
        model.setBpm(120);
        model.setLnobj(2);
        model.setLnmode(LongNote.TYPE_CHARGENOTE);

        List<DecodeLog> logs = new ArrayList<>();
        List<List<String>> sectionLines = List.of(
                List.of("#00111:0100"),
                List.of("#00211:0200"),
                List.of("#00311:0300", "#00311:0400"),
                List.of("#00411:0700", "#00451:0500"),
                List.of("#00511:0600", "#005D1:0100"),
                List.of("#00651:0500"));

        TreeMap<Double, ChartDecoder.TimeLineCache> cache = new TreeMap<>();
        TimeLine base = new TimeLine(0.0, 0, model.getMode().key);
        base.setBPM(120);
        cache.put(0.0, new ChartDecoder.TimeLineCache(0.0, base));

        int[] wavmap = new int[512];
        int[] bgamap = new int[8];
        for (int i = 0; i < wavmap.length; i++) {
            wavmap[i] = i;
        }

        @SuppressWarnings("unchecked")
        List<LongNote>[] lnlist = new List[model.getMode().key];
        LongNote[] startln = new LongNote[model.getMode().key];
        Section prev = null;
        for (List<String> lines : sectionLines) {
            Section section = new Section(model, prev, lines, new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), logs);
            section.makeTimeLines(wavmap, bgamap, cache, lnlist, startln);
            prev = section;
        }

        assertInstanceOf(LongNote.class, cache.get(0.0).timeline.getNote(0));
        LongNote lnFromLnobj = (LongNote) cache.get(0.0).timeline.getNote(0);
        assertNotNull(lnFromLnobj.getPair());
        assertTrue(lnFromLnobj.getPair().isEnd());

        assertHasWarning(logs, "通常ノート追加時に衝突が発生しました");
        assertHasWarning(logs, "LN開始位置に通常ノートが存在します");
        assertHasWarning(logs, "地雷ノート追加時に衝突が発生しました");
        assertHasWarning(logs, "LN内に通常ノートが存在します");

        TimeLine inside = cache.get(4.0).timeline;
        assertNull(inside.getNote(0));
        assertTrue(inside.getBackGroundNotes().length > 0);
    }

    @Test
    void sectionCoversBase62SecondaryChannelsAndMixedLnObjWarnings() {
        BMSModel base62Model = new BMSModel();
        base62Model.setMode(Mode.BEAT_14K);
        base62Model.setBase(62);
        base62Model.setBpm(120);

        List<DecodeLog> logs = new ArrayList<>();
        TreeMap<Integer, Double> bpms = new TreeMap<>();
        TreeMap<Integer, Double> stops = new TreeMap<>();
        TreeMap<Integer, Double> scrolls = new TreeMap<>();
        bpms.put(10, 180.0);
        stops.put(11, 1.0);
        scrolls.put(12, 0.5);
        Section section = new Section(base62Model, null, List.of(
                "#00102:2.0",
                "#00103:0A",
                "#00106:0102",
                "#00109:0B",
                "#001SC:0C",
                "#00141:0100",
                "#00161:0100",
                "#001E1:0100"),
                bpms, stops, scrolls, logs);

        TreeMap<Double, ChartDecoder.TimeLineCache> cache = new TreeMap<>();
        TimeLine base = new TimeLine(0.0, 0, base62Model.getMode().key);
        base.setBPM(120);
        cache.put(0.0, new ChartDecoder.TimeLineCache(0.0, base));
        int[] wavmap = new int[512];
        int[] bgamap = new int[512];
        Arrays.fill(bgamap, -2);
        wavmap[1] = 1;
        bgamap[1] = 7;

        @SuppressWarnings("unchecked")
        List<LongNote>[] lnlist = new List[base62Model.getMode().key];
        LongNote[] startln = new LongNote[base62Model.getMode().key];
        section.makeTimeLines(wavmap, bgamap, cache, lnlist, startln);

        TimeLine timeline = cache.get(0.0).timeline;
        assertEquals(10.0, timeline.getBPM());
        assertEquals(0.5, timeline.getScroll(), 0.0001);
        assertTrue(timeline.getStop() > 0);
        assertNotNull(timeline.getHiddenNote(8));
        assertInstanceOf(LongNote.class, timeline.getNote(8));
        assertEquals(3, timeline.getEventlayer()[0].sequence[0].length);
        assertHasWarning(logs, "地雷ノート追加時に衝突が発生しました");

        BMSModel mixedModel = new BMSModel();
        mixedModel.setMode(Mode.BEAT_5K);
        mixedModel.setBpm(120);
        mixedModel.setLnobj(2);
        mixedModel.setLnmode(LongNote.TYPE_LONGNOTE);
        List<DecodeLog> mixedLogs = new ArrayList<>();
        TreeMap<Double, ChartDecoder.TimeLineCache> mixedCache = new TreeMap<>();
        TimeLine mixedBase = new TimeLine(0.0, 0, mixedModel.getMode().key);
        mixedBase.setBPM(120);
        mixedCache.put(0.0, new ChartDecoder.TimeLineCache(0.0, mixedBase));
        @SuppressWarnings("unchecked")
        List<LongNote>[] mixedLnlist = new List[mixedModel.getMode().key];
        LongNote[] mixedStart = new LongNote[mixedModel.getMode().key];
        int[] identity = new int[512];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i;
        }

        Section prev = null;
        for (List<String> lines : List.of(
                List.of("#00151:0100"),
                List.of("#00211:0200"),
                List.of("#00311:0200"))) {
            Section current = new Section(mixedModel, prev, lines, new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), mixedLogs);
            current.makeTimeLines(identity, new int[8], mixedCache, mixedLnlist, mixedStart);
            prev = current;
        }

        assertHasWarning(mixedLogs, "LNレーンで開始定義し、LNオブジェクトで終端定義しています");
        assertHasWarning(mixedLogs, "LNオブジェクトの対応が取れません");
    }

    @Test
    void sectionCoversRemainingInvalidAndInsideLnBranches() {
        BMSModel invalidModel = new BMSModel();
        invalidModel.setMode(Mode.BEAT_5K);
        invalidModel.setBpm(120);
        invalidModel.setTitle("Invalid");
        List<DecodeLog> invalidLogs = new ArrayList<>();
        Section invalidSection = new Section(invalidModel, null, List.of(
                "#00106:!!",
                "#00111:!1",
                "#00117:0100"),
                new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), invalidLogs);
        TreeMap<Double, ChartDecoder.TimeLineCache> invalidCache = new TreeMap<>();
        TimeLine invalidBase = new TimeLine(0.0, 0, invalidModel.getMode().key);
        invalidBase.setBPM(120);
        invalidCache.put(0.0, new ChartDecoder.TimeLineCache(0.0, invalidBase));
        @SuppressWarnings("unchecked")
        List<LongNote>[] invalidLnlist = new List[invalidModel.getMode().key];
        invalidSection.makeTimeLines(new int[8], new int[8], invalidCache, invalidLnlist, new LongNote[invalidModel.getMode().key]);
        assertHasWarning(invalidLogs, "チャンネル定義中の不正な値");
        assertNull(invalidCache.get(0.0).timeline.getNote(0));

        BMSModel insideModel = new BMSModel();
        insideModel.setMode(Mode.BEAT_5K);
        insideModel.setBpm(120);
        TreeMap<Double, ChartDecoder.TimeLineCache> cache = new TreeMap<>();
        TimeLine base = new TimeLine(0.0, 0, insideModel.getMode().key);
        base.setBPM(120);
        cache.put(0.0, new ChartDecoder.TimeLineCache(0.0, base));
        TimeLine prevTl = new TimeLine(0.0, 0, insideModel.getMode().key);
        prevTl.setBPM(120);
        cache.put(0.0, new ChartDecoder.TimeLineCache(0.0, prevTl));
        TimeLine pairTl = new TimeLine(1.0, 1_000_000, insideModel.getMode().key);
        pairTl.setBPM(120);
        cache.put(1.0, new ChartDecoder.TimeLineCache(1_000_000.0, pairTl));
        LongNote existing = new LongNote(1);
        LongNote existingEnd = new LongNote(-2);
        prevTl.setNote(0, existing);
        pairTl.setNote(0, existingEnd);
        existing.setPair(existingEnd);
        @SuppressWarnings("unchecked")
        List<LongNote>[] lnlist = new List[insideModel.getMode().key];
        lnlist[0] = new ArrayList<>();
        lnlist[0].add(existing);
        List<DecodeLog> logs = new ArrayList<>();

        LongNote[] startInsideSentinel = new LongNote[insideModel.getMode().key];
        Section insideSection = new Section(insideModel, null, List.of("#00151:0101"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), logs);
        insideSection.makeTimeLines(identityMap(64), new int[8], cache, lnlist, startInsideSentinel);
        assertHasWarning(logs, "LN内にLN開始ノートを定義しようとしています");
        assertHasWarning(logs, "LN内にLN終端ノートを定義しようとしています");

        logs.clear();
        LongNote[] startOutsideSentinel = new LongNote[insideModel.getMode().key];
        LongNote sentinel = new LongNote(2);
        sentinel.setSection(Double.MIN_VALUE);
        startOutsideSentinel[0] = sentinel;
        Section dummyPrev = new Section(insideModel, insideSection, List.of(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), logs);
        Section clearSentinel = new Section(insideModel, dummyPrev, List.of("#00151:0100"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), logs);
        @SuppressWarnings("unchecked") List<LongNote>[] clearLnlist = new List[insideModel.getMode().key];
        clearSentinel.makeTimeLines(identityMap(64), new int[8], cache, clearLnlist, startOutsideSentinel);
        assertNull(startOutsideSentinel[0]);

        logs.clear();
        @SuppressWarnings("unchecked")
        List<LongNote>[] endLnlist = new List[insideModel.getMode().key];
        LongNote[] startWithRealSection = new LongNote[insideModel.getMode().key];
        LongNote pending = new LongNote(3);
        pending.setSection(1.0);
        startWithRealSection[0] = pending;
        prevTl.setNote(0, pending);
        Section forceInsideEnd = new Section(insideModel, null, List.of("#00151:0100"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), logs);
        forceInsideEnd.makeTimeLines(identityMap(64), new int[8], cache, lnlist, startWithRealSection);
        assertHasWarning(logs, "LN内にLN終端ノートを定義しようとしています");
        assertNull(prevTl.getNote(0));

        logs.clear();
        LongNote[] mineStart = new LongNote[insideModel.getMode().key];
        Section insideMine = new Section(insideModel, null, List.of("#001D1:0100"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), logs);
        insideMine.makeTimeLines(identityMap(64), new int[8], cache, lnlist, mineStart);
        assertHasWarning(logs, "地雷ノート追加時に衝突が発生しました");

        BMSModel base62MineModel = new BMSModel();
        base62MineModel.setMode(Mode.BEAT_5K);
        base62MineModel.setBase(62);
        base62MineModel.setBpm(120);
        List<DecodeLog> base62Logs = new ArrayList<>();
        Section base62Mine = new Section(base62MineModel, null, List.of("#001D1:0a"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), base62Logs);
        TreeMap<Double, ChartDecoder.TimeLineCache> base62Cache = new TreeMap<>();
        TimeLine base62Tl = new TimeLine(0.0, 0, base62MineModel.getMode().key);
        base62Tl.setBPM(120);
        base62Cache.put(0.0, new ChartDecoder.TimeLineCache(0.0, base62Tl));
        @SuppressWarnings("unchecked") List<LongNote>[] base62Lnlist = new List[base62MineModel.getMode().key];
        base62Mine.makeTimeLines(identityMap(64), new int[8], base62Cache, base62Lnlist, new LongNote[base62MineModel.getMode().key]);
        assertInstanceOf(MineNote.class, base62Cache.get(0.0).timeline.getNote(0));

        BMSModel endModel = new BMSModel();
        endModel.setMode(Mode.BEAT_5K);
        endModel.setBpm(120);
        endModel.setLnmode(LongNote.TYPE_LONGNOTE);
        TreeMap<Double, ChartDecoder.TimeLineCache> endCache = new TreeMap<>();
        TimeLine endBase = new TimeLine(0.0, 0, endModel.getMode().key);
        endBase.setBPM(120);
        endCache.put(0.0, new ChartDecoder.TimeLineCache(0.0, endBase));
        @SuppressWarnings("unchecked")
        List<LongNote>[] endLists = new List[endModel.getMode().key];
        LongNote[] endStart = new LongNote[endModel.getMode().key];
        List<DecodeLog> endLogs = new ArrayList<>();
        Section lnStartSection = new Section(endModel, null, List.of("#00151:0100"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), endLogs);
        lnStartSection.makeTimeLines(identityMap(64), new int[8], endCache, endLists, endStart);
        Section lnEndSection = new Section(endModel, lnStartSection, List.of("#00151:0200"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), endLogs);
        lnEndSection.makeTimeLines(identityMap(64), new int[8], endCache, endLists, endStart);
        assertNotNull(endLists[0]);

        BMSModel mineLoopModel = new BMSModel();
        mineLoopModel.setMode(Mode.BEAT_5K);
        mineLoopModel.setBpm(120);
        TreeMap<Double, ChartDecoder.TimeLineCache> mineLoopCache = new TreeMap<>();
        TimeLine mineBase = new TimeLine(0.0, 0, mineLoopModel.getMode().key);
        mineBase.setBPM(120);
        mineLoopCache.put(0.0, new ChartDecoder.TimeLineCache(0.0, mineBase));
        TimeLine mineEndTl = new TimeLine(0.5, 500_000, mineLoopModel.getMode().key);
        mineEndTl.setBPM(120);
        mineLoopCache.put(0.5, new ChartDecoder.TimeLineCache(500_000.0, mineEndTl));
        LongNote mineLnStart = new LongNote(1);
        LongNote mineLnEnd = new LongNote(-2);
        mineBase.setNote(0, mineLnStart);
        mineEndTl.setNote(0, mineLnEnd);
        mineLnStart.setPair(mineLnEnd);
        @SuppressWarnings("unchecked")
        List<LongNote>[] mineLoopLists = new List[mineLoopModel.getMode().key];
        mineLoopLists[0] = new ArrayList<>();
        mineLoopLists[0].add(mineLnStart);
        Section mineOutsideLn = new Section(mineLoopModel, null, List.of("#001D1:0100"), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), new ArrayList<>());
        mineOutsideLn.makeTimeLines(identityMap(64), new int[8], mineLoopCache, mineLoopLists, new LongNote[mineLoopModel.getMode().key]);
    }

    @Test
    void bmsDecoderCoversMalformedControlFlowAndHeaderWarnings() throws Exception {
        Path chart = writeFile(tempDir.resolve("controlflow.bms"), ""
                + "#IF 1\n"
                + "#ENDIF\n"
                + "#ENDRANDOM\n"
                + "#RANDOM a\n"
                + "#RANDOM 2\n"
                + "#IF a\n"
                + "#ENDIF\n"
                + "#ENDRANDOM\n"
                + "#player 1\n"
                + "#title Lowercase Works\n"
                + "#PLAYER 5\n"
                + "#BPM -120\n"
                + "#BPM 120\n"
                + "#BPMAA nope\n"
                + "#WAV\n"
                + "#WAVA\n"
                + "#BMP\n"
                + "#BMPA\n"
                + "#STOPAA nope\n"
                + "#STOPA\n"
                + "#SCROLLAA nope\n"
                + "#SCROLLA\n"
                + "#00A11:0100\n"
                + "#001@@:0100\n"
                + "#00121:0100\n");

        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertEquals("Lowercase Works", model.getTitle());
        assertHasWarning(decoder.getDecodeLog(), "#IFに対応する#RANDOMが定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "ENDIFに対応するIFが存在しません");
        assertHasWarning(decoder.getDecodeLog(), "ENDRANDOMに対応するRANDOMが存在しません");
        assertHasWarning(decoder.getDecodeLog(), "#RANDOMに数字が定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "#IFに数字が定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "#PLAYERに規定外の数字が定義されています");
        assertHasWarning(decoder.getDecodeLog(), "#negative BPMはサポートされていません");
        assertHasWarning(decoder.getDecodeLog(), "#BPMxxに数字が定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "#WAVxxは不十分な定義です");
        assertHasWarning(decoder.getDecodeLog(), "#BMPxxは不十分な定義です");
        assertHasWarning(decoder.getDecodeLog(), "#STOPxxに数字が定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "#STOPxxは不十分な定義です");
        assertHasWarning(decoder.getDecodeLog(), "#SCROLLxxに数字が定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "#SCROLLxxは不十分な定義です");
        assertHasWarning(decoder.getDecodeLog(), "小節に数字が定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "チャンネル定義が無効です");
        assertHasWarning(decoder.getDecodeLog(), "TOTALが未定義です");
    }

    @Test
    void bmsDecoderFailsWhenInitialBpmIsMissing() {
        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel model = decoder.decode("#TOTAL 100\n#WAV01 sample.wav\n#00111:0100\n".getBytes(), false, null);

        assertNull(model);
        assertHasError(decoder.getDecodeLog(), "開始BPMが定義されていないため、BMS解析に失敗しました");
    }

    @Test
    void bmsDecoderCoversMissingFilesOpenLongNotesAndLateTimelineWarnings() throws Exception {
        BMSDecoder missing = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        assertNull(missing.decode(tempDir.resolve("missing-file.bms")));
        assertHasError(missing.getDecodeLog(), "BMSファイルが見つかりません");

        Path chart = writeFile(tempDir.resolve("edge-warnings.bms"), ""
                + "\n"
                + "#PLAYER 2\n"
                + "#TITLE Edge Warnings\n"
                + "#BASE 62\n"
                + "#BPM nope\n"
                + "#BPM 120\n"
                + "#TOTAL 50\n"
                + "#WAVAA sample.wav\n"
                + "#BMPAA image.bmp\n"
                + "#STOPAA -192\n"
                + "#BPMAB 150\n"
                + "#00151:AA00\n"
                + "#10008:AB00\n");

        BMSDecoder decoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertHasWarning(decoder.getDecodeLog(), "#BPMに数字が定義されていません");
        assertHasWarning(decoder.getDecodeLog(), "#negative STOPはサポートされていません");
        assertHasWarning(decoder.getDecodeLog(), "曲の終端までにLN終端定義されていないLNがあります");
        assertHasWarning(decoder.getDecodeLog(), "TOTAL値が少なすぎます");
        assertHasWarning(decoder.getDecodeLog(), "最後のノート定義から30秒以上の余白があります");
        assertHasWarning(decoder.getDecodeLog(), "#PLAYER定義が2以上にもかかわらず2P側のノーツ定義が一切ありません");
    }

    @Test
    void bmsDecoderCoversPathNullAndAdditionalWarningBranches() throws Exception {
        Path noBpm = writeFile(tempDir.resolve("no-bpm-path.bms"), "#TOTAL 100\n#00111:0100\n");
        BMSDecoder noBpmDecoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        assertNull(noBpmDecoder.decode(noBpm));

        BMSDecoder missingInfoDecoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        assertNull(missingInfoDecoder.decode(new ChartInformation(tempDir.resolve("missing-info.bms"), BMSModel.LNTYPE_LONGNOTE, null)));
        assertHasError(missingInfoDecoder.getDecodeLog(), "BMSファイルが見つかりません");

        Path warningChart = writeFile(tempDir.resolve("extra-warnings.bms"), ""
                + "#TITLE Extra Warnings\n"
                + "#BPM 120\n"
                + "#BPMAA -120\n"
                + "#WAV** sample.wav\n"
                + "#BMP** image.bmp\n"
                + "#TOTAL 100\n"
                + "#00111:0100\n");
        BMSDecoder warningDecoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel warningModel = warningDecoder.decode(warningChart);
        assertNotNull(warningModel);
        assertHasWarning(warningDecoder.getDecodeLog(), "#negative BPMはサポートされていません");
        assertHasWarning(warningDecoder.getDecodeLog(), "#WAVxxは不十分な定義です");
        assertHasWarning(warningDecoder.getDecodeLog(), "#BMPxxは不十分な定義です");

        Path randomCrash = writeFile(tempDir.resolve("random-crash.bms"), "#RANDOM 2\n#TITLE Crash\n#ENDRANDOM\n");
        BMSDecoder crashDecoder = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE);
        assertNull(crashDecoder.decode(new ChartInformation(randomCrash, BMSModel.LNTYPE_LONGNOTE, new int[0])));
        assertHasError(crashDecoder.getDecodeLog(), "何らかの異常によりBMS解析に失敗しました");
    }

    @Test
    void bmsonDecoderCoversNegativeEventsLayerFallbackAndCollisions() throws Exception {
        Path chart = writeFile(tempDir.resolve("stress.bmson"), "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"Stress BMSON\",\n"
                + "    \"artist\": \"Artist\",\n"
                + "    \"subartists\": [],\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-10k\",\n"
                + "    \"level\": 6,\n"
                + "    \"init_bpm\": 120,\n"
                + "    \"judge_rank\": 100,\n"
                + "    \"total\": 160,\n"
                + "    \"resolution\": 240,\n"
                + "    \"ln_type\": 3\n"
                + "  },\n"
                + "  \"lines\": [{ \"y\": 0 }, { \"y\": 960 }],\n"
                + "  \"bpm_events\": [{ \"y\": 0, \"bpm\": -1 }, { \"y\": 240, \"bpm\": 150 }],\n"
                + "  \"stop_events\": [{ \"y\": 120, \"duration\": -10 }, { \"y\": 360, \"duration\": 120 }],\n"
                + "  \"sound_channels\": [\n"
                + "    { \"name\": \"bg.wav\", \"notes\": [{ \"x\": 0, \"y\": 0, \"l\": 0, \"c\": false }] },\n"
                + "    { \"name\": \"keys.wav\", \"notes\": [\n"
                + "      { \"x\": 1, \"y\": 0, \"l\": 0, \"c\": false },\n"
                + "      { \"x\": 1, \"y\": 0, \"l\": 0, \"c\": false },\n"
                + "      { \"x\": 2, \"y\": 240, \"l\": 480, \"c\": false, \"t\": 3 },\n"
                + "      { \"x\": 2, \"y\": 480, \"l\": 0, \"c\": false },\n"
                + "      { \"x\": 2, \"y\": 720, \"up\": true, \"l\": 0, \"c\": false }\n"
                + "    ] }\n"
                + "  ],\n"
                + "  \"key_channels\": [\n"
                + "    { \"name\": \"hidden.wav\", \"notes\": [{ \"x\": 3, \"y\": 0, \"damage\": 1 }] }\n"
                + "  ],\n"
                + "  \"mine_channels\": [\n"
                + "    { \"name\": \"mine.wav\", \"notes\": [{ \"x\": 2, \"y\": 480, \"damage\": 5 }, { \"x\": 1, \"y\": 0, \"damage\": 3 }] }\n"
                + "  ],\n"
                + "  \"bga\": {\n"
                + "    \"bga_header\": [{ \"id\": 1, \"name\": \"a.png\" }, { \"id\": 2, \"name\": \"b.png\" }],\n"
                + "    \"bga_sequence\": [{ \"id\": 2, \"sequence\": [{ \"time\": 0, \"id\": 1 }, { \"time\": 500 }] }],\n"
                + "    \"bga_events\": [{ \"y\": 0, \"id\": 1 }],\n"
                + "    \"layer_events\": [{ \"y\": 0, \"id_set\": [2], \"condition\": \"other\", \"interval\": 2 }],\n"
                + "    \"poor_events\": [{ \"y\": 240, \"id\": 1 }]\n"
                + "  }\n"
                + "}\n");

        BMSONDecoder decoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        assertEquals(Mode.BEAT_10K, model.getMode());
        assertHasWarning(decoder.getDecodeLog(), "negative BPMはサポートされていません");
        assertHasWarning(decoder.getDecodeLog(), "negative STOPはサポートされていません");
        assertHasWarning(decoder.getDecodeLog(), "LN内にノートを定義しています");
        assertHasWarning(decoder.getDecodeLog(), "LN内に地雷ノートを定義しています");
        assertHasWarning(decoder.getDecodeLog(), "地雷ノートを定義している位置に通常ノートが存在します");

        TimeLine start = findTimeline(model, 0.0);
        TimeLine stop = findTimeline(model, 0.375);
        assertNotNull(start);
        assertNotNull(stop);
        assertEquals(Layer.EventType.ALWAYS, start.getEventlayer()[0].event.type);
        assertEquals(2, start.getEventlayer()[0].event.interval);
        assertEquals(1, start.getNote(0).getLayeredNotes().length);
        assertNotNull(start.getHiddenNote(2));
        assertTrue(stop.getStop() > 0);
    }

    @Test
    void bmsonDecoderCoversChartInformationDefaultsAndMissingCollections() throws Exception {
        Path chart = writeFile(tempDir.resolve("defaults.bmson"), "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"Defaults\",\n"
                + "    \"artist\": \"Artist\",\n"
                + "    \"subartists\": [],\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-5k\",\n"
                + "    \"chart_name\": \"ONLYNAME\",\n"
                + "    \"level\": 4,\n"
                + "    \"init_bpm\": 120,\n"
                + "    \"judge_rank\": -1,\n"
                + "    \"total\": 160,\n"
                + "    \"resolution\": 0,\n"
                + "    \"ln_type\": 0\n"
                + "  },\n"
                + "  \"sound_channels\": [\n"
                + "    { \"name\": \"keys.wav\", \"notes\": [\n"
                + "      { \"x\": 1, \"y\": 0, \"l\": 480, \"c\": false },\n"
                + "      { \"x\": 1, \"y\": 480, \"up\": true, \"c\": true }\n"
                + "    ] }\n"
                + "  ]\n"
                + "}\n");

        BMSONDecoder decoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel model = decoder.decode(new ChartInformation(chart, BMSModel.LNTYPE_HELLCHARGENOTE, new int[] { 1 }));

        assertNotNull(model);
        assertEquals("[ONLYNAME]", model.getSubTitle());
        assertEquals(Mode.BEAT_5K, model.getMode());
        assertEquals(BMSModel.LNTYPE_HELLCHARGENOTE, model.getLntype());
        assertHasWarning(decoder.getDecodeLog(), "judge_rankが0以下です");
        assertInstanceOf(LongNote.class, findTimeline(model, 0.0).getNote(0));
    }

    @Test
    void bmsonDecoderCoversLongNoteLayeringAndMissSequences() throws Exception {
        Path chart = writeFile(tempDir.resolve("layered.bmson"), "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"Layered\",\n"
                + "    \"artist\": \"Artist\",\n"
                + "    \"subartists\": [],\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-7k\",\n"
                + "    \"level\": 4,\n"
                + "    \"init_bpm\": 120,\n"
                + "    \"judge_rank\": 100,\n"
                + "    \"total\": 160,\n"
                + "    \"resolution\": 240,\n"
                + "    \"ln_type\": 1\n"
                + "  },\n"
                + "  \"sound_channels\": [\n"
                + "    { \"name\": \"keys.wav\", \"notes\": [\n"
                + "      { \"x\": 1, \"y\": 0, \"l\": 480, \"c\": false, \"t\": 1 },\n"
                + "      { \"x\": 1, \"y\": 0, \"l\": 480, \"c\": false, \"t\": 1 }\n"
                + "    ] }\n"
                + "  ],\n"
                + "  \"bga\": {\n"
                + "    \"bga_header\": [{ \"id\": 1, \"name\": \"a.png\" }, { \"id\": 2, \"name\": \"b.png\" }],\n"
                + "    \"bga_sequence\": [{ \"id\": 2, \"sequence\": [{ \"time\": 0, \"id\": 1 }, { \"time\": 500 }] }],\n"
                + "    \"layer_events\": [{ \"y\": 0, \"id_set\": [2], \"condition\": \"miss\", \"interval\": 3 }],\n"
                + "    \"poor_events\": [{ \"y\": 240, \"id\": 2 }]\n"
                + "  }\n"
                + "}\n");

        BMSONDecoder decoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel model = decoder.decode(chart);

        assertNotNull(model);
        TimeLine start = findTimeline(model, 0.0);
        TimeLine poor = findTimeline(model, 0.25);
        assertNotNull(start);
        assertNotNull(poor);
        assertInstanceOf(LongNote.class, start.getNote(0));
        assertEquals(1, start.getNote(0).getLayeredNotes().length);
        assertEquals(Layer.EventType.MISS, start.getEventlayer()[0].event.type);
        assertEquals(3, start.getEventlayer()[0].event.interval);
        assertEquals(2, poor.getEventlayer()[0].sequence[0].length);
    }

    @Test
    void bmsonDecoderCoversNullArraysDeferredLnEndAndCollisionBranches() throws Exception {
        Path nullArrays = writeFile(tempDir.resolve("null-arrays.bmson"), "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"Null Arrays\",\n"
                + "    \"artist\": \"Artist\",\n"
                + "    \"subartists\": [],\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-7k\",\n"
                + "    \"level\": 1,\n"
                + "    \"init_bpm\": 120,\n"
                + "    \"judge_rank\": 100,\n"
                + "    \"total\": 100\n"
                + "  },\n"
                + "  \"bpm_events\": null,\n"
                + "  \"stop_events\": null,\n"
                + "  \"scroll_events\": null,\n"
                + "  \"sound_channels\": [{ \"name\": \"a.wav\", \"notes\": [{ \"x\": 1, \"y\": 0, \"l\": 0, \"c\": false }] }]\n"
                + "}\n");
        BMSONDecoder nullDecoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);
        assertNotNull(nullDecoder.decode(nullArrays));

        Path edgeChart = writeFile(tempDir.resolve("bmson-edges.bmson"), "{\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"info\": {\n"
                + "    \"title\": \"Edges\",\n"
                + "    \"artist\": \"Artist\",\n"
                + "    \"subartists\": [],\n"
                + "    \"genre\": \"TEST\",\n"
                + "    \"mode_hint\": \"beat-7k\",\n"
                + "    \"level\": 4,\n"
                + "    \"init_bpm\": 120,\n"
                + "    \"judge_rank\": 100,\n"
                + "    \"total\": 160,\n"
                + "    \"resolution\": 240,\n"
                + "    \"ln_type\": 1\n"
                + "  },\n"
                + "  \"sound_channels\": [\n"
                + "    { \"name\": \"base.wav\", \"notes\": [\n"
                + "      { \"x\": 2, \"y\": 0, \"l\": 120, \"c\": false, \"t\": 1 },\n"
                + "      { \"x\": 3, \"y\": 0, \"l\": 240, \"c\": false, \"t\": 1 }\n"
                + "    ] },\n"
                + "    { \"name\": \"normalmid.wav\", \"notes\": [{ \"x\": 1, \"y\": 480, \"l\": 0, \"c\": false }] },\n"
                + "    { \"name\": \"upstore.wav\", \"notes\": [{ \"x\": 2, \"y\": 480, \"up\": true, \"c\": false }] },\n"
                + "    { \"name\": \"longconflict.wav\", \"notes\": [{ \"x\": 1, \"y\": 240, \"l\": 480, \"c\": false, \"t\": 1 }] },\n"
                + "    { \"name\": \"longreuse.wav\", \"notes\": [{ \"x\": 2, \"y\": 240, \"l\": 240, \"c\": false, \"t\": 1 }] },\n"
                + "    { \"name\": \"longwarn.wav\", \"notes\": [{ \"x\": 2, \"y\": 240, \"l\": 480, \"c\": false, \"t\": 1 }] },\n"
                + "    { \"name\": \"normalonln.wav\", \"notes\": [{ \"x\": 2, \"y\": 240, \"l\": 0, \"c\": false }] },\n"
                + "    { \"name\": \"minebase.wav\", \"notes\": [{ \"x\": 4, \"y\": 0, \"l\": 240, \"c\": false, \"t\": 1 }] }\n"
                + "  ],\n"
                + "  \"mine_channels\": [{ \"name\": \"mine.wav\", \"notes\": [{ \"x\": 4, \"y\": 480, \"damage\": 5 }] }]\n"
                + "}\n");
        BMSONDecoder edgeDecoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);
        BMSModel edgeModel = edgeDecoder.decode(edgeChart);
        assertNotNull(edgeModel);
        assertHasWarning(edgeDecoder.getDecodeLog(), "LN内にノートを定義しています");
        assertHasWarning(edgeDecoder.getDecodeLog(), "同一の位置にノートが複数定義されています");
        TimeLine reused = findTimeline(edgeModel, 0.25);
        assertNotNull(reused);
    }

    @Test
    void bmsonDecoderReturnsNullOnInvalidJson() throws Exception {
        Path invalid = writeFile(tempDir.resolve("invalid-json.bmson"), "{ invalid");
        BMSONDecoder decoder = new BMSONDecoder(BMSModel.LNTYPE_LONGNOTE);
        assertNull(decoder.decode(invalid));
    }

    @Test
    void intervalCoverageMergesOutOfOrderIntervalsAndSupportsStabbingQueries() {
        IntervalCoverage coverage = new IntervalCoverage();
        coverage.add(4.0, 6.0);
        coverage.add(1.0, 2.0);
        coverage.add(2.0, 4.5);
        coverage.add(9.0, 8.0);

        assertTrue(coverage.contains(1.5));
        assertTrue(coverage.contains(5.5));
        assertTrue(coverage.contains(8.5));
        assertFalse(coverage.contains(7.0));
        assertFalse(coverage.contains(10.0));
    }

    private TimeLine findTimeline(BMSModel model, double section) {
        return Arrays.stream(model.getAllTimeLines())
                .filter(tl -> Math.abs(tl.getSection() - section) < 0.0001)
                .findFirst()
                .orElse(null);
    }

    private void assertWarning(DecodeLog log, String fragment) {
        assertNotNull(log);
        assertEquals(DecodeLog.State.WARNING, log.getState());
        assertTrue(log.getMessage().contains(fragment), log.getMessage());
    }

    private void assertHasWarning(List<DecodeLog> logs, String fragment) {
        assertTrue(logs.stream().anyMatch(log -> log.getState() == DecodeLog.State.WARNING && log.getMessage().contains(fragment)),
                fragment + " logs=" + logs.stream().map(DecodeLog::getMessage).toList());
    }

    private void assertHasWarning(DecodeLog[] logs, String fragment) {
        assertTrue(Arrays.stream(logs).anyMatch(log -> log.getState() == DecodeLog.State.WARNING && log.getMessage().contains(fragment)),
                fragment + " logs=" + Arrays.stream(logs).map(DecodeLog::getMessage).toList());
    }

    private void assertHasError(DecodeLog[] logs, String fragment) {
        assertTrue(Arrays.stream(logs).anyMatch(log -> log.getState() == DecodeLog.State.ERROR && log.getMessage().contains(fragment)),
                fragment + " logs=" + Arrays.stream(logs).map(DecodeLog::getMessage).toList());
    }

    private Path writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private int[] identityMap(int size) {
        int[] values = new int[size];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        return values;
    }

    private static final class TestChartDecoder extends ChartDecoder {
        private ChartInformation lastInfo;

        @Override
        public BMSModel decode(ChartInformation info) {
            lastInfo = info;
            return new BMSModel();
        }

        void exposePrintLog(Path path) {
            printLog(path);
        }
    }
}
