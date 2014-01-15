package clojure.lang.exp;

import clojure.lang.Compiler.C;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class NilExpr extends LiteralExpr {
    Object val() {
        return null;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitInsn(Opcodes.ACONST_NULL);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return null;
    }
}
