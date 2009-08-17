// bin/sessionjc tests/src/runtime/delegation/case1b/C.sj -d tests/classes/
// bin/sessionj -cp tests/classes/ runtime.delegation.case1b.C 9999

package runtime.delegation.case1b;

import sessionj.runtime.*;
import sessionj.runtime.net.*;

class C 
{
	public static void main(String[] args) throws Exception 
	{
		final noalias protocol p_a { ?(int).!<int> }
		final noalias protocol p_b { sbegin.?(@(p_a)) }

		final noalias SJServerSocket ss;

		try (ss)
		{
			ss = SJServerSocketImpl.create(p_b, Integer.parseInt(args[0]));
			
			final noalias SJSocket s_b;		
			final noalias SJSocket s_a;

			try (s_b, s_a)
			{
				s_b = ss.accept();

				s_a = (@(p_a)) s_b.receive();
				
				System.out.println("Received: " + s_a.receiveInt());				
				
				s_a.send(456);
			}
			finally
			{

			}
		}
		finally
		{

		}
	}
}
