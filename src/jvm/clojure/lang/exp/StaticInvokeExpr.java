package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;

/**
* Created by jyu on 14-1-15.
*/
public class StaticInvokeExpr implements Expr, MaybePrimitiveExpr {
    public final Type              target;
    public final Class             retClass;
    public final Class[]           paramclasses;
    public final Type[]            paramtypes;
    public final IPersistentVector args;
    public final boolean           variadic;
    public final Symbol            tag;

    public StaticInvokeExpr(Type target, Class retClass, Class[] paramclasses, Type[] paramtypes, boolean variadic,
                            IPersistentVector args, Symbol tag) {
        this.target = target;
        this.retClass = retClass;
        this.paramclasses = paramclasses;
        this.paramtypes = paramtypes;
        this.args = args;
        this.variadic = variadic;
        this.tag = tag;
    }

    public Object eval() {
        throw new UnsupportedOperationException("Can't eval StaticInvokeExpr");
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        emitUnboxed(context, objx, gen);
        if (context != C.STATEMENT)
            HostExpr.emitBoxReturn(objx, gen, retClass);
        if (context == C.STATEMENT) {
            if (retClass == long.class || retClass == double.class)
                gen.pop2();
            else
                gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return tag != null ? HostExpr.tagToClass(tag) : retClass;
    }

    public boolean canEmitPrimitive() {
        return retClass.isPrimitive();
    }

    public void emitUnboxed(C context, ObjExpr objx, GeneratorAdapter gen) {
        Method ms = new Method("invokeStatic", getReturnType(), paramtypes);
        if (variadic) {
            for (int i = 0; i < paramclasses.length - 1; i++) {
                Expr e = (Expr) args.nth(i);
                if (Compiler.maybePrimitiveType(e) == paramclasses[i]) {
                    ((MaybePrimitiveExpr) e).emitUnboxed(C.EXPRESSION, objx, gen);
                } else {
                    e.emit(C.EXPRESSION, objx, gen);
                    HostExpr.emitUnboxArg(objx, gen, paramclasses[i]);
                }
            }
            IPersistentVector restArgs = RT.subvec(args, paramclasses.length - 1, args.count());
            MethodExpr.emitArgsAsArray(restArgs, objx, gen);
            gen.invokeStatic(Type.getType(ArraySeq.class), Method.getMethod(
                    "clojure.lang.ArraySeq create(Object[])"));
        } else
            MethodExpr.emitTypedArgs(objx, gen, paramclasses, args);

        gen.invokeStatic(target, ms);
    }

    private Type getReturnType() {
        return Type.getType(retClass);
    }

    public static Expr parse(Var v, ISeq args, Symbol tag) {
        IPersistentCollection paramlists = (IPersistentCollection) RT.get(v.meta(), Compiler.arglistsKey);
        if (paramlists == null)
            throw new IllegalStateException("Can't call static fn with no arglists: " + v);
        IPersistentVector paramlist = null;
        int argcount = RT.count(args);
        boolean variadic = false;
        for (ISeq aseq = RT.seq(paramlists); aseq != null; aseq = aseq.next()) {
            if (!(aseq.first() instanceof IPersistentVector))
                throw new IllegalStateException("Expected vector arglist, had: " + aseq.first());
            IPersistentVector alist = (IPersistentVector) aseq.first();
            if (alist.count() > 1
                    && alist.nth(alist.count() - 2).equals(Compiler._AMP_)) {
                if (argcount >= alist.count() - 2) {
                    paramlist = alist;
                    variadic = true;
                }
            } else if (alist.count() == argcount) {
                paramlist = alist;
                variadic = false;
                break;
            }
        }

        if (paramlist == null)
            throw new IllegalArgumentException("Invalid arity - can't call: " + v + " with " + argcount + " args");

        Class retClass = Compiler.tagClass(Compiler.tagOf(paramlist));

        ArrayList<Class> paramClasses = new ArrayList();
        ArrayList<Type> paramTypes = new ArrayList();

        if (variadic) {
            for (int i = 0; i < paramlist.count() - 2; i++) {
                Class pc = Compiler.tagClass(Compiler.tagOf(paramlist.nth(i)));
                paramClasses.add(pc);
                paramTypes.add(Type.getType(pc));
            }
            paramClasses.add(ISeq.class);
            paramTypes.add(Type.getType(ISeq.class));
        } else {
            for (int i = 0; i < argcount; i++) {
                Class pc = Compiler.tagClass(Compiler.tagOf(paramlist.nth(i)));
                paramClasses.add(pc);
                paramTypes.add(Type.getType(pc));
            }
        }

        String cname = v.ns.name.name.replace('.', '/').replace('-', '_') + "$" + Compiler.munge(v.sym.name);
        Type target = Type.getObjectType(cname);

        PersistentVector argv = PersistentVector.EMPTY;
        for (ISeq s = RT.seq(args); s != null; s = s.next())
            argv = argv.cons(Compiler.analyze(C.EXPRESSION, s.first()));

        return new StaticInvokeExpr(target, retClass, paramClasses.toArray(new Class[paramClasses.size()]),
                                    paramTypes.toArray(new Type[paramTypes.size()]), variadic, argv, tag);
    }
}
