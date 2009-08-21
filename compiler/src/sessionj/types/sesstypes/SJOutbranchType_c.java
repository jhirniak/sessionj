package sessionj.types.sesstypes;

import java.util.*;

import polyglot.types.*;

import sessionj.util.SJLabel;

import static sessionj.SJConstants.*;

public class SJOutbranchType_c extends SJBranchType_c implements SJOutbranchType
{
	public static final long serialVersionUID = SJ_VERSION;

	public SJOutbranchType_c(TypeSystem ts)
	{
		super(ts);
	}

	public SJOutbranchType branchCase(SJLabel lab, SJSessionType st)
	{
		return (SJOutbranchType) super.branchCase(lab, st);
	}
	
	protected boolean eligibleForEquals(SJSessionType st)
	{
		return (st instanceof SJOutbranchType) && (labelSet().equals(((SJOutbranchType) st).labelSet()));
	}
	
	protected boolean eligibleForSubtype(SJSessionType st)
	{
		return (st instanceof SJOutbranchType) && (labelSet().containsAll(((SJOutbranchType) st).labelSet()));
	}
	
	protected boolean eligibleForDualtype(SJSessionType st)
	{
		return (st instanceof SJInbranchType)  && (labelSet().containsAll(((SJInbranchType) st).labelSet()));
	}
	
	public SJSessionType nodeSubsume(SJSessionType st) throws SemanticException
	{
		if (!(st instanceof SJOutbranchType)) 
		{
			throw new SemanticException("[SJOutbranchType_c] Not subsumable: " + this + ", " + st);
		}
		
		SJBranchType them = (SJBranchType) st;
		SJBranchType res = skeleton();
		
		for (SJLabel lab : labelSet())
		{			
			SJSessionType foo = branchCase(lab);
			
			if (them.hasCase(lab))
			{				
				SJSessionType bar = them.branchCase(lab);
			
				if (foo == null)
				{
					if (bar != null)
					{
						throw new SemanticException("[SJOutbranchType_c] Not subsumable: " + this + ", " + st);
					}
				}
				else
				{
					if (bar == null)
					{
						throw new SemanticException("[SJOutbranchType_c] Not subsumable: " + this + ", " + st);
					}
					
					foo = foo.subsume(them.branchCase(lab));
				}
			}
			
			res = res.branchCase(lab, foo);
		}
		
		for (SJLabel lab: them.labelSet())
		{						
			if (!hasCase(lab))
			{
				res = res.branchCase(lab, them.branchCase(lab));
			}
		}
		
		return res;
	}
	
	protected Set<SJLabel> selectComparsionLabelSet(Set<SJLabel> ourLabels, Set<SJLabel> theirLabels, NodeComparison op)  
	{
		switch (op)
		{
			case EQUALS: return ourLabels; 
			case SUBTYPE: return theirLabels;
			case DUALTYPE: return ourLabels;				
		}
		
		throw new RuntimeException("[SJOutbranchType_c] Shouldn't get here: " + ourLabels + " " + op + " " + theirLabels);
	}
	
	protected SJOutbranchType skeleton()
	{
		return typeSystem().SJOutbranchType();
	}
	
	protected String branchConstructorOpen()
	{
		return SJ_STRING_OUTBRANCH_OPEN;
	}
	
	protected String branchConstructorClose()
	{
		return SJ_STRING_OUTBRANCH_CLOSE;
	}	
}
