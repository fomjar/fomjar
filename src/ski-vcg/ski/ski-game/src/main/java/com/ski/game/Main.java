package com.ski.game;

import fomjar.server.FjServerToolkit;

public class Main {

    /**
     * @param args[0] server name
     * @param args[1] server port
     */
    public static void main(String[] args) {
        FjServerToolkit.startConfigMonitor();
        FjServerToolkit.startServer(args[0]).addServerTask(new GameTask(args[0]));
    }

}
