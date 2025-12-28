package com.aquila.ibm.mq.gui;

import com.aquila.ibm.mq.gui.ui.MainWindow;
import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting IBM MQ GUI Application");

        try {
            Display display = new Display();
            MainWindow mainWindow = new MainWindow(display);
            mainWindow.open();
            display.dispose();
        } catch (Exception e) {
            logger.error("Fatal error in application", e);
            e.printStackTrace();
        }

        logger.info("IBM MQ GUI Application terminated");
    }
}