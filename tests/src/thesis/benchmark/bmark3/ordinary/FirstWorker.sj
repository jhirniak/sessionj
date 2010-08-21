//$ bin/sessionjc -cp tests/classes/ tests/src/thesis/benchmark/bmark3/ordinary/FirstWorker.sj -d tests/classes/
//$ bin/sessionj -cp tests/classes/ thesis.benchmark.bmark3.ordinary.FirstWorker false 6666 4440 4441 10

package thesis.benchmark.bmark3.ordinary;

import java.util.Arrays;

import sessionj.runtime.SJIOException;
import sessionj.runtime.SJProtocol;
import sessionj.runtime.net.SJServerSocket;
import sessionj.runtime.net.SJServerSocketCloser;
import sessionj.runtime.net.SJService;
import sessionj.runtime.net.SJSocket;

import thesis.benchmark.Killable;
import thesis.benchmark.bmark3.Common;
import thesis.benchmark.bmark3.Particle;
import thesis.benchmark.bmark3.ParticleV;

public class FirstWorker
{
	private static protocol LAST_LINK_SERVER sbegin.?[?[!<Particle[]>]*]* // No ring token message
			
	private volatile boolean run = true;
	private volatile boolean finished = false;
	private SJServerSocketCloser ssc;	
			 
	public void run(boolean debug, int port, int port_l, int port_r, int numParticles) throws Exception
	{
		final noalias SJServerSocket ss;
		final noalias SJServerSocket ss_l;
		final noalias SJServerSocket ss_r;
		try(ss, ss_l, ss_r)
		{			
			ss = SJServerSocket.create(Common.NBODY_SERVER, port);
			ssc = ss.getCloser();
			
			Common.debugPrintln(debug, "[FirstWorker] Service started on port: " + port);
			
			ss_l = SJServerSocket.create(Common.LINK_SERVER, port_l);
			ss_r = SJServerSocket.create(LAST_LINK_SERVER, port_r);
			
			while(run) 
			{
				final noalias SJSocket s;
				final noalias SJSocket s_l;
				final noalias SJSocket s_r;
				try(s, s_l, s_r)
				{	
					s = ss.accept();
					
					Common.debugPrintln(debug, "[FirstWorker] Accepted client.");
					
					s.receiveBoolean();
					Particle[] particles = (Particle[]) s.receive();
					ParticleV[] pvs = (ParticleV[]) s.receive();						
					
					//Common.debugPrintln(debug, "[FirstWorker] Initial: " + Arrays.toString(particles));
					
					s_l = ss_l.accept();										
					s_l.send(1);
					s_r = ss_r.accept();
					
					Common.debugPrintln(debug, "[FirstWorker] Accepted left and right links.");
					
					int i = 0;				
					<s_l, s_r>.inwhile()
					{				
						Common.debugPrintln(debug, "\n[FirstWorker] Iteration: " + i);
						Common.debugPrintln(debug, "[FirstWorker] Particles: " + Arrays.toString(particles));				
					
						Particle[] current = new Particle[numParticles];										
						System.arraycopy(particles, 0, current, 0, numParticles);					
						<s_l, s_r>.inwhile()
						{					
							s_r.send(current);
							Common.computeForces(particles, current, pvs);						
							current = (Particle[]) s_l.receive();
						}										
						Common.computeForces(particles, current, pvs);					
						Common.computeNewPos(particles, pvs, i);	
						
						i++;
					}
					
					Common.debugPrintln(debug, "\n[FirstWorker] Iteration: " + i);
					Common.debugPrintln(debug, "[FirstWorker] Particles: " + Arrays.toString(particles));
					
					s.send(particles);
				}
				finally { }
			}
		}
		finally 
		{ 
			finished = true;			
		}
	}
	
	public void kill() throws Exception
  {  	  	
  	run = false; // It's important that no more clients are trying to connect after this point		
  	ssc.close(); // Break the accepting loop (make the blocked accept throw an exception)		
		while (!this.finished);
  }	

	public static void main(String args[]) throws Exception
	{
		boolean debug = Boolean.parseBoolean(args[0]);
		int port = Integer.parseInt(args[1]);
		int port_l = Integer.parseInt(args[2]);
		int port_r = Integer.parseInt(args[3]);				
		int numParticles = Integer.parseInt(args[4]);		
		
		if (numParticles > Common.MAX_PARTICLES/* && numParticles <= MAX_PROCESSORS*/)
		{	
			throw new RuntimeException("[FirstWorker] Too many particles: " + numParticles);
		}
		
		FirstWorker fw = new FirstWorker();			
		fw.run(debug, port, port_l, port_r, numParticles);
	}	
}
