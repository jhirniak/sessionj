package sessionj.runtime.session.contexts;

import sessionj.types.sesstypes.SJSessionType;
import sessionj.util.SJLabel;

public class SJInbranchContext extends SJBranchContext
{
	public SJInbranchContext(SJLabel lab, SJSessionType active)
	{
		super(lab, active);
	}

	/*public final boolean isInbranchContext()
	{
		return true;
	}*/
}
