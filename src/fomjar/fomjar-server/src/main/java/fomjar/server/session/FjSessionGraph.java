package fomjar.server.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import fomjar.server.FjMessageWrapper;
import fomjar.server.FjServer;
import fomjar.server.msg.FjDscpMessage;
import fomjar.util.FjLoopTask;

public class FjSessionGraph {
    
    private static final Logger logger = Logger.getLogger(FjSessionGraph.class);
    
    private Set<FjSessionNode>          nodes;
    private Map<Integer, FjSessionNode> heads;
    private Map<String,  FjSessionPath> paths;
    private FjSessionMonitor            monitor;
    
    public FjSessionGraph() {
        paths = new HashMap<String, FjSessionPath>();
        monitor = new FjSessionMonitor();
        new Thread(monitor, "fjsession-monitor").start();
    }
    
    public FjSessionNode createHeadNode(int inst, FjSessionTask task) {
        FjSessionNode head = createNode(inst, task);
        if (null == heads) heads = new HashMap<Integer, FjSessionNode>();
        heads.put(inst, head);
        return head;
    }
    
    public FjSessionNode createNode(int inst, FjSessionTask task) {
        FjSessionNode node = new FjSessionNode(inst, task);
        if (null == nodes) nodes = new HashSet<FjSessionNode>();
        nodes.add(node);
        return node;
    }
    
    public FjSessionNode getHeadNode(int inst) {
        if (null == heads) return null;
        return heads.get(inst);
    }
    
    public Map<Integer, FjSessionNode> getHeadNode() {return heads;}
    
    public Set<FjSessionNode> getNode() {return nodes;}
    
    public void dispatch(FjServer server, FjMessageWrapper wrapper) {
        if (!(wrapper.message() instanceof FjDscpMessage)) {
            logger.warn("can not dispatch non dscp message: " + wrapper.message());
            return;
        }
        
        FjDscpMessage msg  = (FjDscpMessage) wrapper.message();
        FjSessionPath path = paths.get(msg.sid());
        FjSessionNode curr = null;
        // match old
        if (null != path) curr = path.getLast().getNext(msg.inst());
        // match new
        else curr = getHeadNode(msg.inst());
        // not match
        if (null == curr) {
            logger.error("message not match this graph: " + msg);
            if (null != path) {
                logger.error("close path: " + msg.sid());
                path.close();
            }
            return;
        }
        // new path
        if (null == path) path = new FjSessionPath(this, msg.sid());
        path.context().prepare(server, msg);
        boolean isSuccess = false;
        // execute task
        try {isSuccess = curr.getTask().onSession(path, wrapper);}
        catch (Exception e) {logger.error("error occurs when process session for message: " + msg, e);}
        // infer result
        if (isSuccess) {
            if (!paths.containsKey(msg.sid())) {
                synchronized (paths) {paths.put(msg.sid(), path);}
            }
            path.append(curr);
            // no next, end
            if (!curr.hasNext()) path.close();
        } else logger.error("on session failed for message: " + msg);
    }
    
    void closePath(String sid) {
        if (!paths.containsKey(sid)) {
            logger.error("session path not opened: " + sid);
            return;
        }
        synchronized(paths) {paths.remove(sid);}
    }
    
    public FjSessionMonitor getMonitor() {
        return monitor;
    }
    
    public class FjSessionMonitor extends FjLoopTask {
        
        private static final long INTERVAL = 1000L * 60 * 1;
        
        private long timeout;
        
        public FjSessionMonitor() {
            super(INTERVAL, INTERVAL);
            timeout  = 1000L * 60 * 10;
        }
        
        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public void perform() {
            Map<String, FjSessionPath> pathcopy = new HashMap<String, FjSessionPath>(paths);
            pathcopy.values().forEach((path)->{
                if (path.isClosed()) {
                    logger.warn("session close incomplete: " + path.sid());
                    closePath(path.sid());
                } else {
                    if (timeout <= System.currentTimeMillis() - path.context().getLong("time.open")) {
                        logger.info("session timeout: " + path.sid());
                        path.close();
                    }
                }
            });
        }
        
    }
}
