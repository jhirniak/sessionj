/**
 * 
 */
package sessionj.runtime.session;

import sessionj.runtime.SJIOException;
import sessionj.runtime.SJProtocol;
import sessionj.runtime.SJRuntimeException;
import sessionj.runtime.net.*;
import static sessionj.runtime.session.SJMessage.*;
import sessionj.runtime.transport.SJAcceptorThreadGroup;
import sessionj.runtime.transport.SJConnection;
import sessionj.runtime.transport.SJTransportManager;
import sessionj.types.sesstypes.SJSessionType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Raymond
 *
 * Currently, this class collects together init., delegation and close protocol (with corresponding signal handling) into one class, but can easily be separated.
 * 
 */
public class SJSessionProtocolsImpl extends SJSessionProtocols
{
	private static final byte DELEGATION_START = -1; // Would be more uniform to be a control signal (although slower).
	//private static final byte DELEGATION_ACK = -2;	

    /** Collects messages received after a session has been delegated. */
	private List<SJMessage> lostMessages = new LinkedList<SJMessage>(); // Doesn't need synchronization, shouldn't be adding and removing in different threads.
	
	public SJSessionProtocolsImpl(SJAbstractSocket s, SJSerializer ser)
	{
		super(s, ser);
	}

	public void accept() throws SJIOException, SJIncompatibleSessionException
	{
		//dualityCheck(ser, s.getProtocol().encoded());		
		dualityCheck(s.getProtocol().encoded());
	}
	
	public void request() throws SJIOException, SJIncompatibleSessionException
	{
		//dualityCheck(ser, s.getProtocol().encoded());
		dualityCheck(s.getProtocol().encoded());
	}
	
	public void close()
	{
		try
		{
			if (ser.getConnection() != null && !ser.isClosed()) // FIXME: need a isClosed. null in delegation case 2, session-receiver.
			{												
				ser.writeControlSignal(new SJFIN()); // FIXME: currently fails for delegated sessions. 
			
				SJControlSignal cs = null;						
				
				if (lostMessages == null || lostMessages.isEmpty())
				{				
					if (ser.getConnection() != null) // FIXME: need a isClosed.
					{
						try
						{
							cs = ser.readControlSignal(); 
						}
						catch (SJIOException ioe) // We are prematurely closing. 
						{
							// E.g. could be a non-forwarded message due to failed delegation.
						}
					}
				}
				else
				{
					SJMessage m = lostMessages.get(lostMessages.size() - 1);
					
					byte t = m.getType();
					
					if (t != SJ_CONTROL)
					{
						throw new SJRuntimeException("[SJSessionProtocolsImpl] Unexpected message flag: " + t);
					}
					
					cs = (SJControlSignal) m.getContent();
				}
				
				/*if (!(cs instanceof SJFIN || cs instanceof SJDelegationSignal)) // FIXME: need to ACK delegation, and also distinguish good and bad FIN, to avoid confusion at other party.
				{
					throw new SJRuntimeException("[SJSessionProtocolsImpl] Unexpected control signal: " + cs); // No point, we are about to close anyway - maybe after failure, so close protocol may not have been performed properly. In any case, need to close up.
				}*/
			}
		}
		catch (SJIOException ioe)
		{
			//ioe.printStackTrace();
		}
		finally
		{
			//if (ser != null) // Delegation case 2: no connection created between passive party and session acceptor.
			{
				if (!ser.isClosed()) 
				{
					ser.close(); // Doesn't close the underlying connection.					
				}
			}
			
			SJRuntime.closeSocket(s); // Maybe need to guard by an isClosed? Forwarding protocol closes sockets early.  
		}	
	}

	public void send(Object o) throws SJIOException
	{
		ser.writeObject(o);
	}

	public void sendByte(byte b) throws SJIOException
	{
		/*if (isLocal)
		{
			
		}
		else*/
		{
			ser.writeByte(b);
		}
	}
	
	public void sendInt(int i) throws SJIOException
	{
		/*if (isLocal)
		{
			// Could do a zero-copy int send using Integer wrapper, but is that actually faster than encoding as 4 bytes?
		}
		else*/
		{
			ser.writeInt(i);
		}
	}

	public void sendBoolean(boolean b) throws SJIOException
	{
		/*if (isLocal)
		{
		
		}
		else*/
		{
			ser.writeBoolean(b);
		}
	}
	
	public void sendDouble(double d) throws SJIOException
	{
		/*if (isLocal)
		{
			
		}
		else*/
		{
			ser.writeDouble(d);
		}
	}
	
	public void pass(Object o) throws SJIOException
	{
		if (!zeroCopySupported)
		{
			ser.writeObject(o);
		}
		else
		{
			ser.writeReference(o);			
		}
	}
	
	public void copy(Object o) throws SJIOException
	{
		send(o);
	}
	
	/*public void copyInt(int i) throws SJIOException
	{
		sendInt(i);
	}
	
	public void copyDouble(double d) throws SJIOException
	{
		sendDouble(d);
	}*/
	
	public Object receive() throws SJIOException, ClassNotFoundException
	{	
		Object o = null;
		
		while (true)
		{
			try
			{
				if (lostMessages.isEmpty())				
				{
					o = ser.readObject();
				}
				else 
				{
					SJMessage m = lostMessages.remove(0);
					
					byte t = m.getType();
					
					if (t == SJ_OBJECT || t == SJ_REFERENCE)
					{
						o = m.getContent();
					}
					else handleMessage(m, t);
                }
				
				return o;
			}
			catch (SJControlSignal cs)
			{
				handleControlSignal(cs);
			}
		}
	}

	public byte receiveByte() throws SJIOException
	{
		byte b = -1; 
		
		while (true)
		{
			try
			{
				if (lostMessages.isEmpty())				
				{				
					b = ser.readByte();
				}
				else
				{
					SJMessage m = lostMessages.remove(0);
					
					byte t = m.getType();
					
					if (t == SJ_BYTE)
					{
						b = m.getByteValue();
					}
					else handleMessage(m, t);
                }
					
				return b; 
			}
			catch (SJControlSignal cs)
			{
				handleControlSignal(cs);
			}
		}
	}
	
	public int receiveInt() throws SJIOException
	{
		int i = -1;
		
		while (true)
		{
			try
			{
				if (lostMessages.isEmpty())
				{
					i = ser.readInt();					
				}
				else
				{
					SJMessage m = lostMessages.remove(0);
					
					byte t = m.getType();
					
					if (t == SJ_INT)
					{
						i = m.getIntValue();
					}
					else return handleMessage(m, t);
                }
				
				return i;
			}
			catch (SJControlSignal cs)
			{
				handleControlSignal(cs);
			}
		}
	}

    private int handleMessage(SJMessage m, byte t) throws SJControlSignal, SJIOException {
        if (t == SJ_CONTROL)
        {
            throw (SJControlSignal) m.getContent();
        }
        else
        {
            throw new SJIOException("[SJSessionProtocolsImpl] Unexpected lost message type: " + t);
        }
    }

    public boolean receiveBoolean() throws SJIOException
	{
		boolean b = false;
		
		while (true)
		{
			try
			{
				if (lostMessages.isEmpty())				
				{				
					b = ser.readBoolean();
				}
                SJMessage m = lostMessages.remove(0);

                byte t = m.getType();

                if (t == SJ_BOOLEAN)
                {
                    b = m.getBooleanValue();
                }
                else if (t == SJ_CONTROL)
                {
                    handleControlSignal((SJControlSignal) m.getContent());
                }
                else
                {
                    throw new SJIOException("[SJSessionProtocolsImpl] Unexpected lost message type: " + t);
                }

                return b;
			}
			catch (SJControlSignal cs)
			{
				handleControlSignal(cs);
			}
		}
	}
	
	public double receiveDouble() throws SJIOException
	{

        while (true)
		{
			try
			{
                double d = 0;
                if (lostMessages.isEmpty())
				{
					d = ser.readDouble();					
				}
				else
				{
					SJMessage m = lostMessages.remove(0);
					
					byte t = m.getType();
					
					if (t == SJ_DOUBLE)
					{
						d = m.getIntValue();
					}
					else handleMessage(m, t);
                }
				
				return d;
			}
			catch (SJControlSignal cs)
			{
				handleControlSignal(cs);
			}
		}
	}
	
	public void outlabel(String lab) throws SJIOException 
	{
        if (zeroCopySupported) {
            ser.writeReference(lab);
        } else {
            ser.writeObject(lab);
        }
	}
	
	public String inlabel() throws SJIOException 
	{

        try
		{
			while (true)
			{
				try
				{
                    String lab = null;
                    if (lostMessages.isEmpty())
					{				
						lab = (String) ser.readObject();
					}
					else
					{
						SJMessage m = lostMessages.remove(0);
						
						byte t = m.getType();
						
						if (t == SJ_OBJECT || t == SJ_REFERENCE)
						{
							lab = (String) m.getContent();
						}
						else handleMessage(m, t);
                    }
					
					return lab;
				}
				catch (SJControlSignal cs)
				{
					handleControlSignal(cs);
				}			
			}						
		}
		catch (ClassNotFoundException cnfe)
		{
			throw new SJRuntimeException(cnfe);
		}
	}
	
	public void outsync(boolean b) throws SJIOException // FIXME: make support for zerocopy? (And other primitives?)
	{
		ser.writeBoolean(b);
	}
	
	public boolean insync() throws SJIOException 
	{

        while (true)
		{
			try
			{
                boolean b = false;
                if (lostMessages.isEmpty())
				{			
					b = ser.readBoolean();
				}		
				else
				{
					SJMessage m = lostMessages.remove(0);
					
					byte t = m.getType();
					
					if (t == SJ_BOOLEAN)
					{
						b = m.getBooleanValue();
					}
					else handleMessage(m, t);
                }
				
				return b;
			}
			catch (SJControlSignal cs)
			{
				handleControlSignal(cs);
			}						
		}
	}
		
	public void sendChannel(SJService c, SJSessionType st) throws SJIOException // Can get encoded from c.
	{
		// Maybe faster to use a custom protocol, such as !<host>.!<port>.!<encoded>.
		
		//if (ser.zeroCopySupported())
		{	
			pass(c); // OK to alias SJServices across threads, typing ensures they are na-final (they are also thread-safe).
		}
		/*else
		{
			send(c);
		}*/
	}
	
	public SJService receiveChannel(SJSessionType st) throws SJIOException
	{
		try
		{
			return (SJService) receive();
		}
		catch (ClassNotFoundException cnfe)
		{
			throw new SJIOException(cnfe);
		}
	}
	
	public void delegateSession(SJAbstractSocket s, SJSessionType st) throws SJIOException  
	{
		//if (ser.getConnection() instanceof SJLocalConnection) // Attempting to do a purely noalias transfer of session socket object.
		if (ser.zeroCopySupported())
		{	
			ser.writeReference(s); // Or use pass (like sendChannel). // Maybe some way to better structure this aspect of the routine? Or maybe this decision should be made here.
		}
		else
		{
			SJSessionProtocols sp = s.getSJSessionProtocols();
			
			if (!(sp instanceof SJSessionProtocolsImpl))
			{
				throw new SJIOException("[SJSessionProtocolsImpl] Incompatible session peer for delegation: " + sp);
			}
			
			ser.writeByte(DELEGATION_START);
			ser.writeBoolean(s instanceof SJRequestingSocket); // Original requestor.		
			
			int port = receiveInt(); // Should handle delegation case 3 (the two preceding delegation protocol messages are forwarded by peer). 
			
			SJSerializer foo = sp.getSerializer();
			
			String hostName = ser.getConnection().getHostName();
			
			if (hostName.equals("localhost") || hostName.equals("127.0.0.1")) // Factor out constants.
			{
				try
				{
					//hostName = InetAddress.getLocalHost().getHostName();
					hostName = InetAddress.getLocalHost().getHostAddress(); // Main Runtime routines are now using IP addresses rather than host names.
				}
				catch (UnknownHostException uhe)
				{
					throw new SJRuntimeException(uhe);
				}
			}
			
			foo.writeControlSignal(new SJDelegationSignal(hostName, port));
			
			forwardUntilACKOrFINInclusive(foo, ser);
			
			// Maybe need to close sp (and corresponding socket).
			s.getSerializer().close();			
			SJRuntime.closeSocket(s);
		}
	}
	
	//public SJAbstractSocket receiveSession(SJSessionType st) throws SJIOException
	public SJAbstractSocket receiveSession(SJSessionType st, SJSessionParameters params) throws SJIOException
	{
		if (ser.zeroCopySupported())
		{
			try
			{
				return (SJAbstractSocket) ser.readReference(); // Can use ordinary receive (like receiveChannel).
			}
			catch (SJControlSignal cs) // May need revision to handle certain delegation cases.
			{
				throw new SJIOException(cs);				
			}
		}
		else
		{
			byte flag = receiveByte(); // Handles delegation by peer?
			
			if (flag != DELEGATION_START) // Maybe replace by a proper control signal. Then would need to manually handle SJDelegationSignal.
			{
				throw new SJIOException("[SJSessionProtocolsImpl] Unexpected flag: " + flag);
			}
			
			boolean origReq;
			
			SJProtocol p = null;
			LinkedList<SJMessage> bar = null;
			
			SJConnection conn = null;
			
			boolean simdel;
			
			SJTransportManager sjtm = null;
			SJAcceptorThreadGroup atg = null;
						
			try
			{
				try
				{
					//origReq = receiveBoolean(); // Don't want to handle control messages.
					
					if (!lostMessages.isEmpty())
					{
						SJMessage m = lostMessages.remove(0);
						
						byte t = m.getType();
						
						if (t != SJ_BOOLEAN)
						{
							throw new SJIOException("[SJSessionProtocolsImpl] Expected boolean, not: " + t);
						}
						
						origReq = m.getBooleanValue();
					}
					else
					{
						origReq = ser.readBoolean();
					}
				}
				catch (SJControlSignal cs)
				{
					throw new SJIOException(cs);
				}
				
				//int port = SJRuntime.findFreePort();
				//int port = SJRuntime.takeFreePort();
				
				sjtm = SJRuntime.getTransportManager();
				
				/*SJAcceptorThreadGroup atg = sjtm.openAcceptorGroup(port); // FIXME: It's not just the session-port that we have to check is free - we need to make sure that the *setup* transports can be opened for that session port (e.g. some other non-SJ process may have opened a TCP port there already).*/
				
				p = new SJProtocol(SJRuntime.getTypeEncoder().encode(st));
				
				//SJAcceptorThreadGroup atg = SJRuntime.getFreshAcceptorThreadGroup(SJSessionParameters.DEFAULT_PARAMETERS); // FIXME: need to decide about session parameters.
				//SJAcceptorThreadGroup atg = SJRuntime.getFreshAcceptorThreadGroup(s.getParameters());
				atg = SJRuntime.getFreshAcceptorThreadGroup(params);
				
				ser.writeInt(atg.getPort());
		
				bar = new LinkedList<SJMessage>();
				
				simdel = bufferLostMessagesUntilACKOrFIN(ser, bar); // Includes final control message.
				
				//SJConnection conn = null;
							
				SJMessage m = bar.get(bar.size() - 1);
				
				// RAY
				if (simdel && origReq)
				{
					//throw new RuntimeException("foo"); // A test case for failure in the middle fot delegation. Not resolved yet, e.g. try delegation case 4.  
					
					SJDelegationSignal ds = (SJDelegationSignal) bar.get(bar.size() - 1).getContent();
					
					//conn = sjtm.openConnection(ds.getHostName(), ds.getPort(), s.getParameters()); // FIXME: is this the right thing to do about parameters?
					conn = sjtm.openConnection(ds.getHostName(), ds.getPort(), SJSessionParameters.DEFAULT_PARAMETERS); // We're using SJSessionParameters
				}
				else 
				//YAR	
					
					if (/*m != null && */!(m.getType() == SJ_CONTROL && m.getContent() instanceof SJFIN)) // Why check for m is null? 
				{
					conn = atg.nextConnection(); // FIXME: if the passive peer has no compatible setups, we will hang.
					
					//conn = ss.accept().getConnection();
					
					//System.out.println("[SJSessionProtocolsImpl] Accepted connection from: " + conn.getHostName() + ":" + conn.getPort());
				}
				
				bar.remove(bar.size() - 1); // Remove the final control message (SJDelegationACK). // RAY: also SJDelegationSignal.
			}
			finally
			{
				if (sjtm != null && atg != null)
				{
					sjtm.closeAcceptorGroup(atg.getPort()); 
					
					SJRuntime.freePort(atg.getPort()); // Gives the session port the Runtime bound for us.
					
					//ss.close();
				}
			}
			
			SJAbstractSocket foo;
			
			if (origReq)
			{
				foo = new SJRequestingSocket(p, SJSessionParameters.DEFAULT_PARAMETERS); // FIXME: default parameters for now, but maybe receive session should also have some transport configuration options.
			}
			else
			{
				foo = new SJAcceptingSocket(p, SJSessionParameters.DEFAULT_PARAMETERS);
			}				
			
			SJRuntime.bindSocket(foo, conn);
			
			((SJSessionProtocolsImpl) foo.getSJSessionProtocols()).lostMessages = bar;
			
			if (conn != null) // Delegation case 2.
			{
				try // Duals the operations at the end of reconnectToDelegationTarget. But not sure if this should be done here.
				{ 								
					//foo.setHostName((String) foo.getSerializer().readObject()); // Could instead get this information from conn (currently using IP addresses).
					foo.setHostName(InetAddress.getByName(conn.getHostName()).getHostAddress());
					
					// RAY
					if (simdel && origReq)
					{
						foo.getSerializer().writeInt(foo.getLocalPort()); // Mirrors the actions of reconnectToDelegationTarget. 
					}
					else
					//YAR
						
					{
						foo.setPort(foo.getSerializer().readInt()); // This could also be given by the delegator, passive party's session port shouldn't change - except for delegation case 4?
					}
				} 
				/*catch (ClassNotFoundException cnfe)
				{
					throw new RuntimeException("[SJSessionProtocolsImpl] Shouldn't get in here: " + cnfe);
				}*/
				catch (SJControlSignal cs) 
				{ 
					throw new RuntimeException("[SJSessionProtocolsImpl] Shouldn't get in here: " + cs); 
				}	
				catch (UnknownHostException uhe)
				{
					throw new RuntimeException("[SJSessionProtocolsImpl] Shouldn't get in here: " + uhe);
				}
			}
			
			return foo;
		}
	}
	
	/*protected SJControlSignal receiveControlSignal() throws SJIOException
	{
		return ser.readControlSignal();
	}
	
	protected void sendControlSignal(SJControlSignal cs) throws SJIOException
	{
		ser.writeControlSignal(cs);
	}*/
	
	protected void handleControlSignal(SJControlSignal cs) throws SJIOException
	{
		if (cs instanceof SJDelegationSignal)
		{			
			reconnectToDelegationTarget((SJDelegationSignal) cs);						
		}
		else if (cs instanceof SJFIN)
		{
			/*try // Duplicated from close protocol. // No need, close will be done in finally.
			{
				ser.writeControlSignal(new SJFIN());
			}
			catch (SJIOException ioe)
			{
				
			}
			finally
			{
				ser.close(); // Doesn't close the underlying connection.
			
				SJRuntime.closeSocket(s);*/ 
				
				throw new SJIOException("[SJSessionProtocolsImpl] Session prematurely terminated by peer: " + cs);
			//}										
		}
		else
		{
			throw new SJIOException("[SJSessionProtocolsImpl] Unsupported control signal: " + cs);			
		}
	}
	
	//private static void dualityCheck(SJSerializer ser, String encoded) throws SJIOException, SJIncompatibleSessionException
	private void dualityCheck(String encoded) throws SJIOException, SJIncompatibleSessionException
	{
		ser.writeObject(encoded);
		
		SJSessionType ours = SJRuntime.decodeSessionType(encoded);				
		
		try
		{
			SJSessionType theirs = SJRuntime.decodeSessionType((String) ser.readObject());
			
			if (!ours.isDualtype(theirs))
			{
				//ser.close(); // The session socket variable will still be null because of this exception, so close won't be called in the finally block - manually close here to flush (call the close protocol instead?)
				//SJRuntime.getTransportManager().closeConnection(ser.getConnection()); // FIXME: this isn't nice. Also need to unbind session socket...
				
				s.close();
				
				throw new SJIncompatibleSessionException("[SJSessionProtocolsImpl] Our session type (" + ours + ") incompatible with theirs: " + theirs);
			}
		}
		catch (ClassNotFoundException cnfe)
		{
			throw new SJRuntimeException(cnfe);
		}
		catch (SJControlSignal cs)
		{
			throw new SJRuntimeException("[SJSessionProtocolsImpl] Unexpected control signal: " + cs);
		}
	}
	
	private void reconnectToDelegationTarget(SJDelegationSignal ds) throws SJIOException
	{
		//System.out.println("[SJSessionProtocolsImpl] Received SJDelegationSignal: " + ds);
		
		ser.writeControlSignal(new SJDelegationACK());
		
		SJRuntime.reconnectSocket(s, ds.getHostName(), ds.getPort()); // Maybe could send a FIN to close the old socket.
		
		//System.out.println("[SJSessionProtocolsImpl] Reconnected to: " + ds.getHostName() + ":" + ds.getPort());		
		
		//ser.writeObject(s.getLocalHostName()); // FIXME: breaks e.g. PLDI benchmark 3. // Similar to the SJRuntime accept/request exchange. But should this be done here? Maybe reuse the accept/request exchange?
		ser.writeInt(s.getLocalPort()); 
	}
	
	private static void forwardUntilACKOrFINInclusive(SJSerializer s1, SJSerializer s2) throws SJIOException 
	{
		SJMessage m;
		byte type;
		
		while (true)
		{
			try
			{
				m = s1.nextMessage();			
				
				switch (type = m.getType())
				{
					case SJ_CONTROL: 
					{
						SJControlSignal cs = (SJControlSignal) m.getContent();
						
						/*if (cs instanceof SJDelegationSignal) // Double delegation.
						{
							throw new SJRuntimeException("[SJSessionProtocolsImpl] Simultaneous delegation not yet supported: " + cs);
						}*/						
						
						s2.writeControlSignal(cs); 
						
						if (cs instanceof SJDelegationACK || cs instanceof SJFIN)
						{
							//System.out.println("[SJSessionProtocolsImpl] forwarded: " + cs);
							
							return;
						}
						
						// RAY						
						else if (cs instanceof SJDelegationSignal)
						{
							return;
						}						
						// YAR
						
						break;
					}
					case SJ_OBJECT: 
					{
						s2.writeObject(m.getContent()); 
						
						break;
					}
					case SJ_REFERENCE: 
					{
						s2.writeReference(m.getContent()); 
						
						break;
					}
					case SJ_BYTE: 
					{
						s2.writeByte(m.getByteValue()); 
						
						break;
					}
					case SJ_INT: 
					{
						s2.writeInt(m.getIntValue()); 
						
						break;
					}
					case SJ_BOOLEAN: 
					{
						s2.writeBoolean(m.getBooleanValue()); 
						
						break;
					}
					default: 
					{
						throw new SJIOException("[SJSessionProtocolsImpl] Unexpected message type: " + type);
					}
				}
				
				//System.out.println("[SJSessionProtocolsImpl] forwarded: " + m);
			}			
			catch (ClassNotFoundException cnfe) // If we manually transmit the serialized form, no need to worry about this here.
			{
				throw new SJIOException(cnfe);
			}
		}		
	}
	
	private static boolean bufferLostMessagesUntilACKOrFIN(SJSerializer ser, List<SJMessage> lostMessages) throws SJIOException // Inclusive of final control message.
	{
		boolean simdel = false;
		
		try
		{
			while (true)
			{
				SJMessage m = ser.nextMessage();
				
				lostMessages.add(m);
				
				byte t = m.getType(); 
				
				if (t == SJ_CONTROL)					
				{
					if (m.getContent() instanceof SJDelegationACK)						
					{						
						break;
					}
					
					// RAY
					else if (m.getContent() instanceof SJDelegationSignal)
					{
						simdel = true;
						
						break;
					}
					// YAR
				}
				
				//lostMessages.add(m); // receiveSession doesn't want lostMessages to be empty.
				
				//System.out.println("[SJSessionProtocolsImpl] Buffered lost message: " + m);
				
				if ((t == SJ_CONTROL) && (m.getContent() instanceof SJFIN))
				{							
					break;
				}				
			}
		}
		catch (ClassNotFoundException cnfe)
		{
			throw new SJIOException(cnfe);
		}
		
		return simdel;
	}
	
	// Hacks for bounded-buffer communication.

	/*public void sendBB(Object o) throws SJIOException
	{
		ser.writeObject(o);
	}
	
	public void passBB(Object o) throws SJIOException
	{
		if (!zeroCopySupported)
		{
			ser.writeObject(o);
		}
		else
		{
			ser.writeReference(o);			
		}
	}*/
	
	/*public boolean recurseBB(String lab) throws SJIOException
	{
		if (boundedBufferSupported)
		{
			((SJBoundedBufferConnection) s.getConnection()).recurseBB(lab);
			
			return true;
		}
		else
		{
			// recurse(lab); // This routine does not exist yet.
			
			return true;
		}		
	}*/
}
