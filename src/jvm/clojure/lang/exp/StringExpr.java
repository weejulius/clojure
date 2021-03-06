package clojure.lang.exp;

import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class StringExpr extends LiteralExpr {
    public final String str;

    public StringExpr(String str) {
        this.str = str;
    }

    Object val() {
        return str;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (context != C.STATEMENT)
            gen.push(str);
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return String.class;
    }
}
