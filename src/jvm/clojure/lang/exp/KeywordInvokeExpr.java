package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.CompilerException;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class KeywordInvokeExpr implements Expr {
    public final KeywordExpr kw;
    public final Object      tag;
    public final Expr        target;
    public final int         line;
    public final int         column;
    public final int         siteIndex;
    public final String      source;
    static Type ILOOKUP_TYPE = Type.getType(ILookup.class);

    public KeywordInvokeExpr(String source, int line, int column, Symbol tag, KeywordExpr kw, Expr target) {
        this.source = source;
        this.kw = kw;
        this.target = target;
        this.line = line;
        this.column = column;
        this.tag = tag;
        this.siteIndex = Compiler.registerKeywordCallsite(kw.k);
    }

    public Object eval() {
        try {
            return kw.k.invoke(target.eval());
        } catch (Throwable e) {
            if (!(e instanceof CompilerException))
                throw new CompilerException(source, line, column, e);
            else
                throw (CompilerException) e;
        }
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        Label endLabel = gen.newLabel();
        Label faultLabel = gen.newLabel();

        gen.visitLineNumber(line, gen.mark());
        gen.getStatic(objx.objtype, objx.thunkNameStatic(siteIndex), ObjExpr.ILOOKUP_THUNK_TYPE);
        gen.dup();  //thunk, thunk
        target.emit(C.EXPRESSION, objx, gen); //thunk,thunk,target
        gen.dupX2();                          //target,thunk,thunk,target
        gen.invokeInterface(ObjExpr.ILOOKUP_THUNK_TYPE, Method.getMethod(
                "Object get(Object)")); //target,thunk,result
        gen.dupX2();                          //result,target,thunk,result
        gen.visitJumpInsn(Opcodes.IF_ACMPEQ, faultLabel); //result,target
        gen.pop();                                //result
        gen.goTo(endLabel);

        gen.mark(faultLabel);    //result,target
        gen.swap();              //target,result
        gen.pop();               //target
        gen.dup();               //target,target
        gen.getStatic(objx.objtype, objx.siteNameStatic(siteIndex),
                      ObjExpr.KEYWORD_LOOKUPSITE_TYPE);  //target,target,site
        gen.swap();              //target,site,target
        gen.invokeInterface(ObjExpr.ILOOKUP_SITE_TYPE,
                            Method.getMethod("clojure.lang.ILookupThunk fault(Object)"));    //target,new-thunk
        gen.dup();   //target,new-thunk,new-thunk
        gen.putStatic(objx.objtype, objx.thunkNameStatic(siteIndex),
                      ObjExpr.ILOOKUP_THUNK_TYPE);  //target,new-thunk
        gen.swap();              //new-thunk,target
        gen.invokeInterface(ObjExpr.ILOOKUP_THUNK_TYPE, Method.getMethod("Object get(Object)")); //result

        gen.mark(endLabel);
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return tag != null;
    }

    public Class getJavaClass() {
        return HostExpr.tagToClass(tag);
    }

}
