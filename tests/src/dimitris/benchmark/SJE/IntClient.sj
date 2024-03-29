package sessionj.benchmark.SJE;


import sessionj.runtime.*;
import sessionj.runtime.net.*;
import sessionj.runtime.transport.*;

import java.util.Random;

public class IntClient implements Client {

  protocol reqRep !<String>.?(int)
  protocol clientSide cbegin.@(reqRep)

  private static Random generator = new Random(System.currentTimeMillis());
  private String requestString;

  public IntClient() {
    requestString = new String("Number " + (generator.nextInt() % 1024) + " is beeing send");
  }

  public String client(String domain, int port) {
    final noalias SJService serv = SJService.create(clientSide, domain, port);
    int x = 0;
    noalias SJSocket s;
    try (s) {
      s = serv.request();
      s.send(requestString);
      x = s.receiveInt();
    } catch (SJIOException e) {}
      catch (SJIncompatibleSessionException ee) {}
      finally {}
      return "" + x;
  }
}
