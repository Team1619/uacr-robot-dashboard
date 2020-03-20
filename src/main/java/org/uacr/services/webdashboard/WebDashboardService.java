package org.uacr.services.webdashboard;

import org.uacr.shared.abstractions.*;
import org.uacr.utilities.injection.Inject;
import org.uacr.utilities.logging.LogManager;
import org.uacr.utilities.logging.Logger;
import org.uacr.utilities.services.ScheduledService;
import org.uacr.utilities.services.Scheduler;


/**
 * WebDashboardService is the service the creates and runs the webdashboard client in sim and on the robot
 *
 * @author Matthew Oates
 */

public class WebDashboardService implements ScheduledService {

	private static final Logger sLogger = LogManager.getLogger(WebDashboardService.class);

	private final InputValues fSharedInputValues;
	private final RobotConfiguration fRobotConfiguration;
	private WebDashboardServer fWebDashboardServer;
	private double fPreviousTime;
	private long FRAME_TIME_THRESHOLD;

	@Inject
	public WebDashboardService(EventBus eventBus, FMS fms, InputValues inputValues, OutputValues outputValues, RobotConfiguration robotConfiguration) {
		fSharedInputValues = inputValues;
		fRobotConfiguration = robotConfiguration;

		fWebDashboardServer = new WebDashboardServer(eventBus, fms, inputValues, outputValues, robotConfiguration);
	}

	@Override
	public void startUp() throws Exception {
		sLogger.debug("Starting WebDashboardService");

		fPreviousTime = System.currentTimeMillis();
		FRAME_TIME_THRESHOLD = fRobotConfiguration.getInt("global_timing", "frame_time_threshold_webdashboard_service");

		fWebDashboardServer.start();
		sLogger.debug("WebDashboardService started");
	}

	@Override
	public void runOneIteration() throws Exception {

		double frameStartTime = System.currentTimeMillis();

		fWebDashboardServer.update();

		// Check for delayed frames
		double currentTime = System.currentTimeMillis();
		double frameTime = currentTime - frameStartTime;
		double totalCycleTime = currentTime - fPreviousTime;
		fSharedInputValues.setNumeric("ipn_frame_time_webdashboard_service", frameTime);
		if (frameTime > FRAME_TIME_THRESHOLD) {
			sLogger.debug("********** WebDashboard Service frame time = {}", frameTime);
		}
		fPreviousTime = currentTime;
	}

	@Override
	public void shutDown() throws Exception {
		sLogger.debug("Shutting down RobotWebDashboardService");

		fWebDashboardServer.stop();

		sLogger.debug("WebDashboardService shut down");
	}

	@Override
	public Scheduler scheduler() {
		return new Scheduler(1000 / 60);
	}
}
