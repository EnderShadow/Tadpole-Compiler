package net.tadpole.compiler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.util.Pair;
import net.tadpole.compiler.ast.FunctionDecNode;
import net.tadpole.compiler.ast.ParameterListNode;
import net.tadpole.compiler.ast.ParameterNode;
import net.tadpole.compiler.ast.StatementNode;

public class Function
{
	public final String name;
	public final List<Pair<Type, String>> parameters;
	public Type returnType;
	public final List<Statement> statements;
	
	public Function(String name, List<Pair<Type, String>> parameters, Type returnType, List<Statement> statements)
	{
		this.name = name;
		this.parameters = parameters;
		this.returnType = returnType;
		this.statements = statements;
	}
	
	public Function(FunctionDecNode fdn)
	{
		name = fdn.name;
		Optional<ParameterListNode> params = fdn.getChildren().stream().filter(n -> n instanceof ParameterListNode).map(n -> (ParameterListNode) n).findFirst();
		if(params.isPresent())
			parameters = params.get().getChildren().stream().map(n -> (ParameterNode) n).map(pn -> new Pair<Type, String>(pn.type, pn.name)).collect(Collectors.toList());
		else
			parameters = Collections.emptyList();
		returnType = fdn.returnType;
		statements = fdn.getChildren().stream().filter(n -> n instanceof StatementNode).map(n -> Statement.convert((StatementNode) n)).collect(Collectors.toList());
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(obj != null && obj instanceof Function)
		{
			Function other = (Function) obj;
			if(!name.equals(other.name))
				return false;
			if(!parameters.equals(other.parameters))
				return false;
			
			// return type is not used to determine equality
			
			return true;
		}
		return false;
	}
}