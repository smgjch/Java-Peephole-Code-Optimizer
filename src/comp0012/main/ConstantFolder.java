package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.Constants;

public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;
	ConstantPoolGen cpgen;
	JavaClass original = null;
	JavaClass optimized = null;
	boolean in_loop;
	ArrayList<InstructionHandle> loaded_instructions;
	Stack<Number> valuesStack;
	HashMap<Integer, Number> stored_variables;

	public ConstantFolder(String classFilePath)
	{
		try{

			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
			this.cpgen = this.gen.getConstantPool();
			this.in_loop = false;
			this.loaded_instructions = new ArrayList<InstructionHandle>();
			this.valuesStack = new Stack<Number>();
			this.stored_variables = new HashMap<Integer, Number>();

		} catch(IOException e){
			e.printStackTrace();
		}
	}
	public void reset(){
		this.gen = new ClassGen(this.original);
		this.cpgen = this.gen.getConstantPool();
		this.in_loop = false;
		this.loaded_instructions = new ArrayList<InstructionHandle>();
		this.valuesStack = new Stack<Number>();
		this.stored_variables = new HashMap<Integer, Number>();
	}

	private void handle_instruction(InstructionHandle handle, InstructionList instructionList){
		Instruction instruction = handle.getInstruction();

		// Operation Instructions (Instructions that use the previous 2 loaded values)
		if (instruction instanceof ArithmeticInstruction){
			handleArithmetic(handle, instructionList);
		}

		if (instruction instanceof LCMP){
			handleComparison(handle, instructionList);
		}
		// if (instruction instanceof IfInstruction){
		// 	handleComparison(handle, instructionList);
		// }

		// if (instruction instanceof GotoInstruction){
		// 	handleGoTo(handle, instructionList);
		// }

		if (instruction instanceof StoreInstruction){
			handleStore(handle);
		}

		if (is_load_constant_instruction(instruction)){
			handleLoad(handle);
		}
		else if (instruction instanceof LoadInstruction){
			handleVariableLoad(handle);
		}
		else if (instruction instanceof ConversionInstruction){
			handleConversion(handle, instructionList);
		}
		else{
			this.in_loop = false;
		}
		instructionList.setPositions(true);
	}

	private static final Set<Class<? extends Instruction>> CONSTANT_INSTRUCTIONS = Set.of(
			LDC.class, LDC2_W.class, SIPUSH.class, BIPUSH.class,
			ICONST.class, FCONST.class, DCONST.class, LCONST.class
	);

	private static boolean is_load_constant_instruction(Instruction instruction){
		return CONSTANT_INSTRUCTIONS.stream().anyMatch(c -> c.isInstance(instruction));
	}
	

	// Method that converts the value on the top of the stack to another type.
	private void handleConversion(InstructionHandle handle, InstructionList instructionList) {
		if (is_load_constant_instruction(this.loaded_instructions.get(this.loaded_instructions.size() - 1).getInstruction()) || !this.in_loop) {

			valuesStack.push(to_value(handle.getInstruction(), valuesStack.pop()));

// Todo: loaded_instructions
			InstructionHandle  instruction_removed = this.loaded_instructions.remove(this.loaded_instructions.size() - 1);
			remove_handle(instructionList, instruction_removed); 

			Instruction instruction_to_set = build_load(valuesStack.peek(), this.cpgen);
			handle.setInstruction(instruction_to_set);

			this.loaded_instructions.add(handle); 

		}
	}

	private static Instruction build_load(Number value, ConstantPoolGen cpgen){
		if (value instanceof Double){
			return new LDC2_W(cpgen.addDouble((Double) value)); 
		}
		else if (value instanceof Integer){

		    if ((Integer) value >= -1 && (Integer) value <= 5) return new ICONST((Integer) value);
			return new LDC(cpgen.addInteger((Integer) value)); 
		} 
		else if (value instanceof Long){
			return new LDC2_W(cpgen.addLong((Long) value)); 
		} 
		else if (value instanceof Float){
			return new LDC(cpgen.addFloat((Float) value)); 
		}
		throw new IllegalStateException("Illegal");
	}


	private static Number to_value(Instruction instruction, Number value) {
		if (instruction instanceof D2I || instruction instanceof F2I || instruction instanceof L2I){
			return value.intValue();
		}
		else if (instruction instanceof I2L || instruction instanceof D2L || instruction instanceof F2L){
			return value.longValue();
		}
		else if (instruction instanceof I2D || instruction instanceof L2D || instruction instanceof F2D){
			return value.doubleValue();
		}
		else if (instruction instanceof I2F || instruction instanceof L2F || instruction instanceof D2F){
			return value.floatValue();
		}
		return null;
	}

	private void remove_handle(InstructionList instructionList, InstructionHandle handle) {
		InstructionHandle next = handle.getNext(); 
		try {
			instructionList.delete(handle);
		} catch (TargetLostException e) {
			handle_TargetLostException(e, next);
		}
	}

	private void handle_TargetLostException(TargetLostException e, InstructionHandle next) {
		for (InstructionHandle target : e.getTargets()) {
			for (InstructionTargeter destination : target.getTargeters()) {
				destination.updateTarget(target, next);
			}
		}
	}

	
	// private void handleGoTo(InstructionHandle handle, InstructionList instructionList) {
	// 	if (deleteElseBranch){
	// 		deleteElseBranch = false;
	// 		GotoInstruction instruction = (GotoInstruction) handle.getInstruction();
	// 		InstructionHandle targetHandle = instruction.getTarget();
	// 		removeHandle(instructionList, handle, targetHandle.getPrev());
	// 	}
	// }

	private void handleComparison(InstructionHandle handle, InstructionList instructionList) {

		long first = (Long) valuesStack.pop();
		long second = (Long) valuesStack.pop();

		int result = 0;
		if (first > second) result = 1;
		else if (first < second) result = -1;

		remove_handle(instructionList, this.loaded_instructions.remove(this.loaded_instructions.size() - 1));
		remove_handle(instructionList, this.loaded_instructions.remove(this.loaded_instructions.size() - 1));

		handle.setInstruction(build_load(result, this.cpgen));
		this.loaded_instructions.add(handle);
		valuesStack.push(result);
	}



	private void handleStore(InstructionHandle handle) {
		Number value = valuesStack.pop();
		this.loaded_instructions.remove(this.loaded_instructions.size() - 1);

		int key = ((StoreInstruction) handle.getInstruction()).getIndex();
		this.stored_variables.put(key, value);
	}

	private void handleVariableLoad(InstructionHandle handle) {
		int variableKey = ((LoadInstruction) handle.getInstruction()).getIndex();
		valuesStack.push(this.stored_variables.get(variableKey));
		this.loaded_instructions.add(handle);
	}

	private void handleLoad(InstructionHandle handle) {
		Instruction next = handle.getInstruction();
		Number value_to_load = 0;
		if (next instanceof LDC) {
			value_to_load = (Number) ((LDC) next).getValue(this.cpgen); // Todo: ?
		} 
		else if (next instanceof LDC2_W) {
			value_to_load = ((LDC2_W) next).getValue(this.cpgen); 
		} 
		else if (next instanceof SIPUSH) {
			value_to_load = ((SIPUSH) next).getValue();
		} 
		else if (next instanceof ICONST){
			value_to_load = ((ICONST) next).getValue();
		} 
		else if (next instanceof FCONST){
			value_to_load = ((FCONST) next).getValue();
		} 
		else if (next instanceof DCONST){
			value_to_load = ((DCONST) next).getValue();
		} 
		else if (next instanceof LCONST){
			value_to_load = ((LCONST) next).getValue();
		}
		
		valuesStack.push(value_to_load);
		this.loaded_instructions.add(handle);
	}

	private static Number do_calc(Number first, Number second, Instruction nextInstruction){
		Number result=0;

		if (nextInstruction instanceof IADD){
			result = first.intValue() + second.intValue();
		} 
		else if (nextInstruction instanceof ISUB){
			result = first.intValue() - second.intValue();
		} 
		else if (nextInstruction instanceof IMUL){
			result = first.intValue() * second.intValue();
		} 
		else if (nextInstruction instanceof IDIV){
			result = first.intValue() / second.intValue();
		}

		else if (nextInstruction instanceof DADD){
			result = first.doubleValue() + second.doubleValue();
		} 
		else if (nextInstruction instanceof DSUB){
			result = first.doubleValue() - second.doubleValue();
		} 
		else if (nextInstruction instanceof DMUL){
			result = first.doubleValue() * second.doubleValue();
		} 
		else if (nextInstruction instanceof DDIV){
			result = first.doubleValue() / second.doubleValue();
		}


		else if (nextInstruction instanceof FADD){
			result = first.floatValue() + second.floatValue();
		} else if (nextInstruction instanceof FSUB){
			result = first.floatValue() - second.floatValue();
		} else if (nextInstruction instanceof FMUL){
			result = first.floatValue() * second.floatValue();
		} else if (nextInstruction instanceof FDIV){
			result = first.floatValue() / second.floatValue();
		}

		else if (nextInstruction instanceof LADD){
			result = first.longValue() + second.longValue();
		} else if (nextInstruction instanceof LSUB){
			result = first.longValue() - second.longValue();
		} else if (nextInstruction instanceof LMUL){
			result = first.longValue() * second.longValue();
		} else if (nextInstruction instanceof LDIV){
			result = first.longValue() / second.longValue();
		}

        return result;

	}



	private void handleArithmetic(InstructionHandle handle, InstructionList instructionList) {


		Number second = valuesStack.pop(); 
		Number first = valuesStack.pop();
		Number result = do_calc(first, second, handle.getInstruction());
		valuesStack.push(result);
		remove_handle(instructionList, this.loaded_instructions.remove(this.loaded_instructions.size() - 1));
		remove_handle(instructionList, this.loaded_instructions.remove(this.loaded_instructions.size() - 1));

		handle.setInstruction(build_load(valuesStack.peek(), this.cpgen));
        this.loaded_instructions.add(handle);
	}


	private void method_process(Method m){
		Code method_code = m.getCode();
		InstructionList instructionList = new InstructionList(method_code.getCode());



		for (InstructionHandle handle : instructionList.getInstructionHandles()) {
			handle_instruction(handle, instructionList);
		}

		MethodGen mgen = new MethodGen(m.getAccessFlags(), m.getReturnType(), m.getArgumentTypes(),
				null, m.getName(), this.gen.getClassName(), instructionList, this.cpgen);




		mgen.setMaxStack();
		mgen.setMaxLocals();
		Method newMethod = mgen.getMethod();
		this.gen.replaceMethod(m, newMethod);
	}

    private void optimize() {
        // ClassGen cgen = new ClassGen(original);
        // ConstantPoolGen cpgen = cgen.getConstantPool();
		this.gen.setMajor(50);
		this.gen.setMinor(0);
        Method[] methods = this.gen.getMethods();
        for (Method m : methods) {
			method_process(m);
        }

       this.optimized = this.gen.getJavaClass();
    }



	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}