/**
 * 
 */
package sessionj.runtime.session;

import java.nio.ByteBuffer;
import java.util.*;

import sessionj.runtime.SJIOException;
import sessionj.runtime.transport.SJConnection;

/**
 * @author Raymond
 *
 * Intended to be an easier to use and more convenient interface than the full SJSerializer. The serializer should wrap this formatter around the underlying I/O streams.
 *
 */
abstract public class SJCustomMessageFormatter 
{		
	private SJConnection conn;
	
	private ByteBuffer bb = ByteBuffer.allocate(1024); // FIXME: factor out constant. // Called by SJCustomSerializer in a "sequentialised" way, i.e. no race conditions. 
	
	void bindConnection(SJConnection conn)
	{
		this.conn = conn;
	}
	
	protected final void writeMessage(Object o) throws SJIOException
	{
		conn.writeBytes(formatMessage(o));
		conn.flush();
		
		System.out.println("a: " + o + ", " + Arrays.toString(formatMessage(o)));
	}
	
	/*class SJByteArray // Would like to avoid creating allocating lots of new byte arrays all the time, but also don't want to much work to be moved into parseMessage (e.g. we could give the ByteBuffer directly, but the user would have to handle it very carefully). 
	{
		public final int capacity;
		private int position = 0;
		
		private final byte[] bs; 
		
		public SJByteArray(int capacity)
		{
			this.capacity = capacity;
			this.bs = new byte[capacity];
		}
		
		public void read(ByteBuffer bb)
		{
			bb.get(bs, 0, bb.limit());
			
			position = bb.limit();
		}
		
		public byte[] ...
		
		public int capacity()
		{
			return capacity;
		}
		
		public int position()
		{
			return position;
		}
	}*/
	
	private static final byte[] copyByteBufferContents(ByteBuffer bb)
	{
		bb.flip();
		
		byte[] bs = byteBufferToByteArray(bb);
		
		unflipByteBuffer(bb);
		
		return bs;
	}	
	
	// Pre: bb should already be flipped, i.e. ready for (relative) "getting".
	private static final byte[] byteBufferToByteArray(ByteBuffer bb)
	{
		byte[] bs = new byte[bb.limit()];
		
		bb.get(bs, 0, bs.length);		
		
		return bs;
	}

	private static final void unflipByteBuffer(ByteBuffer bb)
	{
		bb.position(bb.limit()); 
		bb.limit(bb.capacity());
	}
	
	//public final SJMessage readNextMessage() throws SJIOException
	protected final Object readNextMessage() throws SJIOException // FIXME: would be better if could implement using arrived.
	{
		bb.put(conn.readByte()); // Need at least one byte for a message.
						
		byte[] bs = copyByteBufferContents(bb);
		
		System.out.println("b1: " + Arrays.toString(bs));
		
		Object o = null;
		
		for (o = parseMessage(bs); o == null; o = parseMessage(bs)) // Assuming parseMessage returns null if parsing unsuccessful. (But what if we want to communicate a null?)
		{
			bb.put(conn.readByte()); // FIXME: bb can become full.
			
			bs = copyByteBufferContents(bb);
			
			System.out.println("b2: " + o + ", " + Arrays.toString(bs));
		}
		
		bb.clear();
		
		return o;
	}
	
	abstract public byte[] formatMessage(Object o) throws SJIOException; // Maybe we should use e.g. SJCustomMessage (subclasses) rather than Object. SJCustomMessage could also offer message-specific formatting operations.
	abstract public Object parseMessage(byte[] bs) throws SJIOException; // Has to be non-blocking (for readNextMessage to work). FIXME: not a good interface for the user.
}