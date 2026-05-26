package com.sunnynet.tools.capture;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 独立入库线程：JNI 回调线程只做入队，绝不阻塞主线程。
 */
public final class CaptureEventQueue {

    private static final int QUEUE_CAPACITY = 512;
    private static final BlockingQueue<Runnable> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    static {
        Thread worker = new Thread(CaptureEventQueue::loop, "sunnynet-capture-store");
        worker.setDaemon(true);
        worker.start();
    }

    private CaptureEventQueue() {
    }

    /**
     * 非阻塞入队；队列满则丢弃。
     */
    public static boolean offer(Runnable task) {
        return QUEUE.offer(task);
    }

    private static void loop() {
        while (true) {
            try {
                Runnable task = QUEUE.take();
                task.run();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignored) {
                // 单条失败不影响后续入库
            }
        }
    }
}
