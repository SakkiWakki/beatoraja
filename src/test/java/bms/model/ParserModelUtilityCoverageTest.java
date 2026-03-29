package bms.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class ParserModelUtilityCoverageTest {

    @Test
    void modelUtilitiesAndTraversalWorkOnConstructedModel() {
        BMSModel model = createModel();

        assertEquals(Mode.BEAT_7K, model.getMode());
        assertEquals(4, model.getTotalNotes());
        assertTrue(model.containsLongNote());
        assertTrue(model.containsMineNote());
        assertFalse(model.containsUndefinedLongNote());

        assertEquals(4, BMSModelUtils.getTotalNotes(model));
        assertEquals(1, BMSModelUtils.getTotalNotes(model, BMSModelUtils.TOTALNOTES_KEY));
        assertEquals(2, BMSModelUtils.getTotalNotes(model, BMSModelUtils.TOTALNOTES_LONG_KEY));
        assertEquals(1, BMSModelUtils.getTotalNotes(model, BMSModelUtils.TOTALNOTES_SCRATCH));
        assertEquals(1, BMSModelUtils.getTotalNotes(model, BMSModelUtils.TOTALNOTES_MINE));
        assertEquals(4, BMSModelUtils.getTotalNotes(model, 0, Integer.MAX_VALUE, BMSModelUtils.TOTALNOTES_ALL, 1));
        assertEquals(0, BMSModelUtils.getTotalNotes(model, 0, Integer.MAX_VALUE, BMSModelUtils.TOTALNOTES_ALL, 2));
        assertTrue(BMSModelUtils.getMaxNotesPerTime(model, 2000) >= 1.0);

        Lane lane0 = model.getLanes()[0];
        assertEquals(1, lane0.getNotes().length);
        assertNotNull(lane0.getNote());
        assertNull(lane0.getNote());
        lane0.reset();
        lane0.mark(1);
        assertNotNull(lane0.getNote());

        Lane lane1 = model.getLanes()[1];
        assertEquals(1, lane1.getHiddens().length);
        assertNotNull(lane1.getHidden());
        assertNull(lane1.getHidden());
        lane1.reset();
        lane1.mark(1);
        assertNotNull(lane1.getHidden());

        EventLane eventLane = model.getEventLane();
        assertTrue(eventLane.getSections().length >= 1);
        assertTrue(eventLane.getBpmChanges().length >= 1);
        assertTrue(eventLane.getStops().length >= 1);
        assertNotNull(eventLane.getSection());
        assertNotNull(eventLane.getBpm());
        assertNotNull(eventLane.getStop());
        eventLane.reset();
        eventLane.mark(1);
        assertNotNull(eventLane.getSection());

        long margin = BMSModelUtils.setStartNoteTime(model, 1000);
        assertTrue(margin > 0);
        assertEquals(0, model.getAllTimeLines()[0].getMilliTime());

        BMSModelUtils.changeFrequency(model, 2.0f);
        assertEquals(240.0, model.getBpm());
    }

    @Test
    void repeatedModelSummariesRemainStableAcrossCallsAndTimelineChanges() {
        BMSModel model = createModel();

        int totalNotes = model.getTotalNotes();
        String chartString = model.toChartString();

        assertEquals(totalNotes, model.getTotalNotes());
        assertEquals(chartString, model.toChartString());

        TimeLine extra = new TimeLine(3.0, 3_000_000, model.getMode().key);
        extra.setBPM(120);
        extra.setNote(0, new NormalNote(9));
        TimeLine[] timelines = Arrays.copyOf(model.getAllTimeLines(), model.getAllTimeLines().length + 1);
        timelines[timelines.length - 1] = extra;
        model.setAllTimeLine(timelines);

        assertEquals(totalNotes + 1, model.getTotalNotes());
        assertTrue(model.toChartString().length() > chartString.length());
    }

    @Test
    void chartDigestMatchesChartStringDigest() throws Exception {
        BMSModel model = createModel();

        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        MessageDigest actual = MessageDigest.getInstance("SHA-256");

        byte[] expectedDigest = expected.digest(model.toChartString().getBytes(StandardCharsets.UTF_8));
        model.updateChartDigest(actual);

        assertArrayEquals(expectedDigest, actual.digest());
    }

    @Test
    void noteLayeringAndCloningBehaveAsExpected() {
        NormalNote note = new NormalNote(1, 2_000, 3_000);
        note.setState(4);
        note.setPlayTime(5);
        note.setSection(1.5);
        note.setMicroTime(7_000);
        note.addLayeredNote(new NormalNote(2));

        assertEquals(1, note.getWav());
        assertEquals(4, note.getState());
        assertEquals(2, note.getMilliStarttime());
        assertEquals(3, note.getMilliDuration());
        assertEquals(5, note.getPlayTime());
        assertEquals(7, note.getMilliTime());
        assertEquals(1.5, note.getSection());
        assertEquals(1, note.getLayeredNotes().length);

        MineNote mine = new MineNote(3, 8.5);
        assertEquals(8.5, mine.getDamage());

        LongNote start = new LongNote(4);
        LongNote end = new LongNote(-2);
        start.setSection(1.0);
        end.setSection(2.0);
        start.setType(LongNote.TYPE_CHARGENOTE);
        start.setPair(end);

        assertFalse(start.isEnd());
        assertTrue(end.isEnd());
        assertEquals(start, end.getPair());
        assertEquals(LongNote.TYPE_CHARGENOTE, end.getType());
        assertNotNull(start.clone());
    }

    @Test
    void generatorAndLayerHelpersWork() {
        byte[] chart = ("#TITLE Generated\n"
                + "#BPM 120\n"
                + "#TOTAL 100\n"
                + "#WAV01 sample.wav\n"
                + "#00111:0100\n").getBytes(StandardCharsets.UTF_8);
        BMSGenerator generator = new BMSGenerator(chart, false, new int[] { 1 });

        BMSModel model = generator.generate(new int[] { 1 });

        assertNotNull(model);
        assertArrayEquals(new int[] { 1 }, generator.getRandom());

        Layer.Sequence end = new Layer.Sequence(100);
        Layer.Sequence value = new Layer.Sequence(200, 3);
        Layer layer = new Layer(new Layer.Event(Layer.EventType.PLAY, 2), new Layer.Sequence[][] { { value, end } });

        assertTrue(end.isEnd());
        assertFalse(value.isEnd());
        assertEquals(Layer.EventType.PLAY, layer.event.type);
        assertEquals(2, layer.event.interval);
        assertEquals(3, layer.sequence[0][0].id);
    }

    private BMSModel createModel() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setLnmode(LongNote.TYPE_CHARGENOTE);
        model.setChartInformation(new ChartInformation(null, BMSModel.LNTYPE_CHARGENOTE, new int[] { 1 }));

        TimeLine tl0 = new TimeLine(0.0, 0, model.getMode().key);
        tl0.setSectionLine(true);
        tl0.setBPM(120);
        tl0.setNote(0, new NormalNote(1));
        tl0.setNote(7, new NormalNote(2));
        tl0.setHiddenNote(1, new NormalNote(3));
        tl0.addBackGroundNote(new NormalNote(4));

        TimeLine tl1 = new TimeLine(0.5, 500_000, model.getMode().key);
        tl1.setBPM(150);
        LongNote start = new LongNote(5);
        start.setType(LongNote.TYPE_CHARGENOTE);
        tl1.setNote(2, start);

        TimeLine tl2 = new TimeLine(1.0, 1_000_000, model.getMode().key);
        tl2.setBPM(150);
        tl2.setStop(250_000);
        LongNote end = new LongNote(-2);
        tl2.setNote(2, end);
        start.setPair(end);
        tl2.setNote(3, new MineNote(6, 4.0));

        model.setAllTimeLine(new TimeLine[] { tl0, tl1, tl2 });
        return model;
    }
}
