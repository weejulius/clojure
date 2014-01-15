package clojure.lang.exp;

import clojure.lang.*;
import clojure.lang.Compiler.C;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
* Created by jyu on 14-1-15.
*/
public class EmptyExpr implements Expr {
    public final Object coll;
    final static Type HASHMAP_TYPE    = Type.getType(PersistentArrayMap.class);
    final static Type HASHSET_TYPE    = Type.getType(PersistentHashSet.class);
    final static Type VECTOR_TYPE     = Type.getType(PersistentVector.class);
    final static Type LIST_TYPE       = Type.getType(PersistentList.class);
    final static Type EMPTY_LIST_TYPE = Type.getType(PersistentList.EmptyList.class);


    public EmptyExpr(Object coll) {
        this.coll = coll;
    }

    public Object eval() {
        return coll;
    }

    public void emit(C context, ObjExpr objx, GeneratorAdapter gen) {
        if (coll instanceof IPersistentList)
            gen.getStatic(LIST_TYPE, "EMPTY", EMPTY_LIST_TYPE);
        else if (coll instanceof IPersistentVector)
            gen.getStatic(VECTOR_TYPE, "EMPTY", VECTOR_TYPE);
        else if (coll instanceof IPersistentMap)
            gen.getStatic(HASHMAP_TYPE, "EMPTY", HASHMAP_TYPE);
        else if (coll instanceof IPersistentSet)
            gen.getStatic(HASHSET_TYPE, "EMPTY", HASHSET_TYPE);
        else
            throw new UnsupportedOperationException("Unknown Collection type");
        if (context == C.STATEMENT) {
            gen.pop();
        }
    }

    public boolean hasJavaClass() {
        return true;
    }

    public Class getJavaClass() {
        if (coll instanceof IPersistentList)
            return IPersistentList.class;
        else if (coll instanceof IPersistentVector)
            return IPersistentVector.class;
        else if (coll instanceof IPersistentMap)
            return IPersistentMap.class;
        else if (coll instanceof IPersistentSet)
            return IPersistentSet.class;
        else
            throw new UnsupportedOperationException("Unknown Collection type");
    }
}
