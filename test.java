import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.Method;

public void optimize() {
    ConstantPoolGen cpgen = this.gen.getConstantPool();
    Method[] methods = this.original.getMethods();

    for (Method method : methods) {
        if (!method.getName().equals("methodOne")) {
            continue;  // Optimize only the targeted method
        }

        MethodGen mgen = new MethodGen(method, this.original.getClassName(), cpgen);
        InstructionList ilist = mgen.getInstructionList();
        if (ilist == null) continue;

        InstructionHandle[] handles = ilist.getInstructionHandles();
        for (int i = 0; i < handles.length - 1; i++) {
            Instruction first = handles[i].getInstruction();
            Instruction second = handles[i + 1].getInstruction();

            if (first instanceof LDC && second instanceof ArithmeticInstruction) {
                LDC ldc = (LDC) first;
                ArithmeticInstruction arithmetic = (ArithmeticInstruction) second;

                Number constantValue = (Number) ldc.getValue(cpgen);
                // Assume next constant is adjacent for simplicity (not always the case in complex scenarios)
                if (i + 2 < handles.length && handles[i + 2].getInstruction() instanceof LDC) {
                    LDC ldc2 = (LDC) handles[i + 2].getInstruction();
                    Number constantValue2 = (Number) ldc2.getValue(cpgen);

                    int result = evaluateArithmetic(constantValue, constantValue2, arithmetic);
                    ilist.insert(handles[i], new LDC(cpgen.addInteger(result)));
                    try {
                        ilist.delete(handles[i], handles[i + 2]);  // Delete the three instruction sequence
                    } catch (TargetLostException e) {
                        handleTargetLost(e);
                    }
                    i += 2;  // Skip over the deleted instructions
                }
            }
        }

        mgen.setInstructionList(ilist);
        mgen.setMaxStack();
        this.gen.replaceMethod(method, mgen.getMethod());
    }

    this.optimized = this.gen.getJavaClass();
}

private int evaluateArithmetic(Number value1, Number value2, ArithmeticInstruction instruction) {
    if (instruction instanceof IADD) return value1.intValue() + value2.intValue();
    else if (instruction instanceof ISUB) return value1.intValue() - value2.intValue();
    else if (instruction instanceof IMUL) return value1.intValue() * value2.intValue();
    else if (instruction instanceof IDIV) return value1.intValue() / value2.intValue();
    else return 0;  // Default case for unsupported operations
}

private void handleTargetLost(TargetLostException e) {
    for (InstructionHandle target : e.getTargets()) {
        InstructionHandle[] targets = target.getInstructionTargets();
        for (InstructionHandle t : targets) {
            try {
                t.setInstruction(new NOP());  // Set NOP for safe deletion
            } catch (Exception ex) {
                System.out.println("Error handling target lost: " + ex);
            }
        }
    }
}
