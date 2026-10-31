package shortestpath.routingcuts;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import shortestpath.pathfinder.AStarRoutingStaticProvider;
import shortestpath.pathfinder.CollisionMap;
import shortestpath.pathfinder.SplitFlagMap;

/**
 * Identity harness for the A* routing data: prints a SHA-256 over every field of the
 * {@code RoutingStatic} that the plugin checkout's {@link AStarRoutingStaticProvider} builds from the
 * committed collision map, transports and banks. Two plugin trees that print the same digest route
 * identically; run it against each tree with {@code -PshortestPathDir}.
 *
 * <p>Encoding: null is one zero byte. Arrays write their length as a little-endian int, then
 * {@code int[]} and {@code char[]} elements as little-endian ints, {@code byte[]} raw,
 * {@code boolean[]} one byte each, {@code long[]} as two little-endian ints (low, then high), and
 * object arrays element by element. Boxed numbers, booleans, characters, strings and bit sets write
 * their {@code toString()} bytes. Any other object is visited once, writing every instance field of
 * its class and superclasses in declaration order: primitives as their string form, references
 * recursively.
 */
public final class RoutingStaticDigest
{
	/** The digest of the pinned reference tree, for comparison only: its data may have moved on. */
	private static final String REFERENCE = "eae908c1f8d0114a9800208a04d9aeb6fdd86958a026ca8249793ef202265fcc";

	private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
	private final MessageDigest digest;
	private final Map<Object, Boolean> seen = new IdentityHashMap<>();

	private RoutingStaticDigest(MessageDigest digest)
	{
		this.digest = digest;
	}

	public static void main(String[] args) throws Exception
	{
		Object routingStatic = new AStarRoutingStaticProvider(() -> new CollisionMap(SplitFlagMap.fromResources())).get();
		RoutingStaticDigest walker = new RoutingStaticDigest(MessageDigest.getInstance("SHA-256"));
		walker.visit(routingStatic);
		walker.flush();
		StringBuilder hex = new StringBuilder();
		for (byte value : walker.digest.digest()) hex.append(String.format("%02x", value));
		System.out.println("routing-static-sha256 " + hex);
		System.out.println("reference " + REFERENCE + (REFERENCE.contentEquals(hex) ? " match" : " differs"));
	}

	private void visit(Object value) throws Exception
	{
		if (value == null)
		{
			pending.write(0);
		}
		else if (value instanceof int[])
		{
			int[] array = (int[]) value;
			writeInt(array.length);
			for (int element : array) writeInt(element);
		}
		else if (value instanceof char[])
		{
			char[] array = (char[]) value;
			writeInt(array.length);
			for (char element : array) writeInt(element);
		}
		else if (value instanceof byte[])
		{
			byte[] array = (byte[]) value;
			writeInt(array.length);
			pending.write(array, 0, array.length);
		}
		else if (value instanceof boolean[])
		{
			boolean[] array = (boolean[]) value;
			writeInt(array.length);
			for (boolean element : array) pending.write(element ? 1 : 0);
		}
		else if (value instanceof long[])
		{
			long[] array = (long[]) value;
			writeInt(array.length);
			for (long element : array)
			{
				writeInt((int) element);
				writeInt((int) (element >>> 32));
			}
		}
		else if (value instanceof Object[])
		{
			Object[] array = (Object[]) value;
			writeInt(array.length);
			for (Object element : array) visit(element);
		}
		else if (value instanceof Number || value instanceof Boolean || value instanceof Character
			|| value instanceof String || value instanceof BitSet)
		{
			byte[] text = value.toString().getBytes(StandardCharsets.UTF_8);
			pending.write(text, 0, text.length);
		}
		else if (seen.put(value, Boolean.TRUE) == null)
		{
			for (Class<?> type = value.getClass(); type != Object.class; type = type.getSuperclass())
			{
				for (Field field : type.getDeclaredFields())
				{
					if (Modifier.isStatic(field.getModifiers())) continue;
					field.setAccessible(true);
					if (field.getType().isPrimitive())
					{
						byte[] text = String.valueOf(field.get(value)).getBytes(StandardCharsets.UTF_8);
						pending.write(text, 0, text.length);
					}
					else
					{
						visit(field.get(value));
					}
				}
			}
		}
		if (pending.size() >= 1 << 20) flush();
	}

	private void writeInt(int value)
	{
		pending.write(value);
		pending.write(value >>> 8);
		pending.write(value >>> 16);
		pending.write(value >>> 24);
	}

	private void flush()
	{
		digest.update(pending.toByteArray());
		pending.reset();
	}
}
