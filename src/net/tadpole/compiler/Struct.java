package net.tadpole.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.util.Pair;
import net.tadpole.compiler.ast.Expression;
import net.tadpole.compiler.ast.FileNode;
import net.tadpole.compiler.ast.ParameterListNode;
import net.tadpole.compiler.ast.ParameterNode;
import net.tadpole.compiler.ast.StructNode;
import net.tadpole.compiler.ast.VariableDecNode;
import net.tadpole.compiler.util.Triplet;

public class Struct
{
	private static final List<Struct> structs = new ArrayList<Struct>();
	
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
			newStructs.add(new Struct(structNode.moduleName, structNode.name, parameters, attributes));
		}
		structs.addAll(newStructs);
		return newStructs;
	}
	
	public final String moduleName;
	public final String name;
	public final List<Pair<Type, String>> parameters;
	public final List<Triplet<Type, String, Expression>> attributes;
	
	private Struct(String moduleName, String name, List<Pair<Type, String>> parameters, List<Triplet<Type, String, Expression>> attributes)
	{
		this.moduleName = moduleName;
		this.name = name;
		this.parameters = parameters;
		this.attributes = attributes;
	}
}