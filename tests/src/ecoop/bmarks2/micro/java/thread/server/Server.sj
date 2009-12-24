//$ bin/sessionj -cp tests/classes/ ecoop.bmarks2.micro.java.thread.server.Server false 8888 

package ecoop.bmarks2.micro.java.thread.server;

import java.io.*;
import java.net.*;
import java.util.*;

import ecoop.bmarks2.micro.*;

public class Server extends ecoop.bmarks2.micro.Server  
{  
	volatile protected boolean run = true;
	volatile protected boolean kill = false;
	
	private List threads = new LinkedList();
	
	volatile private boolean finished = false;
	
	private ServerSocket ss;
	
	public Server(boolean debug, int port)
	{
		super(debug, port);
	}
	
  public void run() throws Exception
  {		
  	debugPrintln("[Server] Started initialisation.");
  	
  	//ServerSocket ss = null;
  	
  	Socket s = null;
  	
		try 
		{
			ss = new ServerSocket(getPort());
			
			debugPrintln("[Server] Listening on: " + getPort());			
			
			boolean debug = isDebug();
			
			for (int i = 0; true; i++)
			{
				ServerThread st = new ServerThread(debug, this, i, ss.accept());
				
				st.start();
				
				this.threads.add(st);
				
				addClient();
			}			
		}
		catch (SocketException se) // Server socket was closed (hopefully by us).
		{
			//se.printStackTrace();
		}
		finally
		{
			//Common.closeServerSocket(ss);
			
			this.finished = true;		
		}
  }

  public void kill() throws Exception
  {
  	this.run = false;
  	this.kill = true;
  	
		for (Iterator i = threads.iterator(); i.hasNext(); )
		{
			((ServerThread) i.next()).join();				
		} 	
		
  	Common.closeServerSocket(ss); // Break the accepting loop.
		
		while (!this.finished);
  	
  	debugPrintln("[Server] Finished running (all Clients killed).");
  }
  
  public static void main(String [] args) throws Exception 
  {
  	boolean debug = Boolean.parseBoolean(args[0].toLowerCase());
  	int port = Integer.parseInt(args[1]);
    
  	new Server(debug, port).run();
  }
}

class ServerThread extends Thread
{
	private boolean debug;
	
	private Server server;
	private int tid;
	
	private Socket s;
	
	public ServerThread(boolean debug, Server server, int tid, Socket s)
	{
		this.debug = debug;
		this.server = server;
		this.tid = tid;
		this.s = s;
	}
	
	public void run()
	{
		ObjectInputStream ois = null;
		ObjectOutputStream oos = null;
		
		try
		{
			s.setTcpNoDelay(true);
			
			ois = new ObjectInputStream(s.getInputStream());
			oos = new ObjectOutputStream(s.getOutputStream());
			
			for (boolean run = true; run; )
			{  			
  			int i = ois.readInt();
  			
  			if (i == Common.REC)
  			{
  				ClientMessage cm = (ClientMessage) ois.readObject();
          
          if (debug) // Redundant, but maybe faster.
          {
          	server.debugPrintln("[ServerThread] Received: " + cm);
          }
                    
          oos.writeObject(new ServerMessage(cm.getServerMessageSize(), server.kill)); // Not synchronized, but shouldn't matter overall (Client may end up doing an extra iteration).
          oos.flush();
          oos.reset();
          
          if (server.isCounting()) 
          {
            server.incrementCount(tid);
            
            if (debug) // Redundant, but maybe faster.
            {
            	server.debugPrintln("[ServerThread] Current count:" + server.getCountTotal());
            }
          }				
  			}
  			else //if (i == Common.QUIT)
  			{
  				run = false;
  				
  				server.removeClient();
  				
  				if (debug) // Redundant, but maybe faster.
  				{
  					server.debugPrintln("[ServerThread] Clients remaning: " + server.getNumClients());
  				}
  			}
			}
		}
		catch (Exception x)
		{
			throw new RuntimeException(x);
		}
		finally
		{
			try
			{
  			Common.closeOutputStream(oos);
  			Common.closeInputStream(ois);
  			Common.closeSocket(s);
			}
			catch (Exception x)
			{
				x.printStackTrace(); 
			}
		}
	}
}