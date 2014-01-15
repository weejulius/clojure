package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class ImportExpr implements Expr {
    public final String c;
    final static Method forNameMethod     = Method.getMethod("Class forName(String)");
    final static Method importClassMethod = Method.getMethod("Class importClass(Class)");
    final static Method derefMethod       = Method.getMethod("Object deref()");

    public ImportExpr(String c) {
        this.c = c;
    }

    public Object eval() {
        Namespace ns = (Namespace) RT.CURRENT_NS.deref();
        ns.importClass(RT.classForName(c));
        return null;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.getStatic(Compiler.RT_TYPE, "CURRENT_NS", Compiler.VAR_TYPE);
        gen.invokeVirtual(Compiler.VAR_TYPE, derefMethod);
        gen.checkCast(Compiler.NS_TYPE);
        gen.push(c);
        gen.invokeStatic(Compiler.CLASS_TYPE, forNameMethod);
        gen.invokeVirtual(Compiler.NS_TYPE, importClassMethod);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return false;
    }

    public Class getJavaClass() {
        throw new IllegalArgumentException("ImportExpr has no Java class");
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object form) {
            return new ImportExpr((String) RT.second(form));
        }
    }
}
