package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class TheVarExpr implements Expr {
    public final Var var;

    public TheVarExpr(Var var) {
        this.var = var;
    }

    public Object eval() {
        return var;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        objx.emitVar(gen, var);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return Var.class;
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            Symbol sym = (Symbol) RT.second(form);
            Var v = Compiler.lookupVar(sym, false);
            if (v != null)
                return new TheVarExpr(v);
            throw Util.runtimeException("Unable to resolve var: " + sym + " in this context");
        }
    }
}
