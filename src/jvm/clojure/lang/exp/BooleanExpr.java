package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class BooleanExpr extends LiteralExpr {
    public final boolean val;


    public BooleanExpr(boolean val) {
        this.val = val;
    }

    Object val() {
        return val ? RT.T : RT.F;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (val)
            gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "TRUE", Compiler.BOOLEAN_OBJECT_TYPE);
        else
            gen.getStatic(Compiler.BOOLEAN_OBJECT_TYPE, "FALSE", Compiler.BOOLEAN_OBJECT_TYPE);
        if (context == C.STATEMENT) {
            gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return Boolean.class;
    }
}
