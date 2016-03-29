package org.shypl.biser.csi.server;

import org.shypl.biser.csi.CommunicationLoggingUtils;
import org.shypl.biser.io.ByteArrayOutputData;
import org.shypl.biser.io.DataWriter;

import java.util.function.Consumer;

public abstract class ClientService {
	private static final ThreadLocal<ByteArrayOutputData> threadLocalData = new ThreadLocal<ByteArrayOutputData>() {
		@Override
		protected ByteArrayOutputData initialValue() {
			return new ByteArrayOutputData();
		}
	};

	private final int    serviceId;
	private final String serviceName;
	private final Client client;

	protected ClientService(int serviceId, String serviceName, Client client) {
		this.serviceId = serviceId;
		this.serviceName = serviceName;
		this.client = client;
	}

	protected final void _send(int methodId, Consumer<DataWriter> message) {
		ByteArrayOutputData data = threadLocalData.get();
		data.clear();

		DataWriter writer = data.getWriter();
		writer.writeInt(0);
		writer.writeInt(serviceId);
		writer.writeInt(methodId);
		message.accept(writer);

		client.sendMessage(data.getArray());
	}

	protected final void _log(String methodName) {
		CommunicationLoggingUtils.logServerCall(client.getLogger(), serviceName, methodName);
	}

	protected final void _log(String methodName, Object arg) {
		CommunicationLoggingUtils.logServerCall(client.getLogger(), serviceName, methodName, arg);
	}

	protected final void _log(String methodName, Object arg1, Object arg2) {
		CommunicationLoggingUtils.logServerCall(client.getLogger(), serviceName, methodName, arg1, arg2);
	}

	protected final void _log(String methodName, Object... args) {
		CommunicationLoggingUtils.logServerCall(client.getLogger(), serviceName, methodName, args);
	}
}