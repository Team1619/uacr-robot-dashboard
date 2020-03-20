package org.uacr.services.webdashboard.websocket;

import org.uacr.utilities.logging.LogManager;
import org.uacr.utilities.logging.Logger;

import javax.annotation.Nullable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractWebsocketServer {

	private static final Logger sLogger = LogManager.getLogger(AbstractWebsocketServer.class);

	private final int fPort;
	private final Set<WebSocket> fSockets;
	private final Set<WebSocket> fCurrentSockets;
	@Nullable
	private ServerSocket fServer;
	//private Executor fExecutor;

	public AbstractWebsocketServer(int port) {
		fPort = port;
		fSockets = Collections.synchronizedSet(new HashSet<>());
		fCurrentSockets = Collections.synchronizedSet(new HashSet<>());
		//fExecutor = Executors.newCachedThreadPool();
	}

	public AbstractWebsocketServer() {
		this(80);
	}

	public void start() {
		startServer();

		execute(this::onStart);

		run();
	}

	private void startServer() {
		try {
			fServer = new ServerSocket(fPort);
			fServer.setReuseAddress(true);
			fServer.setSoTimeout(1);
		} catch (Exception e) {
			onError(null, e);
		}
	}

	private void run() {
		execute(() -> {
			Thread.currentThread().setName("WebSocketServer - Run");
			while (!Thread.currentThread().isInterrupted()) {
				try {
					@Nullable
					Socket connection = null;
					if (fServer != null) {
						connection = fServer.accept();
					}

					try {
						if (connection != null) {
							new WebSocket(connection, this);
						}
					} catch (Exception e) {
						execute(() -> onError(null, e));
					}
				} catch (SocketTimeoutException e) {

				} catch (Exception e) {
					execute(() -> onError(null, e));
					startServer();
				}

				try {
					fCurrentSockets.clear();
					fCurrentSockets.addAll(fSockets);

					for (WebSocket socket : fCurrentSockets) {
						try {
							socket.update();
						} catch (Exception e) {
							execute(() -> onError(socket, e));
						}
					}
				} catch (Exception e) {
					execute(() -> onError(null, e));
				}

				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}
			}

			fCurrentSockets.clear();
			fCurrentSockets.addAll(fSockets);

			for (WebSocket socket : fCurrentSockets) {
				socket.close();
			}

			sLogger.debug("Websocket server shutting down");
		});
	}

	protected void execute(Runnable run) {
		new Thread(run).start();
		//fExecutor.execute(run);
	}

	public abstract void onStart();

	protected final void onopen(WebSocket webSocket) {
		fSockets.add(webSocket);
		onOpen(webSocket);
	}

	public abstract void onOpen(WebSocket webSocket);

	protected final void onmessage(WebSocket webSocket, String message) {
		onMessage(webSocket, message);
	}

	public abstract void onMessage(WebSocket webSocket, String message);

	protected final void onclose(WebSocket webSocket) {
		fSockets.remove(webSocket);
		onClose(webSocket);
	}

	public abstract void onClose(WebSocket webSocket);

	protected final void onerror(WebSocket webSocket, Exception e) {
		onError(webSocket, e);
	}

	public abstract void onError(@Nullable WebSocket webSocket, Exception e);
}