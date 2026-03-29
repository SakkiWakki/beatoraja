package bms.player.beatoraja.stream;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import bms.player.beatoraja.MessageRenderer;
import bms.player.beatoraja.select.MusicSelector;
import bms.player.beatoraja.stream.command.StreamCommand;
import bms.player.beatoraja.stream.command.StreamRequestCommand;

/**
 * beatoraja パイプで受け取った文字列処理
 */
public class StreamController {
    private static final Logger logger = Logger.getGlobal();

    StreamCommand[] commands;
    BufferedReader pipeBuffer;
    Thread polling;
    boolean isActive = false;
    MusicSelector selector;
    MessageRenderer notifier;

    public StreamController(MusicSelector selector, MessageRenderer notifier) {
        this.selector = selector;
        this.notifier = notifier;
        commands = new StreamCommand[] { new StreamRequestCommand(this.selector, this.notifier) };
        try {
            Path pipePath = Paths.get("\\\\.\\pipe\\beatoraja");
            pipeBuffer = Files.newBufferedReader(pipePath);
            isActive = true;
        } catch (Exception e) {
            logger.warning("パイプ接続失敗: " + e.getMessage());
            dispose();
        }
    }

    public void run() {
        if (pipeBuffer == null) {
            return;
        }
        polling = new Thread(() -> {
            try {
                String line;
                while (!Thread.interrupted()) {
                    line = pipeBuffer.readLine();
                    if (line == null) {
                        break;
                    }
                    logger.info("受信:" + line);
                    execute(line);
                }
            } catch (Exception e) {
                logger.warning("パイプ読み込みエラー: " + e.getMessage());
            }
        });
        polling.start();
    }

    public void dispose() {
        if (polling != null) {
            polling.interrupt();
            polling = null;
        }
        if (pipeBuffer != null) {
            try {
                pipeBuffer.close();
            } catch (IOException e) {
                logger.warning("パイプクローズ失敗: " + e.getMessage());
            }
        }
        for (StreamCommand command : commands) {
            command.dispose();
        }
        logger.info("パイプリソース破棄完了");
    }

    private void execute(String line) {
        for (StreamCommand command : commands) {
            String cmd = command.COMMAND_STRING + " ";
            String[] splitLine = line.split(cmd);
            String data = splitLine.length == 2 ? splitLine[1] : "";
            command.run(data);
        }
    }

}
