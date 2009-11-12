//$ bin/sessionjc -cp tests/classes/ tests/src/smtp/sj/server/Server.sj -d tests/classes/
//$ bin/sessionj -cp tests/classes/ smtp.sj.server.Server false 8888 

package smtp.sj.server;

import java.util.*;

import sessionj.runtime.*;
import sessionj.runtime.net.*;
import sessionj.runtime.net.SJSessionParameters.*;
import sessionj.runtime.transport.*;
import sessionj.runtime.transport.tcp.*;
import sessionj.runtime.transport.sharedmem.*;
import sessionj.runtime.transport.httpservlet.*;
import sessionj.runtime.session.*;

import smtp.sj.SJUtf8Formatter;

public class Server
{			
	public protocol p_server
	{
		sbegin
		.!<String>
		.!<String>
		.!<String>
	}
	
	public void run(boolean debug, int port) throws Exception
	{
		SJSessionParameters sparams = SJTransportUtils.createSJSessionParameters(SJCompatibilityMode.CUSTOM, new SJUtf8Formatter());
		
		final noalias SJServerSocket ss;
		
		try (ss)
		{
			ss = SJServerSocket.create(p_server, port, sparams);

			final noalias SJSocket s;	
			
			try (s)
			{
				s = ss.accept();
				
				s.send("A");
				s.send("B");
				s.send("C");
			}
			finally
			{
				
			}
		}
		finally
		{
			
		}		
	}

	public static void main(String[] args) throws Exception
	{
		boolean debug = Boolean.parseBoolean(args[0]);
		int port = Integer.parseInt(args[1]);
		
		new Server().run(debug, port);
	}
}