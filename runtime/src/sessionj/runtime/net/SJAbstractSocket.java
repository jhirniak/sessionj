package sessionj.runtime.net;

import java.io.IOException;
import java.net.InetAddress;

import sessionj.runtime.*;
import sessionj.runtime.session.*;
import sessionj.runtime.transport.*;
import sessionj.types.sesstypes.SJSetType;
import sessionj.types.sesstypes.SJSessionType;

abstract public class SJAbstractSocket implements SJSocket
{
	private SJStateManager sm; // A runtime version of SJContext, specialised for a single session.
	
	private SJProtocol protocol;
	private SJSessionType runtimeType;
  private final SJSessionParameters params;
	
	private String hostName;
	private int port;
	
	private String localHostName; // Session-level values.
	private int localPort;
	
	private SJConnection conn;
	
	private SJSessionProtocols sp;
	private SJSerializer ser;
	
	private boolean isActive = false;

  protected SJAbstractSocket(SJProtocol protocol, SJSessionParameters params) throws SJIOException
	{
		this(protocol, params, protocol.type());
  }

  protected SJAbstractSocket(SJProtocol protocol, SJSessionParameters params, SJSessionType runtimeType)
        throws SJIOException
    {
        this.protocol = protocol; // Remainder of initialisation for client sockets performed when init is called.
		this.params = params;
		this.runtimeType = runtimeType;
        
		try
		{
			localHostName = InetAddress.getLocalHost().getHostName();
		}
		catch (IOException ioe)
		{
			throw new SJIOException(ioe);
		}
		
		//this.sm = new SJStateManager_c(SJRuntime.getTypeSystem(), protocol.type()); // Replaced by a setter (called by SJProtocolsImp).
  }
	
	protected void init(SJConnection conn) throws SJIOException // conn can be null (delegation case 2?).
	{
		this.conn = conn;
		//this.ser = new SJDefaultSerializer(conn); // FIXME: should be...
        ser = SJRuntime.getSerializer(conn);
        sp = new SJSessionProtocolsImpl(this, ser); // ... user configurable.
	}

    public SJSessionType getRuntimeType() { // FIXME: these are subsumed by the more general SJStateManager.
        return runtimeType;
    }

    public void setRuntimeType(SJSessionType runtimeType) {
        this.runtimeType = runtimeType;
    }

    public void reconnect(SJConnection conn) throws SJIOException
	{
        ser.close();
		
		this.conn = conn;
		//this.ser = new SJDefaultSerializer(conn);
        ser = SJRuntime.getSerializer(conn);
        sp.setSerializer(ser);
	}
	
	protected void accept() throws SJIOException, SJIncompatibleSessionException
	{		
		sp.accept();
	}
	
	protected void request() throws SJIOException, SJIncompatibleSessionException
	{		
		sp.request();
	}

	public void close() // FIXME: not compatible with delegation.
	{
		sp.close(); 
	}

	public void send(Object o) throws SJIOException
	{
		sp.send(o);
	}
	
	public void sendInt(int i) throws SJIOException
	{
		sp.sendInt(i);
	}

	public void sendBoolean(boolean b) throws SJIOException
	{
		sp.sendBoolean(b);
	}
	
	public void sendDouble(double d) throws SJIOException
	{
		sp.sendDouble(d);
	}
	
	public void pass(Object o) throws SJIOException
	{
		sp.pass(o);
	}
	
	public void copy(Object o) throws SJIOException
	{
		sp.copy(o);
	}
	
	public Object receive() throws SJIOException, ClassNotFoundException
	{
		return sp.receive();
	}
	
	public int receiveInt() throws SJIOException
	{
		return sp.receiveInt();
	}

	public boolean receiveBoolean() throws SJIOException
	{
		return sp.receiveBoolean();
	}
	
	public double receiveDouble() throws SJIOException
	{
		return sp.receiveDouble();
	}
	
	public void outlabel(String lab) throws SJIOException
	{
		sp.outlabel(lab);
	}
	
	public String inlabel() throws SJIOException
	{
		return sp.inlabel();
	}
	
	public boolean outsync(boolean b) throws SJIOException
	{
		sp.outsync(b);
		
		return b;
	}
	
	public boolean insync() throws SJIOException
	{
		return sp.insync();
	}

    public boolean isPeerInterruptibleOut(boolean selfInterrupting) throws SJIOException {
        return sp.isPeerInterruptibleOut(selfInterrupting);
    }

    public boolean isPeerInterruptingIn(boolean selfInterruptible) throws SJIOException {
        return sp.isPeerInterruptingIn(selfInterruptible);
    }

    public boolean interruptibleOutsync(boolean condition) throws SJIOException {
        return sp.interruptibleOutsync(condition);
    }

    public boolean interruptingInsync(boolean condition, boolean peerInterruptible) throws SJIOException {
        return sp.interruptingInsync(condition, peerInterruptible);
    }

    public void sendChannel(SJService c, String encoded) throws SJIOException
	{
		//sp.sendChannel(c, SJRuntime.getTypeEncoder().decode(c.getProtocol().encoded()));
		sp.sendChannel(c, SJRuntime.decodeType(encoded));
	}
	
	public SJService receiveChannel(String encoded) throws SJIOException
	{
		return sp.receiveChannel(SJRuntime.decodeType(encoded));
	}
	
	public void delegateSession(SJAbstractSocket s, String encoded) throws SJIOException
	{
		sp.delegateSession(s, SJRuntime.decodeType(encoded));
	}
	
	//public SJAbstractSocket receiveSession(String encoded) throws SJIOException
	public SJAbstractSocket receiveSession(String encoded, SJSessionParameters params) throws SJIOException
	{
		return sp.receiveSession(SJRuntime.decodeType(encoded), params);
	}
	
	public boolean isActive()
	{
		return isActive;
	}

	protected void setActive(boolean isActive)
	{
		this.isActive = isActive;
	}
		
	//protected SJConnection getConnection()
	public SJConnection getConnection()
	{
		return conn;
	}
	
	//protected SJSerializer getSerializer()
	public SJSerializer getSerializer()
	{
		return ser;
	}
	
	public SJSessionProtocols getSJSessionProtocols() // Access modifier too open.
	{
		return sp;
	}

	public SJProtocol getProtocol()
	{
		return protocol;
	}
	
	public String getHostName()
	{
		return hostName;
	}
	
	public void setHostName(String hostName) // Access by users disallowed by compiler.
	{
		this.hostName = hostName;
	}
	
	public int getPort()
	{
		return port;
	}
	
	public void setPort(int port)
	{
		this.port = port;
	}
	
	public String getLocalHostName()
	{
		return localHostName;
	}	
	
	public int getLocalPort()
	{
		return localPort;
	}
	
	protected void setLocalHostName(String localHostName)
	{
		this.localHostName = localHostName;
	}
	
	protected void setLocalPort(int localPort)
	{
		this.localPort = localPort;
	}
	
	public SJSessionParameters getParameters()
	{
		return params;		
	}
	
	// Hacks for bounded-buffer communication.

	/*public void sendBB(Object o) throws SJIOException
	{
		sp.sendBB(o);
	}
	
	public void passBB(Object o) throws SJIOException
	{
		sp.passBB(o);
	}
	
	public Object receiveBB() throws SJIOException, ClassNotFoundException
	{
		return sp.receiveBB();
	}
	
	public void outlabelBB(String lab) throws SJIOException
	{
		sp.outlabelBB(lab);
	}
	
	public String inlabelBB() throws SJIOException
	{
		return sp.inlabelBB();
	}*/
	
	/*public boolean recurseBB(String lab) throws SJIOException
	{
		return sp.recurseBB(lab);
	}*/

  public int typeLabel() throws SJIOException {
      // TODO: Better support for runtime type (this currently only works right after a session-receive)
      assert protocol.type() instanceof SJSetType;
      SJSetType set = (SJSetType) protocol.type();
      return set.memberRank(runtimeType);
  }

  /**
   * For zero-copy delegation: receiver needs to keep its own static type
   */
  public void updateStaticAndRuntimeTypes(SJSessionType staticType, SJSessionType runtimeType) throws SJIOException {
      this.runtimeType = runtimeType;
      protocol = new SJProtocol(SJRuntime.encode(staticType));
  }

  public abstract boolean isOriginalRequestor();
  
  public SJStateManager getStateManager()
  {
  	return sm;
  }
  
  public void setStateManager(SJStateManager sm)
  {
  	this.sm = sm;
  }
  
  public SJSessionType currentSessionType()
  {
  	return getStateManager().currentState(); // FIXME: state manager needs to use proper unrolling of loop types.
  }
  
  public SJSessionType remainingSessionType()
  {
  	return getStateManager().expectedType(); // FIXME: state manager needs to use proper unrolling of loop types.
  }
}
