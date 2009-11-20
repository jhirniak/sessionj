//$ bin/sessionjc -cp tests/classes/ tests/src/esmtp/sj/messages/Ehlo.sj -d tests/classes/ 

package esmtp.sj.messages;

public class Ehlo extends SmtpCommand 
{
	public static final String EHLO_COMMAND = "EHLO";
	
	private String fqdn;
	
	public Ehlo(String fqdn)
	{
		this.fqdn = fqdn;
	}
		
	public String command()
	{
		return EHLO_COMMAND;
	}
	
	public String body()
	{
		return fqdn;
	}
}
