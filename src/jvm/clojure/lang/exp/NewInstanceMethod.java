package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.C;
import clojure.lang.Compiler.PATHTYPE;
import clojure.lang.Compiler.PathNode;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.HashMap;
import java.util.Map;

/**
* Created by jyu on 14-1-15.
*/
public class NewInstanceMethod extends ObjMethod {
    String  name;
    Type[]  argTypes;
    Type    retType;
    Class   retClass;
    Class[] exclasses;

    static Symbol dummyThis = Symbol.intern(null, "dummy_this_dlskjsdfower");
    private IPersistentVector parms;

    public NewInstanceMethod(ObjExpr objx, ObjMethod parent) {
        super(objx, parent);
    }

    int numParams() {
        return argLocals.count();
    }

    String getMethodName() {
        return name;
    }

    Type getReturnType() {
        return retType;
    }

    Type[] getArgTypes() {
        return argTypes;
    }


    static public IPersistentVector msig(String name, Class[] paramTypes) {
        return RT.vector(name, RT.seq(paramTypes));
    }

    static NewInstanceMethod parse(ObjExpr objx, ISeq form, Symbol thistag,
                                   Map overrideables) {
        //(methodname [this-name args*] body...)
        //this-name might be nil
        NewInstanceMethod method = new NewInstanceMethod(objx, (ObjMethod) Compiler.METHOD.deref());
        Symbol dotname = (Symbol) RT.first(form);
        Symbol name = (Symbol) Symbol.intern(null, Compiler.munge(dotname.name)).withMeta(RT.meta(dotname));
        IPersistentVector parms = (IPersistentVector) RT.second(form);
        if (parms.count() == 0) {
            throw new IllegalArgumentException("Must supply at least one argument for 'this' in: " + dotname);
        }
        Symbol thisName = (Symbol) parms.nth(0);
        parms = RT.subvec(parms, 1, parms.count());
        ISeq body = RT.next(RT.next(form));
        try {
            method.line = Compiler.lineDeref();
            method.column = Compiler.columnDeref();
            //register as the current method and set up a new env frame
            PathNode pnode = new PathNode(PATHTYPE.PATH, (PathNode) Compiler.CLEAR_PATH.get());
            Var.pushThreadBindings(
                    RT.mapUniqueKeys(
                            Compiler.METHOD, method,
                            Compiler.LOCAL_ENV, Compiler.LOCAL_ENV.deref(),
                            Compiler.LOOP_LOCALS, null,
                            Compiler.NEXT_LOCAL_NUM, 0
                            , Compiler.CLEAR_PATH, pnode
                            , Compiler.CLEAR_ROOT, pnode
                            , Compiler.CLEAR_SITES, PersistentHashMap.EMPTY
                    ));

            //register 'this' as local 0
            if (thisName != null)
                Compiler.registerLocal((thisName == null) ? dummyThis : thisName, thistag, null, false);
            else
                Compiler.getAndIncLocalNum();

            PersistentVector argLocals = PersistentVector.EMPTY;
            method.retClass = Compiler.tagClass(Compiler.tagOf(name));
            method.argTypes = new Type[parms.count()];
            boolean hinted = Compiler.tagOf(name) != null;
            Class[] pclasses = new Class[parms.count()];
            Symbol[] psyms = new Symbol[parms.count()];

            for (int i = 0; i < parms.count(); i++) {
                if (!(parms.nth(i) instanceof Symbol))
                    throw new IllegalArgumentException("params must be Symbols");
                Symbol p = (Symbol) parms.nth(i);
                Object tag = Compiler.tagOf(p);
                if (tag != null)
                    hinted = true;
                if (p.getNamespace() != null)
                    p = Symbol.intern(p.name);
                Class pclass = Compiler.tagClass(tag);
                pclasses[i] = pclass;
                psyms[i] = p;
            }
            Map matches = findMethodsWithNameAndArity(name.name, parms.count(), overrideables);
            Object mk = msig(name.name, pclasses);
            java.lang.reflect.Method m = null;
            if (matches.size() > 0) {
                //multiple methods
                if (matches.size() > 1) {
                    //must be hinted and match one method
                    if (!hinted)
                        throw new IllegalArgumentException("Must hint overloaded method: " + name.name);
                    m = (java.lang.reflect.Method) matches.get(mk);
                    if (m == null)
                        throw new IllegalArgumentException("Can't find matching overloaded method: " + name.name);
                    if (m.getReturnType() != method.retClass)
                        throw new IllegalArgumentException("Mismatched return type: " + name.name +
                                                                   ", expected: " + m.getReturnType().getName() +
                                                                   ", had: " + method.retClass.getName());
                } else  //one match
                {
                    //if hinted, validate match,
                    if (hinted) {
                        m = (java.lang.reflect.Method) matches.get(mk);
                        if (m == null)
                            throw new IllegalArgumentException("Can't find matching method: " + name.name +
                                                                       ", leave off hints for auto match.");
                        if (m.getReturnType() != method.retClass)
                            throw new IllegalArgumentException("Mismatched return type: " + name.name +
                                                                       ", expected: " +
                                                                       m.getReturnType().getName() + ", had: " +
                                                                       method.retClass.getName());
                    } else //adopt found method sig
                    {
                        m = (java.lang.reflect.Method) matches.values().iterator().next();
                        method.retClass = m.getReturnType();
                        pclasses = m.getParameterTypes();
                    }
                }
            }
//			else if(findMethodsWithName(name.name,allmethods).size()>0)
//				throw new IllegalArgumentException("Can't override/overload method: " + name.name);
            else
                throw new IllegalArgumentException("Can't define method not in interfaces: " + name.name);

            //else
            //validate unque name+arity among additional methods

            method.retType = Type.getType(method.retClass);
            method.exclasses = m.getExceptionTypes();

            for (int i = 0; i < parms.count(); i++) {
                LocalBinding lb = Compiler.registerLocal(psyms[i], null, new MethodParamExpr(pclasses[i]), true);
                argLocals = argLocals.assocN(i, lb);
                method.argTypes[i] = Type.getType(pclasses[i]);
            }
            for (int i = 0; i < parms.count(); i++) {
                if (pclasses[i] == long.class || pclasses[i] == double.class)
                    Compiler.getAndIncLocalNum();
            }
            Compiler.LOOP_LOCALS.set(argLocals);
            method.name = name.name;
            method.methodMeta = RT.meta(name);
            method.parms = parms;
            method.argLocals = argLocals;
            method.body = (new BodyExpr.Parser()).parse(C.RETURN, body);
            return method;
        } finally {
            Var.popThreadBindings();
        }
    }

    private static Map findMethodsWithNameAndArity(String name, int arity, Map mm) {
        Map ret = new HashMap();
        for (Object o : mm.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            java.lang.reflect.Method m = (java.lang.reflect.Method) e.getValue();
            if (name.equals(m.getName()) && m.getParameterTypes().length == arity)
                ret.put(e.getKey(), e.getValue());
        }
        return ret;
    }

    private static Map findMethodsWithName(String name, Map mm) {
        Map ret = new HashMap();
        for (Object o : mm.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            java.lang.reflect.Method m = (java.lang.reflect.Method) e.getValue();
            if (name.equals(m.getName()))
                ret.put(e.getKey(), e.getValue());
        }
        return ret;
    }

    public void emit(ObjExpr obj, ClassVisitor cv) {
        Method m = new Method(getMethodName(), getReturnType(), getArgTypes());

        Type[] extypes = null;
        if (exclasses.length > 0) {
            extypes = new Type[exclasses.length];
            for (int i = 0; i < exclasses.length; i++)
                extypes[i] = Type.getType(exclasses[i]);
        }
        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                                                    m,
                                                    null,
                                                    extypes,
                                                    cv);
        Compiler.addAnnotation(gen, methodMeta);
        for (int i = 0; i < parms.count(); i++) {
            IPersistentMap meta = RT.meta(parms.nth(i));
            Compiler.addParameterAnnotation(gen, meta, i);
        }
        gen.visitCode();

        Label loopLabel = gen.mark();

        gen.visitLineNumber(line, loopLabel);
        try {
            Var.pushThreadBindings(RT.map(Compiler.LOOP_LABEL, loopLabel, Compiler.METHOD, this));

            emitBody(objx, gen, retClass, body);
            Label end = gen.mark();
            gen.visitLocalVariable("this", obj.objtype.getDescriptor(), null, loopLabel, end, 0);
            for (ISeq lbs = argLocals.seq(); lbs != null; lbs = lbs.next()) {
                LocalBinding lb = (LocalBinding) lbs.first();
                gen.visitLocalVariable(lb.name, argTypes[lb.idx - 1].getDescriptor(), null, loopLabel, end, lb.idx);
            }
        } finally {
            Var.popThreadBindings();
        }

        gen.returnValue();
        //gen.visitMaxs(1, 1);
        gen.endMethod();
    }
}
