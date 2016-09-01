package com.ski.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;

import com.ski.common.CommonDefinition;
import com.ski.common.CommonService;
import com.ski.common.bean.BeanGame;

import fomjar.server.msg.FjDscpMessage;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class ExecutorScanChannelCommodity implements ToolExecutor {
    
    private static String       g_conf          = "template/scc.conf";
    private static Properties   config          = new Properties();

    @Override
    public void execute(Map<String, String> args) {
        CommonService.setWsiHost("scc.ski.craftvoid.com");  // default wsi host
        
        args.forEach((k, v)->{
            switch (k) {
            case "wsi":     CommonService.setWsiHost(v);    break;
            case "conf":    g_conf = v;                     break;
            default:
                System.out.println(String.format("unknown argument: %s:%s", k, v));
                break;
            }
        });
        
        System.out.print(String.format("%-40s", "loading config..."));
        loadConfig();
        System.out.println(" done!");
        
        System.out.print(String.format("%-40s", "fetching game data..."));
        CommonService.updateGame();
        System.out.println(" done!");
        
        final long osn = System.currentTimeMillis();
        
        CommonService.getGameAll().values().forEach(game->{
            String preset  = config.getProperty(String.format("0x%08X.preset",  game.i_gid));
            if (null == preset)  preset  = config.getProperty("default.preset");
            String include = config.getProperty(String.format("0x%08X.include", game.i_gid));
            if (null == include) include = config.getProperty("default.include");
            String exclude = config.getProperty(String.format("0x%08X.exclude", game.i_gid));
            if (null == exclude) exclude = config.getProperty("default.exclude");
            
            preset  = parseStatement(preset,  game);
            include = parseStatement(include, game);
            exclude = parseStatement(exclude, game);
            
            if (null == preset || null == include || null == exclude) {
                System.out.println("skip scan: " + game.c_name_zh_cn);
                return;
            }
            
            JSONObject args_scc = new JSONObject();
            args_scc.put("preset",  preset);
            args_scc.put("include", include);
            args_scc.put("exclude", exclude);
            System.out.print(String.format("scan game %s ", game.c_name_zh_cn));
            FjDscpMessage rsp = CommonService.send("wa-scc", CommonDefinition.ISIS.INST_ECOM_QUERY_CHANNEL_COMMODITY, args_scc);
            
            if (CommonService.isResponseSuccess(rsp)) {
                JSONArray cc = rsp.argsToJsonObject().getJSONArray("desc");
                System.out.println(cc.size() + " records");
                for (int i = 0; i < cc.size(); i++) {
                    JSONObject args_cdb = cc.getJSONObject(i);
                    args_cdb.put("osn", osn);
                    args_cdb.put("cid", game.i_gid);
                    args_cdb.put("channel", CommonService.CHANNEL_TAOBAO);
                    CommonService.send("cdb", CommonDefinition.ISIS.INST_ECOM_UPDATE_CHANNEL_COMMODITY, args_cdb);
                }
            } else {
                System.out.println("failed: " + rsp);
            }
        });
    }
    
    private static String parseStatement(String statement, BeanGame game) {
        if (null == statement || 0 == statement.length()) return null;
        
        for (Field field : game.getClass().getFields()) {
            String variable = String.format("${%s}", field.getName());
            try {
                if (statement.contains(variable)) {
                    String value = String.valueOf(field.get(game));
                    if (value.contains(":")) value = value.substring(0, value.indexOf(":"));
                    statement = statement.replace(variable, value);
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                System.out.println("parse condition failed for variable: " + variable);
                e.printStackTrace();
            }
        }
        
        if (statement.contains("$")) {
            System.out.println("there are unrecognized variable in condition: " + statement);
            return null;
        }
        
        return statement;
    }
    
    private void loadConfig() {
        InputStreamReader isr = null;
        try {
            isr = new InputStreamReader(new FileInputStream(g_conf), "utf-8");
            config.load(isr);
        } catch (IOException e) {
            System.out.println("load config failed: " + g_conf);
            e.printStackTrace();
        } finally {
            if (null != isr) {
                try {isr.close();}
                catch (IOException e) {e.printStackTrace();}
            }
        }
    }
}