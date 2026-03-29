package bms.player.beatoraja.song;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

import bms.player.beatoraja.Validatable;
import bms.player.beatoraja.SQLiteDatabaseAccessor;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import bms.model.BMSModel;

import org.sqlite.SQLiteConfig.SynchronousMode;

/**
 * 楽曲情報データベースへのアクセスクラス
 * 
 * @author exch
 */
public class SongInformationAccessor extends SQLiteDatabaseAccessor {

	private final SQLiteDataSource ds;

	private final ResultSetHandler<List<SongInformation>> songhandler = new BeanListHandler<SongInformation>(
			SongInformation.class);

	private final QueryRunner qr;

	private Connection conn;

	public SongInformationAccessor(String filepath) throws ClassNotFoundException {
		super(new Table("information", 
				new Column("sha256", "TEXT",1,1),
				new Column("n", "INTEGER"),
				new Column("ln", "INTEGER"),
				new Column("s", "INTEGER"),
				new Column("ls", "INTEGER"),
				new Column("total", "REAL"),
				new Column("density", "REAL"),
				new Column("peakdensity", "REAL"),
				new Column("enddensity", "REAL"),
				new Column("mainbpm", "REAL"),
				new Column("distribution", "TEXT"),
				new Column("speedchange", "TEXT"),
				new Column("lanenotes", "TEXT")
				));
		Class.forName("org.sqlite.JDBC");
		SQLiteConfig conf = new SQLiteConfig();
		conf.setSharedCache(true);
		conf.setSynchronous(SynchronousMode.OFF);
		// conf.setJournalMode(JournalMode.MEMORY);
		ds = new SQLiteDataSource(conf);
		ds.setUrl("jdbc:sqlite:" + filepath);
		qr = new QueryRunner(ds);
		try {
			validate(qr);
		} catch (SQLException e) {
			Logger.getGlobal().severe("楽曲データベース初期化中の例外:" + e.getMessage());
		}
	}

	public SongInformation[] getInformations(String sql) {
		try {
			List<SongInformation> m = Validatable.removeInvalidElements(qr.query("SELECT * FROM information WHERE " + sql, songhandler));
			return m.toArray(new SongInformation[m.size()]);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return new SongInformation[0];		
	}

	public SongInformation getInformation(String sha256) {
		try {
			List<SongInformation> m = Validatable.removeInvalidElements(qr.query("SELECT * FROM information WHERE sha256 = ?", songhandler, sha256));
			if(m.size() > 0) {
				return m.get(0);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void getInformation(SongData[] songs) {
		try {
			// SQLite limits IN clause to ~999 terms; batch to stay within limit
			final int BATCH_SIZE = 500;
			java.util.List<SongInformation> allInfos = new java.util.ArrayList<>();
			for (int start = 0; start < songs.length; start += BATCH_SIZE) {
				int end = Math.min(start + BATCH_SIZE, songs.length);
				StringBuilder str = new StringBuilder((end - start) * 66);
				for (int i = start; i < end; i++) {
					if (songs[i].getSha256() != null) {
						if (str.length() > 0) {
							str.append(',');
						}
						str.append('\'').append(songs[i].getSha256()).append('\'');
					}
				}
				if (str.length() == 0) continue;
				allInfos.addAll(Validatable.removeInvalidElements(qr
						.query("SELECT * FROM information WHERE sha256 IN (" + str.toString() + ")", songhandler)));
			}
			for (SongData song : songs) {
				for (SongInformation info : allInfos) {
					if (info.getSha256().equals(song.getSha256())) {
						song.setInformation(info);
						break;
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void startUpdate() {
		try {
			conn = ds.getConnection();
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			}
			conn = null;
		}
	}

	public void update(BMSModel model) {
		update(new SongInformation(model));
	}

	public void update(SongInformation info) {
		try {
			insert(qr, conn, "information", info);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void endUpdate() {
		if (conn != null) {
			try {
				conn.commit();
				conn.close();
			} catch (SQLException e) {
				if (conn != null) {
					try {
						conn.close();
					} catch (SQLException e1) {
						e1.printStackTrace();
					}
				}
				conn = null;
			}
		}
	}

	public void rollbackUpdate() {
		if (conn != null) {
			try {
				conn.rollback();
				conn.close();
			} catch (SQLException e) {
				if (conn != null) {
					try {
						conn.close();
					} catch (SQLException e1) {
						e1.printStackTrace();
					}
				}
			} finally {
				conn = null;
			}
		}
	}

}
