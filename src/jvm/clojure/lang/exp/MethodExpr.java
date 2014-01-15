package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public abstract class MethodExpr extends HostExpr {
    public static void emitArgsAsArray(IPersistentVector args, ObjExpr objx, GeneratorAdapter gen) {
        gen.push(args.count());
        gen.newArray(Compiler.OBJECT_TYPE);
        for (int i = 0; i < args.count(); i++) {
            gen.dup();
            gen.push(i);
            ((Expr) args.nth(i)).emit(C.EXPRESSION, objx, gen);
            gen.arrayStore(Compiler.OBJECT_TYPE);
        }
    }

    public static void emitTypedArgs(ObjExpr objx,
                                     GeneratorAdapter gen,
                                     Class[] parameterTypes,
                                     IPersistentVector args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Expr e = (Expr) args.nth(i);
            try {
                final Class primc = Compiler.maybePrimitiveType(e);
                if (primc == parameterTypes[i]) {
                    final MaybePrimitiveExpr pe = (MaybePrimitiveExpr) e;
                    pe.emitUnboxed(C.EXPRESSION, objx, gen);
                } else if (primc == int.class && parameterTypes[i] == long.class) {
                    final MaybePrimitiveExpr pe = (MaybePrimitiveExpr) e;
                    pe.emitUnboxed(C.EXPRESSION, objx, gen);
                    gen.visitInsn(Opcodes.I2L);
                } else if (primc == long.class && parameterTypes[i] == int.class) {
                    final MaybePrimitiveExpr pe = (MaybePrimitiveExpr) e;
                    pe.emitUnboxed(C.EXPRESSION, objx, gen);
                    if (RT.booleanCast(RT.UNCHECKED_MATH.deref()))
                        gen.invokeStatic(Compiler.RT_TYPE, Method.getMethod("int uncheckedIntCast(long)"));
                    else
                        gen.invokeStatic(Compiler.RT_TYPE, Method.getMethod("int intCast(long)"));
                } else if (primc == float.class && parameterTypes[i] == double.class) {
                    final MaybePrimitiveExpr pe = (MaybePrimitiveExpr) e;
                    pe.emitUnboxed(C.EXPRESSION, objx, gen);
                    gen.visitInsn(Opcodes.F2D);
                } else if (primc == double.class && parameterTypes[i] == float.class) {
                    final MaybePrimitiveExpr pe = (MaybePrimitiveExpr) e;
                    pe.emitUnboxed(C.EXPRESSION, objx, gen);
                    gen.visitInsn(Opcodes.D2F);
                } else {
                    e.emit(C.EXPRESSION, objx, gen);
                    HostExpr.emitUnboxArg(objx, gen, parameterTypes[i]);
                }
            } catch (Exception e1) {
                e1.printStackTrace(RT.errPrintWriter());
            }

        }
    }
}
