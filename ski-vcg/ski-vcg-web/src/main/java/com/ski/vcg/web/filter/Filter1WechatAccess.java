package com.ski.vcg.web.filter;

import java.nio.channels.SocketChannel;

import org.apache.log4j.Logger;

import com.ski.vcg.web.wechat.WechatInterface;

import fomjar.server.FjServer;
import fomjar.server.msg.FjHttpRequest;
import fomjar.server.msg.FjHttpResponse;
import fomjar.server.web.FjWebFilter;

public class Filter1WechatAccess extends FjWebFilter {

    private static final Logger logger = Logger.getLogger(Filter1WechatAccess.class);

    @Override
    public boolean filter(FjHttpResponse response, FjHttpRequest request, SocketChannel conn, FjServer server) {
        if (request.url().startsWith("/ski-wechat") && request.urlArgs().containsKey("echostr")) {
            logger.info("wechat access: " + request.url());
            WechatInterface.access(conn, request);
            return false;
        }
        return true;
    }

}
