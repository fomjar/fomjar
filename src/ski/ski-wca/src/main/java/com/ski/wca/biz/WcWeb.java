package com.ski.wca.biz;

import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ski.common.CommonDefinition;
import com.ski.common.CommonService;
import com.ski.common.bean.BeanChannelAccount;
import com.ski.common.bean.BeanChannelCommodity;
import com.ski.common.bean.BeanCommodity;
import com.ski.common.bean.BeanGame;
import com.ski.common.bean.BeanGameAccount;
import com.ski.common.bean.BeanGameRentPrice;
import com.ski.common.bean.BeanPlatformAccount;
import com.ski.wca.BaiduMapInterface;
import com.ski.wca.WechatInterface;
import com.ski.wca.monitor.TokenMonitor;

import fomjar.server.FjServerToolkit;
import fomjar.server.FjServerToolkit.FjAddress;
import fomjar.server.msg.FjDscpMessage;
import fomjar.server.msg.FjHttpRequest;
import fomjar.server.msg.FjHttpResponse;
import fomjar.server.msg.FjJsonMessage;
import fomjar.server.msg.FjXmlMessage;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class WcWeb {
    
    private static final Logger logger = Logger.getLogger(WcWeb.class);
    
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    
    public  static final String URL_KEY = "/ski-wcweb";
    
    private static final int ANONYMOUS    = -1;
    
    private static final String STEP_PREPARE    = "prepare";
    private static final String STEP_SETUP      = "setup";
    private static final String STEP_APPLY      = "apply";
    private static final String STEP_SUCCESS    = "success";
    
    private TokenMonitor mon_token;
    
    public WcWeb(TokenMonitor mon_token) {
        this.mon_token = mon_token;
    }
    
    public void dispatch(String server, FjHttpRequest hreq, SocketChannel conn) {
        logger.info("user request url: " + hreq.url());
        logger.debug("user request data: " + hreq.content());
        
        FjHttpResponse response = new FjHttpResponse(null, 200, null, null);
        switch (hreq.path()) {
        case URL_KEY:
        case URL_KEY + "/pay/recharge":
        case URL_KEY + "/pay/refund": {
            JSONObject args = hreq.argsToJson();
            if (!args.has("inst")) {
                logger.error("illegal argument: no inst param: " + args);
                break;
            }
            int user = ANONYMOUS;            
            if (args.has("code")) {
                if (ANONYMOUS == (user = authorize(args))) break;
                
                logger.info("user: " + CommonService.getChannelAccountByCaid(user).getDisplayName());
                redirect(response, server, hreq.path(), user, args);
                break;
            }
            if (hreq.cookie().containsKey("user")) user = Integer.parseInt(hreq.cookie().get("user"), 16);
            else if (args.containsKey("user")) {
                user = Integer.parseInt(args.getString("user"), 16);
                response.setcookie("user", Integer.toHexString(user));
            }
            
            recordaccess(user, conn, server, hreq.url());
            
            WcwRequest request = new WcwRequest(server, hreq.path(), user, args, conn);
            switch (request.inst) {
            case CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY:
                processApplyPlatformAccountMoney(response, request);
                break;
            case CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_BEGIN:
                processApplyRentBegin(response, request);
                break;
            case CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_END:
                processApplyRentEnd(response, request);
                break;
            case CommonDefinition.ISIS.INST_ECOM_QUERY_GAME:
                processQueryGame(response, request);
                break;
            case CommonDefinition.ISIS.INST_ECOM_QUERY_ORDER:
                processQueryOrder(response, request);
                break;
            case CommonDefinition.ISIS.INST_ECOM_QUERY_PLATFORM_ACCOUNT_MAP:
                processQueryPlatformAccountMap(response, request);
                break;
            case CommonDefinition.ISIS.INST_ECOM_QUERY_PLATFORM_ACCOUNT_MONEY:
                processQueryPlatformAccountMoney(response, request);
                break;
            case CommonDefinition.ISIS.INST_ECOM_UPDATE_PLATFORM_ACCOUNT_MAP:
                processUpdatePlatformAccountMap(response, request);
                break;
            }
            break;
        }
        case URL_KEY + "/pay/recharge/success": {
            Document xml = hreq.contentToXml();
            processPayRechargeSuccess(response, server, xml);
            break;
        }
        default:
            fetchFile(response, hreq.url());
            break;
        }
        
        logger.debug("user response data: " + response);
        WechatInterface.sendResponse(response, conn);
    }
    
    private static int authorize(JSONObject args) {
        String code = args.getString("code");
        FjJsonMessage rsp = WechatInterface.snsOauth2(FjServerToolkit.getServerConfig("wca.appid"), FjServerToolkit.getServerConfig("wca.secret"), code);
        if (!rsp.json().has("openid")) {
            logger.error("user authorize failed: " + rsp);
            return ANONYMOUS;
        }
        
        List<BeanChannelAccount> users = CommonService.getChannelAccountByUserNChannel(rsp.json().getString("openid"), CommonService.CHANNEL_WECHAT);
        if (users.isEmpty()) return ANONYMOUS;
        
        return users.get(0).i_caid;
    }
    
    @SuppressWarnings("unchecked")
    private static void redirect(FjHttpResponse response, String server, String path, int user, JSONObject args) {
        int inst = Integer.parseInt(args.getString("inst"), 16);
        String url = generateUrl(server, path, inst);
        StringBuilder sb = new StringBuilder(url);
        args.entrySet().forEach(entry->{
            String key = ((Map.Entry<String, String>) entry).getKey();
            String val = ((Map.Entry<String, String>) entry).getValue();
            if (key.equals("inst")
                    || key.equals("user")
                    || key.equals("code")        // comes from wechat
                    || key.equals("state"))        // comes from wechat
                return;
            sb.append(String.format("&%s=%s", key, val));
        });
        response.code(302);
        response.attr().put("Location", sb.toString());
        response.setcookie("user", Integer.toHexString(user));
        logger.debug("user redirect data: " + response);
    }
    
    private static Map<String, Long>   cache_file_modify = new ConcurrentHashMap<String, Long>();
    private static Map<String, byte[]> cache_file_content = new ConcurrentHashMap<String, byte[]>();
    
    private static void fetchFile(FjHttpResponse response, String url) {
        File file = new File(FjServerToolkit.getServerConfig("wca.form.root") + (url.startsWith(URL_KEY) ? url.substring(URL_KEY.length()) : url));
        if (!file.isFile()) {
            logger.warn("not such file to fetch: " + file.getPath());
            return;
        }
        
        byte[] content = null;
        if (!cache_file_modify.containsKey(url)) cache_file_modify.put(url, 0l);
        
        if (file.lastModified() <= cache_file_modify.get(url)) content = cache_file_content.get(url);
        else {
            FileInputStream         fis = null;
            ByteArrayOutputStream   baos = null;
            try {
                byte[]  buf = new byte[1024 * 4];
                int     len = -1;
                fis     = new FileInputStream(file);
                baos    = new ByteArrayOutputStream();
                while (0 < (len = fis.read(buf))) baos.write(buf, 0, len);
                content = baos.toByteArray();
                
                cache_file_modify.put(url, file.lastModified());
                cache_file_content.put(url, content);
            } catch (IOException e) {logger.error("fetch file failed, url: " + url, e);}
            finally {
                try {
                    fis.close();
                    baos.close();
                } catch (IOException e) {e.printStackTrace();}
            }
        }
        if (null != content) {
            response.attr().put("Content-Type", getFileMime(file.getName()));
            response.content(content);
        }
    }
    
    private static String generateUrl(String server, int inst) {
        return generateUrl(server, URL_KEY, inst);
    }
    
    private static String generateUrl(String server, String path, int inst) {
        FjAddress addr = FjServerToolkit.getSlb().getAddress(server);
        return String.format("http://%s%s%s?inst=%s",
                addr.host,
                80 == addr.port ? "" : (":" + addr.port),
                path,
                Integer.toHexString(inst));
    }
    
    private static String getFileMime (String name) {
        if (!name.contains(".")) return FjHttpRequest.CT_TEXT_PLAIN;
        
        String ext = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
        switch (ext) {
        case "html":
        case "htm":     return FjHttpRequest.CT_TEXT_HTML;
        case "js":      return FjHttpRequest.CT_APPL_JS;
        case "css":
        case "less":    return FjHttpRequest.CT_TEXT_CSS;
        case "xml":     return FjHttpRequest.CT_TEXT_XML;
        case "json":    return FjHttpRequest.CT_APPL_JSON;
        case "jpg":
        case "jpeg":    return FjHttpRequest.CT_IMAG_JPG;
        case "bmp":        return FjHttpRequest.CT_IMAG_BMP;
        case "png":        return FjHttpRequest.CT_IMAG_PNG;
        case "gif":        return FjHttpRequest.CT_IMAG_GIF;
        default:    return FjHttpRequest.CT_TEXT_PLAIN;
        }
    }
    
    private static void recordaccess(int user, SocketChannel conn, String server, String url) {
        try {
            String remote = String.format("%15s|%6d",
                    ((InetSocketAddress)conn.getRemoteAddress()).getHostName(),
                    ((InetSocketAddress)conn.getRemoteAddress()).getPort());
            String local = String.format("%25s|%6d|%10s|%s",
                    ((InetSocketAddress)conn.getLocalAddress()).getHostName(),
                    ((InetSocketAddress)conn.getLocalAddress()).getPort(),
                    server,
                    url);
            JSONObject args = new JSONObject();
            args.put("caid",     user);
            args.put("remote",     remote);
            args.put("local",     local);
            FjDscpMessage msg = new FjDscpMessage();
            msg.json().put("fs", server);
            msg.json().put("ts", "cdb");
            msg.json().put("inst", CommonDefinition.ISIS.INST_ECOM_UPDATE_ACCESS_RECORD);
            msg.json().put("args", args);
            FjServerToolkit.getAnySender().send(msg);
        } catch (IOException e) {e.printStackTrace();}
    }
    
    private void processApplyPlatformAccountMoney(FjHttpResponse response, WcwRequest request) {
        if (ANONYMOUS == request.user) {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "warn");
            response.setcookie("msg_title",     "谢绝游客");
            response.setcookie("msg_content",     "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.setcookie("msg_url",         "");
            return;
        }
        switch (request.path) {
        case URL_KEY + "/pay/recharge":
            processApplyPlatformAccountMoney_Recharge(response, request);
            break;
        case URL_KEY + "/pay/refund":
            processApplyPlatformAccountMoney_Refund(response, request);
            break;
        }
    }
    
    private Set<Integer> cache_user_recharge = new HashSet<Integer>();

    private void processApplyPlatformAccountMoney_Recharge(FjHttpResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            long timestamp  = System.currentTimeMillis() / 1000;
            String noncestr = Long.toHexString(System.currentTimeMillis());
            fetchFile(response, "/apply_platform_account_money_recharge.html");
            response.setcookie("appid",     FjServerToolkit.getServerConfig("wca.appid"));
            response.setcookie("timestamp", String.valueOf(timestamp));
            response.setcookie("noncestr",    noncestr);
            response.setcookie("signature", WechatInterface.createSignature4Config(noncestr, mon_token.ticket(), timestamp, WcWeb.generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY)));
            break;
        }
        case STEP_APPLY: {
            String money = request.args.getString("money");
            String terminal = "127.0.0.1";
            try {terminal = ((InetSocketAddress) request.conn.getRemoteAddress()).getAddress().getHostAddress();}
            catch (IOException e) {logger.error("get user terminal address failed", e);}
            String url = generateUrl(request.server, URL_KEY + "/pay/recharge/success", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY);
            url = url.substring(0, url.indexOf("?"));
            FjXmlMessage rsp = WechatInterface.prepay(
                    "VC电玩-充值",
                    "您已成功充值" + money + "元",
                    (int) (Float.parseFloat(money) * 100),
                    terminal,
                    url,
                    CommonService.getChannelAccountByCaid(request.user).c_user);
            
            String timeStamp    = String.valueOf(System.currentTimeMillis() / 1000);
            String nonceStr     = Long.toHexString(System.currentTimeMillis());
            JSONObject json_prepay = xml2json(rsp.xml());
            
            Map<String, String> map = new HashMap<String, String>();
            map.put("appId",        FjServerToolkit.getServerConfig("wca.appid"));
            map.put("timeStamp",    timeStamp);
            map.put("nonceStr",     nonceStr);
            map.put("package",      "prepay_id=" + json_prepay.getString("prepay_id"));
            map.put("signType",     "MD5");
            String paySign = WechatInterface.createSignature4Pay(map);
            
            JSONObject json_pay = new JSONObject();
            json_pay.put("appId",       FjServerToolkit.getServerConfig("wca.appid"));
            json_pay.put("timeStamp",   timeStamp);
            json_pay.put("nonceStr",    nonceStr);
            json_pay.put("package",     "prepay_id=" + json_prepay.getString("prepay_id"));
            json_pay.put("signType",    "MD5");
            json_pay.put("paySign",     paySign);
            json_prepay.put("pay", json_pay);
            
            cache_user_recharge.add(request.user);
            
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(json_prepay);
            break;
        }
        case STEP_SUCCESS: {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "success");
            response.setcookie("msg_title",     "充值成功");
            response.setcookie("msg_content",     "");
            response.setcookie("msg_url",         generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_QUERY_PLATFORM_ACCOUNT_MONEY));
            break;
        }
        }
    }
    
    private void processApplyPlatformAccountMoney_Refund(FjHttpResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/apply_platform_account_money_refund.html");
            break;
        }
        case STEP_SETUP: {
            JSONObject desc = new JSONObject();
            desc.put("money", CommonService.prestatementByCaid(request.user)[0]);
            JSONObject args = new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args.put("desc", desc);
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(args);
            break;
        }
        case STEP_APPLY: {
            float money = CommonService.prestatementByCaid(request.user)[0];
            JSONObject args = new JSONObject();
            args.put("caid",    request.user);
            args.put("money",   -money);
            FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY, args);
            JSONObject rsp_args = rsp.argsToJsonObject();
            
            if (CommonService.isResponseSuccess(rsp)) {
                // 发红包
                String terminal = "127.0.0.1";
                try {terminal = ((InetSocketAddress) request.conn.getRemoteAddress()).getAddress().getHostAddress();}
                catch (IOException e) {logger.error("get user terminal address failed", e);}
                sendredpack(terminal, CommonService.getChannelAccountByCaid(request.user).c_user, money);
            }
            
            logger.error("user pay refund: " + rsp_args);
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(rsp_args);
            break;
        }
        case STEP_SUCCESS: {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",        "success");
            response.setcookie("msg_title",     "退款成功");
            response.setcookie("msg_content",    "退款将以现金红包的方式发放，超过200元时会拆分多个红包，请耐心等待！");
            response.setcookie("msg_url",         generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_QUERY_PLATFORM_ACCOUNT_MONEY));
            break;
        }
        }
    }
    
    private static void sendredpack(String terminal, String user, float money) {
        float max = Float.parseFloat(FjServerToolkit.getServerConfig("wca.redpack.max"));
        long  interval = Long.parseLong(FjServerToolkit.getServerConfig("wca.redpack.interval"));
        pool.submit(()->{
            try {
                float m = money;
                List<Float> moneys = new LinkedList<Float>();
                while (m > max) {
                    moneys.add(max);
                    m -= max;
                }
                moneys.add(m);
                
                for (int i = 0; i < moneys.size(); i++) {
                    FjXmlMessage rsp_redpack = WechatInterface.sendredpack("VC电玩",
                            user,
                            moneys.get(i),
                            String.format("VC电玩游戏退款(%d/%d)", i + 1, moneys.size()),
                            terminal,
                            "VC电玩活动送好礼",
                            "关注VC电玩");
                    logger.error("send red pack: " + rsp_redpack);
                    Thread.sleep(interval * 1000L);
                }
            } catch (Exception e) {logger.error("error occurs when send redpack", e);}
        });
    }
    
    /**
     * <xml>
     * <appid><![CDATA[wx9c65a26e4f512fd4]]></appid>
     * <attach><![CDATA[您已成功充值1元]]></attach>
     * <bank_type><![CDATA[CFT]]></bank_type>
     * <cash_fee><![CDATA[100]]></cash_fee>
     * <device_info><![CDATA[WEB]]></device_info>
     * <fee_type><![CDATA[CNY]]></fee_type>
     * <is_subscribe><![CDATA[Y]]></is_subscribe>
     * <mch_id><![CDATA[1364744702]]></mch_id>
     * <nonce_str><![CDATA[155e55b5e2f4ec1f9d155793]]></nonce_str>
     * <openid><![CDATA[oRojEwPTK3o2cYrLsXuuX-FuypBM]]></openid>
     * <out_trade_no><![CDATA[20160714014338288]]></out_trade_no>
     * <result_code><![CDATA[SUCCESS]]></result_code>
     * <return_code><![CDATA[SUCCESS]]></return_code>
     * <sign><![CDATA[5B66E346DF7FAE2A508A933092AB6590]]></sign>
     * <time_end><![CDATA[20160714014342]]></time_end>
     * <total_fee>100</total_fee>
     * <trade_type><![CDATA[JSAPI]]></trade_type>
     * <transaction_id><![CDATA[4003922001201607148930843003]]></transaction_id>
     * </xml>
     * 
     * @param xml
     */
    private void processPayRechargeSuccess(FjHttpResponse response, String server, Document xml) {
        JSONObject args = new JSONObject();
        NodeList nodes = xml.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (null == node.getFirstChild()) continue;
            args.put(node.getNodeName(), node.getFirstChild().getNodeValue());
        }

        List<BeanChannelAccount> users = CommonService.getChannelAccountByUserNChannel(args.getString("openid"), CommonService.CHANNEL_WECHAT);
        if (users.isEmpty()) return;
        
        BeanChannelAccount user = users.get(0);
        if (cache_user_recharge.contains(user.i_caid)) {
            cache_user_recharge.remove(user.i_caid);
            
            logger.error("user pay recharge: " + args);
            float money = ((float) args.getInt("total_fee")) / 100;
            JSONObject args_cdb = new JSONObject();
            args_cdb.put("caid",    user.i_caid);
            args_cdb.put("money",   money);
            FjDscpMessage msg_cdb = new FjDscpMessage();
            msg_cdb.json().put("fs",   server);
            msg_cdb.json().put("ts",   "bcs");
            msg_cdb.json().put("inst", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY);
            msg_cdb.json().put("args", args_cdb);
            FjServerToolkit.getAnySender().send(msg_cdb);
        }
        
        response.attr().put("Content-Type", FjHttpRequest.CT_TEXT_XML);
        response.content("<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>");
    }
    
    private void processApplyRentBegin(FjHttpResponse response, WcwRequest request) {
        if (ANONYMOUS == request.user) {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "warn");
            response.setcookie("msg_title",     "谢绝游客");
            response.setcookie("msg_content",     "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.setcookie("msg_url",         "");
            return;
        }
        switch (request.step) {
        case STEP_PREPARE: {
            if (!request.args.has("gid")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数错误");
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
                break;
            }
            if (0 == CommonService.getChannelAccountByCaid(request.user).c_phone.length()) {
                processUpdatePlatformAccountMap(response, request);
                break;
            }
            int gid = Integer.parseInt(request.args.getString("gid"), 16);
            fetchFile(response, "/apply_rent_begin.html");
            response.setcookie("gid", Integer.toHexString(gid));
            break;
        }
//        case STEP_SETUP: break;
        case STEP_APPLY: {
            if (!request.args.has("gid") || !request.args.has("type")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数错误");
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
                break;
            }
            int gid     = Integer.parseInt(request.args.getString("gid"), 16);
            int type    = Integer.parseInt(request.args.getString("type"), 16);
            JSONObject args = new JSONObject();
            args.put("platform",    CommonService.CHANNEL_WECHAT);
            args.put("caid",        request.user);
            args.put("gid",         gid);
            args.put("type",        type);
            FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_BEGIN, args);
            if (!CommonService.isResponseSuccess(rsp)) {
                JSONObject args_rsp = new JSONObject();
                args_rsp.put("code", CommonService.getResponseCode(rsp));
                args_rsp.put("desc", CommonService.getResponseDesc(rsp));
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args_rsp);
                break;
            }
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_BEGIN) + "&step=success");
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(args_rsp);
            break;
        }
        case STEP_SUCCESS: {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "success");
            response.setcookie("msg_title",     "起租成功");
            response.setcookie("msg_content",    "");
            response.setcookie("msg_url",         generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_QUERY_ORDER));
            break;
        }
        }
    }
    
    private void processApplyRentEnd(FjHttpResponse response, WcwRequest request) {
        if (ANONYMOUS == request.user) {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "warn");
            response.setcookie("msg_title",     "谢绝游客");
            response.setcookie("msg_content",     "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.setcookie("msg_url",         "");
            return;
        }
        switch (request.step) {
        case STEP_PREPARE: {
            if (!request.args.has("oid") || !request.args.has("csn")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数错误");
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
                return;
            }
            fetchFile(response, "/apply_rent_end.html");
            response.setcookie("oid", request.args.getString("oid"));
            response.setcookie("csn", request.args.getString("csn"));
            break;
        }
//        case STEP_SETUP: break;
        case STEP_APPLY: {
            JSONObject args = new JSONObject();
            args.put("caid",    request.user);
            args.put("oid",     Integer.parseInt(request.args.getString("oid"), 16));
            args.put("csn",     Integer.parseInt(request.args.getString("csn"), 16));
            FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_END, args);
            if (!CommonService.isResponseSuccess(rsp)) {
                JSONObject args_rsp = new JSONObject();
                args_rsp.put("code", CommonService.getResponseCode(rsp));
                args_rsp.put("desc", CommonService.getResponseDesc(rsp));
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args_rsp);
                break;
            }
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_END) + "&step=success");
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(args_rsp);
            break;
        }
        case STEP_SUCCESS: {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "success");
            response.setcookie("msg_title",     "退租成功");
            response.setcookie("msg_content",    " ");
            response.setcookie("msg_url",        generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_QUERY_ORDER));
            break;
        }
        }
    }
    
    private void processQueryGame(FjHttpResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            if (request.args.has("gid")) {
                fetchFile(response, "/query_game_by_gid.html");
                response.setcookie("gid", request.args.getString("gid"));
            } else if (request.args.has("category")) {
                fetchFile(response, "/query_game_by_category.html");
                response.setcookie("category", request.args.getString("category"));
            } else if (request.args.has("language")) {
                fetchFile(response, "/query_game_by_language.html");
                response.setcookie("language", request.args.getString("language"));
            } else if (request.args.has("tag")) {
                fetchFile(response, "/query_game_by_tag.html");
                response.setcookie("tag", request.args.getString("tag"));
            } else fetchFile(response, "/query_game.html");
            break;
        }
        case STEP_SETUP: {
            if (request.args.has("gid")) {
                int gid = Integer.parseInt(request.args.getString("gid"), 16);
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                JSONObject desc = gameToJson(CommonService.getGameByGid(gid));
                desc.put("ccs", channelCommoditiesToJson(request.user, gid, Integer.parseInt(FjServerToolkit.getServerConfig("wca.cc.max"))));
                args.put("desc", desc);
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            } else if (request.args.has("category")) {
                String[] categorys = request.args.getString("category").split("_");
                JSONArray desc = new JSONArray();
                CommonService.getGameByCategory(categorys).forEach(game->desc.add(gameToJson(game)));
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", desc);
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            } else if (request.args.has("language")) {
                String language = request.args.getString("language");
                JSONArray desc = new JSONArray();
                CommonService.getGameByLanguage(language).forEach(game->desc.add(gameToJson(game)));
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", desc);
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            } else if (request.args.has("tag")) {
                String tag = request.args.getString("tag");
                JSONArray desc = new JSONArray();
                CommonService.getGameByTag(tag).forEach(game->desc.add(gameToJson(game)));
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", desc);
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            } else if (request.args.has("word")) {
                String word = request.args.getString("word");
                JSONArray desc = new JSONArray();
                CommonService.getGameAll().values()
                        .stream()
                        .filter(game->game.getDisplayName().toLowerCase().contains(word.toLowerCase()))
                        .forEach(game->desc.add(gameToJson(game)));
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", desc);
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            } else {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数错误");
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            }
            break;
        }
        }
    }
    
    private static JSONObject gameToJson(BeanGame game) {
        JSONObject json = new JSONObject();
        json.put("gid",             game.i_gid);
        json.put("name_zh_cn",      game.c_name_zh_cn);
        json.put("name_zh_hk",      game.c_name_zh_hk);
        json.put("name_en",         game.c_name_en);
        json.put("name_ja",         game.c_name_ja);
        json.put("name_ko",         game.c_name_ko);
        json.put("name_other",      game.c_name_other);
        json.put("platform",        game.c_platform);
        json.put("category",        game.c_category);
        json.put("language",        game.c_language);
        json.put("size",            game.c_size);
        json.put("vendor",          game.c_vendor);
        json.put("sale",            game.t_sale);
        json.put("url_icon",        game.c_url_icon);
        json.put("url_cover",       game.c_url_cover);
        json.put("url_poster",      JSONArray.fromObject(game.c_url_poster.split(" ")));
        json.put("introduction",    game.c_introduction);
        json.put("version",         game.c_version);
        json.put("vedio",           game.c_vedio);
        
        json.put("display_name",    game.getDisplayName());
        BeanGameRentPrice rent_price_a = CommonService.getGameRentPriceByGid(game.i_gid, CommonService.RENT_TYPE_A);
        json.put("rent_price_a",    null != rent_price_a ? rent_price_a.i_price : 0.0f);
        BeanGameRentPrice rent_price_b = CommonService.getGameRentPriceByGid(game.i_gid, CommonService.RENT_TYPE_B);
        json.put("rent_price_b",    null != rent_price_b ? rent_price_b.i_price : 0.0f);
        
        json.put("rent_avail_a",    CommonService.getGameAccountByGidNRentState(game.i_gid, CommonService.RENT_STATE_IDLE, CommonService.RENT_TYPE_A).size() > 0);
        json.put("rent_avail_b",    CommonService.getGameAccountByGidNRentState(game.i_gid, CommonService.RENT_STATE_IDLE, CommonService.RENT_TYPE_B).size() > 0);
        
        return json;
    }
    
    private static JSONObject channelCommoditiesToJson(int caid, int cid, int max) {
        BeanChannelAccount user = CommonService.getChannelAccountByCaid(caid);
        List<BeanChannelCommodity> ccs = CommonService.getChannelCommodityByCid(cid);
        List<BeanChannelCommodity> cc_conv = new LinkedList<BeanChannelCommodity>(ccs);
        List<BeanChannelCommodity> cc_near = new LinkedList<BeanChannelCommodity>(ccs);
        List<BeanChannelCommodity> cc_trus = new LinkedList<BeanChannelCommodity>(ccs);
        List<BeanChannelCommodity> cc_sold = new LinkedList<BeanChannelCommodity>(ccs);
        cc_conv.sort((cc1, cc2)->{
            float p1 = Float.parseFloat(cc1.c_item_price.split("-")[0].trim()) + cc1.i_express_price;
            float p2 = Float.parseFloat(cc2.c_item_price.split("-")[0].trim()) + cc2.i_express_price;
            return (int) Math.ceil(p1 - p2);
        });
        cc_near.sort((cc1, cc2)->{
            int e = 10000;
            double d1 = getDistance(user.c_address, cc1.c_shop_addr);
            double d2 = getDistance(user.c_address, cc2.c_shop_addr);
            return (int) Math.ceil(d1 * e - d2 * e);
        });
        cc_trus.sort((c1, c2)->{
            int t1 = Arrays.asList(c1.c_shop_rate.split("\\|")).stream().filter(r->0 < r.length()).map(r->r.split(" ")).map(r->(r[0].contains("cap") ? 10000 : r[0].contains("blue") ? 100 : 1) * Integer.parseInt(r[1])).reduce(0, (r1, r2)->{return r1 + r2;});
            int t2 = Arrays.asList(c2.c_shop_rate.split("\\|")).stream().filter(r->0 < r.length()).map(r->r.split(" ")).map(r->(r[0].contains("cap") ? 10000 : r[0].contains("blue") ? 100 : 1) * Integer.parseInt(r[1])).reduce(0, (r1, r2)->{return r1 + r2;});
            return t2 - t1;
        });
        cc_sold.sort((c1, c2)->{
            return c2.i_item_sold - c1.i_item_sold;
        });
        
        if (max < ccs.size()) {
            cc_conv = cc_conv.subList(0, max);
            cc_near = cc_near.subList(0, max);
            cc_trus = cc_trus.subList(0, max);
            cc_sold = cc_sold.subList(0, max);
        }
        JSONObject json = new JSONObject();
        json.put("total", ccs.size());
        json.put("conv", JSONArray.fromObject(cc_conv.stream().map(cc->channelCommodityToJson(cc)).collect(Collectors.toList())));
        json.put("near", JSONArray.fromObject(cc_near.stream().map(cc->channelCommodityToJson(cc)).collect(Collectors.toList())));
        json.put("trus", JSONArray.fromObject(cc_trus.stream().map(cc->channelCommodityToJson(cc)).collect(Collectors.toList())));
        json.put("sold", JSONArray.fromObject(cc_sold.stream().map(cc->channelCommodityToJson(cc)).collect(Collectors.toList())));
        return json;
    }
    
    private static double getDistance(String place1, String place2) {
        Point2D.Double p1 = getCordinate(place1);
        Point2D.Double p2 = getCordinate(place2);
        return Math.sqrt((p1.getX() - p2.getX()) * (p1.getX() - p2.getX()) + (p1.getY() - p2.getY()) * (p1.getY() - p2.getY()));
    }
    
    private static Map<String, Point2D.Double> cache_cordinate = new ConcurrentHashMap<String, Point2D.Double>();
    
    private static Point2D.Double getCordinate(String place) {
        if (cache_cordinate.containsKey(place)) return cache_cordinate.get(place);
        else {
            Point2D.Double p = BaiduMapInterface.getCordinate(FjServerToolkit.getServerConfig("wca.baidu.map.ak"), place);
            if (null != p) {
                cache_cordinate.put(place, p);
                return p;
            }
            return new Point2D.Double(0, 0);
        }
    }
    
    private static JSONObject channelCommodityToJson(BeanChannelCommodity cc) {
        JSONObject json = new JSONObject();
        json.put("time",            cc.t_time);
        json.put("channel",         cc.i_channel);
        json.put("item_url",        cc.c_item_url);
        json.put("item_cover",      cc.c_item_cover);
        json.put("item_name",       cc.c_item_name);
        json.put("item_remark",     cc.c_item_remark);
        json.put("item_sold",       cc.i_item_sold);
        json.put("item_price",      cc.c_item_price);
        json.put("express_price",   cc.i_express_price);
        json.put("shop_url",        cc.c_shop_url);
        json.put("shop_name",       cc.c_shop_name);
        json.put("shop_owner",      cc.c_shop_owner);
        json.put("shop_rate",       cc.c_shop_rate);
        json.put("shop_score",      cc.c_shop_score);
        json.put("shop_addr",       cc.c_shop_addr);
        return json;
    }
    
    private void processQueryOrder(FjHttpResponse response, WcwRequest request) {
        if (ANONYMOUS == request.user) {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "warn");
            response.setcookie("msg_title",     "谢绝游客");
            response.setcookie("msg_content",     "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.setcookie("msg_url",         "");
            return;
        }
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/query_order.html");
            break;
        }
        case STEP_SETUP: {
            if (request.args.has("oid") && request.args.has("csn")) {
                int oid = Integer.parseInt(request.args.getString("oid"), 16);
                int csn = Integer.parseInt(request.args.getString("csn"), 16);
                BeanCommodity c = CommonService.getOrderByOid(oid).commodities.get(csn);
                
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", commodityToJson(c));
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            } else {
                JSONArray desc = new JSONArray();
                CommonService.getOrderByPaid(CommonService.getPlatformAccountByCaid(request.user)).forEach(o->{
                            o.commodities.values().forEach(c->desc.add(commodityToJson(c)));
                        });
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", desc);
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            }
            break;
        }
        }
    }
    
    private static JSONObject commodityToJson(BeanCommodity c) {
        JSONObject json = new JSONObject();
        json.put("oid",      c.i_oid);
        json.put("csn",      c.i_csn);
        json.put("count",    c.i_count);
        json.put("price",    c.i_price);
        json.put("begin",    c.t_begin);
        json.put("end",      c.t_end);
        json.put("expense",  c.i_expense);
        json.put("remark",   c.c_remark);
        json.put("arg0",     c.c_arg0);
        json.put("arg1",     c.c_arg1);
        json.put("arg2",     c.c_arg2);
        json.put("arg3",     c.c_arg3);
        json.put("arg4",     c.c_arg4);
        json.put("arg5",     c.c_arg5);
        json.put("arg6",     c.c_arg6);
        json.put("arg7",     c.c_arg7);
        json.put("arg8",     c.c_arg8);
        json.put("arg9",     c.c_arg9);
        
        BeanGameAccount account = CommonService.getGameAccountByGaid(Integer.parseInt(c.c_arg0, 16));
        json.put("account",  account.c_user);
        json.put("type",     "A".equals(c.c_arg1) ? "认证" : "B".equals(c.c_arg1) ? "不认证" : "未知");
        json.put("game",     gameToJson(CommonService.getGameByGaid(account.i_gaid).get(0)));
        // 预结算
        if (!c.isClose()) {
            json.put("expense", CommonService.prestatementByCommodity(c));
            json.put("pass", account.c_pass);
        }
        
        return json;
    }
    
    private void processQueryPlatformAccountMap(FjHttpResponse response, WcwRequest request) {
        if (ANONYMOUS == request.user) {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "warn");
            response.setcookie("msg_title",     "谢绝游客");
            response.setcookie("msg_content",     "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.setcookie("msg_url",         "");
            return;
        }
        switch (request.step) {
        case STEP_PREPARE: {
            if (CommonService.getChannelAccountRelatedByCaidNChannel(request.user, CommonService.CHANNEL_TAOBAO).isEmpty()) {
                processUpdatePlatformAccountMap(response, request);
                break;
            }
            fetchFile(response, "/query_platform_account_map.html");
            break;
        }
        case STEP_SETUP: {
            JSONObject args = new JSONObject();
            JSONArray desc = new JSONArray();
            CommonService.getChannelAccountRelatedAll(request.user).forEach(user->{
                JSONObject obj = new JSONObject();
                obj.put("caid",     user.i_caid);
                obj.put("channel",  user.i_channel);
                obj.put("user",     user.c_user);
                obj.put("name",     user.c_name);
                obj.put("phone",    user.c_phone);
                obj.put("gender",   user.i_gender);
                obj.put("birth",    user.t_birth);
                obj.put("address",  user.c_address);
                obj.put("zipcode",  user.c_zipcode);
                obj.put("create",   user.t_create);
                desc.add(obj);
            });
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args.put("desc", desc);
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(args);
            break;
        }
        }
    }
    
    private void processQueryPlatformAccountMoney(FjHttpResponse response, WcwRequest request) {
        if (ANONYMOUS == request.user) {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "warn");
            response.setcookie("msg_title",     "谢绝游客");
            response.setcookie("msg_content",     "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.setcookie("msg_url",         "");
            return;
        }
        switch (request.step) {
        case STEP_PREPARE: {
            if (0 == CommonService.getChannelAccountByCaid(request.user).c_phone.length()) {
                processUpdatePlatformAccountMap(response, request);
                break;
            }
            fetchFile(response, "/query_platform_account_money.html");
            break;
        }
        case STEP_SETUP: {
            BeanPlatformAccount puser = CommonService.getPlatformAccountByPaid(CommonService.getPlatformAccountByCaid(request.user));
            JSONObject desc = new JSONObject();
            desc.put("cash", puser.i_cash);
            desc.put("coupon", puser.i_coupon);
            float[] prestatement = CommonService.prestatementByPaid(puser.i_paid);
            desc.put("cash_rt", prestatement[0]);
            desc.put("coupon_rt", prestatement[1]);
            float deposite = CommonService.getOrderByPaid(puser.i_paid)
                    .stream()
                    .map(o->o.commodities.values()
                                .stream()
                                .filter(c->!c.isClose())
                                .count()
                    )
                    .reduce(0L, (o1, o2)->o1 + o2)
                    * Float.parseFloat(FjServerToolkit.getServerConfig("wca.deposite"));
            desc.put("deposite", deposite);
            
            JSONObject args = new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args.put("desc", desc);
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(args);
            break;
        }
        }
    }
    
    private Map<Integer, String> cache_verify_code = new HashMap<Integer, String>();
    
    private void processUpdatePlatformAccountMap(FjHttpResponse response, WcwRequest request) {
        if (ANONYMOUS == request.user) {
            fetchFile(response, "/message.html");
            response.setcookie("msg_type",         "warn");
            response.setcookie("msg_title",     "谢绝游客");
            response.setcookie("msg_content",     "请关注微信“VC电玩”，然后从微信访问我们，非常感谢！");
            response.setcookie("msg_url",         "");
            return;
        }
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/update_platform_account_map.html");
            break;
        }
        case STEP_SETUP: {
            if (!request.args.has("phone")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数不完整");
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
                break;
            }
            String phone    = request.args.getString("phone");
            String time     = String.valueOf(System.currentTimeMillis());
            String verify     = time.substring(time.length() - 4);
            {
                JSONObject args_mma = new JSONObject();
                args_mma.put("phones",  phone);
                args_mma.put("smsargs", verify);
                FjDscpMessage rsp = CommonService.send("mma", CommonDefinition.ISIS.INST_USER_AUTHORIZE, args_mma);
                if (!CommonService.isResponseSuccess(rsp)) {
                    JSONObject args = new JSONObject();
                    args.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                    args.put("desc", "发送失败，请稍候重试");
                    response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                    response.content(args);
                    break;
                }
            }
            
            cache_verify_code.put(request.user, verify);
            
            {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", null);
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
            }
            break;
        }
        case STEP_APPLY: {
            if (!request.args.has("phone") || !request.args.has("verify")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数不完整");
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
                break;
            }
            String  phone     = request.args.getString("phone");
            String  verify  = request.args.getString("verify");
            if (!cache_verify_code.containsKey(request.user)
                    || !verify.equals(cache_verify_code.get(request.user))) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                args.put("desc", "校验失败");
                response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                response.content(args);
                break;
            }
            cache_verify_code.remove(request.user);
            
            { // 更新手机号
                JSONObject args_cdb = new JSONObject();
                args_cdb.put("caid", request.user);
                args_cdb.put("phone", phone);
                FjDscpMessage rsp = CommonService.send("cdb", CommonDefinition.ISIS.INST_ECOM_UPDATE_CHANNEL_ACCOUNT, args_cdb);
                if (!CommonService.isResponseSuccess(rsp)) {
                    JSONObject args= new JSONObject();
                    args.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                    args.put("desc", "更新手机失败，请稍候重试");
                    response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                    response.content(args);
                    break;
                }
            }
            
            List<BeanChannelAccount> users_taobao = CommonService.getChannelAccountByPhoneNChannel(phone, CommonService.CHANNEL_TAOBAO);
            if (1 == users_taobao.size()) { // 尝试关联
                BeanChannelAccount user_taobao = users_taobao.get(0);
                if (CommonService.getChannelAccountRelatedByCaidNChannel(user_taobao.i_caid, CommonService.CHANNEL_WECHAT).isEmpty()) { // 淘宝用户尚未被关联
                    JSONObject args_cdb = new JSONObject();
                    args_cdb.put("paid_from",   CommonService.getPlatformAccountByCaid(request.user));
                    args_cdb.put("paid_to",        CommonService.getPlatformAccountByCaid(user_taobao.i_caid));
                    FjDscpMessage rsp = CommonService.send("cdb", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MERGE, args_cdb);
                    if (!CommonService.isResponseSuccess(rsp)) {
                        JSONObject args = new JSONObject();
                        args.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                        args.put("desc", "关联淘宝用户失败，请稍候重试");
                        response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
                        response.content(args);
                        break;
                    }
                }
            }
            
            JSONObject args= new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args.put("desc", null);
            response.attr().put("Content-Type", FjHttpRequest.CT_APPL_JSON);
            response.content(args);
            
            break;
        }
        }
    }
    
    private static JSONObject xml2json(Document xml) {
        JSONObject json = new JSONObject();
        NodeList nodes = xml.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (null == node.getFirstChild()) continue;
            json.put(node.getNodeName(), node.getFirstChild().getNodeValue());
        }
        return json;
    }
    
    private static class WcwRequest {
        public String               server = null;
        public int                  inst = -1;
        public int                  user = ANONYMOUS;
        public String               path  = null;
        public String               step = null;
        public JSONObject           args = null;
        public SocketChannel        conn = null;
        
        public WcwRequest(String server, String path, int user, JSONObject args, SocketChannel conn) {
            this.server = server;
            this.inst = Integer.parseInt(args.getString("inst"), 16);
            this.user = user;
            this.path = path;
            this.step = args.has("step") ? args.getString("step") : STEP_PREPARE;
            this.args = args;
            this.conn = conn;
        }
    }
}
