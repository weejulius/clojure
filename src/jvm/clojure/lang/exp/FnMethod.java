package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler;
import clojure.lang.Compiler.*;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;

/**
* Created by jyu on 14-1-15.
*/
public class FnMethod extends ObjMethod {
    //localbinding->localbinding
    PersistentVector reqParms = PersistentVector.EMPTY;
    LocalBinding     restParm = null;
    Type[]  argtypes;
    Class[] argclasses;
    Class   retClass;
    String  prim;

    public FnMethod(ObjExpr objx, ObjMethod parent) {
        super(objx, parent);
    }

    static public char classChar(Object x) {
        Class c = null;
        if (x instanceof Class)
            c = (Class) x;
        else if (x instanceof Symbol)
            c = Compiler.primClass((Symbol) x);
        if (c == null || !c.isPrimitive())
            return 'O';
        if (c == long.class)
            return 'L';
        if (c == double.class)
            return 'D';
        throw new IllegalArgumentException("Only long and double primitives are supported");
    }

    static public String primInterface(IPersistentVector arglist) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arglist.count(); i++)
            sb.append(classChar(Compiler.tagOf(arglist.nth(i))));
        sb.append(classChar(Compiler.tagOf(arglist)));
        String ret = sb.toString();
        boolean prim = ret.contains("L") || ret.contains("D");
        if (prim && arglist.count() > 4)
            throw new IllegalArgumentException("fns taking primitives support only 4 or fewer args");
        if (prim)
            return "clojure.lang.IFn$" + ret;
        return null;
    }

    static FnMethod parse(ObjExpr objx, ISeq form, boolean isStatic) {
        //([args] body...)
        IPersistentVector parms = (IPersistentVector) RT.first(form);
        ISeq body = RT.next(form);
        try {
            FnMethod method = new FnMethod(objx, (ObjMethod) Compiler.METHOD.deref());
            method.line = Compiler.lineDeref();
            method.column = Compiler.columnDeref();
            //register as the current method and set up a new env frame
            PathNode pnode = (PathNode) Compiler.CLEAR_PATH.get();
            if (pnode == null)
                pnode = new PathNode(PATHTYPE.PATH, null);
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

            method.prim = primInterface(parms);
            if (method.prim != null)
                method.prim = method.prim.replace('.', '/');

            method.retClass = Compiler.tagClass(Compiler.tagOf(parms));
            if (method.retClass.isPrimitive() &&
                    !(method.retClass == double.class || method.retClass == long.class))
                throw new IllegalArgumentException("Only long and double primitives are supported");

            //register 'this' as local 0
            //registerLocal(THISFN, null, null);
            if (!isStatic) {
                if (objx.thisName != null)
                    Compiler.registerLocal(Symbol.intern(objx.thisName), null, null, false);
                else
                    Compiler.getAndIncLocalNum();
            }
            PSTATE state = PSTATE.REQ;
            PersistentVector argLocals = PersistentVector.EMPTY;
            ArrayList<Type> argtypes = new ArrayList();
            ArrayList<Class> argclasses = new ArrayList();
            for (int i = 0; i < parms.count(); i++) {
                if (!(parms.nth(i) instanceof Symbol))
                    throw new IllegalArgumentException("fn params must be Symbols");
                Symbol p = (Symbol) parms.nth(i);
                if (p.getNamespace() != null)
                    throw Util.runtimeException("Can't use qualified name as parameter: " + p);
                if (p.equals(Compiler._AMP_)) {
//					if(isStatic)
//						throw Util.runtimeException("Variadic fns cannot be static");
                    if (state == PSTATE.REQ)
                        state = PSTATE.REST;
                    else
                        throw Util.runtimeException("Invalid parameter list");
                } else {
                    Class pc = Compiler.primClass(Compiler.tagClass(Compiler.tagOf(p)));
//					if(pc.isPrimitive() && !isStatic)
//						{
//						pc = Object.class;
//						p = (Symbol) ((IObj) p).withMeta((IPersistentMap) RT.assoc(RT.meta(p), RT.TAG_KEY, null));
//						}
//						throw Util.runtimeException("Non-static fn can't have primitive parameter: " + p);
                    if (pc.isPrimitive() && !(pc == double.class || pc == long.class))
                        throw new IllegalArgumentException("Only long and double primitives are supported: " + p);

                    if (state == PSTATE.REST && Compiler.tagOf(p) != null)
                        throw Util.runtimeException("& arg cannot have type hint");
                    if (state == PSTATE.REST && method.prim != null)
                        throw Util.runtimeException("fns taking primitives cannot be variadic");

                    if (state == PSTATE.REST)
                        pc = ISeq.class;
                    argtypes.add(Type.getType(pc));
                    argclasses.add(pc);
                    LocalBinding lb = pc.isPrimitive() ?
                            Compiler.registerLocal(p, null, new MethodParamExpr(pc), true)
                            : Compiler.registerLocal(p, state == PSTATE.REST ? Compiler.ISEQ : Compiler.tagOf(p), null,
                                                     true);
                    argLocals = argLocals.cons(lb);
                    switch (state) {
                        case REQ:
                            method.reqParms = method.reqParms.cons(lb);
                            break;
                        case REST:
                            method.restParm = lb;
                            state = PSTATE.DONE;
                            break;

                        default:
                            throw Util.runtimeException("Unexpected parameter");
                    }
                }
            }
            if (method.reqParms.count() > Compiler.MAX_POSITIONAL_ARITY)
                throw Util.runtimeException("Can't specify more than " + Compiler.MAX_POSITIONAL_ARITY + " params");
            Compiler.LOOP_LOCALS.set(argLocals);
            method.argLocals = argLocals;
//			if(isStatic)
            if (method.prim != null) {
                method.argtypes = argtypes.toArray(new Type[argtypes.size()]);
                method.argclasses = argclasses.toArray(new Class[argtypes.size()]);
                for (int i = 0; i < method.argclasses.length; i++) {
                    if (method.argclasses[i] == long.class || method.argclasses[i] == double.class)
                        Compiler.getAndIncLocalNum();
                }
            }
            method.body = (new BodyExpr.Parser()).parse(C.RETURN, body);
            return method;
        } finally {
            Var.popThreadBindings();
        }
    }

    public void emit(ObjExpr fn, ClassVisitor cv) {
        if (prim != null)
            doEmitPrim(fn, cv);
        else if (fn.isStatic)
            doEmitStatic(fn, cv);
        else
            doEmit(fn, cv);
    }

    public void doEmitStatic(ObjExpr fn, ClassVisitor cv) {
        Method ms = new Method("invokeStatic", getReturnType(), argtypes);

        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
                                                    ms,
                                                    null,
                                                    //todo don't hardwire this
                                                    Compiler.EXCEPTION_TYPES,
                                                    cv);
        gen.visitCode();
        Label loopLabel = gen.mark();
        gen.visitLineNumber(line, loopLabel);
        try {
            Var.pushThreadBindings(RT.map(Compiler.LOOP_LABEL, loopLabel, Compiler.METHOD, this));
            emitBody(objx, gen, retClass, body);

            Label end = gen.mark();
            for (ISeq lbs = argLocals.seq(); lbs != null; lbs = lbs.next()) {
                LocalBinding lb = (LocalBinding) lbs.first();
                gen.visitLocalVariable(lb.name, argtypes[lb.idx].getDescriptor(), null, loopLabel, end, lb.idx);
            }
        } finally {
            Var.popThreadBindings();
        }

        gen.returnValue();
        //gen.visitMaxs(1, 1);
        gen.endMethod();

        //generate the regular invoke, calling the static method
        Method m = new Method(getMethodName(), Compiler.OBJECT_TYPE, getArgTypes());

        gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                                   m,
                                   null,
                                   //todo don't hardwire this
                                   Compiler.EXCEPTION_TYPES,
                                   cv);
        gen.visitCode();
        for (int i = 0; i < argtypes.length; i++) {
            gen.loadArg(i);
            HostExpr.emitUnboxArg(fn, gen, argclasses[i]);
        }
        gen.invokeStatic(objx.objtype, ms);
        gen.box(getReturnType());


        gen.returnValue();
        //gen.visitMaxs(1, 1);
        gen.endMethod();

    }

    public void doEmitPrim(ObjExpr fn, ClassVisitor cv) {
        Type returnType;
        if (retClass == double.class || retClass == long.class)
            returnType = getReturnType();
        else returnType = Compiler.OBJECT_TYPE;
        Method ms = new Method("invokePrim", returnType, argtypes);

        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
                                                    ms,
                                                    null,
                                                    //todo don't hardwire this
                                                    Compiler.EXCEPTION_TYPES,
                                                    cv);
        gen.visitCode();

        Label loopLabel = gen.mark();
        gen.visitLineNumber(line, loopLabel);
        try {
            Var.pushThreadBindings(RT.map(Compiler.LOOP_LABEL, loopLabel, Compiler.METHOD, this));
            emitBody(objx, gen, retClass, body);

            Label end = gen.mark();
            gen.visitLocalVariable("this", "Ljava/lang/Object;", null, loopLabel, end, 0);
            for (ISeq lbs = argLocals.seq(); lbs != null; lbs = lbs.next()) {
                LocalBinding lb = (LocalBinding) lbs.first();
                gen.visitLocalVariable(lb.name, argtypes[lb.idx - 1].getDescriptor(), null, loopLabel, end, lb.idx);
            }
        } finally {
            Var.popThreadBindings();
        }

        gen.returnValue();
        //gen.visitMaxs(1, 1);
        gen.endMethod();

        //generate the regular invoke, calling the prim method
        Method m = new Method(getMethodName(), Compiler.OBJECT_TYPE, getArgTypes());

        gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                                   m,
                                   null,
                                   //todo don't hardwire this
                                   Compiler.EXCEPTION_TYPES,
                                   cv);
        gen.visitCode();
        gen.loadThis();
        for (int i = 0; i < argtypes.length; i++) {
            gen.loadArg(i);
            HostExpr.emitUnboxArg(fn, gen, argclasses[i]);
        }
        gen.invokeInterface(Type.getType("L" + prim + ";"), ms);
        gen.box(getReturnType());


        gen.returnValue();
        //gen.visitMaxs(1, 1);
        gen.endMethod();

    }

    public void doEmit(ObjExpr fn, ClassVisitor cv) {
        Method m = new Method(getMethodName(), getReturnType(), getArgTypes());

        GeneratorAdapter gen = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
                                                    m,
                                                    null,
                                                    //todo don't hardwire this
                                                    Compiler.EXCEPTION_TYPES,
                                                    cv);
        gen.visitCode();

        Label loopLabel = gen.mark();
        gen.visitLineNumber(line, loopLabel);
        try {
            Var.pushThreadBindings(RT.map(Compiler.LOOP_LABEL, loopLabel, Compiler.METHOD, this));

            body.emit(C.RETURN, fn, gen);
            Label end = gen.mark();

            gen.visitLocalVariable("this", "Ljava/lang/Object;", null, loopLabel, end, 0);
            for (ISeq lbs = argLocals.seq(); lbs != null; lbs = lbs.next()) {
                LocalBinding lb = (LocalBinding) lbs.first();
                gen.visitLocalVariable(lb.name, "Ljava/lang/Object;", null, loopLabel, end, lb.idx);
            }
        } finally {
            Var.popThreadBindings();
        }

        gen.returnValue();
        //gen.visitMaxs(1, 1);
        gen.endMethod();
    }


    public final PersistentVector reqParms() {
        return reqParms;
    }

    public final LocalBinding restParm() {
        return restParm;
    }

    boolean isVariadic() {
        return restParm != null;
    }

    int numParams() {
        return reqParms.count() + (isVariadic() ? 1 : 0);
    }

    String getMethodName() {
        return isVariadic() ? "doInvoke" : "invoke";
    }

    Type getReturnType() {
        if (prim != null) //objx.isStatic)
            return Type.getType(retClass);
        return Compiler.OBJECT_TYPE;
    }

    Type[] getArgTypes() {
        if (isVariadic() && reqParms.count() == Compiler.MAX_POSITIONAL_ARITY) {
            Type[] ret = new Type[Compiler.MAX_POSITIONAL_ARITY + 1];
            for (int i = 0; i < Compiler.MAX_POSITIONAL_ARITY + 1; i++)
                ret[i] = Compiler.OBJECT_TYPE;
            return ret;
        }
        return Compiler.ARG_TYPES[numParams()];
    }

    void emitClearLocals(GeneratorAdapter gen) {
//		for(int i = 1; i < numParams() + 1; i++)
//			{
//			if(!localsUsedInCatchFinally.contains(i))
//				{
//				gen.visitInsn(Opcodes.ACONST_NULL);
//				gen.visitVarInsn(OBJECT_TYPE.getOpcode(Opcodes.ISTORE), i);
//				}
//			}
//		for(int i = numParams() + 1; i < maxLocal + 1; i++)
//			{
//			if(!localsUsedInCatchFinally.contains(i))
//				{
//				LocalBinding b = (LocalBinding) RT.get(indexlocals, i);
//				if(b == null || maybePrimitiveType(b.init) == null)
//					{
//					gen.visitInsn(Opcodes.ACONST_NULL);
//					gen.visitVarInsn(OBJECT_TYPE.getOpcode(Opcodes.ISTORE), i);
//					}
//				}
//			}
//		if(((FnExpr)objx).onceOnly)
//			{
//			objx.emitClearCloses(gen);
//			}
    }
}
