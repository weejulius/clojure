package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/ /*set the value of local binding or java fields
*
* (set! (. instance-expr instanceFieldName-symbol) expr)
* (set! (. Classname-symbol staticFieldName-symbol) expr)
*
* */
public class AssignExpr implements Expr {
    public final AssignableExpr target;
    public final Expr           val;

    public AssignExpr(AssignableExpr target, Expr val) {
        this.target = target;
        this.val = val;
    }

    public Object eval() {
        return target.evalAssign(val);
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        target.emitAssign(context, objx, gen, val);
    }

    public boolean hasJavaClass() {
        return val.hasJavaClass();
    }

    public Class getJavaClass() {
        return val.getJavaClass();
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object frm) {
            ISeq form = (ISeq) frm;
            if (RT.length(form) != 3)
                throw new IllegalArgumentException("Malformed assignment, expecting (set! target val) but "+form
                        .toString());
            Expr target = Compiler.analyze(C.EXPRESSION, RT.second(form));
            if (!(target instanceof AssignableExpr))
                throw new IllegalArgumentException("the target cannot be set! -> " + target.toString());
            return new AssignExpr((AssignableExpr) target, Compiler.analyze(C.EXPRESSION, RT.third(form)));
        }
    }
}
