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

		} catch(IOException e){
			e.printStackTrace();
		}
	}
	public void reset(){
		this.gen = new ClassGen(this.original);
		this.cpgen = this.gen.getConstantPool();
		this.in_loop = false;
		this.loaded_instructions = new ArrayList<InstructionHandle>();
	}

	private void handle_instruction(InstructionHandle handle, InstructionList instructionList){
		Instruction instruction = handle.getInstruction();

		// Operation Instructions (Instructions that use the previous 2 loaded values)
		if (instruction instanceof ArithmeticInstruction){
			handleArithmetic(handle, instructionList);
		}

		if (instruction instanceof LCMP){
			handleLongComparison(handle, instructionList);
		}
		else if (instruction instanceof IfInstruction){
			handleComparison(handle, instructionList);
		}

		if (instruction instanceof GotoInstruction){
			handleGoTo(handle, instructionList);
		}

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

// Todo: loadInstructions
			removeHandle(instructionList, this.loaded_instructions.remove(list.size() - 1));
			handle.setInstruction(createLoadInstruction(valuesStack.peek(), cpgen)); // change conversion instruction with load.
			loadInstructions.push(handle); // push new load instruction onto the loadInstruction stack.

		}
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

	private void removeHandle(InstructionList instructionList, InstructionHandle handle) {
		InstructionHandle next = handle.getNext(); // used to get the next instruction if it's a target.
		try {
			instructionList.delete(handle);
		} catch (TargetLostException e) {
			handleTargetLostException(e, next);
		}
	}

	private void handleTargetLostException(TargetLostException e, InstructionHandle next) {
		for (InstructionHandle target : e.getTargets()) {
			for (InstructionTargeter destination : target.getTargeters()) {
				destination.updateTarget(target, next);
			}
		}
	}


	// Method that checks whether to delete the Else Branch of a IfInstruction, and deletes it if necessary.
	private void handleGoTo(InstructionHandle handle, InstructionList instructionList) {
		if (deleteElseBranch){
			deleteElseBranch = false;
			GotoInstruction instruction = (GotoInstruction) handle.getInstruction();
			InstructionHandle targetHandle = instruction.getTarget();
			removeHandle(instructionList, handle, targetHandle.getPrev());
		}
	}

	private void handleLongComparison(InstructionHandle handle, InstructionList instructionList) {
		if (blockOperationIfInLoop) return;

		long first = (Long) valuesStack.pop();
		long second = (Long) valuesStack.pop();

		// LCMP returns -1, 0, 1.
		int result = 0;
		if (first > second) result = 1;
		else if (first < second) result = -1;

		removePreviousTwoLoadInstructions(instructionList);
		handle.setInstruction(createLoadInstruction(result, cpgen));
		loadInstructions.push(handle);
		valuesStack.push(result);
	}

	private void handleComparison(InstructionHandle handle, InstructionList instructionList) {
		if (blockOperationIfInLoop) return;

		IfInstruction comparisonInstruction = (IfInstruction) handle.getInstruction();

		if (getComparisonOutcome(instructionList, comparisonInstruction)) {
			removeHandle(instructionList, handle);
			deleteElseBranch = true;
		} else {
			// if outcome is false then remove the comparison, and remove the if branch (all instructions to target).
			InstructionHandle targetHandle = comparisonInstruction.getTarget();
			removeHandle(instructionList, handle, targetHandle.getPrev());
		}
	}

	private void handleStore(InstructionHandle handle) {
		Number value = valuesStack.pop();
		loadInstructions.pop();
		displayLog("[STORE] Storing Value: " + value);
		int key = ((StoreInstruction) handle.getInstruction()).getIndex();
		variables.put(key, value);
	}

	private void handleVariableLoad(InstructionHandle handle) {
		int variableKey = ((LoadInstruction) handle.getInstruction()).getIndex();
		valuesStack.push(variables.get(variableKey));
		loadInstructions.push(handle);
		displayLog("[LOAD_VARIABLE] Loaded Variable Value: " + valuesStack.peek());
		// if not already blocking: block if this variable load is in a loop & the variable stores a value in the loop.
		blockOperationIfInLoop = blockOperationIfInLoop || variableChangesInLoop(handle, variableKey);
		displayLog("[BLOCK] Status: " + blockOperationIfInLoop);
	}

	private void handleLoad(InstructionHandle handle) {
		valuesStack.push(getLoadConstantValue(handle.getInstruction(), cpgen));
		loadInstructions.push(handle);
		displayLog("[LOAD_CONSTANT] Loaded Constant Value: " + valuesStack.peek());
	}

	private void handleArithmetic(InstructionHandle handle, InstructionList instructionList) {
		if (blockOperationIfInLoop) return; // if block operation is true, then skip this instruction.

		Number second = valuesStack.pop(); // last load is on the top of the stack.
		Number first = valuesStack.pop();
		valuesStack.push(performArithmeticOperation(first, second, handle.getInstruction()));

		displayLog("[ARITHMETIC_OPERATION] Calculated Value: " + valuesStack.peek() + " Pushed Onto Stack.");
		condenseOperationInstructions(instructionList, handle, valuesStack.peek()); // using peek because it needs to be in stack.
	}


	private void method_process(Method m){
		Code method_code = m.getCode();
		InstructionList instructionList = new InstructionList(method_code.getCode());

		MethodGen mgen = new MethodGen(m.getAccessFlags(), m.getReturnType(), m.getArgumentTypes(),
				null, m.getName(), this.gen.getClassName(), instructionList, this.cpgen);


		for (InstructionHandle handle : instructionList.getInstructionHandles()) {
			handle_instruction(handle, instructionList);
		}


//		instructionList.setPositions(true);
		mgen.setMaxStack();
		mgen.setMaxLocals();
		Method newMethod = mgen.getMethod();
		this.gen.replaceMethod(m, newMethod);
	}

    private void optimize() {
        ClassGen cgen = new ClassGen(original);
        // ConstantPoolGen cpgen = cgen.getConstantPool();

        Method[] methods = cgen.getMethods();
        for (Method m : methods) {
			method_process(m);
        }

//        this.optimized = cgen.getJavaClass();
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