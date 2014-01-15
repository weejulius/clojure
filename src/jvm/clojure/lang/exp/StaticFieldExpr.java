package clojure.lang.exp;

import clojure.lang.Compiler.C;
import clojure.lang.Expr;
import clojure.lang.Reflector;
import clojure.lang.Symbol;
import clojure.lang.Util;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class StaticFieldExpr extends FieldExpr implements AssignableExpr {
    //final String className;
    public final String                  fieldName;
    public final Class                   c;
    public final java.lang.reflect.Field field;
    public final Symbol tag;
    //	final static Method getStaticFieldMethod = Method.getMethod("Object getStaticField(String,String)");
//	final static Method setStaticFieldMethod = Method.getMethod("Object setStaticField(String,String,Object)");
    final        int    line;
    final        int    column;

    public StaticFieldExpr(int line, int column, Class c, String fieldName, Symbol tag) {
        //this.className = className;
        this.fieldName = fieldName;
        this.line = line;
        this.column = column;
        //c = Class.forName(className);
        this.c = c;
        try {
            field = c.getField(fieldName);
        } catch (NoSuchFieldException e) {
            throw Util.sneakyThrow(e);
        }
        this.tag = tag;
    }

    public Object eval() {
        return Reflector.getStaticField(c, fieldName);
    }

    public boolean canEmitPrimitive() {
        return Util.isPrimitive(field.getType());
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitLineNumber(line, gen.mark());
        gen.getStatic(Type.getType(c), fieldName, Type.getType(field.getType()));
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitLineNumber(line, gen.mark());

        gen.getStatic(Type.getType(c), fieldName, Type.getType(field.getType()));
        //if(context != C.STATEMENT)
        HostExpr.emitBoxReturn(objx, gen, field.getType());
        if (context == C.STATEMENT) {
            gen.pop();
        }
//		gen.push(className);
//		gen.push(fieldName);
//		gen.invokeStatic(REFLECTOR_TYPE, getStaticFieldMethod);
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        //Class c = Class.forName(className);
        //java.lang.reflect.Field field = c.getField(fieldName);
        return tag != null ? HostExpr.tagToClass(tag) : field.getType();
    }

    public Object evalAssign(Expr val) {
        return Reflector.setStaticField(c, fieldName, val.eval());
    }

    public void emitAssign(C context, ObjExpr objx, GeneratorAdapter gen,
                           Expr val) {
        gen.visitLineNumber(line, gen.mark());
        val.emit(C.EXPRESSION, objx, gen);
        gen.dup();
        HostExpr.emitUnboxArg(objx, gen, field.getType());
        gen.putStatic(Type.getType(c), fieldName, Type.getType(field.getType()));
        if (context == C.STATEMENT)
            gen.pop();
    }


}
