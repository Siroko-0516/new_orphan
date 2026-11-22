package dev.giljabi;

import java.util.function.BooleanSupplier;

/** Finish a brief notice; keep at most the latest next notice, with no stale replay. */
final class GuidanceSpeechQueue<T> {
    private static final class Item<T> {
        final T value;final BooleanSupplier valid;
        Item(T value,BooleanSupplier valid){this.value=value;this.valid=valid;}
        boolean current(){try{return valid.getAsBoolean();}catch(RuntimeException stale){return false;}}
    }
    private Item<T> current,pending;
    synchronized T offer(T value,boolean urgent,BooleanSupplier valid){
        Item<T> next=new Item<>(value,valid);if(!next.current())return null;
        if(current==null||!current.current()||urgent){current=next;pending=null;return value;}
        pending=next;return null;
    }
    synchronized T finish(T value){
        if(current==null||current.value!=value)return null;
        current=null;Item<T> next=pending;pending=null;
        if(next!=null&&next.current()){current=next;return next.value;}return null;
    }
    synchronized void clear(){current=pending=null;}
}
