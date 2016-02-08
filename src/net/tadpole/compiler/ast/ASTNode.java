package net.tadpole.compiler.ast;

import java.util.ArrayList;
import java.util.List;

public abstract class ASTNode
{
	protected ASTNode parent;
	protected List<ASTNode> children;
	
	public ASTNode(ASTNode parent, boolean customList)
	{
		setParent(parent);
		if(!customList)
			children = new ArrayList<ASTNode>();
	}
	
	public ASTNode(ASTNode parent)
	{
		this(parent, false);
	}
	
	public ASTNode getParent()
	{
		return parent;
	}
	
	public ASTNode setParent(ASTNode node)
	{
		ASTNode old = parent;
		if(old != null)
			old.removeChild(this);
		parent = node;
		if(parent != null)
			parent.addChild(this);
		return old;
	}
	
	public List<ASTNode> getChildren()
	{
		return children;
	}
	
	public void addChild(ASTNode node)
	{
		children.add(node);
	}
	
	public void addChild(int index, ASTNode node)
	{
		children.add(index, node);
	}
	
	public boolean removeChild(ASTNode node)
	{
		return children.remove(node);
	}
	
	public ASTNode removeChild(int index)
	{
		return children.remove(index);
	}
	
	public int indexOf(ASTNode node)
	{
		for(int i = 0; i < children.size(); i++)
			if(children.get(i) == node)
				return i;
		return -1;
	}
	
	public ASTNode setChild(int index, ASTNode node)
	{
		return children.set(index, node);
	}
}