package bms.player.beatoraja.song;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import bms.model.BMSDecoder;
import bms.model.BMSModel;
import bms.model.ChartInformation;
import bms.model.LongNote;
import bms.model.MineNote;
import bms.model.Mode;
import bms.model.NormalNote;
import bms.model.TimeLine;

class SongInformationAccessorTest {

    @TempDir
    Path tempDir;

    @Test
    void supportsGetInformationQueriesAndSongAttachment() throws Exception {
        Path chart = createChart(tempDir.resolve("songs").resolve("pack").resolve("info.bms"), "Info Song");
        BMSModel model = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(chart);
        SongInformation expected = new SongInformation(model);

        SongInformationAccessor accessor = new SongInformationAccessor(tempDir.resolve("songinfo.db").toString());
        accessor.startUpdate();
        accessor.update(expected);
        accessor.endUpdate();

        SongInformation[] queried = accessor.getInformations("sha256 = '" + expected.getSha256() + "'");
        assertEquals(1, queried.length);
        assertEquals(expected.getSha256(), queried[0].getSha256());

        SongInformation loaded = accessor.getInformation(expected.getSha256());
        assertNotNull(loaded);
        assertEquals(expected.getMainbpm(), loaded.getMainbpm());
        assertNull(accessor.getInformation("missing"));

        SongData song = new SongData(model, false);
        accessor.getInformation(new SongData[] { song });
        assertNotNull(song.getInformation());
        assertEquals(expected.getSha256(), song.getInformation().getSha256());
    }

    @Test
    void rollbackUpdateDiscardsPendingWrites() throws Exception {
        Path chart = createChart(tempDir.resolve("songs").resolve("pack").resolve("rollback.bms"), "Rollback Song");
        BMSModel model = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(chart);

        SongInformationAccessor accessor = new SongInformationAccessor(tempDir.resolve("songinfo.db").toString());
        accessor.startUpdate();
        accessor.update(model);
        accessor.rollbackUpdate();

        assertNull(accessor.getInformation(model.getSHA256()));
    }

    @Test
    void computesDerivedStatisticsConsistentlyForMixedTimelineContent() {
        BMSModel model = new BMSModel();
        model.setMode(Mode.BEAT_7K);
        model.setBpm(120);
        model.setTotal(200);
        model.setLnmode(LongNote.TYPE_LONGNOTE);
        model.setChartInformation(new ChartInformation(null, BMSModel.LNTYPE_LONGNOTE, null));
        model.setSHA256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        TimeLine tl0 = new TimeLine(0.0, 0, model.getMode().key);
        tl0.setBPM(120);
        tl0.setNote(0, new NormalNote(1));
        tl0.setNote(7, new NormalNote(2));

        TimeLine tl1 = new TimeLine(1.0, 1_000_000, model.getMode().key);
        tl1.setBPM(150);
        LongNote lnStart = new LongNote(3);
        lnStart.setType(LongNote.TYPE_LONGNOTE);
        tl1.setNote(1, lnStart);
        tl1.setNote(2, new MineNote(4, 1.5));

        TimeLine tl2 = new TimeLine(2.0, 2_000_000, model.getMode().key);
        tl2.setBPM(150);
        tl2.setStop(500_000);
        LongNote lnEnd = new LongNote(-2);
        tl2.setNote(1, lnEnd);
        lnStart.setPair(lnEnd);

        model.setAllTimeLine(new TimeLine[] { tl0, tl1, tl2 });

        SongInformation info = new SongInformation(model);

        assertEquals(1, info.getN());
        assertEquals(1, info.getLn());
        assertEquals(1, info.getS());
        assertEquals(0, info.getLs());
        assertEquals(1.0, info.getDensity());
        assertEquals(2.0, info.getPeakdensity());
        assertEquals(4.0 / 3.0, info.getEnddensity());
        assertEquals(120.0, info.getMainbpm());
        assertArrayEquals(new int[][] {
                {0, 0, 1, 0, 0, 1, 0},
                {0, 0, 0, 1, 0, 0, 1},
                {0, 0, 0, 0, 1, 0, 0},
                {0, 0, 0, 0, 0, 0, 0}
        }, info.getDistributionValues());
        assertArrayEquals(new double[][] {
                {120.0, 0.0},
                {150.0, 1000.0},
                {0.0, 2000.0}
        }, info.getSpeedchangeValues());
        assertArrayEquals(new int[][] {
                {1, 0, 0},
                {0, 1, 0},
                {0, 0, 1},
                {0, 0, 0},
                {0, 0, 0},
                {0, 0, 0},
                {0, 0, 0},
                {1, 0, 0}
        }, info.getLanenotesValues());
    }

    @Test
    void getInformationBatchesLargeArraysWithoutSqlError() throws Exception {
        // Regression: getInformation(SongData[]) with >999 songs would exceed SQLite's
        // IN clause parameter limit, causing a SQL error. Now batched at 500.
        SongInformationAccessor accessor = new SongInformationAccessor(tempDir.resolve("songinfo.db").toString());
        accessor.startUpdate();

        // Insert a known info entry
        Path chart = createChart(tempDir.resolve("songs").resolve("pack").resolve("batch.bms"), "Batch Song");
        BMSModel model = new BMSDecoder(BMSModel.LNTYPE_LONGNOTE).decode(chart);
        SongInformation info = new SongInformation(model);
        accessor.update(info);
        accessor.endUpdate();

        // Create an array with >1000 SongData entries, one of which matches
        SongData[] songs = new SongData[1200];
        for (int i = 0; i < songs.length; i++) {
            songs[i] = new SongData();
            songs[i].setSha256(String.format("%064x", i));
        }
        // Place the real one somewhere past the first batch boundary
        songs[600] = new SongData(model, false);

        // This should not throw a SQL exception
        accessor.getInformation(songs);
        assertNotNull(songs[600].getInformation(), "should find info for the matching song in a large batch");
    }

    private Path createChart(Path path, String title) throws Exception {
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, ""
                + "#PLAYER 1\n"
                + "#GENRE TEST\n"
                + "#TITLE " + title + "\n"
                + "#ARTIST Test Artist\n"
                + "#BPM 130\n"
                + "#PLAYLEVEL 3\n"
                + "#RANK 2\n"
                + "#TOTAL 100\n"
                + "#WAV01 sample.wav\n"
                + "#00111:0100\n");
        return path;
    }
}
