package bms.player.beatoraja.select.bar;

import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.song.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ファイルシステムと連動したフォルダバー。
 *
 * @author exch
 */
public class FolderBar extends DirectoryBar {

    private final FolderData folder;
    private final String crc;

    public FolderBar(MusicSelector selector, FolderData folder, String crc) {
        super(selector);
        this.folder = folder;
        this.crc = crc;
    }

    public final FolderData getFolderData() {
        return folder;
    }

    public final String getCRC() {
        return crc;
    }

    @Override
    public final String getTitle() {
        return folder.getTitle();
    }

    @Override
    public Bar[] getChildren() {
        final SongDatabaseAccessor songdb = selector.getSongDatabase();
        final String rootpath = Paths.get(".").toAbsolutePath().toString();
        List<Bar> children = new ArrayList<>();

        Stream.of(songdb.getFolderDatas("parent", crc)).map(folder -> {
            String path = folder.getPath();
            if (path.endsWith(String.valueOf(File.separatorChar))) {
                path = path.substring(0, path.length() - 1);
            }

            String ccrc = SongUtils.crc32(path, new String[0], rootpath);
            return new FolderBar(selector, folder, ccrc);
        }).forEach(children::add);

        for (SongBar songBar : SongBar.toSongBarArray(songdb.getSongDatas("parent", crc))) {
            children.add(songBar);
        }

        return children.toArray(Bar[]::new);
    }

    public void updateFolderStatus() {
        SongDatabaseAccessor songdb = selector.getSongDatabase();
        String path = folder.getPath();
        if (path.endsWith(String.valueOf(File.separatorChar))) {
            path = path.substring(0, path.length() - 1);
        }
        final String ccrc = SongUtils.crc32(path, new String[0], new File(".").getAbsolutePath());
        updateFolderStatus(collectSongs(songdb, ccrc, new HashSet<>(), new ArrayList<>()).toArray(SongData.EMPTY));
    }

    private List<SongData> collectSongs(SongDatabaseAccessor songdb, String currentCrc, Set<String> visitedFolders,
            List<SongData> songs) {
        if (!visitedFolders.add(currentCrc)) {
            return songs;
        }

        for (SongData song : songdb.getSongDatas("parent", currentCrc)) {
            songs.add(song);
        }

        final String rootpath = Paths.get(".").toAbsolutePath().toString();
        for (FolderData child : songdb.getFolderDatas("parent", currentCrc)) {
            String childPath = child.getPath();
            if (childPath.endsWith(String.valueOf(File.separatorChar))) {
                childPath = childPath.substring(0, childPath.length() - 1);
            }
            collectSongs(songdb, SongUtils.crc32(childPath, new String[0], rootpath), visitedFolders, songs);
        }
        return songs;
    }
}
