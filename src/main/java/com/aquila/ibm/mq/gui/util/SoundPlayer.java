package com.aquila.ibm.mq.gui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class SoundPlayer {
    private static final Logger logger = LoggerFactory.getLogger(SoundPlayer.class);
    private static final int SAMPLE_RATE = 8000;

    public void playAlert() {
        playTone(800, 200);
        sleep(50);
        playTone(800, 200);
    }

    public void playWarning() {
        playTone(600, 300);
    }

    private void playTone(int frequency, int duration) {
        try {
            byte[] buffer = generateTone(frequency, duration);
            AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
                 AudioInputStream audioInputStream = new AudioInputStream(bais, audioFormat, buffer.length)) {

                DataLine.Info info = new DataLine.Info(Clip.class, audioFormat);
                if (!AudioSystem.isLineSupported(info)) {
                    logger.warn("Audio line not supported, using system beep");
                    java.awt.Toolkit.getDefaultToolkit().beep();
                    return;
                }

                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(audioInputStream);
                clip.start();

                Thread.sleep(duration);
                clip.close();
            }
        } catch (LineUnavailableException | IOException | InterruptedException e) {
            logger.error("Failed to play tone, falling back to system beep", e);
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    private byte[] generateTone(int frequency, int duration) {
        int samples = (int) ((duration / 1000.0) * SAMPLE_RATE);
        byte[] buffer = new byte[samples];

        for (int i = 0; i < samples; i++) {
            double angle = 2.0 * Math.PI * i / (SAMPLE_RATE / frequency);
            buffer[i] = (byte) (Math.sin(angle) * 127.0);
        }

        return buffer;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void test() {
        logger.info("Testing alert sound...");
        playAlert();
        sleep(500);
        logger.info("Testing warning sound...");
        playWarning();
    }
}
