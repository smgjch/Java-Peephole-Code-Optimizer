package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.Constants;

public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	private boolean simpleFold(InstructionList instList, ClassGen cgen, ConstantPoolGen cpgen) {
		boolean changed = false;
		InstructionFinder f = new InstructionFinder(instList);
		String pattern = "(LDC|LDC_W|LDC2_W) (LDC|LDC_W|LDC2_W) (IADD|ISUB|IMUL|IDIV|FADD|FSUB|FMUL|FDIV|DADD|DSUB|DMUL|DDIV|LADD|LSUB|LMUL|LDIV)";
		for (Iterator<InstructionHandle[]> it = f.search(pattern); it.hasNext(); ) {
			InstructionHandle[] match = it.next();
			try {

				Object value1 = ((LDC) match[0].getInstruction()).getValue(cpgen);
				Object value2 = ((LDC) match[1].getInstruction()).getValue(cpgen);
				Number result = null;

				// perform correct operation on values
				Short opcode_type = match[2].getInstruction().getOpcode();
				if (opcode_type == Constants.IADD || opcode_type == Constants.ISUB || opcode_type == Constants.IMUL || opcode_type == Constants.IDIV) {
					result = intArithmetic(value1, value2, opcode_type);
				} else if (opcode_type == Constants.FADD || opcode_type == Constants.FSUB || opcode_type == Constants.FMUL || opcode_type == Constants.FDIV){
					result = floatArithmetic(value1, value2, opcode_type);
				} else if (opcode_type == Constants.DADD || opcode_type == Constants.DSUB || opcode_type == Constants.DMUL || opcode_type == Constants.DDIV){
					result = doubleArithmetic(value1, value2, opcode_type);
				} else if (opcode_type == Constants.LADD || opcode_type == Constants.LSUB || opcode_type == Constants.LMUL || opcode_type == Constants.LDIV){
					result = longArithmetic(value1, value2, opcode_type);
				}

				if (result != null) {
					InstructionHandle ih;
					changed = true;
					
					// load results onto stack and delete old instructions
					if (result instanceof Integer) {
						ih = instList.insert(match[0], new LDC(cpgen.addInteger(result.intValue())));
					} else if (result instanceof Float) {
						ih = instList.insert(match[0], new LDC(cpgen.addFloat(result.floatValue())));
					} else if (result instanceof Double) {
						ih = instList.insert(match[0], new LDC2_W(cpgen.addDouble(result.doubleValue())));
					} else if (result instanceof Long) {
						ih = instList.insert(match[0], new LDC2_W(cpgen.addLong(result.longValue())));
					} else {
						continue;
					}
					
					instList.delete(match[0], match[2]);
					instList.redirectBranches(match[0], ih);
				}
			} catch (TargetLostException e) {
				// thrown when one or multiple disposed instructions are still being referenced by an InstructionTargeter object
				for (InstructionHandle target : e.getTargets()) {
					for (InstructionTargeter targeter : target.getTargeters()) {
						targeter.updateTarget(target, null);
					}
				}
			} catch (ClassCastException e) {
				// Cast fail
			}
		}
		return changed;

	}

	private Number intArithmetic(Object value1, Object value2, Short opcode_type) {
		if (value1 instanceof Integer && value2 instanceof Integer) {
			if (opcode_type == Constants.IADD){
				return ((Integer) value1) + ((Integer) value2);
			} else if (opcode_type == Constants.ISUB){
				return ((Integer) value1) - ((Integer) value2);
			} else if (opcode_type == Constants.IMUL){
				return ((Integer) value1) * ((Integer) value2);
			} else{
				return ((Integer) value1) / ((Integer) value2);
			}
		}
		return null;
	}

	private Number floatArithmetic(Object value1, Object value2, Short opcode_type) {
		if (value1 instanceof Float && value2 instanceof Float) {
			if (opcode_type == Constants.FADD){
				return ((Float) value1) + ((Float) value2);
			} else if (opcode_type == Constants.FSUB){
				return ((Float) value1) - ((Float) value2);
			} else if (opcode_type == Constants.FMUL){
				return ((Float) value1) * ((Float) value2);
			} else{
				return ((Float) value1) / ((Float) value2);
			}
		}
		return null;
	}

	private Number doubleArithmetic(Object value1, Object value2, Short opcode_type) {
		if (value1 instanceof Double && value2 instanceof Double) {
			if (opcode_type == Constants.DADD){
				return ((Double) value1) + ((Double) value2);
			} else if (opcode_type == Constants.DSUB){
				return ((Double) value1) - ((Double) value2);
			} else if (opcode_type == Constants.DMUL){
				return ((Double) value1) * ((Double) value2);
			} else{
				return ((Double) value1) / ((Double) value2);
			}
		}
		return null;
	}

	private Number longArithmetic(Object value1, Object value2, Short opcode_type) {
		if (value1 instanceof Long && value2 instanceof Long) {
			if (opcode_type == Constants.LADD){
				return ((Long) value1) + ((Long) value2);
			} else if (opcode_type == Constants.LSUB){
				return ((Long) value1) - ((Long) value2);
			} else if (opcode_type == Constants.LMUL){
				return ((Long) value1) * ((Long) value2);
			} else{
				return ((Long) value1) / ((Long) value2);
			}
		}
		return null;
	}


	private void optimizeMethod(ClassGen cgen, ConstantPoolGen cpgen, Method method) {
		// add all the other optimisation stuff here like dynamic and constant folding
        boolean changed = false;
        MethodGen methodGen = new MethodGen(method, cgen.getClassName(), cpgen);
        InstructionList instList = methodGen.getInstructionList();

        do {
            changed &= simpleFold(instList, cgen, cpgen);

            if (changed) {
                instList.setPositions(true);
                methodGen.setMaxStack();
                methodGen.setMaxLocals();
            }
        } while (changed);

        Method newMethod = methodGen.getMethod();
        cgen.replaceMethod(method, newMethod);

        instList.dispose();
    }

    private void optimize() {
        ClassGen cgen = new ClassGen(original);
        ConstantPoolGen cpgen = cgen.getConstantPool();

        Method[] methods = cgen.getMethods();
        for (Method m : methods) {
            optimizeMethod(cgen, cpgen, m);
        }

        this.optimized = cgen.getJavaClass();
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