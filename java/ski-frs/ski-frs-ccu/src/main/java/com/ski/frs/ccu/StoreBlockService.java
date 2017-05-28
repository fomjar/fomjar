package com.ski.frs.ccu;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

import com.ski.frs.ccu.cb.SBDevice;
import com.ski.frs.ccu.cb.SBPicture;
import com.ski.frs.ccu.cb.SBSubject;
import com.ski.frs.isis.ISIS;

import fomjar.server.msg.FjISIS;
import fomjar.util.FjLoopTask;
import fomjar.util.FjThreadFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@SuppressWarnings("unchecked")
public class StoreBlockService {
    
    private static final Logger logger = Logger.getLogger(StoreBlockService.class);
    
    private static ExecutorService pool = null;
    private static StoreBlockService instance = null;
    public static synchronized StoreBlockService getInstance() {
        if (null == instance) {
            pool = Executors.newCachedThreadPool(new FjThreadFactory("sbs"));
            instance = new StoreBlockService();
        }
        return instance;
    }
    
    private FjLoopTask  monitor;
    private SBDevice    sb_dev;
    private SBPicture   sb_pic;
    private SBSubject   sb_sub;
    
    private StoreBlockService() {
        monitor = new Monitor();
        sb_dev = new SBDevice();
        sb_pic = new SBPicture();
        sb_sub = new SBSubject();
    }
    
    public boolean ready() {return sb_dev.ready() && sb_pic.ready() && sb_sub.ready();}
    
    public void open() {
        if (monitor.isRun()) return;
        new Thread(monitor, "sbs-monitor").start();
        if (sb_dev.file().isFile()) {
            pool.submit(()->{
                try {
                    logger.info("load sb dev begin");
                    sb_dev = (SBDevice) sb_dev.load();
                    logger.info("load sb dev success");
                } catch (ClassNotFoundException | IOException e) {logger.error("load sb dev failed", e);}
            });
        }
        if (sb_pic.file().isFile()) {
            pool.submit(()->{
                try {
                    logger.info("load sb pic begin");
                    sb_pic = (SBPicture) sb_pic.load();
                    logger.info("load sb pic success");
                } catch (ClassNotFoundException | IOException e) {logger.error("load sb pic failed", e);}
            });
        }
        if (sb_sub.file().isFile()) {
            pool.submit(()->{
                try {
                    logger.info("load sb sub begin");
                    sb_sub = (SBSubject) sb_sub.load();
                    logger.info("load sb sub success");
                } catch (ClassNotFoundException | IOException e) {logger.error("load sb sub failed", e);}
            });
        }
    }
    
    public void close() {monitor.close();}
    
    private class Monitor extends FjLoopTask {
        
        private static final long TIMEOUT = 1000L * 60 * 5;
        
        public Monitor() {
            setDelay(TIMEOUT);
            setInterval(TIMEOUT);
        }
        @Override
        public void perform() {
            pool.submit(()->{
                try {
                    logger.info("save sb dev begin");
                    sb_dev.save();
                    logger.info("save sb dev success, file size: " + sb_dev.file().length());
                } catch (IOException e) {logger.error("save sb dev failed", e);}
            });
            pool.submit(()->{
                try {
                    logger.info("save sb pic begin");
                    sb_pic.save();
                    logger.info("save sb pic success, file size: " + sb_pic.file().length());
                } catch (IOException e) {logger.error("save sb pic failed", e);}
            });
            pool.submit(()->{
                try {
                    logger.info("save sb sub begin");
                    sb_sub.save();
                    logger.info("save sb sub success, file size: " + sb_sub.file().length());
                } catch (IOException e) {logger.error("save sb sub failed", e);}
            });
        }
        
    }
    
    public JSONObject dispatch(int inst, JSONObject args) {
        if (!ready()) {
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_INTERNAL_ERROR);
            json.put("desc", "system not ready");
            return json;
        }
        for (Field field : ISIS.class.getFields()) {
            if (Integer.class != field.getType()) continue;
            try {
                if (field.getInt(ISIS.class) == inst) {
                    Method method = StoreBlockService.class.getMethod(field.getName(), JSONObject.class);
                    return (JSONObject) method.invoke(this, args);
                }
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
                String desc = String.format("dispatch message failed, inst = 0x%08X(%s), args = %s", inst, field.getName(), args);
                logger.error(desc);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_INTERNAL_ERROR);
                json.put("desc", desc);
                return json;
            }
        }
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_ILLEGAL_INST);
        json.put("desc", "illegal inst: " + inst);
        return json;
    }
    
    public JSONObject INST_SET_PIC(JSONObject args) {
        if (!args.has("name") || !args.has("type") || !args.has("size") || !args.has("path")) {
            String desc = "illegal arguments, no name, type, size, path";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        
        String pid = null;
        if (!args.has("pid")) args.put("pid", pid = "picture-" + UUID.randomUUID().toString().replace("-", ""));
        else pid = args.getString("pid");
        
        if (args.has("did")) {
            String did = args.getString("did"); // 设备下图片
            List<Map<String, Object>> devs = sb_dev.getDevice(did);
            if (devs.isEmpty()) {
                String desc = "illegal arguments, invalid did: " + did;
                logger.error(desc + ", " + args);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
                json.put("desc", desc);
                return json;
            }
            ((List<String>) devs.get(0).get("pids")).add(pid);
        } else if (args.has("sid") && args.has("siid")) {   // 主体库下图片
            String sid = args.getString("sid");
            String siid = args.getString("siid");
            List<Map<String, Object>> subs = sb_sub.getSubject(sid);
            if (subs.isEmpty()) {
                String desc = "illegal arguments, invalid sid: " + sid;
                logger.error(desc + ", " + args);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
                json.put("desc", desc);
                return json;
            }
            Map<String, Object> items = (Map<String, Object>) subs.get(0).get("items");
            if (!items.containsKey(siid)) {
                String desc = "illegal arguments, invalid siid: " + siid;
                logger.error(desc + ", " + args);
                JSONObject json = new JSONObject();
                json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
                json.put("desc", desc);
                return json;
            }
            ((List<String>) ((Map<String, Object>) items.get(siid)).get("pids")).add(pid);
        } else {    // 不存在单独图片
            String desc = "illegal arguments, no did or sid, siid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_pic.setPicture(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_GET_PIC(JSONObject args) {
        if (args.has("pid")) {
            List<String> pid = new LinkedList<>();
            Object obj = args.get("pid");
            if (obj instanceof String) pid.add((String) obj);
            else if (obj instanceof JSONArray) pid.addAll((JSONArray) obj);
            
            List<Map<String, Object>> pics = sb_pic.getPicture(pid.toArray(new String[pid.size()]));
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_SUCCESS);
            json.put("desc", pics);
            return json;
        } else if (args.has("fv") && args.has("min") && args.has("max")) {
            JSONArray array = args.getJSONArray("fv");
            float[] fv = new float[array.size()];
            for (int i = 0; i < array.size(); i++) fv[i] = (float) array.getDouble(i);
            
            List<Map<String, Object>> pics = sb_pic.getPicture(fv, (float) args.getDouble("min"), (float) args.getDouble("max"));
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_SUCCESS);
            json.put("desc", pics);
            return json;
        } else {
            String desc = "illegal arguments, no pid or fv, min, max";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
    }
    
    public JSONObject INST_SET_DEV(JSONObject args) {
        if (!args.has("path")) {
            String desc = "illegal arguments, path";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_dev.setDevice(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_DEL_DEV(JSONObject args) {
        if (!args.has("did")) {
            String desc = "illegal arguments, no did";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        List<String> did = new LinkedList<>();
        Object obj = args.get("did");
        if (obj instanceof String) did.add((String) obj);
        else if (obj instanceof JSONArray) did.addAll((JSONArray) obj);
        
        List<Map<String, Object>> devs = sb_dev.delDevice(did.toArray(new String[did.size()]));
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", devs);
        return json;
    }
    
    public JSONObject INST_GET_DEV(JSONObject args) {
        String[] did = null;
        if (args.has("did")) {
            List<String> list = new LinkedList<>();
            Object obj = args.get("did");
            if (obj instanceof String) list.add((String) obj);
            else if (obj instanceof JSONArray) list.addAll((JSONArray) obj);
            did = list.toArray(new String[list.size()]);
        }
        List<Map<String, Object>> devs = sb_dev.getDevice(did);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", devs);
        return json;
    }
    
    public JSONObject INST_SET_SUB(JSONObject args) {
        if (!args.has("name") || !args.has("type")) {
            String desc = "illegal arguments, no name, type";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_sub.setSubject(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_DEL_SUB(JSONObject args) {
        if (!args.has("sid")) {
            String desc = "illegal arguments, no sid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        List<String> sid = new LinkedList<>();
        Object obj = args.get("sid");
        if (obj instanceof String) sid.add((String) obj);
        else if (obj instanceof JSONArray) sid.addAll((JSONArray) obj);
        
        List<Map<String, Object>> sids = sb_sub.delSubject(sid.toArray(new String[sid.size()]));
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", sids);
        return json;
    }
    
    public JSONObject INST_MOD_SUB(JSONObject args) {
        if (!args.has("sid")) {
            String desc = "illegal arguments, no sid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_sub.modSubject(args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }
    
    public JSONObject INST_GET_SUB(JSONObject args) {
        String[] sid = null;
        if (args.has("sid")) {
            List<String> list = new LinkedList<>();
            Object obj = args.get("sid");
            if (obj instanceof String) list.add((String) obj);
            else if (obj instanceof JSONArray) list.addAll((JSONArray) obj);
            sid = list.toArray(new String[list.size()]);
        }
        List<Map<String, Object>> subs = sb_sub.getSubject(sid);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", subs);
        return json;
    }
    
    public JSONObject INST_SET_SUB_ITEM(JSONObject args) {
        if (!args.has("sid")) {
            String desc = "illegal arguments, no sid";
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        String sid = args.getString("sid");
        List<Map<String, Object>> subs = sb_sub.getSubject(sid);
        if (subs.isEmpty()) {
            String desc = "illegal arguments, subject not exist: " + sid;
            logger.error(desc + ", " + args);
            JSONObject json = new JSONObject();
            json.put("code", FjISIS.CODE_ILLEGAL_ARGS);
            json.put("desc", desc);
            return json;
        }
        sb_sub.setSubjectItem(sid, args);
        JSONObject json = new JSONObject();
        json.put("code", FjISIS.CODE_SUCCESS);
        json.put("desc", args);
        return json;
    }

}