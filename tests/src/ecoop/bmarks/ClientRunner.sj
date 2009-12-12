//$ bin/sessionj -cp tests/classes/ ecoop.bmarks.ClientRunner false localhost 8888 1 100 JT

package ecoop.bmarks;

import ecoop.bmarks.java.thread.client.*;
import ecoop.bmarks.sj.event.client.*;

// Spawns LoadClients.
public class ClientRunner 
{
  public static void main(String [] args) 
  {
    final boolean debug = Boolean.parseBoolean(args[0]);
    final String host = args[1];
    final int port = Integer.parseInt(args[2]);

    int loadClients = Integer.parseInt(args[3]);    
    final int messageSize = Integer.parseInt(args[4]);
    
  	final String server = args[5];
  	
  	if (!(server.equals(SignalClient.JAVA_THREAD) || server.equals(SignalClient.JAVA_EVENT) || server.equals(SignalClient.SJ_THREAD) || server.equals(SignalClient.SJ_EVENT)))
		{
  		System.out.println("[SignalServer] Bad server flag: " + server);
  		
  		return;
		}   
    
    int clientNum = 0;

    for (int i = 0; i < loadClients; i++)	
    {
      final int cn = clientNum++;
      
      new Thread() 
      {
        public void run() 
        {
        	try
        	{
        		if (server.equals(SignalClient.JAVA_THREAD))
        		{
        			new ecoop.bmarks.java.thread.client.LoadClient(debug, host, port, cn, messageSize).run();
        		}
        		else if (server.equals(SignalClient.SJ_EVENT))
        		{
        			new ecoop.bmarks.sj.event.client.LoadClient(debug, host, port, cn, messageSize).run();
        		}
        	}
        	catch (Exception x)
        	{
        		throw new RuntimeException(x);
        	}
        }
      }.start();
    }
  }
}