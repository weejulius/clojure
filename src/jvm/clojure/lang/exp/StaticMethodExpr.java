package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.CompilerException;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
* Created by jyu on 14-1-15.
*/
public class StaticMethodExpr extends MethodExpr {
    //final String className;
    public final Class                    c;
    public final String                   methodName;
    public final IPersistentVector        args;
    public final String                   source;
    public final int                      line;
    public final int                      column;
    public final java.lang.reflect.Method method;
    public final Symbol                   tag;
    final static Method forNameMethod            = Method.getMethod("Class forName(String)");
    final static Method invokeStaticMethodMethod =
            Method.getMethod("Object invokeStaticMethod(Class,String,Object[])");


    public StaticMethodExpr(String source,
                            int line,
                            int column,
                            Symbol tag,
                            Class c,
                            String methodName,
                            IPersistentVector args) {
        this.c = c;
        this.methodName = methodName;
        this.args = args;
        this.source = source;
        this.line = line;
        this.column = column;
        this.tag = tag;

        List methods = Reflector.getMethods(c, args.count(), methodName, true);
        if (methods.isEmpty())
            throw new IllegalArgumentException("No matching method: " + methodName);

        int methodidx = 0;
        if (methods.size() > 1) {
            ArrayList<Class[]> params = new ArrayList();
            ArrayList<Class> rets = new ArrayList();
            for (int i = 0; i < methods.size(); i++) {
                java.lang.reflect.Method m = (java.lang.reflect.Method) methods.get(i);
                params.add(m.getParameterTypes());
                rets.add(m.getReturnType());
            }
            methodidx = Compiler.getMatchingParams(methodName, params, args, rets);
        }
        method = (java.lang.reflect.Method) (methodidx >= 0 ? methods.get(methodidx) : null);
        if (method == null && RT.booleanCast(RT.WARN_ON_REFLECTION.deref())) {
            RT.errPrintWriter()
                    .format("Reflection warning, %s:%d:%d - call to %s can't be resolved.\n",
                            Compiler.SOURCE_PATH.deref(), line, column, methodName);
        }
    }

    public Object eval() {
        try {
            Object[] argvals = new Object[args.count()];
            for (int i = 0; i < args.count(); i++)
                argvals[i] = ((Expr) args.nth(i)).eval();
            if (method != null) {
                LinkedList ms = new LinkedList();
                ms.add(method);
                return Reflector.invokeMatchingMethod(methodName, ms, null, argvals);
            }
            return Reflector.invokeStaticMethod(c, methodName, argvals);
        } catch (Throwable e) {
            if (!(e instanceof CompilerException))
                throw new CompilerException(source, line, column, e);
            else
                throw (CompilerException) e;
        }
    }

    public boolean canEmitPrimitive() {
        return method != null && Util.isPrimitive(method.getReturnType());
    }

    public boolean canEmitIntrinsicPredicate() {
        return method != null && RT.get(Intrinsics.preds, method.toString()) != null;
    }

    public void emitIntrinsicPredicate(C context, ObjExpr objx, GeneratorAdapter gen, Label falseLabel) {
        gen.visitLineNumber(line, gen.mark());
        if (method != null) {
            MethodExpr.emitTypedArgs(objx, gen, method.getParameterTypes(), args);
            if (context == C.RETURN) {
                ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
                method.emitClearLocals(gen);
            }
            Object[] predOps = (Object[]) RT.get(Intrinsics.preds, method.toString());
            for (int i = 0; i < predOps.length - 1; i++)
                gen.visitInsn((Integer) predOps[i]);
            gen.visitJumpInsn((Integer) predOps[predOps.length - 1], falseLabel);
        } else
            throw new UnsupportedOperationException("Unboxed emit of unknown member");
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitLineNumber(line, gen.mark());
        if (method != null) {
            MethodExpr.emitTypedArgs(objx, gen, method.getParameterTypes(), args);
            //Type type = Type.getObjectType(className.replace('.', '/'));
            if (context == C.RETURN) {
                ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
                method.emitClearLocals(gen);
            }
            Object ops = RT.get(Intrinsics.ops, method.toString());
            if (ops != null) {
                if (ops instanceof Object[]) {
                    for (Object op : (Object[]) ops)
                        gen.visitInsn((Integer) op);
                } else
                    gen.visitInsn((Integer) ops);
            } else {
                Type type = Type.getType(c);
                Method m = new Method(methodName, Type.getReturnType(method), Type.getArgumentTypes(method));
                gen.invokeStatic(type, m);
            }
        } else
            throw new UnsupportedOperationException("Unboxed emit of unknown member");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        gen.visitLineNumber(line, gen.mark());
        if (method != null) {
            MethodExpr.emitTypedArgs(objx, gen, method.getParameterTypes(), args);
            //Type type = Type.getObjectType(className.replace('.', '/'));
            if (context == C.RETURN) {
                ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
                method.emitClearLocals(gen);
            }
            Type type = Type.getType(c);
            Method m = new Method(methodName, Type.getReturnType(method), Type.getArgumentTypes(method));
            gen.invokeStatic(type, m);
            //if(context != C.STATEMENT || method.getReturnType() == Void.TYPE)
            Class retClass = method.getReturnType();
            if (context == C.STATEMENT) {
                if (retClass == long.class || retClass == double.class)
                    gen.pop2();
                else if (retClass != void.class)
                    gen.pop();
            } else {
                HostExpr.emitBoxReturn(objx, gen, method.getReturnType());
            }
        } else {
            gen.push(c.getName());
            gen.invokeStatic(Compiler.CLASS_TYPE, forNameMethod);
            gen.push(methodName);
            emitArgsAsArray(args, objx, gen);
            if (context == C.RETURN) {
                ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
                method.emitClearLocals(gen);
            }
            gen.invokeStatic(Compiler.REFLECTOR_TYPE, invokeStaticMethodMethod);
            if (context == C.STATEMENT)
                gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return method != null || tag != null;
    }

    public Class getJavaClass() {
        return tag != null ? HostExpr.tagToClass(tag) : method.getReturnType();
    }
}
