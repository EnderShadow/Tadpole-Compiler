package net.tadpole.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;

import javafx.util.Pair;
import net.tadpole.compiler.ast.Expression;
import net.tadpole.compiler.ast.FileNode;
import net.tadpole.compiler.ast.FunctionDecNode;
import net.tadpole.compiler.ast.ParameterListNode;
import net.tadpole.compiler.ast.ParameterNode;
import net.tadpole.compiler.ast.StructNode;
import net.tadpole.compiler.ast.VariableDecNode;
import net.tadpole.compiler.util.Triplet;
import net.tadpole.compiler.util.TypeUtils;

public class Struct
{
	public static final List<Struct> structs = new ArrayList<Struct>();
	
	public static List<Struct> registerStructs(FileNode fileNode)
	{
		return addStructs(fileNode.getChildren().stream().filter(n -> n instanceof StructNode).map(sn -> (StructNode) sn).collect(Collectors.toList()));
	}
	
	private static List<Struct> addStructs(List<StructNode> structNodes)
	{
		List<Struct> newStructs = new ArrayList<Struct>(structNodes.size());
		for(StructNode structNode : structNodes)
		{
			List<Pair<Type, String>> parameters;
			Optional<ParameterListNode> pln = structNode.getChildren().stream().filter(n -> n instanceof ParameterListNode).map(n -> (ParameterListNode) n).findFirst();
			if(pln.isPresent())
				parameters = pln.get().getChildren().stream().map(n -> (ParameterNode) n).map(pn -> new Pair<Type, String>(pn.type, pn.name)).collect(Collectors.toList());
			else
				parameters = Collections.emptyList();
			List<VariableDecNode> vdns = structNode.getChildren().stream().filter(n -> n instanceof VariableDecNode).map(n -> (VariableDecNode) n).collect(Collectors.toList());
			List<Triplet<Type, String, Expression>> attributes = vdns.stream().map(vdn -> new Triplet<Type, String, Expression>(vdn.type, vdn.name, vdn.expression)).collect(Collectors.toList());
			List<FunctionDecNode> fdns = structNode.getChildren().stream().filter(n -> n instanceof FunctionDecNode).map(n -> (FunctionDecNode) n).collect(Collectors.toList());
			List<Function> functions = fdns.stream().map(Function::new).collect(Collectors.toList());
			newStructs.add(new Struct(structNode.moduleName, structNode.name, parameters, attributes, functions));
		}
		structs.addAll(newStructs);
		return newStructs;
	}
	
	public final String moduleName;
	public final String name;
	public final List<Pair<Type, String>> parameters;
	public final List<Triplet<Type, String, Expression>> attributes;
	public final List<Function> functions;
	
	private Struct(String moduleName, String name, List<Pair<Type, String>> parameters, List<Triplet<Type, String, Expression>> attributes, List<Function> functions)
	{
		this.moduleName = moduleName;
		this.name = name;
		this.parameters = parameters;
		this.attributes = attributes;
		this.functions = functions;
	}
	
	public ClassGen toBytecode()
	{
		// create class
		int accFlags = Constants.ACC_PUBLIC | Constants.ACC_STATIC;
		ClassGen cg = new ClassGen(moduleName + "$" + name, "java.lang.Object", moduleName + ".tadpole", accFlags, new String[0]);
		
		// add fields
		Field[] fa = attributes.stream().map(triplet -> new FieldGen(Constants.ACC_PUBLIC, triplet.first.toBCELType(), triplet.second, cg.getConstantPool()).getField()).peek(cg::addField).toArray(Field[]::new);
		
		functions.stream().map(f -> f.toBytecode(cg).getMethod()).forEach(cg::addMethod);
		
		// <-- START CONSTRUCTOR -->
		// create constructor
		InstructionList il = new InstructionList();
		MethodGen mg = new MethodGen(Constants.ACC_PUBLIC, org.apache.bcel.generic.Type.VOID, parameters.stream().map(pair -> pair.getKey().toBCELType()).toArray(org.apache.bcel.generic.Type[]::new), parameters.stream().map(pair -> pair.getValue()).toArray(String[]::new), "<init>", cg.getClassName(), il, cg.getConstantPool());
		
		// generate constructor body
		InstructionFactory factory = new InstructionFactory(cg);
		
		// call super constructor
		il.append(InstructionConstants.THIS);
		il.append(factory.createInvoke("java.lang.Object", "<init>", org.apache.bcel.generic.Type.VOID, new org.apache.bcel.generic.Type[0], Constants.INVOKESPECIAL));
		
		// initialize all instance variables
		for(Triplet<Type, String, Expression> field : attributes)
		{
			// push this and expression to stack
			il.append(InstructionConstants.THIS);
			Pair<InstructionList, org.apache.bcel.generic.Type> exprBytecode = field.third.toBytecode(cg, mg);
			il.append(exprBytecode.getKey());
			
			org.apache.bcel.generic.Type fieldType = field.first.toBCELType();
			org.apache.bcel.generic.Type exprType = exprBytecode.getValue();
			
			// convert expression if needed
			if(!fieldType.equals(exprType))
				il.append(TypeUtils.cast(exprType, fieldType, factory));
			
			// store expression in field
			il.append(factory.createPutField(cg.getClassName(), field.second, fieldType));
		}
		il.append(InstructionConstants.RETURN);
		
		// add constructor
		mg.removeNOPs();
		mg.setMaxLocals();
		mg.setMaxStack();
		cg.addMethod(mg.getMethod());
		
		// <-- END CONSTRUCTOR -->
		
		// return class
		return cg;
	}
}