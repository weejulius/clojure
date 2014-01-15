package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class LetFnExpr implements Expr {
    public final PersistentVector bindingInits;
    public final Expr             body;

    public LetFnExpr(PersistentVector bindingInits, Expr body) {
        this.bindingInits = bindingInits;
        this.body = body;
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object frm) {
            ISeq form = (ISeq) frm;
            //(letfns* [var (fn [args] body) ...] body...)
            if (!(RT.second(form) instanceof IPersistentVector))
                throw new IllegalArgumentException("Bad binding form, expected vector");

            IPersistentVector bindings = (IPersistentVector) RT.second(form);
            if ((bindings.count() % 2) != 0)
                throw new IllegalArgumentException("Bad binding form, expected matched symbol expression pairs");

            ISeq body = RT.next(RT.next(form));

            if (context == C.EVAL)
                return Compiler.analyze(context, RT.list(RT.list(Compiler.FNONCE, PersistentVector.EMPTY, form)));

            IPersistentMap dynamicBindings = RT.map(Compiler.LOCAL_ENV, Compiler.LOCAL_ENV.deref(),
                                                    Compiler.NEXT_LOCAL_NUM, Compiler.NEXT_LOCAL_NUM.deref());

            try {
                Var.pushThreadBindings(dynamicBindings);

                //pre-seed env (like Lisp labels)
                PersistentVector lbs = PersistentVector.EMPTY;
                for (int i = 0; i < bindings.count(); i += 2) {
                    if (!(bindings.nth(i) instanceof Symbol))
                        throw new IllegalArgumentException(
                                "Bad binding form, expected symbol, got: " + bindings.nth(i));
                    Symbol sym = (Symbol) bindings.nth(i);
                    if (sym.getNamespace() != null)
                        throw Util.runtimeException("Can't let qualified name: " + sym);
                    LocalBinding lb = Compiler.registerLocal(sym, Compiler.tagOf(sym), null, false);
                    lb.canBeCleared = false;
                    lbs = lbs.cons(lb);
                }
                PersistentVector bindingInits = PersistentVector.EMPTY;
                for (int i = 0; i < bindings.count(); i += 2) {
                    Symbol sym = (Symbol) bindings.nth(i);
                    Expr init = Compiler.analyze(C.EXPRESSION, bindings.nth(i + 1), sym.name);
                    LocalBinding lb = (LocalBinding) lbs.nth(i / 2);
                    lb.init = init;
                    BindingInit bi = new BindingInit(lb, init);
                    bindingInits = bindingInits.cons(bi);
                }
                return new LetFnExpr(bindingInits, (new BodyExpr.Parser()).parse(context, body));
            } finally {
                Var.popThreadBindings();
            }
        }
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval letfns");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        for (int i = 0; i < bindingInits.count(); i++) {
            BindingInit bi = (BindingInit) bindingInits.nth(i);
            gen.visitInsn(Opcodes.ACONST_NULL);
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), bi.binding.idx);
        }

        IPersistentSet lbset = PersistentHashSet.EMPTY;

        for (int i = 0; i < bindingInits.count(); i++) {
            BindingInit bi = (BindingInit) bindingInits.nth(i);
            lbset = (IPersistentSet) lbset.cons(bi.binding);
            bi.init.emit(C.EXPRESSION, objx, gen);
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ISTORE), bi.binding.idx);
        }

        for (int i = 0; i < bindingInits.count(); i++) {
            BindingInit bi = (BindingInit) bindingInits.nth(i);
            ObjExpr fe = (ObjExpr) bi.init;
            gen.visitVarInsn(Compiler.OBJECT_TYPE.getOpcode(Opcodes.ILOAD), bi.binding.idx);
            fe.emitLetFnInits(gen, objx, lbset);
        }

        Label loopLabel = gen.mark();

        body.emit(context, objx, gen);

        Label end = gen.mark();
//		gen.visitLocalVariable("this", "Ljava/lang/Object;", null, loopLabel, end, 0);
        for (ISeq bis = bindingInits.seq(); bis != null; bis = bis.next()) {
            BindingInit bi = (BindingInit) bis.first();
            String lname = bi.binding.name;
            if (lname.endsWith("__auto__"))
                lname += RT.nextID();
            Class primc = Compiler.maybePrimitiveType(bi.init);
            if (primc != null)
                gen.visitLocalVariable(lname, Type.getDescriptor(primc), null, loopLabel, end,
                                       bi.binding.idx);
            else
                gen.visitLocalVariable(lname, "Ljava/lang/Object;", null, loopLabel, end, bi.binding.idx);
        }
    }

    public boolean hasJavaClass() {
        return body.hasJavaClass();
    }

    public Class getJavaClass() {
        return body.getJavaClass();
    }
}
