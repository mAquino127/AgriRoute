package com.gagambytes.agriroute.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs network work off the main thread. */
public final class Async {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    private Async() { }

    public static void run(Runnable r) {
        POOL.execute(r);
    }
}
