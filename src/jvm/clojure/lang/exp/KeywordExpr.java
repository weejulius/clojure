package clojure.lang.exp;

import clojure.lang.Compiler.C;
import clojure.lang.Keyword;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class KeywordExpr extends LiteralExpr {
    public final Keyword k;

    public KeywordExpr(Keyword k) {
        this.k = k;
    }

    Object val() {
        return k;
    }

    public Object eval() {
        return k;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        objx.emitKeyword(gen, k);
        if (context == C.STATEMENT)
            gen.pop();

    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return Keyword.class;
    }
}
