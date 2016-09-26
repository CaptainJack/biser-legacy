package org.shypl.biser.csi.server;

import org.shypl.biser.csi.ByteBuffer;
import org.shypl.biser.csi.ConnectionCloseReason;
import org.shypl.biser.csi.Protocol;
import org.shypl.common.concurrent.Worker;
import org.shypl.common.slf4j.PrefixedLoggerProxy;
import org.shypl.common.util.Cancelable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
	private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

	private final Object stopper = new Object();
	private final ScheduledExecutorService connectionsExecutor;
	private final ChannelGate              channelGate;
	private final ServerSettings           settings;
	private final AbstractApi<?>           api;
	private final Worker                   worker;
	private final PrefixedLoggerProxy      logger;

	private final AtomicInteger connectionsAmount = new AtomicInteger();

	private volatile boolean running;
	private volatile boolean opened;

	private Cancelable stopperChecker;
	
	private int stopConnections;
	private int stopClients;
	private int stopWaiting;
	
	public Server(ScheduledExecutorService serverExecutor, ScheduledExecutorService connectionsExecutor,
		ChannelGate channelGate, ServerSettings settings, AbstractApi<?> api
	) {
		this.connectionsExecutor = connectionsExecutor;
		this.channelGate = channelGate;
		this.settings = settings;
		this.api = api;

		worker = new Worker(serverExecutor);
		logger = new PrefixedLoggerProxy(LOGGER, '[' + api.getName() + "] ");
	}

	public final void start() {
		if (running) {
			logger.error("Server already running");
		}
		else {
			logger.info("Starting");
			running = true;
			opened = true;
			channelGate.open(settings.getAddress(), this::acceptConnection);

			logger.info("Started");
		}
	}

	public final void stop() {
		stop(0);
	}

	public final void stop(int timeout) {
		worker.addTask(() -> {
			if (running) {
				opened = false;
				logger.info("Stopping");

				if (timeout == 0 || isNotConnectionsAndClients()) {
					doStop0();
				}
				else {
					byte[] bytes = new ByteBuffer(2).writeByte(Protocol.SERVER_SHUTDOWN_TIMEOUT).writeInt(timeout).readBytes();
					for (AbstractClient client : api.getAllClients()) {
						client.sendData(bytes);
					}
					worker.scheduleTask(this::doStop0, timeout, TimeUnit.SECONDS);
				}
			}
			else {
				logger.error("Server is not running");
			}
		});

		synchronized (stopper) {
			try {
				stopper.wait();
			}
			catch (InterruptedException e) {
				logger.error("Stop is interrupted", e);
			}
		}
	}

	ScheduledExecutorService getConnectionsExecutor() {
		return connectionsExecutor;
	}

	ServerSettings getSettings() {
		return settings;
	}

	AbstractApi<?> getApi() {
		return api;
	}

	ChannelHandler acceptConnection(Channel channel) {
		Connection connection = new Connection(this, channel);
		
		int i = connectionsAmount.incrementAndGet();
		logger.debug("Accept connection #{} (connections: {})", connection.getId(), i);
		
		worker.addTask(() -> {
			if (!opened) {
				connection.close(ConnectionCloseReason.SERVER_SHUTDOWN);
			}
		});

		return connection;
	}

	void releaseConnection(Connection connection) {
		int i = connectionsAmount.decrementAndGet();
		logger.debug("Release connection #{} (connections: {})", connection.getId(), i);
	}

	void connectClient(AbstractClient client, Connection connection) {
		worker.addTask(() -> {
			if (connection.isOpened()) {
				if (opened) {
					AbstractClient oldClient = api.getClient(client.getId());
					if (oldClient == null) {
						logger.debug("Connect client #{} (clients: {})", client.getId(), api.countClients());

						client.connect(this, connection);
						api.addClient(client);
					}
					else {
						logger.debug("Concurrent connect client #{}", client.getId());
						oldClient.disconnect(ConnectionCloseReason.CONCURRENT, () -> connectClient(client, connection));
					}
				}
				else {
					connection.close(ConnectionCloseReason.SERVER_SHUTDOWN);
				}
			}
			else {
				logger.warn("Fail connect client #{} by closed connection", client.getId());
			}
		});
	}

	void disconnectClient(AbstractClient client) {
		worker.addTask(() -> {
			AbstractClient oldClient = api.getClient(client.getId());
			if (oldClient == client) {
				api.removeClient(client);
				logger.debug("Disconnect client #{} (clients: {})", client.getId(), api.countClients());
			}
			else {
				logger.warn("Lose client #{}", client.getId());
			}
		});
	}

	void reconnectClient(long clientId, byte[] clientSid, Connection connection) {
		worker.addTask(() -> {
			if (opened) {
				if (connection.isOpened()) {
					if (running) {
						AbstractClient client = api.getClient(clientId);

						if (client == null || !Arrays.equals(client.calculateSid(), clientSid)) {
							connection.close(ConnectionCloseReason.RECOVERY_REJECT);
						}
						else {
							logger.debug("Reconnect client #{}", client.getId());
							client.reconnect(connection);
						}
					}
					else {
						connection.close(ConnectionCloseReason.SERVER_SHUTDOWN);
					}
				}
				else {
					logger.warn("Fail reconnect client #{} by closed connection", clientId);
				}
			}
			else {
				connection.close(ConnectionCloseReason.SERVER_SHUTDOWN);
			}
		});
	}

	private void doStop0() {
		if (isNotConnectionsAndClients()) {
			doStop1();
		}
		else {
			disconnectAllClients();
			stopperChecker = worker.scheduleTaskPeriodic(this::doStop1, 1, TimeUnit.SECONDS);
		}
	}
	
	private void disconnectAllClients() {
		for (AbstractClient client : api.getAllClients()) {
			client.disconnect(ConnectionCloseReason.SERVER_SHUTDOWN);
		}
	}
	
	private boolean isNotConnectionsAndClients() {
		return connectionsAmount.get() == 0 && api.countClients() == 0;
	}

	private void doStop1() {
		int connections = connectionsAmount.get();
		int clients = api.countClients();
		if (connections > 0 || clients > 0) {
			logger.info("Wait stopping connections and clients (connections: {}, clients: {})", connections, clients);
			
			if (connections == stopConnections && clients == stopClients) {
				++stopWaiting;
				if (stopWaiting == 10) {
					stopWaiting = 0;
					logger.warn("Repeat disconnect all clients");
					disconnectAllClients();
				}
			}
			else {
				stopWaiting = 0;
			}
			stopConnections = connections;
			stopClients = clients;
			
		}
		else {
			if (stopperChecker != null) {
				stopperChecker.cancel();
				stopperChecker = null;
			}

			channelGate.close();
			running = false;

			logger.info("Stopped");

			synchronized (stopper) {
				stopper.notifyAll();
			}
		}
	}
}
