package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
* Created by jyu on 14-1-15.
*/
public class RecurExpr implements Expr, MaybePrimitiveExpr {
    public final IPersistentVector args;
    public final IPersistentVector loopLocals;
    final        int               line;
    final        int               column;
    final        String            source;


    public RecurExpr(IPersistentVector loopLocals, IPersistentVector args, int line, int column, String source) {
        this.loopLocals = loopLocals;
        this.args = args;
        this.line = line;
        this.column = column;
        this.source = source;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval recur");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        Label loopLabel = (Label) Compiler.LOOP_LABEL.deref();
        if (loopLabel == null)
            throw new IllegalStateException();
        for (int i = 0; i < loopLocals.count(); i++) {
            LocalBinding lb = (LocalBinding) loopLocals.nth(i);
            Expr arg = (Expr) args.nth(i);
            if (lb.getPrimitiveType() != null) {
                Class primc = lb.getPrimitiveType();
                final Class pc = Compiler.maybePrimitiveType(arg);
                if (pc == primc)
                    ((MaybePrimitiveExpr) arg).emitUnboxed(C.EXPRESSION, objx, gen);
                else if (primc == long.class && pc == int.class) {
                    ((MaybePrimitiveExpr) arg).emitUnboxed(C.EXPRESSION, objx, gen);
                    gen.visitInsn(Opcodes.I2L);
                } else if (primc == double.class && pc == float.class) {
                    ((MaybePrimitiveExpr) arg).emitUnboxed(C.EXPRESSION, objx, gen);
                    gen.visitInsn(Opcodes.F2D);
                } else if (primc == int.class && pc == long.class) {
                    ((MaybePrimitiveExpr) arg).emitUnboxed(C.EXPRESSION, objx, gen);
                    gen.invokeStatic(Compiler.RT_TYPE, Method.getMethod("int intCast(long)"));
                } else if (primc == float.class && pc == double.class) {
                    ((MaybePrimitiveExpr) arg).emitUnboxed(C.EXPRESSION, objx, gen);
                    gen.visitInsn(Opcodes.D2F);
                } else {
//					if(true)//RT.booleanCast(RT.WARN_ON_REFLECTION.deref()))
                    throw new IllegalArgumentException
//						RT.errPrintWriter().println
                            (//source + ":" + line +
                             " recur arg for primitive local: " +
                                     lb.name + " is not matching primitive, had: " +
                                     (arg.hasJavaClass() ? arg.getJavaClass().getName() : "Object") +
                                     ", needed: " +
                                     primc.getName());
//					arg.emit(C.EXPRESSION, objx, gen);
//					HostExpr.emitUnboxArg(objx,gen,primc);
                }
            } else {
                arg.emit(C.EXPRESSION, objx, gen);
            }
        }

        for (int i = loopLocals.count() - 1; i >= 0; i--) {
            LocalBinding lb = (LocalBinding) loopLocals.nth(i);
            Class primc = lb.getPrimitiveType();
            if (lb.isArg)
                gen.storeArg(lb.idx - (objx.isStatic ? 0 : 1));
            else {
                if (primc != null)
                    gen.visitVarInsn(Type.getType(primc).getOpcode(Opcodes.ISTORE), lb.idx);
                else
                    gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), lb.idx);
            }
        }

        gen.goTo(loopLabel);
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return Compiler.RECUR_CLASS;
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object frm) {
            int line = Compiler.lineDeref();
            int column = Compiler.columnDeref();
            String source = (String) Compiler.SOURCE.deref();

            ISeq form = (ISeq) frm;
            IPersistentVector loopLocals = (IPersistentVector) Compiler.LOOP_LOCALS.deref();
            if (context != C.RETURN || loopLocals == null)
                throw new UnsupportedOperationException("Can only recur from tail position");
            if (Compiler.NO_RECUR.deref() != null)
                throw new UnsupportedOperationException("Cannot recur across try");
            PersistentVector args = PersistentVector.EMPTY;
            for (ISeq s = RT.seq(form.next()); s != null; s = s.next()) {
                args = args.cons(Compiler.analyze(C.EXPRESSION, s.first()));
            }
            if (args.count() != loopLocals.count())
                throw new IllegalArgumentException(
                        String.format("Mismatched argument count to recur, expected: %d args, got: %d",
                                      loopLocals.count(), args.count()));
            for (int i = 0; i < loopLocals.count(); i++) {
                LocalBinding lb = (LocalBinding) loopLocals.nth(i);
                Class primc = lb.getPrimitiveType();
                if (primc != null) {
                    boolean mismatch = false;
                    final Class pc = Compiler.maybePrimitiveType((Expr) args.nth(i));
                    if (primc == long.class) {
                        if (!(pc == long.class
                                || pc == int.class
                                || pc == short.class
                                || pc == char.class
                                || pc == byte.class))
                            mismatch = true;
                    } else if (primc == double.class) {
                        if (!(pc == double.class
                                || pc == float.class))
                            mismatch = true;
                    }
                    if (mismatch) {
                        lb.recurMistmatch = true;
                        if (RT.booleanCast(RT.WARN_ON_REFLECTION.deref()))
                            RT.errPrintWriter().println
                                    (source + ":" + line +
                                             " recur arg for primitive local: " +
                                             lb.name + " is not matching primitive, had: " +
                                             (pc != null ? pc.getName() : "Object") +
                                             ", needed: " +
                                             primc.getName());
                    }
                }
            }
            return new RecurExpr(loopLocals, args, line, column, source);
        }
    }

    public boolean canEmitPrimitive() {
        return true;
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        emit(context, objx, gen);
    }
}
