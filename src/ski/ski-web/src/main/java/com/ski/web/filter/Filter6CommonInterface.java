package com.ski.web.filter;

import java.awt.geom.Point2D;
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
import com.ski.web.baidu.BaiduMapInterface;
import com.ski.web.wechat.WechatBusiness;
import com.ski.web.wechat.WechatInterface;

import fomjar.server.FjServerToolkit;
import fomjar.server.msg.FjDscpMessage;
import fomjar.server.msg.FjHttpRequest;
import fomjar.server.msg.FjHttpResponse;
import fomjar.server.msg.FjXmlMessage;
import fomjar.server.web.FjWebFilter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class Filter6CommonInterface extends FjWebFilter {
    
    private static final Logger logger = Logger.getLogger(Filter6CommonInterface.class);
    
    private static final String URL_KEY = "/ski-web";
    
    private WechatBusiness wechat;
    
    public Filter6CommonInterface(WechatBusiness wechat) {
        this.wechat = wechat;
    }

    @Override
    public boolean filter(FjHttpResponse response, FjHttpRequest request, SocketChannel conn) {
        if (!request.path().startsWith(URL_KEY)) return true;
        
        logger.info(String.format("user common command: %s - %s", request.url(), request.argsToJson()));
        
        switch (request.path()) {
        case URL_KEY + "/pay/recharge/success":
            processPayRechargeSuccess(response, request.contentToXml());
            break;
        default: {
            JSONObject args = request.argsToJson();
            if (args.containsKey("inst")) {
                switch (getIntFromArgs(args, "inst")) {
                case CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY:
                    processApplyPlatformAccountMoney(response, request, conn);
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
                case CommonDefinition.ISIS.INST_ECOM_QUERY_PLATFORM_ACCOUNT:
                    processQueryPlatformAccount(response, request);
                    break;
                case CommonDefinition.ISIS.INST_ECOM_QUERY_PLATFORM_ACCOUNT_MAP:
                    processQueryPlatformAccountMap(response, request);
                    break;
                case CommonDefinition.ISIS.INST_ECOM_UPDATE_PLATFORM_ACCOUNT_MAP:
                    processUpdatePlatformAccountMap(response, request);
                    break;
                }
            }
            break;
        }
        }
        return true;
    }
        
    private void processApplyPlatformAccountMoney(FjHttpResponse response, FjHttpRequest request, SocketChannel conn) {
        switch (request.path()) {
        case URL_KEY + "/pay/recharge/prepare":
            processApplyPlatformAccountMoney_Recharge_Prepare(response, request);
            break;
        case URL_KEY + "/pay/recharge/apply":
            processApplyPlatformAccountMoney_Recharge_Apply(response, request, conn);
            break;
        case URL_KEY + "/pay/refund":
            processApplyPlatformAccountMoney_Refund(response, request, conn);
            break;
        }
    }
    
    private void processApplyPlatformAccountMoney_Recharge_Prepare(FjHttpResponse response, FjHttpRequest request) {
        long timestamp  = System.currentTimeMillis() / 1000;
        String noncestr = Long.toHexString(System.currentTimeMillis());
        JSONObject args = new JSONObject();
        args.put("appid",       FjServerToolkit.getServerConfig("web.wechat.appid"));
        args.put("timestamp",   String.valueOf(timestamp));
        args.put("noncestr",    noncestr);
        args.put("signature",   WechatInterface.createSignature4Config(noncestr,
                wechat.token_monitor().ticket(),
                timestamp,
                String.format("http://%s/wechat/apply_platform_account_money_recharge.html", FjServerToolkit.getSlb().getAddress("web").host)));
        response.attr().put("Content-Type", "application/json");
        response.content(args);
    }
        
    private Set<Integer> cache_user_recharge = new HashSet<Integer>();

    private void processApplyPlatformAccountMoney_Recharge_Apply(FjHttpResponse response, FjHttpRequest request, SocketChannel conn) {
        JSONObject args = request.argsToJson();
        int user = Integer.parseInt(request.cookie().get("user"), 16);
        String money = args.getString("money");
        String terminal = "127.0.0.1";
        try {terminal = ((InetSocketAddress) conn.getRemoteAddress()).getAddress().getHostAddress();}
        catch (IOException e) {logger.error("get user terminal address failed", e);}
        FjXmlMessage rsp = WechatInterface.prepay(
                "VC电玩-充值",
                "您已成功充值" + money + "元",
                (int) (Float.parseFloat(money) * 100),
                terminal,
                String.format("http://%s%s/pay/recharge/success", FjServerToolkit.getSlb().getAddress("web").host, URL_KEY),
                CommonService.getChannelAccountByCaid(user).c_user);
        
        String timeStamp    = String.valueOf(System.currentTimeMillis() / 1000);
        String nonceStr     = Long.toHexString(System.currentTimeMillis());
        JSONObject json_prepay = xml2json(rsp.xml());
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("appId",        FjServerToolkit.getServerConfig("web.wechat.appid"));
        map.put("timeStamp",    timeStamp);
        map.put("nonceStr",     nonceStr);
        map.put("package",      "prepay_id=" + json_prepay.getString("prepay_id"));
        map.put("signType",     "MD5");
        String paySign = WechatInterface.createSignature4Pay(map);
        
        JSONObject json_pay = new JSONObject();
        json_pay.put("appId",       FjServerToolkit.getServerConfig("web.wechat.appid"));
        json_pay.put("timeStamp",   timeStamp);
        json_pay.put("nonceStr",    nonceStr);
        json_pay.put("package",     "prepay_id=" + json_prepay.getString("prepay_id"));
        json_pay.put("signType",    "MD5");
        json_pay.put("paySign",     paySign);
        json_prepay.put("pay", json_pay);
        
        cache_user_recharge.add(user);
        
        response.attr().put("Content-Type", "application/json");
        response.content(json_prepay);
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
    private void processPayRechargeSuccess(FjHttpResponse response, Document xml) {
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
            msg_cdb.json().put("fs",   FjServerToolkit.getAnyServer().name());
            msg_cdb.json().put("ts",   "bcs");
            msg_cdb.json().put("inst", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY);
            msg_cdb.json().put("args", args_cdb);
            FjServerToolkit.getAnySender().send(msg_cdb);
        }
        
        response.attr().put("Content-Type", "text/xml");
        response.content("<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>");
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

    private void processApplyPlatformAccountMoney_Refund(FjHttpResponse response, FjHttpRequest request, SocketChannel conn) {
        int user = Integer.parseInt(request.cookie().get("user"), 16);
        float money = CommonService.prestatementByCaid(user)[0];
        
        JSONObject args_bcs = new JSONObject();
        args_bcs.put("caid",    user);
        args_bcs.put("money",   -money);
        FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MONEY, args_bcs);
        JSONObject args_rsp = rsp.argsToJsonObject();
        
        if (CommonService.isResponseSuccess(rsp)) {
            // 发红包
            String terminal = "127.0.0.1";
            try {terminal = ((InetSocketAddress) conn.getRemoteAddress()).getAddress().getHostAddress();}
            catch (IOException e) {logger.error("get user terminal address failed", e);}
            sendredpack(terminal, CommonService.getChannelAccountByCaid(user).c_user, money);
        }
        
        logger.error("user pay refund: " + args_rsp);
        response.attr().put("Content-Type", "application/json");
        response.content(args_rsp);
    }
    
    private static void sendredpack(String terminal, String user, float money) {
        float max = Float.parseFloat(FjServerToolkit.getServerConfig("web.wechat.redpack.max"));
        long  interval = Long.parseLong(FjServerToolkit.getServerConfig("web.wechat.redpack.interval"));
        new Thread(()->{
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
        }).start();
    }
    
    private void processApplyRentBegin(FjHttpResponse response, FjHttpRequest request) {
        JSONObject args = request.argsToJson();
        int user    = Integer.parseInt(request.cookie().get("user"), 16);
        int gid     = getIntFromArgs(args, "gid");
        int type    = getIntFromArgs(args, "type");
        JSONObject args_bcs = new JSONObject();
        args_bcs.put("platform",    CommonService.CHANNEL_WECHAT);
        args_bcs.put("caid",        user);
        args_bcs.put("gid",         gid);
        args_bcs.put("type",        type);
        FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_BEGIN, args_bcs);
        if (!CommonService.isResponseSuccess(rsp)) {
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonService.getResponseCode(rsp));
            args_rsp.put("desc", CommonService.getResponseDesc(rsp));
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
            return;
        }
        JSONObject args_rsp = new JSONObject();
        args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
        args_rsp.put("desc", null);
        response.attr().put("Content-Type", "application/json");
        response.content(args_rsp);
    }
    
    private void processApplyRentEnd(FjHttpResponse response, FjHttpRequest request) {
        JSONObject args = request.argsToJson();
        int user    = Integer.parseInt(request.cookie().get("user"), 16);
        int oid     = getIntFromArgs(args, "oid");
        int csn     = getIntFromArgs(args, "csn");
        JSONObject args_bcs = new JSONObject();
        args_bcs.put("caid",    user);
        args_bcs.put("oid",     oid);
        args_bcs.put("csn",     csn);
        FjDscpMessage rsp = CommonService.send("bcs", CommonDefinition.ISIS.INST_ECOM_APPLY_RENT_END, args_bcs);
        if (!CommonService.isResponseSuccess(rsp)) {
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonService.getResponseCode(rsp));
            args_rsp.put("desc", CommonService.getResponseDesc(rsp));
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
            return;
        }
        JSONObject args_rsp = new JSONObject();
        args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
        args_rsp.put("desc", null);
        response.attr().put("Content-Type", "application/json");
        response.content(args_rsp);
    }
    
    private void processQueryGame(FjHttpResponse response, FjHttpRequest request) {
        JSONObject args = request.argsToJson();
        int user = Integer.parseInt(request.cookie().get("user"), 16);
        
        if (args.has("gid")) {
            int gid = getIntFromArgs(args, "gid");
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            JSONObject desc = tojson(CommonService.getGameByGid(gid));
            desc.put("ccs", tojson_ccs(user, gid, Integer.parseInt(FjServerToolkit.getServerConfig("web.cc.max"))));
            args_rsp.put("desc", desc);
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        } else if (args.has("category")) {
            String[] categorys = args.getString("category").split("_");
            JSONArray desc = new JSONArray();
            CommonService.getGameByCategory(categorys).forEach(game->desc.add(tojson(game)));
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", desc);
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        } else if (args.has("language")) {
            String language = args.getString("language");
            JSONArray desc = new JSONArray();
            CommonService.getGameByLanguage(language).forEach(game->desc.add(tojson(game)));
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", desc);
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        } else if (args.has("tag")) {
            String tag = args.getString("tag");
            JSONArray desc = new JSONArray();
            CommonService.getGameByTag(tag).forEach(game->desc.add(tojson(game)));
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", desc);
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        } else if (args.has("word")) {
            String word = args.getString("word");
            JSONArray desc = new JSONArray();
            CommonService.getGameAll().values()
                    .stream()
                    .filter(game->game.getDisplayName().toLowerCase().contains(word.toLowerCase()))
                    .forEach(game->desc.add(tojson(game)));
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", desc);
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        } else {
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_ILLEGAL_ARGS);
            args_rsp.put("desc", "参数错误");
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        }
    }
    
    private void processQueryOrder(FjHttpResponse response, FjHttpRequest request) {
        JSONObject args = request.argsToJson();
        int user = Integer.parseInt(request.cookie().get("user"), 16);
        
        if (args.has("oid") && args.has("csn")) {
            int oid = getIntFromArgs(args, "oid");
            int csn = getIntFromArgs(args, "csn");
            BeanCommodity c = CommonService.getOrderByOid(oid).commodities.get(csn);
            
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", tojson(c));
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        } else {
            JSONArray desc = new JSONArray();
            CommonService.getOrderByPaid(CommonService.getPlatformAccountByCaid(user)).forEach(o->{
                        o.commodities.values().forEach(c->desc.add(tojson(c)));
                    });
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", desc);
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        }
    }
    
    private void processQueryPlatformAccount(FjHttpResponse response, FjHttpRequest request) {
        JSONObject args = request.argsToJson();
        if (args.containsKey("caid")) {
            int caid = getIntFromArgs(args, "caid");
            BeanPlatformAccount bean = CommonService.getPlatformAccountByPaid(CommonService.getPlatformAccountByCaid(caid));
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", tojson(bean));
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        } else {
            int caid = Integer.parseInt(request.cookie().get("user"), 16);
            BeanPlatformAccount bean = CommonService.getPlatformAccountByPaid(CommonService.getPlatformAccountByCaid(caid));
            JSONObject args_rsp = new JSONObject();
            args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
            args_rsp.put("desc", tojson(bean));
            response.attr().put("Content-Type", "application/json");
            response.content(args_rsp);
        }
    }
    
    private void processQueryPlatformAccountMap(FjHttpResponse response, FjHttpRequest request) {
        int user = Integer.parseInt(request.cookie().get("user"), 16);
        
        JSONObject args = new JSONObject();
        JSONArray desc = new JSONArray();
        CommonService.getChannelAccountRelatedAll(user).forEach(bean->{
            desc.add(tojson(bean));
        });
        args.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
        args.put("desc", desc);
        response.attr().put("Content-Type", "application/json");
        response.content(args);
    }
    
    private Map<Integer, String> cache_verify_code = new HashMap<Integer, String>();
    
    private void processUpdatePlatformAccountMap(FjHttpResponse response, FjHttpRequest request) {
        JSONObject args = request.argsToJson();
        int user = Integer.parseInt(request.cookie().get("user"), 16);
        
        if (args.has("phone")) {
            if (!args.has("verify")) {
                String phone    = args.getString("phone");
                String time     = String.valueOf(System.currentTimeMillis());
                String verify   = time.substring(time.length() - 4);
                {
                    JSONObject args_mma = new JSONObject();
                    args_mma.put("user",    phone);
                    args_mma.put("content", verify);
                    FjDscpMessage rsp = CommonService.send("mma", CommonDefinition.ISIS.INST_ECOM_APPLY_AUTHORIZE, args_mma);
                    if (!CommonService.isResponseSuccess(rsp)) {
                        JSONObject args_rsp = new JSONObject();
                        args_rsp.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                        args_rsp.put("desc", "发送失败，请稍候重试");
                        response.attr().put("Content-Type", "application/json");
                        response.content(args_rsp);
                        return;
                    }
                }
                
                cache_verify_code.put(user, verify);
                
                {
                    JSONObject args_rsp = new JSONObject();
                    args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                    args_rsp.put("desc", null);
                    response.attr().put("Content-Type", "application/json");
                    response.content(args_rsp);
                }
            } else {
                String  phone   = args.getString("phone");
                String  verify  = args.getString("verify");
                if (!cache_verify_code.containsKey(user)
                        || !verify.equals(cache_verify_code.get(user))) {
                    JSONObject args_rsp = new JSONObject();
                    args_rsp.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                    args_rsp.put("desc", "校验失败");
                    response.attr().put("Content-Type", "application/json");
                    response.content(args_rsp);
                    return;
                }
                cache_verify_code.remove(user);
                
                { // 更新手机号
                    JSONObject args_cdb = new JSONObject();
                    args_cdb.put("caid", user);
                    args_cdb.put("phone", phone);
                    FjDscpMessage rsp = CommonService.send("cdb", CommonDefinition.ISIS.INST_ECOM_UPDATE_CHANNEL_ACCOUNT, args_cdb);
                    if (!CommonService.isResponseSuccess(rsp)) {
                        JSONObject args_rsp = new JSONObject();
                        args_rsp.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                        args_rsp.put("desc", "更新手机失败，请稍候重试");
                        response.attr().put("Content-Type", "application/json");
                        response.content(args_rsp);
                        return;
                    }
                }
                
                List<BeanChannelAccount> users_taobao = CommonService.getChannelAccountByPhoneNChannel(phone, CommonService.CHANNEL_TAOBAO);
                if (1 == users_taobao.size()) { // 尝试关联
                    BeanChannelAccount user_taobao = users_taobao.get(0);
                    if (CommonService.getChannelAccountRelatedByCaidNChannel(user_taobao.i_caid, CommonService.CHANNEL_WECHAT).isEmpty()) { // 淘宝用户尚未被关联
                        JSONObject args_cdb = new JSONObject();
                        args_cdb.put("paid_from",   CommonService.getPlatformAccountByCaid(user));
                        args_cdb.put("paid_to",     CommonService.getPlatformAccountByCaid(user_taobao.i_caid));
                        FjDscpMessage rsp = CommonService.send("cdb", CommonDefinition.ISIS.INST_ECOM_APPLY_PLATFORM_ACCOUNT_MERGE, args_cdb);
                        if (!CommonService.isResponseSuccess(rsp)) {
                            JSONObject args_rsp = new JSONObject();
                            args_rsp.put("code", CommonDefinition.CODE.CODE_USER_AUTHORIZE_FAILED);
                            args_rsp.put("desc", "关联淘宝用户失败，请稍候重试");
                            response.attr().put("Content-Type", "application/json");
                            response.content(args_rsp);
                            return;
                        }
                    }
                }
                
                JSONObject args_rsp = new JSONObject();
                args_rsp.put("code", CommonDefinition.CODE.CODE_SYS_SUCCESS);
                args_rsp.put("desc", null);
                response.attr().put("Content-Type", "application/json");
                response.content(args_rsp);
            }
        }
    }
    
    private static int getIntFromArgs(JSONObject args, String name) {
        if (!args.containsKey(name)) return -1;
        
        Object obj = args.get(name);
        if (obj instanceof Integer) return (int) obj;
        else return Integer.parseInt(obj.toString(), 16);
    }
    
    private static JSONObject tojson(BeanPlatformAccount bean) {
        JSONObject json = new JSONObject();
        json.put("paid",    bean.i_paid);
        json.put("user",    bean.c_user);
        json.put("pass",    bean.c_pass);
        json.put("name",    bean.c_name);
        json.put("mobile",  bean.c_mobile);
        json.put("email",   bean.c_email);
        json.put("birth",   bean.t_birth);
        json.put("cash",    bean.i_cash);
        json.put("coupon",  bean.i_coupon);
        json.put("create",  bean.t_create);
        
        float[] prestatement = CommonService.prestatementByPaid(bean.i_paid);
        json.put("cash_rt", prestatement[0]);
        json.put("coupon_rt", prestatement[1]);
        
        return json;
    }
    
    private static JSONObject tojson(BeanGame game) {
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
    
    private static JSONObject tojson_ccs(int caid, int cid, int max) {
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
        json.put("conv", JSONArray.fromObject(cc_conv.stream().map(cc->tojson(cc)).collect(Collectors.toList())));
        json.put("near", JSONArray.fromObject(cc_near.stream().map(cc->tojson(cc)).collect(Collectors.toList())));
        json.put("trus", JSONArray.fromObject(cc_trus.stream().map(cc->tojson(cc)).collect(Collectors.toList())));
        json.put("sold", JSONArray.fromObject(cc_sold.stream().map(cc->tojson(cc)).collect(Collectors.toList())));
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
            Point2D.Double p = BaiduMapInterface.getCordinate(FjServerToolkit.getServerConfig("web.baidu.map.ak"), place);
            if (null != p) {
                cache_cordinate.put(place, p);
                return p;
            }
            return new Point2D.Double(0, 0);
        }
    }
    
    private static JSONObject tojson(BeanChannelCommodity bean) {
        JSONObject json = new JSONObject();
        json.put("time",            bean.t_time);
        json.put("channel",         bean.i_channel);
        json.put("item_url",        bean.c_item_url);
        json.put("item_cover",      bean.c_item_cover);
        json.put("item_name",       bean.c_item_name);
        json.put("item_remark",     bean.c_item_remark);
        json.put("item_sold",       bean.i_item_sold);
        json.put("item_price",      bean.c_item_price);
        json.put("express_price",   bean.i_express_price);
        json.put("shop_url",        bean.c_shop_url);
        json.put("shop_name",       bean.c_shop_name);
        json.put("shop_owner",      bean.c_shop_owner);
        json.put("shop_rate",       bean.c_shop_rate);
        json.put("shop_score",      bean.c_shop_score);
        json.put("shop_addr",       bean.c_shop_addr);
        return json;
    }
    
    private static JSONObject tojson(BeanCommodity bean) {
        JSONObject json = new JSONObject();
        json.put("oid",      bean.i_oid);
        json.put("csn",      bean.i_csn);
        json.put("count",    bean.i_count);
        json.put("price",    bean.i_price);
        json.put("begin",    bean.t_begin);
        json.put("end",      bean.t_end);
        json.put("expense",  bean.i_expense);
        json.put("remark",   bean.c_remark);
        json.put("arg0",     bean.c_arg0);
        json.put("arg1",     bean.c_arg1);
        json.put("arg2",     bean.c_arg2);
        json.put("arg3",     bean.c_arg3);
        json.put("arg4",     bean.c_arg4);
        json.put("arg5",     bean.c_arg5);
        json.put("arg6",     bean.c_arg6);
        json.put("arg7",     bean.c_arg7);
        json.put("arg8",     bean.c_arg8);
        json.put("arg9",     bean.c_arg9);
        
        BeanGameAccount account = CommonService.getGameAccountByGaid(Integer.parseInt(bean.c_arg0, 16));
        json.put("account",  account.c_user);
        json.put("type",     "A".equals(bean.c_arg1) ? "认证" : "B".equals(bean.c_arg1) ? "不认证" : "未知");
        json.put("game",     tojson(CommonService.getGameByGaid(account.i_gaid).get(0)));
        // 预结算
        if (!bean.isClose()) {
            json.put("expense", CommonService.prestatementByCommodity(bean));
            json.put("pass", account.c_pass);
        }
        
        return json;
    }
    
    private static JSONObject tojson(BeanChannelAccount bean) {
        JSONObject json = new JSONObject();
        json.put("caid",     bean.i_caid);
        json.put("channel",  bean.i_channel);
        json.put("user",     bean.c_user);
        json.put("name",     bean.c_name);
        json.put("phone",    bean.c_phone);
        json.put("gender",   bean.i_gender);
        json.put("birth",    bean.t_birth);
        json.put("address",  bean.c_address);
        json.put("zipcode",  bean.c_zipcode);
        json.put("create",   bean.t_create);
        return json;
    }
    
}