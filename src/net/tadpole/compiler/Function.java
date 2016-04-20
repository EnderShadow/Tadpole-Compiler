package net.tadpole.compiler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.bcel.Constants;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ReturnInstruction;

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
	public final Statement statement;
	
	public Function(String name, List<Pair<Type, String>> parameters, Type returnType, Statement statement)
	{
		this.name = name;
		this.parameters = parameters;
		this.returnType = returnType;
		this.statement = statement;
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
		statement = fdn.getChildren().stream().filter(n -> n instanceof StatementNode).map(n -> Statement.convert((StatementNode) n)).findFirst().get();
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
	
	public MethodGen toBytecode(ClassGen cg)
	{
		InstructionList il = new InstructionList();
		MethodGen mg = new MethodGen(Constants.ACC_PUBLIC | Constants.ACC_STATIC, returnType.toBCELType(), parameters.stream().map(p -> p.getKey().toBCELType()).toArray(org.apache.bcel.generic.Type[]::new), parameters.stream().map(Pair::getValue).toArray(String[]::new), name, cg.getClassName(), il, cg.getConstantPool());
		il.append(statement.toBytecode(cg, mg));
		if(!(il.getEnd().getInstruction() instanceof ReturnInstruction))
			il.append(InstructionFactory.createReturn(mg.getReturnType()));
		mg.removeNOPs();
		mg.setMaxLocals();
		mg.setMaxStack();
		return mg;
	}
}