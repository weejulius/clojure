package clojure.lang;

import clojure.lang.Compiler.C;
import clojure.lang.exp.ObjExpr;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/ /*
 *  an interface to present an expression
 *
 *  for example:
 *
 *  TODO
 *
 * */
public interface Expr {
    Object eval();

    void emit(C context, ObjExpr objx, GeneratorAdapter gen);

    boolean hasJavaClass();

    Class getJavaClass();
}
