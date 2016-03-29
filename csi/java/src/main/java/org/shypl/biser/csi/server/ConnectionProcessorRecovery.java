package org.shypl.biser.csi.server;

import org.apache.commons.codec.binary.Hex;
import org.shypl.biser.csi.ByteBuffer;
import org.shypl.biser.csi.ProtocolException;

class ConnectionProcessorRecovery extends ConnectionProcessor {

	private ByteBuffer buffer;

	@Override
	public void processData() throws ProtocolException {
		if (buffer == null) {
			buffer = new ByteBuffer(connection.read() & 0xFF);
		}
		else {
			int capacity = buffer.getCapacity();

			connection.read(buffer, capacity - buffer.getReadableBytes());

			if (capacity == buffer.getReadableBytes()) {
				long clientId = buffer.readLong();
				byte[] clientSid = buffer.readBytes();

				if (connection.getLogger().isTraceEnabled()) {
					connection.getLogger()
						.debug("Recovery: Try to establish connection (clientId: {}, clientSid: {})", clientId, Hex.encodeHexString(clientSid));
				}
				connection.getServer().reconnectClient(clientId, clientSid, connection);
			}
		}

	}
}