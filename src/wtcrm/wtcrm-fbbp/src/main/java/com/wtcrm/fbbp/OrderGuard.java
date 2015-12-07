package com.wtcrm.fbbp;

import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

import fomjar.server.FjJsonMsg;
import fomjar.server.FjLoopTask;
import fomjar.server.FjToolkit;

public class OrderGuard extends FjLoopTask {
	
	private static final Logger logger = Logger.getLogger(OrderGuard.class);
	
	private String serverName;
	
	public OrderGuard(String serverName) {
		long time = Long.parseLong(FjToolkit.getServerConfig("fbbp.reload-order-interval"));
		time *= 1000L;
		setDelay(time);
		setInterval(time);
		this.serverName = serverName;
	}

	@Override
	public void perform() {
		FjJsonMsg msg = new FjJsonMsg();
		msg.json().put("fs", serverName);
		msg.json().put("ts", "wa");
		msg.json().put("sid", String.valueOf(System.currentTimeMillis()));
		msg.json().put("ae-cmd", "ae.taobao.order-list-new");
		msg.json().put("ae-arg", JSONObject.fromObject(null));
		FjToolkit.getSender(serverName).send(msg);
		logger.debug("send request to get latest order list: " + msg);
	}
	
	public void start() {
		if (isRun()) {
			logger.warn("order-guard has already started");
			return;
		}
		new Thread(this, "order-guard").start();
	}
}
