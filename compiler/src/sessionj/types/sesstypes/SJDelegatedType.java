package sessionj.types.sesstypes;

import polyglot.types.*;
import sessionj.types.sesstypes.SJSessionType_c.NodeComparison;

import static sessionj.SJConstants.*;

/**
 * 
 * @author Raymond
 *
 * SJDelegatedType means that the session was delegated at some point within the encapsulated implementation type (not that this type is the actual H-O message - that's recorded at the actual delegation operation).
 *
 */
public class SJDelegatedType extends SJSessionType_c implements SJSessionType
{
	public static final long serialVersionUID = SJ_VERSION;

	private SJSessionType st;
	
	public SJDelegatedType(TypeSystem ts, SJSessionType st)
	{
		super(ts);
		
		this.st = st.clone();
	}

	public SJSessionType getDelegatedType()
	{
		return st;
	}
	
	// Move default node methods to SJSessionType_c? Are these suitable default values?
	public boolean nodeEquals(SJSessionType st)
	{
		return false;
	}

	protected boolean eligibleForEquals(SJSessionType st)
	{
		return st instanceof SJDelegatedType;
	}

	protected boolean eligibleForSubtype(SJSessionType st)
	{
		return st instanceof SJDelegatedType;
	}

	protected boolean eligibleForDualtype(SJSessionType st)
	{
		return st instanceof SJDelegatedType;
	}
		
	protected boolean compareNode(NodeComparison op, SJSessionType st)
	{
		SJSessionType ours = getDelegatedType();
		SJSessionType theirs = ((SJDelegatedType) st).getDelegatedType();
		
		switch (op)
		{
			case EQUALS: 
			{
				return ours.equals(theirs);
			}
			case SUBTYPE:
			{
				return ours.isSubtype(theirs);
			}
			case DUALTYPE: 
			{
				return ours.isDualtype(theirs);
			}
		}
		
		throw new RuntimeException("[SJCBeginType_c] Shouldn't get here: " + op);
	}
	
	public boolean nodeWellFormed()
	{
		return getDelegatedType().nodeWellFormed();  
	}

	public SJSessionType nodeClone()
	{
		return typeSystem().SJDelegatedType(getDelegatedType().clone());
	}

	public SJSessionType nodeSubsume(SJSessionType st) throws SemanticException
	{
		//if (st instanceof SJDelegatedType)
		{			
			return typeSystem().SJDelegatedType(getDelegatedType().subsume(((SJDelegatedType) st).getDelegatedType()));
		}
		/*else // This case shouldn't happen due to implementation of subsume in SJSessionType_c.
		{
			return getDelegatedType().subsume(st);
		}*/
	}

	public String nodeToString()
	{
		return SJ_STRING_DELEGATED_TYPE + "(" + getDelegatedType() + ")";
	}
}
