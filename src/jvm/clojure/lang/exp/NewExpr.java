package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

/**
* Created by jyu on 14-1-15.
*/
public class NewExpr implements Expr {
    public final IPersistentVector args;
    public final Constructor       ctor;
    public final Class             c;
    final static Method invokeConstructorMethod =
            Method.getMethod("Object invokeConstructor(Class,Object[])");
    //	final static Method forNameMethod = Method.getMethod("Class classForName(String)");
    final static Method forNameMethod           = Method.getMethod("Class forName(String)");


    public NewExpr(Class c, IPersistentVector args, int line, int column) {
        this.args = args;
        this.c = c;
        Constructor[] allctors = c.getConstructors();
        ArrayList ctors = new ArrayList();
        ArrayList<Class[]> params = new ArrayList();
        ArrayList<Class> rets = new ArrayList();
        for (int i = 0; i < allctors.length; i++) {
            Constructor ctor = allctors[i];
            if (ctor.getParameterTypes().length == args.count()) {
                ctors.add(ctor);
                params.add(ctor.getParameterTypes());
                rets.add(c);
            }
        }
        if (ctors.isEmpty())
            throw new IllegalArgumentException("No matching ctor found for " + c);

        int ctoridx = 0;
        if (ctors.size() > 1) {
            ctoridx = Compiler.getMatchingParams(c.getName(), params, args, rets);
        }

        this.ctor = ctoridx >= 0 ? (Constructor) ctors.get(ctoridx) : null;
        if (ctor == null && RT.booleanCast(RT.WARN_ON_REFLECTION.deref())) {
            RT.errPrintWriter()
                    .format("Reflection warning, %s:%d:%d - call to %s ctor can't be resolved.\n",
                            Compiler.SOURCE_PATH.deref(), line, column, c.getName());
        }
    }

    public Object eval() {
        Object[] argvals = new Object[args.count()];
        for (int i = 0; i < args.count(); i++)
            argvals[i] = ((Expr) args.nth(i)).eval();
        if (this.ctor != null) {
            try {
                return ctor.newInstance(Reflector.boxArgs(ctor.getParameterTypes(), argvals));
            } catch (Exception e) {
                throw Util.sneakyThrow(e);
            }
        }
        return Reflector.invokeConstructor(c, argvals);
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (this.ctor != null) {
            Type type = Compiler.getType(c);
            gen.newInstance(type);
            gen.dup();
            MethodExpr.emitTypedArgs(objx, gen, ctor.getParameterTypes(), args);
            if (context == C.RETURN) {
                ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
                method.emitClearLocals(gen);
            }
            gen.invokeConstructor(type, new Method("<init>", Type.getConstructorDescriptor(ctor)));
        } else {
            gen.push(Compiler.destubClassName(c.getName()));
            gen.invokeStatic(Compiler.CLASS_TYPE, forNameMethod);
            MethodExpr.emitArgsAsArray(args, objx, gen);
            if (context == C.RETURN) {
                ObjMethod method = (ObjMethod) Compiler.METHOD.deref();
                method.emitClearLocals(gen);
            }
            gen.invokeStatic(Compiler.REFLECTOR_TYPE, invokeConstructorMethod);
        }
        if (context == C.STATEMENT)
            gen.pop();
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        return c;
    }

    public static class Parser implements IParser {
        public Expr parse(C context, Object frm) {
            int line = Compiler.lineDeref();
            int column = Compiler.columnDeref();
            ISeq form = (ISeq) frm;
            //(new Classname args...)
            if (form.count() < 2)
                throw Util.runtimeException("wrong number of arguments, expecting: (new Classname args...)");
            Class c = HostExpr.maybeClass(RT.second(form), false);
            if (c == null)
                throw new IllegalArgumentException("Unable to resolve classname: " + RT.second(form));
            PersistentVector args = PersistentVector.EMPTY;
            for (ISeq s = RT.next(RT.next(form)); s != null; s = s.next())
                args = args.cons(Compiler.analyze(context == C.EVAL ? context : C.EXPRESSION, s.first()));
            return new NewExpr(c, args, line, column);
        }
    }

}
