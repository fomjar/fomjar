package com.ski.wca.biz;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ski.common.CommonDefinition;
import com.ski.common.CommonService;
import com.ski.common.bean.BeanChannelAccount;
import com.ski.common.bean.BeanCommodity;
import com.ski.common.bean.BeanGame;
import com.ski.common.bean.BeanGameAccount;
import com.ski.common.bean.BeanPlatformAccount;
import com.ski.wca.WechatInterface;
import com.ski.wca.monitor.TokenMonitor;

import fomjar.server.FjServerToolkit;
import fomjar.server.FjServerToolkit.FjAddress;
import fomjar.server.msg.FjDscpMessage;
import fomjar.server.msg.FjHttpRequest;
import fomjar.server.msg.FjJsonMessage;
import fomjar.server.msg.FjXmlMessage;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class WcWeb {
    
    private static final Logger logger = Logger.getLogger(WcWeb.class);
    
    public  static final String URL_KEY = "/ski-wcweb";
    
    private static final String STEP_PREPARE    = "prepare";
    private static final String STEP_SETUP      = "setup";
    private static final String STEP_APPLY      = "apply";
    private static final String STEP_SUCCESS    = "success";
    
    public static void dispatch(String server, FjHttpRequest req, SocketChannel conn) {
        logger.info("user request url: " + req.url());
        logger.debug("user request data: " + req.content());
        
        WcwResponse response = new WcwResponse();
        String url = req.url().contains("?") ? req.url().substring(0, req.url().indexOf("?")) : req.url();
        switch (url) {
        case URL_KEY:
        case URL_KEY + "/pay/recharge":
        case URL_KEY + "/pay/refund": {
            JSONObject args = req.argsToJson();
            if (!args.has("inst")) {
                logger.error("illegal argument: no inst param: " + args);
                break;
            }
            if (!args.has("user")) {
                if (!args.has("code")) {
                    logger.error("illegal request, no user or code param: " + args);
                    break;
                }
                String code = args.getString("code");
                FjJsonMessage rsp = WechatInterface.snsOauth2(FjServerToolkit.getServerConfig("wca.appid"), FjServerToolkit.getServerConfig("wca.secret"), code);
                if (!rsp.json().has("openid")) {
                    logger.error("user authorize failed: " + rsp);
                    break;
                }
                
                args.put("user", Integer.toHexString(CommonService.getChannelAccountByUserNChannel(rsp.json().getString("openid"), CommonService.CHANNEL_WECHAT).get(0).i_caid));
            }
            WcwRequest request = new WcwRequest(server, url, args, conn);
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
            case CommonDefinition.ISIS.INST_ECOM_UPDATE_CHANNEL_ACCOUNT:
                processUpdateChannelAccount(response, request);
                break;
            }
            break;
        }
        case URL_KEY + "/pay/recharge/success": {
            Document xml = req.contentToXml();
            processPayRechargeSuccess(response, server, xml);
            break;
        }
        default:
            fetchFile(response, req.url());
            break;
        }
        
        if (null != response.content) {
            logger.debug("user response data: " + response.content);
            WechatInterface.sendResponse(response.type, response.content, conn);
        } else {
            try {conn.close();}
            catch (IOException e) {e.printStackTrace();}
        }
    }
    
    private static void fetchFile(WcwResponse response, String url, Object... args) {
        File file = new File(FjServerToolkit.getServerConfig("wca.form.root") + (url.startsWith(URL_KEY) ? url.substring(URL_KEY.length()) : url));
        if (!file.isFile()) {
            logger.warn("not such file to fetch: " + file.getPath());
            response.type   = FjHttpRequest.CT_TEXT;
            return;
        }
        
        FileInputStream         fis = null;
        ByteArrayOutputStream   baos = null;
        try {
            byte[]  buf = new byte[1024 * 4];
            int     len = -1;
            fis     = new FileInputStream(file);
            baos    = new ByteArrayOutputStream();
            while (0 < (len = fis.read(buf))) baos.write(buf, 0, len);
            response.type       = getFileMime(file.getName());
            response.content    = baos.toString("utf-8");
            if (null != args && 0 < args.length) response.content = String.format(response.content, args);
        } catch (IOException e) {logger.error("fetch file failed, url: " + url, e);}
        finally {
            try {
                fis.close();
                baos.close();
            } catch (IOException e) {e.printStackTrace();}
        }
    }
    
    public static String generateUrl(String server, int inst, int user) {
        return generateUrl(server, URL_KEY, inst, user);
    }
    
    public static String generateUrl(String server, String url, int inst, int user) {
        FjAddress addr = FjServerToolkit.getSlb().getAddress(server);
        return String.format("http://%s%s%s?inst=%s&user=%s",
                addr.host,
                80 == addr.port ? "" : (":" + addr.port),
                url,
                Integer.toHexString(inst),
                Integer.toHexString(user));
    }
    
    private static String getChannelDesc(int channel) {
        switch (channel) {
        case CommonService.CHANNEL_TAOBAO: return "淘宝";
        case CommonService.CHANNEL_WECHAT: return "微信";
        case CommonService.CHANNEL_ALIPAY: return "支付宝";
        default: return "未知";
        }
    }
    
    private static String getFileMime (String name) {
        if (!name.contains(".")) return FjHttpRequest.CT_TEXT;
        
        String ext = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
        switch (ext) {
        case "html":
        case "htm":     return FjHttpRequest.CT_HTML;
        case "js":      return FjHttpRequest.CT_JS;
        case "css":
        case "less":    return FjHttpRequest.CT_CSS;
        case "xml":     return FjHttpRequest.CT_XML;
        case "json":    return FjHttpRequest.CT_JSON;
        default:    return FjHttpRequest.CT_TEXT;
        }
    }
    
    private static void processApplyPlatformAccountMoney(WcwResponse response, WcwRequest request) {
        switch (request.url) {
        case URL_KEY + "/pay/recharge":
            processApplyPlatformAccountMoney_Recharge(response, request);
            break;
        case URL_KEY + "/pay/refund":
            processApplyPlatformAccountMoney_Refund(response, request);
            break;
        }
    }
    
    private static Set<Integer> cache_user_recharge = new HashSet<Integer>();

    private static void processApplyPlatformAccountMoney_Recharge(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            long timestamp  = System.currentTimeMillis() / 1000;
            String nonceStr = Long.toHexString(System.currentTimeMillis());
            fetchFile(response, "/apply_platform_account_money_recharge.html",
                    request.user,
                    FjServerToolkit.getServerConfig("wca.appid"),
                    timestamp,
                    nonceStr,
                    WechatInterface.createSignature4Config(nonceStr, TokenMonitor.getInstance().ticket(), timestamp, WcWeb.generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY, request.user)));
            break;
        }
        case STEP_APPLY: {
            String money = request.args.getString("money");
            String terminal = "127.0.0.1";
            try {terminal = ((InetSocketAddress) request.conn.getRemoteAddress()).getAddress().getHostAddress();}
            catch (IOException e) {logger.error("get user terminal address failed", e);}
            String url = generateUrl(request.server, URL_KEY + "/pay/recharge/success", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY, request.user);
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
            
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = json_prepay.toString();
            break;
        }
        case STEP_SUCCESS: {
            fetchFile(response, "/message_success.html", "充值成功", "", "");
            break;
        }
        }
    }
    
    private static void processApplyPlatformAccountMoney_Refund(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/apply_platform_account_money_refund.html", request.user);
            break;
        }
        case STEP_SETUP: {
            BeanChannelAccount user_alipay = CommonService.getChannelAccountRelatedByCaidNChannel(request.user, CommonService.CHANNEL_ALIPAY).get(0);
            JSONObject desc = new JSONObject();
            desc.put("user", user_alipay.c_user);
            desc.put("name", user_alipay.c_name);
            desc.put("money", CommonService.prestatementByCaid(request.user)[0]);
            JSONObject args = new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args.put("desc", desc);
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args.toString();
            break;
        }
        case STEP_APPLY: {
            float money = CommonService.prestatementByCaid(request.user)[0];
            JSONObject args = new JSONObject();
            args.put("caid",    request.user);
            args.put("money",   -money);
            FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY, args);
            
            response.type = FjHttpRequest.CT_JSON;
            response.content = rsp.args().toString();
            break;
        }
        case STEP_SUCCESS: {
            fetchFile(response, "/message_success.html", "退款成功", "", "");
            break;
        }
        }
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
    private static void processPayRechargeSuccess(WcwResponse response, String server, Document xml) {
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
        
        response.type       = FjHttpRequest.CT_XML;
        response.content    = "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
    }
    
    private static void processApplyRentBegin(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            if (!request.args.has("gid")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数错误");
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args.toString();
                break;
            }
            int gid = Integer.parseInt(request.args.getString("gid"), 16);
            fetchFile(response, "/apply_rent_begin.html", request.user, gid);
            break;
        }
//        case STEP_SETUP: break;
        case STEP_APPLY: {
            if (!request.args.has("gid") || !request.args.has("type")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数错误");
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args.toString();
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
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args_rsp.toString();
                break;
            }
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_BEGIN, request.user) + "&step=success");
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args_rsp.toString();
            break;
        }
        case STEP_SUCCESS: {
            fetchFile(response, "/message_success.html", "起租成功", "", "");
            break;
        }
        }
    }
    
    private static void processApplyRentEnd(WcwResponse response, WcwRequest request) {
        if (!request.args.has("oid") || !request.args.has("csn")) {
            JSONObject args = new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
            args.put("desc", "参数错误");
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args.toString();
            return;
        }
        int oid = Integer.parseInt(request.args.getString("oid"), 16);
        int csn = Integer.parseInt(request.args.getString("csn"), 16);
    	switch (request.step) {
    	case STEP_PREPARE: {
    		fetchFile(response, "/apply_rent_end.html", request.user, oid, csn);
    		break;
    	}
//    	case STEP_SETUP: break;
    	case STEP_APPLY: {
            JSONObject args = new JSONObject();
            args.put("caid",    request.user);
            args.put("oid", 	oid);
            args.put("csn", 	csn);
            FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_END, args);
            if (!CommonService.isResponseSuccess(rsp)) {
                JSONObject args_rsp = new JSONObject();
                args_rsp.put("code", CommonService.getResponseCode(rsp));
                args_rsp.put("desc", CommonService.getResponseDesc(rsp));
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args_rsp.toString();
                break;
            }
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", generateUrl(request.server, CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_END, request.user) + "&step=success");
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args_rsp.toString();
    	}
        case STEP_SUCCESS: {
            fetchFile(response, "/message_success.html", "退租成功", "", "");
            break;
        }
    	}
    }
    
    private static void processQueryGame(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            if (request.args.has("gid")) fetchFile(response, "/query_game_by_gid.html", request.user, Integer.parseInt(request.args.getString("gid"), 16));
            else if (request.args.has("tag")) fetchFile(response, "/query_game_by_tag.html", request.user, request.args.getString("tag"));
            else fetchFile(response, "/query_game.html", request.user);
            break;
        }
        case STEP_SETUP: {
        	if (request.args.has("word")) {
                String word = request.args.getString("word");
                JSONArray desc = new JSONArray();
                CommonService.getGameAll().values()
                        .stream()
                        .filter(game->game.getDisplayName().toLowerCase().contains(word.toLowerCase()))
                        .forEach(game->desc.add(gameToJson(game)));
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args.put("desc", desc);
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args.toString();
            } else if (request.args.has("tag")) {
            	String tag = request.args.getString("tag");
            	JSONArray desc = new JSONArray();
            	CommonService.getGameByTag(tag).forEach(game->desc.add(gameToJson(game)));
	            JSONObject args = new JSONObject();
	            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
	            args.put("desc", desc);
	            response.type       = FjHttpRequest.CT_JSON;
	            response.content    = args.toString();
            } else if (request.args.has("gid")) {
	            int gid = Integer.parseInt(request.args.getString("gid"), 16);
	            JSONObject args = new JSONObject();
	            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
	            args.put("desc", gameToJson(CommonService.getGameByGid(gid)));
	            response.type       = FjHttpRequest.CT_JSON;
	            response.content    = args.toString();
            } else {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数错误");
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args.toString();
            }
            break;
        }
        }
    }
    
    private static JSONObject gameToJson(BeanGame game) {
    	JSONObject json = new JSONObject();
    	json.put("gid",             game.i_gid);
    	json.put("name_zh_cn",		game.c_name_zh_cn);
    	json.put("name_zh_hk",		game.c_name_zh_hk);
    	json.put("name_en",         game.c_name_en);
    	json.put("name_ja",         game.c_name_ja);
    	json.put("name_ko",         game.c_name_ko);
    	json.put("name_other",		game.c_name_other);
    	json.put("platform",        game.c_platform);
    	json.put("category",		game.c_category);
    	json.put("language",		game.c_language);
    	json.put("size",			game.c_size);
    	json.put("vendor",			game.c_vendor);
    	json.put("sale",            game.t_sale);
    	json.put("url_icon",        game.c_url_icon);
    	json.put("url_cover",       game.c_url_cover);
    	json.put("url_poster",      JSONArray.fromObject(game.c_url_poster.split(" ")));
    	json.put("introduction",    game.c_introduction);
    	json.put("version",      	game.c_version);
    	
    	json.put("display_name",    game.getDisplayName());
    	json.put("price_a",         CommonService.getGameRentPriceByGid(game.i_gid, CommonService.RENT_TYPE_A).i_price);
    	json.put("price_b",         CommonService.getGameRentPriceByGid(game.i_gid, CommonService.RENT_TYPE_B).i_price);
    	return json;
    }
    
    private static void processQueryOrder(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/query_order.html", request.user);
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
	            response.type       = FjHttpRequest.CT_JSON;
	            response.content    = args.toString();
        	} else {
	            JSONArray desc = new JSONArray();
	            CommonService.getOrderByCaid(request.user).forEach(o->{
	                        o.commodities.values().forEach(c->desc.add(commodityToJson(c)));
	                    });
	            JSONObject args = new JSONObject();
	            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
	            args.put("desc", desc);
	            response.type       = FjHttpRequest.CT_JSON;
	            response.content    = args.toString();
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
        json.put("game",     CommonService.getGameByGaid(Integer.parseInt(c.c_arg0, 16)).stream().map(game->game.getDisplayName()).collect(Collectors.joining("; ")));
        // 预结算
        if (!c.isClose()) {
            json.put("expense", CommonService.prestatementByCommodity(c));
            json.put("pass", account.c_pass);
        }
        
        return json;
    }
    
    private static void processQueryPlatformAccountMap(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/query_platform_account_map.html", request.user);
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
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args.toString();
            break;
        }
        }
    }
    
    private static void processQueryPlatformAccountMoney(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/query_platform_account_money.html", request.user);
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
            
            JSONObject args = new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args.put("desc", desc);
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args.toString();
            break;
        }
        }
    }
    
    private static void processUpdateChannelAccount(WcwResponse response, WcwRequest request) {
        switch (request.step) {
        case STEP_PREPARE: {
            fetchFile(response, "/update_channel_account.html",
                    request.user,
                    request.args.has("caid") ? String.valueOf(Integer.parseInt(request.args.getString("caid"), 16)) : "null",
                    request.args.has("channel") ? String.valueOf(Integer.parseInt(request.args.getString("channel"), 16)) : "null");
            break;
        }
        case STEP_SETUP: {
            int caid = Integer.parseInt(request.args.getString("caid"), 16);
            BeanChannelAccount user = CommonService.getChannelAccountByCaid(caid);
            JSONObject desc = new JSONObject();
            desc.put("caid",     user.i_caid);
            desc.put("channel",  user.i_channel);
            desc.put("user",     user.c_user);
            desc.put("name",     user.c_name);
            desc.put("phone",    user.c_phone);
            desc.put("gender",   user.i_gender);
            desc.put("birth",    user.t_birth);
            desc.put("address",  user.c_address);
            desc.put("zipcode",  user.c_zipcode);
            desc.put("create",   user.t_create);
            JSONObject args = new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args.put("desc", desc);
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args.toString();
            break;
        }
        case STEP_APPLY: {
            if (!request.args.has("channel") || !request.args.has("_user") || !request.args.has("name")) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "参数不完整");
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args.toString();
                break;
            }
            int     caid    = request.args.has("caid") ? Integer.parseInt(request.args.getString("caid"), 16) : -1;
            int     channel = request.args.getInt("channel");
            String  user    = request.args.getString("_user");
            String  name    = request.args.getString("name");
            // 更新已有用户
            if (-1 != caid) {
                // 未找到已有用户
                if (null == CommonService.getChannelAccountByCaid(caid)) {
                    JSONObject args = new JSONObject();
                    args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                    args.put("desc", "用户不存在");
                    response.type       = FjHttpRequest.CT_JSON;
                    response.content    = args.toString();
                    break;
                }
            }
            // 新创建用户但已有重复渠道用户
            if (-1 == caid && !CommonService.getChannelAccountRelatedByCaidNChannel(request.user, channel).isEmpty()) {
                JSONObject args = new JSONObject();
                args.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
                args.put("desc", "已经关联了" + getChannelDesc(channel) + "用户，不能再重复关联");
                response.type       = FjHttpRequest.CT_JSON;
                response.content    = args.toString();
                break;
            }
            {   // 创建用户
                JSONObject args_cdb = new JSONObject();
                if (-1 != caid) args_cdb.put("caid", caid);
                args_cdb.put("channel", channel);
                args_cdb.put("user",    user);
                args_cdb.put("name",    name);
                FjDscpMessage rsp = CommonService.send("cdb", CommonDefinition.ISIS.INST_ECOM_UPDATE_CHANNEL_ACCOUNT, args_cdb);
                
                if (!CommonService.isResponseSuccess(rsp)) {
                    response.type       = FjHttpRequest.CT_JSON;
                    response.content    = rsp.args().toString();
                    break;
                }
                caid = Integer.parseInt(CommonService.getResponseDesc(rsp), 16);
            }
            CommonService.updateChannelAccount();
            CommonService.updatePlatformAccount();
            CommonService.updatePlatformAccountMap();
            // 合并用户
            if (CommonService.getPlatformAccountByCaid(caid) != CommonService.getPlatformAccountByCaid(request.user)) {
                int paid = CommonService.getPlatformAccountByCaid(caid);
                JSONObject args_cdb = new JSONObject();
                args_cdb.put("paid_to",     CommonService.getPlatformAccountByCaid(request.user));
                args_cdb.put("paid_from",   paid);
                FjDscpMessage rsp = CommonService.send("cdb", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MERGE, args_cdb);
                
                if (!CommonService.isResponseSuccess(rsp)) {
                    response.type       = FjHttpRequest.CT_JSON;
                    response.content    = rsp.args().toString();
                    break;
                }
            }
            JSONObject args = new JSONObject();
            args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            response.type       = FjHttpRequest.CT_JSON;
            response.content    = args.toString();
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
    
    private WcWeb() {}
    
    private static class WcwRequest {
        public String           server = null;
        public int              inst = -1;
        public int              user = -1;
        public String           url  = null;
        public String           step = null;
        public JSONObject       args = null;
        public SocketChannel    conn = null;
        
        public WcwRequest(String server, String url, JSONObject args, SocketChannel conn) {
            this.server = server;
            if (args.has("inst")) this.inst = Integer.parseInt(args.getString("inst"), 16);
            if (args.has("user")) this.user = Integer.parseInt(args.getString("user"), 16);
            this.url  = url;
            this.step = args.has("step") ? args.getString("step") : STEP_PREPARE;
            this.args = args;
            this.conn = conn;
        }
    }
    
    private static class WcwResponse {
        public String type = FjHttpRequest.CT_TEXT;
        public String content = null;
    }
}
