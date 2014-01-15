package clojure.lang.exp;

import clojure.lang.Compiler.C;
import clojure.lang.Expr;
import clojure.lang.Symbol;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class UnresolvedVarExpr implements Expr {
    public final Symbol symbol;

    public UnresolvedVarExpr(Symbol symbol) {
        this.symbol = symbol;
    }

    public boolean hasJavaClass() {
        return false;
    }

    public Class getJavaClass() {
        throw new IllegalArgumentException(
                "UnresolvedVarExpr has no Java class");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
    }

    public Object eval() {
        throw new IllegalArgumentException(
                "UnresolvedVarExpr cannot be evalled");
    }
}
