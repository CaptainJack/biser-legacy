package org.shypl.biser.io;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public interface BiserReader {
	byte readByte() throws IOException;

	boolean readBool() throws IOException;

	int readInt() throws IOException;

	long readUint() throws IOException;

	long readLong() throws IOException;

	long readUlong() throws IOException;

	double readDouble() throws IOException;

	byte[] readBytes() throws IOException;

	String readString() throws IOException;

	Date readDate() throws IOException;

	<E extends Enum<E>> E readEnum(Class<E> type) throws IOException;

	<E extends Entity> E readEntity(Class<E> type) throws IOException;

	byte[] readByteArray() throws IOException;

	boolean[] readBoolArray() throws IOException;

	int[] readIntArray() throws IOException;

	int[] readUintArray() throws IOException;

	long[] readLongArray() throws IOException;

	long[] readUlongArray() throws IOException;

	double[] readDoubleArray() throws IOException;

	<E> E[] readArray(Decoder<? super E> elementDecoder) throws IOException;

	<K, V> Map<K, V> readMap(Decoder<? super K> keyDecoder, Decoder<? super V> valueDecoder) throws IOException;

	<E> Collection<E> readCollection(Decoder<? super E> elementDecoder) throws IOException;
}