package org.uacr.services.webdashboard;

import org.uacr.events.sim.SimInputBooleanSetEvent;
import org.uacr.events.sim.SimInputNumericSetEvent;
import org.uacr.events.sim.SimInputVectorSetEvent;
import org.uacr.services.webdashboard.websocket.AbstractWebsocketServer;
import org.uacr.services.webdashboard.websocket.WebSocket;
import org.uacr.shared.abstractions.*;
import org.uacr.utilities.LimitedSizeQueue;
import org.uacr.utilities.logging.LogHandler;
import org.uacr.utilities.logging.LogManager;
import org.uacr.utilities.logging.Logger;
import org.uacr.utilities.services.Scheduler;

import javax.annotation.Nullable;
import java.util.*;

/**
 * WebsocketServer connects and communicates with the computer webdashboard server
 *
 * @author Matthew Oates
 */

public class WebsocketServer extends AbstractWebsocketServer implements LogHandler {

	private static final Logger sLogger = LogManager.getLogger(WebsocketServer.class);
	private final EventBus fEventBus;
	private final FMS fFMS;
	private final InputValues fSharedInputValues;
	private final OutputValues fSharedOutputValues;
	private final RobotConfiguration fRobotConfiguration;
	private final Set<WebSocket> fWebDashboardSockets = new HashSet<>();
	private final Set<WebSocket> fValuesSockets = new HashSet<>();
	private final Set<WebSocket> fMatchSockets = new HashSet<>();

	//Web sockets
	//Connects web page
	private final Set<WebSocket> fLogSockets = new HashSet<>();
	private final Map<String, Object> fLastMatchValues = new HashMap<>();
	private final Scheduler fLoggingScheduler = new Scheduler(250);
	private List<String> fAutoOriginList = new ArrayList<>();
	private List<String> fAutoDestinationList = new ArrayList<>();
	private List<String> fAutoActionList = new ArrayList<>();
	private Map<String, Double> fNumerics = new HashMap<>();
	private Map<String, Boolean> fBooleans = new HashMap<>();
	private Map<String, String> fStrings = new HashMap<>();
	private Map<String, Map<String, Double>> fVectors = new HashMap<>();
	private Map<String, Object> fOutputs = new HashMap<>();
	private Map<String, Double> fLastNumerics = new HashMap<>();
	private Map<String, Boolean> fLastBooleans = new HashMap<>();
	private Map<String, String> fLastStrings = new HashMap<>();
	private Map<String, Map<String, Double>> fLastVectors = new HashMap<>();
	private Map<String, Object> fLastOutputs = new HashMap<>();
	private Map<String, Map<String, Object>> fMatchValues = new HashMap<>();
	private Queue<Map<String, String>> fLogMessages = new LimitedSizeQueue<>(100);
	private Queue<Map<String, String>> fWebdashboadLogMessages = new LimitedSizeQueue<>(100);
	private StringBuilder fMainStringBuilder = new StringBuilder();
	private StringBuilder fSecondaryStringBuilder = new StringBuilder();
	private UrlFormData fSendFormData = new UrlFormData();
	private UrlFormData fReceiveFormData = new UrlFormData();
	private Map<String, Object> fAllMatchValues = new HashMap<>();

	public WebsocketServer(int port, EventBus eventBus, FMS fms, InputValues inputValues, OutputValues outputValues, RobotConfiguration robotConfiguration) {
		super(port);

		fEventBus = eventBus;
		fFMS = fms;
		fSharedInputValues = inputValues;
		fSharedOutputValues = outputValues;
		fRobotConfiguration = robotConfiguration;

		fSharedInputValues.setString("ips_selected_auto", "No Auto");

		LogManager.addLogHandler(this);
	}

	public void initialize() {
		Map<String, Object> config = fRobotConfiguration.getCategory("global_webdashboard");

		Object matchValuesConfig = config.get("match_values");
		if (matchValuesConfig instanceof HashMap) {
			fMatchValues = (Map<String, Map<String, Object>>) matchValuesConfig;
		}

		Object autoSelectorObject = config.get("auto_selector");
		if (autoSelectorObject instanceof Map) {
			Map<String, List<String>> autoSelector = (Map<String, List<String>>) autoSelectorObject;

			fAutoOriginList = autoSelector.get("origins");
			fAutoDestinationList = autoSelector.get("destinations");
			fAutoActionList = autoSelector.get("actions");
		}

		start();
	}

	//Puts a log message into the cue to be sent to the dashboard
	public void log(String type, String message) {
		fLogMessages.add(Map.of("type", type, "message", message));
		fWebdashboadLogMessages.add(Map.of("type", type, "message", message));
	}

	//Called by the service every frame
	public void broadcastToWebDashboard() {
		broadcastValuesDataToWebDashboard();

		broadcastMatchDataToWebDashboard();

		broadcastLogDataToWebDashboard();
	}

	//Send information for the values page
	private void broadcastValuesDataToWebDashboard() {
		if (fValuesSockets.isEmpty()) return;

		fMainStringBuilder.setLength(0);

		fNumerics.clear();
		fNumerics.putAll(fSharedInputValues.getAllNumerics());
		for (HashMap.Entry<String, Double> value : fNumerics.entrySet()) {
			if (!value.getValue().equals(fLastNumerics.get(value.getKey()))) {
				fMainStringBuilder.append("numeric*").append(value.getKey()).append("*").append(String.format("%6f", value.getValue())).append("~");
			}
		}
		fLastNumerics.clear();
		fLastNumerics.putAll(fNumerics);

		fBooleans.clear();
		fBooleans.putAll(fSharedInputValues.getAllBooleans());
		for (HashMap.Entry<String, Boolean> value : fBooleans.entrySet()) {
			if (!value.getValue().equals(fLastBooleans.get(value.getKey()))) {
				fMainStringBuilder.append("boolean*").append(value.getKey()).append("*").append(value.getValue()).append("~");
			}
		}
		fLastBooleans.clear();
		fLastBooleans.putAll(fBooleans);

		fStrings.clear();
		fStrings.putAll(fSharedInputValues.getAllStrings());
		for (HashMap.Entry<String, String> value : fStrings.entrySet()) {
			if (!value.getValue().equals(fLastStrings.get(value.getKey()))) {
				fMainStringBuilder.append("string*").append(value.getKey()).append("*").append(value.getValue()).append("~");
			}
		}
		fLastStrings.clear();
		fLastStrings.putAll(fStrings);

		fVectors.clear();
		fVectors.putAll(fSharedInputValues.getAllVectors());
		for (HashMap.Entry<String, Map<String, Double>> value : fVectors.entrySet()) {
			if (!value.getValue().equals(fLastVectors.get(value.getKey()))) {
				fMainStringBuilder.append("vector*").append(value.getKey());
				for (Map.Entry<String, Double> v : value.getValue().entrySet()) {
					fMainStringBuilder.append("*").append(v.getKey()).append(": ").append(v.getValue());
				}
				fMainStringBuilder.append("~");
			}
		}
		fLastVectors.clear();
		fLastVectors.putAll(fVectors);

		fOutputs.clear();
		fOutputs.putAll(fSharedOutputValues.getAllOutputs());
		for (HashMap.Entry<String, Object> value : fOutputs.entrySet()) {
			if (!value.getValue().equals(fLastOutputs.get(value.getKey()))) {
				fMainStringBuilder.append("output*").append(value.getKey()).append("*").append(value.getValue()).append("~");
			}
		}
		fLastOutputs.clear();
		fLastOutputs.putAll(fOutputs);

		if (fMainStringBuilder.length() > 0) {

			fSendFormData.clear();

			send(fValuesSockets, fSendFormData
					.add("response", "values")
					.add("values", fMainStringBuilder.substring(0, fMainStringBuilder.length() - 1))
					.getData());
		}
	}

	//Send information for the match web page
	private void broadcastMatchDataToWebDashboard() {
		if (fMatchSockets.isEmpty()) return;

		fAllMatchValues.clear();
		fAllMatchValues.putAll(fSharedInputValues.getAllNumerics());
		fAllMatchValues.putAll(fSharedInputValues.getAllBooleans());
		fAllMatchValues.putAll(fSharedInputValues.getAllStrings());
		fAllMatchValues.putAll(fSharedOutputValues.getAllOutputs());

		fMainStringBuilder.setLength(0);

		for (HashMap.Entry<String, Map<String, Object>> matchValue : fMatchValues.entrySet()) {
			String type = matchValue.getValue().get("type").toString();

			String name = matchValue.getKey();
			if (matchValue.getValue().containsKey("display_name")) {
				name = String.valueOf(matchValue.getValue().get("display_name"));
			}

			String value = "";
			if (fAllMatchValues.containsKey(matchValue.getKey())) {
				value = String.valueOf(fAllMatchValues.get(matchValue.getKey()));
			} else {
				if (type.equals("value") || type.equals("boolean") || type.equals("other") || type.equals("auto")) {
					value = "";
				} else if (type.equals("dial")) {
					value = "0";
				}
			}

			if (!(fLastMatchValues.containsKey(matchValue.getKey()) && fLastMatchValues.get(matchValue.getKey()).equals(value))) {
				if (type.equals("value") || type.equals("boolean") || type.equals("other") || type.equals("auto")) {
					fMainStringBuilder.append(type).append("*$#$*").append(name).append("*$#$*").append(value).append("~$#$~");
				} else if (type.equals("dial")) {
					String min = "0";
					if (matchValue.getValue().containsKey("min")) {
						min = String.valueOf(matchValue.getValue().get("min"));
					}

					String max = "10";
					if (matchValue.getValue().containsKey("max")) {
						max = String.valueOf(matchValue.getValue().get("max"));
					}

					fMainStringBuilder.append(type).append("*$#$*").append(name).append("*$#$*").append(value).append("*$#$*").append(min).append("*$#$*").append(max).append("~$#$~");
				} else if (type.equals("log")) {
					String level = "INFO";
					if (matchValue.getValue().containsKey("level")) {
						level = String.valueOf(matchValue.getValue().get("level")).toUpperCase();
					}

					fSecondaryStringBuilder.setLength(0);

					while (!fWebdashboadLogMessages.isEmpty()) {
						Map<String, String> data = fWebdashboadLogMessages.remove();

						if (LogManager.Level.valueOf(data.get("type")).getPriority() >= LogManager.Level.valueOf(level).getPriority()) {
							fSecondaryStringBuilder.append("TYPE:").append(data.get("type")).append("MESSAGE:").append(data.get("message"));
						}
					}

					if (fSecondaryStringBuilder.length() > 0) {
						value = fSecondaryStringBuilder.toString();

						fMainStringBuilder.append(type).append("*$#$*").append(name).append("*$#$*").append(fSecondaryStringBuilder).append("~$#$~");
					} else {
						value = "empty";
					}
				}
			}

			fLastMatchValues.put(matchValue.getKey(), value);
		}

		fSendFormData.clear();

		if (fMainStringBuilder.length() > 0) {
			send(fMatchSockets, fSendFormData
					.add("response", "match_values")
					.add("values", fMainStringBuilder.substring(0, fMainStringBuilder.length() - 5))
					.getData());
		}
	}

	//Sends information for the log web page
	private void broadcastLogDataToWebDashboard() {

		if (fLoggingScheduler.shouldRun()) {
			fLoggingScheduler.run();

			if (fLogSockets.isEmpty()) {
				return;
			}

			fMainStringBuilder.setLength(0);

			while (!fLogMessages.isEmpty()) {
				Map<String, String> data = fLogMessages.remove();

				fMainStringBuilder.append("TYPE:").append(data.get("type")).append("MESSAGE:").append(data.get("message"));
			}

			fSendFormData.clear();

			if (fMainStringBuilder.length() > 0) {
				send(fLogSockets, fSendFormData
						.add("response", "log")
						.add("messages", fMainStringBuilder.toString())
						.getData());
			}
		}
	}

	private String listToUrlFormDataList(List list) {
		StringBuilder urlFormDataList = new StringBuilder();

		for (Object item : list) {
			urlFormDataList.append(item.toString()).append("~");
		}
		if (urlFormDataList.length() > 0) {
			urlFormDataList = new StringBuilder(urlFormDataList.substring(0, urlFormDataList.length() - 1));
		}

		return urlFormDataList.toString();
	}

	//Called when a new websocket connection opens
	@Override
	public void onOpen(WebSocket socket) {
		try {
			switch (socket.getPath()) {
				case "/webdashboard": {
					fWebDashboardSockets.add(socket);

					sendAutoData();

					sendConnected();

					break;
				}
				case "/values": {
					fValuesSockets.add(socket);

					clearAllValues();

					break;
				}
				case "/match": {
					fMatchSockets.add(socket);

					sendAutoData();

					clearMatchValues();
					break;
				}
				case "/log": {
					fLogSockets.add(socket);
					break;
				}
			}
		} catch (Exception e) {
			onError(socket, e);
		}
	}

	//Called when a websocket connection closes
	@Override
	public void onClose(WebSocket webSocket) {
		removeSocket(webSocket);
	}

	//All messages received over websocket connections come here to be forwarded to the robot or web page
	@Override
	public void onMessage(WebSocket webSocket, String message) {

		fReceiveFormData.clear();
		fReceiveFormData.parse(message);

		if (fReceiveFormData.containsKey("request")) {
			switch (fReceiveFormData.get("request")) {
				case "all_values": {
					clearAllValues();
					break;
				}
				case "all_match_values": {
					clearMatchValues();
					break;
				}
				case "change_value": {
					switch (fReceiveFormData.get("type")) {
						case "numeric":
							fEventBus.post(new SimInputNumericSetEvent(fReceiveFormData.get("name"), Double.valueOf(fReceiveFormData.get("value"))));
							break;
						case "boolean":
							fEventBus.post(new SimInputBooleanSetEvent(fReceiveFormData.get("name"), Boolean.valueOf(fReceiveFormData.get("value"))));
							break;
						case "string":
							break;
						case "vector":
							Map<String, Double> vector = new HashMap<>();
							vector.putAll(fSharedInputValues.getVector(fReceiveFormData.get("name")));
							vector.put(fReceiveFormData.get("selected"), Double.valueOf(fReceiveFormData.get("value")));
							fEventBus.post(new SimInputVectorSetEvent(fReceiveFormData.get("name"), vector));
							break;
					}
					break;
				}
				case "get_auto_data": {
					sendAutoData();
					break;
				}
				case "set_auto_data": {
					fSharedInputValues.setString("ips_auto_origin", fReceiveFormData.get("auto_origin"));
					fSharedInputValues.setString("ips_auto_destination", fReceiveFormData.get("auto_destination"));
					fSharedInputValues.setString("ips_auto_action", fReceiveFormData.get("auto_action"));
					fSharedInputValues.setString("ips_selected_auto",
							fSharedInputValues.getString("ips_auto_origin") + ", " +
									fSharedInputValues.getString("ips_auto_destination") + ", " +
									fSharedInputValues.getString("ips_auto_action"));
					break;
				}
				case "set_fms_mode": {
					switch (fReceiveFormData.get("mode")) {
						case "auto":
							fFMS.setMode(FMS.Mode.AUTONOMOUS);
							return;
						case "teleop":
							fFMS.setMode(FMS.Mode.TELEOP);
							return;
						case "disabled":
							fFMS.setMode(FMS.Mode.DISABLED);
							return;
						case "test":
							fFMS.setMode(FMS.Mode.TEST);
							return;
					}
				}
				default: {
					sLogger.debug("Unknown data: " + fReceiveFormData);
				}
			}
		} else {
			sLogger.debug("Unknown message: " + message);
		}
	}

	//All websocket error are handled here
	@Override
	public void onError(@Nullable WebSocket webSocket, Exception e) {
		sLogger.error("{} exception on {}", e.getMessage(), webSocket);
		e.printStackTrace();
	}

	//Called when the server starts
	@Override
	public void onStart() {

	}

	//Sends robot connection status to the web page
	private synchronized void sendConnected() {
		String message = new UrlFormData()
				.add("response", "connected")
				.add("connected", "true")
				.getData();

		send(fWebDashboardSockets, message);
	}

	private void sendAutoData() {
		String response = new UrlFormData()
				.add("response", "auto_data")
				.add("auto_origin_list", listToUrlFormDataList(fAutoOriginList))
				.add("auto_destination_list", listToUrlFormDataList(fAutoDestinationList))
				.add("auto_action_list", listToUrlFormDataList(fAutoActionList))
				.getData();

		send(fWebDashboardSockets, response);
		send(fMatchSockets, response);
	}

	private void clearAllValues() {
		fLastNumerics.clear();
		fLastBooleans.clear();
		fLastStrings.clear();
		fLastVectors.clear();
		fLastOutputs.clear();
	}

	private void clearMatchValues() {
		fLastMatchValues.clear();
	}

	private void send(Set<WebSocket> sockets, String message) {
		sockets.forEach(socket -> {
			try {
				socket.send(message);
			} catch (Exception e) {
				sLogger.error(e);
			}
		});
	}

	private void removeSocket(WebSocket socket) {
		fWebDashboardSockets.remove(socket);
		fValuesSockets.remove(socket);
		fMatchSockets.remove(socket);
		fLogSockets.remove(socket);
	}

	// Call the log method with the correct message level
	@Override
	public void trace(String message) {
		log("TRACE", message);
	}

	@Override
	public void debug(String message) {
		log("DEBUG", message);
	}

	@Override
	public void info(String message) {
		log("INFO", message);
	}

	@Override
	public void error(String message) {
		log("ERROR", message);
	}
}
