package bms.player.beatoraja.play.bga;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Gdx2DPixmap;

/**
 * ffmpegを使用した動画表示用クラス
 *
 * @author exch
 */
public class FFmpegProcessor implements MovieProcessor {
	private enum ProcessorStatus {
		TEXTURE_INACTIVE,
		TEXTURE_ACTIVE,
		DISPOSED,
	}

	private Texture showingtex;
	private final int fpsd;
	private MovieSeekThread movieseek;

	private volatile long time;
	private volatile ProcessorStatus processorStatus = ProcessorStatus.TEXTURE_INACTIVE;

	public FFmpegProcessor(int fpsd) {
		this.fpsd = fpsd;
	}

	public void create(String filepath) {
		movieseek = new MovieSeekThread(filepath);
		movieseek.setDaemon(true);
		movieseek.start();
	}

	@Override
	public Texture getFrame(long time) {
		this.time = time;
		if (processorStatus == ProcessorStatus.TEXTURE_ACTIVE) {
			return showingtex;
		}
		return null;
	}

	public void play(long time, boolean loop) {
		if (processorStatus == ProcessorStatus.DISPOSED) return;
		this.time = time;
		movieseek.exec(loop ? Command.LOOP : Command.PLAY);
	}

	public void stop() {
		if (processorStatus == ProcessorStatus.DISPOSED) return;
		movieseek.exec(Command.STOP);
	}

	@Override
	public void dispose() {
		processorStatus = ProcessorStatus.DISPOSED;
		if (movieseek != null) {
			movieseek.exec(Command.HALT);
			try {
				movieseek.join(3000);
			} catch (InterruptedException ignored) {
			}
			movieseek = null;
		}
		if (showingtex != null) {
			showingtex.dispose();
		}
	}

	class MovieSeekThread extends Thread {
		private static final Method setVideoFrameNumber;
		static {
			Method method = null;
			try {
				method = FFmpegFrameGrabber.class.getMethod("setVideoFrameNumber", int.class);
			} catch (NoSuchMethodException | SecurityException ignored) {}
			setVideoFrameNumber = method;
		}

		private FFmpegFrameGrabber grabber;
		private final LinkedBlockingDeque<Command> commands = new LinkedBlockingDeque<>(4);

		private boolean eof = true;

		private ByteBuffer pixelBuffer;
		private Pixmap pixmap;
		private volatile boolean textureUploadPending;

		private final String filepath;
		private long offset;
		private long framecount;

		public MovieSeekThread(String filepath) {
			this.filepath = filepath;
		}

		public void run() {
			try {
				grabber = new FFmpegFrameGrabber(filepath);
				Logger.getGlobal().info("動画デコーダ開始 : " + filepath);
				grabber.start();
				while (grabber.getVideoBitrate() < 10) {
					final int videoStream = grabber.getVideoStream();
					try {
						if (videoStream < 5) {
							grabber.setVideoStream(videoStream + 1);
							grabber.restart();
						} else {
							grabber.setVideoStream(-1);
							grabber.restart();
							break;
						}
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
				Logger.getGlobal()
						.info("movie decode - fps : " + grabber.getFrameRate() + " format : " + grabber.getFormat()
								+ " size : " + grabber.getImageWidth() + " x " + grabber.getImageHeight()
								+ " length (frame / time) : " + grabber.getLengthInFrames() + " / "
								+ grabber.getLengthInTime());

				offset = grabber.getTimestamp();
				Frame frame = null;
				boolean halt = false;
				boolean loop = false;
				while (!halt) {
					if (!commands.isEmpty()) {
						switch (commands.pollFirst()) {
						case PLAY:
							loop = false;
							restart();
							break;
						case LOOP:
							loop = true;
							restart();
							break;
						case STOP:
							eof = true;
							break;
						case HALT:
							halt = true;
						}
					}
					if (halt) break;

					final long microtime = time * 1000 + offset;
					if (eof) {
						try {
							sleep(3600000);
						} catch (InterruptedException e) {
						}
					} else if (microtime >= grabber.getTimestamp()) {
						while (microtime >= grabber.getTimestamp() || framecount % fpsd != 0) {
							if (processorStatus == ProcessorStatus.DISPOSED || !commands.isEmpty()) {
								break;
							}
							frame = grabber.grabImage();
							if (frame == null) {
								break;
							}
							framecount++;
						}
						if (frame == null) {
							eof = true;
							if (loop) {
								commands.offerLast(Command.LOOP);
							}
						} else if (frame.image != null && frame.image[0] != null) {
							ByteBuffer src = (ByteBuffer) frame.image[0];
							int bytes = src.remaining();
							if (pixelBuffer == null || pixelBuffer.capacity() < bytes) {
								pixelBuffer = ByteBuffer.allocateDirect(bytes);
							}
							pixelBuffer.clear();
							src.mark();
							pixelBuffer.put(src);
							src.reset();
							pixelBuffer.flip();

							if (pixmap == null) {
								final long[] nativeData = { 0, bytes / frame.imageHeight / 3, frame.imageHeight,
										Gdx2DPixmap.GDX2D_FORMAT_RGB888 };
								pixmap = new Pixmap(new Gdx2DPixmap(pixelBuffer, nativeData));
							}

							if (!textureUploadPending) {
								textureUploadPending = true;
								Gdx.app.postRunnable(() -> {
									if (pixmap == null || processorStatus == ProcessorStatus.DISPOSED) {
										textureUploadPending = false;
										return;
									}
									if (showingtex != null) {
										showingtex.draw(pixmap, 0, 0);
									} else {
										showingtex = new Texture(pixmap);
									}
									processorStatus = ProcessorStatus.TEXTURE_ACTIVE;
									textureUploadPending = false;
								});
							}
						}
					} else {
						final long sleeptime = (grabber.getTimestamp() - microtime) / 1000 - 1;
						try {
							sleep(Math.max(1, sleeptime));
						} catch (InterruptedException e) {
						}
					}
				}
			} catch (Throwable e) {
				Logger.getGlobal().severe("動画再生スレッド例外 : " + filepath + " - " + e.getClass().getName() + ": " + e.getMessage());
				e.printStackTrace();
			} finally {
				if (pixmap != null) {
					pixmap.dispose();
					pixmap = null;
				}
				try {
					grabber.stop();
					grabber.close();
					Logger.getGlobal().info("動画リソースの開放 : " + filepath);
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		private void restart() throws Exception {
			if (pixmap != null) {
				pixmap.dispose();
				pixmap = null;
			}
			pixelBuffer = null;
			if (setVideoFrameNumber != null) {
				try {
					setVideoFrameNumber.invoke(grabber, 0);
				} catch (IllegalAccessException | InvocationTargetException e) {
					grabber.restart();
					grabber.grabImage();
				}
			} else {
				grabber.restart();
				grabber.grabImage();
			}
			eof = false;
			offset = grabber.getTimestamp() - time * 1000;
			framecount = 1;
		}

		public void exec(Command com) {
			commands.offerLast(com);
			interrupt();
		}
	}

	enum Command {
		PLAY, LOOP, STOP, HALT;
	}
}
