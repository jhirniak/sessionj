package sessionj.runtime.net;

import sessionj.runtime.SJIOException;
import sessionj.runtime.SJProtocol;
import sessionj.types.sesstypes.SJSessionType;

public class SJRequestingSocket extends SJAbstractSocket
{
	private SJService service;
	
	SJRequestingSocket(SJService service, SJSessionParameters params) throws SJIOException
	{
		super(service.getProtocol(), params); // Could override getProtocol but no point.
		
		this.service = service;
		
		//setParameters(params);
	}

    /**
     * For session receive: type can be a set type, need to know the actual runtime type for typecase
     */
	public SJRequestingSocket(SJProtocol p, SJSessionParameters params, SJSessionType actualType) throws SJIOException
	{
		super(p, params, actualType); // FIXME: null service OK? Probably OK for received sessions.
	}
	
	public SJServerIdentifier getServerIdentifier()
	{
		return service.getServerIdentifier();
	}
}
