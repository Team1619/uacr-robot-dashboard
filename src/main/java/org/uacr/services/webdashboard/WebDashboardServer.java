package org.uacr.services.webdashboard;

import org.uacr.shared.abstractions.*;
import org.uacr.utilities.logging.LogManager;
import org.uacr.utilities.logging.Logger;

/**
 * WebDashboardServer creates and manages a WebsocketServer on the correct ip
 *
 * @author Matthew Oates
 */

public class WebDashboardServer {

	private static final Logger sLogger = LogManager.getLogger(WebDashboardServer.class);
	private static final int sPort = 5800;

	private WebHttpServer fWebHttpServer;
	private WebsocketServer fWebsocketServer;

	public WebDashboardServer(EventBus eventBus, FMS fms, InputValues inputValues, OutputValues outputValues, RobotConfiguration robotConfiguration) {
		fWebHttpServer = new WebHttpServer(sPort);
		fWebsocketServer = new WebsocketServer(sPort + 1, eventBus, fms, inputValues, outputValues, robotConfiguration);
	}

	public void start() {
		fWebsocketServer.initialize();
	}

	public void update() {
		fWebsocketServer.broadcastToWebDashboard();
	}

	public void stop() {

	}
}
