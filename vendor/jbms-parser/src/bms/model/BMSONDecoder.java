package bms.model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

import static bms.model.DecodeLog.State.*;

import bms.model.Layer.EventType;
import bms.model.bmson.*;
import bms.model.bmson.Note;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * bmsonデコーダー
 * 
 * @author exch
 */
public class BMSONDecoder extends ChartDecoder {

	private final ObjectMapper mapper = new ObjectMapper();

	private BMSModel model;
	private int[] timelineTicks = new int[0];
	private TimeLineCache[] timelineCaches = new TimeLineCache[0];
	private int recentY = Integer.MIN_VALUE;
	private TimeLineCache recentTimelineCache;

	public BMSONDecoder(int lntype) {
		this.lntype = lntype;
	}

	public BMSModel decode(ChartInformation info) {
		this.lntype = info.lntype;
		return decode(info.path);
	}

	@SuppressWarnings("unchecked")
	public BMSModel decode(Path f) {
		Logger.getGlobal().fine("BMSONファイル解析開始 :" + f.toString());
		log.clear();
		timelineTicks = new int[0];
		timelineCaches = new TimeLineCache[0];
		recentY = Integer.MIN_VALUE;
		recentTimelineCache = null;
		final long currnttime = System.currentTimeMillis();
		// BMS読み込み、ハッシュ値取得
		model = new BMSModel();
		Bmson bmson = null;
		try {
			byte[] data = Files.readAllBytes(f);
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(data);
			bmson = mapper.readValue(data, Bmson.class);
			model.setSHA256(BMSDecoder.convertHexString(digest.digest()));
		} catch (NoSuchAlgorithmException | IOException e) {
			e.printStackTrace();
			return null;
		}
		
		model.setTitle(bmson.info.title);
		model.setSubTitle((bmson.info.subtitle != null ? bmson.info.subtitle : "")
				+ (bmson.info.subtitle != null && bmson.info.subtitle.length() > 0 && bmson.info.chart_name != null
						&& bmson.info.chart_name.length() > 0 ? " " : "")
				+ (bmson.info.chart_name != null && bmson.info.chart_name.length() > 0
						? "[" + bmson.info.chart_name + "]" : ""));
		model.setArtist(bmson.info.artist);
		StringBuilder subartist = new StringBuilder();
		for (String s : bmson.info.subartists) {
			subartist.append((subartist.length() > 0 ? "," : "") + s);
		}
		model.setSubArtist(subartist.toString());
		model.setGenre(bmson.info.genre);

		if (bmson.info.judge_rank < 0) {
			log.add(new DecodeLog(WARNING, "judge_rankが0以下です。judge_rank = " + bmson.info.judge_rank));
		} else if (bmson.info.judge_rank < 5) {
			model.setJudgerank(bmson.info.judge_rank);
			log.add(new DecodeLog(WARNING, "judge_rankの定義が仕様通りでない可能性があります。judge_rank = " + bmson.info.judge_rank));
			model.setJudgerankType(BMSModel.JudgeRankType.BMS_RANK);
		} else {
			model.setJudgerank(bmson.info.judge_rank);
			model.setJudgerankType(BMSModel.JudgeRankType.BMSON_JUDGERANK);
		}

		if(bmson.info.total > 0) {
			model.setTotal(bmson.info.total);
			model.setTotalType(BMSModel.TotalType.BMSON);
		} else {
			log.add(new DecodeLog(WARNING, "totalが0以下です。total = " + bmson.info.total));
		}

		model.setBpm(bmson.info.init_bpm);
		model.setPlaylevel(String.valueOf(bmson.info.level));
		final Mode mode = Mode.getMode(bmson.info.mode_hint);
		if(mode != null) {
			model.setMode(mode);			
		} else {
			log.add(new DecodeLog(WARNING, "非対応のmode_hintです。mode_hint = " + bmson.info.mode_hint));
			model.setMode(Mode.BEAT_7K);
		}
		if (bmson.info.ln_type > 0 && bmson.info.ln_type <= 3) {
			model.setLnmode(bmson.info.ln_type);
		}
		final int[] keyassign;
		switch (model.getMode()) {
		case BEAT_5K:
			keyassign = new int[] { 0, 1, 2, 3, 4, -1, -1, 5 };
			break;
		case BEAT_10K:
			keyassign = new int[] { 0, 1, 2, 3, 4, -1, -1, 5, 6, 7, 8, 9, 10, -1, -1, 11 };
			break;
		default:
			keyassign = new int[model.getMode().key];
			for (int i = 0; i < keyassign.length; i++) {
				keyassign[i] = i;
			}
		}
		List<LongNote>[] lnlist = new List[model.getMode().key];
		IntervalCoverage[] completedCoverage = new IntervalCoverage[model.getMode().key];
		for (int lane = 0; lane < completedCoverage.length; lane++) {
			completedCoverage[lane] = new IntervalCoverage();
		}
		Map<Long, LongNote> lnup = new HashMap<Long, LongNote>();

		model.setBanner(bmson.info.banner_image);
		model.setBackbmp(bmson.info.back_image);
		model.setStagefile(bmson.info.eyecatch_image);
		model.setPreview(bmson.info.preview_music);

		if (bmson.bpm_events == null) {
			bmson.bpm_events = new BpmEvent[0];
		}
		if (bmson.stop_events == null) {
			bmson.stop_events = new StopEvent[0];
		}
		if (bmson.scroll_events == null) {
			bmson.scroll_events = new ScrollEvent[0];
		}
		if (bmson.sound_channels == null) {
			bmson.sound_channels = new SoundChannel[0];
		}
		if (bmson.key_channels == null) {
			bmson.key_channels = new MineChannel[0];
		}
		if (bmson.mine_channels == null) {
			bmson.mine_channels = new MineChannel[0];
		}

		final double resolution = bmson.info.resolution > 0 ? bmson.info.resolution * 4 : 960;
		final Comparator<BMSONObject> comparator = (n1,n2) -> (n1.y - n2.y);
		// bpmNotes, stopNotes処理
		Arrays.sort(bmson.bpm_events, comparator);
		Arrays.sort(bmson.stop_events, comparator);
		Arrays.sort(bmson.scroll_events, comparator);
		prepareTimelineIndex(bmson, resolution);
		// lines処理(小節線)
		if (bmson.lines != null) {
			for (BarLine bl : bmson.lines) {
				getTimeLine(bl.y, resolution).setSectionLine(true);
			}
		}

		String[] wavmap = new String[bmson.sound_channels.length + bmson.key_channels.length + bmson.mine_channels.length];
		int id = 0;
		long starttime = 0;
		for (SoundChannel sc : bmson.sound_channels) {
			wavmap[id] = sc.name;
			Arrays.sort(sc.notes, comparator);
			final int length = sc.notes.length;
			final int[] nextDistinctIndices = buildNextDistinctIndices(sc.notes);
			for (int i = 0; i < length; i++) {
				final bms.model.bmson.Note n = sc.notes[i];
				final bms.model.bmson.Note next = nextDistinctIndices[i] >= 0 ? sc.notes[nextDistinctIndices[i]] : null;
				long duration = 0;
				if (!n.c) {
					starttime = 0;
				}
				TimeLine tl = getTimeLine(n.y, resolution);
				if (next != null && next.c) {
					duration = getTimeLine(next.y, resolution).getMicroTime() - tl.getMicroTime();
				}

				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key < 0) {
					// BGノート
					tl.addBackGroundNote(new NormalNote(id, starttime, duration));
				} else if (n.up) {
					// LN終端音定義
					boolean assigned = false;
					if (lnlist[key] != null) {
						final double section = (n.y / resolution);
						for (LongNote ln : lnlist[key]) {
							if (section == ln.getPair().getSection()) {
								ln.getPair().setWav(id);
								ln.getPair().setMicroStarttime(starttime);
								ln.getPair().setMicroDuration(duration);
								assigned = true;
								break;
							}
						}
						if(!assigned) {
							lnup.put(noteKey(n.x, n.y), new LongNote(id, starttime, duration));
						}
					}
				} else {
					boolean insideln = false;
					final double section = (n.y / resolution);
					insideln = completedCoverage[key].containsAfterStart(section);

					if (insideln) {
						log.add(new DecodeLog(WARNING,
								"LN内にノートを定義しています - x :  " + n.x + " y : " + n.y));
						tl.addBackGroundNote(new NormalNote(id, starttime, duration));
					} else {
						if (n.l > 0) {
							// ロングノート
							TimeLine end = getTimeLine(n.y + n.l, resolution);
							LongNote ln = new LongNote(id, starttime, duration);
							if (tl.getNote(key) != null) {
								// レイヤーノート判定
								bms.model.Note en = tl.getNote(key);
								if (en instanceof LongNote && end.getNote(key) == ((LongNote) en).getPair()) {
									en.addLayeredNote(ln);
								} else {
									log.add(new DecodeLog(WARNING,
											"同一の位置にノートが複数定義されています - x :  " + n.x + " y : " + n.y));
								}
							} else {
								boolean existNote = existsNoteInRange(n.y, n.y + n.l, key);
								if (existNote) {
									log.add(new DecodeLog(WARNING,
											"LN内にノートを定義しています - x :  " + n.x + " y : " + n.y));
									tl.addBackGroundNote(new NormalNote(id, starttime, duration));
								} else {
									tl.setNote(key, ln);
									// ln.setDuration(end.getTime() -
									// start.getTime());
									LongNote lnend = lnup.remove(noteKey(n.x, n.y + n.l));
									if(lnend == null) {
										lnend = new LongNote(-2);
									}

									end.setNote(key, lnend);
									ln.setType(n.t > 0 && n.t <= 3 ? n.t : model.getLnmode());
									ln.setPair(lnend);
									if (lnlist[key] == null) {
										lnlist[key] = new ArrayList<LongNote>();
									}
									lnlist[key].add(ln);
									completedCoverage[key].add(ln.getSection(), ln.getPair().getSection());
								}
							}
						} else {
							// 通常ノート
							if (tl.existNote(key)) {
								if (tl.getNote(key) instanceof NormalNote) {
									tl.getNote(key).addLayeredNote(new NormalNote(id, starttime, duration));
								} else {
									log.add(new DecodeLog(WARNING,
											"同一の位置にノートが複数定義されています - x :  " + n.x + " y : " + n.y));
								}
							} else {
								tl.setNote(key, new NormalNote(id, starttime, duration));
							}
						}
					}
				}
				starttime += duration;
			}
			id++;
		}
		
		for (MineChannel sc : bmson.key_channels) {
			wavmap[id] = sc.name;
			Arrays.sort(sc.notes, comparator);
			final int length = sc.notes.length;
			for (int i = 0; i < length; i++) {
				final bms.model.bmson.MineNote n = sc.notes[i];
				TimeLine tl = getTimeLine(n.y, resolution);

				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key >= 0) {
					// BGノート
					tl.setHiddenNote(key, new NormalNote(id));
				}
			}
			id++;
		}
		for (MineChannel sc : bmson.mine_channels) {
			wavmap[id] = sc.name;
			Arrays.sort(sc.notes, comparator);
			final int length = sc.notes.length;
			for (int i = 0; i < length; i++) {
				final bms.model.bmson.MineNote n = sc.notes[i];
				TimeLine tl = getTimeLine(n.y, resolution);

				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key >= 0) {
					boolean insideln = false;
					insideln = completedCoverage[key].containsAfterStart(n.y / resolution);

					if (insideln) {
						log.add(new DecodeLog(WARNING,
								"LN内に地雷ノートを定義しています - x :  " + n.x + " y : " + n.y));
					} else if(tl.existNote(key)){
						log.add(new DecodeLog(WARNING,
								"地雷ノートを定義している位置に通常ノートが存在します - x :  " + n.x + " y : " + n.y));
					} else {
						tl.setNote(key, new MineNote(id, n.damage));						
					}
				}
			}
			id++;
		}

		model.setWavList(wavmap);
		// BGA処理
		if (bmson.bga != null && bmson.bga.bga_header != null) {
			final String[] bgamap = new String[bmson.bga.bga_header.length];
			final Map<Integer, Integer> idmap = new HashMap<Integer, Integer>(bmson.bga.bga_header.length);
			final Map<Integer, Layer.Sequence[]> seqmap = new HashMap<Integer, Layer.Sequence[]>();
			for (int i = 0; i < bmson.bga.bga_header.length; i++) {
				BGAHeader bh = bmson.bga.bga_header[i];
				bgamap[i] = bh.name;
				idmap.put(bh.id, i);
			}
			if (bmson.bga.bga_sequence != null) {
				for (BGASequence n : bmson.bga.bga_sequence) {
					if(n != null) {
						Layer.Sequence[] sequence = new Layer.Sequence[n.sequence.length];
						for(int i =0;i < sequence.length;i++) {
							Sequence seq = n.sequence[i];
							if(seq.id != Integer.MIN_VALUE) {
								sequence[i] = new Layer.Sequence(seq.time, seq.id);
							} else {
								sequence[i] = new Layer.Sequence(seq.time);								
							}
						}
						seqmap.put(n.id, sequence);
					}
				}
			}
			if (bmson.bga.bga_events != null) {
				for (BNote n : bmson.bga.bga_events) {
					getTimeLine(n.y, resolution).setBGA(idmap.get(n.id));
				}
			}
			if (bmson.bga.layer_events != null) {
				for (BNote n : bmson.bga.layer_events) {
					int[] idset = n.id_set != null ? n.id_set : new int[] {n.id};
					Layer.Sequence[][] seqs = new Layer.Sequence[idset.length][];
					Layer.Event event = null;
					switch(n.condition != null ? n.condition : "") {
					case "play":
						event = new Layer.Event(EventType.PLAY, n.interval);
						break;
					case "miss":
						event = new Layer.Event(EventType.MISS, n.interval);
						break;
					default:								
						event = new Layer.Event(EventType.ALWAYS, n.interval);
					}
					for(int seqindex = 0; seqindex < seqs.length;seqindex++) {
						int nid = idset[seqindex];
						if(seqmap.containsKey(nid) ) {
							seqs[seqindex] = seqmap.get(nid);
						} else {
							seqs[seqindex] = new Layer.Sequence[] {new Layer.Sequence(0, idmap.get(n.id)),new Layer.Sequence(500)};
						}						
					}
					getTimeLine(n.y, resolution).setEventlayer(new Layer[] {new Layer(event, seqs)});						
				}
			}
			if (bmson.bga.poor_events != null) {
				for (BNote n : bmson.bga.poor_events) {
					if(seqmap.containsKey(n.id) ) {
						getTimeLine(n.y, resolution).setEventlayer(new Layer[] {new Layer(new Layer.Event(EventType.MISS, 1),
								new Layer.Sequence[][] {seqmap.get(n.id)})});						
					} else {
						getTimeLine(n.y, resolution).setEventlayer(new Layer[] {new Layer(new Layer.Event(EventType.MISS, 1),
								new Layer.Sequence[][] {{new Layer.Sequence(0, idmap.get(n.id)),new Layer.Sequence(500)}})});
					}
				}
			}
			model.setBgaList(bgamap);
		}
		TimeLine[] timelines = new TimeLine[timelineCaches.length];
		for (int i = 0; i < timelineCaches.length; i++) {
			timelines[i] = timelineCaches[i].timeline;
		}
		model.setAllTimeLine(timelines);

		Logger.getGlobal().fine("BMSONファイル解析完了 :" + f.toString() + " - TimeLine数:" + timelineCaches.length + " 時間(ms):"
				+ (System.currentTimeMillis() - currnttime));
		
		model.setChartInformation(new ChartInformation(f, lntype, null));
		printLog(f);
		return model;
	}
	
	private TimeLine getTimeLine(int y, double resolution) {
		if (recentTimelineCache != null && recentY == y) {
			return recentTimelineCache.timeline;
		}
		final int index = Arrays.binarySearch(timelineTicks, y);
		if (index >= 0) {
			recentY = y;
			recentTimelineCache = timelineCaches[index];
			return timelineCaches[index].timeline;
		}
		throw new IllegalStateException("Timeline tick was not precomputed: " + y);
	}

	private static int[] buildNextDistinctIndices(bms.model.bmson.Note[] notes) {
		final int[] nextDistinctIndices = new int[notes.length];
		int nextDistinctIndex = -1;
		for (int i = notes.length - 1; i >= 0; i--) {
			nextDistinctIndices[i] = nextDistinctIndex;
			if (i == 0 || notes[i - 1].y < notes[i].y) {
				nextDistinctIndex = i;
			}
		}
		return nextDistinctIndices;
	}

	private static long noteKey(int x, int y) {
		return (((long) x) << 32) | (y & 0xffffffffL);
	}

	private void prepareTimelineIndex(Bmson bmson, double resolution) {
		final IntAccumulator accumulator = new IntAccumulator(256);
		accumulator.add(0);
		for (BpmEvent event : bmson.bpm_events) {
			accumulator.add(event.y);
		}
		for (StopEvent event : bmson.stop_events) {
			accumulator.add(event.y);
		}
		for (ScrollEvent event : bmson.scroll_events) {
			accumulator.add(event.y);
		}
		if (bmson.lines != null) {
			for (BarLine line : bmson.lines) {
				accumulator.add(line.y);
			}
		}
		for (SoundChannel channel : bmson.sound_channels) {
			if (channel.notes != null) {
				for (bms.model.bmson.Note note : channel.notes) {
					accumulator.add(note.y);
					if (note.l > 0) {
						accumulator.add(note.y + note.l);
					}
				}
			}
		}
		for (MineChannel channel : bmson.key_channels) {
			if (channel.notes != null) {
				for (bms.model.bmson.MineNote note : channel.notes) {
					accumulator.add(note.y);
				}
			}
		}
		for (MineChannel channel : bmson.mine_channels) {
			if (channel.notes != null) {
				for (bms.model.bmson.MineNote note : channel.notes) {
					accumulator.add(note.y);
				}
			}
		}
		if (bmson.bga != null) {
			addBgaNotes(accumulator, bmson.bga.bga_events);
			addBgaNotes(accumulator, bmson.bga.layer_events);
			addBgaNotes(accumulator, bmson.bga.poor_events);
		}

		timelineTicks = accumulator.toSortedUniqueArray();
		timelineCaches = new TimeLineCache[timelineTicks.length];

		int bpmpos = 0;
		int stoppos = 0;
		int scrollpos = 0;
		for (int i = 0; i < timelineTicks.length; i++) {
			final int y = timelineTicks[i];
			final double time;
			final TimeLine timeline = new TimeLine(y / resolution,
					i == 0 ? 0 : (long) (timelineCaches[i - 1].time + timelineCaches[i - 1].timeline.getMicroStop()
							+ (240000.0 * 1000 * ((y - timelineTicks[i - 1]) / resolution))
									/ timelineCaches[i - 1].timeline.getBPM()),
					model.getMode().key);
			if (i == 0) {
				time = 0.0;
				timeline.setBPM(model.getBpm());
			} else {
				time = timeline.getMicroTime();
				timeline.setBPM(timelineCaches[i - 1].timeline.getBPM());
				timeline.setScroll(timelineCaches[i - 1].timeline.getScroll());
			}

			while (scrollpos < bmson.scroll_events.length && bmson.scroll_events[scrollpos].y == y) {
				timeline.setScroll(bmson.scroll_events[scrollpos].rate);
				scrollpos++;
			}
			while (bpmpos < bmson.bpm_events.length && bmson.bpm_events[bpmpos].y == y) {
				if (bmson.bpm_events[bpmpos].bpm > 0) {
					timeline.setBPM(bmson.bpm_events[bpmpos].bpm);
				} else {
					log.add(new DecodeLog(WARNING,
							"negative BPMはサポートされていません - y : " + bmson.bpm_events[bpmpos].y + " bpm : " + bmson.bpm_events[bpmpos].bpm));
				}
				bpmpos++;
			}
			while (stoppos < bmson.stop_events.length && bmson.stop_events[stoppos].y == y) {
				if (bmson.stop_events[stoppos].duration >= 0) {
					timeline.setStop((long) ((1000.0 * 1000 * 60 * 4 * bmson.stop_events[stoppos].duration)
							/ (timeline.getBPM() * resolution)));
				} else {
					log.add(new DecodeLog(WARNING,
							"negative STOPはサポートされていません - y : " + bmson.stop_events[stoppos].y + " bpm : " + bmson.stop_events[stoppos].duration));
				}
				stoppos++;
			}

			timelineCaches[i] = new TimeLineCache(time, timeline);
		}
	}

	private boolean existsNoteInRange(int startExclusive, int endInclusive, int key) {
		int index = Arrays.binarySearch(timelineTicks, startExclusive);
		if (index < 0) {
			index = -index - 1;
		} else {
			index++;
		}
		while (index < timelineTicks.length && timelineTicks[index] <= endInclusive) {
			if (timelineCaches[index].timeline.existNote(key)) {
				return true;
			}
			index++;
		}
		return false;
	}

	private void addBgaNotes(IntAccumulator accumulator, BNote[] notes) {
		if (notes != null) {
			for (BNote note : notes) {
				accumulator.add(note.y);
			}
		}
	}

	private static final class IntAccumulator {
		private int[] values;
		private int size;

		IntAccumulator(int initialCapacity) {
			values = new int[Math.max(8, initialCapacity)];
		}

		void add(int value) {
			if (size == values.length) {
				values = Arrays.copyOf(values, values.length * 2);
			}
			values[size++] = value;
		}

		int[] toSortedUniqueArray() {
			int[] copy = Arrays.copyOf(values, size);
			Arrays.sort(copy);
			if (copy.length == 0) {
				return copy;
			}
			int uniqueSize = 1;
			for (int i = 1; i < copy.length; i++) {
				if (copy[i] != copy[uniqueSize - 1]) {
					copy[uniqueSize++] = copy[i];
				}
			}
			return Arrays.copyOf(copy, uniqueSize);
		}
	}
}
