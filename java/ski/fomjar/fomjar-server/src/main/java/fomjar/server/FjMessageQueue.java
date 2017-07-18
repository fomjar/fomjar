package fomjar.server;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.log4j.Logger;

public class FjMessageQueue {

    private static final Logger logger = Logger.getLogger(FjMessageQueue.class);
    private Queue<FjMessageWrapper> wrappers;

    public FjMessageQueue() {wrappers = new LinkedList<>();}

    public void offer(FjMessageWrapper wrapper) {
        if (null == wrapper) throw new NullPointerException();

        synchronized (wrappers) {
            wrappers.offer(wrapper);
            logger.debug("offered a new message: " + wrapper.message());
            logger.debug("there are " + size() + " messages in this queue");
            wrappers.notify();
        }
    }

    public FjMessageWrapper poll() {
        FjMessageWrapper wrapper = null;
        synchronized (wrappers) {
            while (null == (wrapper = wrappers.poll())) {
                logger.debug("there is no message now, wait");
                try {wrappers.wait();}
                catch (InterruptedException e) {logger.error("wait for message failed", e);}
            }
        }
        logger.debug("polled a message: " + wrapper.message());
        logger.debug("there are " + size() + " messages in this queue");
        return wrapper;
    }

    public int size() {return wrappers.size();}

}
